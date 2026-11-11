package cl.hogarya.domain.service.search;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientResponseException;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Servicio de Indexación Semántica Vectorial de Propiedades.
 * - Genera embeddings densos de 768 dimensiones utilizando TaskType.RETRIEVAL_DOCUMENT al publicar o actualizar.
 * - Provee job de relleno (backfill) para propiedades sin vectorizar en la base de datos.
 * - Monitorea y registra métricas de indexación exitosa, fallos generales y errores HTTP 429 (Rate Limit / Quota Exceeded).
 */
@Service
public class PropertyIndexingService {

    private static final Logger log = LoggerFactory.getLogger(PropertyIndexingService.class);

    private final EmbeddingProvider embeddingProvider;
    private final JdbcClient jdbcClient;

    private final AtomicLong indexedCount = new AtomicLong(0);
    private final AtomicLong failureCount = new AtomicLong(0);
    private final AtomicLong rateLimit429Count = new AtomicLong(0);

    public record IndexingMetrics(
            long indexedCount,
            long failureCount,
            long rateLimit429Count
    ) {}

    @Autowired
    public PropertyIndexingService(
            @Autowired(required = false) EmbeddingProvider embeddingProvider,
            JdbcClient jdbcClient
    ) {
        this.embeddingProvider = embeddingProvider;
        this.jdbcClient = jdbcClient;
    }

    /**
     * Vectoriza e indexa una propiedad individual mediante TaskType.RETRIEVAL_DOCUMENT.
     *
     * @param propertyId Identificador único de la propiedad
     * @return true si la indexación y persistencia fueron exitosas, false en caso de fallo
     */
    @Transactional
    public boolean indexProperty(UUID propertyId) {
        if (embeddingProvider == null) {
            log.warn("No se puede indexar la propiedad {}: No hay un EmbeddingProvider configurado.", propertyId);
            failureCount.incrementAndGet();
            return false;
        }

        try {
            Map<String, Object> row = jdbcClient.sql("""
                    SELECT p.titulo, p.tipo_propiedad, p.operacion, 
                           COALESCE(c.nombre, '') AS comuna_nombre, 
                           COALESCE(p.barrio, '') AS barrio, 
                           COALESCE(p.descripcion, '') AS descripcion
                    FROM propiedad p
                    LEFT JOIN comuna c ON c.id = p.comuna_id
                    WHERE p.id = :id
                    """)
                    .param("id", propertyId)
                    .query(new org.springframework.jdbc.core.ColumnMapRowMapper())
                    .optional()
                    .orElse(null);

            if (row == null) {
                log.warn("Propiedad {} no encontrada para indexación", propertyId);
                failureCount.incrementAndGet();
                return false;
            }

            String titulo = (String) row.getOrDefault("titulo", "");
            String tipo = (String) row.getOrDefault("tipo_propiedad", "");
            String operacion = (String) row.getOrDefault("operacion", "");
            String comuna = (String) row.getOrDefault("comuna_nombre", "");
            String barrio = (String) row.getOrDefault("barrio", "");
            String descripcion = (String) row.getOrDefault("descripcion", "");

            String canonicalDocument = buildCanonicalDocumentText(titulo, tipo, operacion, comuna, barrio, descripcion);

            // Invocación explícita con RETRIEVAL_DOCUMENT para indexación de documentos
            float[] vector = embeddingProvider.generateEmbedding(
                    canonicalDocument,
                    GeminiEmbeddingProvider.TaskType.RETRIEVAL_DOCUMENT
            );

            if (vector == null || vector.length != embeddingProvider.getDimensions()) {
                throw new IllegalStateException("Vector retornado inválido o de longitud incorrecta");
            }

            String vectorLiteral = formatVectorForPgVector(vector);

            int updated = jdbcClient.sql("""
                    UPDATE propiedad
                    SET embedding = ('[' || :vec || ']')::vector,
                        actualizada_el = NOW()
                    WHERE id = :id
                    """)
                    .param("vec", vectorLiteral)
                    .param("id", propertyId)
                    .update();

            if (updated > 0) {
                indexedCount.incrementAndGet();
                log.info("Propiedad {} indexada exitosamente con RETRIEVAL_DOCUMENT (dim={})", propertyId, vector.length);
                return true;
            } else {
                failureCount.incrementAndGet();
                return false;
            }

        } catch (Exception ex) {
            handleIndexingException(propertyId, ex);
            return false;
        }
    }

    /**
     * Job de relleno (backfill) para propiedades existentes que aún no poseen embedding semántico.
     *
     * @param batchSize Número máximo de propiedades a procesar en esta tanda
     * @return Número de propiedades indexadas con éxito
     */
    public int backfillMissingEmbeddings(int batchSize) {
        if (embeddingProvider == null) {
            log.warn("Backfill abortado: EmbeddingProvider no disponible");
            return 0;
        }

        int limit = Math.max(1, Math.min(batchSize, 500));
        List<UUID> missingIds = jdbcClient.sql("""
                SELECT id 
                FROM propiedad 
                WHERE embedding IS NULL AND activo = TRUE 
                ORDER BY publicada_el ASC 
                LIMIT :limit
                """)
                .param("limit", limit)
                .query((rs, rowNum) -> (UUID) rs.getObject("id"))
                .list();

        log.info("Iniciando backfill de embeddings para {} propiedades pendientes (límite batch: {})", missingIds.size(), limit);
        int successCount = 0;

        for (UUID id : missingIds) {
            boolean ok = indexProperty(id);
            if (ok) {
                successCount++;
            }
        }

        log.info("Backfill completado: {}/{} propiedades indexadas con éxito", successCount, missingIds.size());
        return successCount;
    }

    /**
     * Retorna las métricas operacionales acumuladas de indexación.
     */
    public IndexingMetrics getMetrics() {
        return new IndexingMetrics(
                indexedCount.get(),
                failureCount.get(),
                rateLimit429Count.get()
        );
    }

    private String buildCanonicalDocumentText(String titulo, String tipo, String operacion, String comuna, String barrio, String descripcion) {
        StringBuilder sb = new StringBuilder();
        if (titulo != null && !titulo.isBlank()) sb.append(titulo.trim()).append(". ");
        if (tipo != null && !tipo.isBlank()) sb.append(tipo.trim()).append(" en ");
        if (operacion != null && !operacion.isBlank()) sb.append(operacion.trim()).append(". ");
        if (comuna != null && !comuna.isBlank()) {
            sb.append("Ubicado en comuna ").append(comuna.trim());
            if (barrio != null && !barrio.isBlank()) {
                sb.append(", sector ").append(barrio.trim());
            }
            sb.append(". ");
        }
        if (descripcion != null && !descripcion.isBlank()) {
            sb.append(descripcion.trim());
        }
        return sb.toString();
    }

    private String formatVectorForPgVector(float[] vector) {
        StringBuilder sb = new StringBuilder(vector.length * 9);
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(vector[i]);
        }
        return sb.toString();
    }

    private void handleIndexingException(UUID propertyId, Exception ex) {
        failureCount.incrementAndGet();

        boolean is429 = false;
        if (ex instanceof HttpStatusCodeException hsce && hsce.getStatusCode().value() == 429) {
            is429 = true;
        } else if (ex instanceof RestClientResponseException rcre && rcre.getStatusCode().value() == 429) {
            is429 = true;
        } else if (ex.getMessage() != null && (ex.getMessage().contains("429") || ex.getMessage().toLowerCase().contains("too many requests") || ex.getMessage().toLowerCase().contains("resource_exhausted"))) {
            is429 = true;
        }

        if (is429) {
            rateLimit429Count.incrementAndGet();
            log.error("RATE LIMIT 429 excedido al indexar propiedad {}: {}", propertyId, ex.getMessage());
        } else {
            log.error("Error al indexar semánticamente la propiedad {}: {}", propertyId, ex.getMessage(), ex);
        }
    }
}
