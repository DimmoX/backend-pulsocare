package cl.pulsocare.consultas.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Evento de auditoria que envia el frontend. idPaciente es opcional (no todo evento es
 * sobre un paciente); detalle es texto libre corto para dar contexto.
 */
public record RegistroBitacoraRequest(
        @NotNull Long idUsuario,
        Long idPaciente,
        @NotBlank String accion,
        String detalle
) {}
