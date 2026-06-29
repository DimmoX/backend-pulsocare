package cl.pulsocare.auth;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * PulsoCare - ms-auth.
 *
 * Recibe la identidad que el frontend obtiene desde Azure Entra ID
 * (nombre a mostrar, correo y contrasena) y la registra/sincroniza en
 * PC_USUARIO, ademas de autenticar (login) contra esa informacion.
 */
@SpringBootApplication
public class AuthApplication {
    public static void main(String[] args) {
        SpringApplication.run(AuthApplication.class, args);
    }
}
