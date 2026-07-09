package cl.pulsocare.pacientes.service;

import cl.pulsocare.pacientes.model.Asignacion;
import cl.pulsocare.pacientes.model.Cuidador;
import cl.pulsocare.pacientes.model.Paciente;
import cl.pulsocare.pacientes.repo.AsignacionRepository;
import cl.pulsocare.pacientes.repo.PacienteRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class AsignacionService {

    private static final Logger log = LoggerFactory.getLogger(AsignacionService.class);

    private final AsignacionRepository repo;
    private final PacienteRepository pacientes;

    public AsignacionService(AsignacionRepository repo, PacienteRepository pacientes) {
        this.repo = repo;
        this.pacientes = pacientes;
    }

    /** Asigna un cuidador al paciente. 404 si el paciente no existe, 409 si ya estaba asignado. */
    public Asignacion asignar(long idPaciente, long idUsuario) {
        pacientes.buscar(idPaciente).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Paciente " + idPaciente + " no encontrado"));
        if (repo.buscarActiva(idPaciente, idUsuario).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "El usuario " + idUsuario + " ya está asignado a este paciente");
        }
        repo.asignar(idPaciente, idUsuario);
        log.info("Usuario {} asignado al paciente {}", idUsuario, idPaciente);
        return repo.buscarActiva(idPaciente, idUsuario).orElseThrow();
    }

    /** Quita el vínculo activo. 404 si no había una asignación activa. */
    public void desasignar(long idPaciente, long idUsuario) {
        if (repo.desactivar(idPaciente, idUsuario) == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "No hay una asignación activa del usuario " + idUsuario + " en el paciente " + idPaciente);
        }
        log.info("Usuario {} desasignado del paciente {}", idUsuario, idPaciente);
    }

    public List<Cuidador> cuidadores(long idPaciente) {
        return repo.cuidadoresDePaciente(idPaciente);
    }

    public List<Paciente> pacientesDeUsuario(long idUsuario) {
        return repo.pacientesDeUsuario(idUsuario);
    }
}
