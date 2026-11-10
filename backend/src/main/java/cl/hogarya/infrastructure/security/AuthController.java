package cl.hogarya.infrastructure.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * Controlador de Autenticación.
 * Emite la cookie JWT de sesión con directivas de alta seguridad tras validar credenciales con BCrypt:
 * - HttpOnly: inaccesible para scripts frontend (prevención de robo XSS).
 * - Secure: transmisión restringida a canales cifrados HTTPS.
 * - SameSite: prevención de ataques CSRF.
 * Si las credenciales son inválidas o el usuario no existe/está inactivo, retorna 401 Unauthorized SIN Set-Cookie.
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final JwtTokenService jwtTokenService;
    private final PasswordEncoder passwordEncoder;
    private final UserLookup userLookup;
    private final boolean secureCookie;
    private final String sameSite;

    @FunctionalInterface
    public interface UserLookup {
        Optional<UsuarioAuth> findByIdentifier(String identifier);
    }

    public record UsuarioAuth(String rut, String email, String passwordHash, String rol, boolean activo) {}

    public AuthController(
            JwtTokenService jwtTokenService,
            PasswordEncoder passwordEncoder,
            JdbcClient jdbcClient,
            @Value("${hogarya.security.cookie.secure:true}") boolean secureCookie,
            @Value("${hogarya.security.cookie.same-site:Strict}") String sameSite
    ) {
        this(
                jwtTokenService,
                passwordEncoder,
                (UserLookup) identifier -> jdbcClient.sql("""
                        SELECT rut, email, password_hash AS passwordHash, rol, activo
                        FROM usuario
                        WHERE LOWER(email) = LOWER(:id) OR rut = :id
                        """)
                        .param("id", identifier.trim())
                        .query(UsuarioAuth.class)
                        .optional(),
                secureCookie,
                sameSite
        );
    }

    public static AuthController forTesting(
            JwtTokenService jwtTokenService,
            PasswordEncoder passwordEncoder,
            UserLookup userLookup,
            boolean secureCookie,
            String sameSite
    ) {
        return new AuthController(jwtTokenService, passwordEncoder, userLookup, secureCookie, sameSite);
    }

    private AuthController(
            JwtTokenService jwtTokenService,
            PasswordEncoder passwordEncoder,
            UserLookup userLookup,
            boolean secureCookie,
            String sameSite
    ) {
        this.jwtTokenService = jwtTokenService;
        this.passwordEncoder = passwordEncoder;
        this.userLookup = userLookup;
        this.secureCookie = secureCookie;
        this.sameSite = sameSite;
    }

    public record LoginRequest(String username, String password, String rut) {}
    public record AuthResponse(String username, String mensaje) {}

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@RequestBody LoginRequest request) {
        if (request == null || request.username() == null || request.username().isBlank()
                || request.password() == null || request.password().isBlank()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new AuthResponse(null, "Credenciales inválidas"));
        }

        Optional<UsuarioAuth> usuarioOpt = userLookup.findByIdentifier(request.username());
        if (usuarioOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new AuthResponse(null, "Credenciales inválidas"));
        }

        UsuarioAuth usuario = usuarioOpt.get();
        if (!usuario.activo() || !passwordEncoder.matches(request.password(), usuario.passwordHash())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new AuthResponse(null, "Credenciales inválidas"));
        }

        String username = usuario.email();
        String rut = usuario.rut();
        List<String> roles = List.of(usuario.rol() != null ? usuario.rol() : "ROLE_PERSONA");

        String token = jwtTokenService.generateToken(username, rut, roles);

        ResponseCookie cookie = ResponseCookie.from(JwtAuthenticationFilter.COOKIE_NAME, token)
                .httpOnly(true)
                .secure(secureCookie)
                .sameSite(sameSite)
                .path("/")
                .maxAge(Duration.ofMinutes(60))
                .build();

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(new AuthResponse(username, "Autenticación exitosa. Sesión iniciada con cookie HttpOnly"));
    }

    @PostMapping("/logout")
    public ResponseEntity<AuthResponse> logout() {
        ResponseCookie cookie = ResponseCookie.from(JwtAuthenticationFilter.COOKIE_NAME, "")
                .httpOnly(true)
                .secure(secureCookie)
                .sameSite(sameSite)
                .path("/")
                .maxAge(0)
                .build();

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(new AuthResponse(null, "Sesión cerrada exitosamente"));
    }
}
