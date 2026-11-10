package cl.hogarya.domain.service.subsidy;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

/**
 * Servicio de Elegibilidad para Subsidios Habitacionales del MINVU (DS19 / DS1).
 * Reglas según D.S. N° 1 de 2011 y D.S. N° 19 de 2016.
 */
@Service
public class MinvuSubsidyEligibilityService {

    private final JdbcClient jdbcClient;

    public MinvuSubsidyEligibilityService() {
        this.jdbcClient = null;
    }

    public MinvuSubsidyEligibilityService(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public record DsResult(boolean elegible, String codigoSubsidio, BigDecimal subsidioEstimadoUf, String motivo) {}

    public DsResult evaluateDs19(BigDecimal precioUf, String comunaCut, boolean conConvenio) {
        if (!conConvenio) {
            return new DsResult(false, "DS19_INTEGRACION_SOCIAL", BigDecimal.ZERO, "Proyecto no cuenta con convenio Minvu activo");
        }
        BigDecimal topeCentro = BigDecimal.valueOf(2200);
        if (precioUf.compareTo(topeCentro) <= 0) {
            return new DsResult(true, "DS19_INTEGRACION_SOCIAL", BigDecimal.valueOf(325), "Cumple requisitos DS19 Integración Social");
        }
        return new DsResult(false, "DS19_INTEGRACION_SOCIAL", BigDecimal.ZERO, "Precio supera el tope de 2.200 UF para la zona");
    }

    public DsResult evaluateDs1(BigDecimal precioUf, int tramo, int rshPorcentaje, BigDecimal ahorroUf, String comunaCut) {
        BigDecimal ahorro = ahorroUf != null ? ahorroUf : BigDecimal.ZERO;

        if (tramo == 1) {
            BigDecimal precioMax = BigDecimal.valueOf(1100);
            BigDecimal ahorroMin = BigDecimal.valueOf(30);
            if (precioUf.compareTo(precioMax) > 0) {
                return new DsResult(false, "DS1_TRAMO_1", BigDecimal.ZERO, "Precio supera 1.100 UF para Tramo 1");
            }
            if (rshPorcentaje > 60) {
                return new DsResult(false, "DS1_TRAMO_1", BigDecimal.ZERO, "RSH supera el 60% máximo");
            }
            if (ahorro.compareTo(ahorroMin) < 0) {
                return new DsResult(false, "DS1_TRAMO_1", BigDecimal.ZERO, "Ahorro mínimo insuficiente (exige 30 UF)");
            }
            return new DsResult(true, "DS1_TRAMO_1", BigDecimal.valueOf(600), "Aprobado DS1 Tramo 1");
        } else if (tramo == 2) {
            BigDecimal precioMax = BigDecimal.valueOf(1600);
            BigDecimal ahorroMin = BigDecimal.valueOf(40);
            if (precioUf.compareTo(precioMax) > 0 || rshPorcentaje > 80 || ahorro.compareTo(ahorroMin) < 0) {
                return new DsResult(false, "DS1_TRAMO_2", BigDecimal.ZERO, "No cumple requisitos DS1 Tramo 2");
            }
            return new DsResult(true, "DS1_TRAMO_2", BigDecimal.valueOf(500), "Aprobado DS1 Tramo 2");
        } else {
            BigDecimal precioMax = BigDecimal.valueOf(2200);
            BigDecimal ahorroMin = BigDecimal.valueOf(80);
            if (precioUf.compareTo(precioMax) > 0 || rshPorcentaje > 90 || ahorro.compareTo(ahorroMin) < 0) {
                return new DsResult(false, "DS1_TRAMO_3", BigDecimal.ZERO, "No cumple requisitos DS1 Tramo 3");
            }
            return new DsResult(true, "DS1_TRAMO_3", BigDecimal.valueOf(350), "Aprobado DS1 Tramo 3");
        }
    }

    public SubsidyEligibilityEvaluation evaluateProperty(
            BigDecimal precioUf,
            String zonaTerritorial,
            BigDecimal ahorroDisponibleUf
    ) {
        if (precioUf == null || precioUf.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("El precio en UF debe ser mayor a cero");
        }
        String zona = (zonaTerritorial != null && !zonaTerritorial.isBlank()) ? zonaTerritorial.toUpperCase() : "CENTRO";
        BigDecimal ahorro = ahorroDisponibleUf != null ? ahorroDisponibleUf : BigDecimal.ZERO;

        if (jdbcClient == null) {
            throw new IllegalStateException("JdbcClient no inicializado");
        }

        List<SubsidyRuleRecord> rules = jdbcClient.sql("""
            SELECT programa, tramo, zona_territorial, precio_maximo_uf, ahorro_minimo_uf, decreto_fuente
            FROM subsidio_regla
            WHERE zona_territorial = :zona AND activo = TRUE
            """)
            .param("zona", zona)
            .query(SubsidyRuleRecord.class)
            .list();

        List<EligibleSubsidy> eligibleSubsidies = rules.stream()
                .filter(r -> precioUf.compareTo(r.precioMaximoUf()) <= 0)
                .map(r -> {
                    boolean cumpleAhorro = ahorro.compareTo(r.ahorroMinimoUf()) >= 0;
                    BigDecimal ahorroFaltante = cumpleAhorro ? BigDecimal.ZERO : r.ahorroMinimoUf().subtract(ahorro);
                    return new EligibleSubsidy(
                            r.programa(),
                            r.tramo(),
                            r.precioMaximoUf(),
                            r.ahorroMinimoUf(),
                            cumpleAhorro,
                            ahorroFaltante,
                            r.decretoFuente()
                    );
                })
                .toList();

        return new SubsidyEligibilityEvaluation(
                precioUf,
                zona,
                !eligibleSubsidies.isEmpty(),
                eligibleSubsidies
        );
    }

    public record SubsidyRuleRecord(
            String programa,
            String tramo,
            String zonaTerritorial,
            BigDecimal precioMaximoUf,
            BigDecimal ahorroMinimoUf,
            String decretoFuente
    ) {}

    public record EligibleSubsidy(
            String programa,
            String tramo,
            BigDecimal precioMaximoPermitidoUf,
            BigDecimal ahorroMinimoExigidoUf,
            boolean cumpleAhorroActual,
            BigDecimal ahorroFaltanteUf,
            String decretoNormativo
    ) {}

    public record SubsidyEligibilityEvaluation(
            BigDecimal precioPropiedadUf,
            String zonaEvaluada,
            boolean tieneSubsidioAplicable,
            List<EligibleSubsidy> programasCompatibles
    ) {}
}
