-- ====================================================================================================
-- BENCHMARK DE VOLUMEN (100.000 PROPIEDADES) Y FIDELIDAD DE ÍNDICE HNSW
-- HOGARYA - PORTAL INMOBILIARIO CHILE
--
-- ADVERTENCIA ÉTICA Y TÉCNICA (Regla de Honestidad):
-- Toda medición de latencia o planes de ejecución generados en este script deben interpretarse como:
-- [PROYECCIÓN NO MEDIDA EN ESTE SANDBOX] hasta que sean ejecutados sobre hardware PostgreSQL dedicado.
-- El Recall@10 calculado evalúa la FIDELIDAD DEL ÍNDICE (recall algorítmico HNSW vs búsqueda secuencial
-- exhaustiva exacta), NO la calidad semántica percibida por el usuario final.
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

-- Pool temporal de 500 vectores de 768 dimensiones para generación determinista de alto rendimiento
CREATE TEMP TABLE vector_pool AS
SELECT p AS pool_id,
       ('[' || array_to_string(array_agg(round(((sin(p * 768 + s))::numeric), 4)), ', ') || ']')::vector(768) AS vec
FROM generate_series(0, 499) p,
     generate_series(1, 768) s
GROUP BY p;

-- 1. Generación sintética de 100.000 propiedades representativas del mercado chileno
-- Integridad contra V1: columnas reales (m2_util, m2_total, direccion_referencial, comuna_id smallint)
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
    'Excelente propiedad luminosa cercana a comercio, áreas verdes, servicios y estación de metro. ' ||
    (ARRAY['Con piscina y quincho.', 'Vista despejada a la cordillera.', 'Ideal para familias.', 'Bajos gastos comunes.'])[1 + (i % 4)],
    CASE (i % 4)
        WHEN 0 THEN 'DEPARTAMENTO'
        WHEN 1 THEN 'CASA'
        WHEN 2 THEN 'OFICINA'
        ELSE 'PARCELA'
    END,
    CASE WHEN (i % 4 = 0) THEN 'ARRIENDO' ELSE 'VENTA' END,
    'USADA',
    round((1500 + (random() * 16500))::numeric, 2),
    round((35 + (random() * 200))::numeric, 1),
    round((40 + (random() * 250))::numeric, 1),
    (1 + (i % 5))::smallint,
    (1 + (i % 4))::smallint,
    (1 + (i % 6))::smallint, -- Comuna IDs 1 al 6 (smallint FK a comuna.id)
    'Av. Principal ' || i,
    (ARRAY['Plaza Ñuñoa', 'El Golf', 'San Carlos', 'Bellavista', 'Centro', 'Pajaritos'])[1 + (i % 6)],
    ST_SetSRID(ST_MakePoint(-70.65 + (random() - 0.5) * 0.25, -33.45 + (random() - 0.5) * 0.25), 4326)::geography,
    (i % 10 = 0),
    true,
    vp.vec
FROM generate_series(1, 100000) AS i
JOIN vector_pool vp ON vp.pool_id = (i % 500);

COMMIT;

-- 2. Actualizar estadísticas del optimizador de PostgreSQL
ANALYZE organizacion;
ANALYZE propiedad;
ANALYZE estacion_metro;
ANALYZE valor_uf;

-- Extraer vector de consulta representativo del pool hacia variable psql q_vec
SELECT vec::text AS q_vec FROM vector_pool WHERE pool_id = 0 \gset

-- 3. Configuración para búsqueda vectorial con índice HNSW
SET hnsw.ef_search = 200;
SET work_mem = '64MB';
SET enable_seqscan = off;

-- 4. EXPLAIN (ANALYZE, BUFFERS, FORMAT TEXT)
-- Búsqueda híbrida real: Filtros duros + Proximidad a Metro + Fusión RRF (Vector HNSW + GIN FTS)
EXPLAIN (ANALYZE, BUFFERS)
WITH top_vector AS (
    SELECT id, ROW_NUMBER() OVER (ORDER BY embedding <=> :'q_vec'::vector(768)) as rank_vec
    FROM propiedad
    WHERE activo = true
      AND operacion = 'VENTA'
      AND tipo_propiedad = 'DEPARTAMENTO'
      AND precio_uf BETWEEN 2000 AND 6000
    ORDER BY embedding <=> :'q_vec'::vector(768)
    LIMIT 200
),
top_text AS (
    SELECT id, ROW_NUMBER() OVER (ORDER BY ts_rank_cd(search_vector, websearch_to_tsquery('spanish', 'piscina metro')) DESC) as rank_text
    FROM propiedad
    WHERE activo = true
      AND operacion = 'VENTA'
      AND tipo_propiedad = 'DEPARTAMENTO'
      AND precio_uf BETWEEN 2000 AND 6000
      AND (
          search_vector @@ websearch_to_tsquery('spanish', 'piscina metro')
          OR titulo % 'piscina metro'
      )
    ORDER BY ts_rank_cd(search_vector, websearch_to_tsquery('spanish', 'piscina metro')) DESC
    LIMIT 200
),
rrf_fusion AS (
    SELECT
        COALESCE(v.id, t.id) AS id,
        (COALESCE(1.0 / (60.0 + v.rank_vec), 0.0) + COALESCE(1.0 / (60.0 + t.rank_text), 0.0)) AS rrf_score
    FROM top_vector v
    FULL OUTER JOIN top_text t ON v.id = t.id
)
SELECT p.id, p.codigo_publicacion, p.titulo, p.precio_uf, rrf.rrf_score
FROM rrf_fusion rrf
JOIN propiedad p ON p.id = rrf.id
WHERE EXISTS (
    SELECT 1 FROM estacion_metro em
    WHERE em.activo = true
      AND ST_DWithin(p.ubicacion, em.ubicacion, 1000)
)
ORDER BY rrf.rrf_score DESC
LIMIT 20;

-- ====================================================================================================
-- EVALUACIÓN DE RECALL@10: FIDELIDAD DEL ÍNDICE (HNSW vs BÚSQUEDA EXHAUSTIVA)
-- ====================================================================================================
-- Recall@10 = |Top10_HNSW ∩ Top10_Exacto| / 10.0 con ef_search=200
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
    v_top_hnsw uuid[];
    v_top_exact uuid[];
    v_intersection_count int;
    v_recall numeric;
BEGIN
    SELECT vec INTO v_query FROM vector_pool WHERE pool_id = 0;

    -- 1. Búsqueda con índice HNSW habilitado y ef_search = 200
    SET LOCAL enable_seqscan = off;
    SET LOCAL enable_indexscan = on;
    SET LOCAL hnsw.ef_search = 200;

    SELECT array_agg(id) INTO v_top_hnsw
    FROM (
        SELECT id FROM propiedad
        WHERE activo = true
        ORDER BY embedding <=> v_query
        LIMIT 10
    ) sub;

    -- 2. Búsqueda exhaustiva exacta (fuerza Seq Scan)
    SET LOCAL enable_indexscan = off;
    SET LOCAL enable_seqscan = on;

    SELECT array_agg(id) INTO v_top_exact
    FROM (
        SELECT id FROM propiedad
        WHERE activo = true
        ORDER BY embedding <=> v_query
        LIMIT 10
    ) sub;

    -- 3. Calcular intersección
    SELECT count(*) INTO v_intersection_count
    FROM unnest(v_top_hnsw) h
    WHERE h = ANY(v_top_exact);

    v_recall := round((v_intersection_count::numeric / 10.0), 3);

    INSERT INTO benchmark_metrics (metrica, recall, coincidencias, rotulo)
    VALUES (
        'Recall@10',
        v_recall,
        v_intersection_count || '/10',
        'FIDELIDAD DEL ÍNDICE ALGORÍTMICO (HNSW vs EXHAUSTIVO), NO CALIDAD SEMÁNTICA'
    );

    RAISE NOTICE '=======================================================';
    RAISE NOTICE 'MÉTRICA DE FIDELIDAD DEL ÍNDICE (HNSW vs EXHAUSTIVO):';
    RAISE NOTICE 'Recall@10 calculado: % (Coincidencias: %/10)', v_recall, v_intersection_count;
    RAISE NOTICE 'Rotulado: FIDELIDAD DEL ÍNDICE ALGORÍTMICO, NO CALIDAD SEMÁNTICA';
    RAISE NOTICE '=======================================================';
END $$;

SELECT metrica, recall, coincidencias, rotulo FROM benchmark_metrics;
