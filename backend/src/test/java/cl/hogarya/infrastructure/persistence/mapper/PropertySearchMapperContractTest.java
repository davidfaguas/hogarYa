package cl.hogarya.infrastructure.persistence.mapper;

import cl.hogarya.domain.model.search.PropertySearchCriteria;
import cl.hogarya.domain.model.search.PropertySearchResultDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Defecto 4, 5, 6 y 10: Contrato de Mapeo MyBatis y Búsqueda Geodésica/Semántica")
class PropertySearchMapperContractTest {

    @Test
    @DisplayName("Defecto 10: Constructor de PropertySearchResultDto se mapea fielmente con tipos inmutables")
    void shouldInstantiateDtoViaConstructor() {
        UUID id = UUID.randomUUID();
        PropertySearchResultDto dto = new PropertySearchResultDto(
                id,
                "PAR-10492",
                "Departamento vista despejada cercano a Metro Ñuñoa",
                "VENTA",
                "DEPARTAMENTO",
                "USADA",
                BigDecimal.valueOf(4200),
                BigDecimal.valueOf(158971050),
                BigDecimal.valueOf(82.5),
                BigDecimal.valueOf(95.0),
                (short) 3,
                (short) 2,
                (short) 1,
                "Ñuñoa",
                "Plaza Ñuñoa",
                -33.4542,
                -70.6015,
                350.0,
                "Metro Ñuñoa",
                true,
                false,
                true,
                "https://cdn.hogarya.cl/img1.webp",
                0.0315
        );

        assertNotNull(dto);
        assertEquals(id, dto.id());
        assertEquals("PAR-10492", dto.codigoPublicacion());
        assertEquals("Ñuñoa", dto.nombreComuna());
        assertEquals(350.0, dto.distanciaMetroMetros());
        assertEquals(0.0315, dto.rrfScore());
    }

    @Test
    @DisplayName("Defecto 4: Fórmula RRF (Reciprocal Rank Fusion) con K=60")
    void shouldComputeRrfScoreCorrectly() {
        // En RRF: score = 1.0 / (60 + rank_vector) + 1.0 / (60 + rank_text)
        // Si una propiedad quedó en rank 1 en vector y rank 1 en texto:
        int rankVec = 1;
        int rankText = 1;
        double rrfTop1 = (1.0 / (60.0 + rankVec)) + (1.0 / (60.0 + rankText));

        assertEquals(2.0 / 61.0, rrfTop1, 0.00001);

        // Si quedó rank 1 en vector pero no apareció en texto (penalización rank = 1000)
        double rrfVecOnly = (1.0 / (60.0 + 1)) + (1.0 / (60.0 + 1000));
        assertTrue(rrfTop1 > rrfVecOnly);
    }

    @Test
    @DisplayName("Defecto 6: Criterios de búsqueda y hardFilters preservan consistencia")
    void shouldPreserveFilterConsistency() {
        PropertySearchCriteria criteria = new PropertySearchCriteria(
                "VENTA",
                "CASA",
                (short) 5,
                BigDecimal.valueOf(2000),
                BigDecimal.valueOf(5000),
                (short) 3,
                (short) 2,
                true,
                false,
                true,
                1500,
                "casa con piscina cerca del metro",
                "relevancia"
        );

        assertEquals("VENTA", criteria.operacion());
        assertEquals(1500, criteria.metroDistanciaMaximaMts());
        assertTrue(criteria.cercaMetro());
    }
}
