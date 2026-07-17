package cl.pulsocare.consultas.service;

import cl.pulsocare.consultas.cache.CacheLecturas;
import cl.pulsocare.consultas.model.Lectura;
import cl.pulsocare.consultas.repo.LecturaRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
        when(repo.contar(anyLong(), any(), any(), any())).thenReturn(9000L);
        service().historico(41L, null, null, null, 5000, 0, null, null);   // pide 5000, tope 1000
        verify(repo).historico(eq(41L), isNull(), isNull(), isNull(), eq(1000), eq(0), isNull(), eq(false));
    }

    @Test
    @DisplayName("Sin limite explicito usa el maximo configurado")
    void historico_sinLimite_usaMaximo() {
        when(repo.contar(anyLong(), any(), any(), any())).thenReturn(9000L);
        service().historico(41L, null, null, null, null, null, null, null);
        verify(repo).historico(eq(41L), isNull(), isNull(), isNull(), eq(1000), eq(0), isNull(), eq(false));
    }

    @Test
    @DisplayName("Devuelve el total de filas, no solo las de la pagina")
    void historico_devuelveElTotal() {
        when(repo.contar(41L, null, null, null)).thenReturn(84210L);
        when(repo.historico(anyLong(), any(), any(), any(), anyInt(), anyInt(), any(), anyBoolean()))
                .thenReturn(List.of(lectura()));

        var pagina = service().historico(41L, null, null, null, 50, 100, "fecha", true);

        assertThat(pagina.total()).isEqualTo(84210L);
        assertThat(pagina.lecturas()).hasSize(1);
        verify(repo).historico(eq(41L), isNull(), isNull(), isNull(), eq(50), eq(100), eq("fecha"), eq(true));
    }

    @Test
    @DisplayName("Sin filas no consulta la pagina: se ahorra la segunda query")
    void historico_totalCero_noConsultaLaPagina() {
        when(repo.contar(anyLong(), any(), any(), any())).thenReturn(0L);

        var pagina = service().historico(41L, null, null, null, 50, 0, null, null);

        assertThat(pagina.total()).isZero();
        assertThat(pagina.lecturas()).isEmpty();
        verify(repo, never()).historico(anyLong(), any(), any(), any(), anyInt(), anyInt(), any(), anyBoolean());
    }

    @Test
    @DisplayName("Un offset negativo se normaliza a 0 en vez de romper la consulta")
    void historico_offsetNegativo_seNormaliza() {
        when(repo.contar(anyLong(), any(), any(), any())).thenReturn(10L);
        service().historico(41L, null, null, null, 50, -20, null, null);
        verify(repo).historico(eq(41L), isNull(), isNull(), isNull(), eq(50), eq(0), isNull(), eq(false));
    }

    @Test
    @DisplayName("Un orden no permitido responde 400 y NO llega al repositorio")
    void historico_ordenInvalido_400() {
        assertThatThrownBy(() -> service().historico(41L, null, null, null, 50, 0, "; DROP TABLE", null))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("400");

        verifyNoInteractions(repo);
    }
}
