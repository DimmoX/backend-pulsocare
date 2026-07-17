package cl.pulsocare.consultas.service;

import cl.pulsocare.consultas.model.Alerta;
import cl.pulsocare.consultas.repo.AlertaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class AlertaService {

    private static final Logger log = LoggerFactory.getLogger(AlertaService.class);

    private final AlertaRepository repo;
    private final int limiteMaximo;
    private final int limitePorDefecto;

    public AlertaService(AlertaRepository repo,
                         @Value("${pulsocare.consultas.limite-maximo:1000}") int limiteMaximo,
                         @Value("${pulsocare.consultas.limite-alertas:200}") int limitePorDefecto) {
        this.repo = repo;
        this.limiteMaximo = limiteMaximo;
        this.limitePorDefecto = limitePorDefecto;
    }

    /**
     * El dashboard solo necesita las alertas recientes, asi que el defecto es bajo:
     * un cliente que no pida limite no puede arrastrar la tabla entera ni retener su
     * conexion del pool. limiteMaximo sigue siendo el tope para quien pida de mas.
     */
    public List<Alerta> listar(Long idPaciente, String estadoCodigo, Integer limite) {
        int efectivo = (limite == null || limite <= 0) ? limitePorDefecto : Math.min(limite, limiteMaximo);
        return repo.listar(idPaciente, estadoCodigo, efectivo);
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
