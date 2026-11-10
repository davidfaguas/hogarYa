package cl.hogarya.domain.service.search;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Random;

/**
 * Implementación simulada determinista de {@link EmbeddingProvider}.
 * Genera vectores de 768 dimensiones normalizados con norma euclidiana L2 = 1.0,
 * garantizando que el mismo texto siempre genere el mismo embedding para tests reproducibles.
 */
@Component
public class FakeEmbeddingProvider implements EmbeddingProvider {

    public static final int VECTOR_DIMENSIONS = 768;

    @Override
    public float[] generateEmbedding(String text) {
        if (text == null || text.isBlank()) {
            return new float[VECTOR_DIMENSIONS];
        }

        // Semilla determinista derivada del hash SHA-256 del texto
        long seed = computeSeed(text);
        Random random = new Random(seed);

        float[] vector = new float[VECTOR_DIMENSIONS];
        double sumSquares = 0.0;

        for (int i = 0; i < VECTOR_DIMENSIONS; i++) {
            // Generar valores en distribución normal ~ N(0, 1)
            float val = (float) random.nextGaussian();
            vector[i] = val;
            sumSquares += (double) val * (double) val;
        }

        // Normalización L2 (norma euclidiana unitaria para cosine similarity)
        double norm = Math.sqrt(sumSquares);
        if (norm > 1e-9) {
            for (int i = 0; i < VECTOR_DIMENSIONS; i++) {
                vector[i] = (float) (vector[i] / norm);
            }
        }

        return vector;
    }

    @Override
    public int getDimensions() {
        return VECTOR_DIMENSIONS;
    }

    private long computeSeed(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(text.trim().toLowerCase().getBytes(StandardCharsets.UTF_8));
            long seed = 0;
            for (int i = 0; i < 8; i++) {
                seed = (seed << 8) | (hash[i] & 0xFFL);
            }
            return seed;
        } catch (NoSuchAlgorithmException e) {
            return text.hashCode();
        }
    }
}
