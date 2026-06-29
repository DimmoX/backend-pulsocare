package cl.pulsocare.auth.service;

import cl.pulsocare.auth.dto.LoginRequest;
import cl.pulsocare.auth.dto.RegistroRequest;
import cl.pulsocare.auth.model.Usuario;
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

    @Value("${pulsocare.auth.rol-por-defecto:3}")
    private long rolPorDefecto;

    public AuthService(UsuarioRepository repo) {
        this.repo = repo;
    }

    /**
     * Registra o sincroniza al usuario que llega desde Azure Entra ID.
     * Idempotente: si el correo ya existe, actualiza nombre/telefono/contrasena.
     */
    public Usuario registrar(RegistroRequest req) {
        String[] nombre = separarNombre(req.displayName());
        String hash = encoder.encode(req.pass());

        if (repo.existeCorreo(req.correo())) {
            repo.actualizarSincronizacion(req.correo(), nombre[0], nombre[1], req.telefono(), hash);
            log.info("Usuario sincronizado: {}", req.correo());
        } else {
            long idRol = req.idRol() != null ? req.idRol() : rolPorDefecto;
            repo.insertar(idRol, nombre[0], nombre[1], req.correo(),
                    req.telefono(), req.entraOid(), req.idParentesco(), hash);
            log.info("Usuario registrado: {} (rol {})", req.correo(), idRol);
        }
        return repo.buscarPorCorreo(req.correo()).orElseThrow();
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
