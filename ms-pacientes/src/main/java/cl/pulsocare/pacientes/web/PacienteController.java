package cl.pulsocare.pacientes.web;

import cl.pulsocare.pacientes.dto.ActualizarPacienteRequest;
import cl.pulsocare.pacientes.dto.CrearPacienteRequest;
import cl.pulsocare.pacientes.dto.EstadoPacienteRequest;
import cl.pulsocare.pacientes.model.Paciente;
import cl.pulsocare.pacientes.service.PacienteService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/pacientes")
public class PacienteController {

    private final PacienteService service;

    public PacienteController(PacienteService service) {
        this.service = service;
    }

    /**
     * Lista pacientes. soloMonitoreados=true excluye a los dados de alta (lo usa el
     * replayer); sin el flag, el panel del admin los ve todos para poder reactivarlos.
     */
    @GetMapping
    public List<Paciente> listar(
            @RequestParam(name = "soloMonitoreados", defaultValue = "false") boolean soloMonitoreados) {
        return service.listar(soloMonitoreados);
    }

    @GetMapping("/{id}")
    public Paciente obtener(@PathVariable long id) {
        return service.obtener(id);
    }

    @PostMapping
    public ResponseEntity<Paciente> crear(@Valid @RequestBody CrearPacienteRequest req,
                                          UriComponentsBuilder uri) {
        Paciente creado = service.crear(req);
        URI location = uri.path("/api/pacientes/{id}").buildAndExpand(creado.idPaciente()).toUri();
        return ResponseEntity.created(location).body(creado);
    }

    @PutMapping("/{id}")
    public Paciente actualizar(@PathVariable long id, @Valid @RequestBody ActualizarPacienteRequest req) {
        return service.actualizar(id, req);
    }

    /**
     * Da de alta a un paciente o lo reactiva, segun el codigo (ALTA / ESTABLE). Es una
     * baja logica: no se borra su historia clinica, solo se cierra el monitoreo.
     */
    @PutMapping("/{id}/estado")
    public Paciente cambiarEstado(@PathVariable long id, @Valid @RequestBody EstadoPacienteRequest req) {
        return service.cambiarEstado(id, req.codigo());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminar(@PathVariable long id) {
        service.eliminar(id);
    }
}
