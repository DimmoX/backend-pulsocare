package cl.pulsocare.consultas.service;

import cl.pulsocare.consultas.cache.CacheLecturas;
import cl.pulsocare.consultas.model.Lectura;
import cl.pulsocare.consultas.repo.LecturaRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Punto critico: el patron cache-aside. Un cache hit NO debe golpear Oracle
 * (rendimiento) y un cache miss SI debe consultar Oracle y poblar el cache.
 * Ademas el limite del historico se acota para no traer millones de filas.
 */
@ExtendWith(MockitoExtension.class)
class LecturaServiceTest {

    @Mock LecturaRepository repo;
    @Mock CacheLecturas cache;

    private LecturaService service() {
        return new LecturaService(repo, cache, 1000);   // limite maximo = 1000
    }

    private Lectura lectura() {
        return new Lectura(1L, 41L, 1L, "FC", "Frecuencia cardiaca",
                new BigDecimal("82"), "bpm", LocalDateTime.now(), LocalDateTime.now(), "MIMIC");
    }

    @Test
    @DisplayName("Cache hit: devuelve del cache y NO consulta Oracle")
    void ultimas_cacheHit_noConsultaOracle() {
        List<Lectura> enCache = List.of(lectura());
        when(cache.ultimas(41L)).thenReturn(Optional.of(enCache));

        assertThat(service().ultimas(41L)).isEqualTo(enCache);
        verify(repo, never()).ultimasPorSigno(anyLong());
    }

    @Test
    @DisplayName("Cache miss: consulta Oracle y puebla el cache")
    void ultimas_cacheMiss_consultaOracleYGuarda() {
        List<Lectura> deOracle = List.of(lectura());
        when(cache.ultimas(41L)).thenReturn(Optional.empty());
        when(repo.ultimasPorSigno(41L)).thenReturn(deOracle);

        assertThat(service().ultimas(41L)).isEqualTo(deOracle);
        verify(repo).ultimasPorSigno(41L);
        verify(cache).guardarUltimas(41L, deOracle);
    }

    @Test
    @DisplayName("El limite pedido se acota al maximo configurado")
    void historico_acotaLimiteAlMaximo() {
        service().historico(41L, null, null, null, 5000);   // pide 5000, tope 1000
        verify(repo).historico(eq(41L), isNull(), isNull(), isNull(), eq(1000));
    }

    @Test
    @DisplayName("Sin limite explicito usa el maximo configurado")
    void historico_sinLimite_usaMaximo() {
        service().historico(41L, null, null, null, null);
        verify(repo).historico(eq(41L), isNull(), isNull(), isNull(), eq(1000));
    }
}
