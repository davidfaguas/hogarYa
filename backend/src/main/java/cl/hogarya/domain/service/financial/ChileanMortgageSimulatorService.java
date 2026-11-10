package cl.hogarya.domain.service.financial;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

/**
 * Servicio de Simulación de Crédito Hipotecario en Chile.
 * Corrección Defecto 8:
 * - Evita error de truncamiento: el dividendo en CLP se calcula desde el valor de alta precisión continua,
 *   contrastado con la función PAGO / PMT financiera de Excel: =PAGO(tasa_anual/12; plazo*12; -monto_credito).
 * - Manejo robusto de tasa 0% (evita ArithmeticException por división por cero).
 * - Cálculo de gastos operacionales y elegibilidad FOGAES (Ley 21.543, tope 4.500 UF).
 */
@Service
public class ChileanMortgageSimulatorService {

    private static final MathContext MC_CALCULO = new MathContext(24, RoundingMode.HALF_EVEN);
    
    // Parámetros normativos y arancelarios de referencia
    public static final BigDecimal TOPE_FOGAES_UF = new BigDecimal("4500.00");
    public static final BigDecimal FACTOR_CBR = new BigDecimal("0.002");                       // 0.2% Decreto 588
    public static final BigDecimal TOPE_ESTIMADO_CBR_CLP = new BigDecimal("450000");           // Pendiente verificar circular MINJU
    public static final BigDecimal TASACION_UF = new BigDecimal("3.00");
    public static final BigDecimal ESTUDIO_TITULOS_UF = new BigDecimal("4.50");
    public static final BigDecimal TIMBRES_ESTAMPILAS_DFL2 = new BigDecimal("0.002");         // 0.2% DFL-2
    public static final BigDecimal FACTOR_CARGA_FINANCIERA = new BigDecimal("4");              // 25% cuota/ingreso (Práctica bancaria)

    public record SimulationRequest(
            BigDecimal valorPropiedadUf,
            BigDecimal porcentajePie,
            int plazoAnos,
            BigDecimal tasaInteresAnual,
            BigDecimal valorUfClp,
            boolean esPrimeraVivienda,
            boolean tieneSubsidioDs19
    ) {}

    public MortgageSimulationResult simulate(SimulationRequest request) {
        int pieInt = request.porcentajePie().intValue();
        MortgageSimulationResult base = simulate(
                request.valorPropiedadUf(),
                pieInt,
                request.plazoAnos(),
                request.tasaInteresAnual(),
                request.valorUfClp()
        );

        boolean fogaes = request.esPrimeraVivienda()
                && request.valorPropiedadUf().compareTo(TOPE_FOGAES_UF) <= 0
                && pieInt >= 10 && pieInt <= 20;

        BigDecimal financiamiento = BigDecimal.valueOf(100 - pieInt);
        BigDecimal seguros = base.creditoClp().multiply(new BigDecimal("0.0004"), MC_CALCULO).setScale(0, RoundingMode.HALF_EVEN);
        BigDecimal dividendoConSeguros = base.dividendoMensualClp().add(seguros);

        return new MortgageSimulationResult(
                base.precioPropiedadUf(),
                base.valorUfUsado(),
                base.piePorcentaje(),
                base.pieUf(),
                base.pieClp(),
                base.creditoUf(),
                base.creditoClp(),
                base.dividendoMensualUf(),
                base.dividendoMensualClp(),
                base.rentaMinimaSugeridaClp(),
                fogaes,
                base.gastosOperacionales(),
                dividendoConSeguros,
                financiamiento
        );
    }

    public MortgageSimulationResult simulate(
            BigDecimal precioPropiedadUf,
            int piePorcentaje,
            int plazoAnos,
            BigDecimal tasaAnualPercent,
            BigDecimal valorUfDelDia
    ) {
        if (precioPropiedadUf == null || precioPropiedadUf.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("El precio en UF debe ser estrictamente mayor a cero");
        }
        if (piePorcentaje < 10 || piePorcentaje > 50) {
            throw new IllegalArgumentException("El porcentaje de pie debe estar entre 10% y 50%");
        }
        if (plazoAnos < 5 || plazoAnos > 30) {
            throw new IllegalArgumentException("El plazo del crédito debe estar entre 5 y 30 años");
        }
        if (tasaAnualPercent == null || tasaAnualPercent.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("La tasa de interés no puede ser negativa");
        }
        if (valorUfDelDia == null || valorUfDelDia.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("El valor de la UF del día debe ser mayor a cero");
        }

        // 1. Desglose de Capital y Financiamiento
        BigDecimal pieRatio = BigDecimal.valueOf(piePorcentaje).divide(BigDecimal.valueOf(100), MC_CALCULO);
        BigDecimal pieUf = precioPropiedadUf.multiply(pieRatio, MC_CALCULO);
        BigDecimal pieClp = pieUf.multiply(valorUfDelDia, MC_CALCULO).setScale(0, RoundingMode.HALF_EVEN);

        BigDecimal montoCreditoUf = precioPropiedadUf.subtract(pieUf, MC_CALCULO);
        BigDecimal montoCreditoClp = montoCreditoUf.multiply(valorUfDelDia, MC_CALCULO).setScale(0, RoundingMode.HALF_EVEN);

        // 2. Amortización Francesa de Alta Precisión
        // M = P * [ i * (1+i)^n ] / [ (1+i)^n - 1 ]
        // Si tasa == 0%, M = P / n (evita división por cero)
        int totalMeses = plazoAnos * 12;
        BigDecimal dividendoContinuoUf;

        if (tasaAnualPercent.compareTo(BigDecimal.ZERO) == 0) {
            dividendoContinuoUf = montoCreditoUf.divide(BigDecimal.valueOf(totalMeses), MC_CALCULO);
        } else {
            BigDecimal tasaMensual = tasaAnualPercent
                    .divide(BigDecimal.valueOf(100), MC_CALCULO)
                    .divide(BigDecimal.valueOf(12), MC_CALCULO);
            
            BigDecimal unoMasI = BigDecimal.ONE.add(tasaMensual, MC_CALCULO);
            BigDecimal factorPotencia = unoMasI.pow(totalMeses, MC_CALCULO);

            BigDecimal numerador = montoCreditoUf.multiply(tasaMensual.multiply(factorPotencia, MC_CALCULO), MC_CALCULO);
            BigDecimal denominador = factorPotencia.subtract(BigDecimal.ONE, MC_CALCULO);
            
            dividendoContinuoUf = numerador.divide(denominador, MC_CALCULO);
        }

        // Dividendo en UF con 4 decimales para visualización precisa
        BigDecimal dividendoMensualUf = dividendoContinuoUf.setScale(4, RoundingMode.HALF_EVEN);

        // Corrección Defecto 8: El dividendo en CLP se calcula desde el dividendo continuo de alta precisión,
        // no desde el dividendo en UF ya truncado a 2 decimales.
        BigDecimal dividendoMensualClp = dividendoContinuoUf.multiply(valorUfDelDia, MC_CALCULO).setScale(0, RoundingMode.HALF_EVEN);

        // Renta mínima sugerida (Práctica bancaria chilena: dividendo no debe superar 25% del ingreso líquido)
        BigDecimal rentaMinimaSugeridaClp = dividendoMensualClp.multiply(FACTOR_CARGA_FINANCIERA).setScale(0, RoundingMode.HALF_EVEN);

        // 3. Gastos Operacionales
        BigDecimal tasacionClp = TASACION_UF.multiply(valorUfDelDia, MC_CALCULO).setScale(0, RoundingMode.HALF_EVEN);
        BigDecimal titulosClp = ESTUDIO_TITULOS_UF.multiply(valorUfDelDia, MC_CALCULO).setScale(0, RoundingMode.HALF_EVEN);
        
        BigDecimal cbrEstimado = montoCreditoClp.multiply(FACTOR_CBR, MC_CALCULO).setScale(0, RoundingMode.HALF_EVEN);
        BigDecimal cbrFinalClp = cbrEstimado.min(TOPE_ESTIMADO_CBR_CLP);

        BigDecimal timbresClp = montoCreditoClp.multiply(TIMBRES_ESTAMPILAS_DFL2, MC_CALCULO).setScale(0, RoundingMode.HALF_EVEN);
        BigDecimal totalGastosOperacionalesClp = tasacionClp.add(titulosClp).add(cbrFinalClp).add(timbresClp);

        // 4. Elegibilidad FOGAES básica
        boolean elegibleFogaes = piePorcentaje == 10 && precioPropiedadUf.compareTo(TOPE_FOGAES_UF) <= 0;
        BigDecimal financiamiento = BigDecimal.valueOf(100 - piePorcentaje);
        BigDecimal seguros = montoCreditoClp.multiply(new BigDecimal("0.0004"), MC_CALCULO).setScale(0, RoundingMode.HALF_EVEN);
        BigDecimal dividendoConSeguros = dividendoMensualClp.add(seguros);

        return new MortgageSimulationResult(
                precioPropiedadUf.setScale(2, RoundingMode.HALF_EVEN),
                valorUfDelDia,
                piePorcentaje,
                pieUf.setScale(2, RoundingMode.HALF_EVEN),
                pieClp,
                montoCreditoUf.setScale(2, RoundingMode.HALF_EVEN),
                montoCreditoClp,
                dividendoMensualUf,
                dividendoMensualClp,
                rentaMinimaSugeridaClp,
                elegibleFogaes,
                new OperationalCosts(tasacionClp, titulosClp, cbrFinalClp, timbresClp, totalGastosOperacionalesClp),
                dividendoConSeguros,
                financiamiento
        );
    }

    public record MortgageSimulationResult(
            BigDecimal precioPropiedadUf,
            BigDecimal valorUfUsado,
            int piePorcentaje,
            BigDecimal pieUf,
            BigDecimal pieClp,
            BigDecimal creditoUf,
            BigDecimal creditoClp,
            BigDecimal dividendoMensualUf,
            BigDecimal dividendoMensualClp,
            BigDecimal rentaMinimaSugeridaClp,
            boolean fogaesAplicable,
            OperationalCosts gastosOperacionales,
            BigDecimal dividendoTotalConSegurosClp,
            BigDecimal porcentajeFinanciamiento
    ) {
        public boolean aplicaFogaes() {
            return fogaesAplicable;
        }
    }

    public record OperationalCosts(
            BigDecimal tasacionClp,
            BigDecimal estudioTitulosClp,
            BigDecimal conservadorBienesRaicesClp,
            BigDecimal impuestoTimbresEstampillasClp,
            BigDecimal totalGastosOperacionalesClp
    ) {}
}
