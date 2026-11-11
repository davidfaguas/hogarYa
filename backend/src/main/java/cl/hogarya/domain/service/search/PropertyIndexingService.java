package cl.hogarya.domain.service.search;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientResponseException;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Servicio de Indexación Semántica Vectorial de Propiedades.
 * - Genera embeddings densos de 768 dimensiones utilizando TaskType.RETRIEVAL_DOCUMENT al publicar o actualizar.
 * - Provee job asíncrono con reintentos para publicaciones y @Scheduled con control de tasa para relleno (backfill).
 * - Registra indexedCount, failureCount y rateLimit429Count en AtomicLong y en Micrometer MeterRegistry.
 */
@Service
public class PropertyIndexingService {

    private static final Logger log = LoggerFactory.getLogger(PropertyIndexingService.class);

    private final EmbeddingProvider embeddingProvider;
    private final JdbcClient jdbcClient;

    private final AtomicLong indexedCount = new AtomicLong(0);
    private final AtomicLong failureCount = new AtomicLong(0);
    private final AtomicLong rateLimit429Count = new AtomicLong(0);

    private final Counter indexedCounter;
    private final Counter failureCounter;
    private final Counter rateLimit429Counter;

    public record IndexingMetrics(
            long indexedCount,
            long failureCount,
            long rateLimit429Count
    ) {}

    public PropertyIndexingService(
            EmbeddingProvider embeddingProvider,
            JdbcClient jdbcClient
    ) {
        this(embeddingProvider, jdbcClient, null);
    }

    @Autowired
    public PropertyIndexingService(
            @Autowired(required = false) EmbeddingProvider embeddingProvider,
            JdbcClient jdbcClient,
            @Autowired(required = false) MeterRegistry meterRegistry
    ) {
        this.embeddingProvider = embeddingProvider;
        this.jdbcClient = jdbcClient;

        if (meterRegistry != null) {
            this.indexedCounter = Counter.builder("hogarya.indexing.indexed.count")
                    .description("Cantidad total de propiedades indexadas exitosamente con embedding")
                    .register(meterRegistry);
            this.failureCounter = Counter.builder("hogarya.indexing.failure.count")
                    .description("Cantidad total de fallos irrecuperables de indexación")
                    .register(meterRegistry);
            this.rateLimit429Counter = Counter.builder("hogarya.indexing.ratelimit.429.count")
                    .description("Cantidad de errores HTTP 429 Rate Limit / Quota Exceeded")
                    .register(meterRegistry);
        } else {
            this.indexedCounter = null;
            this.failureCounter = null;
            this.rateLimit429Counter = null;
        }
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
            recordFailure();
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
                recordFailure();
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
                recordSuccess();
                log.info("Propiedad {} indexada exitosamente con RETRIEVAL_DOCUMENT (dim={})", propertyId, vector.length);
                return true;
            } else {
                recordFailure();
                return false;
            }

        } catch (Exception ex) {
            handleIndexingException(propertyId, ex);
            return false;
        }
    }

    /**
     * Indexación asíncrona con reintentos exponenciales para el flujo de publicación.
     */
    @Async
    public CompletableFuture<Boolean> indexPropertyAsync(UUID propertyId) {
        int maxRetries = 3;
        long backoffMs = 500;
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            boolean success = indexProperty(propertyId);
            if (success) {
                return CompletableFuture.completedFuture(true);
            }
            if (attempt < maxRetries) {
                try {
                    Thread.sleep(backoffMs * attempt);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        log.warn("Agotados los {} reintentos de indexación asíncrona para la propiedad {}", maxRetries, propertyId);
        return CompletableFuture.completedFuture(false);
    }

    /**
     * Job programado de relleno (backfill) para propiedades pendientes de embedding con límite de velocidad.
     */
    @Scheduled(
            initialDelayString = "${hogarya.indexing.backfill.initial-delay-ms:15000}",
            fixedDelayString = "${hogarya.indexing.backfill.interval-ms:60000}"
    )
    public void scheduledBackfill() {
        backfillMissingEmbeddings(20);
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
            try {
                // Control de velocidad preventiva para no saturar TPS de Gemini
                Thread.sleep(100);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                break;
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

    private void recordSuccess() {
        indexedCount.incrementAndGet();
        if (indexedCounter != null) {
            indexedCounter.increment();
        }
    }

    private void recordFailure() {
        failureCount.incrementAndGet();
        if (failureCounter != null) {
            failureCounter.increment();
        }
    }

    private void record429() {
        rateLimit429Count.incrementAndGet();
        if (rateLimit429Counter != null) {
            rateLimit429Counter.increment();
        }
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
        recordFailure();

        boolean is429 = false;
        if (ex instanceof HttpStatusCodeException hsce && hsce.getStatusCode().value() == 429) {
            is429 = true;
        } else if (ex instanceof RestClientResponseException rcre && rcre.getStatusCode().value() == 429) {
            is429 = true;
        } else if (ex.getMessage() != null && (ex.getMessage().contains("429") || ex.getMessage().toLowerCase().contains("too many requests") || ex.getMessage().toLowerCase().contains("resource_exhausted"))) {
            is429 = true;
        }

        if (is429) {
            record429();
            log.error("RATE LIMIT 429 excedido al indexar propiedad {}: {}", propertyId, ex.getMessage());
        } else {
            log.error("Error al indexar semánticamente la propiedad {}: {}", propertyId, ex.getMessage(), ex);
        }
    }
}
