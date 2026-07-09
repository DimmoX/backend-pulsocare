package cl.pulsocare.config.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Representa una fila de PC_UMBRAL (limites normales y criticos por paciente/signo). */
public record Umbral(
        Long idUmbral,
        Long idPaciente,
        Long idSignoVital,
        BigDecimal valorMin,
        BigDecimal valorMax,
        BigDecimal valorMinCritico,
        BigDecimal valorMaxCritico,
        Integer vigente,
        LocalDateTime vigenteDesde,
        Long idDefinidoPor
) {}
