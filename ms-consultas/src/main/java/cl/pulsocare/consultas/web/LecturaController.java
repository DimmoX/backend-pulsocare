package cl.pulsocare.consultas.web;

import cl.pulsocare.consultas.model.Lectura;
import cl.pulsocare.consultas.service.LecturaService;
import org.springframework.format.annotation.DateTimeFormat;
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
     * Historico de lecturas del paciente. Filtros opcionales: idSignoVital,
     * desde/hasta (ISO, p.ej. 2026-07-08T10:00:00) y limite.
     */
    @GetMapping
    public List<Lectura> historico(
            @PathVariable long idPaciente,
            @RequestParam(required = false) Long idSignoVital,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime hasta,
            @RequestParam(required = false) Integer limite) {
        return service.historico(idPaciente, idSignoVital, desde, hasta, limite);
    }

    /** Ultima lectura de cada signo vital (para los tiles en vivo del dashboard). */
    @GetMapping("/ultimas")
    public List<Lectura> ultimas(@PathVariable long idPaciente) {
        return service.ultimas(idPaciente);
    }
}
