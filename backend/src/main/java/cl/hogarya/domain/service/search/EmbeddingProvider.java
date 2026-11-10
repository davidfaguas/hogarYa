package cl.hogarya.domain.service.search;

/**
 * Proveedor de Embeddings Semánticos para Búsqueda Vectorial.
 * 
 * Pipeline de Generación:
 * 1. Propiedades: Al publicar o actualizar una propiedad, se concatenan campos clave normalizados
 *    (título, comuna, barrio, tipo, características principales y descripción) y se genera un
 *    vector denso de 768 dimensiones (ej. Google text-embedding-004 o Multilingual-E5).
 * 2. Consultas: Cuando el usuario ingresa una consulta en lenguaje natural (ej. "departamento en Ñuñoa con terraza"),
 *    la consulta se vectoriza utilizando este proveedor con prefijo de instrucción "Represent this sentence for searching relevant real estate passages:".
 */
public interface EmbeddingProvider {

    /**
     * Genera un embedding vectorial de punto flotante a partir de un texto en lenguaje natural.
     *
     * @param text Texto a vectorizar (consulta o contenido de propiedad)
     * @return Vector de flotantes normalizado L2
     */
    float[] generateEmbedding(String text);

    /**
     * Dimensión fija del espacio vectorial (768 para compatibilidad con esquema pgvector).
     *
     * @return Número de dimensiones del vector
     */
    int getDimensions();
}
