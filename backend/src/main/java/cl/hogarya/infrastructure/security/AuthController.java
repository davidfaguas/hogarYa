package cl.hogarya.infrastructure.security;

import cl.hogarya.domain.model.identity.ChileanRut;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

import java.net.InetAddress;
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
 * - Proxy de confianza con soporte CIDR (ej. 10.0.0.0/8, 172.16.0.0/12, 192.168.0.0/16) y recorrido de derecha a izquierda
 * - HttpOnly, Secure, SameSite
 */
@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Autenticación", description = "Endpoints seguros de login y logout con emisión de cookie JWT HttpOnly")
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
    private final List<TrustedProxyMatcher> trustedProxyMatchers;
    private final JdbcClient jdbcClient;

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
            @Value("${hogarya.security.trusted-proxies:}") String trustedProxiesConfig,
            @Autowired(required = false) Environment env
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
                parseTrustedProxies(validateTrustedProxiesConfig(trustedProxiesConfig, env)),
                jdbcClient
        );
    }

    private static String validateTrustedProxiesConfig(String config, Environment env) {
        boolean isProd = env != null && Arrays.asList(env.getActiveProfiles()).contains("prod");
        if (isProd && (config == null || config.trim().isBlank())) {
            throw new IllegalStateException(
                    "Fallo al arrancar en perfil producción: La variable de entorno HOGARYA_TRUSTED_PROXIES es obligatoria " +
                    "y no tiene valor por defecto en producción. Configure la lista de IPs o rangos CIDR de proxies autorizados."
            );
        }
        return config;
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
                Clock.systemUTC(), parseTrustedProxies("127.0.0.1,::1,10.0.0.1"), null
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
                parseTrustedProxies("127.0.0.1,::1,10.0.0.1"), null
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
        List<TrustedProxyMatcher> matchers = new ArrayList<>();
        if (trustedProxies != null) {
            for (String p : trustedProxies) {
                matchers.add(createMatcher(p));
            }
        }
        return new AuthController(jwtTokenService, passwordEncoder, userLookup, secureCookie, sameSite, clock, matchers, null);
    }

    private AuthController(
            JwtTokenService jwtTokenService,
            PasswordEncoder passwordEncoder,
            UserLookup userLookup,
            boolean secureCookie,
            String sameSite,
            Clock clock,
            List<TrustedProxyMatcher> trustedProxyMatchers,
            JdbcClient jdbcClient
    ) {
        this.jwtTokenService = jwtTokenService;
        this.passwordEncoder = passwordEncoder;
        this.userLookup = userLookup;
        this.secureCookie = secureCookie;
        this.sameSite = sameSite;
        this.clock = clock;
        this.trustedProxyMatchers = trustedProxyMatchers != null ? trustedProxyMatchers : Collections.emptyList();
        this.dummyPasswordHash = passwordEncoder.encode("hogarya-timing-mitigation-dummy-password");
        this.jdbcClient = jdbcClient;
    }

    public String getDummyPasswordHash() {
        return dummyPasswordHash;
    }

    public record RegisterRequest(
            @Schema(description = "Email del usuario", example = "particular@hogarya.cl") String email,
            @Schema(description = "Contraseña en texto plano (mínimo 8 caracteres)", example = "passwordSegura123!") String password,
            @Schema(description = "Nombres del particular", example = "Pedro") String nombres,
            @Schema(description = "Apellidos del particular", example = "Gómez") String apellidos,
            @Schema(description = "RUT chileno del particular", example = "11.111.112-K") String rut,
            @Schema(description = "Teléfono de contacto opcional", example = "+56912345678") String telefono
    ) {}

    public record RegisterResponse(
            @Schema(description = "Identificador único del usuario creado") UUID usuarioId,
            @Schema(description = "Identificador único de la organización PARTICULAR creada") UUID organizacionId,
            @Schema(description = "Email registrado") String email,
            @Schema(description = "RUT canónico chileno") String rut,
            @Schema(description = "Rol asignado al usuario") String rol,
            @Schema(description = "Tipo de organización asignada") String tipoOrganizacion
    ) {}

    @PostMapping("/registro")
    @Operation(summary = "Registro de nuevo usuario particular", description = "Crea automáticamente una organización de tipo PARTICULAR y el usuario asociado con ROLE_PARTICULAR, emitiendo cookie de sesión")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Usuario particular registrado exitosamente con sesión iniciada",
                    content = @Content(schema = @Schema(implementation = RegisterResponse.class))),
            @ApiResponse(responseCode = "400", description = "Datos incompletos o RUT chileno inválido"),
            @ApiResponse(responseCode = "409", description = "El email o RUT ya se encuentra registrado")
    })
    public ResponseEntity<?> register(
            @RequestBody RegisterRequest req
    ) {
        if (jdbcClient == null) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Servicio de base de datos no disponible para registro"));
        }
        if (req == null || req.email() == null || req.email().isBlank()
                || req.password() == null || req.password().length() < 8
                || req.nombres() == null || req.nombres().isBlank()
                || req.apellidos() == null || req.apellidos().isBlank()
                || req.rut() == null || req.rut().isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Todos los campos obligatorios deben estar presentes (contraseña mínima de 8 caracteres)"));
        }
        if (!ChileanRut.isValid(req.rut())) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "El RUT provisto no es un RUT chileno válido"));
        }

        String canonicalRut = ChileanRut.of(req.rut()).toCanonicalString();
        String email = req.email().trim().toLowerCase();

        // Verificar unicidad de email y RUT
        Integer existing = jdbcClient.sql("""
                SELECT COUNT(*) FROM usuario WHERE LOWER(email) = :email OR rut = :rut
                """)
                .param("email", email)
                .param("rut", canonicalRut)
                .query(Integer.class)
                .single();

        if (existing != null && existing > 0) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", "El email o RUT ya se encuentra registrado en el sistema"));
        }

        UUID orgId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        String fullName = (req.nombres().trim() + " " + req.apellidos().trim()).trim();
        String phone = (req.telefono() != null && !req.telefono().isBlank()) ? req.telefono().trim() : "+56900000000";

        // 1. Insertar organización tipo PARTICULAR
        jdbcClient.sql("""
                INSERT INTO organizacion (
                    id, rut_empresa, razon_social, nombre_fantasia, tipo,
                    telefono_contacto, whatsapp_contacto, email_contacto
                ) VALUES (
                    :id, :rut, :razon, :fantasia, 'PARTICULAR',
                    :tel, :tel, :email
                )
                """)
                .param("id", orgId)
                .param("rut", canonicalRut)
                .param("razon", fullName)
                .param("fantasia", fullName)
                .param("tel", phone)
                .param("email", email)
                .update();

        // 2. Insertar usuario con ROLE_PARTICULAR
        String passHash = passwordEncoder.encode(req.password());
        jdbcClient.sql("""
                INSERT INTO usuario (
                    id, rut, email, password_hash, nombres, apellidos, rol, organizacion_id, activo
                ) VALUES (
                    :id, :rut, :email, :hash, :nombres, :apellidos, 'ROLE_PARTICULAR', :orgId, TRUE
                )
                """)
                .param("id", userId)
                .param("rut", canonicalRut)
                .param("email", email)
                .param("hash", passHash)
                .param("nombres", req.nombres().trim())
                .param("apellidos", req.apellidos().trim())
                .param("orgId", orgId)
                .update();

        // 3. Emitir cookie JWT de sesión
        String token = jwtTokenService.generateToken(email, canonicalRut, List.of("ROLE_PARTICULAR"));
        ResponseCookie cookie = ResponseCookie.from(JwtAuthenticationFilter.COOKIE_NAME, token)
                .httpOnly(true)
                .secure(secureCookie)
                .sameSite(sameSite)
                .path("/")
                .maxAge(Duration.ofMinutes(60))
                .build();

        return ResponseEntity.status(HttpStatus.CREATED)
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(new RegisterResponse(
                        userId,
                        orgId,
                        email,
                        canonicalRut,
                        "ROLE_PARTICULAR",
                        "PARTICULAR"
                ));
    }

    public record LoginRequest(
            @Schema(description = "Email o RUT del usuario", example = "usuario@hogarya.cl") String username,
            @Schema(description = "Contraseña en texto plano", example = "passwordSegura123!") String password,
            @Schema(description = "RUT opcional explícito", example = "11.111.112-K") String rut
    ) {}

    public record AuthResponse(
            @Schema(description = "Email o identificador del usuario autenticado", example = "usuario@hogarya.cl") String username,
            @Schema(description = "Mensaje informativo del estado de autenticación") String mensaje
    ) {}

    @PostMapping("/login")
    @Operation(summary = "Inicio de sesión", description = "Valida credenciales contra BCrypt, mitiga timing attacks con dummy hash y emite cookie JWT HttpOnly")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Autenticación exitosa con cookie Set-Cookie emitida",
                    content = @Content(schema = @Schema(implementation = AuthResponse.class))),
            @ApiResponse(responseCode = "401", description = "Credenciales inválidas",
                    content = @Content(schema = @Schema(implementation = AuthResponse.class))),
            @ApiResponse(responseCode = "429", description = "Demasiados intentos fallidos (bloqueo por rate limit)",
                    content = @Content(schema = @Schema(implementation = AuthResponse.class)))
    })
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

        String rawIdentifier = request.username().trim();
        String normalizedIdentifier = normalizeIdentifier(rawIdentifier, request.rut());

        String ipKey = normalizedIdentifier + "@" + clientIp;
        if (isBlocked(globalUserAttempts, normalizedIdentifier, MAX_GLOBAL_FAILED_ATTEMPTS)
                || isBlocked(ipAttempts, ipKey, MAX_FAILED_ATTEMPTS_PER_IP)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(new AuthResponse(null, "Demasiados intentos fallidos. Intente más tarde"));
        }

        Optional<UsuarioAuth> usuarioOpt = userLookup.findByIdentifier(normalizedIdentifier);

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
    @Operation(summary = "Cierre de sesión", description = "Invalida la cookie de sesión JWT")
    @ApiResponse(responseCode = "200", description = "Sesión cerrada exitosamente",
            content = @Content(schema = @Schema(implementation = AuthResponse.class)))
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

    public record CsrfResponse(
            @Schema(description = "Token CSRF criptográfico") String token,
            @Schema(description = "Nombre de la cabecera HTTP esperada", example = "X-XSRF-TOKEN") String headerName,
            @Schema(description = "Nombre del parámetro esperado", example = "_csrf") String parameterName
    ) {}

    public record UserProfileResponse(
            @Schema(description = "Email o identificador principal del usuario", example = "usuario@hogarya.cl") String username,
            @Schema(description = "RUT chileno normalizado del usuario", example = "12345678-5") String rut,
            @Schema(description = "Roles o permisos asignados", example = "[\"ROLE_PERSONA\"]") List<String> roles,
            @Schema(description = "Indica si la sesión se encuentra autenticada", example = "true") boolean autenticado
    ) {}

    @GetMapping("/csrf")
    @Operation(summary = "Obtener token CSRF", description = "Entrega el token CSRF actual en el cuerpo JSON de la respuesta para clientes SPA / Cross-Site")
    @ApiResponse(responseCode = "200", description = "Token CSRF entregado correctamente",
            content = @Content(schema = @Schema(implementation = CsrfResponse.class)))
    public ResponseEntity<CsrfResponse> getCsrf(HttpServletRequest request) {
        CsrfToken token = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
        if (token == null) {
            return ResponseEntity.ok(new CsrfResponse("", "X-XSRF-TOKEN", "_csrf"));
        }
        return ResponseEntity.ok(new CsrfResponse(token.getToken(), token.getHeaderName(), token.getParameterName()));
    }

    @GetMapping("/me")
    @Operation(summary = "Perfil de sesión del usuario actual", description = "Retorna la información del usuario autenticado a partir de la sesión JWT")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Usuario autenticado",
                    content = @Content(schema = @Schema(implementation = UserProfileResponse.class))),
            @ApiResponse(responseCode = "401", description = "No autenticado o sesión inválida")
    })
    public ResponseEntity<UserProfileResponse> getCurrentUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        String username = authentication.getName();
        List<String> roles = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList();

        String rut = null;
        if (authentication.getDetails() instanceof Map<?, ?> details) {
            Object rutObj = details.get("rut");
            if (rutObj != null) {
                rut = rutObj.toString();
            }
        }

        return ResponseEntity.ok(new UserProfileResponse(username, rut, roles, true));
    }

    public String resolveClientIp(HttpServletRequest request) {
        if (request == null) return "127.0.0.1";
        String remoteAddr = request.getRemoteAddr() != null ? request.getRemoteAddr().trim() : "127.0.0.1";

        if (!isTrustedProxy(remoteAddr)) {
            return remoteAddr;
        }

        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor == null || xForwardedFor.isBlank()) {
            return remoteAddr;
        }

        String[] hops = xForwardedFor.split(",");
        for (int i = hops.length - 1; i >= 0; i--) {
            String hop = hops[i].trim();
            if (!hop.isBlank() && !isTrustedProxy(hop)) {
                return hop;
            }
        }

        for (String hop : hops) {
            String h = hop.trim();
            if (!h.isBlank()) {
                return h;
            }
        }

        return remoteAddr;
    }

    public boolean isTrustedProxy(String ip) {
        if (ip == null || ip.isBlank()) return false;
        String cleanIp = ip.trim();
        for (TrustedProxyMatcher matcher : trustedProxyMatchers) {
            if (matcher.matches(cleanIp)) {
                return true;
            }
        }
        return false;
    }

    public interface TrustedProxyMatcher {
        boolean matches(String ip);
    }

    private static List<TrustedProxyMatcher> parseTrustedProxies(String config) {
        if (config == null || config.isBlank()) return Collections.emptyList();
        List<TrustedProxyMatcher> list = new ArrayList<>();
        for (String s : config.split(",")) {
            String entry = s.trim();
            if (!entry.isEmpty()) {
                list.add(createMatcher(entry));
            }
        }
        return Collections.unmodifiableList(list);
    }

    private static TrustedProxyMatcher createMatcher(String entry) {
        String trimmed = entry.trim();
        if (trimmed.contains("/")) {
            try {
                return new CidrMatcher(trimmed);
            } catch (Exception ignored) {}
        }
        return new ExactIpMatcher(trimmed);
    }

    static class ExactIpMatcher implements TrustedProxyMatcher {
        private final String exactIp;
        private final byte[] exactBytes;

        ExactIpMatcher(String ip) {
            this.exactIp = ip.trim();
            byte[] b = null;
            try {
                b = InetAddress.getByName(this.exactIp).getAddress();
            } catch (Exception ignored) {}
            this.exactBytes = b;
        }

        @Override
        public boolean matches(String ip) {
            if (exactIp.equalsIgnoreCase(ip.trim())) {
                return true;
            }
            if (exactBytes != null) {
                try {
                    byte[] cand = InetAddress.getByName(ip.trim()).getAddress();
                    return Arrays.equals(exactBytes, cand);
                } catch (Exception ignored) {}
            }
            return false;
        }
    }

    static class CidrMatcher implements TrustedProxyMatcher {
        private final byte[] networkBytes;
        private final int prefixBits;

        CidrMatcher(String cidr) throws Exception {
            String[] parts = cidr.split("/");
            InetAddress netAddr = InetAddress.getByName(parts[0].trim());
            this.networkBytes = netAddr.getAddress();
            this.prefixBits = Integer.parseInt(parts[1].trim());
        }

        @Override
        public boolean matches(String ip) {
            try {
                InetAddress candidate = InetAddress.getByName(ip.trim());
                byte[] candBytes = candidate.getAddress();
                if (candBytes.length != networkBytes.length) {
                    return false;
                }
                int fullBytes = prefixBits / 8;
                int remainingBits = prefixBits % 8;
                for (int i = 0; i < fullBytes; i++) {
                    if (candBytes[i] != networkBytes[i]) {
                        return false;
                    }
                }
                if (remainingBits > 0) {
                    int mask = (0xFF << (8 - remainingBits)) & 0xFF;
                    if ((candBytes[fullBytes] & mask) != (networkBytes[fullBytes] & mask)) {
                        return false;
                    }
                }
                return true;
            } catch (Exception e) {
                return false;
            }
        }
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
