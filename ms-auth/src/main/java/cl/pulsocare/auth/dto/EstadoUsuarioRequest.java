package cl.pulsocare.auth.dto;

import jakarta.validation.constraints.NotBlank;

/** Alta o baja logica de un usuario: ACTIVO o INACTIVO (PC_USUARIO.CK_USUARIO_ESTADO). */
public record EstadoUsuarioRequest(
        @NotBlank String estado
) {}
