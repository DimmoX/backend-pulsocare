package cl.pulsocare.auth.service;

import cl.pulsocare.auth.b2c.GraphB2cService;
import cl.pulsocare.auth.dto.LoginRequest;
import cl.pulsocare.auth.dto.RegistroRequest;
import cl.pulsocare.auth.model.Usuario;
import cl.pulsocare.auth.notificacion.SnsSuscripcionService;
import cl.pulsocare.auth.repo.UsuarioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    private final UsuarioRepository repo;
    private final GraphB2cService graph;
    private final SnsSuscripcionService sns;

    @Value("${pulsocare.auth.rol-por-defecto:3}")
    private long rolPorDefecto;

    public AuthService(UsuarioRepository repo, GraphB2cService graph, SnsSuscripcionService sns) {
        this.repo = repo;
        this.graph = graph;
        this.sns = sns;
    }

    /**
     * Registra o sincroniza un usuario. Dos escenarios, distinguidos por el
     * entraOid del payload:
     *  - Sincronizacion de login (trae entraOid): el usuario ya existe en B2C;
     *    solo se hace upsert por correo en Oracle.
     *  - Creacion por el admin (sin entraOid) y usuario nuevo: ademas de Oracle,
     *    se crea la cuenta en Azure B2C (via Graph) y se devuelve la contrasena
     *    temporal para que el admin se la entregue al usuario.
     */
    public Usuario registrar(RegistroRequest req) {
        String[] nombre = separarNombre(req.displayName());
        String hash = encoder.encode(req.pass());

        if (repo.existeCorreo(req.correo())) {
            repo.actualizarSincronizacion(req.correo(), nombre[0], nombre[1], req.telefono(), hash);
            log.info("Usuario sincronizado: {}", req.correo());
            return repo.buscarPorCorreo(req.correo()).orElseThrow();
        }

        long idRol = req.idRol() != null ? req.idRol() : rolPorDefecto;

        // Creacion por el admin (sin entraOid): crear tambien la cuenta en B2C.
        String entraOid = req.entraOid();
        String passwordTemporal = null;
        boolean creacionAdmin = (entraOid == null || entraOid.isBlank());
        if (creacionAdmin && graph.estaHabilitado()) {
            var resultado = graph.crearUsuario(req.displayName(), req.correo(), rolAJobTitle(idRol));
            entraOid = resultado.oid();
            passwordTemporal = resultado.passwordTemporal();
        }

        repo.insertar(idRol, nombre[0], nombre[1], req.correo(),
                req.telefono(), entraOid, req.idParentesco(), hash);
        log.info("Usuario registrado: {} (rol {}, b2c={})", req.correo(), idRol, passwordTemporal != null);

        Usuario usuario = repo.buscarPorCorreo(req.correo()).orElseThrow();

        // Suscribir su correo al topic de alertas (filtrado por id_usuario) para
        // que reciba las alertas de sus pacientes. Solo roles que son cuidadores.
        if (esRolCuidador(idRol)) {
            sns.suscribir(usuario.idUsuario(), usuario.correo());
        }

        return passwordTemporal == null ? usuario : usuario.conPassword(passwordTemporal);
    }

    /** Roles que pueden ser equipo de cuidado (reciben alertas): Medico, Enfermero, Familiar. */
    private static boolean esRolCuidador(long idRol) {
        return idRol == 1 || idRol == 2 || idRol == 3;
    }

    /** Rol (PC_ROL) -> jobTitle de B2C, que el frontend usa para resolver el rol. */
    private static String rolAJobTitle(long idRol) {
        return switch ((int) idRol) {
            case 1 -> "Medico";
            case 2 -> "Enfermero";
            case 4 -> "Administrador";
            default -> "Familiar";
        };
    }

    /** Autentica por correo + contrasena. */
    public Usuario login(LoginRequest req) {
        String hash = repo.hashPorCorreo(req.correo())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Credenciales invalidas"));
        if (hash == null || !encoder.matches(req.pass(), hash)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Credenciales invalidas");
        }
        return repo.buscarPorCorreo(req.correo()).orElseThrow();
    }

    public List<Usuario> listar() {
        return repo.listar();
    }

    public Usuario obtener(long id) {
        return repo.buscarPorId(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuario " + id + " no encontrado"));
    }

    /** Separa el "nombre a mostrar" en nombre + apellido paterno. */
    private static String[] separarNombre(String displayName) {
        String dn = displayName.trim();
        int sp = dn.indexOf(' ');
        if (sp < 0) return new String[]{dn, dn};         // sin apellido: se duplica
        return new String[]{dn.substring(0, sp), dn.substring(sp + 1).trim()};
    }
}
