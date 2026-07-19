package cl.pulsocare.config.service;

import cl.pulsocare.config.dto.ActualizarUmbralRequest;
import cl.pulsocare.config.dto.CrearUmbralRequest;
import cl.pulsocare.config.model.Umbral;
import cl.pulsocare.config.repo.UmbralRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
    @Mock cl.pulsocare.config.repo.BitacoraRepository bitacora;
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
        when(repo.buscar(77L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.desactivar(77L, 99L))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("404");
        verify(bitacora, never()).registrar(any(), any(), any(), any());
    }

    // --- auditoria y coherencia de los limites ---------------------------------

    @Test @DisplayName("crear un umbral queda registrado en la bitacora")
    void crear_registra_en_bitacora() {
        CrearUmbralRequest req = new CrearUmbralRequest(41L, 2L, bd("88"), bd("100"), bd("85"), bd("100"), 99L);
        when(repo.insertar(any())).thenReturn(7L);
        when(repo.buscar(7L)).thenReturn(Optional.of(
                new Umbral(7L, 41L, 2L, bd("88"), bd("100"), bd("85"), bd("100"), 1, null, 99L)));

        service.crear(req);

        verify(bitacora).registrar(eq(99L), eq(41L), eq("CREAR_UMBRAL"), contains("88"));
    }

    @Test @DisplayName("editar deja en la bitacora el valor anterior y el nuevo")
    void actualizar_registra_antes_y_despues() {
        when(repo.buscar(7L))
                .thenReturn(Optional.of(new Umbral(7L, 41L, 2L, bd("95"), bd("100"), bd("90"), bd("100"), 1, null, 99L)))
                .thenReturn(Optional.of(new Umbral(7L, 41L, 2L, bd("88"), bd("100"), bd("85"), bd("100"), 1, null, 99L)));

        service.actualizar(7L, new ActualizarUmbralRequest(bd("88"), bd("100"), bd("85"), bd("100"), 99L));

        ArgumentCaptor<String> detalle = ArgumentCaptor.forClass(String.class);
        verify(bitacora).registrar(eq(99L), eq(41L), eq("EDITAR_UMBRAL"), detalle.capture());
        // Debe poder reconstruirse el cambio: el 95 de antes y el 88 de despues.
        assertThat(detalle.getValue()).contains("95").contains("88");
    }

    @Test @DisplayName("el rango critico debe contener al normal")
    void critico_debe_contener_al_normal() {
        // Normal 90-100 con critico 95-100: un valor de 92 seria normal y critico a la vez.
        CrearUmbralRequest req = new CrearUmbralRequest(41L, 2L, bd("90"), bd("100"), bd("95"), bd("100"), 99L);
        assertThatThrownBy(() -> service.crear(req))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("minimo critico");
        verify(repo, never()).insertar(any());
        verify(bitacora, never()).registrar(any(), any(), any(), any());
    }

    @Test @DisplayName("un umbral invalido no se registra en la bitacora")
    void umbral_invalido_no_audita() {
        CrearUmbralRequest req = new CrearUmbralRequest(41L, 2L, bd("100"), bd("90"), bd("85"), bd("100"), 99L);
        assertThatThrownBy(() -> service.crear(req)).isInstanceOf(ResponseStatusException.class);
        verify(bitacora, never()).registrar(any(), any(), any(), any());
    }

    // --- el detalle debe decir QUE cambio, no repetir los cuatro limites --------

    @Test @DisplayName("editar solo un limite registra solo ese limite")
    void detalle_lista_solo_lo_que_cambio() {
        when(repo.buscar(7L))
                .thenReturn(Optional.of(new Umbral(7L, 41L, 1L, bd("60"), bd("100"), bd("40"), bd("130"), 1, null, 99L)))
                .thenReturn(Optional.of(new Umbral(7L, 41L, 1L, bd("60"), bd("105"), bd("40"), bd("130"), 1, null, 99L)));

        service.actualizar(7L, new ActualizarUmbralRequest(bd("60"), bd("105"), bd("40"), bd("130"), 99L));

        ArgumentCaptor<String> detalle = ArgumentCaptor.forClass(String.class);
        verify(bitacora).registrar(eq(99L), eq(41L), eq("EDITAR_UMBRAL"), detalle.capture());
        assertThat(detalle.getValue())
                .isEqualTo("Signo 1: Máx. normal: 100 -> 105")
                .doesNotContain("crítico");   // no se toco, no debe aparecer
    }

    @Test @DisplayName("cambiar dos limites los lista ambos")
    void detalle_lista_los_dos_que_cambiaron() {
        when(repo.buscar(7L))
                .thenReturn(Optional.of(new Umbral(7L, 41L, 1L, bd("60"), bd("100"), bd("40"), bd("130"), 1, null, 99L)))
                .thenReturn(Optional.of(new Umbral(7L, 41L, 1L, bd("60"), bd("105"), bd("35"), bd("130"), 1, null, 99L)));

        service.actualizar(7L, new ActualizarUmbralRequest(bd("60"), bd("105"), bd("35"), bd("130"), 99L));

        ArgumentCaptor<String> detalle = ArgumentCaptor.forClass(String.class);
        verify(bitacora).registrar(any(), any(), any(), detalle.capture());
        assertThat(detalle.getValue()).isEqualTo("Signo 1: Máx. normal: 100 -> 105, Mín. crítico: 40 -> 35");
    }

    @Test @DisplayName("distinta escala no es un cambio (60 y 60.00 son el mismo valor)")
    void escala_distinta_no_cuenta_como_cambio() {
        // La base devuelve NUMBER(6,2): un 60 enviado vuelve como 60.00. Con equals()
        // en vez de compareTo, la bitacora reportaria un cambio que nunca ocurrio.
        when(repo.buscar(7L))
                .thenReturn(Optional.of(new Umbral(7L, 41L, 1L, bd("60.00"), bd("100.00"), bd("40.00"), bd("130.00"), 1, null, 99L)))
                .thenReturn(Optional.of(new Umbral(7L, 41L, 1L, bd("60"), bd("105"), bd("40"), bd("130"), 1, null, 99L)));

        service.actualizar(7L, new ActualizarUmbralRequest(bd("60"), bd("105"), bd("40"), bd("130"), 99L));

        ArgumentCaptor<String> detalle = ArgumentCaptor.forClass(String.class);
        verify(bitacora).registrar(any(), any(), any(), detalle.capture());
        assertThat(detalle.getValue()).isEqualTo("Signo 1: Máx. normal: 100 -> 105");
    }

    @Test @DisplayName("al crear se listan los limites definidos, con su etiqueta")
    void detalle_al_crear_lista_los_definidos() {
        CrearUmbralRequest req = new CrearUmbralRequest(41L, 2L, bd("88"), bd("100"), bd("85"), bd("100"), 99L);
        when(repo.insertar(any())).thenReturn(7L);
        when(repo.buscar(7L)).thenReturn(Optional.of(
                new Umbral(7L, 41L, 2L, bd("88"), bd("100"), bd("85"), bd("100"), 1, null, 99L)));

        service.crear(req);

        ArgumentCaptor<String> detalle = ArgumentCaptor.forClass(String.class);
        verify(bitacora).registrar(eq(99L), eq(41L), eq("CREAR_UMBRAL"), detalle.capture());
        assertThat(detalle.getValue())
                .isEqualTo("Signo 2: Mín. normal: 88, Máx. normal: 100, Mín. crítico: 85, Máx. crítico: 100");
    }
}
