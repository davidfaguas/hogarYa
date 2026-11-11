package cl.hogarya.domain.service.search;

import cl.hogarya.domain.model.search.PropertySearchCriteria;
import cl.hogarya.infrastructure.persistence.mapper.PropertySearchMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("Búsqueda Semántica y HNSW: Verificación de versión y EmbeddingProvider")
class PropertySearchServiceTest {

    @Test
    @DisplayName("isVersionAtLeast compara semver correctamente")
    void shouldCompareVersionsCorrectly() {
        assertTrue(PropertySearchService.isVersionAtLeast("0.8.0", "0.8.0"));
        assertTrue(PropertySearchService.isVersionAtLeast("0.8.1", "0.8.0"));
        assertTrue(PropertySearchService.isVersionAtLeast("0.9.0", "0.8.0"));
        assertTrue(PropertySearchService.isVersionAtLeast("1.0.0", "0.8.0"));

        assertFalse(PropertySearchService.isVersionAtLeast("0.7.4", "0.8.0"));
        assertFalse(PropertySearchService.isVersionAtLeast("0.7.0", "0.8.0"));
        assertFalse(PropertySearchService.isVersionAtLeast("0.6.2", "0.8.0"));
    }

    @Test
    @DisplayName("EmbeddingProvider fake genera un vector unitario normalizado de 768 dimensiones")
    void shouldGenerateNormalizedEmbeddingOf768Dimensions() {
        EmbeddingProvider provider = new FakeEmbeddingProvider();
        assertEquals(768, provider.getDimensions());

        float[] embedding = provider.generateEmbedding("departamento luminoso con piscina en Ñuñoa");
        assertNotNull(embedding);
        assertEquals(768, embedding.length);

        // Verificar norma unitaria (L2 ~ 1.0)
        double sumSquares = 0.0;
        for (float f : embedding) {
            sumSquares += f * f;
        }
        assertEquals(1.0, Math.sqrt(sumSquares), 0.001, "El embedding debe estar normalizado L2");
    }

    @Test
    @DisplayName("Búsqueda con texto libre resuelve automáticamente el vector con EmbeddingProvider sin pasar float[] a mano")
    void shouldResolveEmbeddingAutomaticallyFromFreeText() {
        PropertySearchMapper mockMapper = mock(PropertySearchMapper.class);
        JdbcClient mockJdbcClient = mock(JdbcClient.class, RETURNS_DEEP_STUBS);
        EmbeddingProvider spyProvider = spy(new FakeEmbeddingProvider());

        when(mockJdbcClient.sql(anyString()).update()).thenReturn(1);
        when(mockMapper.searchHybrid(any(), any(), anyInt(), anyInt(), anyInt()))
                .thenReturn(Collections.emptyList());

        PropertySearchService service = new PropertySearchService(mockMapper, mockJdbcClient, spyProvider);

        PropertySearchCriteria criteria = new PropertySearchCriteria(
                "VENTA",
                "CASA",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                "casa en venta con piscina cerca de metro",
                "relevancia"
        );

        // Invocación SIN pasar float[] manualmente
        service.search(criteria, 10, 0);

        // Verificar que el servicio invocó generateEmbedding con el texto libre
        verify(spyProvider, times(1)).generateEmbedding("casa en venta con piscina cerca de metro");

        // Capturar los argumentos enviados al mapper
        ArgumentCaptor<float[]> vectorCaptor = ArgumentCaptor.forClass(float[].class);
        verify(mockMapper).searchHybrid(eq(criteria), vectorCaptor.capture(), eq(200), eq(10), eq(0));

        float[] capturedVector = vectorCaptor.getValue();
        assertNotNull(capturedVector, "El mapper debe recibir el vector calculado automáticamente");
        assertEquals(768, capturedVector.length);
    }

    @Test
    @DisplayName("Caché LRU: Reutiliza embedding en consultas repetidas y registra métricas")
    void shouldUseLruCacheForQueryEmbeddings() {
        PropertySearchMapper mockMapper = mock(PropertySearchMapper.class);
        JdbcClient mockJdbcClient = mock(JdbcClient.class, RETURNS_DEEP_STUBS);
        EmbeddingProvider spyProvider = spy(new FakeEmbeddingProvider());
        io.micrometer.core.instrument.simple.SimpleMeterRegistry meterRegistry = new io.micrometer.core.instrument.simple.SimpleMeterRegistry();

        when(mockJdbcClient.sql(anyString()).update()).thenReturn(1);
        when(mockMapper.searchHybrid(any(), any(), anyInt(), anyInt(), anyInt()))
                .thenReturn(Collections.emptyList());

        PropertySearchService service = new PropertySearchService(
                mockMapper, mockJdbcClient, spyProvider, null, meterRegistry, 300L
        );

        PropertySearchCriteria criteria = new PropertySearchCriteria(
                "VENTA", "DEPARTAMENTO", null, null, null, null, null, null, null, null, null,
                "departamento en providencia", "relevancia"
        );

        // 1ª búsqueda -> Cache Miss
        service.search(criteria, 10, 0);
        // 2ª búsqueda idéntica -> Cache Hit
        service.search(criteria, 10, 0);

        // El proveedor sólo debió ser invocado 1 vez
        verify(spyProvider, times(1)).generateEmbedding("departamento en providencia");

        var metrics = service.getEmbeddingMetrics();
        assertEquals(1, metrics.cacheMissCount());
        assertEquals(1, metrics.cacheHitCount());
        assertEquals(1.0, meterRegistry.get("hogarya.search.embedding.cache.hit").counter().count());
        assertEquals(1.0, meterRegistry.get("hogarya.search.embedding.cache.miss").counter().count());
    }

    @Test
    @DisplayName("Timeout y Fallback: Retorna null y hace fallback a texto si el proveedor excede el timeout")
    void shouldFallbackToTextOnlyOnTimeout() {
        PropertySearchMapper mockMapper = mock(PropertySearchMapper.class);
        JdbcClient mockJdbcClient = mock(JdbcClient.class, RETURNS_DEEP_STUBS);
        EmbeddingProvider slowProvider = mock(EmbeddingProvider.class);
        io.micrometer.core.instrument.simple.SimpleMeterRegistry meterRegistry = new io.micrometer.core.instrument.simple.SimpleMeterRegistry();

        when(mockJdbcClient.sql(anyString()).update()).thenReturn(1);
        when(slowProvider.generateEmbedding(anyString())).thenThrow(
                new org.springframework.web.client.ResourceAccessException("Read timed out", new java.net.SocketTimeoutException("Read timed out"))
        );

        PropertySearchService service = new PropertySearchService(
                mockMapper, mockJdbcClient, slowProvider, null, meterRegistry
        );

        PropertySearchCriteria criteria = new PropertySearchCriteria(
                "VENTA", "CASA", null, null, null, null, null, null, null, null, null,
                "casa con retraso extremo en modelo", "relevancia"
        );

        service.search(criteria, 10, 0);

        // Mapper debe haber recibido null (fallback a solo texto)
        ArgumentCaptor<float[]> vectorCaptor = ArgumentCaptor.forClass(float[].class);
        verify(mockMapper).searchHybrid(eq(criteria), vectorCaptor.capture(), eq(200), eq(10), eq(0));
        assertNull(vectorCaptor.getValue(), "Al expirar el timeout, el vector debe ser null para fallback a texto");

        var metrics = service.getEmbeddingMetrics();
        assertEquals(1, metrics.timeoutCount());
        assertEquals(1, metrics.fallbackTextCount());
        assertEquals(1.0, meterRegistry.get("hogarya.search.embedding.timeout").counter().count());
        assertEquals(1.0, meterRegistry.get("hogarya.search.embedding.fallback.text").counter().count());
    }
}
