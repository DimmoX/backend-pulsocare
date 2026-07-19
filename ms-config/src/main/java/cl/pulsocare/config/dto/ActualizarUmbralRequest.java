package cl.pulsocare.config.dto;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * Valores editables de un umbral (el paciente y el signo no se modifican).
 * idDefinidoPor identifica al medico que hace el cambio, para la bitacora.
 */
public record ActualizarUmbralRequest(
        BigDecimal valorMin,
        BigDecimal valorMax,
        BigDecimal valorMinCritico,
        BigDecimal valorMaxCritico,
        @NotNull Long idDefinidoPor
) {}
