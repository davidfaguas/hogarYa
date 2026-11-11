package cl.hogarya.domain.service.search;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@DisplayName("Gemini: Integración con GeminiEmbeddingProvider (gemini-embedding-001, dim 768, normalización L2)")
class GeminiEmbeddingProviderTest {

    @Test
    @DisplayName("Genera embedding con gemini-embedding-001, 768 dimensiones, L2 norm ≈ 1.0 y task type RETRIEVAL_QUERY")
    void shouldGenerate768DimNormalizedEmbeddingForQuery() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://generativelanguage.googleapis.com/v1beta");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

        GeminiEmbeddingProvider provider = new GeminiEmbeddingProvider("test-api-key-12345", "models/gemini-embedding-001", builder.build());

        // Simular respuesta JSON de Gemini con 768 floats no normalizados
        StringBuilder jsonBuilder = new StringBuilder("{\"embedding\":{\"values\":[");
        for (int i = 0; i < 768; i++) {
            if (i > 0) jsonBuilder.append(",");
            // valores arbitrarios entre -2.0 y 2.0
            float val = (float) Math.sin(i * 0.17) * 1.5f;
            jsonBuilder.append(val);
        }
        jsonBuilder.append("]}}");

        server.expect(requestTo("https://generativelanguage.googleapis.com/v1beta/models/gemini-embedding-001:embedContent?key=test-api-key-12345"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.model").value("models/gemini-embedding-001"))
                .andExpect(jsonPath("$.taskType").value("RETRIEVAL_QUERY"))
                .andExpect(jsonPath("$.outputDimensionality").value(768))
                .andExpect(jsonPath("$.content.parts[0].text").value("departamento en Providencia cerca de metro"))
                .andRespond(withSuccess(jsonBuilder.toString(), MediaType.APPLICATION_JSON));

        float[] embedding = provider.generateEmbedding("departamento en Providencia cerca de metro");

        server.verify();

        assertNotNull(embedding, "El embedding no debe ser nulo");
        assertEquals(768, embedding.length, "La dimensión debe ser exactamente 768");

        double norm = GeminiEmbeddingProvider.calculateL2Norm(embedding);
        assertEquals(1.0, norm, 0.0001, "La norma L2 euclidiana debe ser aproximadamente 1.0");
    }

    @Test
    @DisplayName("Genera embedding para indexación de documento con task type RETRIEVAL_DOCUMENT")
    void shouldGenerateEmbeddingWithRetrievalDocumentTaskType() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://generativelanguage.googleapis.com/v1beta");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

        GeminiEmbeddingProvider provider = new GeminiEmbeddingProvider("test-key-doc", "models/gemini-embedding-001", builder.build());

        StringBuilder jsonBuilder = new StringBuilder("{\"embedding\":{\"values\":[");
        for (int i = 0; i < 768; i++) {
            if (i > 0) jsonBuilder.append(",");
            jsonBuilder.append(0.5f);
        }
        jsonBuilder.append("]}}");

        server.expect(requestTo("https://generativelanguage.googleapis.com/v1beta/models/gemini-embedding-001:embedContent?key=test-key-doc"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.taskType").value("RETRIEVAL_DOCUMENT"))
                .andRespond(withSuccess(jsonBuilder.toString(), MediaType.APPLICATION_JSON));

        float[] embedding = provider.generateEmbedding(
                "Casa de 3 dormitorios en La Florida",
                GeminiEmbeddingProvider.TaskType.RETRIEVAL_DOCUMENT
        );

        server.verify();
        assertEquals(768, embedding.length);
        assertEquals(1.0, GeminiEmbeddingProvider.calculateL2Norm(embedding), 0.0001);
    }

    @Test
    @DisplayName("Falla al inicializar en producción si GEMINI_API_KEY no está definida o está vacía")
    void shouldThrowExceptionWhenApiKeyIsMissing() {
        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> {
            new GeminiEmbeddingProvider(null, null, null, null);
        });
        assertTrue(ex.getMessage().contains("GEMINI_API_KEY es obligatoria"));

        IllegalStateException ex2 = assertThrows(IllegalStateException.class, () -> {
            new GeminiEmbeddingProvider("   ", "models/gemini-embedding-001", null, null);
        });
        assertTrue(ex2.getMessage().contains("GEMINI_API_KEY es obligatoria"));
    }
}
