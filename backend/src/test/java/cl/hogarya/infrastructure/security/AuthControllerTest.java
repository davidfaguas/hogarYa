package cl.hogarya.infrastructure.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Seguridad: AuthController valida contraseñas con BCrypt y emite cookies seguras")
class AuthControllerTest {

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private JwtTokenService tokenService;

    @BeforeEach
    void setUp() {
        tokenService = new JwtTokenService(
                "ClaveSecretaHogarYaChileSuperSegura2026DeAlMenos256BitsLongitudMinima",
                "https://hogarya.cl",
                60
        );
    }

    @Test
    @DisplayName("Login exitoso valida hash BCrypt y emite cookie HOGARYA_SESSION HttpOnly")
    void shouldAuthenticateValidBcryptCredentialsAndSetCookie() {
        String rawPassword = "passwordSuperSegura123!";
        String bcryptHash = passwordEncoder.encode(rawPassword);

        AuthController.UserLookup userLookup = identifier -> Optional.of(
                new AuthController.UsuarioAuth("11111112-K", "usuario@hogarya.cl", bcryptHash, "ROLE_PERSONA", true)
        );

        AuthController authController = AuthController.forTesting(tokenService, passwordEncoder, userLookup, true, "Strict");

        ResponseEntity<AuthController.AuthResponse> response = authController.login(
                new AuthController.LoginRequest("usuario@hogarya.cl", rawPassword, null)
        );

        assertEquals(HttpStatus.OK.value(), response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("usuario@hogarya.cl", response.getBody().username());

        String setCookieHeader = response.getHeaders().getFirst(HttpHeaders.SET_COOKIE);
        assertNotNull(setCookieHeader, "Debe incluir cabecera Set-Cookie en autenticación exitosa");
        assertTrue(setCookieHeader.contains("HOGARYA_SESSION="));
        assertTrue(setCookieHeader.contains("HttpOnly"), "La cookie debe ser HttpOnly");
        assertTrue(setCookieHeader.contains("Secure"), "La cookie debe ser Secure");
        assertTrue(setCookieHeader.contains("SameSite=Strict"), "La cookie debe tener SameSite=Strict");
    }

    @Test
    @DisplayName("Login con contraseña inválida contra hash BCrypt devuelve 401 SIN Set-Cookie")
    void shouldReturn401WithoutSetCookieWhenPasswordDoesNotMatchBcrypt() {
        String realPassword = "passwordReal123!";
        String bcryptHash = passwordEncoder.encode(realPassword);

        AuthController.UserLookup userLookup = identifier -> Optional.of(
                new AuthController.UsuarioAuth("11111112-K", "usuario@hogarya.cl", bcryptHash, "ROLE_PERSONA", true)
        );

        AuthController authController = AuthController.forTesting(tokenService, passwordEncoder, userLookup, true, "Strict");

        ResponseEntity<AuthController.AuthResponse> response = authController.login(
                new AuthController.LoginRequest("usuario@hogarya.cl", "contraseñaEquivocada", null)
        );

        assertEquals(HttpStatus.UNAUTHORIZED.value(), response.getStatusCode().value());
        assertNull(response.getHeaders().getFirst(HttpHeaders.SET_COOKIE), "NO debe incluir cabecera Set-Cookie");
        assertNotNull(response.getBody());
        assertEquals("Credenciales inválidas", response.getBody().mensaje());
    }

    @Test
    @DisplayName("Login con usuario inexistente devuelve 401 SIN Set-Cookie")
    void shouldReturn401WithoutSetCookieWhenUserDoesNotExist() {
        AuthController.UserLookup emptyLookup = identifier -> Optional.empty();

        AuthController authController = AuthController.forTesting(tokenService, passwordEncoder, emptyLookup, true, "Strict");

        ResponseEntity<AuthController.AuthResponse> response = authController.login(
                new AuthController.LoginRequest("inexistente@hogarya.cl", "cualquierClave", null)
        );

        assertEquals(HttpStatus.UNAUTHORIZED.value(), response.getStatusCode().value());
        assertNull(response.getHeaders().getFirst(HttpHeaders.SET_COOKIE), "NO debe emitir Set-Cookie para usuarios inexistentes");
    }

    @Test
    @DisplayName("Login con usuario inactivo devuelve 401 SIN Set-Cookie")
    void shouldReturn401WithoutSetCookieWhenUserIsInactive() {
        String password = "claveCorrecta";
        String bcryptHash = passwordEncoder.encode(password);

        AuthController.UserLookup inactiveLookup = identifier -> Optional.of(
                new AuthController.UsuarioAuth("11111112-K", "inactivo@hogarya.cl", bcryptHash, "ROLE_PERSONA", false)
        );

        AuthController authController = AuthController.forTesting(tokenService, passwordEncoder, inactiveLookup, true, "Strict");

        ResponseEntity<AuthController.AuthResponse> response = authController.login(
                new AuthController.LoginRequest("inactivo@hogarya.cl", password, null)
        );

        assertEquals(HttpStatus.UNAUTHORIZED.value(), response.getStatusCode().value());
        assertNull(response.getHeaders().getFirst(HttpHeaders.SET_COOKIE), "NO debe emitir Set-Cookie para usuarios inactivos");
    }

    @Test
    @DisplayName("Logout invalida la cookie seteando max-age en 0")
    void shouldInvalidateCookieOnLogout() {
        AuthController.UserLookup userLookup = identifier -> Optional.empty();
        AuthController authController = AuthController.forTesting(tokenService, passwordEncoder, userLookup, true, "Strict");

        ResponseEntity<AuthController.AuthResponse> response = authController.logout();

        assertEquals(HttpStatus.OK.value(), response.getStatusCode().value());
        String setCookieHeader = response.getHeaders().getFirst(HttpHeaders.SET_COOKIE);
        assertNotNull(setCookieHeader);
        assertTrue(setCookieHeader.contains("Max-Age=0") || setCookieHeader.contains("max-age=0"));
    }
}
