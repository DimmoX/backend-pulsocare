package cl.pulsocare.pacientes.service;

import cl.pulsocare.pacientes.dto.ActualizarPacienteRequest;
import cl.pulsocare.pacientes.dto.CrearPacienteRequest;
import cl.pulsocare.pacientes.model.Paciente;
import cl.pulsocare.pacientes.repo.PacienteRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class PacienteService {

    private static final Logger log = LoggerFactory.getLogger(PacienteService.class);

    private final PacienteRepository repo;
    private final SubjectPool pool;

    public PacienteService(PacienteRepository repo, SubjectPool pool) {
        this.repo = repo;
        this.pool = pool;
    }

    public List<Paciente> listar() {
        return repo.listar();
    }

    public Paciente obtener(long id) {
        return repo.buscar(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Paciente " + id + " no encontrado"));
    }

    public Paciente crear(CrearPacienteRequest req) {
        Long subjectId = req.subjectId();
        if (subjectId == null) {
            // Regla de negocio: asignar un subject_id aleatorio del pool de MIMIC.
            subjectId = pool.asignar(repo.subjectsUsados());
        } else if (repo.existeSubject(subjectId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "El subject_id " + subjectId + " ya esta asignado a otro paciente");
        }

        Paciente nuevo = new Paciente(null, subjectId, req.nombre(), req.apellidoPaterno(),
                req.apellidoMaterno(), req.fechaNacimiento(), req.sexo(),
                req.idComuna(), req.idModalidad(), req.idEstadoPaciente());

        long id = repo.insertar(nuevo);
        log.info("Paciente {} creado con subject_id {}", id, subjectId);
        return repo.buscar(id).orElseThrow();
    }

    public Paciente actualizar(long id, ActualizarPacienteRequest req) {
        obtener(id); // 404 si no existe
        Paciente cambios = new Paciente(id, null, req.nombre(), req.apellidoPaterno(),
                req.apellidoMaterno(), req.fechaNacimiento(), req.sexo(),
                req.idComuna(), req.idModalidad(), req.idEstadoPaciente());
        repo.actualizar(id, cambios);
        return repo.buscar(id).orElseThrow();
    }

    public void eliminar(long id) {
        if (repo.eliminar(id) == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Paciente " + id + " no encontrado");
        }
    }
}
