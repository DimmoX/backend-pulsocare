package cl.pulsocare.gateway.seguridad;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Deja entrar a los procesos internos que no pueden presentar un token de usuario.
 *
 * El replayer consulta GET /api/pacientes para saber a quien reproducir, y es un
 * proceso sin persona detras: no hay forma de que complete un flujo de login en B2C.
 * Sin esta puerta, activar la autenticacion lo dejaria fuera y cortaria la ingesta de
 * signos vitales, que es justamente lo que sostiene toda la plataforma.
 *
 * La clave habilita SOLO las rutas de PROCESOS_PERMITIDOS. Aunque se filtrara, no
 * sirve para leer alertas, umbrales ni la bitacora.
 */
@Component
public class FiltroClaveDeServicio implements WebFilter {

    /** Cabecera donde el proceso presenta su clave. */
    public static final String CABECERA = "X-Api-Key";

    /** Lo unico que un servicio puede pedir: la lista de pacientes a monitorear. */
    private static final List<String> PROCESOS_PERMITIDOS = List.of("/api/pacientes");

    private final String claveEsperada;

    public FiltroClaveDeServicio(@Value("${pulsocare.clave-servicio:}") String claveEsperada) {
        this.claveEsperada = claveEsperada;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        if (!esLlamadaDeServicioValida(exchange.getRequest())) {
            return chain.filter(exchange);   // sigue la cadena: tendra que traer un JWT
        }
        var autenticacion = new UsernamePasswordAuthenticationToken(
                "servicio-interno", null, AuthorityUtils.createAuthorityList("ROLE_SERVICIO"));
        return chain.filter(exchange)
                .contextWrite(ReactiveSecurityContextHolder.withAuthentication(autenticacion));
    }

    private boolean esLlamadaDeServicioValida(ServerHttpRequest peticion) {
        // Sin clave configurada, la puerta no existe: es preferible que el replayer
        // falle de forma evidente a dejar una entrada abierta con un valor por defecto.
        if (claveEsperada == null || claveEsperada.isBlank()) return false;
        if (!HttpMethod.GET.equals(peticion.getMethod())) return false;

        String ruta = peticion.getPath().value();
        if (PROCESOS_PERMITIDOS.stream().noneMatch(ruta::equals)) return false;

        String recibida = peticion.getHeaders().getFirst(CABECERA);
        return recibida != null && constantesIguales(recibida, claveEsperada);
    }

    /**
     * Comparacion en tiempo constante. String.equals corta en el primer caracter
     * distinto, y ese tiempo de respuesta permite adivinar la clave byte a byte.
     */
    private static boolean constantesIguales(String a, String b) {
        if (a.length() != b.length()) return false;
        int diferencia = 0;
        for (int i = 0; i < a.length(); i++) {
            diferencia |= a.charAt(i) ^ b.charAt(i);
        }
        return diferencia == 0;
    }
}
