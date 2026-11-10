package cl.hogarya.domain.service.subsidy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Servicio de Elegibilidad de Subsidios Minvu (DS19 y DS1)")
class MinvuSubsidyEligibilityServiceTest {

    private MinvuSubsidyEligibilityService subsidyService;

    @BeforeEach
    void setUp() {
        subsidyService = new MinvuSubsidyEligibilityService();
    }

    @Test
    @DisplayName("DS19: Proyecto con convenio, valor <= 2.200 UF (Zona Central) -> Elegible")
    void shouldBeEligibleForDs19WithinZoneLimit() {
        var result = subsidyService.evaluateDs19(
                BigDecimal.valueOf(1850), // precio UF
                "13120",                  // CUT Ñuñoa (RM)
                true                      // proyectoInmobiliarioConConvenio
        );

        assertTrue(result.elegible());
        assertEquals("DS19_INTEGRACION_SOCIAL", result.codigoSubsidio());
        assertTrue(result.subsidioEstimadoUf().compareTo(BigDecimal.ZERO) > 0);
    }

    @Test
    @DisplayName("DS19: No elegible si el proyecto no cuenta con convenio Minvu activo")
    void shouldNotBeEligibleForDs19WithoutConvenio() {
        var result = subsidyService.evaluateDs19(
                BigDecimal.valueOf(1850),
                "13120",
                false // Sin convenio
        );

        assertFalse(result.elegible());
        assertTrue(result.motivo().contains("convenio"));
    }

    @Test
    @DisplayName("DS1 Tramo 1: Valor <= 1.100 UF y RSH <= 60% -> Elegible")
    void shouldBeEligibleForDs1Tramo1() {
        var result = subsidyService.evaluateDs1(
                BigDecimal.valueOf(950), // <= 1.100 UF
                1,                       // Tramo 1
                40,                      // 40% RSH (<= 60%)
                BigDecimal.valueOf(40),  // Ahorro mínimo 30 UF
                "13101"                  // Santiago
        );

        assertTrue(result.elegible());
        assertEquals(0, result.subsidioEstimadoUf().compareTo(BigDecimal.valueOf(600)));
    }

    @Test
    @DisplayName("DS1 Tramo 1: Rechazado si el ahorro es inferior al mínimo exigido de 30 UF")
    void shouldRejectDs1Tramo1WhenSavingsInsufficient() {
        var result = subsidyService.evaluateDs1(
                BigDecimal.valueOf(950),
                1,
                40,
                BigDecimal.valueOf(25), // < 30 UF requerido
                "13101"
        );

        assertFalse(result.elegible());
        assertTrue(result.motivo().contains("Ahorro mínimo"));
    }
}
