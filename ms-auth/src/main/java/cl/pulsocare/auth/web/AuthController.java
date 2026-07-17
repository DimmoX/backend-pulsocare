package cl.pulsocare.auth.web;

import cl.pulsocare.auth.dto.EstadoUsuarioRequest;
import cl.pulsocare.auth.dto.LoginRequest;
import cl.pulsocare.auth.dto.RegistroRequest;
import cl.pulsocare.auth.model.Usuario;
import cl.pulsocare.auth.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService service;

    public AuthController(AuthService service) {
        this.service = service;
    }

    /** Registra/sincroniza al usuario que llega desde Azure Entra ID. */
    @PostMapping("/registro")
    public Usuario registro(@Valid @RequestBody RegistroRequest req) {
        return service.registrar(req);
    }

    /** Autentica por correo + contrasena (401 si no coincide). */
    @PostMapping("/login")
    public Usuario login(@Valid @RequestBody LoginRequest req) {
        return service.login(req);
    }

    @GetMapping("/usuarios")
    public List<Usuario> usuarios() {
        return service.listar();
    }

    @GetMapping("/usuarios/{id}")
    public Usuario usuario(@PathVariable long id) {
        return service.obtener(id);
    }

    /**
     * Da de baja o rehabilita a un usuario (ACTIVO / INACTIVO).
     *
     * Es un PUT sobre el estado y no un DELETE porque eso es lo que ocurre de verdad:
     * el usuario no se borra (siete FKs lo impiden, y en salud no se borra quien
     * reconocio una alerta), se desactiva. Y asi la operacion es reversible.
     */
    @PutMapping("/usuarios/{id}/estado")
    public Usuario cambiarEstado(@PathVariable long id, @Valid @RequestBody EstadoUsuarioRequest req) {
        return service.cambiarEstado(id, req.estado());
    }
}
