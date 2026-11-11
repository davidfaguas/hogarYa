package cl.hogarya.infrastructure.web;

import cl.hogarya.domain.model.search.PropertySearchCriteria;
import cl.hogarya.domain.model.search.PropertySearchResultDto;
import cl.hogarya.domain.service.search.PropertyIndexingService;
import cl.hogarya.domain.service.search.PropertySearchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Controlador REST para Búsqueda, Ficha y Publicación de Propiedades.
 * Integra búsqueda híbrida vectorial (pgvector HNSW) y textual (PostgreSQL FTS + pg_trgm),
 * filtros duros geodésicos (PostGIS ST_DWithin / Metro), detalle de ficha técnica y
 * publicación de propiedades con indexación semántica asíncrona.
 */
@RestController
@RequestMapping("/api/v1/propiedades")
@Tag(name = "Propiedades", description = "Endpoints de búsqueda híbrida geodésica/semántica, ficha técnica y publicación de propiedades")
public class PropertyController {

    private final PropertySearchService propertySearchService;
    private final PropertyIndexingService propertyIndexingService;
    private final JdbcClient jdbcClient;

    public PropertyController(
            PropertySearchService propertySearchService,
            PropertyIndexingService propertyIndexingService,
            JdbcClient jdbcClient
    ) {
        this.propertySearchService = propertySearchService;
        this.propertyIndexingService = propertyIndexingService;
        this.jdbcClient = jdbcClient;
    }

    public record SearchResponse(
            @Schema(description = "Total de propiedades que satisfacen los filtros duros") long total,
            @Schema(description = "Límite de filas solicitadas") int limit,
            @Schema(description = "Desplazamiento / offset aplicado") int offset,
            @Schema(description = "Resultados ordenados por RRF (Reciprocal Rank Fusion) o criterio elegido") List<PropertySearchResultDto> items
    ) {}

    public record PublishPropertyRequest(
            @Schema(description = "Título descriptivo de la publicación", example = "Moderno departamento 2D2B en Providencia")
            String titulo,
            @Schema(description = "Descripción detallada del inmueble", example = "Excelente departamento luminoso cercano a Metro Tobalaba con estacionamiento")
            String descripcion,
            @Schema(description = "Tipo de inmueble", example = "DEPARTAMENTO")
            String tipoPropiedad,
            @Schema(description = "Operación", example = "VENTA")
            String operacion,
            @Schema(description = "Precio en UF", example = "4200.00")
            BigDecimal precioUf,
            @Schema(description = "Metros cuadrados útiles", example = "75.5")
            BigDecimal m2Util,
            @Schema(description = "Metros cuadrados totales", example = "85.0")
            BigDecimal m2Total,
            @Schema(description = "Cantidad de dormitorios", example = "2")
            Short dormitorios,
            @Schema(description = "Cantidad de baños", example = "2")
            Short banos,
            @Schema(description = "ID de la comuna", example = "1")
            Short comunaId,
            @Schema(description = "Dirección referencial", example = "Av. Providencia 1234")
            String direccionReferencial,
            @Schema(description = "Barrio o sector", example = "Providencia")
            String barrio,
            @Schema(description = "Latitud WGS84", example = "-33.4250")
            Double latitud,
            @Schema(description = "Longitud WGS84", example = "-70.6100")
            Double longitud,
            @Schema(description = "Acepta subsidio DS-19", example = "false")
            Boolean subsidioDs19,
            @Schema(description = "Acepta subsidio DS-1", example = "false")
            Boolean subsidioDs1,
            @Schema(description = "ID de la organización inmobiliaria (opcional)")
            UUID organizacionId
    ) {}

    public record PublishPropertyResponse(
            @Schema(description = "ID asignado a la propiedad") UUID id,
            @Schema(description = "Código de publicación único") String codigoPublicacion,
            @Schema(description = "Estado de publicación e indexación") String mensaje
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

    @PostMapping
    @Operation(summary = "Publicar nueva propiedad", description = "Registra una nueva propiedad en el portal y dispara su indexación semántica asíncrona")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Propiedad publicada exitosamente",
                    content = @Content(schema = @Schema(implementation = PublishPropertyResponse.class))),
            @ApiResponse(responseCode = "400", description = "Datos de publicación inválidos"),
            @ApiResponse(responseCode = "403", description = "No autenticado o token CSRF ausente")
    })
    public ResponseEntity<PublishPropertyResponse> publishNew(
            @RequestBody PublishPropertyRequest req
    ) {
        if (req == null || req.titulo() == null || req.titulo().isBlank()
                || req.precioUf() == null || req.comunaId() == null) {
            return ResponseEntity.badRequest().build();
        }

        UUID id = UUID.randomUUID();
        String codigo = "PROP-" + id.toString().substring(0, 8).toUpperCase();
        UUID orgId = req.organizacionId() != null
                ? req.organizacionId()
                : UUID.fromString("00000000-0000-0000-0000-000000000001");

        double lat = req.latitud() != null ? req.latitud() : -33.4250;
        double lon = req.longitud() != null ? req.longitud() : -70.6100;

        jdbcClient.sql("""
                INSERT INTO propiedad (
                    id, codigo_publicacion, organizacion_id, titulo, descripcion,
                    tipo_propiedad, operacion, estado, precio_uf,
                    m2_util, m2_total, dormitorios, banos,
                    comuna_id, direccion_referencial, barrio, ubicacion,
                    subsidio_ds19, subsidio_ds1, activo, publicada_el, actualizada_el
                ) VALUES (
                    :id, :codigo, :orgId, :titulo, :descripcion,
                    :tipoPropiedad, :operacion, 'USADA', :precioUf,
                    :m2Util, :m2Total, :dormitorios, :banos,
                    :comunaId, :direccion, :barrio,
                    ST_SetSRID(ST_MakePoint(:lon, :lat), 4326)::geography,
                    :ds19, :ds1, TRUE, NOW(), NOW()
                )
                """)
                .param("id", id)
                .param("codigo", codigo)
                .param("orgId", orgId)
                .param("titulo", req.titulo().trim())
                .param("descripcion", req.descripcion() != null ? req.descripcion().trim() : "")
                .param("tipoPropiedad", req.tipoPropiedad() != null ? req.tipoPropiedad().trim() : "DEPARTAMENTO")
                .param("operacion", req.operacion() != null ? req.operacion().trim() : "VENTA")
                .param("precioUf", req.precioUf())
                .param("m2Util", req.m2Util() != null ? req.m2Util() : BigDecimal.valueOf(50.0))
                .param("m2Total", req.m2Total() != null ? req.m2Total() : BigDecimal.valueOf(60.0))
                .param("dormitorios", req.dormitorios() != null ? req.dormitorios() : (short) 1)
                .param("banos", req.banos() != null ? req.banos() : (short) 1)
                .param("comunaId", req.comunaId())
                .param("direccion", req.direccionReferencial() != null ? req.direccionReferencial().trim() : "Sin dirección")
                .param("barrio", req.barrio() != null ? req.barrio().trim() : "Sin sector")
                .param("lon", lon)
                .param("lat", lat)
                .param("ds19", Boolean.TRUE.equals(req.subsidioDs19()))
                .param("ds1", Boolean.TRUE.equals(req.subsidioDs1()))
                .update();

        // Disparo asíncrono con reintentos
        propertyIndexingService.indexPropertyAsync(id);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new PublishPropertyResponse(id, codigo, "Propiedad publicada e indexación semántica iniciada"));
    }

    @PostMapping("/{id}/publicar")
    @Operation(summary = "Publicar propiedad existente", description = "Activa una propiedad y dispara su indexación semántica asíncrona")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Propiedad activada e indexación iniciada"),
            @ApiResponse(responseCode = "404", description = "Propiedad no encontrada")
    })
    public ResponseEntity<PublishPropertyResponse> publishExisting(
            @Parameter(description = "Identificador único de la propiedad") @PathVariable UUID id
    ) {
        int updated = jdbcClient.sql("""
                UPDATE propiedad
                SET activo = TRUE, publicada_el = NOW(), actualizada_el = NOW()
                WHERE id = :id
                """)
                .param("id", id)
                .update();

        if (updated == 0) {
            return ResponseEntity.notFound().build();
        }

        // Disparo asíncrono con reintentos
        propertyIndexingService.indexPropertyAsync(id);

        return ResponseEntity.ok(new PublishPropertyResponse(id, "ACTIVA", "Propiedad activada e indexación semántica iniciada"));
    }
}
