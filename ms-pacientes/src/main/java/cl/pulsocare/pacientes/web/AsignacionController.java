package cl.pulsocare.pacientes.web;

import cl.pulsocare.pacientes.dto.CrearAsignacionRequest;
import cl.pulsocare.pacientes.model.Asignacion;
import cl.pulsocare.pacientes.model.Cuidador;
import cl.pulsocare.pacientes.model.Paciente;
import cl.pulsocare.pacientes.service.AsignacionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.List;

/**
 * Vínculo cuidador-paciente (PC_ASIGNACION_CUIDADO). Un cuidador es un usuario
 * (medico o familiar); asignarlo es lo que habilita sus notificaciones y su
 * acceso a los signos vitales del paciente.
 */
@RestController
public class AsignacionController {

    private final AsignacionService service;

    public AsignacionController(AsignacionService service) {
        this.service = service;
    }

    /** Cuidadores activos de un paciente (panel del administrador). */
    @GetMapping("/api/pacientes/{idPaciente}/asignaciones")
    public List<Cuidador> cuidadores(@PathVariable long idPaciente) {
        return service.cuidadores(idPaciente);
    }

    /** Asigna un cuidador (medico o familiar) al paciente. */
    @PostMapping("/api/pacientes/{idPaciente}/asignaciones")
    public ResponseEntity<Asignacion> asignar(@PathVariable long idPaciente,
                                              @Valid @RequestBody CrearAsignacionRequest req,
                                              UriComponentsBuilder uri) {
        Asignacion creada = service.asignar(idPaciente, req.idUsuario());
        URI location = uri.path("/api/pacientes/{idPaciente}/asignaciones")
                .buildAndExpand(idPaciente).toUri();
        return ResponseEntity.created(location).body(creada);
    }

    /** Quita el vínculo (baja lógica). */
    @DeleteMapping("/api/pacientes/{idPaciente}/asignaciones/{idUsuario}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void desasignar(@PathVariable long idPaciente, @PathVariable long idUsuario) {
        service.desasignar(idPaciente, idUsuario);
    }

    /** Pacientes que un usuario tiene a cargo (dashboard de medico/familiar). */
    @GetMapping("/api/usuarios/{idUsuario}/pacientes")
    public List<Paciente> pacientesDeUsuario(@PathVariable long idUsuario) {
        return service.pacientesDeUsuario(idUsuario);
    }
}
