package cl.pulsocare.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * PulsoCare - puerta de entrada unica del backend.
 *
 * Expone un solo host (:8080) al frontend y enruta cada ruta /api/** al
 * microservicio correspondiente. En AWS puede ir detras del API Gateway; en
 * local reemplaza a esa puerta para poder probar sin depender de AWS.
 */
@SpringBootApplication
public class GatewayApplication {
    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }
}
