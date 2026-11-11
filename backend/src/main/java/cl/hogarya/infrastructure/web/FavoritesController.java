package cl.hogarya.infrastructure.web;

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
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Controlador REST para Favoritos.
 * Permite a los usuarios autenticados gestionar su lista de propiedades guardadas.
 */
@RestController
@RequestMapping("/api/v1/favoritos")
@Tag(name = "Favoritos", description = "Endpoints de gestión de propiedades guardadas como favoritas por el usuario")
public class FavoritesController {

    private final JdbcClient jdbcClient;

    public FavoritesController(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public record AddFavoriteRequest(
            @Schema(description = "Identificador de la propiedad a guardar", example = "11111111-1111-1111-1111-111111111111")
            UUID propiedadId,
            @Schema(description = "Nota opcional del usuario", example = "Me gustó por la cercanía al metro")
            String notas
    ) {}

    public record FavoriteItem(
            UUID id,
            UUID propiedadId,
            String codigoPublicacion,
            String titulo,
            String tipoPropiedad,
            java.math.BigDecimal precioUf,
            String comunaNombre,
            String notas,
            java.time.Instant guardadoEl
    ) {}

    @GetMapping
    @Operation(summary = "Listar favoritos del usuario autenticado", description = "Retorna las propiedades marcadas como favoritas por el usuario actual")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Listado de favoritos obtenido exitosamente"),
            @ApiResponse(responseCode = "401", description = "No autenticado")
    })
    public ResponseEntity<List<Map<String, Object>>> getFavorites(Authentication authentication) {
        String username = authentication != null ? authentication.getName() : "demo@hogarya.cl";

        Optional<UUID> usuarioIdOpt = jdbcClient.sql("SELECT id FROM usuario WHERE email = :email")
                .param("email", username)
                .query(UUID.class)
                .optional();

        if (usuarioIdOpt.isEmpty()) {
            return ResponseEntity.ok(List.of());
        }

        List<Map<String, Object>> favorites = jdbcClient.sql("""
                SELECT 
                    f.id AS favorito_id,
                    p.id AS propiedad_id,
                    p.codigo_publicacion,
                    p.titulo,
                    p.tipo_propiedad,
                    p.operacion,
                    p.precio_uf,
                    c.nombre AS comuna_nombre,
                    p.imagen_portada_url,
                    f.notas,
                    f.creado_el AS guardado_el
                FROM favorito f
                JOIN propiedad p ON p.id = f.propiedad_id
                JOIN comuna c ON c.id = p.comuna_id
                WHERE f.usuario_id = :userId
                ORDER BY f.creado_el DESC
                """)
                .param("userId", usuarioIdOpt.get())
                .query()
                .listOfRows();

        return ResponseEntity.ok(favorites);
    }

    @PostMapping
    @Operation(summary = "Guardar propiedad en favoritos", description = "Registra una propiedad en la lista de favoritos del usuario")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Propiedad agregada a favoritos"),
            @ApiResponse(responseCode = "400", description = "Solicitud inválida"),
            @ApiResponse(responseCode = "401", description = "No autenticado")
    })
    public ResponseEntity<Map<String, String>> addFavorite(
            @RequestBody AddFavoriteRequest request,
            Authentication authentication
    ) {
        if (request == null || request.propiedadId() == null) {
            return ResponseEntity.badRequest().body(Map.of("mensaje", "propiedadId es obligatorio"));
        }

        String username = authentication != null ? authentication.getName() : "demo@hogarya.cl";

        Optional<UUID> usuarioIdOpt = jdbcClient.sql("SELECT id FROM usuario WHERE email = :email")
                .param("email", username)
                .query(UUID.class)
                .optional();

        if (usuarioIdOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("mensaje", "Usuario no encontrado"));
        }

        jdbcClient.sql("""
                INSERT INTO favorito (id, usuario_id, propiedad_id, notas, creado_el)
                VALUES (gen_random_uuid(), :userId, :propId, :notas, CURRENT_TIMESTAMP)
                ON CONFLICT (usuario_id, propiedad_id) DO UPDATE SET notas = EXCLUDED.notas
                """)
                .param("userId", usuarioIdOpt.get())
                .param("propId", request.propiedadId())
                .param("notas", request.notas())
                .update();

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("mensaje", "Propiedad guardada en favoritos"));
    }

    @DeleteMapping("/{propiedadId}")
    @Operation(summary = "Eliminar propiedad de favoritos", description = "Remueve una propiedad previamente guardada")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Propiedad removida de favoritos"),
            @ApiResponse(responseCode = "401", description = "No autenticado")
    })
    public ResponseEntity<Void> removeFavorite(
            @Parameter(description = "Identificador de la propiedad a remover") @PathVariable UUID propiedadId,
            Authentication authentication
    ) {
        String username = authentication != null ? authentication.getName() : "demo@hogarya.cl";

        Optional<UUID> usuarioIdOpt = jdbcClient.sql("SELECT id FROM usuario WHERE email = :email")
                .param("email", username)
                .query(UUID.class)
                .optional();

        usuarioIdOpt.ifPresent(userId -> jdbcClient.sql("DELETE FROM favorito WHERE usuario_id = :userId AND propiedad_id = :propId")
                .param("userId", userId)
                .param("propId", propiedadId)
                .update());

        return ResponseEntity.noContent().build();
    }
}
