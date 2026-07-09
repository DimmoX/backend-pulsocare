package cl.pulsocare.pacientes.dto;

import jakarta.validation.constraints.NotNull;

/** Usuario (medico o familiar) que se asigna al cuidado de un paciente. */
public record CrearAsignacionRequest(
        @NotNull Long idUsuario
) {}
