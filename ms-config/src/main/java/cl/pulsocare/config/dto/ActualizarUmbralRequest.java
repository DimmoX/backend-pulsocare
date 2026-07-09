package cl.pulsocare.config.dto;

import java.math.BigDecimal;

/** Valores editables de un umbral (el paciente y el signo no se modifican). */
public record ActualizarUmbralRequest(
        BigDecimal valorMin,
        BigDecimal valorMax,
        BigDecimal valorMinCritico,
        BigDecimal valorMaxCritico,
        Long idDefinidoPor
) {}
