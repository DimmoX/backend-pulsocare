package cl.pulsocare.consultas.web;

import cl.pulsocare.consultas.dto.ReconocerAlertaRequest;
import cl.pulsocare.consultas.model.Alerta;
import cl.pulsocare.consultas.service.AlertaService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/alertas")
public class AlertaController {

    private final AlertaService service;

    public AlertaController(AlertaService service) {
        this.service = service;
    }

    /** Lista alertas; con ?idPaciente= y/o ?estado= (GENERADA/NOTIFICADA/RECONOCIDA/RESUELTA) filtra. */
    @GetMapping
    public List<Alerta> listar(@RequestParam(required = false) Long idPaciente,
                               @RequestParam(required = false) String estado) {
        return service.listar(idPaciente, estado);
    }

    @GetMapping("/{id}")
    public Alerta obtener(@PathVariable long id) {
        return service.obtener(id);
    }

    /** El medico reconoce/atiende la alerta (pasa a estado RECONOCIDA). */
    @PutMapping("/{id}/reconocer")
    public Alerta reconocer(@PathVariable long id, @Valid @RequestBody ReconocerAlertaRequest req) {
        return service.reconocer(id, req.idUsuario());
    }
}
