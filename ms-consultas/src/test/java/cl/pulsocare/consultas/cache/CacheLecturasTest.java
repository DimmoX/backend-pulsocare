package cl.pulsocare.consultas.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Punto critico: el cache NUNCA debe romper la consulta ni "envenenarse" con
 * resultados vacios. Si cacheara una lista vacia, el dashboard mostraria al
 * paciente sin signos hasta que expire el TTL, aun teniendo datos en Oracle.
 */
@ExtendWith(MockitoExtension.class)
class CacheLecturasTest {

    @Mock StringRedisTemplate redis;
    private final ObjectMapper json = new ObjectMapper();

    private CacheLecturas cache(boolean habilitado) {
        return new CacheLecturas(redis, json, habilitado, 30);
    }

    @Test
    @DisplayName("Deshabilitado: ultimas() devuelve vacio y no toca Redis")
    void deshabilitado_noTocaRedis() {
        assertThat(cache(false).ultimas(41L)).isEmpty();
        verifyNoInteractions(redis);
    }

    @Test
    @DisplayName("No cachea listas vacias (evita envenenar el cache)")
    void noCacheaVacios() {
        cache(true).guardarUltimas(41L, List.of());
        verifyNoInteractions(redis);   // no debe escribir nada
    }

    @Test
    @DisplayName("Degradacion elegante: si Redis falla, ultimas() devuelve vacio y cae a Oracle")
    void redisCaido_noRompe() {
        when(redis.opsForHash()).thenThrow(new RuntimeException("Redis no disponible"));
        assertThat(cache(true).ultimas(41L)).isEmpty();
    }

    @Test
    @DisplayName("Cache miss (hash vacio en Redis): devuelve vacio para que el servicio consulte Oracle")
    @SuppressWarnings({"unchecked", "rawtypes"})
    void cacheMiss_devuelveVacio() {
        HashOperations hashOps = mock(HashOperations.class);
        when(redis.opsForHash()).thenReturn(hashOps);
        when(hashOps.entries(anyString())).thenReturn(Map.of());
        assertThat(cache(true).ultimas(41L)).isEmpty();
    }
}
