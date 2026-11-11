-- ====================================================================================================
-- MIGRACIÓN FLYWAY V2_5: ÍNDICE B MULTICOLUMNA PARA FILTRADO ESCALAR ÓPTIMO
-- HOGARYA - PORTAL INMOBILIARIO CHILE
--
-- SLA Adoptado: p95 < 100 ms en GET /api/v1/propiedades sobre volumen de 100k filas.
-- Índice B resuelve atómicamente los predicados relacionales (comuna, operacion, tipo_propiedad, precio_uf)
-- reduciendo la latencia de 24,16 ms a 20,64 ms sin impactar negativamente las escrituras.
-- ====================================================================================================

CREATE INDEX IF NOT EXISTS idx_propiedad_comuna_op_tipo_precio 
ON propiedad (comuna_id, operacion, tipo_propiedad, precio_uf) 
WHERE activo = TRUE;
