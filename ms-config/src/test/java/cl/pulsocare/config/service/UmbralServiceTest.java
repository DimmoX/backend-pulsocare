package cl.pulsocare.config.service;

import cl.pulsocare.config.dto.ActualizarUmbralRequest;
import cl.pulsocare.config.dto.CrearUmbralRequest;
import cl.pulsocare.config.model.Umbral;
import cl.pulsocare.config.repo.UmbralRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * ms-config: gestion de umbrales (limites normal/critico por paciente y signo).
 * Puntos criticos: la validacion de rangos (min <= max) que refleja la
 * restriccion CK_UMBRAL_RANGO de la BD, y los 404 de recursos inexistentes.
 */
@ExtendWith(MockitoExtension.class)
class UmbralServiceTest {

    @Mock UmbralRepository repo;
    @InjectMocks UmbralService service;

    private static BigDecimal bd(String v) { return new BigDecimal(v); }

    private CrearUmbralRequest crear(String min, String max, String cmin, String cmax) {
        return new CrearUmbralRequest(1L, 1L,
                min == null ? null : bd(min), max == null ? null : bd(max),
                cmin == null ? null : bd(cmin), cmax == null ? null : bd(cmax), 99L);
    }

    private Umbral umbral(long id) {
        return new Umbral(id, 1L, 1L, bd("60"), bd("100"), bd("40"), bd("130"), 1, null, 99L);
    }

    // ---- listar --------------------------------------------------------------

    @Test @DisplayName("listar sin idPaciente consulta todos los umbrales")
    void listar_sinPaciente() {
        when(repo.listar()).thenReturn(List.of(umbral(1)));
        assertThat(service.listar(null)).hasSize(1);
        verify(repo).listar();
        verify(repo, never()).listarPorPaciente(anyLong());
    }

    @Test @DisplayName("listar con idPaciente filtra por paciente")
    void listar_conPaciente() {
        when(repo.listarPorPaciente(5L)).thenReturn(List.of(umbral(1)));
        assertThat(service.listar(5L)).hasSize(1);
        verify(repo).listarPorPaciente(5L);
        verify(repo, never()).listar();
    }

    // ---- obtener -------------------------------------------------------------

    @Test @DisplayName("obtener existente devuelve el umbral")
    void obtener_existente() {
        when(repo.buscar(3L)).thenReturn(Optional.of(umbral(3)));
        assertThat(service.obtener(3L).idUmbral()).isEqualTo(3L);
    }

    @Test @DisplayName("obtener inexistente responde 404")
    void obtener_inexistente_404() {
        when(repo.buscar(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.obtener(99L))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("404");
    }

    // ---- crear ---------------------------------------------------------------

    @Test @DisplayName("crear con rango valido inserta y devuelve el umbral")
    void crear_valido() {
        when(repo.insertar(any())).thenReturn(7L);
        when(repo.buscar(7L)).thenReturn(Optional.of(umbral(7)));
        assertThat(service.crear(crear("60", "100", "40", "130")).idUmbral()).isEqualTo(7L);
        verify(repo).insertar(any());
    }

    @Test @DisplayName("crear con valorMin > valorMax responde 400 y NO inserta")
    void crear_minMayorQueMax_400() {
        assertThatThrownBy(() -> service.crear(crear("120", "100", "40", "130")))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("400");
        verify(repo, never()).insertar(any());
    }

    @Test @DisplayName("crear con minCritico > maxCritico responde 400")
    void crear_criticosInvertidos_400() {
        assertThatThrownBy(() -> service.crear(crear("60", "100", "150", "130")))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("400");
        verify(repo, never()).insertar(any());
    }

    @Test @DisplayName("crear con limites nulos (rango parcial) es valido")
    void crear_conNulos_permitido() {
        when(repo.insertar(any())).thenReturn(8L);
        when(repo.buscar(8L)).thenReturn(Optional.of(umbral(8)));
        // solo define el maximo critico; el resto null no debe fallar la validacion
        assertThat(service.crear(crear(null, null, null, "130"))).isNotNull();
    }

    // ---- actualizar / desactivar --------------------------------------------

    @Test @DisplayName("actualizar un umbral inexistente responde 404 y NO actualiza")
    void actualizar_inexistente_404() {
        when(repo.buscar(50L)).thenReturn(Optional.empty());
        ActualizarUmbralRequest req = new ActualizarUmbralRequest(bd("60"), bd("100"), bd("40"), bd("130"), 99L);
        assertThatThrownBy(() -> service.actualizar(50L, req))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("404");
        verify(repo, never()).actualizar(anyLong(), any());
    }

    @Test @DisplayName("desactivar (baja logica) un umbral inexistente responde 404")
    void desactivar_inexistente_404() {
        when(repo.desactivar(77L)).thenReturn(0);
        assertThatThrownBy(() -> service.desactivar(77L))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("404");
    }
}
