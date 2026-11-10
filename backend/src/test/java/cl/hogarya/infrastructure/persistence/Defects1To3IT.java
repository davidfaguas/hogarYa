package cl.hogarya.infrastructure.persistence;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pruebas de regresión automatizadas para los Defectos 1, 2 y 3.
 * Metodología: Pruebas primero demostrando la causa raíz del fallo y la solución validada.
 */
@Testcontainers
@DisabledIfSystemProperty(named = "test.docker.skip", matches = "true", disabledReason = "Ejecución local explícita sin Docker (-Dtest.docker.skip=true)")
class Defects1To3IT {

    /**
     * DEFECTO 2:
     * La imagen oficial 'postgis/postgis:16-3.4' NO incluye pgvector.
     * Si se intenta instanciar una imagen base 'postgis/postgis:16-3.4' y ejecutar 'CREATE EXTENSION vector;',
     * PostgreSQL arroja: ERROR: extension "vector" is not available (SQLSTATE 0A000).
     *
     * Solución: Se construye la imagen mediante Dockerfile.test-db agregando 'postgresql-16-pgvector'.
     */
    @SuppressWarnings("resource")
    @Container
    static GenericContainer<?> postgresWithPostgisAndPgvector = new GenericContainer<>(
            new ImageFromDockerfile("hogarya-postgis-pgvector:16-test", false)
                    .withFileFromPath("Dockerfile", Paths.get("Dockerfile.test-db"))
    )
            .withExposedPorts(5432)
            .withEnv("POSTGRES_DB", "hogarya_test")
            .withEnv("POSTGRES_USER", "hogarya_app")
            .withEnv("POSTGRES_PASSWORD", "hogarya_secret");

    private Connection getConnection() throws SQLException {
        String jdbcUrl = "jdbc:postgresql://"
                + postgresWithPostgisAndPgvector.getHost() + ":"
                + postgresWithPostgisAndPgvector.getMappedPort(5432) + "/hogarya_test";
        return DriverManager.getConnection(
                jdbcUrl,
                "hogarya_app",
                "hogarya_secret"
        );
    }

    @Nested
    @DisplayName("Defecto 1: Inmutabilidad de unaccent() en GENERATED ALWAYS AS ... STORED")
    class Defecto1UnaccentImmutabilityTest {

        @Test
        @DisplayName("FALLA ESPERADA: 'unaccent()' nativo es STABLE y falla con 42P17 al usarse en columna generada")
        void shouldFailWhenUsingNativeUnaccentInGeneratedColumn() throws SQLException {
            try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {
                stmt.execute("CREATE EXTENSION IF NOT EXISTS unaccent;");

                // Intento ingenuo: usar unaccent() directamente en GENERATED ALWAYS AS ... STORED
                String faultyDdl = """
                    CREATE TABLE IF NOT EXISTS test_propiedad_falla_unaccent (
                        id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                        titulo VARCHAR(255) NOT NULL,
                        search_vector tsvector GENERATED ALWAYS AS (
                            to_tsvector('spanish', unaccent(titulo))
                        ) STORED
                    );
                """;

                SQLException exception = assertThrows(SQLException.class, () -> stmt.execute(faultyDdl),
                        "Debe fallar porque unaccent() tiene volatilidad STABLE en pg_proc");

                // SQLSTATE 42P17 = generation_expression_is_not_immutable
                assertEquals("42P17", exception.getSQLState(),
                        "El código de error debe ser 42P17 (generation expression is not immutable)");
                assertTrue(exception.getMessage().contains("generation expression is not immutable"),
                        "El mensaje debe indicar explícitamente que la expresión no es inmutable");
            }
        }

        @Test
        @DisplayName("CORRECCIÓN EXITOSA: Con immutable_unaccent() la columna generada compila y actualiza el tsvector")
        void shouldSucceedWhenUsingImmutableUnaccentWrapper() throws SQLException {
            try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {
                stmt.execute("CREATE EXTENSION IF NOT EXISTS unaccent;");

                // Creamos la función wrapper declarada IMMUTABLE y con esquema fijado
                stmt.execute("""
                    CREATE OR REPLACE FUNCTION public.immutable_unaccent(text)
                    RETURNS text AS $$
                        SELECT public.unaccent('public.unaccent', $1);
                    $$ LANGUAGE sql IMMUTABLE STRICT PARALLEL SAFE;
                """);

                // DDL corregido utilizando public.immutable_unaccent()
                stmt.execute("""
                    CREATE TABLE IF NOT EXISTS test_propiedad_corregida_unaccent (
                        id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                        titulo VARCHAR(255) NOT NULL,
                        search_vector tsvector GENERATED ALWAYS AS (
                            to_tsvector('spanish', public.immutable_unaccent(coalesce(titulo, '')))
                        ) STORED
                    );
                """);

                // Inserción de prueba con caracteres especiales en español
                stmt.execute("""
                    INSERT INTO test_propiedad_corregida_unaccent (titulo)
                    VALUES ('Amplio departamento en Ñuñoa con calefacción central');
                """);

                // Verificamos que el vector se generó sin tildes ni eñes transformadas a la raíz canónica
                try (ResultSet rs = stmt.executeQuery("""
                    SELECT search_vector @@ to_tsquery('spanish', public.immutable_unaccent('Ñuñoa & calefaccion')) AS matches
                    FROM test_propiedad_corregida_unaccent;
                """)) {
                    assertTrue(rs.next());
                    assertTrue(rs.getBoolean("matches"), "La búsqueda de texto debe coincidir ignorando tildes y eñes");
                }
            }
        }
    }

    @Nested
    @DisplayName("Defecto 2: Extensión pgvector disponible en entorno de base de datos")
    class Defecto2PgvectorExtensionTest {

        @Test
        @DisplayName("CORRECCIÓN EXITOSA: La extensión 'vector' existe e interactúa con PostGIS sin conflicto")
        void shouldVerifyPostgisAndPgvectorCoexist() throws SQLException {
            try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {
                // Habilitamos ambas extensiones
                stmt.execute("CREATE EXTENSION IF NOT EXISTS postgis;");
                stmt.execute("CREATE EXTENSION IF NOT EXISTS vector;");

                // Verificamos versión de postgis
                try (ResultSet rs = stmt.executeQuery("SELECT PostGIS_Version();")) {
                    assertTrue(rs.next());
                    String postgisVersion = rs.getString(1);
                    assertNotNull(postgisVersion);
                    assertTrue(postgisVersion.startsWith("3."));
                }

                // Verificamos tipo de dato vector y función de distancia coseno (<=>)
                try (ResultSet rs = stmt.executeQuery("""
                    SELECT ('[1.0, 0.0, 0.0]'::vector(3) <=> '[0.0, 1.0, 0.0]'::vector(3)) AS cosine_dist;
                """)) {
                    assertTrue(rs.next());
                    double dist = rs.getDouble("cosine_dist");
                    assertEquals(1.0, dist, 0.0001, "La distancia coseno entre vectores ortogonales debe ser 1.0");
                }
            }
        }
    }

    @Nested
    @DisplayName("Defecto 3: Consulta de valor UF no debe seleccionar fechas futuras")
    class Defecto3UfDateConditionTest {

        @Test
        @DisplayName("FALLA ESPERADA: 'ORDER BY fecha DESC LIMIT 1' sin filtro selecciona UF de días futuros prepublicados")
        void shouldFailWhenSelectingLatestUfWithoutDateUpperCap() throws SQLException {
            try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {
                stmt.execute("""
                    CREATE TABLE IF NOT EXISTS valor_uf (
                        fecha DATE PRIMARY KEY,
                        valor NUMERIC(10, 2) NOT NULL CHECK (valor > 0),
                        publicado_el TIMESTAMPTZ NOT NULL DEFAULT NOW(),
                        fuente VARCHAR(100) NOT NULL DEFAULT 'Banco Central de Chile - BDE'
                    );
                """);
                stmt.execute("TRUNCATE TABLE valor_uf;");

                LocalDate today = LocalDate.now();
                LocalDate yesterday = today.minusDays(1);
                LocalDate futureDate = today.plusDays(7); // Simula el ciclo del 10 al 9 publicado por CMF

                stmt.execute(String.format("INSERT INTO valor_uf (fecha, valor, fuente) VALUES ('%s', 37800.00, 'Banco Central');", yesterday));
                stmt.execute(String.format("INSERT INTO valor_uf (fecha, valor, fuente) VALUES ('%s', 37850.00, 'Banco Central');", today));
                stmt.execute(String.format("INSERT INTO valor_uf (fecha, valor, fuente) VALUES ('%s', 37990.00, 'Banco Central');", futureDate));

                // Consulta defectuosa que solo toma el valor más reciente absoluto
                try (ResultSet rs = stmt.executeQuery("SELECT fecha, valor FROM valor_uf ORDER BY fecha DESC LIMIT 1;")) {
                    assertTrue(rs.next());
                    LocalDate selectedDate = rs.getDate("fecha").toLocalDate();
                    assertEquals(futureDate, selectedDate,
                            "La consulta sin 'fecha <= CURRENT_DATE' toma erróneamente la fecha futura publicada por CMF");
                }
            }
        }

        @Test
        @DisplayName("CORRECCIÓN EXITOSA: Con 'WHERE fecha <= CURRENT_DATE ORDER BY fecha DESC LIMIT 1' retorna la UF legal de hoy")
        void shouldSucceedWhenRestrictingUfToCurrentDateOrPast() throws SQLException {
            try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {
                LocalDate today = LocalDate.now();

                // Consulta corregida utilizada en PropertySearchMapper.xml y FinancialService
                String correctedQuery = """
                    SELECT fecha, valor
                    FROM valor_uf
                    WHERE fecha <= CURRENT_DATE
                    ORDER BY fecha DESC
                    LIMIT 1;
                """;

                try (ResultSet rs = stmt.executeQuery(correctedQuery)) {
                    assertTrue(rs.next());
                    LocalDate selectedDate = rs.getDate("fecha").toLocalDate();
                    double valor = rs.getDouble("valor");

                    assertEquals(today, selectedDate, "Debe seleccionar la fecha de hoy y jamás una fecha futura");
                    assertEquals(37850.00, valor, 0.001, "El valor debe corresponder a la UF vigente a la fecha");
                }
            }
        }
    }
}
