package cl.pulsocare.config.service;

import cl.pulsocare.config.dto.ActualizarUmbralRequest;
import cl.pulsocare.config.dto.CrearUmbralRequest;
import cl.pulsocare.config.model.Umbral;
import cl.pulsocare.config.repo.UmbralRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class UmbralService {

    private static final Logger log = LoggerFactory.getLogger(UmbralService.class);

    private final UmbralRepository repo;

    public UmbralService(UmbralRepository repo) {
        this.repo = repo;
    }

    public List<Umbral> listar(Long idPaciente) {
        return idPaciente == null ? repo.listar() : repo.listarPorPaciente(idPaciente);
    }

    public Umbral obtener(long id) {
        return repo.buscar(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Umbral " + id + " no encontrado"));
    }

    public Umbral crear(CrearUmbralRequest req) {
        validarRangos(req.valorMin(), req.valorMax(), req.valorMinCritico(), req.valorMaxCritico());
        Umbral nuevo = new Umbral(null, req.idPaciente(), req.idSignoVital(),
                req.valorMin(), req.valorMax(), req.valorMinCritico(), req.valorMaxCritico(),
                null, null, req.idDefinidoPor());
        long id = repo.insertar(nuevo);
        log.info("Umbral {} creado para paciente {} signo {}", id, req.idPaciente(), req.idSignoVital());
        return repo.buscar(id).orElseThrow();
    }

    public Umbral actualizar(long id, ActualizarUmbralRequest req) {
        obtener(id); // 404 si no existe
        validarRangos(req.valorMin(), req.valorMax(), req.valorMinCritico(), req.valorMaxCritico());
        Umbral cambios = new Umbral(id, null, null,
                req.valorMin(), req.valorMax(), req.valorMinCritico(), req.valorMaxCritico(),
                null, null, req.idDefinidoPor());
        repo.actualizar(id, cambios);
        return repo.buscar(id).orElseThrow();
    }

    /** Baja logica: deja el umbral como no vigente (VIGENTE = 0). */
    public void desactivar(long id) {
        if (repo.desactivar(id) == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Umbral " + id + " no encontrado");
        }
    }

    /** El minimo no puede superar al maximo (misma regla que CK_UMBRAL_RANGO en la BD). */
    private void validarRangos(java.math.BigDecimal min, java.math.BigDecimal max,
                               java.math.BigDecimal minCritico, java.math.BigDecimal maxCritico) {
        if (min != null && max != null && min.compareTo(max) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "valorMin no puede ser mayor que valorMax");
        }
        if (minCritico != null && maxCritico != null && minCritico.compareTo(maxCritico) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "valorMinCritico no puede ser mayor que valorMaxCritico");
        }
    }
}
