package cl.pulsocare.consultas;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * PulsoCare - microservicio de consultas (cold path).
 *
 * Expone una API REST de solo lectura para el dashboard: el historico de
 * lecturas de signos vitales (PC_LECTURA_SIGNO_VITAL) y las alertas generadas
 * por el hot path (PC_ALERTA), con la operacion de reconocer una alerta.
 */
@SpringBootApplication
public class ConsultasApplication {
    public static void main(String[] args) {
        SpringApplication.run(ConsultasApplication.class, args);
    }
}
