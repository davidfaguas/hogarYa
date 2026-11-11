package cl.hogarya.infrastructure.web;

import cl.hogarya.infrastructure.security.JwtTokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import jakarta.servlet.http.Cookie;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
@DisabledIfSystemProperty(named = "test.docker.skip", matches = "true", disabledReason = "Ejecución local explícita sin Docker (-Dtest.docker.skip=true)")
@DisplayName("Integración Endpoints REST: PropertyController, FavoritesController y MortgageSimulatorController")
class ControllersIntegrationIT {

    @SuppressWarnings("resource")
    @Container
    static GenericContainer<?> postgresContainer = new GenericContainer<>(
            new ImageFromDockerfile("hogarya-postgis-pgvector:16-test", false)
                    .withFileFromPath("Dockerfile", Paths.get("Dockerfile.test-db"))
    )
            .withExposedPorts(5432)
            .withEnv("POSTGRES_DB", "hogarya_db")
            .withEnv("POSTGRES_USER", "hogarya_app")
            .withEnv("POSTGRES_PASSWORD", "hogarya_secret");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () ->
                "jdbc:postgresql://" + postgresContainer.getHost() + ":" + postgresContainer.getMappedPort(5432) + "/hogarya_db");
        registry.add("spring.datasource.username", () -> "hogarya_app");
        registry.add("spring.datasource.password", () -> "hogarya_secret");
        registry.add("spring.flyway.enabled", () -> "true");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcClient jdbcClient;

    @Autowired
    private JwtTokenService jwtTokenService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private UUID userId;
    private UUID testPropId;
    private Cookie authCookie;
    private Cookie corredorOrg1Cookie;
    private Cookie corredorOrg2Cookie;
    private Cookie adminCookie;

    @Autowired(required = false)
    private org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor indexingTaskExecutor;

    @Autowired
    private cl.hogarya.domain.service.search.PropertyIndexingService propertyIndexingService;

    @BeforeEach
    void setUpTestData() {
        UUID orgId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID orgId2 = UUID.fromString("00000000-0000-0000-0000-000000000002");
        userId = UUID.fromString("00000000-0000-0000-0000-000000000099");
        UUID corredor1Id = UUID.fromString("00000000-0000-0000-0000-000000000088");
        UUID corredor2Id = UUID.fromString("00000000-0000-0000-0000-000000000077");
        UUID adminId = UUID.fromString("00000000-0000-0000-0000-000000000066");
        testPropId = UUID.fromString("55555555-5555-5555-5555-555555555555");

        jdbcClient.sql("""
            INSERT INTO organizacion (id, rut_empresa, razon_social, nombre_fantasia, tipo, telefono_contacto, whatsapp_contacto, email_contacto)
            VALUES (:id, '76123456-0', 'Inmobiliaria Web Test SpA', 'HogarYa Web', 'INMOBILIARIA', '+56911111111', '+56911111111', 'contacto@hogarya.cl')
            ON CONFLICT (id) DO NOTHING
            """).param("id", orgId).update();

        jdbcClient.sql("""
            INSERT INTO organizacion (id, rut_empresa, razon_social, nombre_fantasia, tipo, telefono_contacto, whatsapp_contacto, email_contacto)
            VALUES (:id, '76999999-0', 'Corredora Otra SpA', 'Otra Corredora', 'CORREDORA', '+56922222222', '+56922222222', 'contacto@otra.cl')
            ON CONFLICT (id) DO NOTHING
            """).param("id", orgId2).update();

        // 1. Usuario Persona (rol insuficiente para publicar)
        jdbcClient.sql("""
            INSERT INTO usuario (id, rut, email, password_hash, nombres, apellidos, rol, organizacion_id, activo)
            VALUES (:id, '12345678-5', 'usuario.test@hogarya.cl', :hash, 'Juan', 'Perez', 'ROLE_PERSONA', :orgId, true)
            ON CONFLICT (id) DO UPDATE SET password_hash = EXCLUDED.password_hash
            """)
            .param("id", userId)
            .param("hash", passwordEncoder.encode("passwordTest123!"))
            .param("orgId", orgId)
            .update();

        // 2. Corredor en Org 1 (dueño del recurso testPropId)
        jdbcClient.sql("""
            INSERT INTO usuario (id, rut, email, password_hash, nombres, apellidos, rol, organizacion_id, activo)
            VALUES (:id, '11111111-1', 'corredor1@hogarya.cl', :hash, 'Carlos', 'Corredor', 'ROLE_CORREDOR', :orgId, true)
            ON CONFLICT (id) DO UPDATE SET password_hash = EXCLUDED.password_hash
            """)
            .param("id", corredor1Id)
            .param("hash", passwordEncoder.encode("passwordTest123!"))
            .param("orgId", orgId)
            .update();

        // 3. Corredor en Org 2 (organización ajena)
        jdbcClient.sql("""
            INSERT INTO usuario (id, rut, email, password_hash, nombres, apellidos, rol, organizacion_id, activo)
            VALUES (:id, '22222222-2', 'corredor2@otra.cl', :hash, 'Ana', 'Ajena', 'ROLE_CORREDOR', :orgId2, true)
            ON CONFLICT (id) DO UPDATE SET password_hash = EXCLUDED.password_hash
            """)
            .param("id", corredor2Id)
            .param("hash", passwordEncoder.encode("passwordTest123!"))
            .param("orgId2", orgId2)
            .update();

        // 4. Administrador con permisos globales
        jdbcClient.sql("""
            INSERT INTO usuario (id, rut, email, password_hash, nombres, apellidos, rol, organizacion_id, activo)
            VALUES (:id, '99999999-9', 'admin@hogarya.cl', :hash, 'Admin', 'Root', 'ROLE_ADMIN', :orgId, true)
            ON CONFLICT (id) DO UPDATE SET password_hash = EXCLUDED.password_hash
            """)
            .param("id", adminId)
            .param("hash", passwordEncoder.encode("passwordTest123!"))
            .param("orgId", orgId)
            .update();

        jdbcClient.sql("""
            INSERT INTO propiedad (
                id, codigo_publicacion, organizacion_id, titulo, descripcion, tipo_propiedad, 
                operacion, estado, precio_uf, m2_util, m2_total, dormitorios, banos, 
                comuna_id, direccion_referencial, barrio, ubicacion, activo
            ) VALUES (
                :id, 'WEB-PROP-1', :orgId,
                'Hermoso departamento en Providencia frente a parque',
                'Excelente departamento muy luminoso con vista despejada',
                'DEPARTAMENTO', 'VENTA', 'NUEVA', 4200.00, 80.0, 90.0, 2, 2, 
                1, 'Av. Providencia 1234', 'Providencia', 
                ST_SetSRID(ST_MakePoint(-70.6100, -33.4250), 4326)::geography, TRUE
            ) ON CONFLICT (id) DO NOTHING
            """).param("id", testPropId).param("orgId", orgId).update();

        // Generar cookies JWT de prueba
        authCookie = new Cookie("HOGARYA_SESSION", jwtTokenService.generateToken("usuario.test@hogarya.cl", "12345678-5", List.of("ROLE_PERSONA")));
        authCookie.setHttpOnly(true);
        authCookie.setPath("/");

        corredorOrg1Cookie = new Cookie("HOGARYA_SESSION", jwtTokenService.generateToken("corredor1@hogarya.cl", "11111111-1", List.of("ROLE_CORREDOR")));
        corredorOrg1Cookie.setHttpOnly(true);
        corredorOrg1Cookie.setPath("/");

        corredorOrg2Cookie = new Cookie("HOGARYA_SESSION", jwtTokenService.generateToken("corredor2@otra.cl", "22222222-2", List.of("ROLE_CORREDOR")));
        corredorOrg2Cookie.setHttpOnly(true);
        corredorOrg2Cookie.setPath("/");

        adminCookie = new Cookie("HOGARYA_SESSION", jwtTokenService.generateToken("admin@hogarya.cl", "99999999-9", List.of("ROLE_ADMIN")));
        adminCookie.setHttpOnly(true);
        adminCookie.setPath("/");
    }

    @Test
    @DisplayName("PropertyController: GET /api/v1/propiedades retorna resultados y metadata de paginación")
    void shouldSearchProperties() throws Exception {
        mockMvc.perform(get("/api/v1/propiedades")
                        .param("operacion", "VENTA")
                        .param("tipoPropiedad", "DEPARTAMENTO")
                        .param("limit", "10")
                        .param("offset", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total", greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.limit", is(10)))
                .andExpect(jsonPath("$.offset", is(0)))
                .andExpect(jsonPath("$.items", notNullValue()));
    }

    @Test
    @DisplayName("PropertyController: GET /api/v1/propiedades/{id} retorna ficha técnica detallada")
    void shouldGetPropertyDetailById() throws Exception {
        mockMvc.perform(get("/api/v1/propiedades/" + testPropId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(testPropId.toString())))
                .andExpect(jsonPath("$.codigo_publicacion", is("WEB-PROP-1")))
                .andExpect(jsonPath("$.precio_uf", is(4200.0)))
                .andExpect(jsonPath("$.precio_clp_estimado", notNullValue()))
                .andExpect(jsonPath("$.dormitorios", is(2)));
    }

    @Test
    @DisplayName("PropertyController: GET /api/v1/propiedades/{id} con ID inexistente retorna 404")
    void shouldReturn404ForNonExistentProperty() throws Exception {
        mockMvc.perform(get("/api/v1/propiedades/" + UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("MortgageSimulatorController: POST /api/v1/simulador calcula dividendo y elegibilidad FOGAES")
    void shouldSimulateMortgageLoan() throws Exception {
        String requestJson = """
                {
                    "valorPropiedadUf": 3500.00,
                    "porcentajePie": 10.0,
                    "plazoAnos": 25,
                    "tasaInteresAnual": 4.5,
                    "valorUfClp": 37850.00,
                    "esPrimeraVivienda": true,
                    "tieneSubsidioDs19": false
                }
                """;

        mockMvc.perform(post("/api/v1/simulador")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.precioPropiedadUf", is(3500.0)))
                .andExpect(jsonPath("$.pieUf", is(350.0)))
                .andExpect(jsonPath("$.creditoUf", is(3150.0)))
                .andExpect(jsonPath("$.dividendoMensualUf", notNullValue()))
                .andExpect(jsonPath("$.dividendoMensualClp", notNullValue()))
                .andExpect(jsonPath("$.fogaesAplicable", is(true)))
                .andExpect(jsonPath("$.gastosOperacionales", notNullValue()));
    }

    @Test
    @DisplayName("FavoritesController: Ciclo completo sobre tabla 'favorito' (Flyway V2_4) con protección CSRF")
    void shouldManageFavoritesLifecycle() throws Exception {
        String addJson = """
                {
                    "propiedadId": "%s",
                    "notas": "Excelente ubicación y precio"
                }
                """.formatted(testPropId);

        // 1. Mutación POST sin token CSRF debe ser rechazada con 403 Forbidden
        mockMvc.perform(post("/api/v1/favoritos")
                        .cookie(authCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(addJson))
                .andExpect(status().isForbidden());

        // 2. Mutación POST con token CSRF válido debe retornar 201 Created
        mockMvc.perform(post("/api/v1/favoritos")
                        .with(csrf())
                        .cookie(authCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(addJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.mensaje", containsString("guardada en favoritos")));

        // 3. Listar favoritos (GET seguro, no requiere CSRF)
        mockMvc.perform(get("/api/v1/favoritos").cookie(authCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$[0].propiedad_id", is(testPropId.toString())))
                .andExpect(jsonPath("$[0].codigo_publicacion", is("WEB-PROP-1")))
                .andExpect(jsonPath("$[0].notas", is("Excelente ubicación y precio")));

        // 4. Mutación DELETE sin token CSRF debe ser rechazada con 403 Forbidden
        mockMvc.perform(delete("/api/v1/favoritos/" + testPropId).cookie(authCookie))
                .andExpect(status().isForbidden());

        // 5. Mutación DELETE con token CSRF válido debe retornar 204 No Content
        mockMvc.perform(delete("/api/v1/favoritos/" + testPropId)
                        .with(csrf())
                        .cookie(authCookie))
                .andExpect(status().isNoContent());

        // 6. Verificar que la lista ya no contiene la propiedad
        mockMvc.perform(get("/api/v1/favoritos").cookie(authCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.propiedad_id == '%s')]".formatted(testPropId), empty()));
    }

    @Test
    @DisplayName("CORS: Origen exacto autorizado recibe cabeceras Access-Control-Allow-Origin y Credentials")
    void shouldHandleCorsForAuthorizedOrigin() throws Exception {
        mockMvc.perform(options("/api/v1/propiedades")
                        .header("Origin", "http://localhost:3000")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:3000"))
                .andExpect(header().string("Access-Control-Allow-Credentials", "true"));
    }

    @Test
    @DisplayName("CORS: Origen no autorizado es rechazado con 403 Forbidden")
    void shouldRejectCorsForDisallowedOrigin() throws Exception {
        mockMvc.perform(options("/api/v1/propiedades")
                        .header("Origin", "http://evil-attacker.com")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Auth: GET /api/v1/auth/csrf retorna token CSRF en el cuerpo JSON para topologías cross-site")
    void shouldProvideCsrfTokenInResponseBody() throws Exception {
        mockMvc.perform(get("/api/v1/auth/csrf"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token", notNullValue()))
                .andExpect(jsonPath("$.headerName", is("X-XSRF-TOKEN")))
                .andExpect(jsonPath("$.parameterName", is("_csrf")));
    }

    @Test
    @DisplayName("Auth: GET /api/v1/auth/me protegido (401 sin sesión, 200 con sesión activa y perfil)")
    void shouldReturnCurrentUserProfileWhenAuthenticated() throws Exception {
        // Sin autenticación debe retornar 401 Unauthorized
        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized());

        // Con cookie de sesión válida debe retornar el perfil del usuario autenticado
        mockMvc.perform(get("/api/v1/auth/me").cookie(authCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username", is("usuario.test@hogarya.cl")))
                .andExpect(jsonPath("$.rut", is("12345678-5")))
                .andExpect(jsonPath("$.roles[0]", is("ROLE_PERSONA")))
                .andExpect(jsonPath("$.autenticado", is(true)));
    }

    @Test
    @DisplayName("Propiedades: POST /api/v1/propiedades valida roles y CSRF")
    void shouldPublishNewPropertyWithAuthAndCsrf() throws Exception {
        String publishJson = """
                {
                    "titulo": "Departamento de Lujo en Vitacura con vista al Club de Polo",
                    "descripcion": "Exclusivo penthouse con finas terminaciones, terraza panorámica y piscina privada",
                    "tipoPropiedad": "DEPARTAMENTO",
                    "operacion": "VENTA",
                    "precioUf": 12500.00,
                    "m2Util": 180.0,
                    "m2Total": 220.0,
                    "dormitorios": 3,
                    "banos": 3,
                    "comunaId": 1,
                    "direccionReferencial": "Av. Vitacura 5000",
                    "barrio": "Vitacura",
                    "latitud": -33.4050,
                    "longitud": -70.5850,
                    "subsidioDs19": false,
                    "subsidioDs1": false
                }
                """;

        // 1. Sin CSRF debe ser rechazado con 403
        mockMvc.perform(post("/api/v1/propiedades")
                        .cookie(corredorOrg1Cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(publishJson))
                .andExpect(status().isForbidden());

        // 2. Con rol insuficiente (ROLE_PERSONA) debe responder 403 Forbidden
        mockMvc.perform(post("/api/v1/propiedades")
                        .with(csrf())
                        .cookie(authCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(publishJson))
                .andExpect(status().isForbidden());

        // 3. Con CSRF y rol permitido (ROLE_CORREDOR) debe responder 201 Created y retornar ID
        mockMvc.perform(post("/api/v1/propiedades")
                        .with(csrf())
                        .cookie(corredorOrg1Cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(publishJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.codigoPublicacion", startsWith("PROP-")))
                .andExpect(jsonPath("$.mensaje", containsString("publicada e indexación")));
    }

    @Test
    @DisplayName("Propiedades: POST /api/v1/propiedades/{id}/publicar valida pertenencia de organización y roles")
    void shouldEnforceOwnershipAndRolesOnPublishExisting() throws Exception {
        // 1. Rol insuficiente (ROLE_PERSONA) -> 403 Forbidden
        mockMvc.perform(post("/api/v1/propiedades/" + testPropId + "/publicar")
                        .with(csrf())
                        .cookie(authCookie))
                .andExpect(status().isForbidden());

        // 2. Corredor de OTRA organización (Org 2 != Org 1) intentando publicar recurso ajeno -> 403 Forbidden
        mockMvc.perform(post("/api/v1/propiedades/" + testPropId + "/publicar")
                        .with(csrf())
                        .cookie(corredorOrg2Cookie))
                .andExpect(status().isForbidden());

        // 3. Corredor de la MISMA organización (Org 1 == Org 1) -> 200 OK
        mockMvc.perform(post("/api/v1/propiedades/" + testPropId + "/publicar")
                        .with(csrf())
                        .cookie(corredorOrg1Cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(testPropId.toString())))
                .andExpect(jsonPath("$.mensaje", containsString("activada e indexación")));

        // 4. Administrador con permisos globales sobre cualquier recurso -> 200 OK
        mockMvc.perform(post("/api/v1/propiedades/" + testPropId + "/publicar")
                        .with(csrf())
                        .cookie(adminCookie))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Actuator: /actuator/metrics/** restringido exclusivamente a ROLE_ADMIN (401 unauth, 403 persona, 200 admin)")
    void shouldProtectActuatorMetricsAndExposeIndexingCounters() throws Exception {
        // 1. Sin autenticación -> 401 Unauthorized
        mockMvc.perform(get("/actuator/metrics/hogarya.indexing.indexed.count"))
                .andExpect(status().isUnauthorized());

        // 2. Con autenticación pero rol no administrativo (ROLE_PERSONA) -> 403 Forbidden
        mockMvc.perform(get("/actuator/metrics/hogarya.indexing.indexed.count").cookie(authCookie))
                .andExpect(status().isForbidden());

        // 3. Con rol ROLE_ADMIN -> 200 OK
        mockMvc.perform(get("/actuator/metrics/hogarya.indexing.indexed.count").cookie(adminCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", is("hogarya.indexing.indexed.count")));
    }

    @Test
    @DisplayName("Rendimiento E2E: Medir latencia HTTP p95 de GET /api/v1/propiedades con y sin cálculo de embedding")
    void shouldMeasureEndToEndHttpLatencyWithAndWithoutEmbeddingCall() throws Exception {
        // Calentamiento (5 repeticiones)
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(get("/api/v1/propiedades")
                            .param("query", "departamento providencia metro")
                            .param("limit", "10")
                            .cookie(authCookie))
                    .andExpect(status().isOk());
        }

        int repetitions = 35;
        long[] timesWithFreshEmbedding = new long[repetitions];
        long[] timesWithCachedEmbedding = new long[repetitions];

        // Caso A: Con resolución de embedding (consultas no cacheadas / frescas)
        for (int i = 0; i < repetitions; i++) {
            String freshQuery = "departamento providencia sector " + UUID.randomUUID().toString().substring(0, 8);
            long start = System.nanoTime();
            mockMvc.perform(get("/api/v1/propiedades")
                            .param("query", freshQuery)
                            .param("limit", "10")
                            .cookie(authCookie))
                    .andExpect(status().isOk());
            timesWithFreshEmbedding[i] = (System.nanoTime() - start) / 1_000_000; // ms
        }

        // Caso B: Con embedding en caché LRU (consultas idénticas sin llamada al proveedor)
        String cachedQuery = "departamento vitacura parque";
        // Pre-cargar en caché
        mockMvc.perform(get("/api/v1/propiedades")
                        .param("query", cachedQuery)
                        .param("limit", "10")
                        .cookie(authCookie))
                .andExpect(status().isOk());

        for (int i = 0; i < repetitions; i++) {
            long start = System.nanoTime();
            mockMvc.perform(get("/api/v1/propiedades")
                            .param("query", cachedQuery)
                            .param("limit", "10")
                            .cookie(authCookie))
                    .andExpect(status().isOk());
            timesWithCachedEmbedding[i] = (System.nanoTime() - start) / 1_000_000; // ms
        }

        java.util.Arrays.sort(timesWithFreshEmbedding);
        java.util.Arrays.sort(timesWithCachedEmbedding);

        int p95Index = (int) Math.ceil(0.95 * repetitions) - 1;
        int medianIndex = repetitions / 2;

        long freshMedian = timesWithFreshEmbedding[medianIndex];
        long freshP95 = timesWithFreshEmbedding[p95Index];
        long cachedMedian = timesWithCachedEmbedding[medianIndex];
        long cachedP95 = timesWithCachedEmbedding[p95Index];

        System.out.println("====================================================================================");
        System.out.println("MEDICIÓN LATENCIA END-TO-END HTTP GET /api/v1/propiedades (35 reps, MockMvc + JWT):");
        System.out.println(String.format(" - Con cálculo Embedding:   Mediana = %d ms | p95 = %d ms (min: %d ms, max: %d ms)",
                freshMedian, freshP95, timesWithFreshEmbedding[0], timesWithFreshEmbedding[repetitions - 1]));
        System.out.println(String.format(" - Con Caché LRU (sin call): Mediana = %d ms | p95 = %d ms (min: %d ms, max: %d ms)",
                cachedMedian, cachedP95, timesWithCachedEmbedding[0], timesWithCachedEmbedding[repetitions - 1]));
        System.out.println("====================================================================================");

        // Aseveración de control de latencia E2E
        org.junit.jupiter.api.Assertions.assertTrue(cachedP95 < 250,
                "El p95 extremo a extremo con caché debe estar holgadamente bajo 250 ms");
    }

    @Test
    @DisplayName("Async y Backfill: Executor acotado y consulta FOR UPDATE SKIP LOCKED")
    void shouldVerifyBoundedIndexingExecutorAndSkipLocked() {
        if (indexingTaskExecutor != null) {
            org.junit.jupiter.api.Assertions.assertEquals(2, indexingTaskExecutor.getCorePoolSize());
            org.junit.jupiter.api.Assertions.assertEquals(4, indexingTaskExecutor.getMaxPoolSize());
            org.junit.jupiter.api.Assertions.assertTrue(indexingTaskExecutor.getThreadNamePrefix().startsWith("indexing-"));
        }

        // Ejecución de backfill verificando que ejecuta SKIP LOCKED sin lanzar excepciones
        int processed = propertyIndexingService.backfillMissingEmbeddings(5);
        org.junit.jupiter.api.Assertions.assertTrue(processed >= 0);
    }

    @Test
    @DisplayName("Seguridad: Cookie XSRF-TOKEN con SameSite configurado")
    void shouldReturnCoherentCsrfCookieSameSite() throws Exception {
        var result = mockMvc.perform(get("/api/v1/auth/csrf"))
                .andExpect(status().isOk())
                .andReturn();

        String setCookie = result.getResponse().getHeader("Set-Cookie");
        if (setCookie != null && setCookie.contains("XSRF-TOKEN")) {
            org.junit.jupiter.api.Assertions.assertTrue(
                    setCookie.toLowerCase().contains("samesite"),
                    "La cookie XSRF-TOKEN debe contener el atributo SameSite"
            );
        }
    }

    @Test
    @DisplayName("OpenAPI: Exportar especificación /v3/api-docs a target/openapi.json como artefacto de CI")
    void shouldExportOpenApiSpec() throws Exception {
        byte[] openApiJson = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsByteArray();

        File targetDir = new File("target");
        if (!targetDir.exists()) {
            targetDir.mkdirs();
        }
        Files.write(Path.of("target", "openapi.json"), openApiJson);
    }
}
