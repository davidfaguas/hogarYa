package cl.hogarya.infrastructure.persistence;

import cl.hogarya.domain.model.search.PropertySearchCriteria;
import cl.hogarya.domain.model.search.PropertySearchResultDto;
import cl.hogarya.domain.service.search.PropertySearchService;
import cl.hogarya.infrastructure.persistence.mapper.PropertySearchMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.file.Paths;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test de Integración Integral con @SpringBootTest y Testcontainers.
 * - Levanta el contexto completo de Spring Boot contra Dockerfile.test-db (PostgreSQL 16 + PostGIS + pgvector).
 * - Ejecuta las migraciones Flyway oficiales (V1, V2_1, V2_2, V2_3).
 * - Ejecuta el SQL REAL del mapper PropertySearchMapper (searchHybrid y countByCriteria).
 * - Valida:
 *   1. Múltiples estaciones de Metro cercanas dentro del radio NO duplican la propiedad (filtro EXISTS).
 *   2. Coherencia estricta total == páginas (countByCriteria coincide con el recuento paginado).
 *   3. Búsqueda semántica/textual real sobre tsvector generado con immutable_unaccent y trigramas pg_trgm.
 * - Regla de CI: Falla si Docker no está activo, a menos que se invoque con -Dtest.docker.skip=true explícito.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers
@DisabledIfSystemProperty(named = "test.docker.skip", matches = "true", disabledReason = "Ejecución local explícita sin Docker (-Dtest.docker.skip=true)")
@DisplayName("Defectos 4, 5, 6 y 10: Integración real de PropertySearchMapper contra PostgreSQL 16 + PostGIS + pgvector")
class PropertySearchMapperIntegrationIT {

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
    private PropertySearchService propertySearchService;

    @Autowired
    private PropertySearchMapper propertySearchMapper;

    @Autowired
    private JdbcClient jdbcClient;

    @Autowired
    private cl.hogarya.domain.service.search.EmbeddingProvider embeddingProvider;

    private UUID orgId;

    private String formatVector(float[] vec) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < vec.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(vec[i]);
        }
        return sb.toString();
    }

    @BeforeEach
    void setUpTestData() {
        orgId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        jdbcClient.sql("""
            INSERT INTO organizacion (id, rut_empresa, razon_social, nombre_fantasia, tipo, telefono_contacto, whatsapp_contacto, email_contacto)
            VALUES (:id, '76123456-0', 'Inmobiliaria Test SpA', 'HogarYa Test', 'INMOBILIARIA', '+56911111111', '+56911111111', 'contacto@hogarya.cl')
            ON CONFLICT (id) DO NOTHING
            """)
            .param("id", orgId)
            .update();

        // Limpiar propiedades previas
        jdbcClient.sql("DELETE FROM propiedad").update();

        // Insertar estaciones de metro para la prueba de proximidad múltiple
        // Estación 1: Bellavista de La Florida (-70.5986, -33.5222)
        // Estación 2: Vicente Valdés (-70.5978, -33.5278) (están a ~600m entre sí)
        jdbcClient.sql("""
            INSERT INTO estacion_metro (id, red, linea, nombre, comuna_id, ubicacion, fuente_datos, fuente_verificada, activo)
            VALUES 
            (101, 'Metro de Santiago', 'L4', 'Test Metro A', 1, ST_SetSRID(ST_MakePoint(-70.5986, -33.5222), 4326)::geography, 'TEST', TRUE, TRUE),
            (102, 'Metro de Santiago', 'L4', 'Test Metro B', 1, ST_SetSRID(ST_MakePoint(-70.5978, -33.5278), 4326)::geography, 'TEST', TRUE, TRUE)
            ON CONFLICT (id) DO NOTHING
            """).update();

        // Propiedad P1: Ubicada exactamente entre ambas estaciones (a menos de 400m de ambas)
        // Coordenadas: (-70.5982, -33.5250)
        jdbcClient.sql("""
            INSERT INTO propiedad (
                id, codigo_publicacion, organizacion_id, titulo, descripcion, tipo_propiedad, 
                operacion, estado, precio_uf, m2_util, m2_total, dormitorios, banos, 
                comuna_id, direccion_referencial, barrio, ubicacion, activo
            ) VALUES (
                '11111111-1111-1111-1111-111111111111'::uuid, 'TEST-PROP-1', :orgId,
                'Hermoso departamento con piscina y quincho cerca del metro',
                'Departamento luminoso excelente conectividad cercano a metro Vicente Valdés',
                'DEPARTAMENTO', 'VENTA', 'NUEVA', 3500.00, 75.0, 85.0, 2, 2, 
                1, 'Av. Vicuña Mackenna 7000', 'Bellavista', 
                ST_SetSRID(ST_MakePoint(-70.5982, -33.5250), 4326)::geography, TRUE
            )
            """).param("orgId", orgId).update();

        // Propiedad P2: Casa en Las Condes lejana al metro
        jdbcClient.sql("""
            INSERT INTO propiedad (
                id, codigo_publicacion, organizacion_id, titulo, descripcion, tipo_propiedad, 
                operacion, estado, precio_uf, m2_util, m2_total, dormitorios, banos, 
                comuna_id, direccion_referencial, barrio, ubicacion, activo
            ) VALUES (
                '22222222-2222-2222-2222-222222222222'::uuid, 'TEST-PROP-2', :orgId,
                'Moderna casa familiar en San Carlos de Apoquindo',
                'Amplia casa con jardín privado y quincho sin metro cercano',
                'CASA', 'VENTA', 'USADA', 9800.00, 180.0, 320.0, 4, 3, 
                4, 'Camino El Alba 12000', 'San Carlos', 
                ST_SetSRID(ST_MakePoint(-70.5050, -33.3950), 4326)::geography, TRUE
            )
            """).param("orgId", orgId).update();

        // Asignar embeddings generados por EmbeddingProvider a P1 y P2
        float[] p1Vec = embeddingProvider.generateEmbedding("Hermoso departamento con piscina y quincho cerca del metro");
        float[] p2Vec = embeddingProvider.generateEmbedding("Moderna casa familiar en San Carlos de Apoquindo");
        jdbcClient.sql("UPDATE propiedad SET embedding = ('[' || :vec || ']')::vector WHERE id = '11111111-1111-1111-1111-111111111111'::uuid")
                .param("vec", formatVector(p1Vec)).update();
        jdbcClient.sql("UPDATE propiedad SET embedding = ('[' || :vec || ']')::vector WHERE id = '22222222-2222-2222-2222-222222222222'::uuid")
                .param("vec", formatVector(p2Vec)).update();
    }

    @Test
    @DisplayName("Defecto 6: Múltiples estaciones de metro dentro del radio NO duplican filas (EXISTS)")
    void shouldNotDuplicatePropertiesWhenNearMultipleMetroStations() {
        PropertySearchCriteria criteria = new PropertySearchCriteria(
                "VENTA", null, null, null, null, null, null, null, null,
                true, // cercaMetro = true
                1000, // radio 1000m (P1 está a menos de 500m de Metro A y Metro B)
                null, "relevancia"
        );

        List<PropertySearchResultDto> results = propertySearchService.search(criteria, null, 10, 0);

        // Debe retornar exactamente 1 resultado, sin duplicación
        assertEquals(1, results.size(), "La propiedad no debe multiplicarse aunque esté cerca de dos estaciones");
        assertEquals("TEST-PROP-1", results.get(0).codigoPublicacion());
        assertNotNull(results.get(0).nombreEstacionCercana());
    }

    @Test
    @DisplayName("Defecto 6: Coherencia total entre countByCriteria y resultados paginados")
    void shouldPreserveConsistencyBetweenCountAndPagination() {
        PropertySearchCriteria criteria = new PropertySearchCriteria(
                "VENTA", null, null, null, null, null, null, null, null,
                null, null, null, "relevancia"
        );

        long totalCount = propertySearchService.count(criteria);
        assertEquals(2, totalCount, "Deben existir 2 propiedades activas");

        // Paginación: tamaño de página 1
        List<PropertySearchResultDto> page1 = propertySearchService.search(criteria, null, 1, 0);
        List<PropertySearchResultDto> page2 = propertySearchService.search(criteria, null, 1, 1);

        assertEquals(1, page1.size());
        assertEquals(1, page2.size());
        assertNotEquals(page1.get(0).id(), page2.get(0).id(), "Las páginas deben contener elementos distintos");
        assertEquals(totalCount, page1.size() + page2.size(), "La suma de páginas debe igualar el total de countByCriteria");
    }

    @Test
    @DisplayName("Defecto 5: Búsqueda textual con tsvector + immutable_unaccent + pg_trgm")
    void shouldMatchUsingTextSearchAndTrigram() {
        PropertySearchCriteria criteria = new PropertySearchCriteria(
                "VENTA", null, null, null, null, null, null, null, null,
                null, null,
                "piscina conectividad", // Consulta que debe coincidir con P1
                "relevancia"
        );

        List<PropertySearchResultDto> results = propertySearchService.search(criteria, null, 10, 0);

        assertFalse(results.isEmpty(), "Debe encontrar resultados para texto 'piscina conectividad'");
        assertEquals("TEST-PROP-1", results.get(0).codigoPublicacion());
    }

    @Test
    @DisplayName("Pgvector: Búsqueda vectorial real de extremo a extremo usando embedding de consulta")
    void shouldExecuteVectorSearchWithRealEmbeddingToPgVector() {
        // Generar vector real mediante EmbeddingProvider
        float[] queryVector = embeddingProvider.generateEmbedding("piscina y quincho departamento luminoso");

        PropertySearchCriteria criteria = new PropertySearchCriteria(
                "VENTA", null, null, null, null, null, null, null, null,
                null, null, null, "relevancia"
        );

        List<PropertySearchResultDto> results = propertySearchService.search(criteria, queryVector, 10, 0);

        assertFalse(results.isEmpty(), "Debe encontrar resultados en pgvector");
        // El vector de P1 está más cercano semánticamente a 'piscina y quincho departamento luminoso'
        assertEquals("TEST-PROP-1", results.get(0).codigoPublicacion());
        assertTrue(results.get(0).rrfScore() > 0.0, "El rrf_score debe ser mayor a 0");
    }

    @Test
    @DisplayName("RRF & Metro: Desempate determinista por precio_uf ASC cuando rrf_score es idéntico con metro_detalle")
    void shouldBreakTieByLowestPriceWhenRrfScoreIsEqualAndRetainMetroDetail() {
        // Insertar dos propiedades con texto idéntico y mismo vector para garantizar empate estricto de RRF
        float[] tieVec = embeddingProvider.generateEmbedding("propiedad para test de desempate idéntico");
        String vecStr = formatVector(tieVec);

        // Propiedad Barata (3100 UF)
        jdbcClient.sql("""
            INSERT INTO propiedad (
                id, codigo_publicacion, organizacion_id, titulo, descripcion, tipo_propiedad, 
                operacion, estado, precio_uf, m2_util, m2_total, dormitorios, banos, 
                comuna_id, direccion_referencial, barrio, ubicacion, activo, embedding
            ) VALUES (
                '33333333-3333-3333-3333-333333333333'::uuid, 'TIE-CHEAP', :orgId,
                'Empate departamento identico con jardin', 'Misma descripcion para ambos inmuebles',
                'DEPARTAMENTO', 'VENTA', 'NUEVA', 3100.00, 60.0, 70.0, 2, 1, 
                1, 'Av. Test 100', 'Bellavista', 
                ST_SetSRID(ST_MakePoint(-70.5986, -33.5222), 4326)::geography, TRUE,
                ('[' || :vec || ']')::vector
            )
            """).param("orgId", orgId).param("vec", vecStr).update();

        // Propiedad Cara (4900 UF)
        jdbcClient.sql("""
            INSERT INTO propiedad (
                id, codigo_publicacion, organizacion_id, titulo, descripcion, tipo_propiedad, 
                operacion, estado, precio_uf, m2_util, m2_total, dormitorios, banos, 
                comuna_id, direccion_referencial, barrio, ubicacion, activo, embedding
            ) VALUES (
                '44444444-4444-4444-4444-444444444444'::uuid, 'TIE-EXPENSIVE', :orgId,
                'Empate departamento identico con jardin', 'Misma descripcion para ambos inmuebles',
                'DEPARTAMENTO', 'VENTA', 'NUEVA', 4900.00, 60.0, 70.0, 2, 1, 
                1, 'Av. Test 200', 'Bellavista', 
                ST_SetSRID(ST_MakePoint(-70.5986, -33.5222), 4326)::geography, TRUE,
                ('[' || :vec || ']')::vector
            )
            """).param("orgId", orgId).param("vec", vecStr).update();

        PropertySearchCriteria criteria = new PropertySearchCriteria(
                "VENTA", "DEPARTAMENTO", (short) 1, null, null, null, null, null, null,
                null, null, "Empate departamento identico", "relevancia"
        );

        List<PropertySearchResultDto> results = propertySearchService.search(criteria, tieVec, 10, 0);

        assertTrue(results.size() >= 2, "Debe retornar al menos las 2 propiedades empatadas");
        PropertySearchResultDto first = results.get(0);
        PropertySearchResultDto second = results.get(1);

        // Validar empate de rrf_score (precisión 1e-5)
        assertEquals(first.rrfScore(), second.rrfScore(), 0.00001, "Ambas propiedades deben tener RRF score idéntico");

        // Validar desempate determinista por precio_uf ASC: la más barata (3100 UF) aparece primero
        assertEquals("TIE-CHEAP", first.codigoPublicacion(), "La propiedad de menor precio (3100 UF) debe desempatar primero");
        assertEquals("TIE-EXPENSIVE", second.codigoPublicacion(), "La propiedad de mayor precio (4900 UF) debe ir en segundo lugar");

        // Validar asociación correcta de metro_detalle para ambas
        assertNotNull(first.nombreEstacionCercana(), "La primera propiedad debe tener metro_detalle poblado");
        assertNotNull(second.nombreEstacionCercana(), "La segunda propiedad debe tener metro_detalle poblado");
        assertNotNull(first.distanciaMetroMetros(), "La primera propiedad debe tener distancia de metro calculada");
        assertNotNull(second.distanciaMetroMetros(), "La segunda propiedad debe tener distancia de metro calculada");
        assertEquals(first.nombreEstacionCercana(), second.nombreEstacionCercana(), "Ambas propiedades deben asociar la misma estación de metro más cercana");
        assertEquals(first.distanciaMetroMetros(), second.distanciaMetroMetros(), 0.01, "La distancia a la estación de metro debe ser idéntica");
    }
}
