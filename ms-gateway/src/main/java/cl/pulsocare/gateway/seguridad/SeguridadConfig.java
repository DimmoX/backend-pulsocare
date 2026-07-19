package cl.pulsocare.gateway.seguridad;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Autenticacion en el unico punto de entrada del backend.
 *
 * Antes el gateway enrutaba cualquier peticion que llegara: un GET a /api/pacientes sin
 * token devolvia la lista completa de pacientes con sus datos personales. Ahora toda
 * ruta exige un token valido emitido por Azure AD B2C, salvo las excepciones de abajo.
 *
 * Se valida aqui y no en cada microservicio a proposito: es el unico camino hacia
 * ellos, asi que un solo punto de control cubre a todos y no hay forma de olvidarse de
 * proteger uno nuevo.
 */
@Configuration
@EnableWebFluxSecurity
public class SeguridadConfig {

    private final FiltroClaveDeServicio filtroClaveDeServicio;
    private final String origenFrontend;

    public SeguridadConfig(FiltroClaveDeServicio filtroClaveDeServicio,
                           @Value("${FRONTEND_ORIGIN:http://localhost:4200}") String origenFrontend) {
        this.filtroClaveDeServicio = filtroClaveDeServicio;
        this.origenFrontend = origenFrontend;
    }

    /**
     * CORS para Spring Security.
     *
     * El gateway ya define globalcors, pero esa configuracion la aplica un filtro que
     * corre DESPUES de Security: sin este bean, Security rechaza el preflight con 403 y
     * el navegador bloquea todas las llamadas del frontend. Se repiten aqui los mismos
     * valores del application.yml a proposito, porque son dos capas distintas.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration cors = new CorsConfiguration();
        cors.setAllowedOrigins(List.of(origenFrontend));
        cors.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        cors.setAllowedHeaders(List.of("authorization", "content-type", FiltroClaveDeServicio.CABECERA));
        // Sin esto el navegador oculta X-Total-Count y el historico no sabria paginar.
        cors.setExposedHeaders(List.of("X-Total-Count"));
        cors.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource fuente = new UrlBasedCorsConfigurationSource();
        fuente.registerCorsConfiguration("/**", cors);
        return fuente;
    }

    @Bean
    public SecurityWebFilterChain cadena(ServerHttpSecurity http) {
        return http
                // El gateway ya resuelve CORS con globalcors; aqui solo se le indica a
                // Security que lo respete, o rechazaria el preflight antes de llegar.
                .cors(Customizer.withDefaults())
                // Sin sesiones ni formularios: el estado vive en el token.
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                // Autentica a los servicios internos (el replayer) por clave compartida,
                // antes de que la cadena exija un JWT que un proceso no tiene como pedir.
                .addFilterAt(filtroClaveDeServicio, SecurityWebFiltersOrder.AUTHENTICATION)
                .authorizeExchange(ex -> ex
                        // El navegador manda el preflight sin cabecera Authorization: si
                        // se exigiera token aqui, TODA llamada del frontend fallaria.
                        .pathMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        // Sondas de salud: las consulta el propio Docker, sin credenciales.
                        .pathMatchers("/actuator/health/**", "/actuator/info").permitAll()
                        .anyExchange().authenticated())
                .oauth2ResourceServer(oauth -> oauth.jwt(Customizer.withDefaults()))
                .build();
    }
}
