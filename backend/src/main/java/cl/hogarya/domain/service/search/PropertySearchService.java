package cl.hogarya.domain.service.search;

import cl.hogarya.domain.model.search.PropertySearchCriteria;
import cl.hogarya.domain.model.search.PropertySearchResultDto;
import cl.hogarya.infrastructure.persistence.mapper.PropertySearchMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Servicio de Búsqueda Híbrida Geodésica y Semántica.
 * Corrección HNSW:
 * - Configura SET LOCAL hnsw.ef_search = 200 dentro de @Transactional para garantizar
 *   que pgvector retorne hasta 200 candidatos del Top-K vectorial.
 * - Activa SET LOCAL hnsw.iterative_scan = relaxed_order y SET LOCAL hnsw.max_scan_tuples = 50000
 *   únicamente si la extensión pgvector instalada es versión >= 0.8.0.
 * - Integra EmbeddingProvider para vectorizar automáticamente consultas en lenguaje natural
 *   sin requerir que la capa superior pase un float[] manualmente.
 * - Falla obligatoria al iniciar en producción si no existe una implementación real de EmbeddingProvider.
 */
@Service
public class PropertySearchService {

    private final PropertySearchMapper propertySearchMapper;
    private final JdbcClient jdbcClient;
    private final EmbeddingProvider embeddingProvider;
    private volatile Boolean supportsIterativeScan = null;

    @Autowired
    public PropertySearchService(
            PropertySearchMapper propertySearchMapper,
            JdbcClient jdbcClient,
            @Autowired(required = false) EmbeddingProvider embeddingProvider,
            @Autowired(required = false) Environment environment
    ) {
        this.propertySearchMapper = propertySearchMapper;
        this.jdbcClient = jdbcClient;

        if (embeddingProvider == null) {
            boolean isProd = false;
            if (environment != null) {
                for (String p : environment.getActiveProfiles()) {
                    if ("prod".equalsIgnoreCase(p) || "production".equalsIgnoreCase(p)) {
                        isProd = true;
                        break;
                    }
                }
            }
            if (isProd) {
                throw new IllegalStateException(
                        "Fallo al arrancar en perfil producción: No se ha configurado un proveedor real de EmbeddingProvider. " +
                        "FakeEmbeddingProvider solo está disponible en perfiles dev y test. " +
                        "Configure una implementación real conectada al modelo de embeddings."
                );
            }
            this.embeddingProvider = new FakeEmbeddingProvider();
        } else {
            this.embeddingProvider = embeddingProvider;
        }
    }

    public PropertySearchService(
            PropertySearchMapper propertySearchMapper,
            JdbcClient jdbcClient,
            EmbeddingProvider embeddingProvider
    ) {
        this(propertySearchMapper, jdbcClient, embeddingProvider, null);
    }

    public PropertySearchService(PropertySearchMapper propertySearchMapper, JdbcClient jdbcClient) {
        this(propertySearchMapper, jdbcClient, new FakeEmbeddingProvider(), null);
    }

    /**
     * Búsqueda híbrida con resolución automática del vector semántico a partir del texto de consulta.
     */
    @Transactional(readOnly = true)
    public List<PropertySearchResultDto> search(
            PropertySearchCriteria criteria,
            int limit,
            int offset
    ) {
        float[] vector = null;
        if (criteria != null && criteria.rawQuery() != null && !criteria.rawQuery().isBlank()) {
            vector = embeddingProvider.generateEmbedding(criteria.rawQuery());
        }
        return search(criteria, vector, limit, offset);
    }

    @Transactional(readOnly = true)
    public List<PropertySearchResultDto> search(
            PropertySearchCriteria criteria,
            float[] embeddingVector,
            int limit,
            int offset
    ) {
        // Si no se proporcionó vector explícito pero hay texto libre en los criterios, generarlo automáticamente
        if (embeddingVector == null && criteria != null && criteria.rawQuery() != null && !criteria.rawQuery().isBlank()) {
            embeddingVector = embeddingProvider.generateEmbedding(criteria.rawQuery());
        }

        configureVectorSession();
        return propertySearchMapper.searchHybrid(criteria, embeddingVector, 200, limit, offset);
    }

    @Transactional(readOnly = true)
    public long count(PropertySearchCriteria criteria) {
        return propertySearchMapper.countByCriteria(criteria);
    }

    public void configureVectorSession() {
        jdbcClient.sql("SET LOCAL hnsw.ef_search = 200").update();
        if (isIterativeScanSupported()) {
            jdbcClient.sql("SET LOCAL hnsw.iterative_scan = relaxed_order").update();
            jdbcClient.sql("SET LOCAL hnsw.max_scan_tuples = 50000").update();
        }
    }

    public boolean isIterativeScanSupported() {
        if (supportsIterativeScan == null) {
            synchronized (this) {
                if (supportsIterativeScan == null) {
                    try {
                        Optional<String> versionOpt = jdbcClient.sql(
                                "SELECT extversion FROM pg_extension WHERE extname = 'vector'"
                        ).query(String.class).optional();

                        if (versionOpt.isPresent()) {
                            supportsIterativeScan = isVersionAtLeast(versionOpt.get().trim(), "0.8.0");
                        } else {
                            supportsIterativeScan = false;
                        }
                    } catch (Exception e) {
                        supportsIterativeScan = false;
                    }
                }
            }
        }
        return supportsIterativeScan;
    }

    public static boolean isVersionAtLeast(String current, String target) {
        String[] currParts = current.split("[^0-9]+");
        String[] targParts = target.split("[^0-9]+");
        int length = Math.max(currParts.length, targParts.length);
        for (int i = 0; i < length; i++) {
            int c = (i < currParts.length && !currParts[i].isEmpty()) ? Integer.parseInt(currParts[i]) : 0;
            int t = (i < targParts.length && !targParts[i].isEmpty()) ? Integer.parseInt(targParts[i]) : 0;
            if (c > t) return true;
            if (c < t) return false;
        }
        return true;
    }
}
