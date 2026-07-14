package cl.pulsocare.auth.notificacion;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.SubscribeRequest;

import java.util.Map;

/**
 * Suscribe automaticamente el correo de un usuario al topic SNS de alertas al
 * momento de crearlo, con una filter policy atada a su id_usuario. Asi el
 * usuario recibe SOLO las alertas de sus pacientes (el worker publica cada
 * alerta con el atributo id_usuario del destinatario) y el admin no tiene que
 * suscribir a nadie a mano: AWS envia el correo de confirmacion.
 *
 * Es best-effort: si SNS falla, se registra y la creacion del usuario continua.
 * Deshabilitado por defecto (util en local), se activa con SNS_SUBSCRIBE_ENABLED.
 */
@Service
public class SnsSuscripcionService {

    private static final Logger log = LoggerFactory.getLogger(SnsSuscripcionService.class);

    private final boolean habilitado;
    private final String topicArn;
    private final String region;
    private volatile SnsClient sns;   // se crea perezosamente solo si esta habilitado

    public SnsSuscripcionService(
            @Value("${pulsocare.sns.enabled:false}") boolean habilitado,
            @Value("${pulsocare.sns.topic-arn:}") String topicArn,
            @Value("${aws.region:us-east-1}") String region) {
        this.habilitado = habilitado;
        this.topicArn = topicArn;
        this.region = region;
    }

    public boolean estaHabilitado() {
        return habilitado && topicArn != null && !topicArn.isBlank();
    }

    /**
     * Crea la suscripcion email al topic con filter policy {"id_usuario":["<id>"]}.
     * AWS manda el correo de confirmacion; hasta que el usuario confirma, no recibe.
     */
    public void suscribir(long idUsuario, String correo) {
        if (!estaHabilitado()) return;
        if (correo == null || correo.isBlank()) return;
        try {
            String filtro = "{\"id_usuario\":[\"" + idUsuario + "\"]}";
            SubscribeRequest req = SubscribeRequest.builder()
                    .topicArn(topicArn)
                    .protocol("email")
                    .endpoint(correo)
                    .attributes(Map.of("FilterPolicy", filtro))
                    .returnSubscriptionArn(true)
                    .build();
            cliente().subscribe(req);
            log.info("Suscripcion SNS creada para usuario {} ({}); pendiente de confirmacion.",
                    idUsuario, correo);
        } catch (Exception e) {
            log.warn("No se pudo suscribir a SNS al usuario {} ({}): {}", idUsuario, correo, e.getMessage());
        }
    }

    private SnsClient cliente() {
        if (sns == null) {
            synchronized (this) {
                if (sns == null) {
                    sns = SnsClient.builder()
                            .region(Region.of(region))
                            .credentialsProvider(DefaultCredentialsProvider.create())
                            .build();
                }
            }
        }
        return sns;
    }
}
