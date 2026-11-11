package cl.hogarya.infrastructure.web;

import cl.hogarya.domain.service.financial.ChileanMortgageSimulatorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

/**
 * Controlador REST para el Simulador Hipotecario Chileno.
 * Calcula cuotas mensuales de amortización francesa contrastadas contra la función financiera PAGO de Excel,
 * gastos operacionales (CBR, tasación, estudio de títulos, timbres y estampillas) y garantía estatal FOGAES (Ley 21.543).
 */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "Simulador Hipotecario", description = "Endpoints de simulación financiera de créditos hipotecarios en Chile")
public class MortgageSimulatorController {

    private final ChileanMortgageSimulatorService simulatorService;

    public MortgageSimulatorController(ChileanMortgageSimulatorService simulatorService) {
        this.simulatorService = simulatorService;
    }

    public record SimulationApiRequest(
            @Schema(description = "Valor de la propiedad en Unidades de Fomento (UF)", example = "3500.00")
            BigDecimal valorPropiedadUf,

            @Schema(description = "Porcentaje de pie aportado (ej. 10 para 10%, 20 para 20%)", example = "20")
            BigDecimal porcentajePie,

            @Schema(description = "Plazo del crédito en años (ej. 20, 25, 30)", example = "25")
            int plazoAnos,

            @Schema(description = "Tasa de interés anual (ej. 4.5 para 4.5% anual)", example = "4.50")
            BigDecimal tasaInteresAnual,

            @Schema(description = "Valor actual de la UF en pesos chilenos (CLP)", example = "37850.00")
            BigDecimal valorUfClp,

            @Schema(description = "Indica si es adquisición de primera vivienda", example = "true")
            boolean esPrimeraVivienda,

            @Schema(description = "Indica si aplica subsidio de integración social DS-19", example = "false")
            boolean tieneSubsidioDs19
    ) {}

    @PostMapping({"/simulador", "/finanzas/simular-credito"})
    @Operation(
            summary = "Simular crédito hipotecario",
            description = "Calcula el dividendo mensual en UF y CLP, gastos operacionales detallados, ingreso familiar requerido y elegibilidad de garantía FOGAES"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Simulación calculada con alta precisión",
                    content = @Content(schema = @Schema(implementation = ChileanMortgageSimulatorService.MortgageSimulationResult.class))),
            @ApiResponse(responseCode = "400", description = "Parámetros inválidos o fuera de rango")
    })
    public ResponseEntity<ChileanMortgageSimulatorService.MortgageSimulationResult> simular(
            @RequestBody SimulationApiRequest request
    ) {
        if (request == null || request.valorPropiedadUf() == null || request.porcentajePie() == null
                || request.tasaInteresAnual() == null || request.valorUfClp() == null || request.plazoAnos() <= 0) {
            return ResponseEntity.badRequest().build();
        }

        ChileanMortgageSimulatorService.SimulationRequest domainReq = new ChileanMortgageSimulatorService.SimulationRequest(
                request.valorPropiedadUf(),
                request.porcentajePie(),
                request.plazoAnos(),
                request.tasaInteresAnual(),
                request.valorUfClp(),
                request.esPrimeraVivienda(),
                request.tieneSubsidioDs19()
        );

        ChileanMortgageSimulatorService.MortgageSimulationResult result = simulatorService.simulate(domainReq);
        return ResponseEntity.ok(result);
    }
}
