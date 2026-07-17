package cl.pulsocare.consultas.web;

import cl.pulsocare.consultas.model.Lectura;
import cl.pulsocare.consultas.service.LecturaService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/pacientes/{idPaciente}/lecturas")
public class LecturaController {

    private final LecturaService service;

    public LecturaController(LecturaService service) {
        this.service = service;
    }

    /**
     * Una pagina del historico de lecturas del paciente. Filtros opcionales:
     * idSignoVital, desde/hasta (ISO, p.ej. 2026-07-08T10:00:00), y la ventana con
     * limite/offset. orden acepta fecha|valor|signo|origen y ascendente su sentido.
     * El total de filas que cumplen los filtros va en la cabecera X-Total-Count.
     */
    @GetMapping
    public ResponseEntity<List<Lectura>> historico(
            @PathVariable long idPaciente,
            @RequestParam(required = false) Long idSignoVital,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime hasta,
            @RequestParam(required = false) Integer limite,
            @RequestParam(required = false) Integer offset,
            @RequestParam(required = false) String orden,
            @RequestParam(required = false) Boolean ascendente) {
        var pagina = service.historico(idPaciente, idSignoVital, desde, hasta,
                limite, offset, orden, ascendente);
        // El total va en cabecera y no en el cuerpo: asi el endpoint sigue devolviendo
        // un array de lecturas y no rompe a quien ya lo consume.
        return ResponseEntity.ok()
                .header("X-Total-Count", String.valueOf(pagina.total()))
                .body(pagina.lecturas());
    }

    /** Ultima lectura de cada signo vital (para los tiles en vivo del dashboard). */
    @GetMapping("/ultimas")
    public List<Lectura> ultimas(@PathVariable long idPaciente) {
        return service.ultimas(idPaciente);
    }
}
