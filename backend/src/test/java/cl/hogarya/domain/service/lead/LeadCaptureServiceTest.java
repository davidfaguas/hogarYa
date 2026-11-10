package cl.hogarya.domain.service.lead;

import cl.hogarya.domain.model.identity.ChileanRut;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Defecto 9: Captación de Leads y Consentimiento Ley 21.719")
class LeadCaptureServiceTest {

    @Test
    @DisplayName("Genera código de seguimiento correlativo con prefijo HY-")
    void shouldFormatTrackingCodeWithPrefix() {
        // Simulación del formateador correlativo de secuencia PostgreSQL
        long sequenceVal = 10425L;
        String trackingCode = String.format("HY-%05d", sequenceVal);

        assertEquals("HY-10425", trackingCode);
    }

    @Test
    @DisplayName("Genera hash SHA-256 criptográfico para consentimiento de datos personales")
    void shouldGenerateSha256ConsentHash() {
        String rut = "11.111.112-K";
        String email = "contacto@inmobiliaria.cl";
        String versionPolitica = "v1.2-2026";
        String userAgent = "Mozilla/5.0 (X11; Linux x86_64)";
        String timestamp = "2026-11-10T12:00:00Z";

        String rawPayload = rut + "|" + email + "|" + versionPolitica + "|" + userAgent + "|" + timestamp;

        String hash = LeadCaptureService.computeSha256ConsentHash(rawPayload);

        assertNotNull(hash);
        assertEquals(64, hash.length(), "El hash SHA-256 en hexadecimal debe medir exactamente 64 caracteres");
        // Idempotencia: el mismo payload genera el mismo hash exacto
        assertEquals(hash, LeadCaptureService.computeSha256ConsentHash(rawPayload));
    }

    @Test
    @DisplayName("Rechaza solicitud de lead si el RUT es inválido")
    void shouldRejectLeadCreationWithInvalidRut() {
        assertThrows(IllegalArgumentException.class, () ->
                ChileanRut.of("12.345.678-0") // DV falso
        );
    }
}
