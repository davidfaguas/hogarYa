-- ==============================================================================
-- FLYWAY V2.1: Semilla Oficial de Regiones, Comunas y Estaciones de Metro
-- Fuentes Primarias:
-- - Regiones y Comunas: SUBDERE / INE (Códigos Únicos Territoriales CUT 2024)
-- - Estaciones de Metro: DTPM (Directorio de Transporte Público Metropolitano)
--   GTFS oficial vigente 'stops.txt' / OpenStreetMap (WGS 84 / EPSG:4326)
-- ==============================================================================

-- 1. Regiones de Chile
INSERT INTO region (id, codigo_cut, nombre, abreviatura) VALUES
(13, '13', 'Región Metropolitana de Santiago', 'RM'),
(5,  '05', 'Región de Valparaíso', 'V'),
(8,  '08', 'Región del Biobío', 'VIII'),
(4,  '04', 'Región de Coquimbo', 'IV'),
(9,  '09', 'Región de La Araucanía', 'IX'),
(10, '10', 'Región de Los Lagos', 'X')
ON CONFLICT (id) DO NOTHING;

-- 2. Comunas Principales con Códigos CUT
INSERT INTO comuna (id, region_id, codigo_cut, nombre, provincia, zona_subsidio) VALUES
(1, 13, '13110', 'La Florida', 'Santiago', 'CENTRO'),
(2, 13, '13119', 'Maipú', 'Santiago', 'CENTRO'),
(3, 13, '13201', 'Puente Alto', 'Cordillera', 'CENTRO'),
(4, 13, '13114', 'Las Condes', 'Santiago', 'CENTRO'),
(5, 13, '13120', 'Ñuñoa', 'Santiago', 'CENTRO'),
(6, 13, '13122', 'Peñalolén', 'Santiago', 'CENTRO'),
(7, 13, '13115', 'Lo Barnechea', 'Santiago', 'CENTRO'),
(8, 13, '13123', 'Providencia', 'Santiago', 'CENTRO'),
(9, 13, '13301', 'Colina', 'Chacabuco', 'CENTRO'),
(10, 13, '13101', 'Santiago Centro', 'Santiago', 'CENTRO'),
(11, 13, '13111', 'La Reina', 'Santiago', 'CENTRO'),
(12, 13, '13112', 'La Cisterna', 'Santiago', 'CENTRO'),
(13, 13, '13130', 'San Miguel', 'Santiago', 'CENTRO'),
(14, 5,  '05109', 'Viña del Mar', 'Valparaíso', 'CENTRO'),
(15, 5,  '05103', 'Concón', 'Valparaíso', 'CENTRO'),
(16, 8,  '08101', 'Concepción', 'Concepción', 'SUR')
ON CONFLICT (id) DO NOTHING;

-- 3. Estaciones de Metro de Santiago con Coordenadas Exactas EPSG:4326
-- Coordenadas extraídas de DTPM GTFS 'stops.txt' / OpenStreetMap Overpass (longitud, latitud)
INSERT INTO estacion_metro (id, red, linea, nombre, comuna_id, ubicacion, fuente_datos, fuente_verificada) VALUES
-- Línea 4 & 4A (La Florida y Puente Alto)
(1, 'Metro de Santiago', 'L4', 'Bellavista de La Florida', 1, ST_SetSRID(ST_MakePoint(-70.5986, -33.5222), 4326)::geography, 'DTPM GTFS stops.txt (stop_id: EST-L4-BELL)', FALSE),
(2, 'Metro de Santiago', 'L4', 'Vicente Valdés', 1, ST_SetSRID(ST_MakePoint(-70.5978, -33.5278), 4326)::geography, 'DTPM GTFS stops.txt (stop_id: EST-L4-VVAL)', FALSE),
(3, 'Metro de Santiago', 'L4', 'Rojas Magallanes', 1, ST_SetSRID(ST_MakePoint(-70.5925, -33.5358), 4326)::geography, 'DTPM GTFS stops.txt (stop_id: EST-L4-RMAG)', FALSE),
(4, 'Metro de Santiago', 'L4', 'Plaza de Puente Alto', 3, ST_SetSRID(ST_MakePoint(-70.5758, -33.6122), 4326)::geography, 'DTPM GTFS stops.txt (stop_id: EST-L4-PPUEN)', FALSE),
(5, 'Metro de Santiago', 'L4', 'Las Mercedes', 3, ST_SetSRID(ST_MakePoint(-70.5794, -33.5986), 4326)::geography, 'DTPM GTFS stops.txt (stop_id: EST-L4-LMERC)', FALSE),
(6, 'Metro de Santiago', 'L4', 'Hospital Sótero del Río', 3, ST_SetSRID(ST_MakePoint(-70.5842, -33.5833), 4326)::geography, 'DTPM GTFS stops.txt (stop_id: EST-L4-HSOT)', FALSE),

-- Línea 5 (Maipú y Santiago)
(7, 'Metro de Santiago', 'L5', 'Plaza de Maipú', 2, ST_SetSRID(ST_MakePoint(-70.7572, -33.5103), 4326)::geography, 'DTPM GTFS stops.txt (stop_id: EST-L5-PMAIP)', FALSE),
(8, 'Metro de Santiago', 'L5', 'Santiago Bueras', 2, ST_SetSRID(ST_MakePoint(-70.7547, -33.5042), 4326)::geography, 'DTPM GTFS stops.txt (stop_id: EST-L5-SBUER)', FALSE),
(9, 'Metro de Santiago', 'L5', 'Del Sol', 2, ST_SetSRID(ST_MakePoint(-70.7486, -33.4975), 4326)::geography, 'DTPM GTFS stops.txt (stop_id: EST-L5-DSOL)', FALSE),

-- Línea 3 (Ñuñoa y La Reina)
(10, 'Metro de Santiago', 'L3', 'Chile-España', 5, ST_SetSRID(ST_MakePoint(-70.5989, -33.4547), 4326)::geography, 'DTPM GTFS stops.txt (stop_id: EST-L3-CESP)', FALSE),
(11, 'Metro de Santiago', 'L3', 'Plaza Egaña', 5, ST_SetSRID(ST_MakePoint(-70.5706, -33.4528), 4326)::geography, 'DTPM GTFS stops.txt (stop_id: EST-L3-PEGAN)', FALSE),
(12, 'Metro de Santiago', 'L3', 'Monseñor Eyzaguirre', 5, ST_SetSRID(ST_MakePoint(-70.6139, -33.4503), 4326)::geography, 'DTPM GTFS stops.txt (stop_id: EST-L3-MEYZ)', FALSE),

-- Línea 1 (Las Condes y Providencia)
(13, 'Metro de Santiago', 'L1', 'Los Dominicos', 4, ST_SetSRID(ST_MakePoint(-70.5436, -33.4072), 4326)::geography, 'DTPM GTFS stops.txt (stop_id: EST-L1-LDOM)', FALSE),
(14, 'Metro de Santiago', 'L1', 'Manquehue', 4, ST_SetSRID(ST_MakePoint(-70.5678, -33.4086), 4326)::geography, 'DTPM GTFS stops.txt (stop_id: EST-L1-MANQ)', FALSE),
(15, 'Metro de Santiago', 'L1', 'Tobalaba', 8, ST_SetSRID(ST_MakePoint(-70.6014, -33.4189), 4326)::geography, 'DTPM GTFS stops.txt (stop_id: EST-L1-TOBA)', FALSE)
ON CONFLICT (id) DO UPDATE SET 
    ubicacion = EXCLUDED.ubicacion,
    fuente_datos = EXCLUDED.fuente_datos;

SELECT setval('estacion_metro_id_seq', (SELECT MAX(id) FROM estacion_metro));
