package cl.pulsocare.notification;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * PulsoCare - notification-service.
 *
 * Worker que consume el evento "AlarmRaised" desde Amazon SQS (publicado por la
 * Lambda cuando una lectura sale de rango), determina el equipo de cuidado del
 * paciente y envia la notificacion por Amazon SNS (email), registrando el envio
 * en PC_NOTIFICACION.
 */
@SpringBootApplication
public class NotificationServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(NotificationServiceApplication.class, args);
    }
}
