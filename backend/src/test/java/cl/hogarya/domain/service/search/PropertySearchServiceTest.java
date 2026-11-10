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
}
