package cl.pulsocare.auth.dto;

import jakarta.validation.constraints.NotBlank;

/** Credenciales para autenticar (correo + contrasena). */
public record LoginRequest(
        @NotBlank String correo,
        @NotBlank String pass
) {}
