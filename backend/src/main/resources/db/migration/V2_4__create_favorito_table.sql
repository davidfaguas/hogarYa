-- ====================================================================================================
-- V2_4: Creación de la tabla de favoritos de usuario
-- Portal Inmobiliario HogarYa Chile
-- ====================================================================================================

CREATE TABLE IF NOT EXISTS favorito (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    usuario_id UUID NOT NULL REFERENCES usuario(id) ON DELETE CASCADE,
    propiedad_id UUID NOT NULL REFERENCES propiedad(id) ON DELETE CASCADE,
    notas TEXT,
    creado_el TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_usuario_propiedad_favorito UNIQUE (usuario_id, propiedad_id)
);

CREATE INDEX IF NOT EXISTS idx_favorito_usuario ON favorito (usuario_id);
CREATE INDEX IF NOT EXISTS idx_favorito_propiedad ON favorito (propiedad_id);

COMMENT ON TABLE favorito IS 'Registro de propiedades guardadas como favoritas por usuarios registrados';
COMMENT ON COLUMN favorito.notas IS 'Notas o comentarios privados del usuario sobre el inmueble';
