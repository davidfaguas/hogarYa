package cl.hogarya.domain.service.search;

import cl.hogarya.domain.model.search.PropertySearchCriteria;
import cl.hogarya.domain.model.search.PropertySearchResultDto;
import cl.hogarya.infrastructure.persistence.mapper.PropertySearchMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Servicio de Búsqueda Híbrida Geodésica y Semántica.
 * Corrección HNSW y Resiliencia de Consultas:
 * - Caché LRU en memoria acotada (1.000 entradas) para embeddings de texto de consulta.
 * - Timeout estricto (300 ms) en la llamada a EmbeddingProvider con fallback transparente a FTS textual.
 * - Registro de métricas Micrometer (hit, miss, timeout, fallback).
 * - Configura SET LOCAL hnsw.ef_search = 200 dentro de @Transactional para garantizar
 *   que pgvector retorne hasta 200 candidatos del Top-K vectorial.
 * - Activa SET LOCAL hnsw.iterative_scan = relaxed_order y SET LOCAL hnsw.max_scan_tuples = 50000
 *   únicamente si la extensión pgvector instalada es versión >= 0.8.0.
 * - Falla obligatoria al iniciar en producción si no existe una implementación real de EmbeddingProvider.
 */
@Service
public class PropertySearchService {

    private static final Logger log = LoggerFactory.getLogger(PropertySearchService.class);

    private final PropertySearchMapper propertySearchMapper;
    private final JdbcClient jdbcClient;
    private final EmbeddingProvider embeddingProvider;
    private volatile Boolean supportsIterativeScan = null;

    private final QueryEmbeddingLruCache queryEmbeddingCache;

    private final AtomicLong cacheHitCount = new AtomicLong(0);
    private final AtomicLong cacheMissCount = new AtomicLong(0);
    private final AtomicLong timeoutCount = new AtomicLong(0);
    private final AtomicLong fallbackTextCount = new AtomicLong(0);

    private Counter cacheHitCounter;
    private Counter cacheMissCounter;
    private Counter timeoutCounter;
    private Counter fallbackTextCounter;

    public record EmbeddingMetrics(
            long cacheHitCount,
            long cacheMissCount,
            long timeoutCount,
            long fallbackTextCount
    ) {}

    public static class QueryEmbeddingLruCache {
        private final int maxEntries;
        private final Map<String, float[]> cache;

        public QueryEmbeddingLruCache(int maxEntries) {
            this.maxEntries = maxEntries;
            this.cache = Collections.synchronizedMap(
                    new LinkedHashMap<>(maxEntries, 0.75f, true) {
                        @Override
                        protected boolean removeEldestEntry(Map.Entry<String, float[]> eldest) {
                            return size() > QueryEmbeddingLruCache.this.maxEntries;
                        }
                    }
            );
        }

        public float[] get(String key) {
            return cache.get(key);
        }

        public void put(String key, float[] vector) {
            if (key != null && vector != null) {
                cache.put(key, vector);
            }
        }

        public int size() {
            return cache.size();
        }

        public void clear() {
            cache.clear();
        }
    }

    @Autowired
    public PropertySearchService(
            PropertySearchMapper propertySearchMapper,
            JdbcClient jdbcClient,
            @Autowired(required = false) EmbeddingProvider embeddingProvider,
            @Autowired(required = false) Environment environment
    ) {
        this.propertySearchMapper = propertySearchMapper;
        this.jdbcClient = jdbcClient;
        this.queryEmbeddingCache = new QueryEmbeddingLruCache(1000);

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
            EmbeddingProvider embeddingProvider,
            Environment environment,
            MeterRegistry meterRegistry
    ) {
        this.propertySearchMapper = propertySearchMapper;
        this.jdbcClient = jdbcClient;
        this.queryEmbeddingCache = new QueryEmbeddingLruCache(1000);
        this.embeddingProvider = embeddingProvider != null ? embeddingProvider : new FakeEmbeddingProvider();
        if (meterRegistry != null) {
            setMeterRegistry(meterRegistry);
        }
    }

    public PropertySearchService(
            PropertySearchMapper propertySearchMapper,
            JdbcClient jdbcClient,
            EmbeddingProvider embeddingProvider,
            Environment environment,
            MeterRegistry meterRegistry,
            long embeddingTimeoutMs
    ) {
        this(propertySearchMapper, jdbcClient, embeddingProvider, environment, meterRegistry);
    }

    @Autowired(required = false)
    public void setMeterRegistry(MeterRegistry meterRegistry) {
        if (meterRegistry != null) {
            this.cacheHitCounter = Counter.builder("hogarya.search.embedding.cache.hit")
                    .description("Aciertos de caché LRU para embeddings de consulta")
                    .register(meterRegistry);
            this.cacheMissCounter = Counter.builder("hogarya.search.embedding.cache.miss")
                    .description("Fallos de caché LRU para embeddings de consulta")
                    .register(meterRegistry);
            this.timeoutCounter = Counter.builder("hogarya.search.embedding.timeout")
                    .description("Timeouts excedidos al resolver embeddings de consulta")
                    .register(meterRegistry);
            this.fallbackTextCounter = Counter.builder("hogarya.search.embedding.fallback.text")
                    .description("Ocasiones en que se ejecutó fallback exclusivo a búsqueda por texto")
                    .register(meterRegistry);
        }
    }

    public PropertySearchService(
            PropertySearchMapper propertySearchMapper,
            JdbcClient jdbcClient,
            EmbeddingProvider embeddingProvider
    ) {
        this(propertySearchMapper, jdbcClient, embeddingProvider, null, null, 2000L);
    }

    public PropertySearchService(PropertySearchMapper propertySearchMapper, JdbcClient jdbcClient) {
        this(propertySearchMapper, jdbcClient, new FakeEmbeddingProvider(), null, null, 2000L);
    }

    /**
     * Resuelve el embedding de una consulta de texto libre aplicando caché LRU y timeout con retorno seguro.
     */
    public float[] resolveEmbeddingVector(String rawQuery) {
        if (rawQuery == null || rawQuery.isBlank()) {
            return null;
        }

        String cacheKey = rawQuery.trim().toLowerCase();
        float[] cached = queryEmbeddingCache.get(cacheKey);
        if (cached != null) {
            recordCacheHit();
            return cached;
        }

        recordCacheMiss();
        if (embeddingProvider == null) {
            recordFallbackText();
            return null;
        }

        try {
            float[] vector = embeddingProvider.generateEmbedding(rawQuery);
            if (vector != null) {
                queryEmbeddingCache.put(cacheKey, vector);
            }
            return vector;
        } catch (org.springframework.web.client.ResourceAccessException rae) {
            recordTimeout();
            recordFallbackText();
            log.warn("Timeout o fallo de conexión en cliente HTTP al generar embedding para '{}': {}. Fallback a búsqueda solo texto.",
                    rawQuery, rae.getMessage());
            return null;
        } catch (Exception ex) {
            if (ex instanceof java.net.SocketTimeoutException
                    || (ex.getCause() != null && ex.getCause() instanceof java.net.SocketTimeoutException)) {
                recordTimeout();
            }
            recordFallbackText();
            log.warn("Error generando embedding para consulta '{}': {}. Fallback a búsqueda solo texto.",
                    rawQuery, ex.getMessage());
            return null;
        }
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
        return search(criteria, (float[]) null, limit, offset);
    }

    @Transactional(readOnly = true)
    public List<PropertySearchResultDto> search(
            PropertySearchCriteria criteria,
            float[] embeddingVector,
            int limit,
            int offset
    ) {
        // Si no se proporcionó vector explícito pero hay texto libre en los criterios, generarlo con caché y timeout
        if (embeddingVector == null && criteria != null && criteria.rawQuery() != null && !criteria.rawQuery().isBlank()) {
            embeddingVector = resolveEmbeddingVector(criteria.rawQuery());
        }

        configureVectorSession();
        return propertySearchMapper.searchHybrid(criteria, embeddingVector, 200, limit, offset);
    }

    public EmbeddingMetrics getEmbeddingMetrics() {
        return new EmbeddingMetrics(
                cacheHitCount.get(),
                cacheMissCount.get(),
                timeoutCount.get(),
                fallbackTextCount.get()
        );
    }

    public QueryEmbeddingLruCache getQueryEmbeddingCache() {
        return queryEmbeddingCache;
    }

    private void recordCacheHit() {
        cacheHitCount.incrementAndGet();
        if (cacheHitCounter != null) {
            cacheHitCounter.increment();
        }
    }

    private void recordCacheMiss() {
        cacheMissCount.incrementAndGet();
        if (cacheMissCounter != null) {
            cacheMissCounter.increment();
        }
    }

    private void recordTimeout() {
        timeoutCount.incrementAndGet();
        if (timeoutCounter != null) {
            timeoutCounter.increment();
        }
    }

    private void recordFallbackText() {
        fallbackTextCount.incrementAndGet();
        if (fallbackTextCounter != null) {
            fallbackTextCounter.increment();
        }
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
