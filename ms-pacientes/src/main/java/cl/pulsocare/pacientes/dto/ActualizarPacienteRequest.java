package cl.pulsocare.pacientes.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/** Datos editables de un paciente (el subject_id no se modifica). */
public record ActualizarPacienteRequest(
        @NotBlank @Size(max = 60) String nombre,
        @NotBlank @Size(max = 60) String apellidoPaterno,
        @Size(max = 60) String apellidoMaterno,
        LocalDate fechaNacimiento,
        @Size(max = 1) String sexo,
        Long idComuna,
        Long idModalidad,
        Long idEstadoPaciente
) {}
