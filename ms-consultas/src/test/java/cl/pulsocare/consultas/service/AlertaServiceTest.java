package cl.pulsocare.consultas.service;

import cl.pulsocare.consultas.repo.AlertaRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;

/**
 * Punto critico: el listado de alertas alimenta el dashboard y se refresca cada
 * pocos segundos. Sin acotar la cantidad de filas, un paciente con meses de
 * monitoreo devuelve decenas de miles de alertas: la consulta retiene su conexion
 * del pool durante minutos y termina agotandolo para todo ms-consultas.
 */
@ExtendWith(MockitoExtension.class)
class AlertaServiceTest {

    @Mock AlertaRepository repo;

    private AlertaService service() {
        return new AlertaService(repo, 1000, 200);   // tope = 1000, defecto = 200
    }

    @Test
    @DisplayName("Sin limite explicito usa el defecto bajo, no el tope (nunca sin acotar)")
    void listar_sinLimite_usaDefecto() {
        service().listar(41L, null, null);
        verify(repo).listar(eq(41L), isNull(), eq(200));
    }

    @Test
    @DisplayName("El limite pedido se acota al maximo configurado")
    void listar_acotaLimiteAlMaximo() {
        service().listar(41L, null, 50000);
        verify(repo).listar(eq(41L), isNull(), eq(1000));
    }

    @Test
    @DisplayName("Un limite menor al maximo se respeta")
    void listar_limiteMenor_seRespeta() {
        service().listar(41L, null, 50);
        verify(repo).listar(eq(41L), isNull(), eq(50));
    }

    @Test
    @DisplayName("Un limite invalido (0 o negativo) cae al defecto")
    void listar_limiteInvalido_usaDefecto() {
        service().listar(41L, null, 0);
        verify(repo).listar(eq(41L), isNull(), eq(200));

        service().listar(41L, null, -5);
        verify(repo, org.mockito.Mockito.times(2)).listar(eq(41L), isNull(), eq(200));
    }

    @Test
    @DisplayName("El filtro por estado se pasa al repositorio junto con el limite")
    void listar_conEstado_pasaAmbosFiltros() {
        service().listar(41L, "GENERADA", 20);
        verify(repo).listar(eq(41L), eq("GENERADA"), eq(20));
    }
}
