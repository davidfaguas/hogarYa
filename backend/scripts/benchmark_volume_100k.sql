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
-- 4. BARRIDO DE SELECTIVIDAD (54, 500, 5.000, 50.000 FILAS): EVALUACIÓN DE PLAN, FILAS Y LATENCIA
-- ====================================================================================================
CREATE TEMP TABLE IF NOT EXISTS barrido_selectividad (
    punto text,
    filas_esperadas int,
    filas_iterative_off int,
    filas_iterative_relaxed int,
    plan_usado text,
    latencia_ms numeric
);
TRUNCATE barrido_selectividad;

DO $$
DECLARE
    v_qvec text;
    v_line text;
    v_plan_type text;
    v_exec_time numeric;
    v_cnt_off int;
    v_cnt_relaxed int;
    v_match text[];
    v_total_esperadas int;
    v_filter text;
    v_punto_label text;
    i int;
BEGIN
    SELECT (base_vec + noise_vec)::text INTO v_qvec
    FROM vector_base_pool bp, vector_noise_pool np
    WHERE bp.pool_id = 0 AND np.noise_id = 0;

    FOR i IN 1..4 LOOP
        IF i = 1 THEN
            v_punto_label := '54 filas (~0.05%)';
            v_filter := 'p.activo = true AND p.operacion = ''VENTA'' AND p.tipo_propiedad = ''DEPARTAMENTO'' AND p.comuna_id = 1 AND p.dormitorios = 1 AND p.precio_uf BETWEEN 3000 AND 3070';
        ELSIF i = 2 THEN
            v_punto_label := '500 filas (~0.5%)';
            v_filter := 'p.activo = true AND p.operacion = ''VENTA'' AND p.tipo_propiedad = ''DEPARTAMENTO'' AND p.comuna_id = 1 AND p.dormitorios = 1 AND p.precio_uf BETWEEN 3000 AND 3656';
        ELSIF i = 3 THEN
            v_punto_label := '5.000 filas (~5.0%)';
            v_filter := 'p.activo = true AND p.operacion = ''VENTA'' AND p.tipo_propiedad IN (''DEPARTAMENTO'', ''CASA'') AND p.comuna_id IN (1, 2, 3) AND p.precio_uf <= 4196';
        ELSE
            v_punto_label := '50.000 filas (~50.0%)';
            v_filter := 'p.activo = true AND p.operacion = ''VENTA''';
        END IF;

        -- Conteo exacto en dataset
        EXECUTE 'SELECT count(*) FROM propiedad p WHERE ' || v_filter INTO v_total_esperadas;

        -- Medición con iterative_scan = off
        SET LOCAL hnsw.ef_search = 200;
        SET LOCAL hnsw.iterative_scan = off;
        EXECUTE 'SELECT count(*) FROM (SELECT p.id FROM propiedad p WHERE ' || v_filter || 
                ' ORDER BY p.embedding <=> ''' || v_qvec || '''::vector(768) LIMIT 200) sub' INTO v_cnt_off;

        -- Medición con iterative_scan = relaxed_order
        SET LOCAL hnsw.ef_search = 200;
        SET LOCAL hnsw.iterative_scan = relaxed_order;
        SET LOCAL hnsw.max_scan_tuples = 50000;
        EXECUTE 'SELECT count(*) FROM (SELECT p.id FROM propiedad p WHERE ' || v_filter || 
                ' ORDER BY p.embedding <=> ''' || v_qvec || '''::vector(768) LIMIT 200) sub' INTO v_cnt_relaxed;

        -- Medición de plan y latencia con EXPLAIN (ANALYZE, BUFFERS)
        v_plan_type := 'Sort / Seq Scan';
        v_exec_time := 0.0;
        FOR v_line IN EXECUTE 'EXPLAIN (ANALYZE, BUFFERS) SELECT p.id FROM propiedad p WHERE ' || v_filter || 
                              ' ORDER BY p.embedding <=> ''' || v_qvec || '''::vector(768) LIMIT 200' LOOP
            IF v_line ~* 'idx_propiedad_embedding_hnsw' THEN
                v_plan_type := 'HNSW Index Scan';
            END IF;
            IF v_line ~* 'Execution Time:' THEN
                v_match := regexp_matches(v_line, 'Execution Time:\s+([0-9.]+)');
                IF v_match IS NOT NULL AND array_length(v_match, 1) >= 1 THEN
                    v_exec_time := v_match[1]::numeric;
                END IF;
            END IF;
        END LOOP;

        INSERT INTO barrido_selectividad (punto, filas_esperadas, filas_iterative_off, filas_iterative_relaxed, plan_usado, latencia_ms)
        VALUES (v_punto_label, v_total_esperadas, v_cnt_off, v_cnt_relaxed, v_plan_type, v_exec_time);
    END LOOP;
END $$;

\echo '===================================================================================================='
\echo 'BARRIDO DE SELECTIVIDAD (54, 500, 5.000, 50.000 FILAS): HNSW vs SORT, FILAS Y LATENCIA'
\echo '===================================================================================================='
SELECT 
    punto,
    filas_esperadas,
    filas_iterative_off,
    filas_iterative_relaxed,
    plan_usado,
    latencia_ms || ' ms' AS latencia
FROM barrido_selectividad;

-- ====================================================================================================
-- 5. BENCHMARK DE LATENCIA RECONCILIADA: 35 REPETICIONES DE LA CONSULTA HÍBRIDA COMPLETA
--    MEDIANA Y P95 CALCULADAS DIRECTAMENTE DESDE LOS EXPLAIN (MISMA FUENTE DE DATOS)
-- ====================================================================================================
CREATE TEMP TABLE IF NOT EXISTS benchmark_runs (
    iteracion int,
    duracion_ms numeric,
    plan_text text
);
TRUNCATE benchmark_runs;

SET hnsw.ef_search = 200;
SET hnsw.iterative_scan = relaxed_order;
SET hnsw.max_scan_tuples = 50000;
SET work_mem = '64MB';

DO $$
DECLARE
    v_qvec text;
    v_sql text;
    v_line text;
    v_cur_plan text;
    v_cur_time numeric;
    v_match text[];
    i int;
BEGIN
    SELECT (base_vec + noise_vec)::text INTO v_qvec
    FROM vector_base_pool bp, vector_noise_pool np
    WHERE bp.pool_id = 0 AND np.noise_id = 0;

    v_sql := $q$
    WITH uf_actual AS (
        SELECT valor AS uf_hoy 
        FROM valor_uf 
        WHERE fecha <= CURRENT_DATE 
        ORDER BY fecha DESC 
        LIMIT 1
    ),
    top_vec AS (
        SELECT p.id, ROW_NUMBER() OVER (ORDER BY p.embedding <=> '$q$ || v_qvec || $q$'::vector(768)) as r_vec
        FROM propiedad p
        WHERE p.activo = true
          AND p.operacion = 'VENTA'
          AND p.tipo_propiedad = 'DEPARTAMENTO'
          AND p.comuna_id = 1
          AND p.dormitorios = 1
          AND p.precio_uf BETWEEN 3000 AND 3070
        ORDER BY p.embedding <=> '$q$ || v_qvec || $q$'::vector(768)
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
            SELECT u.id 
            FROM rrf_unificado u
            JOIN propiedad p ON p.id = u.id
            ORDER BY u.rrf_score DESC, p.precio_uf ASC
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
    LIMIT 20 OFFSET 0
    $q$;

    -- Warm-up (5 iteraciones de calentamiento)
    FOR i IN 1..5 LOOP
        FOR v_line IN EXECUTE 'EXPLAIN (ANALYZE, BUFFERS) ' || v_sql LOOP
        END LOOP;
    END LOOP;

    -- 35 repeticiones de medición continua
    FOR i IN 1..35 LOOP
        v_cur_plan := '';
        v_cur_time := 0.0;
        FOR v_line IN EXECUTE 'EXPLAIN (ANALYZE, BUFFERS) ' || v_sql LOOP
            v_cur_plan := v_cur_plan || v_line || E'\n';
            IF v_line ~* 'Execution Time:' THEN
                v_match := regexp_matches(v_line, 'Execution Time:\s+([0-9.]+)');
                IF v_match IS NOT NULL AND array_length(v_match, 1) >= 1 THEN
                    v_cur_time := v_match[1]::numeric;
                END IF;
            END IF;
        END LOOP;
        INSERT INTO benchmark_runs (iteracion, duracion_ms, plan_text)
        VALUES (i, v_cur_time, v_cur_plan);
    END LOOP;
END $$;

\echo '===================================================================================================='
\echo 'ESTADÍSTICAS RECONCILIADAS DE LATENCIA DE LA CONSULTA HÍBRIDA (35 REPETICIONES)'
\echo '===================================================================================================='
SELECT 
    count(*) AS repeticiones_totales,
    percentile_cont(0.50) WITHIN GROUP (ORDER BY duracion_ms) || ' ms' AS mediana_latencia_ms,
    percentile_cont(0.95) WITHIN GROUP (ORDER BY duracion_ms) || ' ms' AS p95_latencia_ms,
    min(duracion_ms) || ' ms' AS min_latencia_ms,
    max(duracion_ms) || ' ms' AS max_latencia_ms
FROM benchmark_runs;

\echo '===================================================================================================='
\echo 'DISTRIBUCIÓN DE LAS 35 EJECUCIONES (EXTRACTO COMPLETO DE LATENCIAS RECONCILIADAS):'
\echo '===================================================================================================='
SELECT iteracion, duracion_ms || ' ms' AS duracion FROM benchmark_runs ORDER BY iteracion;

\echo '===================================================================================================='
\echo 'PLAN DE EJECUCIÓN COMPLETO (ÍNTEGRO) - EJECUCIÓN MÁS LENTA (MÁXIMA LATENCIA)'
\echo '===================================================================================================='
SELECT plan_text FROM benchmark_runs ORDER BY duracion_ms DESC LIMIT 1;

\echo '===================================================================================================='
\echo 'PLAN DE EJECUCIÓN COMPLETO (ÍNTEGRO) - EJECUCIÓN MÁS RÁPIDA (MÍNIMA LATENCIA)'
\echo '===================================================================================================='
SELECT plan_text FROM benchmark_runs ORDER BY duracion_ms ASC LIMIT 1;

-- ====================================================================================================
-- 6. EVALUACIÓN DE RECALL@10: FIDELIDAD DEL ÍNDICE SOBRE DISTANCIAS CON VECTORES ÚNICOS
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
