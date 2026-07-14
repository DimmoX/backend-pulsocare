package cl.pulsocare.notification;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.PublishRequest;
import software.amazon.awssdk.services.sns.model.PublishResponse;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.Message;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * ms-notification: el worker que consume SQS y notifica al equipo de cuidado.
 * Puntos criticos (todos verificados en QA): segmentacion por id_usuario, nivel
 * de la alerta, descarte si no hay cuidadores, y NO borrar el mensaje si falla
 * (para que SQS lo reintente y termine en la DLQ).
 */
@ExtendWith(MockitoExtension.class)
class NotificationWorkerTest {

    @Mock SqsClient sqs;
    @Mock SnsClient sns;
    @Mock JdbcTemplate jdbc;

    NotificationWorker worker;

    @BeforeEach
    void setUp() {
        worker = new NotificationWorker(sqs, sns, jdbc);
        ReflectionTestUtils.setField(worker, "queueUrl", "https://sqs/pulsocare-alertas");
        ReflectionTestUtils.setField(worker, "topicArn", "arn:aws:sns:us-east-1:1:topic");
    }

    // --- helpers --------------------------------------------------------------

    private Message mensaje(long idAlerta, long idPaciente, int nivel) {
        String body = String.format(
                "{\"id_alerta\":%d,\"id_paciente\":%d,\"signo\":\"Presion diastolica\"," +
                "\"valor\":59,\"unidad\":\"mmHg\",\"nivel\":%d}", idAlerta, idPaciente, nivel);
        return Message.builder().body(body).receiptHandle("rh-1").build();
    }

    private Map<String, Object> cuidador(long idUsuario, String telefono) {
        Map<String, Object> m = new HashMap<>();
        m.put("ID_USUARIO", idUsuario);
        m.put("NOMBRE", "Ana");
        m.put("APELLIDO_PATERNO", "Diaz");
        m.put("ROL", "Medico");
        m.put("TELEFONO", telefono);
        m.put("CORREO", "ana@pulsocare.cl");
        return m;
    }

    /** Stubea las consultas comunes (nombre del paciente + equipo de cuidado). */
    private void conEquipo(List<Map<String, Object>> equipo) {
        lenient().when(jdbc.queryForObject(anyString(), eq(String.class), any(Object[].class)))
                .thenReturn("Rosa Fuentealba");
        lenient().when(jdbc.queryForList(anyString(), any(Object[].class))).thenReturn(equipo);
        lenient().when(sns.publish(any(Consumer.class)))
                .thenReturn(PublishResponse.builder().messageId("m-1").build());
    }

    private void procesar(Message m) {
        ReflectionTestUtils.invokeMethod(worker, "procesar", m);
    }

    private PublishRequest capturarPublish() {
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Consumer<PublishRequest.Builder>> captor = ArgumentCaptor.forClass(Consumer.class);
        verify(sns).publish(captor.capture());
        PublishRequest.Builder b = PublishRequest.builder();
        captor.getValue().accept(b);
        return b.build();
    }

    // --- tests ----------------------------------------------------------------

    @Test @DisplayName("Con un cuidador: publica en SNS, registra en BD y borra el mensaje de SQS")
    void unCuidador_flujoCompleto() {
        conEquipo(List.of(cuidador(45, "123")));
        procesar(mensaje(1005, 41, 2));
        verify(sns, times(1)).publish(any(Consumer.class));
        verify(jdbc, times(1)).update(anyString(), any(), any(), any(), any(), any());
        verify(sqs, times(1)).deleteMessage(any(DeleteMessageRequest.class));
    }

    @Test @DisplayName("La publicacion lleva el atributo id_usuario del destinatario (segmentacion)")
    void publica_conAtributoIdUsuario() {
        conEquipo(List.of(cuidador(45, "123")));
        procesar(mensaje(1005, 41, 2));
        PublishRequest req = capturarPublish();
        assertThat(req.messageAttributes()).containsKey("id_usuario");
        assertThat(req.messageAttributes().get("id_usuario").stringValue()).isEqualTo("45");
    }

    @Test @DisplayName("Nivel 2 -> el asunto indica CRITICO")
    void nivel2_asuntoCritico() {
        conEquipo(List.of(cuidador(45, "123")));
        procesar(mensaje(1005, 41, 2));
        assertThat(capturarPublish().subject()).contains("CRITICO");
    }

    @Test @DisplayName("Nivel 1 -> el asunto indica ATENCION")
    void nivel1_asuntoAtencion() {
        conEquipo(List.of(cuidador(45, "123")));
        procesar(mensaje(1005, 41, 1));
        assertThat(capturarPublish().subject()).contains("ATENCION");
    }

    @Test @DisplayName("Sin equipo de cuidado: no publica ni registra, pero SI borra el mensaje")
    void sinEquipo_descarta() {
        conEquipo(List.of());
        procesar(mensaje(1005, 41, 2));
        verify(sns, never()).publish(any(Consumer.class));
        verify(jdbc, never()).update(anyString(), any(), any(), any(), any(), any());
        verify(sqs, times(1)).deleteMessage(any(DeleteMessageRequest.class));
    }

    @Test @DisplayName("Dos cuidadores: publica y registra dos veces (uno por destinatario)")
    void dosCuidadores_dosEnvios() {
        conEquipo(List.of(cuidador(45, "123"), cuidador(46, "456")));
        procesar(mensaje(1005, 41, 2));
        verify(sns, times(2)).publish(any(Consumer.class));
        verify(jdbc, times(2)).update(anyString(), any(), any(), any(), any(), any());
    }

    @Test @DisplayName("Si SNS falla, NO se borra el mensaje (SQS reintenta -> DLQ)")
    void fallaSns_noBorra() {
        lenient().when(jdbc.queryForObject(anyString(), eq(String.class), any(Object[].class)))
                .thenReturn("Rosa Fuentealba");
        lenient().when(jdbc.queryForList(anyString(), any(Object[].class)))
                .thenReturn(List.of(cuidador(45, "123")));
        when(sns.publish(any(Consumer.class))).thenThrow(new RuntimeException("SNS caido"));

        procesar(mensaje(1005, 41, 2));

        verify(sqs, never()).deleteMessage(any(DeleteMessageRequest.class));
    }

    @Test @DisplayName("El registro en PC_NOTIFICACION guarda el messageId devuelto por SNS")
    void registra_conMessageId() {
        conEquipo(List.of(cuidador(45, "123")));
        procesar(mensaje(1005, 41, 2));
        // El insert pasa (idAlerta, idUsuario, telefono, cuerpo, messageId): capturo el ultimo.
        ArgumentCaptor<Object> messageId = ArgumentCaptor.forClass(Object.class);
        verify(jdbc).update(anyString(), any(), any(), any(), any(), messageId.capture());
        assertThat(messageId.getValue()).isEqualTo("m-1");
    }

    @Test @DisplayName("Un cuidador sin telefono no rompe el procesamiento")
    void telefonoNull_noRompe() {
        conEquipo(List.of(cuidador(45, null)));
        procesar(mensaje(1005, 41, 2));
        verify(sns, times(1)).publish(any(Consumer.class));
        verify(sqs, times(1)).deleteMessage(any(DeleteMessageRequest.class));
    }

    @Test @DisplayName("El mensaje se borra usando el receiptHandle correcto")
    void borra_conReceiptHandleCorrecto() {
        conEquipo(List.of(cuidador(45, "123")));
        procesar(mensaje(1005, 41, 2));
        ArgumentCaptor<DeleteMessageRequest> captor = ArgumentCaptor.forClass(DeleteMessageRequest.class);
        verify(sqs).deleteMessage(captor.capture());
        assertThat(captor.getValue().receiptHandle()).isEqualTo("rh-1");
    }
}
