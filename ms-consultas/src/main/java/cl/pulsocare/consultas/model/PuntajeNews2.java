package cl.pulsocare.consultas.model;

import java.math.BigDecimal;
import java.util.List;

/**
 * Resultado de la escala de alerta temprana NEWS2 para un paciente, calculado a partir
 * de sus ultimas lecturas.
 *
 * Es una ADAPTACION de NEWS2: la escala original usa 7 parametros y PulsoCare captura 5
 * (FR, SpO2, temperatura, PAS y FC). Los dos que faltan —nivel de consciencia y si el
 * paciente recibe oxigeno suplementario— se asumen en su valor normal (alerta y aire
 * ambiente), que aportan 0 puntos. Por eso el puntaje es un piso, no el NEWS2 clinico
 * completo; sirve como senal de alerta temprana para que el medico revise.
 */
public record PuntajeNews2(
        int total,
        String nivelRiesgo,      // BAJO, MEDIO, ALTO
        boolean banderaRoja,     // algun parametro individual con 3 puntos (revision urgente)
        List<PuntajeSigno> detalle
) {
    /** Puntaje de un signo individual dentro de la escala. */
    public record PuntajeSigno(String signoCodigo, BigDecimal valor, int puntos) {}
}
