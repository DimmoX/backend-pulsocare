package cl.pulsocare.consultas.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Una alerta enriquecida con el codigo del signo, el nivel (amarillo/rojo) y el
 * estado (generada/notificada/reconocida/resuelta), para el dashboard.
 */
public record Alerta(
        Long idAlerta,
        Long idLectura,
        Long idPaciente,
        Long idSignoVital,
        String signoCodigo,
        Long idNivelAlerta,
        String nivelCodigo,
        Long idEstadoAlerta,
        String estadoCodigo,
        BigDecimal valorRegistrado,
        String umbralViolado,
        LocalDateTime fechaGeneracion,
        Long idReconocidaPor,
        LocalDateTime fechaReconocimiento
) {}
