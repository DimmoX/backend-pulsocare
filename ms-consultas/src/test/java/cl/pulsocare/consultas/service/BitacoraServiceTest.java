package cl.pulsocare.consultas.service;

import cl.pulsocare.consultas.repo.BitacoraRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Punto critico: la auditoria de accesos. Debe registrar el evento pero NUNCA romper la
 * accion del usuario si el registro falla, y respetar los limites de columna de la tabla.
 */
@ExtendWith(MockitoExtension.class)
class BitacoraServiceTest {

    @Mock BitacoraRepository repo;
    @InjectMocks BitacoraService service;

    @Test
    @DisplayName("Registra el acceso tal cual cuando entra dentro de los limites")
    void registra_ok() {
        service.registrarAcceso(45L, 41L, "VER_HISTORICO", "detalle corto", "1.2.3.4");
        verify(repo).registrar(45L, 41L, "VER_HISTORICO", "detalle corto", "1.2.3.4");
    }

    @Test
    @DisplayName("Un fallo del INSERT NO se propaga: la auditoria no tumba la funcionalidad")
    void registra_fallo_noPropaga() {
        doThrow(new RuntimeException("FK invalida")).when(repo)
                .registrar(anyLong(), any(), anyString(), any(), any());

        assertThatCode(() -> service.registrarAcceso(999L, 41L, "VER_HISTORICO", null, "ip"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Recorta ACCION y DETALLE a los limites de la columna")
    void registra_recortaLargos() {
        String accionLarga = "A".repeat(80);   // columna: 50
        String detalleLargo = "D".repeat(500);  // columna: 400
        service.registrarAcceso(45L, 41L, accionLarga, detalleLargo, "ip");

        ArgumentCaptor<String> accion = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> detalle = ArgumentCaptor.forClass(String.class);
        verify(repo).registrar(eq(45L), eq(41L), accion.capture(), detalle.capture(), eq("ip"));
        assertThat(accion.getValue()).hasSize(50);
        assertThat(detalle.getValue()).hasSize(400);
    }

    @Test
    @DisplayName("idPaciente y detalle pueden ir nulos (ej. un LOGIN)")
    void registra_conNulos() {
        service.registrarAcceso(45L, null, "LOGIN", null, "ip");
        verify(repo).registrar(45L, null, "LOGIN", null, "ip");
    }

    @Test
    @DisplayName("Listar sin limite usa el defecto (200)")
    void listar_sinLimite_usaDefecto() {
        service.listar(null);
        verify(repo).listar(200);
    }

    @Test
    @DisplayName("Listar acota el limite pedido al maximo (1000)")
    void listar_acotaAlMaximo() {
        service.listar(50000);
        verify(repo).listar(1000);
    }

    @Test
    @DisplayName("Un limite valido se respeta")
    void listar_limiteValido() {
        service.listar(30);
        verify(repo).listar(30);
    }
}
