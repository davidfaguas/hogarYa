-- ====================================================================================================
-- BENCHMARK DE VOLUMEN (100.000 PROPIEDADES) Y FIDELIDAD DE ÍNDICE HNSW
-- HOGARYA - PORTAL INMOBILIARIO CHILE
--
-- ADVERTENCIA ÉTICA Y TÉCNICA (Regla de Honestidad):
-- Toda medición de latencia o planes de ejecución generados en este script deben interpretarse como:
-- [PROYECCIÓN NO MEDIDA EN ESTE SANDBOX] hasta que sean ejecutados sobre hardware PostgreSQL dedicado.
-- El Recall@10 calculado evalúa la FIDELIDAD DEL ÍNDICE (recall algorítmico HNSW vs búsqueda secuencial
-- exhaustiva exacta sobre distancias con vectores únicos), NO la calidad semántica percibida.
-- ====================================================================================================

\set ON_ERROR_STOP on
SET client_min_messages = notice;

BEGIN;

CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS postgis;
CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS pg_trgm;
CREATE EXTENSION IF NOT EXISTS unaccent;

-- Organización de soporte para integridad referencial de propiedades
INSERT INTO organizacion (
    id, rut_empresa, razon_social, nombre_fantasia, tipo,
    telefono_contacto, whatsapp_contacto, email_contacto
) VALUES (
    '00000000-0000-0000-0000-000000000001'::uuid,
    '76123456-0',
    'Inmobiliaria Benchmark SpA',
    'HogarYa Demo',
    'INMOBILIARIA',
    '+56911111111',
    '+56911111111',
    'contacto@hogarya.cl'
) ON CONFLICT (id) DO NOTHING;

-- Pool de vectores base (2.000 vectores de 768 dims) + Pool de perturbación de ruido (50 vectores)
-- Genera 2000 x 50 = 100.000 combinaciones estrictamente ÚNICAS de embeddings
CREATE TEMP TABLE vector_base_pool AS
SELECT p AS pool_id,
       ('[' || array_to_string(array_agg(round(((sin(p * 768 + s))::numeric), 4)), ', ') || ']')::vector(768) AS base_vec
FROM generate_series(0, 1999) p,
     generate_series(1, 768) s
GROUP BY p;

CREATE TEMP TABLE vector_noise_pool AS
SELECT n AS noise_id,
       ('[' || array_to_string(array_agg(round(((cos(n * 768 + s) * 0.005)::numeric), 6)), ', ') || ']')::vector(768) AS noise_vec
FROM generate_series(0, 49) n,
     generate_series(1, 768) s
GROUP BY n;

-- 1. Generación sintética de 100.000 propiedades representativas del mercado chileno
-- Datos descorrelacionados con combinaciones reales de VENTA + DEPARTAMENTO y distribución de texto
INSERT INTO propiedad (
    id,
    codigo_publicacion,
    organizacion_id,
    titulo,
    descripcion,
    tipo_propiedad,
    operacion,
    estado,
    precio_uf,
    m2_util,
    m2_total,
    dormitorios,
    banos,
    comuna_id,
    direccion_referencial,
    barrio,
    ubicacion,
    subsidio_ds19,
    activo,
    embedding
)
SELECT
    gen_random_uuid(),
    'SYN-' || lpad(i::text, 6, '0'),
    '00000000-0000-0000-0000-000000000001'::uuid,
    'Propiedad ' || i || ' en ' || (ARRAY['Ñuñoa', 'Providencia', 'Las Condes', 'Santiago', 'La Florida', 'Maipú'])[1 + (i % 6)],
    'Excelente departamento luminoso con ' ||
    (ARRAY[
        'piscina y quincho cerca de estacion de metro.',
        'vista despejada a la cordillera y terraza amplia.',
        'areas verdes y seguridad 24 horas.',
        'estacionamiento techado y bodega grande.',
        'cocina equipada y logia independiente.'
    ])[1 + (i % 5)],
    (ARRAY['DEPARTAMENTO', 'CASA', 'OFICINA', 'PARCELA'])[1 + (i % 4)],
    CASE WHEN ((i / 4) % 2 = 0) THEN 'VENTA' ELSE 'ARRIENDO' END,
    'USADA',
    round((2000 + ((i * 37) % 5500))::numeric, 2), -- Rango controlado entre 2000 y 7500 UF
    round((40 + (i % 120))::numeric, 1),
    round((50 + (i % 150))::numeric, 1),
    (1 + (i % 4))::smallint,
    (1 + (i % 3))::smallint,
    (1 + (i % 6))::smallint, -- Comuna IDs 1 al 6 (smallint FK a comuna.id)
    'Av. Principal ' || i,
    (ARRAY['Plaza Ñuñoa', 'El Golf', 'San Carlos', 'Bellavista', 'Centro', 'Pajaritos'])[1 + (i % 6)],
    -- Ubicación en radio < 800m de estaciones de Metro L4 (La Florida / Puente Alto)
    ST_SetSRID(ST_MakePoint(-70.598 + (((i % 100) - 50) * 0.0001), -33.525 + (((i % 100) - 50) * 0.0001)), 4326)::geography,
    (i % 10 = 0),
    true,
    (bp.base_vec + np.noise_vec)
FROM generate_series(1, 100000) AS i
JOIN vector_base_pool bp ON bp.pool_id = (i % 2000)
JOIN vector_noise_pool np ON np.noise_id = ((i / 2000) % 50);

COMMIT;

-- 2. Actualizar estadísticas del optimizador de PostgreSQL
ANALYZE organizacion;
ANALYZE propiedad;
ANALYZE estacion_metro;
ANALYZE valor_uf;

-- Extraer vector de consulta representativo del pool hacia variable psql q_vec
SELECT (base_vec + noise_vec)::text AS q_vec
FROM vector_base_pool bp, vector_noise_pool np
WHERE bp.pool_id = 0 AND np.noise_id = 0 \gset

-- ====================================================================================================
-- ====================================================================================================
-- 3. DIAGNÓSTICO: RECUENTO TOP-200 VECTORIAL CON FILTROS SELECTIVOS (CON Y SIN ITERATIVE SCAN)
--    Filtro altamente selectivo (~54 filas de 100.000, 0.054% del universo total):
--    comuna_id = 1 (Santiago), operacion = 'VENTA', tipo = 'DEPARTAMENTO', dormitorios = 1, precio 3000..3070 UF
-- ====================================================================================================
CREATE TEMP TABLE diagnostico_iterative_scan (
    configuracion text,
    ef_search int,
    iterative_scan text,
    max_scan_tuples text,
    filas_devueltas int
);

-- Caso A: Sin iterative_scan (iterative_scan = off, standard HNSW)
SET hnsw.ef_search = 200;
SET hnsw.iterative_scan = off;

INSERT INTO diagnostico_iterative_scan
SELECT
    'Sin iterative_scan (standard HNSW con filtros selectivos)',
    200,
    'off',
    'default',
    count(*)
FROM (
    SELECT id
    FROM propiedad
    WHERE activo = true
      AND operacion = 'VENTA'
      AND tipo_propiedad = 'DEPARTAMENTO'
      AND comuna_id = 1
      AND dormitorios = 1
      AND precio_uf BETWEEN 3000 AND 3070
    ORDER BY embedding <=> :'q_vec'::vector(768)
    LIMIT 200
) sub;

-- Caso B: Con iterative_scan = relaxed_order y max_scan_tuples ajustado
SET hnsw.ef_search = 200;
SET hnsw.iterative_scan = relaxed_order;
SET hnsw.max_scan_tuples = 50000;

INSERT INTO diagnostico_iterative_scan
SELECT
    'Con iterative_scan = relaxed_order (ajustado para filtros selectivos)',
    200,
    'relaxed_order',
    '50000',
    count(*)
FROM (
    SELECT id
    FROM propiedad
    WHERE activo = true
      AND operacion = 'VENTA'
      AND tipo_propiedad = 'DEPARTAMENTO'
      AND comuna_id = 1
      AND dormitorios = 1
      AND precio_uf BETWEEN 3000 AND 3070
    ORDER BY embedding <=> :'q_vec'::vector(768)
    LIMIT 200
) sub;

\echo '===================================================================================================='
\echo 'DIAGNÓSTICO HNSW: FILAS DEVUELTAS EN TOP-200 CON FILTROS ALTAMENTE SELECTIVOS (~50 FILAS EN TOTAL)'
\echo '===================================================================================================='
SELECT configuracion, ef_search, iterative_scan, max_scan_tuples, filas_devueltas FROM diagnostico_iterative_scan;

-- ====================================================================================================
-- 4. EXPLAIN (ANALYZE, BUFFERS): CONSULTA HÍBRIDA COMPLETA DEL MAPPER (FILTROS + FTS + VECTOR + RRF)
--    MEDICIÓN SIN enable_bitmapscan = off (PERMITIENDO BITMAP SCAN AL OPTIMIZADOR)
--    3 REPETICIONES COMPLETAS CON PLANES ÍNTEGROS Y ESTADÍSTICAS (MEDIANA Y P95)
-- ====================================================================================================
SET hnsw.ef_search = 200;
SET hnsw.iterative_scan = relaxed_order;
SET hnsw.max_scan_tuples = 50000;
SET work_mem = '64MB';
-- NOTA CRÍTICA: NO se desactiva enable_bitmapscan; se mantiene la configuración natural del motor.

-- Warm-up previo para estabilizar buffers en memoria y simular régimen operacional continuo
DO $$
BEGIN
    PERFORM p.id
    FROM propiedad p
    WHERE p.activo = true
      AND p.operacion = 'VENTA'
      AND p.tipo_propiedad = 'DEPARTAMENTO'
      AND p.comuna_id = 1
      AND p.dormitorios = 1
      AND p.precio_uf BETWEEN 3000 AND 3070
    ORDER BY p.embedding <=> (
        SELECT (base_vec + noise_vec) FROM vector_base_pool bp, vector_noise_pool np WHERE bp.pool_id = 0 AND np.noise_id = 0
    )
    LIMIT 200;
END $$;

\echo '===================================================================================================='
\echo 'EXPLAIN (ANALYZE, BUFFERS) - REPETICIÓN 1 DE 3'
\echo '===================================================================================================='
EXPLAIN (ANALYZE, BUFFERS)
WITH uf_actual AS (
    SELECT valor AS uf_hoy 
    FROM valor_uf 
    WHERE fecha <= CURRENT_DATE 
    ORDER BY fecha DESC 
    LIMIT 1
),
top_vec AS (
    SELECT p.id, ROW_NUMBER() OVER (ORDER BY p.embedding <=> :'q_vec'::vector(768)) as r_vec
    FROM propiedad p
    WHERE p.activo = true
      AND p.operacion = 'VENTA'
      AND p.tipo_propiedad = 'DEPARTAMENTO'
      AND p.comuna_id = 1
      AND p.dormitorios = 1
      AND p.precio_uf BETWEEN 3000 AND 3070
    ORDER BY p.embedding <=> :'q_vec'::vector(768)
    LIMIT 200
),
top_fts AS (
    SELECT p.id, ROW_NUMBER() OVER (ORDER BY ts_rank_cd(p.search_vector, websearch_to_tsquery('spanish', 'piscina metro')) DESC) as r_fts
    FROM propiedad p
    WHERE p.activo = true
      AND p.operacion = 'VENTA'
      AND p.tipo_propiedad = 'DEPARTAMENTO'
      AND p.comuna_id = 1
      AND p.dormitorios = 1
      AND p.precio_uf BETWEEN 3000 AND 3070
      AND p.search_vector @@ websearch_to_tsquery('spanish', 'piscina metro')
    ORDER BY ts_rank_cd(p.search_vector, websearch_to_tsquery('spanish', 'piscina metro')) DESC
    LIMIT 200
),
rrf_unificado AS (
    SELECT
        COALESCE(f.id, v.id) AS id,
        (COALESCE(1.0 / (60.0 + f.r_fts), 0.0) + COALESCE(1.0 / (60.0 + v.r_vec), 0.0)) AS rrf_score
    FROM top_fts f
    FULL OUTER JOIN top_vec v ON f.id = v.id
),
metro_detalle AS (
    SELECT DISTINCT ON (p.id)
        p.id AS propiedad_id,
        em.nombre AS nombre_estacion,
        ROUND(ST_Distance(p.ubicacion, em.ubicacion)::numeric, 1)::float8 AS distancia_metros
    FROM (
        SELECT id 
        FROM rrf_unificado 
        ORDER BY rrf_score DESC 
        LIMIT 20 OFFSET 0
    ) u
    JOIN propiedad p ON p.id = u.id
    JOIN estacion_metro em ON ST_DWithin(p.ubicacion, em.ubicacion, 1000)
    WHERE em.activo = TRUE
    ORDER BY p.id, ST_Distance(p.ubicacion, em.ubicacion) ASC
)
SELECT 
    p.id,
    p.codigo_publicacion,
    p.titulo,
    p.operacion,
    p.tipo_propiedad,
    p.precio_uf,
    ROUND(p.precio_uf * COALESCE(uf.uf_hoy, 37850), 0) AS precio_clp_estimado,
    c.nombre AS comuna_nombre,
    md.distancia_metros AS distancia_metro_metros,
    md.nombre_estacion AS nombre_estacion_cercana,
    u.rrf_score
FROM rrf_unificado u
JOIN propiedad p ON p.id = u.id
JOIN comuna c ON c.id = p.comuna_id
LEFT JOIN uf_actual uf ON TRUE
LEFT JOIN metro_detalle md ON md.propiedad_id = p.id
ORDER BY u.rrf_score DESC, p.precio_uf ASC
LIMIT 20 OFFSET 0;

\echo '===================================================================================================='
\echo 'EXPLAIN (ANALYZE, BUFFERS) - REPETICIÓN 2 DE 3'
\echo '===================================================================================================='
EXPLAIN (ANALYZE, BUFFERS)
WITH uf_actual AS (
    SELECT valor AS uf_hoy 
    FROM valor_uf 
    WHERE fecha <= CURRENT_DATE 
    ORDER BY fecha DESC 
    LIMIT 1
),
top_vec AS (
    SELECT p.id, ROW_NUMBER() OVER (ORDER BY p.embedding <=> :'q_vec'::vector(768)) as r_vec
    FROM propiedad p
    WHERE p.activo = true
      AND p.operacion = 'VENTA'
      AND p.tipo_propiedad = 'DEPARTAMENTO'
      AND p.comuna_id = 1
      AND p.dormitorios = 1
      AND p.precio_uf BETWEEN 3000 AND 3070
    ORDER BY p.embedding <=> :'q_vec'::vector(768)
    LIMIT 200
),
top_fts AS (
    SELECT p.id, ROW_NUMBER() OVER (ORDER BY ts_rank_cd(p.search_vector, websearch_to_tsquery('spanish', 'piscina metro')) DESC) as r_fts
    FROM propiedad p
    WHERE p.activo = true
      AND p.operacion = 'VENTA'
      AND p.tipo_propiedad = 'DEPARTAMENTO'
      AND p.comuna_id = 1
      AND p.dormitorios = 1
      AND p.precio_uf BETWEEN 3000 AND 3070
      AND p.search_vector @@ websearch_to_tsquery('spanish', 'piscina metro')
    ORDER BY ts_rank_cd(p.search_vector, websearch_to_tsquery('spanish', 'piscina metro')) DESC
    LIMIT 200
),
rrf_unificado AS (
    SELECT
        COALESCE(f.id, v.id) AS id,
        (COALESCE(1.0 / (60.0 + f.r_fts), 0.0) + COALESCE(1.0 / (60.0 + v.r_vec), 0.0)) AS rrf_score
    FROM top_fts f
    FULL OUTER JOIN top_vec v ON f.id = v.id
),
metro_detalle AS (
    SELECT DISTINCT ON (p.id)
        p.id AS propiedad_id,
        em.nombre AS nombre_estacion,
        ROUND(ST_Distance(p.ubicacion, em.ubicacion)::numeric, 1)::float8 AS distancia_metros
    FROM (
        SELECT id 
        FROM rrf_unificado 
        ORDER BY rrf_score DESC 
        LIMIT 20 OFFSET 0
    ) u
    JOIN propiedad p ON p.id = u.id
    JOIN estacion_metro em ON ST_DWithin(p.ubicacion, em.ubicacion, 1000)
    WHERE em.activo = TRUE
    ORDER BY p.id, ST_Distance(p.ubicacion, em.ubicacion) ASC
)
SELECT 
    p.id,
    p.codigo_publicacion,
    p.titulo,
    p.operacion,
    p.tipo_propiedad,
    p.precio_uf,
    ROUND(p.precio_uf * COALESCE(uf.uf_hoy, 37850), 0) AS precio_clp_estimado,
    c.nombre AS comuna_nombre,
    md.distancia_metros AS distancia_metro_metros,
    md.nombre_estacion AS nombre_estacion_cercana,
    u.rrf_score
FROM rrf_unificado u
JOIN propiedad p ON p.id = u.id
JOIN comuna c ON c.id = p.comuna_id
LEFT JOIN uf_actual uf ON TRUE
LEFT JOIN metro_detalle md ON md.propiedad_id = p.id
ORDER BY u.rrf_score DESC, p.precio_uf ASC
LIMIT 20 OFFSET 0;

\echo '===================================================================================================='
\echo 'EXPLAIN (ANALYZE, BUFFERS) - REPETICIÓN 3 DE 3'
\echo '===================================================================================================='
EXPLAIN (ANALYZE, BUFFERS)
WITH uf_actual AS (
    SELECT valor AS uf_hoy 
    FROM valor_uf 
    WHERE fecha <= CURRENT_DATE 
    ORDER BY fecha DESC 
    LIMIT 1
),
top_vec AS (
    SELECT p.id, ROW_NUMBER() OVER (ORDER BY p.embedding <=> :'q_vec'::vector(768)) as r_vec
    FROM propiedad p
    WHERE p.activo = true
      AND p.operacion = 'VENTA'
      AND p.tipo_propiedad = 'DEPARTAMENTO'
      AND p.comuna_id = 1
      AND p.dormitorios = 1
      AND p.precio_uf BETWEEN 3000 AND 3070
    ORDER BY p.embedding <=> :'q_vec'::vector(768)
    LIMIT 200
),
top_fts AS (
    SELECT p.id, ROW_NUMBER() OVER (ORDER BY ts_rank_cd(p.search_vector, websearch_to_tsquery('spanish', 'piscina metro')) DESC) as r_fts
    FROM propiedad p
    WHERE p.activo = true
      AND p.operacion = 'VENTA'
      AND p.tipo_propiedad = 'DEPARTAMENTO'
      AND p.comuna_id = 1
      AND p.dormitorios = 1
      AND p.precio_uf BETWEEN 3000 AND 3070
      AND p.search_vector @@ websearch_to_tsquery('spanish', 'piscina metro')
    ORDER BY ts_rank_cd(p.search_vector, websearch_to_tsquery('spanish', 'piscina metro')) DESC
    LIMIT 200
),
rrf_unificado AS (
    SELECT
        COALESCE(f.id, v.id) AS id,
        (COALESCE(1.0 / (60.0 + f.r_fts), 0.0) + COALESCE(1.0 / (60.0 + v.r_vec), 0.0)) AS rrf_score
    FROM top_fts f
    FULL OUTER JOIN top_vec v ON f.id = v.id
),
metro_detalle AS (
    SELECT DISTINCT ON (p.id)
        p.id AS propiedad_id,
        em.nombre AS nombre_estacion,
        ROUND(ST_Distance(p.ubicacion, em.ubicacion)::numeric, 1)::float8 AS distancia_metros
    FROM (
        SELECT id 
        FROM rrf_unificado 
        ORDER BY rrf_score DESC 
        LIMIT 20 OFFSET 0
    ) u
    JOIN propiedad p ON p.id = u.id
    JOIN estacion_metro em ON ST_DWithin(p.ubicacion, em.ubicacion, 1000)
    WHERE em.activo = TRUE
    ORDER BY p.id, ST_Distance(p.ubicacion, em.ubicacion) ASC
)
SELECT 
    p.id,
    p.codigo_publicacion,
    p.titulo,
    p.operacion,
    p.tipo_propiedad,
    p.precio_uf,
    ROUND(p.precio_uf * COALESCE(uf.uf_hoy, 37850), 0) AS precio_clp_estimado,
    c.nombre AS comuna_nombre,
    md.distancia_metros AS distancia_metro_metros,
    md.nombre_estacion AS nombre_estacion_cercana,
    u.rrf_score
FROM rrf_unificado u
JOIN propiedad p ON p.id = u.id
JOIN comuna c ON c.id = p.comuna_id
LEFT JOIN uf_actual uf ON TRUE
LEFT JOIN metro_detalle md ON md.propiedad_id = p.id
ORDER BY u.rrf_score DESC, p.precio_uf ASC
LIMIT 20 OFFSET 0;

-- Cálculo automatizado de tiempos de ejecución para mediana y p95
CREATE TEMP TABLE latencias_ejecucion (
    iteracion int,
    duracion_ms numeric
);

DO $$
DECLARE
    t_inicio timestamptz;
    t_fin timestamptz;
    i int;
    v_ms numeric;
BEGIN
    FOR i IN 1..3 LOOP
        t_inicio := clock_timestamp();
        PERFORM 1 FROM (
            WITH uf_actual AS (
                SELECT valor AS uf_hoy 
                FROM valor_uf 
                WHERE fecha <= CURRENT_DATE 
                ORDER BY fecha DESC 
                LIMIT 1
            ),
            top_vec AS (
                SELECT p.id, ROW_NUMBER() OVER (ORDER BY p.embedding <=> (
                    SELECT (base_vec + noise_vec) FROM vector_base_pool bp, vector_noise_pool np WHERE bp.pool_id = 0 AND np.noise_id = 0
                )) as r_vec
                FROM propiedad p
                WHERE p.activo = true
                  AND p.operacion = 'VENTA'
                  AND p.tipo_propiedad = 'DEPARTAMENTO'
                  AND p.comuna_id = 1
                  AND p.dormitorios = 1
                  AND p.precio_uf BETWEEN 3000 AND 3070
                ORDER BY p.embedding <=> (
                    SELECT (base_vec + noise_vec) FROM vector_base_pool bp, vector_noise_pool np WHERE bp.pool_id = 0 AND np.noise_id = 0
                )
                LIMIT 200
            ),
            top_fts AS (
                SELECT p.id, ROW_NUMBER() OVER (ORDER BY ts_rank_cd(p.search_vector, websearch_to_tsquery('spanish', 'piscina metro')) DESC) as r_fts
                FROM propiedad p
                WHERE p.activo = true
                  AND p.operacion = 'VENTA'
                  AND p.tipo_propiedad = 'DEPARTAMENTO'
                  AND p.comuna_id = 1
                  AND p.dormitorios = 1
                  AND p.precio_uf BETWEEN 3000 AND 3070
                  AND p.search_vector @@ websearch_to_tsquery('spanish', 'piscina metro')
                ORDER BY ts_rank_cd(p.search_vector, websearch_to_tsquery('spanish', 'piscina metro')) DESC
                LIMIT 200
            ),
            rrf_unificado AS (
                SELECT
                    COALESCE(f.id, v.id) AS id,
                    (COALESCE(1.0 / (60.0 + f.r_fts), 0.0) + COALESCE(1.0 / (60.0 + v.r_vec), 0.0)) AS rrf_score
                FROM top_fts f
                FULL OUTER JOIN top_vec v ON f.id = v.id
            ),
            metro_detalle AS (
                SELECT DISTINCT ON (p.id)
                    p.id AS propiedad_id,
                    em.nombre AS nombre_estacion,
                    ROUND(ST_Distance(p.ubicacion, em.ubicacion)::numeric, 1)::float8 AS distancia_metros
                FROM (
                    SELECT id 
                    FROM rrf_unificado 
                    ORDER BY rrf_score DESC 
                    LIMIT 20 OFFSET 0
                ) u
                JOIN propiedad p ON p.id = u.id
                JOIN estacion_metro em ON ST_DWithin(p.ubicacion, em.ubicacion, 1000)
                WHERE em.activo = TRUE
                ORDER BY p.id, ST_Distance(p.ubicacion, em.ubicacion) ASC
            )
            SELECT 
                p.id,
                p.codigo_publicacion,
                p.precio_uf,
                c.nombre AS comuna_nombre,
                md.distancia_metros,
                u.rrf_score
            FROM rrf_unificado u
            JOIN propiedad p ON p.id = u.id
            JOIN comuna c ON c.id = p.comuna_id
            LEFT JOIN uf_actual uf ON TRUE
            LEFT JOIN metro_detalle md ON md.propiedad_id = p.id
            ORDER BY u.rrf_score DESC, p.precio_uf ASC
            LIMIT 20 OFFSET 0
        ) consulta;
        t_fin := clock_timestamp();
        v_ms := round((extract(epoch from (t_fin - t_inicio)) * 1000.0)::numeric, 2);
        INSERT INTO latencias_ejecucion (iteracion, duracion_ms) VALUES (i, v_ms);
    END LOOP;
END $$;

\echo '===================================================================================================='
\echo 'ESTADÍSTICAS DE LATENCIA REAL DE LA CONSULTA HÍBRIDA (3 REPETICIONES)'
\echo '===================================================================================================='
SELECT 
    percentile_cont(0.50) WITHIN GROUP (ORDER BY duracion_ms) AS mediana_latencia_ms,
    percentile_cont(0.95) WITHIN GROUP (ORDER BY duracion_ms) AS p95_latencia_ms,
    min(duracion_ms) AS min_latencia_ms,
    max(duracion_ms) AS max_latencia_ms
FROM latencias_ejecucion;

-- ====================================================================================================
-- 5. EVALUACIÓN DE RECALL@10: FIDELIDAD DEL ÍNDICE SOBRE DISTANCIAS CON VECTORES ÚNICOS
-- ====================================================================================================
CREATE TEMP TABLE IF NOT EXISTS benchmark_metrics (
    metrica text,
    recall numeric,
    coincidencias text,
    rotulo text
);
TRUNCATE benchmark_metrics;

DO $$
DECLARE
    v_query vector(768);
    v_exact_dists float8[];
    v_hnsw_dists float8[];
    v_matches int := 0;
    v_recall numeric;
    d float8;
    max_exact_dist float8;
BEGIN
    -- Query vector representativo único
    SELECT (base_vec + noise_vec) INTO v_query
    FROM vector_base_pool bp, vector_noise_pool np
    WHERE bp.pool_id = 7 AND np.noise_id = 13;

    -- 1. Búsqueda exacta exhaustiva (SeqScan forzado)
    SET LOCAL enable_indexscan = off;
    SET LOCAL enable_seqscan = on;

    SELECT array_agg(dist) INTO v_exact_dists
    FROM (
        SELECT (embedding <=> v_query) AS dist
        FROM propiedad
        WHERE activo = true
        ORDER BY embedding <=> v_query
        LIMIT 10
    ) sub;

    -- 2. Búsqueda aproximada HNSW (ef_search = 200, IndexScan forzado)
    SET LOCAL enable_seqscan = off;
    SET LOCAL enable_indexscan = on;
    SET LOCAL hnsw.ef_search = 200;

    SELECT array_agg(dist) INTO v_hnsw_dists
    FROM (
        SELECT (embedding <=> v_query) AS dist
        FROM propiedad
        WHERE activo = true
        ORDER BY embedding <=> v_query
        LIMIT 10
    ) sub;

    -- 3. Medir coincidencias sobre distancias (tolerancia de punto flotante 1e-4)
    max_exact_dist := v_exact_dists[10];
    FOREACH d IN ARRAY v_hnsw_dists
    LOOP
        IF d <= (max_exact_dist + 0.0001) THEN
            v_matches := v_matches + 1;
        END IF;
    END LOOP;

    v_recall := round((v_matches::numeric / 10.0), 3);

    INSERT INTO benchmark_metrics (metrica, recall, coincidencias, rotulo)
    VALUES (
        'Recall@10',
        v_recall,
        v_matches || '/10',
        'FIDELIDAD DEL ÍNDICE ALGORÍTMICO (DISTANCIAS HNSW vs EXHAUSTIVO SOBRE VECTORES ÚNICOS)'
    );

    RAISE NOTICE '=======================================================';
    RAISE NOTICE 'MÉTRICA DE FIDELIDAD DEL ÍNDICE SOBRE DISTANCIAS:';
    RAISE NOTICE 'Recall@10 calculado: % (Coincidencias en distancia: %/10)', v_recall, v_matches;
    RAISE NOTICE 'Distancia max top-10 exacto: %', max_exact_dist;
    RAISE NOTICE 'Rotulado: FIDELIDAD DEL ÍNDICE ALGORÍTMICO, NO CALIDAD SEMÁNTICA';
    RAISE NOTICE '=======================================================';
END $$;

\echo '===================================================================================================='
\echo 'TABLA DE MÉTRICA DE FIDELIDAD (RECALL@10)'
\echo '===================================================================================================='
SELECT metrica, recall, coincidencias, rotulo FROM benchmark_metrics;
