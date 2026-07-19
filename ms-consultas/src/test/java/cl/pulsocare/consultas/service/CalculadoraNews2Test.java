package cl.pulsocare.consultas.service;

import cl.pulsocare.consultas.model.Lectura;
import cl.pulsocare.consultas.model.PuntajeNews2;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Punto critico: la escala NEWS2 es una senal clinica de deterioro. Un umbral mal puesto
 * subestima el riesgo de un paciente. Estos tests fijan la tabla NEWS2 Scale 1 valor por
 * valor, incluidos los bordes de cada rango, que es donde los errores se esconden.
 */
class CalculadoraNews2Test {

    private static Lectura lec(String codigo, String valor) {
        return new Lectura(1L, 41L, 1L, codigo, codigo, new BigDecimal(valor), "u", null, null, "MIMIC");
    }

    private static int puntos(String codigo, String valor) {
        Integer p = CalculadoraNews2.puntosDe(codigo, new BigDecimal(valor));
        return p == null ? -1 : p;
    }

    // ---- tablas por parametro (bordes incluidos) -----------------------------

    @Test
    @DisplayName("Frecuencia respiratoria: 8→3, 9/11→1, 12/20→0, 21/24→2, 25→3")
    void fr() {
        assertThat(puntos("FR", "8")).isEqualTo(3);
        assertThat(puntos("FR", "9")).isEqualTo(1);
        assertThat(puntos("FR", "11")).isEqualTo(1);
        assertThat(puntos("FR", "12")).isEqualTo(0);
        assertThat(puntos("FR", "20")).isEqualTo(0);
        assertThat(puntos("FR", "21")).isEqualTo(2);
        assertThat(puntos("FR", "24")).isEqualTo(2);
        assertThat(puntos("FR", "25")).isEqualTo(3);
    }

    @Test
    @DisplayName("SpO2: 91→3, 92/93→2, 94/95→1, 96→0")
    void spo2() {
        assertThat(puntos("SPO2", "91")).isEqualTo(3);
        assertThat(puntos("SPO2", "92")).isEqualTo(2);
        assertThat(puntos("SPO2", "93")).isEqualTo(2);
        assertThat(puntos("SPO2", "94")).isEqualTo(1);
        assertThat(puntos("SPO2", "95")).isEqualTo(1);
        assertThat(puntos("SPO2", "96")).isEqualTo(0);
        assertThat(puntos("SPO2", "100")).isEqualTo(0);
    }

    @Test
    @DisplayName("Presion sistolica: 90→3, 100→2, 110→1, 111/219→0, 220→3")
    void pas() {
        assertThat(puntos("PAS", "90")).isEqualTo(3);
        assertThat(puntos("PAS", "91")).isEqualTo(2);
        assertThat(puntos("PAS", "100")).isEqualTo(2);
        assertThat(puntos("PAS", "101")).isEqualTo(1);
        assertThat(puntos("PAS", "110")).isEqualTo(1);
        assertThat(puntos("PAS", "111")).isEqualTo(0);
        assertThat(puntos("PAS", "219")).isEqualTo(0);
        assertThat(puntos("PAS", "220")).isEqualTo(3);
    }

    @Test
    @DisplayName("Frecuencia cardiaca: 40→3, 50→1, 51/90→0, 110→1, 130→2, 131→3")
    void fc() {
        assertThat(puntos("FC", "40")).isEqualTo(3);
        assertThat(puntos("FC", "41")).isEqualTo(1);
        assertThat(puntos("FC", "50")).isEqualTo(1);
        assertThat(puntos("FC", "51")).isEqualTo(0);
        assertThat(puntos("FC", "90")).isEqualTo(0);
        assertThat(puntos("FC", "91")).isEqualTo(1);
        assertThat(puntos("FC", "110")).isEqualTo(1);
        assertThat(puntos("FC", "111")).isEqualTo(2);
        assertThat(puntos("FC", "130")).isEqualTo(2);
        assertThat(puntos("FC", "131")).isEqualTo(3);
    }

    @Test
    @DisplayName("Temperatura: 35.0→3, 36.0→1, 36.1/38.0→0, 39.0→1, 39.1→2")
    void temp() {
        assertThat(puntos("TEMP", "35.0")).isEqualTo(3);
        assertThat(puntos("TEMP", "35.1")).isEqualTo(1);
        assertThat(puntos("TEMP", "36.0")).isEqualTo(1);
        assertThat(puntos("TEMP", "36.1")).isEqualTo(0);
        assertThat(puntos("TEMP", "38.0")).isEqualTo(0);
        assertThat(puntos("TEMP", "38.1")).isEqualTo(1);
        assertThat(puntos("TEMP", "39.0")).isEqualTo(1);
        assertThat(puntos("TEMP", "39.1")).isEqualTo(2);
    }

    @Test
    @DisplayName("La presion diastolica no participa de NEWS2 (puntos = null)")
    void pad_seIgnora() {
        assertThat(CalculadoraNews2.puntosDe("PAD", new BigDecimal("60"))).isNull();
    }

    // ---- agregacion y nivel de riesgo ----------------------------------------

    @Test
    @DisplayName("Paciente estable: todo en rango normal -> total 0, riesgo BAJO")
    void estable_bajo() {
        var r = CalculadoraNews2.calcular(List.of(
                lec("FC", "75"), lec("SPO2", "98"), lec("PAS", "120"),
                lec("TEMP", "36.7"), lec("FR", "16"), lec("PAD", "80")));
        assertThat(r.total()).isZero();
        assertThat(r.nivelRiesgo()).isEqualTo("BAJO");
        assertThat(r.banderaRoja()).isFalse();
        // PAD no debe aparecer en el detalle.
        assertThat(r.detalle()).extracting(PuntajeNews2.PuntajeSigno::signoCodigo).doesNotContain("PAD");
    }

    @Test
    @DisplayName("Un solo parametro en 3 (bandera roja) sube a MEDIO aunque el total sea bajo")
    void banderaRoja_medio() {
        // SpO2 91 (=3 pts) y el resto normal: total 3, pero un parametro en 3.
        var r = CalculadoraNews2.calcular(List.of(
                lec("FC", "75"), lec("SPO2", "91"), lec("PAS", "120"),
                lec("TEMP", "36.7"), lec("FR", "16")));
        assertThat(r.total()).isEqualTo(3);
        assertThat(r.banderaRoja()).isTrue();
        assertThat(r.nivelRiesgo()).isEqualTo("MEDIO");
    }

    @Test
    @DisplayName("Total 5-6 sin bandera roja -> MEDIO")
    void medio_porSuma() {
        // FR 22 (2) + SpO2 93 (2) + FC 95 (1) = 5, ninguno en 3.
        var r = CalculadoraNews2.calcular(List.of(
                lec("FR", "22"), lec("SPO2", "93"), lec("FC", "95"),
                lec("PAS", "120"), lec("TEMP", "36.7")));
        assertThat(r.total()).isEqualTo(5);
        assertThat(r.banderaRoja()).isFalse();
        assertThat(r.nivelRiesgo()).isEqualTo("MEDIO");
    }

    @Test
    @DisplayName("Total >=7 -> ALTO (respuesta de emergencia)")
    void alto() {
        // FR 26 (3) + SpO2 91 (3) + FC 115 (2) = 8.
        var r = CalculadoraNews2.calcular(List.of(
                lec("FR", "26"), lec("SPO2", "91"), lec("FC", "115"),
                lec("PAS", "120"), lec("TEMP", "36.7")));
        assertThat(r.total()).isEqualTo(8);
        assertThat(r.nivelRiesgo()).isEqualTo("ALTO");
    }

    @Test
    @DisplayName("Sin lecturas: total 0, riesgo BAJO, detalle vacio (no revienta)")
    void sinLecturas() {
        var r = CalculadoraNews2.calcular(List.of());
        assertThat(r.total()).isZero();
        assertThat(r.nivelRiesgo()).isEqualTo("BAJO");
        assertThat(r.detalle()).isEmpty();
    }

    @Test
    @DisplayName("Glasgow 15 -> alerta, 0 puntos")
    void concienciaAlerta() {
        var r = CalculadoraNews2.calcular(List.of(lec("GCS", "15")));
        assertThat(r.total()).isZero();
        assertThat(r.banderaRoja()).isFalse();
    }

    @Test
    @DisplayName("Glasgow bajo 15 -> 3 puntos y bandera roja (NEWS2 no gradua la conciencia)")
    void concienciaAlterada() {
        // 14 ya no es "alerta": NEWS2 salta directo a 3, no hay puntajes intermedios.
        var r = CalculadoraNews2.calcular(List.of(lec("GCS", "14")));
        assertThat(r.total()).isEqualTo(3);
        assertThat(r.banderaRoja()).isTrue();
    }

    @Test
    @DisplayName("Aire ambiente -> 0 puntos; con oxigeno -> 2 puntos")
    void oxigenoSuplementario() {
        assertThat(CalculadoraNews2.calcular(List.of(lec("O2SUP", "0"))).total()).isZero();

        var conOxigeno = CalculadoraNews2.calcular(List.of(lec("O2SUP", "1")));
        assertThat(conOxigeno.total()).isEqualTo(2);
        // 2 puntos no es bandera roja: esa se reserva a un parametro con 3.
        assertThat(conOxigeno.banderaRoja()).isFalse();
    }

    @Test
    @DisplayName("Los 7 parametros juntos suman como la escala clinica")
    void escalaCompleta() {
        // Caso real sembrado (paciente 2): GCS 9 (3) + O2 si (2) + FR 22 (2) = 7.
        var r = CalculadoraNews2.calcular(List.of(
                lec("FR", "22"), lec("SPO2", "97"), lec("FC", "80"),
                lec("PAS", "120"), lec("TEMP", "36.7"),
                lec("GCS", "9"), lec("O2SUP", "1")));
        assertThat(r.total()).isEqualTo(7);
        assertThat(r.nivelRiesgo()).isEqualTo("ALTO");
        assertThat(r.banderaRoja()).isTrue();
        assertThat(r.detalle()).hasSize(7);
    }
}
