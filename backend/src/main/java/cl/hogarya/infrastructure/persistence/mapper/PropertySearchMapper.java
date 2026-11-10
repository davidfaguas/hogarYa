package cl.hogarya.infrastructure.persistence.mapper;

import cl.hogarya.domain.model.search.PropertySearchCriteria;
import cl.hogarya.domain.model.search.PropertySearchResultDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface PropertySearchMapper {

    /**
     * Búsqueda Híbrida Corregida:
     * - Defecto 4: Top-K (200) de cada ranking directo sobre la tabla (con HNSW y GIN activos) y FULL OUTER JOIN.
     * - Defecto 5: websearch_to_tsquery('spanish', immutable_unaccent(:rawQuery)) + pg_trgm similarity.
     * - Defecto 6: Filtros duros unificados vía <sql id="hardFilters"> y EXISTS espacial a Metro.
     * - Defecto 10: Mapeo por <constructor><arg> de Records.
     */
    List<PropertySearchResultDto> searchHybrid(
            @Param("criteria") PropertySearchCriteria criteria,
            @Param("embeddingVector") float[] embeddingVector,
            @Param("topK") int topK,
            @Param("limit") int limit,
            @Param("offset") int offset
    );

    /**
     * Conteo exacto que comparte idénticos filtros duros y el EXISTS a Metro sin duplicación.
     */
    long countByCriteria(@Param("criteria") PropertySearchCriteria criteria);
}
