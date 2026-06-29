package cl.pulsocare.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Identidad que llega desde el frontend (capturada en Azure Entra ID):
 * nombre a mostrar, correo y contrasena. Los demas campos son opcionales.
 */
public record RegistroRequest(
        @NotBlank @Size(max = 120) String displayName,
        @NotBlank @Email @Size(max = 120) String correo,
        @NotBlank String pass,
        @Size(max = 20) String telefono,
        @Size(max = 100) String entraOid,
        Long idRol,
        Long idParentesco
) {}
