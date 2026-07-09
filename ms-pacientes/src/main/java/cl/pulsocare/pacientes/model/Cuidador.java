package cl.pulsocare.pacientes.model;

import java.time.LocalDate;

/**
 * Cuidador (medico o familiar) asignado a un paciente, enriquecido con sus
 * datos y rol, para el panel del administrador.
 */
public record Cuidador(
        Long idAsignacion,
        Long idUsuario,
        String nombre,
        String apellidoPaterno,
        String correo,
        String rol,
        LocalDate fechaInicio
) {}
