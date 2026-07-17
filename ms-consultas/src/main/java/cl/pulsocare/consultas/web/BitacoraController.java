package cl.pulsocare.consultas.web;

import cl.pulsocare.consultas.dto.RegistroBitacoraRequest;
import cl.pulsocare.consultas.model.EventoBitacora;
import cl.pulsocare.consultas.service.BitacoraService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Auditoria de accesos: deja constancia de quien vio que dato de que paciente. */
@RestController
@RequestMapping("/api/bitacora")
public class BitacoraController {

    private final BitacoraService service;

    public BitacoraController(BitacoraService service) {
        this.service = service;
    }

    /**
     * Registra un acceso. Responde 202 (aceptado) y no 200/201 a proposito: es un evento
     * best-effort, el cliente no debe depender de que se haya persistido para seguir.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void registrar(@Valid @RequestBody RegistroBitacoraRequest req, HttpServletRequest http) {
        service.registrarAcceso(req.idUsuario(), req.idPaciente(), req.accion(), req.detalle(), ipDe(http));
    }

    /**
     * Lista los ultimos accesos, con nombres legibles, para el panel del administrador.
     * El control de que solo el ADMIN llegue aqui lo hace el frontend (ruta protegida por
     * rol); este servicio no valida JWT.
     */
    @GetMapping
    public List<EventoBitacora> listar(@RequestParam(required = false) Integer limite) {
        return service.listar(limite);
    }

    /** IP del cliente, respetando el X-Forwarded-For que agrega el API Gateway/proxy. */
    private static String ipDe(HttpServletRequest http) {
        String reenviada = http.getHeader("X-Forwarded-For");
        if (reenviada != null && !reenviada.isBlank()) {
            // El primer valor de la lista es el cliente original.
            return reenviada.split(",")[0].trim();
        }
        return http.getRemoteAddr();
    }
}
