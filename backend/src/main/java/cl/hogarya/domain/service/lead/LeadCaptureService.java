package cl.hogarya.domain.service.lead;

import cl.hogarya.domain.model.identity.ChileanRut;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;

/**
 * Servicio de Captación de Leads con cumplimiento de la Ley 21.719 de Protección de Datos Personales.
 * Corrección Defecto 9:
 * - Código correlativo formal generado vía sequence de base de datos 'seq_lead_tracking_code'.
 * - Hash de consentimiento verificable: registra versión del texto normativo y timestamp exacto.
 * - Despacho garantizado a CRM inmobiliario vía Transactional Outbox Pattern.
 */
@Service
public class LeadCaptureService {

    public static final String CONSENT_TEXT_VERSION = "v2026.1";

    private final JdbcClient jdbcClient;
    private final ObjectMapper objectMapper;

    public LeadCaptureService(JdbcClient jdbcClient, ObjectMapper objectMapper) {
        this.jdbcClient = jdbcClient;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public LeadResponse captureLead(LeadRequest request, String ipAddress, String userAgent) {
        ChileanRut rut = ChileanRut.of(request.rut());
        Instant consentTimestamp = Instant.now();

        // 1. Hash Criptográfico del Consentimiento (Ley 21.719 / Ley 19.628)
        // Permite demostrar ante la Agencia de Protección de Datos qué texto y cuándo se aceptó
        String consentPayload = CONSENT_TEXT_VERSION + "|" + 
                                consentTimestamp.toString() + "|" + 
                                rut.toCanonicalString() + "|" + 
                                request.email().trim().toLowerCase();
        String consentHash = computeSha256(consentPayload);

        // 2. Obtener siguiente código correlativo de la secuencia
        Long seqVal = jdbcClient.sql("SELECT nextval('seq_lead_tracking_code')").query(Long.class).single();
        String trackingCode = "HY-" + seqVal;

        UUID leadId = UUID.randomUUID();

        // 3. Persistir Lead
        jdbcClient.sql("""
            INSERT INTO lead_cotizacion (
                id, tracking_code, propiedad_id, rut_solicitante, nombres, email, 
                telefono, mensaje, pie_porcentaje, con_preaprobacion_bancaria, 
                consentimiento_hash, consentimiento_texto_version, consentimiento_timestamp,
                ip_origen, user_agent, estado
            ) VALUES (
                :id, :trackingCode, :propiedadId, :rut, :nombres, :email,
                :telefono, :mensaje, :pie, :preaprobacion,
                :consentHash, :consentVersion, :consentTimestamp,
                :ip::inet, :userAgent, 'RECIBIDO'
            )
            """)
            .param("id", leadId)
            .param("trackingCode", trackingCode)
            .param("propiedadId", request.propiedadId())
            .param("rut", rut.toCanonicalString())
            .param("nombres", request.nombres().trim())
            .param("email", request.email().trim().toLowerCase())
            .param("telefono", request.telefono().trim())
            .param("mensaje", request.mensaje())
            .param("pie", request.piePorcentaje())
            .param("preaprobacion", request.conPreaprobacion())
            .param("consentHash", consentHash)
            .param("consentVersion", CONSENT_TEXT_VERSION)
            .param("consentTimestamp", consentTimestamp)
            .param("ip", ipAddress != null ? ipAddress : "127.0.0.1")
            .param("userAgent", userAgent != null ? userAgent : "Unknown")
            .update();

        // 4. Registrar en Outbox Transaccional
        Map<String, Object> outboxData = Map.of(
                "leadId", leadId.toString(),
                "trackingCode", trackingCode,
                "propiedadId", request.propiedadId().toString(),
                "rut", rut.toCanonicalString(),
                "nombres", request.nombres().trim(),
                "email", request.email().trim().toLowerCase(),
                "telefono", request.telefono().trim(),
                "piePorcentaje", request.piePorcentaje(),
                "preaprobado", request.conPreaprobacion(),
                "timestamp", consentTimestamp.toString()
        );

        try {
            String jsonPayload = objectMapper.writeValueAsString(outboxData);
            jdbcClient.sql("""
                INSERT INTO outbox_evento (aggregate_type, aggregate_id, event_type, payload)
                VALUES ('LEAD', :aggregateId, 'LEAD_COTIZACION_CREADO', :payload::jsonb)
                """)
                .param("aggregateId", trackingCode)
                .param("payload", jsonPayload)
                .update();
        } catch (Exception e) {
            throw new IllegalStateException("Error al registrar evento en outbox transaccional", e);
        }

        return new LeadResponse(trackingCode, "Solicitud recibida exitosamente bajo código " + trackingCode);
    }

    public static String computeSha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Algoritmo SHA-256 no disponible", e);
        }
    }

    public static String computeSha256ConsentHash(String input) {
        return computeSha256(input);
    }

    public record LeadRequest(
            UUID propiedadId,
            String rut,
            String nombres,
            String email,
            String telefono,
            String mensaje,
            int piePorcentaje,
            boolean conPreaprobacion
    ) {}

    public record LeadResponse(String trackingCode, String mensaje) {}
}
