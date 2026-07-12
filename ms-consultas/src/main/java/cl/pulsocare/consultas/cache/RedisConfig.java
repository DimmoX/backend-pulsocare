package cl.pulsocare.consultas.cache;

import io.lettuce.core.cluster.ClusterClientOptions;
import io.lettuce.core.cluster.ClusterTopologyRefreshOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisClusterConfiguration;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;

import java.time.Duration;
import java.util.List;

/**
 * Construye la conexion a Redis segun el entorno:
 *   - Local:  standalone, sin TLS (un contenedor Redis).
 *   - AWS:    ElastiCache con modo cluster + cifrado en transito (TLS).
 * Se controla por variables de entorno (REDIS_CLUSTER, REDIS_SSL, ...), asi el
 * mismo jar sirve en ambos casos sin recompilar.
 */
@Configuration
public class RedisConfig {

    @Bean
    public LettuceConnectionFactory redisConnectionFactory(
            @Value("${pulsocare.redis.host:localhost}") String host,
            @Value("${pulsocare.redis.port:6379}") int port,
            @Value("${pulsocare.redis.cluster:false}") boolean cluster,
            @Value("${pulsocare.redis.ssl:false}") boolean ssl) {

        LettuceClientConfiguration.LettuceClientConfigurationBuilder client =
                LettuceClientConfiguration.builder().commandTimeout(Duration.ofSeconds(2));
        if (ssl) {
            client.useSsl();
        }

        if (cluster) {
            // ElastiCache en modo cluster: se descubre la topologia desde el
            // configuration endpoint y se refresca de forma periodica.
            RedisClusterConfiguration conf = new RedisClusterConfiguration(List.of(host + ":" + port));
            ClusterTopologyRefreshOptions refresh = ClusterTopologyRefreshOptions.builder()
                    .enablePeriodicRefresh(Duration.ofSeconds(30))
                    .enableAllAdaptiveRefreshTriggers()
                    .build();
            client.clientOptions(ClusterClientOptions.builder()
                    .topologyRefreshOptions(refresh)
                    .validateClusterNodeMembership(false)
                    .build());
            return new LettuceConnectionFactory(conf, client.build());
        }

        RedisStandaloneConfiguration conf = new RedisStandaloneConfiguration(host, port);
        return new LettuceConnectionFactory(conf, client.build());
    }
}
