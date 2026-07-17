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

    // Codigos del catalogo PC_ESTADO_PACIENTE.
    private static final String ESTADO_ALTA = "ALTA";
    private static final String ESTADO_ACTIVO_POR_DEFECTO = "ESTABLE";

    public List<Paciente> listar() {
        return listar(false);
    }

    /**
     * Lista pacientes. Con soloMonitoreados=true excluye a los dados de alta: eso lo usa
     * el replayer para dejar de generarles lecturas. El panel del admin llama sin el flag
     * para poder ver y reactivar tambien a los de alta.
     */
    public List<Paciente> listar(boolean soloMonitoreados) {
        if (soloMonitoreados) {
            Long idAlta = repo.idEstadoPorCodigo(ESTADO_ALTA);
            if (idAlta != null) {
                return repo.listarExcluyendoEstado(idAlta);
            }
        }
        return repo.listar();
    }

    /**
     * Da de alta a un paciente (deja de monitorearse) o lo reactiva. No se borra: un
     * DELETE fallaria por las seis FKs sin CASCADE, y en salud no se elimina la historia
     * clinica; solo se cierra el episodio de monitoreo.
     */
    public Paciente cambiarEstado(long idPaciente, String codigo) {
        obtener(idPaciente); // 404 si no existe
        Long idEstado = repo.idEstadoPorCodigo(codigo);
        if (idEstado == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Estado no valido: " + codigo);
        }
        repo.actualizarEstado(idPaciente, idEstado);
        log.info("Paciente {} pasa al estado {}", idPaciente, codigo);
        return repo.buscar(idPaciente).orElseThrow();
    }

    /** Reactiva a un paciente dado de alta, devolviendolo al monitoreo. */
    public Paciente reactivar(long idPaciente) {
        return cambiarEstado(idPaciente, ESTADO_ACTIVO_POR_DEFECTO);
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
        // Todo paciente nuevo nace con sus umbrales clinicos por defecto, para que
        // ms-config los devuelva y el monitoreo tenga rangos por paciente desde el alta.
        repo.crearUmbralesPorDefecto(id);
        log.info("Paciente {} creado con subject_id {} y umbrales por defecto", id, subjectId);
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
