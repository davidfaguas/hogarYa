package cl.hogarya.domain.service.search;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;

/**
 * Proveedor de Embeddings Semánticos de Producción utilizando Google Gemini Embeddings API.
 * - Modelo por defecto: gemini-embedding-001 (o text-embedding-004)
 * - Dimensión de salida fija: 768 (outputDimensionality=768 para compatibilidad estricta con pgvector)
 * - Normalización euclidiana L2 (||v||_2 = 1.0)
 * - Soporte para Task Type: RETRIEVAL_QUERY (búsquedas) y RETRIEVAL_DOCUMENT (propiedades)
 * - Activo exclusivamente en perfil 'prod' cuando se configure GEMINI_API_KEY.
 */
@Component
@Profile("prod")
@ConditionalOnExpression("T(org.springframework.util.StringUtils).hasText('${hogarya.gemini.api-key:}') || T(org.springframework.util.StringUtils).hasText('${GEMINI_API_KEY:}')")
public class GeminiEmbeddingProvider implements EmbeddingProvider {

    public static final int DIMENSIONS = 768;
    public static final String DEFAULT_MODEL = "models/gemini-embedding-001";
    public static final String DEFAULT_BASE_URL = "https://generativelanguage.googleapis.com/v1beta";

    public enum TaskType {
        RETRIEVAL_QUERY,
        RETRIEVAL_DOCUMENT,
        SEMANTIC_SIMILARITY,
        CLASSIFICATION,
        CLUSTERING
    }

    private final RestClient restClient;
    private final String apiKey;
    private final String model;
    private final String baseUrl;

    @Autowired
    public GeminiEmbeddingProvider(
            @Value("${hogarya.gemini.api-key:${GEMINI_API_KEY:}}") String apiKey,
            @Value("${hogarya.gemini.model:models/gemini-embedding-001}") String model,
            @Value("${hogarya.gemini.base-url:https://generativelanguage.googleapis.com/v1beta}") String baseUrl,
            @Autowired(required = false) RestClient.Builder restClientBuilder
    ) {
        if (apiKey == null || apiKey.trim().isBlank()) {
            throw new IllegalStateException(
                    "Fallo al arrancar en perfil producción: La variable de entorno GEMINI_API_KEY es obligatoria " +
                    "para inicializar GeminiEmbeddingProvider en perfil de producción."
            );
        }
        this.apiKey = apiKey.trim();
        this.model = (model != null && !model.isBlank()) ? model.trim() : DEFAULT_MODEL;
        this.baseUrl = (baseUrl != null && !baseUrl.isBlank()) ? baseUrl.trim() : DEFAULT_BASE_URL;

        RestClient.Builder builder = restClientBuilder != null ? restClientBuilder : RestClient.builder();
        org.springframework.http.client.SimpleClientHttpRequestFactory requestFactory =
                new org.springframework.http.client.SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(java.time.Duration.ofMillis(150));
        requestFactory.setReadTimeout(java.time.Duration.ofMillis(200));
        this.restClient = builder.baseUrl(this.baseUrl).requestFactory(requestFactory).build();
    }

    /**
     * Constructor para pruebas con RestClient inyectado directamente.
     */
    public GeminiEmbeddingProvider(String apiKey, String model, RestClient restClient) {
        this.apiKey = apiKey != null ? apiKey.trim() : "test-key";
        this.model = model != null ? model.trim() : DEFAULT_MODEL;
        this.baseUrl = DEFAULT_BASE_URL;
        this.restClient = restClient;
    }

    @Override
    public float[] generateEmbedding(String text) {
        return generateEmbedding(text, TaskType.RETRIEVAL_QUERY);
    }

    public float[] generateEmbedding(String text, TaskType taskType) {
        if (text == null || text.isBlank()) {
            return new float[DIMENSIONS];
        }

        TaskType effectiveTaskType = taskType != null ? taskType : TaskType.RETRIEVAL_QUERY;
        GeminiEmbedRequest requestBody = new GeminiEmbedRequest(
                this.model,
                new GeminiContent(List.of(new GeminiPart(text.trim()))),
                effectiveTaskType.name(),
                DIMENSIONS
        );

        String uri = "/" + this.model + ":embedContent?key=" + this.apiKey;

        GeminiEmbedResponse response = restClient.post()
                .uri(uri)
                .contentType(MediaType.APPLICATION_JSON)
                .body(requestBody)
                .retrieve()
                .body(GeminiEmbedResponse.class);

        if (response == null || response.embedding() == null || response.embedding().values() == null) {
            throw new IllegalStateException("Respuesta vacía o inválida recibida desde Gemini Embeddings API");
        }

        List<Float> rawValues = response.embedding().values();
        if (rawValues.size() != DIMENSIONS) {
            throw new IllegalStateException("Dimensión inesperada en respuesta de Gemini: esperado " + DIMENSIONS + ", obtenido " + rawValues.size());
        }

        return normalizeL2(rawValues);
    }

    @Override
    public int getDimensions() {
        return DIMENSIONS;
    }

    public String getModel() {
        return model;
    }

    private float[] normalizeL2(List<Float> values) {
        double sumSquares = 0.0;
        for (Float val : values) {
            float v = val != null ? val : 0.0f;
            sumSquares += (double) v * (double) v;
        }

        double norm = Math.sqrt(sumSquares);
        float[] normalized = new float[values.size()];

        if (norm > 1e-12) {
            for (int i = 0; i < values.size(); i++) {
                float v = values.get(i) != null ? values.get(i) : 0.0f;
                normalized[i] = (float) (v / norm);
            }
        }

        return normalized;
    }

    public static double calculateL2Norm(float[] vector) {
        if (vector == null) return 0.0;
        double sum = 0.0;
        for (float v : vector) {
            sum += (double) v * (double) v;
        }
        return Math.sqrt(sum);
    }

    // DTOs para serialización JSON
    public record GeminiPart(String text) {}
    public record GeminiContent(List<GeminiPart> parts) {}
    public record GeminiEmbedRequest(
            String model,
            GeminiContent content,
            String taskType,
            @JsonProperty("outputDimensionality") int outputDimensionality
    ) {}
    public record GeminiEmbeddingData(List<Float> values) {}
    public record GeminiEmbedResponse(GeminiEmbeddingData embedding) {}
}
