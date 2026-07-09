package cl.pulsocare.consultas.repo;

import cl.pulsocare.consultas.model.Lectura;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** Acceso de solo lectura a PC_LECTURA_SIGNO_VITAL (con datos del signo). */
@Repository
public class LecturaRepository {

    private final JdbcTemplate jdbc;

    public LecturaRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static final RowMapper<Lectura> MAPPER = (rs, n) -> new Lectura(
            nullableLong(rs, "ID_LECTURA"),
            nullableLong(rs, "ID_PACIENTE"),
            nullableLong(rs, "ID_SIGNO_VITAL"),
            rs.getString("SIGNO_CODIGO"),
            rs.getString("SIGNO_NOMBRE"),
            rs.getBigDecimal("VALOR_NUM"),
            rs.getString("UNIDAD"),
            fecha(rs, "FECHA_MEDICION"),
            fecha(rs, "FECHA_REGISTRO"),
            rs.getString("ORIGEN"));

    private static final String SELECT =
            "SELECT l.ID_LECTURA, l.ID_PACIENTE, l.ID_SIGNO_VITAL, " +
            "s.CODIGO AS SIGNO_CODIGO, s.NOMBRE AS SIGNO_NOMBRE, " +
            "l.VALOR_NUM, l.UNIDAD, l.FECHA_MEDICION, l.FECHA_REGISTRO, l.ORIGEN " +
            "FROM PC_LECTURA_SIGNO_VITAL l " +
            "JOIN PC_SIGNO_VITAL s ON s.ID_SIGNO_VITAL = l.ID_SIGNO_VITAL ";

    /**
     * Historico de un paciente. idSignoVital, desde y hasta son filtros
     * opcionales; limite acota la cantidad de filas (mas recientes primero).
     */
    public List<Lectura> historico(long idPaciente, Long idSignoVital,
                                   LocalDateTime desde, LocalDateTime hasta, int limite) {
        StringBuilder sql = new StringBuilder(SELECT).append("WHERE l.ID_PACIENTE = ? ");
        List<Object> args = new ArrayList<>();
        args.add(idPaciente);
        if (idSignoVital != null) {
            sql.append("AND l.ID_SIGNO_VITAL = ? ");
            args.add(idSignoVital);
        }
        if (desde != null) {
            sql.append("AND l.FECHA_MEDICION >= ? ");
            args.add(java.sql.Timestamp.valueOf(desde));
        }
        if (hasta != null) {
            sql.append("AND l.FECHA_MEDICION <= ? ");
            args.add(java.sql.Timestamp.valueOf(hasta));
        }
        sql.append("ORDER BY l.FECHA_MEDICION DESC FETCH FIRST ? ROWS ONLY");
        args.add(limite);
        return jdbc.query(sql.toString(), MAPPER, args.toArray());
    }

    /** Ultima lectura registrada de cada signo vital del paciente (para los tiles en vivo). */
    public List<Lectura> ultimasPorSigno(long idPaciente) {
        String sql = SELECT +
                "WHERE l.ID_PACIENTE = ? AND l.FECHA_MEDICION = (" +
                "  SELECT MAX(l2.FECHA_MEDICION) FROM PC_LECTURA_SIGNO_VITAL l2 " +
                "  WHERE l2.ID_PACIENTE = l.ID_PACIENTE AND l2.ID_SIGNO_VITAL = l.ID_SIGNO_VITAL) " +
                "ORDER BY l.ID_SIGNO_VITAL";
        return jdbc.query(sql, MAPPER, idPaciente);
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
