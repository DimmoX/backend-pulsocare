package cl.pulsocare.pacientes.model;

import java.time.LocalDate;

/** Representa una fila de PC_PACIENTE. */
public record Paciente(
        Long idPaciente,
        Long subjectId,
        String nombre,
        String apellidoPaterno,
        String apellidoMaterno,
        LocalDate fechaNacimiento,
        String sexo,
        Long idComuna,
        Long idModalidad,
        Long idEstadoPaciente
) {}
