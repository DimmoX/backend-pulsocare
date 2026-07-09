package cl.pulsocare.consultas.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Una lectura de signo vital enriquecida con el codigo/nombre del signo, para
 * que el dashboard reciba etiquetas legibles y no solo IDs.
 */
public record Lectura(
        Long idLectura,
        Long idPaciente,
        Long idSignoVital,
        String signoCodigo,
        String signoNombre,
        BigDecimal valorNum,
        String unidad,
        LocalDateTime fechaMedicion,
        LocalDateTime fechaRegistro,
        String origen
) {}
