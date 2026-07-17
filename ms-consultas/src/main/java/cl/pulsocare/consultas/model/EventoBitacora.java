package cl.pulsocare.consultas.model;

import java.time.LocalDateTime;

/**
 * Una fila de la bitacora, ya resuelta con nombres legibles para mostrarla en pantalla
 * (no solo los ids). El paciente puede ser null si el evento no era sobre un paciente.
 */
public record EventoBitacora(
        Long idBitacora,
        Long idUsuario,
        String usuario,
        String rol,
        Long idPaciente,
        String paciente,
        String accion,
        String detalle,
        String direccionIp,
        LocalDateTime fechaEvento
) {}
