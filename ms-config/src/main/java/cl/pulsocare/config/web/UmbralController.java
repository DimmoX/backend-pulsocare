package cl.pulsocare.config.web;

import cl.pulsocare.config.dto.ActualizarUmbralRequest;
import cl.pulsocare.config.dto.CrearUmbralRequest;
import cl.pulsocare.config.model.Umbral;
import cl.pulsocare.config.service.UmbralService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/umbrales")
public class UmbralController {

    private final UmbralService service;

    public UmbralController(UmbralService service) {
        this.service = service;
    }

    /** Lista umbrales; con ?idPaciente= filtra por paciente. */
    @GetMapping
    public List<Umbral> listar(@RequestParam(required = false) Long idPaciente) {
        return service.listar(idPaciente);
    }

    @GetMapping("/{id}")
    public Umbral obtener(@PathVariable long id) {
        return service.obtener(id);
    }

    @PostMapping
    public ResponseEntity<Umbral> crear(@Valid @RequestBody CrearUmbralRequest req,
                                        UriComponentsBuilder uri) {
        Umbral creado = service.crear(req);
        URI location = uri.path("/api/umbrales/{id}").buildAndExpand(creado.idUmbral()).toUri();
        return ResponseEntity.created(location).body(creado);
    }

    @PutMapping("/{id}")
    public Umbral actualizar(@PathVariable long id, @Valid @RequestBody ActualizarUmbralRequest req) {
        return service.actualizar(id, req);
    }

    /**
     * Baja logica del umbral: el signo vuelve a su rango por defecto.
     *
     * Pide idUsuario porque el cambio queda en la bitacora, y un ajuste de alarma sin
     * responsable identificado no es auditable.
     */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void desactivar(@PathVariable long id, @RequestParam Long idUsuario) {
        service.desactivar(id, idUsuario);
    }
}
