package cl.hogarya.domain.model.search;

import java.math.BigDecimal;

/**
 * Criterios de búsqueda y filtros para el motor híbrido.
 */
public record PropertySearchCriteria(
        String operacion,                // 'VENTA' o 'ARRIENDO'
        String tipoPropiedad,            // 'CASA', 'DEPARTAMENTO', 'PARCELA', etc.
        Short comunaId,
        BigDecimal precioMinUf,
        BigDecimal precioMaxUf,
        Short dormitoriosMin,
        Short banosMin,
        Boolean subsidioDs19,
        Boolean subsidioDs1,
        Boolean cercaMetro,              // true: exige proximidad espacial estricta con PostGIS EXISTS
        Integer metroDistanciaMaximaMts, // radio en metros (ej: 800)
        String rawQuery,                 // texto libre ingresado por el usuario
        String sortOption                // 'relevancia', 'menor_precio', 'mayor_precio', 'mas_recientes'
) {}
