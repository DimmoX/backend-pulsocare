package cl.pulsocare.pacientes.service;

import cl.pulsocare.pacientes.dto.CrearPacienteRequest;
import cl.pulsocare.pacientes.model.Paciente;
import cl.pulsocare.pacientes.repo.PacienteRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Punto critico: PacienteService.crear() decide el subject_id y protege la
 * unicidad. Un fallo aqui duplica subjects (dos pacientes con los mismos signos)
 * o deja pacientes sin subject valido.
 */
@ExtendWith(MockitoExtension.class)
class PacienteServiceTest {

    @Mock PacienteRepository repo;
    @Mock SubjectPool pool;
    @InjectMocks PacienteService service;

    private CrearPacienteRequest req(Long subjectId) {
        return new CrearPacienteRequest("Rosa", "Fuentealba", "Soto",
                null, "F", null, null, null, subjectId);
    }

    private Paciente paciente(long id, long subjectId) {
        return new Paciente(id, subjectId, "Rosa", "Fuentealba", "Soto",
                null, "F", null, null, null);
    }

    @Test
    @DisplayName("Sin subject_id: asigna uno del pool y lo persiste")
    void crear_sinSubject_asignaDelPool() {
        when(repo.subjectsUsados()).thenReturn(Set.of(10005348L));
        when(pool.asignar(Set.of(10005348L))).thenReturn(10031757L);
        when(repo.insertar(any())).thenReturn(41L);
        when(repo.buscar(41L)).thenReturn(Optional.of(paciente(41, 10031757L)));

        Paciente creado = service.crear(req(null));

        assertThat(creado.subjectId()).isEqualTo(10031757L);
        ArgumentCaptor<Paciente> captor = ArgumentCaptor.forClass(Paciente.class);
        verify(repo).insertar(captor.capture());
        assertThat(captor.getValue().subjectId()).isEqualTo(10031757L);
    }

    @Test
    @DisplayName("Con subject_id ya asignado a otro paciente: responde 409 y NO inserta")
    void crear_subjectDuplicado_conflict() {
        when(repo.existeSubject(10005348L)).thenReturn(true);

        assertThatThrownBy(() -> service.crear(req(10005348L)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("409");

        verify(repo, never()).insertar(any());
        verifyNoInteractions(pool);
    }

    @Test
    @DisplayName("Con subject_id nuevo: usa ese subject (no pide uno al pool)")
    void crear_subjectNuevo_usaEseSubject() {
        when(repo.existeSubject(10039708L)).thenReturn(false);
        when(repo.insertar(any())).thenReturn(50L);
        when(repo.buscar(50L)).thenReturn(Optional.of(paciente(50, 10039708L)));

        service.crear(req(10039708L));

        ArgumentCaptor<Paciente> captor = ArgumentCaptor.forClass(Paciente.class);
        verify(repo).insertar(captor.capture());
        assertThat(captor.getValue().subjectId()).isEqualTo(10039708L);
        verify(pool, never()).asignar(any());
    }

    @Test
    @DisplayName("Obtener un paciente inexistente responde 404")
    void obtener_inexistente_404() {
        when(repo.buscar(999L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.obtener(999L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("404");
    }

    @Test
    @DisplayName("Eliminar un paciente inexistente responde 404")
    void eliminar_inexistente_404() {
        when(repo.eliminar(999L)).thenReturn(0);
        assertThatThrownBy(() -> service.eliminar(999L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("404");
    }
}
