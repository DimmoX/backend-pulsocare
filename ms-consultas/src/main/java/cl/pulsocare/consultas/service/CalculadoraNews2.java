package cl.pulsocare.consultas.service;

import cl.pulsocare.consultas.model.Lectura;
import cl.pulsocare.consultas.model.PuntajeNews2;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Calcula la escala de alerta temprana NEWS2 (National Early Warning Score 2, Scale 1)
 * a partir de las ultimas lecturas de un paciente.
 *
 * Cada parametro aporta de 0 a 3 puntos segun rangos clinicos fijos; el total estima el
 * riesgo de deterioro. Es logica pura (sin estado ni dependencias) a proposito: asi es
 * facil de probar contra la tabla clinica, que es donde la correctitud importa.
 *
 * Cubre los 7 parametros de la escala: FR, SpO2, temperatura, PAS, FC, nivel de
 * conciencia y oxigeno suplementario. Los dos ultimos se derivan de MIMIC en el
 * replayer (el Glasgow sumado, y el dispositivo de oxigeno como si/no) y llegan ya
 * numericos. NEWS2 no usa la presion diastolica, que por eso queda fuera.
 */
public final class CalculadoraNews2 {

    private CalculadoraNews2() {}

    public static PuntajeNews2 calcular(List<Lectura> ultimas) {
        List<PuntajeNews2.PuntajeSigno> detalle = new ArrayList<>();
        int total = 0;
        boolean banderaRoja = false;

        for (Lectura l : ultimas) {
            Integer puntos = puntosDe(l.signoCodigo(), l.valorNum());
            if (puntos == null) continue;   // signo que NEWS2 no evalua (ej. PAD)
            detalle.add(new PuntajeNews2.PuntajeSigno(l.signoCodigo(), l.valorNum(), puntos));
            total += puntos;
            if (puntos == 3) banderaRoja = true;
        }
        return new PuntajeNews2(total, nivelRiesgo(total, banderaRoja), banderaRoja, detalle);
    }

    /** Puntos NEWS2 de un signo; null si la escala no lo evalua. */
    static Integer puntosDe(String codigo, BigDecimal valor) {
        if (valor == null) return null;
        double v = valor.doubleValue();
        return switch (codigo) {
            case "FR" -> frecuenciaRespiratoria(v);
            case "SPO2" -> saturacion(v);
            case "PAS" -> presionSistolica(v);
            case "FC" -> frecuenciaCardiaca(v);
            case "TEMP" -> temperatura(v);
            case "GCS" -> nivelDeConciencia(v);
            case "O2SUP" -> oxigenoSuplementario(v);
            default -> null;   // PAD y cualquier otro quedan fuera de NEWS2
        };
    }

    // ---- Tablas NEWS2 Scale 1 --------------------------------------------------

    private static int frecuenciaRespiratoria(double v) {
        if (v <= 8) return 3;
        if (v <= 11) return 1;
        if (v <= 20) return 0;
        if (v <= 24) return 2;
        return 3;
    }

    private static int saturacion(double v) {
        if (v <= 91) return 3;
        if (v <= 93) return 2;
        if (v <= 95) return 1;
        return 0;
    }

    private static int presionSistolica(double v) {
        if (v <= 90) return 3;
        if (v <= 100) return 2;
        if (v <= 110) return 1;
        if (v <= 219) return 0;
        return 3;
    }

    private static int frecuenciaCardiaca(double v) {
        if (v <= 40) return 3;
        if (v <= 50) return 1;
        if (v <= 90) return 0;
        if (v <= 110) return 1;
        if (v <= 130) return 2;
        return 3;
    }

    private static int temperatura(double v) {
        if (v <= 35.0) return 3;
        if (v <= 36.0) return 1;
        if (v <= 38.0) return 0;
        if (v <= 39.0) return 1;
        return 2;
    }

    /**
     * Nivel de conciencia. NEWS2 usa la escala ACVPU y solo distingue dos casos: el
     * paciente esta alerta (0 puntos) o no lo esta (3 puntos, sin grados intermedios).
     * Aqui llega como Glasgow sumado, donde 15 es el equivalente a "alerta".
     */
    private static int nivelDeConciencia(double v) {
        return v >= 15 ? 0 : 3;
    }

    /**
     * Oxigeno suplementario: 2 puntos por recibirlo, sin importar el dispositivo ni el
     * flujo. Que un paciente necesite oxigeno para sostener su saturacion es en si
     * mismo un signo de gravedad, aparte de cuanto marque el saturometro.
     */
    private static int oxigenoSuplementario(double v) {
        return v > 0 ? 2 : 0;
    }

    /**
     * Nivel de riesgo segun el total y la bandera roja, siguiendo la respuesta clinica
     * recomendada de NEWS2:
     *  - 0-4  : BAJO (monitoreo de rutina)
     *  - 3 en un solo parametro: MEDIO aunque el total sea bajo (revision urgente)
     *  - 5-6  : MEDIO (respuesta urgente)
     *  - >=7  : ALTO (respuesta de emergencia)
     */
    private static String nivelRiesgo(int total, boolean banderaRoja) {
        if (total >= 7) return "ALTO";
        if (total >= 5 || banderaRoja) return "MEDIO";
        return "BAJO";
    }
}
