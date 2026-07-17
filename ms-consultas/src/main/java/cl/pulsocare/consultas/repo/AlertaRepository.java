package cl.pulsocare.consultas.repo;

import cl.pulsocare.consultas.model.Alerta;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Acceso a PC_ALERTA (lectura + reconocimiento). */
@Repository
public class AlertaRepository {

    private final JdbcTemplate jdbc;

    public AlertaRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static final RowMapper<Alerta> MAPPER = (rs, n) -> new Alerta(
            nullableLong(rs, "ID_ALERTA"),
            nullableLong(rs, "ID_LECTURA"),
            nullableLong(rs, "ID_PACIENTE"),
            nullableLong(rs, "ID_SIGNO_VITAL"),
            rs.getString("SIGNO_CODIGO"),
            nullableLong(rs, "ID_NIVEL_ALERTA"),
            rs.getString("NIVEL_CODIGO"),
            nullableLong(rs, "ID_ESTADO_ALERTA"),
            rs.getString("ESTADO_CODIGO"),
            rs.getBigDecimal("VALOR_REGISTRADO"),
            rs.getString("UMBRAL_VIOLADO"),
            fecha(rs, "FECHA_GENERACION"),
            nullableLong(rs, "ID_RECONOCIDA_POR"),
            fecha(rs, "FECHA_RECONOCIM"));

    private static final String SELECT =
            "SELECT a.ID_ALERTA, a.ID_LECTURA, a.ID_PACIENTE, a.ID_SIGNO_VITAL, " +
            "s.CODIGO AS SIGNO_CODIGO, a.ID_NIVEL_ALERTA, nv.CODIGO AS NIVEL_CODIGO, " +
            "a.ID_ESTADO_ALERTA, es.CODIGO AS ESTADO_CODIGO, a.VALOR_REGISTRADO, " +
            "a.UMBRAL_VIOLADO, a.FECHA_GENERACION, a.ID_RECONOCIDA_POR, a.FECHA_RECONOCIM " +
            "FROM PC_ALERTA a " +
            "JOIN PC_SIGNO_VITAL s  ON s.ID_SIGNO_VITAL   = a.ID_SIGNO_VITAL " +
            "JOIN PC_NIVEL_ALERTA nv ON nv.ID_NIVEL_ALERTA = a.ID_NIVEL_ALERTA " +
            "JOIN PC_ESTADO_ALERTA es ON es.ID_ESTADO_ALERTA = a.ID_ESTADO_ALERTA ";

    /**
     * Lista alertas; idPaciente y estadoCodigo (GENERADA/NOTIFICADA/...) son filtros
     * opcionales. limite acota la cantidad de filas (mas recientes primero): sin el,
     * un paciente con meses de monitoreo devuelve decenas de miles de alertas.
     */
    public List<Alerta> listar(Long idPaciente, String estadoCodigo, int limite) {
        StringBuilder sql = new StringBuilder(SELECT).append("WHERE 1 = 1 ");
        List<Object> args = new ArrayList<>();
        if (idPaciente != null) {
            sql.append("AND a.ID_PACIENTE = ? ");
            args.add(idPaciente);
        }
        if (estadoCodigo != null) {
            sql.append("AND es.CODIGO = ? ");
            args.add(estadoCodigo);
        }
        sql.append("ORDER BY a.FECHA_GENERACION DESC FETCH FIRST ? ROWS ONLY");
        args.add(limite);
        return jdbc.query(sql.toString(), MAPPER, args.toArray());
    }

    public Optional<Alerta> buscar(long id) {
        return jdbc.query(SELECT + "WHERE a.ID_ALERTA = ?", MAPPER, id).stream().findFirst();
    }

    /**
     * Marca la alerta como RECONOCIDA y registra quien y cuando. El estado se
     * resuelve por codigo para no depender de IDs fijos del catalogo.
     */
    public int reconocer(long id, long idUsuario) {
        return jdbc.update(
                "UPDATE PC_ALERTA SET " +
                "ID_ESTADO_ALERTA = (SELECT ID_ESTADO_ALERTA FROM PC_ESTADO_ALERTA WHERE CODIGO = 'RECONOCIDA'), " +
                "ID_RECONOCIDA_POR = ?, FECHA_RECONOCIM = SYSTIMESTAMP " +
                "WHERE ID_ALERTA = ?",
                idUsuario, id);
    }

    private static Long nullableLong(ResultSet rs, String col) throws SQLException {
        BigDecimal v = rs.getBigDecimal(col);
        return v == null ? null : v.longValue();
    }

    private static LocalDateTime fecha(ResultSet rs, String col) throws SQLException {
        java.sql.Timestamp t = rs.getTimestamp(col);
        return t == null ? null : t.toLocalDateTime();
    }
}
