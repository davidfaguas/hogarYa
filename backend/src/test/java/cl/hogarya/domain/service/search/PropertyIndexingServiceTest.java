package cl.hogarya.domain.service.search;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.client.HttpClientErrorException;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("Servicio de Indexación: Vectorización con RETRIEVAL_DOCUMENT y métricas")
class PropertyIndexingServiceTest {

    private EmbeddingProvider mockEmbeddingProvider;
    private JdbcClient mockJdbcClient;
    private PropertyIndexingService indexingService;

    @BeforeEach
    void setUp() {
        mockEmbeddingProvider = Mockito.mock(EmbeddingProvider.class);
        mockJdbcClient = Mockito.mock(JdbcClient.class, Mockito.RETURNS_DEEP_STUBS);
        when(mockEmbeddingProvider.getDimensions()).thenReturn(768);

        indexingService = new PropertyIndexingService(mockEmbeddingProvider, mockJdbcClient);
    }

    @Test
    @DisplayName("indexProperty: Embebe con RETRIEVAL_DOCUMENT y actualiza propiedad.embedding")
    void shouldIndexPropertyWithRetrievalDocumentTaskType() {
        UUID propId = UUID.randomUUID();
        Map<String, Object> row = Map.of(
                "titulo", "Casa en Las Condes con amplio jardín",
                "tipo_propiedad", "CASA",
                "operacion", "VENTA",
                "comuna_nombre", "Las Condes",
                "barrio", "San Carlos de Apoquindo",
                "descripcion", "Maravillosa casa familiar"
        );

        when(mockJdbcClient.sql(contains("FROM propiedad p"))
                .param("id", propId)
                .query(any(org.springframework.jdbc.core.ColumnMapRowMapper.class))
                .optional())
                .thenReturn(Optional.of(row));

        float[] mockVec = new float[768];
        mockVec[0] = 0.5f;
        when(mockEmbeddingProvider.generateEmbedding(anyString(), eq(GeminiEmbeddingProvider.TaskType.RETRIEVAL_DOCUMENT)))
                .thenReturn(mockVec);

        when(mockJdbcClient.sql(contains("UPDATE propiedad"))
                .param(eq("vec"), anyString())
                .param(eq("id"), eq(propId))
                .update())
                .thenReturn(1);

        boolean success = indexingService.indexProperty(propId);

        assertTrue(success, "La indexación debe ser exitosa");
        verify(mockEmbeddingProvider, times(1)).generateEmbedding(
                contains("Casa en Las Condes"),
                eq(GeminiEmbeddingProvider.TaskType.RETRIEVAL_DOCUMENT)
        );

        PropertyIndexingService.IndexingMetrics metrics = indexingService.getMetrics();
        assertEquals(1, metrics.indexedCount());
        assertEquals(0, metrics.failureCount());
        assertEquals(0, metrics.rateLimit429Count());
    }

    @Test
    @DisplayName("indexProperty: Captura y registra métrica de error HTTP 429 Rate Limit")
    void shouldCaptureAndCountRateLimit429Errors() {
        UUID propId = UUID.randomUUID();
        Map<String, Object> row = Map.of(
                "titulo", "Departamento en Providencia",
                "tipo_propiedad", "DEPARTAMENTO",
                "operacion", "VENTA"
        );

        when(mockJdbcClient.sql(contains("FROM propiedad p"))
                .param("id", propId)
                .query(any(org.springframework.jdbc.core.ColumnMapRowMapper.class))
                .optional())
                .thenReturn(Optional.of(row));

        HttpClientErrorException tooManyRequests = HttpClientErrorException.create(
                HttpStatusCode.valueOf(429),
                "Too Many Requests",
                HttpHeaders.EMPTY,
                "{\"error\":{\"code\":429,\"message\":\"RESOURCE_EXHAUSTED\"}}".getBytes(StandardCharsets.UTF_8),
                StandardCharsets.UTF_8
        );

        when(mockEmbeddingProvider.generateEmbedding(anyString(), eq(GeminiEmbeddingProvider.TaskType.RETRIEVAL_DOCUMENT)))
                .thenThrow(tooManyRequests);

        boolean success = indexingService.indexProperty(propId);

        assertFalse(success, "Debe retornar false ante error de rate limit");
        PropertyIndexingService.IndexingMetrics metrics = indexingService.getMetrics();
        assertEquals(0, metrics.indexedCount());
        assertEquals(1, metrics.failureCount());
        assertEquals(1, metrics.rateLimit429Count(), "Debe incrementar el contador específico de 429");
    }

    @Test
    @DisplayName("Micrometer: Registra e incrementa métricas en MeterRegistry")
    void shouldRegisterAndIncrementMicrometerCounters() {
        MeterRegistry registry = new SimpleMeterRegistry();
        PropertyIndexingService serviceWithMeter = new PropertyIndexingService(mockEmbeddingProvider, mockJdbcClient, registry);

        UUID propId = UUID.randomUUID();
        Map<String, Object> row = Map.of(
                "titulo", "Departamento Vitacura",
                "tipo_propiedad", "DEPARTAMENTO",
                "operacion", "VENTA"
        );

        when(mockJdbcClient.sql(contains("FROM propiedad p"))
                .param("id", propId)
                .query(any(org.springframework.jdbc.core.ColumnMapRowMapper.class))
                .optional())
                .thenReturn(Optional.of(row));

        float[] mockVec = new float[768];
        when(mockEmbeddingProvider.generateEmbedding(anyString(), any())).thenReturn(mockVec);

        when(mockJdbcClient.sql(contains("UPDATE propiedad"))
                .param(eq("vec"), anyString())
                .param(eq("id"), eq(propId))
                .update())
                .thenReturn(1);

        boolean success = serviceWithMeter.indexProperty(propId);
        assertTrue(success);

        assertEquals(1.0, registry.get("hogarya.indexing.indexed.count").counter().count());
        assertEquals(0.0, registry.get("hogarya.indexing.failure.count").counter().count());
        assertEquals(0.0, registry.get("hogarya.indexing.ratelimit.429.count").counter().count());
    }
}
