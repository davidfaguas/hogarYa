package cl.hogarya.infrastructure.outbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Worker asíncrono para el patrón Transactional Outbox.
 * Corrección Defecto 9:
 * - Emplea 'SELECT ... FOR UPDATE SKIP LOCKED' para permitir múltiples instancias del worker
 *   sin colisiones ni condiciones de carrera concurrentes.
 * - Manejo de reintentos con límite (máximo 5) y actualización de estado ('PROCESADO' o 'ERROR').
 */
@Component
public class OutboxWorker {

    private static final Logger log = LoggerFactory.getLogger(OutboxWorker.class);
    private static final int MAX_INTENTOS = 5;

    private final JdbcClient jdbcClient;

    public OutboxWorker(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Scheduled(fixedDelayString = "${hogarya.outbox.interval-ms:2000}")
    @Transactional
    public void processPendingEvents() {
        // Bloqueo pesimista concurrente seguro sin esperas
        List<OutboxRecord> pendingEvents = jdbcClient.sql("""
            SELECT id, aggregate_type, aggregate_id, event_type, payload::text, intentos
            FROM outbox_evento
            WHERE estado = 'PENDIENTE'
            ORDER BY creado_el ASC
            LIMIT 50
            FOR UPDATE SKIP LOCKED
            """)
            .query(OutboxRecord.class)
            .list();

        if (pendingEvents.isEmpty()) {
            return;
        }

        log.debug("Procesando lote de {} eventos pendientes en outbox", pendingEvents.size());

        for (OutboxRecord event : pendingEvents) {
            try {
                // Simulación del despacho al webhook/CRM de la inmobiliaria (ej: PlanOK / Enlace Inmobiliario)
                dispatchToExternalCrm(event);

                // Marcar como procesado exitosamente
                jdbcClient.sql("""
                    UPDATE outbox_evento 
                    SET estado = 'PROCESADO', procesado_el = :now 
                    WHERE id = :id
                    """)
                    .param("now", Instant.now())
                    .param("id", event.id())
                    .update();

                // Actualizar estado del lead correspondiente
                if ("LEAD".equals(event.aggregateType())) {
                    jdbcClient.sql("UPDATE lead_cotizacion SET estado = 'DESPACHADO_CRM' WHERE tracking_code = :code")
                        .param("code", event.aggregateId())
                        .update();
                }

            } catch (Exception ex) {
                int nuevosIntentos = event.intentos() + 1;
                String nuevoEstado = nuevosIntentos >= MAX_INTENTOS ? "ERROR" : "PENDIENTE";

                log.error("Fallo al despachar evento outbox id={} (intento {}/{}): {}", 
                        event.id(), nuevosIntentos, MAX_INTENTOS, ex.getMessage());

                jdbcClient.sql("""
                    UPDATE outbox_evento 
                    SET estado = :estado, 
                        intentos = :intentos, 
                        ultimo_error = :error 
                    WHERE id = :id
                    """)
                    .param("estado", nuevoEstado)
                    .param("intentos", nuevosIntentos)
                    .param("error", ex.getMessage())
                    .param("id", event.id())
                    .update();
            }
        }
    }

    private void dispatchToExternalCrm(OutboxRecord event) {
        // Simulación controlada del despacho HTTP/REST a webhook inmobiliario
        log.info("Despachando evento {} para agregado {} a CRM inmobiliario", event.eventType(), event.aggregateId());
    }

    public record OutboxRecord(
            long id,
            String aggregateType,
            String aggregateId,
            String eventType,
            String payload,
            int intentos
    ) {}
}
