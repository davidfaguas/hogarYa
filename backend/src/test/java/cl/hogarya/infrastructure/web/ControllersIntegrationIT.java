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

    @BeforeEach
    void setUpTestData() {
        UUID orgId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        userId = UUID.fromString("00000000-0000-0000-0000-000000000099");
        testPropId = UUID.fromString("55555555-5555-5555-5555-555555555555");

        jdbcClient.sql("""
            INSERT INTO organizacion (id, rut_empresa, razon_social, nombre_fantasia, tipo, telefono_contacto, whatsapp_contacto, email_contacto)
            VALUES (:id, '76123456-0', 'Inmobiliaria Web Test SpA', 'HogarYa Web', 'INMOBILIARIA', '+56911111111', '+56911111111', 'contacto@hogarya.cl')
            ON CONFLICT (id) DO NOTHING
            """).param("id", orgId).update();

        jdbcClient.sql("""
            INSERT INTO usuario (id, rut, email, password_hash, nombres, apellidos, rol, organizacion_id, activo)
            VALUES (:id, '12345678-5', 'usuario.test@hogarya.cl', :hash, 'Juan', 'Perez', 'ROLE_PERSONA', :orgId, true)
            ON CONFLICT (id) DO UPDATE SET password_hash = EXCLUDED.password_hash
            """)
            .param("id", userId)
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

        // Generar cookie JWT válida para usuario autenticado
        String token = jwtTokenService.generateToken("usuario.test@hogarya.cl", "12345678-5", List.of("ROLE_PERSONA"));
        authCookie = new Cookie("HOGARYA_SESSION", token);
        authCookie.setHttpOnly(true);
        authCookie.setPath("/");
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
    @DisplayName("Propiedades: POST /api/v1/propiedades publica propiedad con CSRF y dispara indexación")
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
                        .cookie(authCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(publishJson))
                .andExpect(status().isForbidden());

        // 2. Con CSRF y Auth debe responder 201 Created y retornar ID
        mockMvc.perform(post("/api/v1/propiedades")
                        .with(csrf())
                        .cookie(authCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(publishJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.codigoPublicacion", startsWith("PROP-")))
                .andExpect(jsonPath("$.mensaje", containsString("publicada e indexación")));
    }

    @Test
    @DisplayName("Actuator: Métricas protegidas de indexación (401/403 sin auth, 200 con auth)")
    void shouldProtectActuatorMetricsAndExposeIndexingCounters() throws Exception {
        // Sin autenticación debe ser rechazado
        mockMvc.perform(get("/actuator/metrics/hogarya.indexing.indexed.count"))
                .andExpect(status().isUnauthorized());

        // Con autenticación debe retornar la métrica registrada en Micrometer
        mockMvc.perform(get("/actuator/metrics/hogarya.indexing.indexed.count").cookie(authCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", is("hogarya.indexing.indexed.count")));
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
