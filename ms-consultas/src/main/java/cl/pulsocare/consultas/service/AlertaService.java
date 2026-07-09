package cl.pulsocare.consultas.service;

import cl.pulsocare.consultas.model.Alerta;
import cl.pulsocare.consultas.repo.AlertaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class AlertaService {

    private static final Logger log = LoggerFactory.getLogger(AlertaService.class);

    private final AlertaRepository repo;

    public AlertaService(AlertaRepository repo) {
        this.repo = repo;
    }

    public List<Alerta> listar(Long idPaciente, String estadoCodigo) {
        return repo.listar(idPaciente, estadoCodigo);
    }

    public Alerta obtener(long id) {
        return repo.buscar(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Alerta " + id + " no encontrada"));
    }

    /** El medico reconoce la alerta: pasa a estado RECONOCIDA y queda registrado. */
    public Alerta reconocer(long id, long idUsuario) {
        obtener(id); // 404 si no existe
        repo.reconocer(id, idUsuario);
        log.info("Alerta {} reconocida por usuario {}", id, idUsuario);
        return repo.buscar(id).orElseThrow();
    }
}
