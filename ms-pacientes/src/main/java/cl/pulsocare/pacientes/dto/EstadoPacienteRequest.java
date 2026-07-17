package cl.pulsocare.pacientes.dto;

import jakarta.validation.constraints.NotBlank;

/** Cambio de estado clinico del paciente: ALTA (deja de monitorearse) o ESTABLE (reactivar). */
public record EstadoPacienteRequest(
        @NotBlank String codigo
) {}
