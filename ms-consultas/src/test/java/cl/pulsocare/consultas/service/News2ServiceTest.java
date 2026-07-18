package cl.pulsocare.consultas.service;

import cl.pulsocare.consultas.model.Lectura;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * El puntaje se arma sobre las ultimas lecturas del paciente: si esa fuente cambia o
 * viene vacia, el servicio debe seguir respondiendo algo coherente y no reventar.
 */
@ExtendWith(MockitoExtension.class)
class News2ServiceTest {

    @Mock LecturaService lecturas;
    @InjectMocks News2Service service;

    private static Lectura lec(String codigo, String valor) {
        return new Lectura(1L, 41L, 1L, codigo, codigo, new BigDecimal(valor), "u", null, null, "MIMIC");
    }

    @Test
    @DisplayName("Calcula el puntaje a partir de las ultimas lecturas del paciente")
    void calcula_desdeUltimas() {
        when(lecturas.ultimas(41L)).thenReturn(List.of(
                lec("FR", "26"), lec("SPO2", "91"), lec("FC", "115"),
                lec("PAS", "120"), lec("TEMP", "36.7")));

        var r = service.calcular(41L);

        assertThat(r.total()).isEqualTo(8);
        assertThat(r.nivelRiesgo()).isEqualTo("ALTO");
        assertThat(r.banderaRoja()).isTrue();
    }

    @Test
    @DisplayName("Paciente sin lecturas: responde puntaje 0 y riesgo BAJO, sin fallar")
    void sinLecturas_noRevienta() {
        when(lecturas.ultimas(99L)).thenReturn(List.of());

        var r = service.calcular(99L);

        assertThat(r.total()).isZero();
        assertThat(r.nivelRiesgo()).isEqualTo("BAJO");
        assertThat(r.detalle()).isEmpty();
    }
}
