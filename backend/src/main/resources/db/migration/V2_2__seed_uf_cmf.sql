-- ==============================================================================
-- FLYWAY V2.2: Semilla de la Unidad de Fomento (UF)
-- Trazabilidad: Marcada como SINTETICO y verificado = FALSE hasta cotejo
-- exhaustivo día por día con la serie oficial descargada del Banco Central de Chile
-- mediante el script complementario scripts/load_cmf_uf.sql.
-- ==============================================================================

ALTER TABLE valor_uf ADD COLUMN IF NOT EXISTS verificado BOOLEAN NOT NULL DEFAULT FALSE;

INSERT INTO valor_uf (fecha, valor, fuente, verificado) VALUES
('2026-01-01', 39731.79, 'SINTETICO', FALSE),
('2026-02-01', 39703.50, 'SINTETICO', FALSE),
('2026-03-01', 39796.31, 'SINTETICO', FALSE),
('2026-04-01', 39841.72, 'SINTETICO', FALSE),
('2026-05-01', 40133.50, 'SINTETICO', FALSE),
('2026-06-01', 40627.62, 'SINTETICO', FALSE),
('2026-07-01', 40823.03, 'SINTETICO', FALSE),
('2026-08-01', 40844.79, 'SINTETICO', FALSE),
('2026-09-01', 40875.09, 'SINTETICO', FALSE),
('2026-10-01', 41065.38, 'SINTETICO', FALSE),
('2026-11-01', 41252.94, 'SINTETICO', FALSE),
('2026-11-10', 41261.20, 'SINTETICO', FALSE),
('2026-12-01', 41446.78, 'SINTETICO', FALSE)
ON CONFLICT (fecha) DO UPDATE SET 
    valor = EXCLUDED.valor,
    fuente = EXCLUDED.fuente,
    verificado = EXCLUDED.verificado;
