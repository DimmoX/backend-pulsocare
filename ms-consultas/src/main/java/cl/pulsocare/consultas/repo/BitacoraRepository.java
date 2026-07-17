package cl.pulsocare.consultas.repo;

import cl.pulsocare.consultas.model.EventoBitacora;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;

/** Lee y escribe eventos de auditoria en PC_BITACORA_ACCESO (quien accede a que dato). */
@Repository
public class BitacoraRepository {

    private final JdbcTemplate jdbc;

    public BitacoraRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Registra un evento. idPaciente puede ser null (ej. un LOGIN no es sobre un paciente). */
    public void registrar(long idUsuario, Long idPaciente, String accion, String detalle, String ip) {
        jdbc.update(
                "INSERT INTO PC_BITACORA_ACCESO (ID_USUARIO, ID_PACIENTE, ACCION, DETALLE, DIRECCION_IP) " +
                "VALUES (?, ?, ?, ?, ?)",
                idUsuario, idPaciente, accion, detalle, ip);
    }

    private static final RowMapper<EventoBitacora> MAPPER = (rs, n) -> new EventoBitacora(
            nullableLong(rs, "ID_BITACORA"),
            nullableLong(rs, "ID_USUARIO"),
            rs.getString("USUARIO"),
            rs.getString("ROL"),
            nullableLong(rs, "ID_PACIENTE"),
            rs.getString("PACIENTE"),
            rs.getString("ACCION"),
            rs.getString("DETALLE"),
            rs.getString("DIRECCION_IP"),
            fecha(rs, "FECHA_EVENTO"));

    // El LEFT JOIN a paciente es a proposito: hay eventos sin paciente (un LOGIN), y no
    // deben desaparecer del listado por no tener con quien unir.
    private static final String SELECT =
            "SELECT b.ID_BITACORA, b.ID_USUARIO, " +
            "u.NOMBRE || ' ' || u.APELLIDO_PATERNO AS USUARIO, r.NOMBRE AS ROL, " +
            "b.ID_PACIENTE, p.NOMBRE || ' ' || p.APELLIDO_PATERNO AS PACIENTE, " +
            "b.ACCION, b.DETALLE, b.DIRECCION_IP, b.FECHA_EVENTO " +
            "FROM PC_BITACORA_ACCESO b " +
            "JOIN PC_USUARIO u ON u.ID_USUARIO = b.ID_USUARIO " +
            "JOIN PC_ROL r ON r.ID_ROL = u.ID_ROL " +
            "LEFT JOIN PC_PACIENTE p ON p.ID_PACIENTE = b.ID_PACIENTE ";

    /** Ultimos eventos, de mas reciente a mas antiguo, acotados por limite. */
    public List<EventoBitacora> listar(int limite) {
        return jdbc.query(
                SELECT + "ORDER BY b.FECHA_EVENTO DESC FETCH FIRST ? ROWS ONLY",
                MAPPER, limite);
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
