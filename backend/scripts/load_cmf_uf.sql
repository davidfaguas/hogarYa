-- ==============================================================================
-- SCRIPT DE INGESTA OFICIAL CMF: Carga Masiva de la Serie Diaria de la UF
-- Permite cargar directamente la planilla descargada desde la Base de Datos
-- Estadísticos del Banco Central de Chile (BDE) / CMF sin inventar valores.
-- ==============================================================================

-- 1. Tabla temporal de carga
CREATE TEMP TABLE staging_uf (
    fecha_raw TEXT,
    valor_raw TEXT
);

-- 2. Copiar desde el archivo CSV oficial descargado (ajustar ruta local al ejecutar)
-- Formato esperado: 'YYYY-MM-DD;39796.31' o 'DD-MM-YYYY;39.796,31'
-- COPY staging_uf (fecha_raw, valor_raw) FROM '/ruta/descargas/serie_uf_cmf.csv' WITH (FORMAT csv, DELIMITER ';', HEADER true);

-- 3. Inserción con sanitización y casteo a DATE y NUMERIC(10,2)
-- INSERT INTO valor_uf (fecha, valor, fuente)
-- SELECT 
--     TO_DATE(fecha_raw, 'YYYY-MM-DD'),
--     REPLACE(REPLACE(valor_raw, '.', ''), ',', '.')::NUMERIC(10,2),
--     'Planilla Oficial CMF/Banco Central importada el ' || CURRENT_DATE
-- FROM staging_uf
-- ON CONFLICT (fecha) DO UPDATE SET 
--     valor = EXCLUDED.valor,
--     fuente = EXCLUDED.fuente;

-- DROP TABLE staging_uf;
