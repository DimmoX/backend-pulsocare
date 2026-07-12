package cl.pulsocare.consultas.cache;

import cl.pulsocare.consultas.model.Lectura;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Cache de la "ventana reciente" (ultimas lecturas por signo) en Redis /
 * ElastiCache, para que el dashboard cargue sin golpear Oracle.
 *
 * Estructura: un HASH por paciente
 *   clave  = ultimas:{idPaciente}
 *   campo  = {idSignoVital}
 *   valor  = JSON de la Lectura
 * con un TTL configurable.
 *
 * Degradacion elegante: si el cache esta deshabilitado o Redis falla, los
 * metodos devuelven "sin dato" y el servicio cae a Oracle. El cache NUNCA
 * rompe la consulta.
 */
@Component
public class CacheLecturas {

    private static final Logger log = LoggerFactory.getLogger(CacheLecturas.class);

    private final StringRedisTemplate redis;
    private final ObjectMapper json;
    private final boolean habilitado;
    private final Duration ttl;

    public CacheLecturas(StringRedisTemplate redis,
                         ObjectMapper json,
                         @Value("${pulsocare.cache.enabled:false}") boolean habilitado,
                         @Value("${pulsocare.cache.ttl-segundos:30}") long ttlSegundos) {
        this.redis = redis;
        this.json = json;
        this.habilitado = habilitado;
        this.ttl = Duration.ofSeconds(ttlSegundos);
    }

    private static String clave(long idPaciente) {
        return "ultimas:" + idPaciente;
    }

    /** Lee la ventana reciente del cache. Vacio si no esta, esta deshabilitado o falla Redis. */
    public Optional<List<Lectura>> ultimas(long idPaciente) {
        if (!habilitado) return Optional.empty();
        try {
            Map<Object, Object> hash = redis.opsForHash().entries(clave(idPaciente));
            if (hash == null || hash.isEmpty()) return Optional.empty();
            List<Lectura> lecturas = new ArrayList<>(hash.size());
            for (Object valor : hash.values()) {
                lecturas.add(json.readValue((String) valor, Lectura.class));
            }
            return Optional.of(lecturas);
        } catch (Exception e) {
            log.warn("Cache no disponible al leer paciente {}: {}", idPaciente, e.getMessage());
            return Optional.empty();
        }
    }

    /** Guarda la ventana reciente en el cache (con TTL). Silencioso si falla. */
    public void guardarUltimas(long idPaciente, List<Lectura> lecturas) {
        if (!habilitado || lecturas == null || lecturas.isEmpty()) return;
        try {
            Map<String, String> campos = new HashMap<>();
            for (Lectura l : lecturas) {
                campos.put(String.valueOf(l.idSignoVital()), json.writeValueAsString(l));
            }
            String clave = clave(idPaciente);
            redis.opsForHash().putAll(clave, campos);
            redis.expire(clave, ttl);
        } catch (Exception e) {
            log.warn("Cache no disponible al escribir paciente {}: {}", idPaciente, e.getMessage());
        }
    }
}
