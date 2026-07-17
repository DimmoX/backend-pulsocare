package cl.pulsocare.auth.service;

import cl.pulsocare.auth.b2c.GraphB2cService;
import cl.pulsocare.auth.b2c.GraphB2cService.ResultadoB2c;
import cl.pulsocare.auth.dto.LoginRequest;
import cl.pulsocare.auth.dto.RegistroRequest;
import cl.pulsocare.auth.model.Usuario;
import cl.pulsocare.auth.notificacion.SnsSuscripcionService;
import cl.pulsocare.auth.repo.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Punto critico: AuthService.registrar() decide entre sincronizar un login y
 * crear un usuario nuevo (Oracle + Azure B2C + suscripcion SNS). Un error aqui
 * crea cuentas invalidas o deja usuarios sin poder loguear/recibir alertas.
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock UsuarioRepository repo;
    @Mock GraphB2cService graph;
    @Mock SnsSuscripcionService sns;
    @InjectMocks AuthService service;

    private static final String CORREO = "ana@pulsocare.cl";

    private Usuario usuario(long id, long idRol) {
        return usuario(id, idRol, "ACTIVO");
    }

    private Usuario usuario(long id, long idRol, String estado) {
        return new Usuario(id, idRol, "Medico", "Ana", "Diaz", null,
                CORREO, null, "oid-existente", null, estado, null);
    }

    private RegistroRequest req(String entraOid, Long idRol) {
        return new RegistroRequest("Ana Diaz", CORREO, "Secreta123", null, entraOid, idRol, null);
    }

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "rolPorDefecto", 3L);
    }

    @Test
    @DisplayName("Login-sync (correo ya existe): actualiza en Oracle, NO toca B2C ni SNS")
    void loginSync_soloActualiza() {
        when(repo.existeCorreo(CORREO)).thenReturn(true);
        when(repo.buscarPorCorreo(CORREO)).thenReturn(Optional.of(usuario(7, 1)));

        Usuario r = service.registrar(req("oid-existente", 1L));

        assertThat(r.idUsuario()).isEqualTo(7);
        assertThat(r.passwordTemporal()).isNull();
        verify(repo).actualizarSincronizacion(eq(CORREO), any(), any(), any(), anyString());
        verify(repo, never()).insertar(anyLong(), any(), any(), any(), any(), any(), any(), any());
        verifyNoInteractions(graph, sns);
    }

    @Test
    @DisplayName("Creacion por admin con B2C habilitado: crea en B2C y devuelve la contrasena temporal")
    void creacionAdmin_conB2c_creaYDevuelvePassword() {
        when(repo.existeCorreo(CORREO)).thenReturn(false);
        when(graph.estaHabilitado()).thenReturn(true);
        when(graph.crearUsuario(eq("Ana Diaz"), eq(CORREO), eq("Medico")))
                .thenReturn(new ResultadoB2c("oid-nuevo", "Temp#Ab12cd"));
        when(repo.buscarPorCorreo(CORREO)).thenReturn(Optional.of(usuario(10, 1)));

        Usuario r = service.registrar(req(null, 1L));   // sin entraOid = creacion por admin

        assertThat(r.passwordTemporal()).isEqualTo("Temp#Ab12cd");
        // El OID que devuelve B2C debe persistirse en Oracle (6º argumento de insertar).
        verify(repo).insertar(eq(1L), anyString(), anyString(), eq(CORREO), any(),
                eq("oid-nuevo"), any(), anyString());
    }

    @Test
    @DisplayName("Creacion con B2C deshabilitado: NO crea en B2C y NO devuelve contrasena")
    void creacionAdmin_sinB2c_noCreaCuentaExterna() {
        when(repo.existeCorreo(CORREO)).thenReturn(false);
        when(graph.estaHabilitado()).thenReturn(false);
        when(repo.buscarPorCorreo(CORREO)).thenReturn(Optional.of(usuario(11, 1)));

        Usuario r = service.registrar(req(null, 1L));

        assertThat(r.passwordTemporal()).isNull();
        verify(graph, never()).crearUsuario(any(), any(), any());
        verify(repo).insertar(eq(1L), anyString(), anyString(), eq(CORREO), any(),
                isNull(), any(), anyString());   // entraOid null
    }

    @Test
    @DisplayName("Rol cuidador (medico) se suscribe a SNS; rol administrador NO")
    void suscripcionSns_soloParaCuidadores() {
        when(repo.existeCorreo(CORREO)).thenReturn(false);
        when(graph.estaHabilitado()).thenReturn(false);
        when(repo.buscarPorCorreo(CORREO)).thenReturn(Optional.of(usuario(12, 1)));
        service.registrar(req(null, 1L));                 // medico
        verify(sns, times(1)).suscribir(eq(12L), eq(CORREO));

        clearInvocations(sns, repo, graph);
        when(repo.existeCorreo(CORREO)).thenReturn(false);
        when(graph.estaHabilitado()).thenReturn(false);
        when(repo.buscarPorCorreo(CORREO)).thenReturn(Optional.of(usuario(13, 4)));
        service.registrar(req(null, 4L));                 // administrador
        verify(sns, never()).suscribir(anyLong(), anyString());
    }

    @Test
    @DisplayName("Login con contrasena incorrecta responde 401")
    void login_credencialInvalida_401() {
        String hash = new BCryptPasswordEncoder().encode("correcta");
        when(repo.hashPorCorreo(CORREO)).thenReturn(Optional.of(hash));

        assertThatThrownBy(() -> service.login(new LoginRequest(CORREO, "incorrecta")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("401");
    }

    @Test
    @DisplayName("Login correcto devuelve el usuario")
    void login_ok_devuelveUsuario() {
        String hash = new BCryptPasswordEncoder().encode("correcta");
        when(repo.hashPorCorreo(CORREO)).thenReturn(Optional.of(hash));
        when(repo.buscarPorCorreo(CORREO)).thenReturn(Optional.of(usuario(7, 1)));

        assertThat(service.login(new LoginRequest(CORREO, "correcta")).idUsuario()).isEqualTo(7);
    }

    // ---- baja logica de usuarios (lo que el admin ve como "eliminar") ----------

    @Test
    @DisplayName("Un usuario INACTIVO no puede volver a entrar aunque su cuenta viva en B2C")
    void loginSync_usuarioInactivo_403() {
        when(repo.existeCorreo(CORREO)).thenReturn(true);
        when(repo.buscarPorCorreo(CORREO)).thenReturn(Optional.of(usuario(1, 1, "INACTIVO")));

        assertThatThrownBy(() -> service.registrar(req("oid-existente", null)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("403");

        // No debe quedar rastro de la sesion: ni sincroniza ni lo reactiva.
        verify(repo, never()).actualizarSincronizacion(anyString(), anyString(), any(), any(), anyString());
    }

    @Test
    @DisplayName("Un usuario ACTIVO sigue sincronizando con normalidad")
    void loginSync_usuarioActivo_ok() {
        when(repo.existeCorreo(CORREO)).thenReturn(true);
        when(repo.buscarPorCorreo(CORREO)).thenReturn(Optional.of(usuario(1, 1, "ACTIVO")));

        assertThat(service.registrar(req("oid-existente", null)).estado()).isEqualTo("ACTIVO");
        verify(repo).actualizarSincronizacion(eq(CORREO), anyString(), any(), any(), anyString());
    }

    @Test
    @DisplayName("Dar de baja: actualiza el estado, NO borra la fila")
    void cambiarEstado_inactivo() {
        when(repo.actualizarEstado(7L, "INACTIVO")).thenReturn(1);
        when(repo.buscarPorId(7L)).thenReturn(Optional.of(usuario(7, 1, "INACTIVO")));

        assertThat(service.cambiarEstado(7L, "INACTIVO").estado()).isEqualTo("INACTIVO");
        verify(repo).actualizarEstado(7L, "INACTIVO");
    }

    @Test
    @DisplayName("La baja es reversible: se puede rehabilitar")
    void cambiarEstado_reactivar() {
        when(repo.actualizarEstado(7L, "ACTIVO")).thenReturn(1);
        when(repo.buscarPorId(7L)).thenReturn(Optional.of(usuario(7, 1, "ACTIVO")));

        assertThat(service.cambiarEstado(7L, "ACTIVO").estado()).isEqualTo("ACTIVO");
    }

    @Test
    @DisplayName("Un estado fuera del CHECK de la tabla responde 400 y no toca la BD")
    void cambiarEstado_invalido_400() {
        assertThatThrownBy(() -> service.cambiarEstado(7L, "BORRADO"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("400");

        verify(repo, never()).actualizarEstado(anyLong(), anyString());
    }

    @Test
    @DisplayName("Cambiar el estado de un usuario inexistente responde 404")
    void cambiarEstado_inexistente_404() {
        when(repo.actualizarEstado(999L, "INACTIVO")).thenReturn(0);

        assertThatThrownBy(() -> service.cambiarEstado(999L, "INACTIVO"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("404");
    }
}
