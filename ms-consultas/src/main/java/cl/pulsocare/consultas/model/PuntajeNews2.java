package cl.pulsocare.consultas.model;

import java.math.BigDecimal;
import java.util.List;

/**
 * Resultado de la escala de alerta temprana NEWS2 para un paciente, calculado a partir
 * de sus ultimas lecturas.
 *
 * Cubre los 7 parametros de la escala clinica: FR, SpO2, temperatura, PAS, FC, nivel de
 * consciencia y oxigeno suplementario. Estos dos ultimos existen en MIMIC como texto (el
 * Glasgow por componentes, y el dispositivo de oxigeno) y el replayer los convierte a
 * numero antes de publicarlos, para que entren al pipeline como cualquier otro signo.
 *
 * El total sigue siendo un piso cuando al paciente le falta alguna lectura: el detalle
 * dice sobre cuantos parametros se calculo, y la vista lo advierte. Es una senal de
 * alerta temprana para que el medico revise, no un diagnostico.
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
