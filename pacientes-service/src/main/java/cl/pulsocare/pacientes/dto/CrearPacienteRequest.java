package cl.pulsocare.pacientes.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * Datos para crear un paciente. subjectId es opcional: si no se envia, el
 * servicio asigna uno aleatorio del pool de MIMIC.
 */
public record CrearPacienteRequest(
        @NotBlank @Size(max = 60) String nombre,
        @NotBlank @Size(max = 60) String apellidoPaterno,
        @Size(max = 60) String apellidoMaterno,
        LocalDate fechaNacimiento,
        @Size(max = 1) String sexo,
        Long idComuna,
        Long idModalidad,
        Long idEstadoPaciente,
        Long subjectId
) {}
