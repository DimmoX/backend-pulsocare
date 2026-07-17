package cl.pulsocare.consultas.repo;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;

/**
 * Punto critico: el ORDER BY se concatena al SQL (el nombre de columna no puede ir
 * como parametro JDBC), asi que la lista blanca es lo unico que separa este endpoint
 * de una inyeccion. Ademas el SQL se arma aqui: los tests del service mockean este
 * repositorio y no verian un error de construccion.
 */
@ExtendWith(MockitoExtension.class)
class LecturaRepositoryTest {

    @Mock JdbcTemplate jdbc;

    private String sqlGenerado(String orden, boolean asc) {
        new LecturaRepository(jdbc).historico(41L, null, null, null, 50, 100, orden, asc);
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).query(sql.capture(), any(RowMapper.class), any(Object[].class));
        return sql.getValue();
    }

    @Test
    @DisplayName("Sin orden (null) usa la fecha y NO lanza NPE")
    void historico_ordenNull_usaFecha() {
        // Map.of() rechaza claves null: un getOrDefault(null, ...) directo reventaria.
        assertThat(sqlGenerado(null, false)).contains("ORDER BY l.FECHA_MEDICION DESC");
    }

    @Test
    @DisplayName("Un orden desconocido cae a la fecha y nunca llega al SQL")
    void historico_ordenDesconocido_caeAFecha() {
        String sql = sqlGenerado("'; DROP TABLE PC_LECTURA_SIGNO_VITAL --", true);
        assertThat(sql).contains("ORDER BY l.FECHA_MEDICION");
        assertThat(sql).doesNotContain("DROP");
    }

    @Test
    @DisplayName("Cada columna de la lista blanca se traduce a su columna real")
    void historico_columnasPermitidas() {
        assertThat(sqlGenerado("valor", true)).contains("ORDER BY l.VALOR_NUM ASC");
    }

    @Test
    @DisplayName("Al ordenar por otra columna se desempata por fecha: paginar es estable")
    void historico_ordenNoFecha_desempataPorFecha() {
        String sql = sqlGenerado("signo", false);
        assertThat(sql).contains("ORDER BY s.CODIGO DESC, l.FECHA_MEDICION DESC, l.ID_LECTURA DESC");
    }

    @Test
    @DisplayName("La ventana se pide a Oracle, no al cliente")
    void historico_paginaEnLaBase() {
        assertThat(sqlGenerado(null, false)).contains("OFFSET ? ROWS FETCH NEXT ? ROWS ONLY");
    }

    @Test
    @DisplayName("ordenValido acepta la lista blanca y null, y rechaza el resto")
    void ordenValido() {
        assertThat(LecturaRepository.ordenValido(null)).isTrue();
        assertThat(LecturaRepository.ordenValido("fecha")).isTrue();
        assertThat(LecturaRepository.ordenValido("valor")).isTrue();
        assertThat(LecturaRepository.ordenValido("signo")).isTrue();
        assertThat(LecturaRepository.ordenValido("origen")).isTrue();
        assertThat(LecturaRepository.ordenValido("VALOR_NUM")).isFalse();
        assertThat(LecturaRepository.ordenValido("; DROP TABLE")).isFalse();
    }

    @Test
    @DisplayName("contar() aplica los mismos filtros que la pagina")
    void contar_mismosFiltros() {
        new LecturaRepository(jdbc).contar(41L, 1L, null, null);
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).queryForObject(sql.capture(), any(Class.class), any(Object[].class));
        assertThat(sql.getValue()).contains("COUNT(*)");
        assertThat(sql.getValue()).contains("WHERE l.ID_PACIENTE = ?");
        assertThat(sql.getValue()).contains("AND l.ID_SIGNO_VITAL = ?");
    }
}
