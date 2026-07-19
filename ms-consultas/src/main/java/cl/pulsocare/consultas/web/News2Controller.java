package cl.pulsocare.consultas.web;

import cl.pulsocare.consultas.model.PuntajeNews2;
import cl.pulsocare.consultas.service.News2Service;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Escala de alerta temprana NEWS2 del paciente, calculada sobre sus ultimas lecturas.
 * Es una senal para que el equipo medico revise, no un diagnostico.
 */
@RestController
@RequestMapping("/api/pacientes/{idPaciente}/news2")
public class News2Controller {

    private final News2Service service;

    public News2Controller(News2Service service) {
        this.service = service;
    }

    @GetMapping
    public PuntajeNews2 obtener(@PathVariable long idPaciente) {
        return service.calcular(idPaciente);
    }
}
