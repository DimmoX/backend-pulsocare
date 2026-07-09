package cl.pulsocare.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * PulsoCare - microservicio de configuracion (cold path).
 *
 * Expone una API REST para administrar los umbrales de signos vitales por
 * paciente (PC_UMBRAL): los limites normales y criticos que la Lambda usa para
 * clasificar cada lectura y disparar alertas.
 */
@SpringBootApplication
public class ConfigApplication {
    public static void main(String[] args) {
        SpringApplication.run(ConfigApplication.class, args);
    }
}
