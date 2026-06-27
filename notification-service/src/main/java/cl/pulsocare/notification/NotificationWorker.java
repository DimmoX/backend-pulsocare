package cl.pulsocare.notification;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.PublishResponse;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;

import java.util.List;
import java.util.Map;

/**
 * Bucle que sondea SQS y procesa cada alerta:
 *   1) lee el evento AlarmRaised,
 *   2) busca el equipo de cuidado del paciente (medico/familiar),
 *   3) publica el aviso en SNS (email),
 *   4) registra el envio en PC_NOTIFICACION,
 *   5) borra el mensaje de SQS (si falla, SQS lo reintenta -> DLQ).
 */
@Component
public class NotificationWorker implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(NotificationWorker.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final SqsClient sqs;
    private final SnsClient sns;
    private final JdbcTemplate jdbc;

    @Value("${aws.sqs.queue-url}")
    private String queueUrl;
    @Value("${aws.sns.topic-arn}")
    private String topicArn;
    @Value("${pulsocare.sqs.wait-seconds:20}")
    private int waitSeconds;
    @Value("${pulsocare.sqs.max-messages:10}")
    private int maxMessages;

    public NotificationWorker(SqsClient sqs, SnsClient sns, JdbcTemplate jdbc) {
        this.sqs = sqs;
        this.sns = sns;
        this.jdbc = jdbc;
    }

    @Override
    public void run(String... args) {
        log.info("notification-service iniciado. Escuchando SQS: {}", queueUrl);
        while (true) {
            try {
                ReceiveMessageRequest req = ReceiveMessageRequest.builder()
                        .queueUrl(queueUrl)
                        .maxNumberOfMessages(maxMessages)
                        .waitTimeSeconds(waitSeconds)   // long polling
                        .build();
                List<Message> mensajes = sqs.receiveMessage(req).messages();
                for (Message m : mensajes) {
                    procesar(m);
                }
            } catch (Exception e) {
                log.error("Error en el bucle de sondeo: {}", e.getMessage(), e);
                dormir(3000);
            }
        }
    }

    private void procesar(Message m) {
        try {
            JsonNode a = MAPPER.readTree(m.body());
            long idAlerta = a.path("id_alerta").asLong();
            long idPaciente = a.path("id_paciente").asLong();
            String signo = a.path("signo").asText();
            double valor = a.path("valor").asDouble();
            String unidad = a.path("unidad").asText("");
            int nivel = a.path("nivel").asInt();
            String nivelTxt = nivel == 2 ? "CRITICO" : "ATENCION";

            String paciente = nombrePaciente(idPaciente);
            List<Map<String, Object>> destinatarios = equipoDeCuidado(idPaciente);

            if (destinatarios.isEmpty()) {
                log.warn("Alerta {} sin equipo de cuidado para paciente {}. Se descarta.", idAlerta, idPaciente);
            }

            for (Map<String, Object> d : destinatarios) {
                long idUsuario = ((Number) d.get("ID_USUARIO")).longValue();
                String nombreDest = d.get("NOMBRE") + " " + d.get("APELLIDO_PATERNO");
                String rol = String.valueOf(d.get("ROL"));
                String telefono = d.get("TELEFONO") == null ? null : String.valueOf(d.get("TELEFONO"));

                String asunto = String.format("PulsoCare ALERTA %s - %s", nivelTxt, paciente);
                String cuerpo = String.format(
                        "Aviso para %s (%s)%n%n" +
                        "Paciente: %s%n" +
                        "Signo vital: %s = %s %s%n" +
                        "Nivel: %s%n" +
                        "Alerta #%d%n",
                        nombreDest, rol, paciente, signo, valor, unidad, nivelTxt, idAlerta);

                // 3) Publicar en SNS (email)
                PublishResponse resp = sns.publish(b -> b
                        .topicArn(topicArn)
                        .subject(asunto)
                        .message(cuerpo));

                // 4) Registrar en PC_NOTIFICACION (estado 2 = 'Enviado')
                jdbc.update(
                        "INSERT INTO PC_NOTIFICACION (ID_ALERTA, ID_USUARIO_DESTINO, " +
                        "ID_ESTADO_NOTIFICACION, CANAL, TELEFONO_DESTINO, MENSAJE, " +
                        "ID_PROVEEDOR_MSG, INTENTOS, FECHA_ENVIO) " +
                        "VALUES (?, ?, 2, 'EMAIL', ?, ?, ?, 1, SYSTIMESTAMP)",
                        idAlerta, idUsuario, telefono, cuerpo, resp.messageId());

                log.info("Notificado {} [{}] por alerta #{} ({} {}={}{})",
                        nombreDest, rol, idAlerta, paciente, signo, valor, unidad);
            }

            // 5) Borrar de SQS (procesado OK)
            sqs.deleteMessage(DeleteMessageRequest.builder()
                    .queueUrl(queueUrl)
                    .receiptHandle(m.receiptHandle())
                    .build());

        } catch (Exception e) {
            // No se borra el mensaje: SQS lo reintenta y, tras N veces, va a la DLQ.
            log.error("Fallo procesando alerta, se reintentara via SQS: {}", e.getMessage(), e);
        }
    }

    private String nombrePaciente(long idPaciente) {
        return jdbc.queryForObject(
                "SELECT NOMBRE || ' ' || APELLIDO_PATERNO FROM PC_PACIENTE WHERE ID_PACIENTE = ?",
                String.class, idPaciente);
    }

    private List<Map<String, Object>> equipoDeCuidado(long idPaciente) {
        return jdbc.queryForList(
                "SELECT u.ID_USUARIO, u.NOMBRE, u.APELLIDO_PATERNO, u.CORREO, u.TELEFONO, " +
                "       r.NOMBRE AS ROL " +
                "FROM PC_ASIGNACION_CUIDADO a " +
                "JOIN PC_USUARIO u ON u.ID_USUARIO = a.ID_USUARIO " +
                "JOIN PC_ROL r ON r.ID_ROL = u.ID_ROL " +
                "WHERE a.ID_PACIENTE = ? AND a.ACTIVO = 1 AND u.ESTADO = 'ACTIVO'",
                idPaciente);
    }

    private void dormir(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
    }
}
