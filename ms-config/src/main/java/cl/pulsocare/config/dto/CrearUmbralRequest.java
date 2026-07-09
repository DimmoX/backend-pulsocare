package cl.pulsocare.config.dto;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * Datos para definir un umbral. Los valores son opcionales (un signo puede
 * controlar solo minimo, solo maximo, o ambos); idDefinidoPor es el medico que
 * lo ajusta.
 */
public record CrearUmbralRequest(
        @NotNull Long idPaciente,
        @NotNull Long idSignoVital,
        BigDecimal valorMin,
        BigDecimal valorMax,
        BigDecimal valorMinCritico,
        BigDecimal valorMaxCritico,
        Long idDefinidoPor
) {}
