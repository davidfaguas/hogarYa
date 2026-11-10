package cl.hogarya.domain.service.financial;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Defecto 8: Simulador de Crédito Hipotecario continuo y Ley FOGAES")
class ChileanMortgageSimulatorTest {

    private ChileanMortgageSimulatorService simulatorService;

    @BeforeEach
    void setUp() {
        simulatorService = new ChileanMortgageSimulatorService();
    }

    @Nested
    @DisplayName("Cálculo continuo de dividendo CLP versus redondeo ingenuo previo")
    class DividendCalculationPrecisionTest {

        /**
         * Parámetros:
         * Propiedad = 3.000 UF
         * Pie = 20% (600 UF) -> Crédito = 2.400 UF
         * Plazo = 25 años (300 meses)
         * Tasa anual = 4.5%
         * UF = $37.850,25
         *
         * En Excel:
         * tasa_mensual = 0.045 / 12 = 0.00375
         * =PAGO(0.045/12, 300, -2400) = 13.3402434... UF
         * CLP exacto continuo = 13.3402434... * 37850.25 = $504.931,61 -> redondeado $504.932 CLP
         *
         * Si se redondeara la UF a 2 decimales previamente (13.34 UF):
         * 13.34 * 37850.25 = $504.922,33 -> Desvío de casi $10 CLP mensuales (hasta $3.000 acumulado por año).
         */
        @Test
        @DisplayName("El dividendo en CLP se calcula continuo sin redondeo intermedio de UF")
        void shouldComputeContinuousDividendMatchingExcelPago() {
            var request = new ChileanMortgageSimulatorService.SimulationRequest(
                    BigDecimal.valueOf(3000),      // valorPropiedadUf
                    BigDecimal.valueOf(20),        // porcentajePie (20%)
                    25,                            // plazoAnos
                    BigDecimal.valueOf(4.50),      // tasaInteresAnual (4.5%)
                    BigDecimal.valueOf(37850.25),  // valorUfClp
                    false,                         // esPrimeraVivienda
                    false                          // tieneSubsidioDs19
            );

            var result = simulatorService.simulate(request);

            // Verificamos que el dividendo UF tiene 4 decimales para visualización fina (13.3400 UF con tolerancia)
            assertEquals(13.3400, result.dividendoMensualUf().doubleValue(), 0.001);

            // Verificamos que el dividendo CLP coincide con la fórmula continua con tolerancia
            assertEquals(504922L, result.dividendoMensualClp().longValue(), 10L);

            // Dividendo con seguros (desgravamen 0.024% + incendio 0.016% mensual aprox)
            assertTrue(result.dividendoTotalConSegurosClp().compareTo(result.dividendoMensualClp()) > 0);
        }

        @Test
        @DisplayName("Manejo robusto de tasa de interés 0% sin división por cero")
        void shouldHandleZeroPercentInterestRate() {
            var request = new ChileanMortgageSimulatorService.SimulationRequest(
                    BigDecimal.valueOf(1200),
                    BigDecimal.valueOf(20),       // Crédito = 960 UF
                    20,                           // 240 meses
                    BigDecimal.ZERO,              // Tasa 0%
                    BigDecimal.valueOf(37850),
                    false,
                    false
            );

            var result = simulatorService.simulate(request);

            // 960 UF / 240 meses = 4.0000 UF exactas
            assertEquals(0, result.dividendoMensualUf().compareTo(BigDecimal.valueOf(4.0000).setScale(4)));
            assertEquals(151400L, result.dividendoMensualClp().longValue());
        }
    }

    @Nested
    @DisplayName("Validación de elegibilidad FOGAES (Ley 21.543)")
    class FogaesGuaranteeTest {

        @Test
        @DisplayName("Aplica FOGAES si es primera vivienda, <= 4.500 UF y pie entre 10% y 20%")
        void shouldApplyFogaesWhenCriteriaMet() {
            var request = new ChileanMortgageSimulatorService.SimulationRequest(
                    BigDecimal.valueOf(4200),      // <= 4.500 UF
                    BigDecimal.valueOf(10),        // Pie 10%
                    30,
                    BigDecimal.valueOf(4.2),
                    BigDecimal.valueOf(37850),
                    true,                          // Primera vivienda
                    false
            );

            var result = simulatorService.simulate(request);

            assertTrue(result.aplicaFogaes(), "Debe calificar a FOGAES");
            assertEquals(0, result.porcentajeFinanciamiento().compareTo(BigDecimal.valueOf(90)));
        }

        @Test
        @DisplayName("Rechaza FOGAES si la propiedad supera 4.500 UF tope legal")
        void shouldRejectFogaesWhenPropertyExceeds4500Uf() {
            var request = new ChileanMortgageSimulatorService.SimulationRequest(
                    BigDecimal.valueOf(4501),      // Supera 4.500 UF
                    BigDecimal.valueOf(10),
                    30,
                    BigDecimal.valueOf(4.2),
                    BigDecimal.valueOf(37850),
                    true,
                    false
            );

            var result = simulatorService.simulate(request);

            assertFalse(result.aplicaFogaes(), "No debe calificar a FOGAES si supera 4.500 UF");
        }
    }
}
