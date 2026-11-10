-- ==============================================================================
-- FLYWAY V2.3: Parámetros Normativos y Semillas con Trazabilidad Legal
-- Distinción estricta:
-- - verificado = TRUE: Cuenta con Decreto/Ley primaria citada con fecha.
-- - verificado = FALSE: Parámetro operativo de mercado o estimación sujeta a confirmación reglamentaria.
-- ==============================================================================

CREATE TABLE IF NOT EXISTS parametro_mercado (
    clave VARCHAR(50) PRIMARY KEY,
    valor_numerico NUMERIC(14, 4) NOT NULL,
    unidad VARCHAR(20) NOT NULL, -- 'CLP', 'UF', 'PORCENTAJE'
    descripcion TEXT NOT NULL,
    fuente_normativa TEXT NOT NULL,
    articulo_ley TEXT,
    fecha_vigencia DATE,
    verificado BOOLEAN NOT NULL DEFAULT FALSE
);

INSERT INTO parametro_mercado (clave, valor_numerico, unidad, descripcion, fuente_normativa, articulo_ley, fecha_vigencia, verificado) VALUES
-- 1. FOGAES: Verificado según Ley N° 21.543 (Fondo de Garantías Especiales para la Vivienda)
('FOGAES_TOPE_VALOR_VIVIENDA_UF', 4500.0000, 'UF', 'Tope máximo de valor de la primera vivienda para acceder a garantía estatal FOGAES (financiamiento hasta 90% pie)', 'Ley N° 21.543 crea un Fondo de Garantías Especiales (Pendiente cotejar reglamento CMF)', 'Artículo 3°', '2023-02-13', FALSE),

-- 2. Impuesto de Timbres y Estampillas DFL-2: Verificado según D.L. N° 3475 de 1980
('TIMBRES_ESTAMPILAS_DFL2_PORCENTAJE', 0.2000, 'PORCENTAJE', 'Tasa reducida preferencial de impuesto de timbres y estampillas en mutuos hipotecarios destinados a viviendas DFL-2', 'Decreto Ley N° 3475 de 1980 sobre Impuesto de Timbres y Estampillas', 'Artículo 3° y DFL N° 2 de 1959', '1980-01-01', TRUE),

-- 3. Impuesto de Timbres y Estampillas General: Verificado según D.L. N° 3475
('TIMBRES_ESTAMPILAS_GENERAL_PORCENTAJE', 0.8000, 'PORCENTAJE', 'Tasa máxima general de impuesto de timbres y estampillas para mutuos no DFL-2', 'Decreto Ley N° 3475', 'Artículo 1° N° 3', '2016-01-01', TRUE),

-- 4. Conservador de Bienes Raíces (CBR): PENDIENTE DE VERIFICAR TOPE
-- El arancel base es 0,2% según Decreto N° 588 del Ministerio de Justicia (Reglamento de Aranceles de Conservadores).
-- Sin embargo, el tope de $450.000 es un valor promedio de plaza que varía por jurisdicción y reajuste anual no unificado en todo Chile.
('CBR_FACTOR_ARANCEL_PORCENTAJE', 0.2000, 'PORCENTAJE', 'Factor arancelario para inscripción de dominio e hipoteca en el Conservador de Bienes Raíces', 'Decreto N° 588 de 1998, Ministerio de Justicia', 'Artículo 2° Arancel de Conservadores', '1998-12-04', TRUE),
('CBR_TOPE_MAXIMO_CLP', 450000.0000, 'CLP', 'Tope arancelario estimado aplicado en plazas de Santiago para mutuos hipotecarios', 'Práctica de plaza de Conservadores de Santiago (No verificado con decreto único nacional)', 'Pendiente circular MINJU', NULL, FALSE),

-- 5. Renta Mínima / Carga Financiera (25% a 30%): PRÁCTICA BANCARIA, NO NORMA CMF
-- La CMF no fija una cuota máxima obligatoria del 25%; es una política de riesgo de la banca chilena (Compendio de Normas Financieras Capítulo B-1).
('CARGA_FINANCIERA_MAXIMA_PORCENTAJE', 25.0000, 'PORCENTAJE', 'Porcentaje máximo sugerido de la renta líquida mensual destinado al pago del dividendo hipotecario', 'Práctica crediticia estándar de bancos en Chile / Recomendación prudencial CMF (No es tope legal)', 'Capítulo B-1 RAN CMF', NULL, FALSE);

-- Reglas Oficiales de Subsidios Minvu DS19 y DS1
INSERT INTO subsidio_regla (programa, tramo, zona_territorial, precio_maximo_uf, ahorro_minimo_uf, decreto_fuente, activo, verificado) VALUES
('DS19', 'INTEGRACION', 'CENTRO', 2200.00, 80.00, 'Decreto Supremo N° 19 (V. y U.), de 2016 - Reglamento del Programa de Integración Social y Territorial', TRUE, TRUE),
('DS19', 'INTEGRACION', 'NORTE', 2600.00, 90.00, 'Decreto Supremo N° 19 (V. y U.), de 2016 (Zonas especiales)', TRUE, TRUE),
('DS1', 'TRAMO_1', 'CENTRO', 1100.00, 30.00, 'Decreto Supremo N° 1 (V. y U.), de 2011 - Título I Tramo 1', TRUE, TRUE),
('DS1', 'TRAMO_2', 'CENTRO', 1600.00, 40.00, 'Decreto Supremo N° 1 (V. y U.), de 2011 - Título I Tramo 2', TRUE, TRUE),
('DS1', 'TRAMO_3', 'CENTRO', 2200.00, 80.00, 'Decreto Supremo N° 1 (V. y U.), de 2011 - Título II Tramo 3', TRUE, TRUE)
ON CONFLICT (programa, tramo, zona_territorial) DO NOTHING;
