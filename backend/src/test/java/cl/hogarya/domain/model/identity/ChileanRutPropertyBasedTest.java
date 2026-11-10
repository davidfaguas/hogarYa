package cl.hogarya.domain.model.identity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Defecto 7: Validador formal de RUT chileno (Módulo 11)")
class ChileanRutPropertyBasedTest {

    @Nested
    @DisplayName("Pruebas de casos frontera y corrección de DV K / 0")
    class KnownCasesTest {

        @Test
        @DisplayName("FALLA ESPERADA PREVIA: '16243576-K' es matemáticamente inválido porque su DV real es '0'")
        void shouldReject16243576WithK() {
            // Demostración: 16.243.576 tiene DV = '0'.
            // Intentar crear ChileanRut("16243576-K") debe lanzar IllegalArgumentException
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                    ChileanRut.of("16243576-K")
            );
            assertTrue(ex.getMessage().contains("Dígito verificador inválido"));
        }

        @Test
        @DisplayName("CORRECCIÓN EXITOSA: '16243576-0' es válido con DV '0'")
        void shouldAccept16243576WithZero() {
            ChileanRut rut = ChileanRut.of("16243576-0");
            assertEquals(16243576, rut.cuerpo());
            assertEquals('0', rut.dv());
            assertEquals("16.243.576-0", rut.formatoConPuntosYGuion());
        }

        @Test
        @DisplayName("CORRECCIÓN EXITOSA: '11111112-K' es un caso K real matemáticamente verificado")
        void shouldAcceptRealKCase11111112K() {
            // Verificación paso a paso:
            // 2*2 + 1*3 + 1*4 + 1*5 + 1*6 + 1*7 + 1*2 + 1*3 = 4 + 3 + 4 + 5 + 6 + 7 + 2 + 3 = 34
            // 34 % 11 = 1 -> 11 - 1 = 10 -> 'K'
            ChileanRut rut = ChileanRut.of("11111112-K");
            assertEquals(11111112, rut.cuerpo());
            assertEquals('K', rut.dv());
            assertEquals("11.111.112-K", rut.formatoConPuntosYGuion());
        }

        @ParameterizedTest(name = "RUT de empresa válido: {0}")
        @ValueSource(strings = {
                "76.123.456-0",
                "96.987.654-K",
                "99.123.456-K"
        })
        @DisplayName("CORRECCIÓN EXITOSA: Soporte de empresas e inmobiliarias hasta rango 99.xxx.xxx")
        void shouldSupportEnterpriseRuts(String rawRut) {
            assertDoesNotThrow(() -> ChileanRut.of(rawRut));
        }

        @ParameterizedTest(name = "RUT fuera de rango: {0}")
        @ValueSource(strings = {
                "999.999-7",     // Menor a 1.000.000
                "100.000.000-7"  // Mayor a 99.999.999
        })
        void shouldRejectOutOfRangeRuts(String invalidRut) {
            assertThrows(IllegalArgumentException.class, () -> ChileanRut.of(invalidRut));
        }
    }

    @Nested
    @DisplayName("Prueba basada en propiedades (10.000 RUTs aleatorios contra implementación canónica)")
    class PropertyBasedValidationTest {

        @Test
        @DisplayName("10.000 RUTs generados coinciden exactamente con el algoritmo canónico Módulo 11")
        void testPropertyBasedRutEquivalence() {
            Random random = new Random(42); // Semilla reproducible
            int iterations = 10_000;

            for (int i = 0; i < iterations; i++) {
                // Generar cuerpo aleatorio entre 1.000.000 y 99.999.999
                int cuerpo = 1_000_000 + random.nextInt(99_000_000);
                char expectedDv = computeCanonicalModulo11(cuerpo);

                // Caso válido
                String validRutString = cuerpo + "-" + expectedDv;
                ChileanRut parsed = ChileanRut.of(validRutString);
                assertEquals(cuerpo, parsed.cuerpo());
                assertEquals(expectedDv, parsed.dv());

                // Caso corrupto (mutar DV)
                char corruptedDv = (expectedDv == 'K') ? '0' : (char) (expectedDv + 1);
                if (corruptedDv > '9') corruptedDv = 'K';
                if (corruptedDv == expectedDv) corruptedDv = (expectedDv == '0') ? '1' : '0';

                final String invalidRutString = cuerpo + "-" + corruptedDv;
                assertThrows(IllegalArgumentException.class, () -> ChileanRut.of(invalidRutString),
                        "Debe rechazar DV adulterado: " + invalidRutString);
            }
        }

        /**
         * Implementación canónica e independiente del algoritmo Módulo 11 según normativa chilena.
         */
        private char computeCanonicalModulo11(int cuerpo) {
            int m = 0;
            int s = 1;
            int t = cuerpo;
            while (t != 0) {
                s = (s + (t % 10) * (9 - (m++ % 6))) % 11;
                t /= 10;
            }
            if (s > 0) {
                return (char) (s + 47); // 48 es '0', s-1 + 48 = s + 47
            } else {
                return 'K';
            }
        }
    }
}
