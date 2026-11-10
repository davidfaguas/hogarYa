-- ==============================================================================
-- FLYWAY V1: Esquema Nuclear de HogarYa Chile
-- Corrección Defecto 1: immutable_unaccent() para columna GENERATED search_vector
-- Corrección Defecto 9: Secuencia correlativa para HY-XXXXX y consentimiento versionado
-- ==============================================================================

-- 1. Extensiones requeridas
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "postgis";
CREATE EXTENSION IF NOT EXISTS "vector";
CREATE EXTENSION IF NOT EXISTS "pg_trgm";
CREATE EXTENSION IF NOT EXISTS "unaccent";

-- 2. Función inmutable para unaccent (Corrección Defecto 1)
-- unaccent() nativo de PostgreSQL está marcado como STABLE porque depende de unaccent.rules.
-- Para usarse en columnas GENERATED ALWAYS AS ... STORED debe ser estrictamente IMMUTABLE.
CREATE OR REPLACE FUNCTION public.immutable_unaccent(text)
RETURNS text AS $$
    SELECT public.unaccent($1);
$$ LANGUAGE sql IMMUTABLE PARALLEL SAFE STRICT;

-- 3. Catálogo Territorial Oficial de Chile (SUBDERE / CUT)
CREATE TABLE region (
    id SMALLINT PRIMARY KEY,
    codigo_cut VARCHAR(5) NOT NULL UNIQUE,
    nombre VARCHAR(100) NOT NULL,
    abreviatura VARCHAR(10) NOT NULL
);

CREATE TABLE comuna (
    id SMALLINT PRIMARY KEY,
    region_id SMALLINT NOT NULL REFERENCES region(id),
    codigo_cut VARCHAR(5) NOT NULL UNIQUE,
    nombre VARCHAR(100) NOT NULL,
    provincia VARCHAR(100) NOT NULL,
    zona_subsidio VARCHAR(20) NOT NULL DEFAULT 'CENTRO' -- 'CENTRO', 'NORTE', 'SUR', 'EXTREMA'
);
CREATE INDEX idx_comuna_region ON comuna(region_id);
CREATE INDEX idx_comuna_nombre_trgm ON comuna USING GIN (nombre gin_trgm_ops);

-- 4. Estaciones de Metro y Trenes Suburbanos (EPSG:4326 con trazabilidad de fuente)
CREATE TABLE estacion_metro (
    id SERIAL PRIMARY KEY,
    red VARCHAR(50) NOT NULL,              -- 'Metro de Santiago', 'EFE Valparaíso', 'Biotrén'
    linea VARCHAR(10) NOT NULL,            -- 'L1', 'L2', 'L3', 'L4', 'L4A', 'L5', 'L6'
    nombre VARCHAR(100) NOT NULL,
    comuna_id SMALLINT NOT NULL REFERENCES comuna(id),
    ubicacion GEOGRAPHY(POINT, 4326) NOT NULL,
    fuente_datos VARCHAR(100) NOT NULL,    -- ej: 'GTFS DTPM stops.txt' o 'OpenStreetMap overpass'
    fuente_verificada BOOLEAN NOT NULL DEFAULT TRUE,
    activo BOOLEAN NOT NULL DEFAULT TRUE
);
CREATE INDEX idx_estacion_metro_ubicacion ON estacion_metro USING GIST (ubicacion);

-- 5. Organizaciones y Usuarios
CREATE TABLE organizacion (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    rut_empresa VARCHAR(12) NOT NULL UNIQUE, -- Canónico '12345678-K'
    razon_social VARCHAR(150) NOT NULL,
    nombre_fantasia VARCHAR(100) NOT NULL,
    tipo VARCHAR(30) NOT NULL,               -- 'INMOBILIARIA', 'CORREDORA', 'PARTICULAR'
    insignia VARCHAR(30),                    -- 'HOGARYA_PREMIER', 'INSPIRA'
    telefono_contacto VARCHAR(20) NOT NULL,
    whatsapp_contacto VARCHAR(20) NOT NULL,
    email_contacto VARCHAR(120) NOT NULL,
    creado_el TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE usuario (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    rut VARCHAR(12) UNIQUE,                  -- Canónico '12345678-K'
    email VARCHAR(150) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    nombres VARCHAR(100) NOT NULL,
    apellidos VARCHAR(100) NOT NULL,
    telefono VARCHAR(20),
    rol VARCHAR(30) NOT NULL DEFAULT 'ROLE_PERSONA',
    organizacion_id UUID REFERENCES organizacion(id),
    activo BOOLEAN NOT NULL DEFAULT TRUE,
    creado_el TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 6. Serie Histórica de la Unidad de Fomento (CMF / Banco Central)
CREATE TABLE valor_uf (
    fecha DATE PRIMARY KEY,
    valor NUMERIC(10, 2) NOT NULL CHECK (valor > 0),
    publicado_el TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    fuente VARCHAR(100) NOT NULL DEFAULT 'Banco Central de Chile - BDE'
);
CREATE INDEX idx_valor_uf_fecha_desc ON valor_uf(fecha DESC);

-- 7. Catálogo de Reglas de Subsidio Minvu (DS19 / DS1)
CREATE TABLE subsidio_regla (
    id SERIAL PRIMARY KEY,
    programa VARCHAR(10) NOT NULL,          -- 'DS19', 'DS1'
    tramo VARCHAR(20),                      -- 'TRAMO_1', 'TRAMO_2', 'TRAMO_3', 'INTEGRACION'
    zona_territorial VARCHAR(20) NOT NULL,  -- 'CENTRO', 'NORTE', 'SUR', 'EXTREMA'
    precio_maximo_uf NUMERIC(10, 2) NOT NULL,
    ahorro_minimo_uf NUMERIC(8, 2) NOT NULL,
    decreto_fuente VARCHAR(150) NOT NULL,   -- ej: 'D.S. N° 1 (V. y U.), de 2011'
    activo BOOLEAN NOT NULL DEFAULT TRUE,
    verificado BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT uq_subsidio_regla UNIQUE (programa, tramo, zona_territorial)
);

-- 8. Propiedades
CREATE TABLE propiedad (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    codigo_publicacion VARCHAR(20) NOT NULL UNIQUE,
    organizacion_id UUID NOT NULL REFERENCES organizacion(id),
    titulo VARCHAR(160) NOT NULL,
    descripcion TEXT NOT NULL,
    tipo_propiedad VARCHAR(30) NOT NULL,    -- 'CASA', 'DEPARTAMENTO', etc.
    operacion VARCHAR(20) NOT NULL,         -- 'VENTA', 'ARRIENDO'
    estado VARCHAR(20) NOT NULL,            -- 'NUEVA', 'USADA'
    
    precio_uf NUMERIC(12, 2) NOT NULL CHECK (precio_uf > 0),
    original_precio_uf NUMERIC(12, 2),
    gastos_comunes_clp NUMERIC(10, 0) NOT NULL DEFAULT 0,
    
    m2_util NUMERIC(8, 2) NOT NULL CHECK (m2_util > 0),
    m2_terrazas NUMERIC(8, 2) NOT NULL DEFAULT 0,
    m2_total NUMERIC(8, 2) NOT NULL,
    
    dormitorios SMALLINT NOT NULL CHECK (dormitorios >= 0),
    banos SMALLINT NOT NULL CHECK (banos >= 0),
    estacionamientos SMALLINT NOT NULL DEFAULT 0,
    bodegas SMALLINT NOT NULL DEFAULT 0,
    ano_construccion SMALLINT,
    
    comuna_id SMALLINT NOT NULL REFERENCES comuna(id),
    direccion_referencial VARCHAR(200) NOT NULL,
    barrio VARCHAR(100),
    ubicacion GEOGRAPHY(POINT, 4326) NOT NULL,
    
    subsidio_ds19 BOOLEAN NOT NULL DEFAULT FALSE,
    subsidio_ds1 BOOLEAN NOT NULL DEFAULT FALSE,
    verificada BOOLEAN NOT NULL DEFAULT FALSE,
    destacada BOOLEAN NOT NULL DEFAULT FALSE,
    tiene_tour_virtual BOOLEAN NOT NULL DEFAULT FALSE,
    tiene_video BOOLEAN NOT NULL DEFAULT FALSE,
    
    imagen_portada_url TEXT,
    publicada_el TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    actualizada_el TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    activo BOOLEAN NOT NULL DEFAULT TRUE,

    -- Columna search_vector corregida usando public.immutable_unaccent()
    search_vector TSVECTOR GENERATED ALWAYS AS (
        setweight(to_tsvector('spanish', public.immutable_unaccent(coalesce(titulo, ''))), 'A') ||
        setweight(to_tsvector('spanish', public.immutable_unaccent(coalesce(barrio, ''))), 'B') ||
        setweight(to_tsvector('spanish', public.immutable_unaccent(coalesce(descripcion, ''))), 'C')
    ) STORED,

    embedding VECTOR(768)
);

-- Índices especializados para búsqueda facetada, espacial, textual y vectorial
CREATE INDEX idx_propiedad_busqueda_facetada ON propiedad (
    operacion, 
    tipo_propiedad, 
    estado, 
    precio_uf, 
    dormitorios, 
    banos
) WHERE activo = TRUE;

CREATE INDEX idx_propiedad_comuna_operacion ON propiedad (comuna_id, operacion, precio_uf) 
WHERE activo = TRUE;

CREATE INDEX idx_propiedad_ubicacion_gist ON propiedad USING GIST (ubicacion) 
WHERE activo = TRUE;

CREATE INDEX idx_propiedad_search_vector_gin ON propiedad USING GIN (search_vector);
CREATE INDEX idx_propiedad_titulo_trgm_gin ON propiedad USING GIN (titulo gin_trgm_ops);

CREATE INDEX idx_propiedad_embedding_hnsw ON propiedad USING hnsw (embedding vector_cosine_ops)
WITH (m = 16, ef_construction = 64);

-- 9. Secuencia Correlativa para Tracking HY-XXXXX (Corrección Defecto 9)
CREATE SEQUENCE seq_lead_tracking_code START WITH 10001 INCREMENT BY 1;

CREATE TABLE lead_cotizacion (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tracking_code VARCHAR(20) NOT NULL UNIQUE DEFAULT ('HY-' || nextval('seq_lead_tracking_code')::text),
    propiedad_id UUID NOT NULL REFERENCES propiedad(id),
    usuario_id UUID REFERENCES usuario(id),
    rut_solicitante VARCHAR(12) NOT NULL,
    nombres VARCHAR(100) NOT NULL,
    email VARCHAR(150) NOT NULL,
    telefono VARCHAR(20) NOT NULL,
    mensaje TEXT,
    pie_porcentaje SMALLINT NOT NULL CHECK (pie_porcentaje BETWEEN 10 AND 50),
    con_preaprobacion_bancaria BOOLEAN NOT NULL DEFAULT FALSE,
    consentimiento_hash VARCHAR(64) NOT NULL,
    consentimiento_texto_version VARCHAR(20) NOT NULL DEFAULT 'v2026.1',
    consentimiento_timestamp TIMESTAMPTZ NOT NULL,
    ip_origen INET NOT NULL,
    user_agent TEXT NOT NULL,
    estado VARCHAR(30) NOT NULL DEFAULT 'RECIBIDO',
    creado_el TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_lead_cotizacion_propiedad ON lead_cotizacion(propiedad_id);

CREATE TABLE outbox_evento (
    id BIGSERIAL PRIMARY KEY,
    aggregate_type VARCHAR(50) NOT NULL,
    aggregate_id VARCHAR(50) NOT NULL,
    event_type VARCHAR(50) NOT NULL,
    payload JSONB NOT NULL,
    estado VARCHAR(20) NOT NULL DEFAULT 'PENDIENTE',
    intentos SMALLINT NOT NULL DEFAULT 0,
    ultimo_error TEXT,
    creado_el TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    procesado_el TIMESTAMPTZ
);
CREATE INDEX idx_outbox_pendientes ON outbox_evento (estado, creado_el) 
WHERE estado = 'PENDIENTE';
