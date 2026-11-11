package cl.hogarya.infrastructure.web;

import cl.hogarya.domain.model.search.PropertySearchCriteria;
import cl.hogarya.domain.model.search.PropertySearchResultDto;
import cl.hogarya.domain.service.search.PropertySearchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Controlador REST para Búsqueda y Ficha de Propiedades.
 * Integra búsqueda híbrida vectorial (pgvector HNSW) y textual (PostgreSQL FTS + pg_trgm),
 * filtros duros geodésicos (PostGIS ST_DWithin / Metro) y detalle de ficha técnica.
 */
@RestController
@RequestMapping("/api/v1/propiedades")
@Tag(name = "Propiedades", description = "Endpoints de búsqueda híbrida geodésica/semántica y ficha técnica de propiedades")
public class PropertyController {

    private final PropertySearchService propertySearchService;
    private final JdbcClient jdbcClient;

    public PropertyController(PropertySearchService propertySearchService, JdbcClient jdbcClient) {
        this.propertySearchService = propertySearchService;
        this.jdbcClient = jdbcClient;
    }

    public record SearchResponse(
            @Schema(description = "Total de propiedades que satisfacen los filtros duros") long total,
            @Schema(description = "Límite de filas solicitadas") int limit,
            @Schema(description = "Desplazamiento / offset aplicado") int offset,
            @Schema(description = "Resultados ordenados por RRF (Reciprocal Rank Fusion) o criterio elegido") List<PropertySearchResultDto> items
    ) {}

    @GetMapping
    @Operation(
            summary = "Búsqueda híbrida de propiedades",
            description = "Ejecuta búsqueda combinada con filtros relacionales duros, similitud semántica con embeddings y proximidad espacial a Metro"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Listado paginado y total de resultados",
                    content = @Content(schema = @Schema(implementation = SearchResponse.class)))
    })
    public ResponseEntity<SearchResponse> search(
            @Parameter(description = "Tipo de operación (VENTA o ARRIENDO)") @RequestParam(required = false, defaultValue = "VENTA") String operacion,
            @Parameter(description = "Tipo de propiedad (DEPARTAMENTO, CASA, PARCELA, OFICINA)") @RequestParam(required = false) String tipoPropiedad,
            @Parameter(description = "Identificador de la comuna (1 a 346)") @RequestParam(required = false) Short comunaId,
            @Parameter(description = "Precio mínimo en UF") @RequestParam(required = false) BigDecimal precioMinUf,
            @Parameter(description = "Precio máximo en UF") @RequestParam(required = false) BigDecimal precioMaxUf,
            @Parameter(description = "Cantidad mínima de dormitorios") @RequestParam(required = false) Short dormitoriosMin,
            @Parameter(description = "Cantidad mínima de baños") @RequestParam(required = false) Short banosMin,
            @Parameter(description = "Acepta subsidio DS-19") @RequestParam(required = false) Boolean subsidioDs19,
            @Parameter(description = "Acepta subsidio DS-1") @RequestParam(required = false) Boolean subsidioDs1,
            @Parameter(description = "Exige cercanía a estación de Metro") @RequestParam(required = false) Boolean cercaMetro,
            @Parameter(description = "Radio máximo hacia estación de Metro en metros (ej. 800)") @RequestParam(required = false) Integer radioMetroMetros,
            @Parameter(description = "Texto libre o semántico ingresado por el usuario") @RequestParam(required = false) String query,
            @Parameter(description = "Criterio de orden: relevancia, menor_precio, mayor_precio, mas_recientes") @RequestParam(required = false, defaultValue = "relevancia") String sortOption,
            @Parameter(description = "Cantidad máxima de resultados a retornar") @RequestParam(required = false, defaultValue = "20") int limit,
            @Parameter(description = "Desplazamiento para paginación") @RequestParam(required = false, defaultValue = "0") int offset
    ) {
        PropertySearchCriteria criteria = new PropertySearchCriteria(
                operacion, tipoPropiedad, comunaId, precioMinUf, precioMaxUf,
                dormitoriosMin, banosMin, subsidioDs19, subsidioDs1,
                cercaMetro, radioMetroMetros, query, sortOption
        );

        long total = propertySearchService.count(criteria);
        List<PropertySearchResultDto> items = propertySearchService.search(criteria, limit, offset);

        return ResponseEntity.ok(new SearchResponse(total, limit, offset, items));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtener ficha detallada de una propiedad", description = "Retorna la información completa de la propiedad especificada por UUID")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Ficha técnica de la propiedad encontrada"),
            @ApiResponse(responseCode = "404", description = "Propiedad no encontrada")
    })
    public ResponseEntity<Map<String, Object>> getById(
            @Parameter(description = "Identificador único de la propiedad") @PathVariable UUID id
    ) {
        return jdbcClient.sql("""
                SELECT 
                    p.id, p.codigo_publicacion, p.titulo, p.descripcion, p.operacion, 
                    p.tipo_propiedad, p.estado, p.precio_uf,
                    ROUND(p.precio_uf * COALESCE(uf.valor, 37850), 0) AS precio_clp_estimado,
                    p.m2_util, p.m2_total, p.dormitorios, p.banos, p.estacionamientos,
                    p.gastos_comunes_clp,
                    c.nombre AS comuna_nombre, p.barrio, p.direccion_referencial,
                    ST_Y(p.ubicacion::geometry) AS latitud,
                    ST_X(p.ubicacion::geometry) AS longitud,
                    p.subsidio_ds19, p.subsidio_ds1,
                    p.bodegas, p.ano_construccion,
                    p.destacada, p.tiene_tour_virtual, p.tiene_video,
                    p.verificada, p.imagen_portada_url,
                    p.publicada_el, p.actualizada_el
                FROM propiedad p
                JOIN comuna c ON c.id = p.comuna_id
                LEFT JOIN LATERAL (
                    SELECT valor FROM valor_uf WHERE fecha <= CURRENT_DATE ORDER BY fecha DESC LIMIT 1
                ) uf ON TRUE
                WHERE p.id = :id AND p.activo = TRUE
                """)
                .param("id", id)
                .query(new org.springframework.jdbc.core.ColumnMapRowMapper())
                .optional()
                .map(map -> ResponseEntity.ok((Map<String, Object>) map))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
