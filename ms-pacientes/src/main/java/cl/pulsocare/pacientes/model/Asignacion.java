package cl.pulsocare.pacientes.model;

import java.time.LocalDate;

/** Representa una fila de PC_ASIGNACION_CUIDADO (vínculo usuario-paciente). */
public record Asignacion(
        Long idAsignacion,
        Long idUsuario,
        Long idPaciente,
        LocalDate fechaInicio,
        LocalDate fechaFin,
        Integer activo
) {}
