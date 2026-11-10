package cl.hogarya.domain.model.search;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Record inmutable que modela la proyección de resultados de búsqueda.
 * Cumple con Defecto 10: Mapeo estricto vía constructor canónico de MyBatis (<constructor><arg>).
 */
public record PropertySearchResultDto(
        UUID id,
        String codigoPublicacion,
        String titulo,
        String operacion,
        String tipoPropiedad,
        String estado,
        BigDecimal precioUf,
        BigDecimal precioClpEstimado,
        BigDecimal m2Util,
        BigDecimal m2Total,
        short dormitorios,
        short banos,
        short estacionamientos,
        String comunaNombre,
        String barrio,
        double latitud,
        double longitud,
        Double distanciaMetroMetros,
        String nombreEstacionCercana,
        boolean tieneTourVirtual,
        boolean tieneVideo,
        boolean verificada,
        String imagenPortadaUrl,
        double rrfScore
) {
    public String nombreComuna() {
        return comunaNombre;
    }
}
