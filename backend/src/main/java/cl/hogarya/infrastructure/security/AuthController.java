package cl.hogarya.infrastructure.security;

import cl.hogarya.domain.model.identity.ChileanRut;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Controlador de Autenticación.
 * Emite la cookie JWT de sesión con directivas de alta seguridad tras validar credenciales con BCrypt:
 * - Normalización de RUT vía ChileanRut.of(...)
 * - Mitigación de Timing Attacks (compara contra dummy hash generado con el mismo strength que los reales)
 * - Rate limiting por tupla (identificador + IP) con tope de 5 intentos fallidos
 * - Tope global por identificador (20 intentos fallidos máximos en ventana de 15 min a través de cualquier IP)
 * - Proxy de confianza: No confía a ciegas en X-Forwarded-For si el remoteAddr no es un proxy autorizado
 * - HttpOnly, Secure, SameSite
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    public static final int MAX_FAILED_ATTEMPTS_PER_IP = 5;
    public static final int MAX_GLOBAL_FAILED_ATTEMPTS = 20;
    public static final Duration LOCKOUT_DURATION = Duration.ofMinutes(15);

    private final JwtTokenService jwtTokenService;
    private final PasswordEncoder passwordEncoder;
    private final UserLookup userLookup;
    private final boolean secureCookie;
    private final String sameSite;
    private final String dummyPasswordHash;
    private final Clock clock;
    private final Set<String> trustedProxies;

    private final ConcurrentMap<String, AttemptInfo> ipAttempts = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, AttemptInfo> globalUserAttempts = new ConcurrentHashMap<>();

    record AttemptInfo(int count, Instant lastAttempt) {}

    @FunctionalInterface
    public interface UserLookup {
        Optional<UsuarioAuth> findByIdentifier(String identifier);
    }

    public record UsuarioAuth(String rut, String email, String passwordHash, String rol, boolean activo) {}

    @Autowired
    public AuthController(
            JwtTokenService jwtTokenService,
            PasswordEncoder passwordEncoder,
            JdbcClient jdbcClient,
            @Value("${hogarya.security.cookie.secure:true}") boolean secureCookie,
            @Value("${hogarya.security.cookie.same-site:Strict}") String sameSite,
            @Value("${hogarya.security.trusted-proxies:127.0.0.1,::1,0:0:0:0:0:0:0:1,10.0.0.1}") String trustedProxiesConfig
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
                sameSite,
                Clock.systemUTC(),
                parseTrustedProxies(trustedProxiesConfig)
        );
    }

    public static AuthController forTesting(
            JwtTokenService jwtTokenService,
            PasswordEncoder passwordEncoder,
            UserLookup userLookup,
            boolean secureCookie,
            String sameSite
    ) {
        return new AuthController(
                jwtTokenService, passwordEncoder, userLookup, secureCookie, sameSite,
                Clock.systemUTC(), Set.of("127.0.0.1", "::1", "10.0.0.1")
        );
    }

    public static AuthController forTestingWithClock(
            JwtTokenService jwtTokenService,
            PasswordEncoder passwordEncoder,
            UserLookup userLookup,
            boolean secureCookie,
            String sameSite,
            Clock clock
    ) {
        return new AuthController(
                jwtTokenService, passwordEncoder, userLookup, secureCookie, sameSite, clock,
                Set.of("127.0.0.1", "::1", "10.0.0.1")
        );
    }

    public static AuthController forTestingWithClock(
            JwtTokenService jwtTokenService,
            PasswordEncoder passwordEncoder,
            UserLookup userLookup,
            boolean secureCookie,
            String sameSite,
            Clock clock,
            Set<String> trustedProxies
    ) {
        return new AuthController(jwtTokenService, passwordEncoder, userLookup, secureCookie, sameSite, clock, trustedProxies);
    }

    private AuthController(
            JwtTokenService jwtTokenService,
            PasswordEncoder passwordEncoder,
            UserLookup userLookup,
            boolean secureCookie,
            String sameSite,
            Clock clock,
            Set<String> trustedProxies
    ) {
        this.jwtTokenService = jwtTokenService;
        this.passwordEncoder = passwordEncoder;
        this.userLookup = userLookup;
        this.secureCookie = secureCookie;
        this.sameSite = sameSite;
        this.clock = clock;
        this.trustedProxies = trustedProxies != null ? trustedProxies : Collections.emptySet();
        this.dummyPasswordHash = passwordEncoder.encode("hogarya-timing-mitigation-dummy-password");
    }

    public String getDummyPasswordHash() {
        return dummyPasswordHash;
    }

    public record LoginRequest(String username, String password, String rut) {}
    public record AuthResponse(String username, String mensaje) {}

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(
            @RequestBody LoginRequest request,
            HttpServletRequest httpRequest
    ) {
        String clientIp = resolveClientIp(httpRequest);
        return executeLogin(request, clientIp);
    }

    public ResponseEntity<AuthResponse> login(LoginRequest request) {
        return executeLogin(request, "127.0.0.1");
    }

    public ResponseEntity<AuthResponse> login(LoginRequest request, String clientIp) {
        return executeLogin(request, clientIp != null ? clientIp : "127.0.0.1");
    }

    private ResponseEntity<AuthResponse> executeLogin(LoginRequest request, String clientIp) {
        if (request == null || request.username() == null || request.username().isBlank()
                || request.password() == null || request.password().isBlank()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new AuthResponse(null, "Credenciales inválidas"));
        }

        // 1. Normalización de Identificador (Email o RUT con ChileanRut.of)
        String rawIdentifier = request.username().trim();
        String normalizedIdentifier = normalizeIdentifier(rawIdentifier, request.rut());

        // 2. Control de Tasa:
        // A) Tope global por identificador (ej. 20 intentos en 15 min independientemente de la IP)
        // B) Límite por tupla (identificador + IP) (5 intentos en 15 min)
        String ipKey = normalizedIdentifier + "@" + clientIp;
        if (isBlocked(globalUserAttempts, normalizedIdentifier, MAX_GLOBAL_FAILED_ATTEMPTS)
                || isBlocked(ipAttempts, ipKey, MAX_FAILED_ATTEMPTS_PER_IP)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(new AuthResponse(null, "Demasiados intentos fallidos. Intente más tarde"));
        }

        Optional<UsuarioAuth> usuarioOpt = userLookup.findByIdentifier(normalizedIdentifier);

        // 3. Mitigación de Timing Attacks:
        // Si el usuario no existe, ejecutar matches contra hash dummy con el mismo strength para tiempo constante
        if (usuarioOpt.isEmpty()) {
            passwordEncoder.matches(request.password(), dummyPasswordHash);
            recordFailedAttempt(normalizedIdentifier, ipKey);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new AuthResponse(null, "Credenciales inválidas"));
        }

        UsuarioAuth usuario = usuarioOpt.get();
        if (!usuario.activo() || !passwordEncoder.matches(request.password(), usuario.passwordHash())) {
            recordFailedAttempt(normalizedIdentifier, ipKey);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new AuthResponse(null, "Credenciales inválidas"));
        }

        // Éxito: restablecer intentos fallidos
        resetAttempts(normalizedIdentifier, ipKey);

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

    /**
     * Resuelve la dirección IP del cliente de forma segura.
     * Solo confía en la cabecera X-Forwarded-For si el remoteAddr que entrega la petición
     * pertenece a la lista de proxies de confianza configurados.
     */
    public String resolveClientIp(HttpServletRequest request) {
        if (request == null) return "127.0.0.1";
        String remoteAddr = request.getRemoteAddr() != null ? request.getRemoteAddr().trim() : "127.0.0.1";

        if (isTrustedProxy(remoteAddr)) {
            String xForwardedFor = request.getHeader("X-Forwarded-For");
            if (xForwardedFor != null && !xForwardedFor.isBlank()) {
                String firstHop = xForwardedFor.split(",")[0].trim();
                if (!firstHop.isBlank()) {
                    return firstHop;
                }
            }
        }

        return remoteAddr;
    }

    private boolean isTrustedProxy(String ip) {
        return trustedProxies.contains(ip);
    }

    private static Set<String> parseTrustedProxies(String config) {
        if (config == null || config.isBlank()) return Collections.emptySet();
        Set<String> set = new HashSet<>();
        for (String s : config.split(",")) {
            if (!s.isBlank()) set.add(s.trim());
        }
        return Collections.unmodifiableSet(set);
    }

    private String normalizeIdentifier(String rawIdentifier, String explicitRut) {
        if (explicitRut != null && !explicitRut.isBlank()) {
            try {
                return ChileanRut.of(explicitRut).toCanonicalString();
            } catch (Exception ignored) {}
        }

        try {
            return ChileanRut.of(rawIdentifier).toCanonicalString();
        } catch (Exception ignored) {
            return rawIdentifier.toLowerCase();
        }
    }

    private boolean isBlocked(ConcurrentMap<String, AttemptInfo> map, String key, int maxAttempts) {
        AttemptInfo info = map.get(key);
        if (info == null) return false;
        Instant now = clock.instant();
        if (Duration.between(info.lastAttempt(), now).compareTo(LOCKOUT_DURATION) > 0) {
            map.remove(key);
            return false;
        }
        return info.count() >= maxAttempts;
    }

    private void recordFailedAttempt(String userKey, String ipKey) {
        recordAttempt(globalUserAttempts, userKey);
        recordAttempt(ipAttempts, ipKey);
    }

    private void recordAttempt(ConcurrentMap<String, AttemptInfo> map, String key) {
        map.compute(key, (k, old) -> {
            Instant now = clock.instant();
            if (old == null || Duration.between(old.lastAttempt(), now).compareTo(LOCKOUT_DURATION) > 0) {
                return new AttemptInfo(1, now);
            }
            return new AttemptInfo(old.count() + 1, now);
        });
    }

    private void resetAttempts(String userKey, String ipKey) {
        globalUserAttempts.remove(userKey);
        ipAttempts.remove(ipKey);
    }
}
