package cl.hogarya.infrastructure.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Seguridad: AuthController valida contraseñas con BCrypt, normaliza RUT, aplica rate limit (user+IP) con expiración y fake hash del mismo strength")
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
    @DisplayName("Login normaliza el RUT formateado (con puntos y minúscula) usando ChileanRut.of(...)")
    void shouldNormalizeRutIdentifierUsingChileanRut() {
        String rawPassword = "password123!";
        String bcryptHash = passwordEncoder.encode(rawPassword);

        AuthController.UserLookup userLookup = identifier -> {
            if ("11111112-K".equals(identifier)) {
                return Optional.of(new AuthController.UsuarioAuth("11111112-K", "persona@hogarya.cl", bcryptHash, "ROLE_PERSONA", true));
            }
            return Optional.empty();
        };

        AuthController authController = AuthController.forTesting(tokenService, passwordEncoder, userLookup, true, "Strict");

        ResponseEntity<AuthController.AuthResponse> response = authController.login(
                new AuthController.LoginRequest("11.111.112-k", rawPassword, null)
        );

        assertEquals(HttpStatus.OK.value(), response.getStatusCode().value());
        assertEquals("persona@hogarya.cl", response.getBody().username());
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
    @DisplayName("Login con usuario inexistente ejecuta hash dummy con mismo strength y devuelve 401 SIN Set-Cookie")
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
    @DisplayName("Fake hash tiene el mismo strength (cost factor 10) que los hashes reales del PasswordEncoder")
    void shouldEnsureDummyHashHasExactSameStrengthAsRealPasswordEncoder() {
        AuthController.UserLookup emptyLookup = identifier -> Optional.empty();
        AuthController authController = AuthController.forTesting(tokenService, passwordEncoder, emptyLookup, true, "Strict");

        String dummyHash = authController.getDummyPasswordHash();
        assertNotNull(dummyHash, "El dummy hash no debe ser nulo");
        assertTrue(dummyHash.startsWith("$2a$10$") || dummyHash.startsWith("$2b$10$"),
                "El hash dummy debe tener exactamente el cost factor 10 igual a los hashes reales de BCryptPasswordEncoder");
        assertFalse(passwordEncoder.matches("passwordInvalida", dummyHash));
    }

    @Test
    @DisplayName("Rate limiting por clave compuesta (identificador + IP): 5 intentos fallidos desde IP1 no bloquean a IP2")
    void shouldTrackRateLimitByCompositeKeyIdentifierAndIp() {
        AuthController.UserLookup emptyLookup = identifier -> Optional.empty();
        AuthController authController = AuthController.forTesting(tokenService, passwordEncoder, emptyLookup, true, "Strict");

        String targetUser = "victima@hogarya.cl";
        String attackerIp = "192.168.1.50";
        String legitimateUserIp = "192.168.1.99";

        // 5 intentos fallidos desde la IP del atacante
        for (int i = 0; i < 5; i++) {
            ResponseEntity<AuthController.AuthResponse> r = authController.login(
                    new AuthController.LoginRequest(targetUser, "claveInvalida" + i, null),
                    attackerIp
            );
            assertEquals(HttpStatus.UNAUTHORIZED.value(), r.getStatusCode().value());
        }

        // El 6to intento desde la IP atacante debe ser bloqueado con 429
        ResponseEntity<AuthController.AuthResponse> blockedResponse = authController.login(
                new AuthController.LoginRequest(targetUser, "otraClave", null),
                attackerIp
        );
        assertEquals(HttpStatus.TOO_MANY_REQUESTS.value(), blockedResponse.getStatusCode().value());
        assertTrue(blockedResponse.getBody().mensaje().contains("Demasiados intentos fallidos"));

        // Un intento para el mismo usuario desde OTRA IP (usuario legítimo) NO debe estar bloqueado (retorna 401, no 429)
        ResponseEntity<AuthController.AuthResponse> legitResponse = authController.login(
                new AuthController.LoginRequest(targetUser, "intentoLegitimo", null),
                legitimateUserIp
        );
        assertEquals(HttpStatus.UNAUTHORIZED.value(), legitResponse.getStatusCode().value(),
                "La IP legítima no debe verse afectada por el bloqueo de la IP atacante");
    }

    @Test
    @DisplayName("Expiración de Rate Limit: tras 15 minutos el bloqueo expira y permite nuevos intentos")
    void shouldExpireRateLimitLockoutAfterDuration() {
        AuthController.UserLookup emptyLookup = identifier -> Optional.empty();

        AtomicReference<Instant> currentInstant = new AtomicReference<>(Instant.parse("2026-11-10T20:00:00Z"));
        Clock testClock = new Clock() {
            @Override
            public ZoneId getZone() { return ZoneId.of("UTC"); }
            @Override
            public Clock withZone(ZoneId zone) { return this; }
            @Override
            public Instant instant() { return currentInstant.get(); }
        };

        AuthController authController = AuthController.forTestingWithClock(
                tokenService, passwordEncoder, emptyLookup, true, "Strict", testClock
        );

        String user = "bloqueo@hogarya.cl";
        String ip = "10.0.0.1";

        // Realizar 5 intentos para bloquear
        for (int i = 0; i < 5; i++) {
            authController.login(new AuthController.LoginRequest(user, "clave" + i, null), ip);
        }

        // Confirmar bloqueo
        ResponseEntity<AuthController.AuthResponse> blocked = authController.login(
                new AuthController.LoginRequest(user, "intento6", null), ip
        );
        assertEquals(HttpStatus.TOO_MANY_REQUESTS.value(), blocked.getStatusCode().value());

        // Avanzar el reloj 16 minutos (la duración de bloqueo es 15 minutos)
        currentInstant.set(currentInstant.get().plus(Duration.ofMinutes(16)));

        // Tras la expiración, el intento es procesado nuevamente (retorna 401 por credencial inválida, no 429)
        ResponseEntity<AuthController.AuthResponse> postExpiration = authController.login(
                new AuthController.LoginRequest(user, "intentoPostExpiracion", null), ip
        );
        assertEquals(HttpStatus.UNAUTHORIZED.value(), postExpiration.getStatusCode().value(),
                "Tras expirar el período de 15 minutos, no debe retornar 429");
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

    @Test
    @DisplayName("Seguridad IP: No confía en X-Forwarded-For si el remoteAddr no es un proxy de confianza")
    void shouldNotTrustXForwardedForFromUntrustedRemoteAddr() {
        AuthController.UserLookup emptyLookup = identifier -> Optional.empty();
        AuthController authController = AuthController.forTesting(tokenService, passwordEncoder, emptyLookup, true, "Strict");

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("198.51.100.25"); // IP pública/no de confianza
        request.addHeader("X-Forwarded-For", "203.0.113.195"); // Intento de spoofing

        String resolvedIp = authController.resolveClientIp(request);
        assertEquals("198.51.100.25", resolvedIp, "Debe ignorar X-Forwarded-For cuando remoteAddr no está en proxies de confianza");

        // 5 intentos fallidos con esa IP no confiable bloquean esa IP
        String targetUser = "victima-spoof@hogarya.cl";
        for (int i = 0; i < 5; i++) {
            ResponseEntity<AuthController.AuthResponse> r = authController.login(
                    new AuthController.LoginRequest(targetUser, "claveInvalida" + i, null),
                    request
            );
            assertEquals(HttpStatus.UNAUTHORIZED.value(), r.getStatusCode().value());
        }

        // Intento 6 debe dar 429
        ResponseEntity<AuthController.AuthResponse> blocked = authController.login(
                new AuthController.LoginRequest(targetUser, "claveInvalida6", null),
                request
        );
        assertEquals(HttpStatus.TOO_MANY_REQUESTS.value(), blocked.getStatusCode().value());
    }

    @Test
    @DisplayName("Seguridad Global: Rotar X-Forwarded-For a través de proxy confiable bloquea tras 20 intentos por tope global")
    void shouldBlockWhenExceedingGlobalCapEvenWithRotatingXForwardedForFromTrustedProxy() {
        AuthController.UserLookup emptyLookup = identifier -> Optional.empty();
        AuthController authController = AuthController.forTesting(tokenService, passwordEncoder, emptyLookup, true, "Strict");

        String targetUser = "victima-global@hogarya.cl";

        // Simular 20 intentos con IPs de cliente rotadas a través del proxy de confianza 10.0.0.1
        for (int i = 1; i <= 20; i++) {
            MockHttpServletRequest req = new MockHttpServletRequest();
            req.setRemoteAddr("10.0.0.1"); // Proxy confiable
            req.addHeader("X-Forwarded-For", "198.51.100." + i); // IP rotada en cada petición

            ResponseEntity<AuthController.AuthResponse> r = authController.login(
                    new AuthController.LoginRequest(targetUser, "claveRotada" + i, null),
                    req
            );
            assertEquals(HttpStatus.UNAUTHORIZED.value(), r.getStatusCode().value(),
                    "Intento " + i + " debe fallar con 401 antes de alcanzar el tope global");
        }

        // Intento 21 con una IP completamente NUEVA (por ejemplo 198.51.100.21)
        MockHttpServletRequest req21 = new MockHttpServletRequest();
        req21.setRemoteAddr("10.0.0.1");
        req21.addHeader("X-Forwarded-For", "198.51.100.21");

        ResponseEntity<AuthController.AuthResponse> blocked = authController.login(
                new AuthController.LoginRequest(targetUser, "claveNueva21", null),
                req21
        );

        assertEquals(HttpStatus.TOO_MANY_REQUESTS.value(), blocked.getStatusCode().value(),
                "Tras el tope global de 20 intentos para el identificador, debe responder 429 incluso desde una nueva IP");
        assertTrue(blocked.getBody().mensaje().contains("Demasiados intentos fallidos"));
    }

    @Test
    @DisplayName("Seguridad IP: Recorre X-Forwarded-For de derecha a izquierda y descarta IPs falsificadas inyectadas por el cliente")
    void shouldResolveClientIpFromRightToLeftIgnoringForgedPrefix() {
        AuthController.UserLookup emptyLookup = identifier -> Optional.empty();
        Set<String> trusted = Set.of("127.0.0.1", "::1", "10.0.0.1", "10.0.0.2");
        AuthController authController = AuthController.forTestingWithClock(
                tokenService, passwordEncoder, emptyLookup, true, "Strict", Clock.systemUTC(), trusted
        );

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.1"); // Inmediato: proxy confiable
        // Cadena falsificada: cliente real (198.51.100.25) inyectó "203.0.113.195", luego pasó por proxy "10.0.0.2"
        request.addHeader("X-Forwarded-For", "203.0.113.195, 198.51.100.25, 10.0.0.2");

        String resolved = authController.resolveClientIp(request);
        assertEquals("198.51.100.25", resolved,
                "Debe resolver 198.51.100.25 ignorando el prefijo falso 203.0.113.195 y saltando el proxy confiable 10.0.0.2");
    }

    @Test
    @DisplayName("Seguridad IP: Soporte de rangos CIDR en proxies de confianza (IPv4 e IPv6)")
    void shouldMatchTrustedProxiesUsingCidrRanges() {
        AuthController.UserLookup emptyLookup = identifier -> Optional.empty();
        Set<String> trusted = Set.of("10.0.0.0/8", "192.168.0.0/16", "172.16.0.0/12", "127.0.0.1", "::1");
        AuthController authController = AuthController.forTestingWithClock(
                tokenService, passwordEncoder, emptyLookup, true, "Strict", Clock.systemUTC(), trusted
        );

        // IPs dentro de los rangos CIDR configurados deben ser reconocidas como proxies de confianza
        assertTrue(authController.isTrustedProxy("10.1.2.3"), "10.1.2.3 debe coincidir con 10.0.0.0/8");
        assertTrue(authController.isTrustedProxy("10.255.255.254"), "10.255.255.254 debe coincidir con 10.0.0.0/8");
        assertTrue(authController.isTrustedProxy("192.168.1.100"), "192.168.1.100 debe coincidir con 192.168.0.0/16");
        assertTrue(authController.isTrustedProxy("172.16.50.1"), "172.16.50.1 debe coincidir con 172.16.0.0/12");
        assertTrue(authController.isTrustedProxy("127.0.0.1"), "127.0.0.1 debe coincidir con IP exacta");

        // IPs fuera de los rangos NO deben ser consideradas proxies de confianza
        assertFalse(authController.isTrustedProxy("198.51.100.1"), "198.51.100.1 es IP pública, no proxy confiable");
        assertFalse(authController.isTrustedProxy("172.32.0.1"), "172.32.0.1 está fuera de 172.16.0.0/12");
        assertFalse(authController.isTrustedProxy("8.8.8.8"), "8.8.8.8 no es proxy confiable");
    }

    @Test
    @DisplayName("Seguridad IP: Login desde dos redes distintas - se cuentan como IPs distintas y bloqueo en una no bloquea a la otra")
    void shouldCountLoginsFromDifferentNetworksSeparatelyAndNotBlockAcrossNetworks() {
        String validEmail = "usuario.redes@hogarya.cl";
        String validRut = "11.111.112-K";
        String password = "claveCorrecta2026!";
        String encoded = passwordEncoder.encode(password);

        AuthController.UserLookup userLookup = identifier -> Optional.of(
                new AuthController.UsuarioAuth(validRut, validEmail, encoded, "ROLE_CLIENTE", true)
        );

        Set<String> trusted = Set.of("10.0.0.0/8", "172.16.0.0/12");
        AuthController authController = AuthController.forTestingWithClock(
                tokenService, passwordEncoder, userLookup, true, "Strict", Clock.systemUTC(), trusted
        );

        // Red A: Cliente 198.51.100.10 a través del proxy 10.0.0.1
        MockHttpServletRequest reqRedA = new MockHttpServletRequest();
        reqRedA.setRemoteAddr("10.0.0.1");
        reqRedA.addHeader("X-Forwarded-For", "198.51.100.10");

        // Red B: Cliente 203.0.113.20 a través del proxy 172.16.1.1
        MockHttpServletRequest reqRedB = new MockHttpServletRequest();
        reqRedB.setRemoteAddr("172.16.1.1");
        reqRedB.addHeader("X-Forwarded-For", "203.0.113.20");

        assertEquals("198.51.100.10", authController.resolveClientIp(reqRedA));
        assertEquals("203.0.113.20", authController.resolveClientIp(reqRedB));

        // 5 intentos fallidos consecutivos desde la Red A para bloquearla
        for (int i = 0; i < 5; i++) {
            ResponseEntity<AuthController.AuthResponse> resp = authController.login(
                    new AuthController.LoginRequest(validEmail, "claveEquivocada", null),
                    reqRedA
            );
            assertEquals(HttpStatus.UNAUTHORIZED.value(), resp.getStatusCode().value());
        }

        // El 6to intento desde la Red A debe retornar 429 TOO_MANY_REQUESTS
        ResponseEntity<AuthController.AuthResponse> respRedABlocked = authController.login(
                new AuthController.LoginRequest(validEmail, "claveEquivocada", null),
                reqRedA
        );
        assertEquals(HttpStatus.TOO_MANY_REQUESTS.value(), respRedABlocked.getStatusCode().value(),
                "Red A debe quedar bloqueada tras 5 intentos fallidos");

        // Mientras tanto, la petición legítima desde la Red B debe tener ÉXITO (200 OK con cookie emitida)
        ResponseEntity<AuthController.AuthResponse> respRedB = authController.login(
                new AuthController.LoginRequest(validEmail, password, null),
                reqRedB
        );
        assertEquals(HttpStatus.OK.value(), respRedB.getStatusCode().value(),
                "Un bloqueo por rate limit en la Red A NO debe afectar los logins legítimos desde la Red B");
        assertTrue(respRedB.getHeaders().containsKey(HttpHeaders.SET_COOKIE),
                "La sesión debe iniciarse exitosamente emitiendo la cookie Set-Cookie");
    }
}
