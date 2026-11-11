-- V2_6: Reclamo atómico con caducidad para indexación semántica concurrente
ALTER TABLE propiedad ADD COLUMN IF NOT EXISTS indexando_desde TIMESTAMPTZ;

CREATE INDEX IF NOT EXISTS idx_propiedad_embedding_indexando
    ON propiedad (indexando_desde)
    WHERE embedding IS NULL AND activo = TRUE;
