package cl.pulsocare.auth.b2c;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.util.List;
import java.util.Map;

/**
 * Crea usuarios en Azure AD B2C via Microsoft Graph (flujo client credentials).
 *
 * Solo actua si esta habilitado (pulsocare.b2c.enabled=true). Si no, es un
 * no-op y ms-auth funciona igual (util en local, sin credenciales de Graph).
 *
 * Requiere una app registration de B2C con permiso de aplicacion
 * User.ReadWrite.All (con consentimiento de admin) y un client secret.
 */
@Service
public class GraphB2cService {

    private static final Logger log = LoggerFactory.getLogger(GraphB2cService.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final boolean habilitado;
    private final String tenantId;
    private final String clientId;
    private final String clientSecret;
    private final String issuerDomain;

    private final RestClient rest = RestClient.create();

    public GraphB2cService(
            @Value("${pulsocare.b2c.enabled:false}") boolean habilitado,
            @Value("${pulsocare.b2c.tenant-id:}") String tenantId,
            @Value("${pulsocare.b2c.client-id:}") String clientId,
            @Value("${pulsocare.b2c.client-secret:}") String clientSecret,
            @Value("${pulsocare.b2c.issuer-domain:}") String issuerDomain) {
        this.habilitado = habilitado;
        this.tenantId = tenantId;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.issuerDomain = issuerDomain;
    }

    public boolean estaHabilitado() {
        return habilitado;
    }

    /**
     * Crea una cuenta local en B2C (inicio por email) con:
     *  - jobTitle = rol (para que el frontend resuelva el rol del token),
     *  - el correo tambien en otherMails (para que el claim "emails" venga poblado),
     *  - contrasena temporal forzando su cambio en el primer inicio de sesion.
     * Devuelve el oid del usuario creado y la contrasena temporal (para el admin).
     */
    public ResultadoB2c crearUsuario(String displayName, String correo, String jobTitle) {
        String token = obtenerToken();
        String passwordTemporal = generarPassword();

        Map<String, Object> body = Map.of(
                "accountEnabled", true,
                "displayName", displayName,
                "jobTitle", jobTitle,
                "identities", List.of(Map.of(
                        "signInType", "emailAddress",
                        "issuer", issuerDomain,
                        "issuerAssignedId", correo)),
                "otherMails", List.of(correo),
                "passwordProfile", Map.of(
                        "password", passwordTemporal,
                        "forceChangePasswordNextSignIn", true),
                "passwordPolicies", "DisablePasswordExpiration");

        try {
            Map<?, ?> respuesta = rest.post()
                    .uri("https://graph.microsoft.com/v1.0/users")
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(Map.class);

            String oid = respuesta != null ? String.valueOf(respuesta.get("id")) : null;
            log.info("Usuario B2C creado: {} (oid {})", correo, oid);
            return new ResultadoB2c(oid, passwordTemporal);
        } catch (RestClientResponseException e) {
            log.error("Graph rechazo la creacion de {}: {} -> {}",
                    correo, e.getStatusCode(), e.getResponseBodyAsString());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "No se pudo crear el usuario en Azure B2C (revisar si el correo ya existe).");
        }
    }

    /** Token de aplicacion (client credentials) para Microsoft Graph. */
    private String obtenerToken() {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_id", clientId);
        form.add("client_secret", clientSecret);
        form.add("scope", "https://graph.microsoft.com/.default");
        form.add("grant_type", "client_credentials");

        try {
            Map<?, ?> respuesta = rest.post()
                    .uri("https://login.microsoftonline.com/{tenant}/oauth2/v2.0/token", tenantId)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(Map.class);

            if (respuesta == null || respuesta.get("access_token") == null) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "No se obtuvo token de Graph");
            }
            return String.valueOf(respuesta.get("access_token"));
        } catch (RestClientResponseException e) {
            log.error("Fallo al obtener token de Graph: {} -> {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "No se pudo autenticar contra Microsoft Graph");
        }
    }

    /** Contrasena temporal que cumple la complejidad de B2C (may/min/dig/simb). */
    private static String generarPassword() {
        String may = "ABCDEFGHJKLMNPQRSTUVWXYZ";
        String min = "abcdefghijkmnpqrstuvwxyz";
        String dig = "23456789";
        String sim = "!@#$%*?-";
        String todos = may + min + dig + sim;
        StringBuilder sb = new StringBuilder();
        sb.append(may.charAt(RANDOM.nextInt(may.length())));
        sb.append(min.charAt(RANDOM.nextInt(min.length())));
        sb.append(dig.charAt(RANDOM.nextInt(dig.length())));
        sb.append(sim.charAt(RANDOM.nextInt(sim.length())));
        for (int i = 0; i < 12; i++) {
            sb.append(todos.charAt(RANDOM.nextInt(todos.length())));
        }
        return sb.toString();
    }

    /** oid del usuario creado en B2C + contrasena temporal a entregar al admin. */
    public record ResultadoB2c(String oid, String passwordTemporal) {}
}
