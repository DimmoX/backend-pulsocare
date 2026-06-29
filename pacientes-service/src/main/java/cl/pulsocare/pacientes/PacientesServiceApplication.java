package cl.pulsocare.pacientes;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * PulsoCare - microservicio de pacientes (cold path).
 *
 * Expone una API REST para administrar pacientes. Al crear un paciente sin
 * subject_id, le asigna uno aleatorio del pool de MIMIC-IV (preferentemente uno
 * no usado), que es el vinculo para mostrar sus signos vitales reales.
 */
@SpringBootApplication
public class PacientesServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(PacientesServiceApplication.class, args);
    }
}
