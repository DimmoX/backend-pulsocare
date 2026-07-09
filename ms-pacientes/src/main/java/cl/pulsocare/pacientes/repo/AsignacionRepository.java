package cl.pulsocare.pacientes.repo;

import cl.pulsocare.pacientes.model.Asignacion;
import cl.pulsocare.pacientes.model.Cuidador;
import cl.pulsocare.pacientes.model.Paciente;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

/** Acceso a PC_ASIGNACION_CUIDADO (vínculo usuario-paciente). */
@Repository
public class AsignacionRepository {

    private final JdbcTemplate jdbc;

    public AsignacionRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ---- mappers ----------------------------------------------------------

    private static final RowMapper<Asignacion> ASIGNACION = (rs, n) -> new Asignacion(
            nullableLong(rs, "ID_ASIGNACION"),
            nullableLong(rs, "ID_USUARIO"),
            nullableLong(rs, "ID_PACIENTE"),
            rs.getDate("FECHA_INICIO") == null ? null : rs.getDate("FECHA_INICIO").toLocalDate(),
            rs.getDate("FECHA_FIN") == null ? null : rs.getDate("FECHA_FIN").toLocalDate(),
            rs.getInt("ACTIVO"));

    private static final RowMapper<Cuidador> CUIDADOR = (rs, n) -> new Cuidador(
            nullableLong(rs, "ID_ASIGNACION"),
            nullableLong(rs, "ID_USUARIO"),
            rs.getString("NOMBRE"),
            rs.getString("APELLIDO_PATERNO"),
            rs.getString("CORREO"),
            rs.getString("ROL"),
            rs.getDate("FECHA_INICIO") == null ? null : rs.getDate("FECHA_INICIO").toLocalDate());

    private static final RowMapper<Paciente> PACIENTE = (rs, n) -> new Paciente(
            nullableLong(rs, "ID_PACIENTE"),
            nullableLong(rs, "SUBJECT_ID"),
            rs.getString("NOMBRE"),
            rs.getString("APELLIDO_PATERNO"),
            rs.getString("APELLIDO_MATERNO"),
            rs.getDate("FECHA_NACIMIENTO") == null ? null : rs.getDate("FECHA_NACIMIENTO").toLocalDate(),
            rs.getString("SEXO"),
            nullableLong(rs, "ID_COMUNA"),
            nullableLong(rs, "ID_MODALIDAD"),
            nullableLong(rs, "ID_ESTADO_PACIENTE"));

    // ---- consultas --------------------------------------------------------

    /** Asignación activa (si existe) del par usuario-paciente. */
    public Optional<Asignacion> buscarActiva(long idPaciente, long idUsuario) {
        return jdbc.query(
                "SELECT ID_ASIGNACION, ID_USUARIO, ID_PACIENTE, FECHA_INICIO, FECHA_FIN, ACTIVO " +
                "FROM PC_ASIGNACION_CUIDADO WHERE ID_PACIENTE = ? AND ID_USUARIO = ? AND ACTIVO = 1",
                ASIGNACION, idPaciente, idUsuario).stream().findFirst();
    }

    /** Cuidadores activos de un paciente, con nombre y rol (panel admin). */
    public List<Cuidador> cuidadoresDePaciente(long idPaciente) {
        return jdbc.query(
                "SELECT a.ID_ASIGNACION, a.ID_USUARIO, u.NOMBRE, u.APELLIDO_PATERNO, u.CORREO, " +
                "r.NOMBRE AS ROL, a.FECHA_INICIO " +
                "FROM PC_ASIGNACION_CUIDADO a " +
                "JOIN PC_USUARIO u ON u.ID_USUARIO = a.ID_USUARIO " +
                "JOIN PC_ROL r     ON r.ID_ROL     = u.ID_ROL " +
                "WHERE a.ID_PACIENTE = ? AND a.ACTIVO = 1 " +
                "ORDER BY a.FECHA_INICIO DESC",
                CUIDADOR, idPaciente);
    }

    /** Pacientes que un usuario (medico o familiar) tiene a cargo. */
    public List<Paciente> pacientesDeUsuario(long idUsuario) {
        return jdbc.query(
                "SELECT p.ID_PACIENTE, p.SUBJECT_ID, p.NOMBRE, p.APELLIDO_PATERNO, p.APELLIDO_MATERNO, " +
                "p.FECHA_NACIMIENTO, p.SEXO, p.ID_COMUNA, p.ID_MODALIDAD, p.ID_ESTADO_PACIENTE " +
                "FROM PC_ASIGNACION_CUIDADO a " +
                "JOIN PC_PACIENTE p ON p.ID_PACIENTE = a.ID_PACIENTE " +
                "WHERE a.ID_USUARIO = ? AND a.ACTIVO = 1 " +
                "ORDER BY p.ID_PACIENTE",
                PACIENTE, idUsuario);
    }

    // ---- comandos ---------------------------------------------------------

    /**
     * Vincula usuario y paciente. Si ya existió una asignación (aunque inactiva)
     * la reactiva, evitando chocar con la UNIQUE al reasignar el mismo día;
     * si no existe ninguna, la inserta.
     */
    public void asignar(long idPaciente, long idUsuario) {
        int reactivadas = jdbc.update(
                "UPDATE PC_ASIGNACION_CUIDADO SET ACTIVO = 1, FECHA_FIN = NULL " +
                "WHERE ID_PACIENTE = ? AND ID_USUARIO = ?",
                idPaciente, idUsuario);
        if (reactivadas == 0) {
            jdbc.update(
                    "INSERT INTO PC_ASIGNACION_CUIDADO (ID_USUARIO, ID_PACIENTE) VALUES (?, ?)",
                    idUsuario, idPaciente);
        }
    }

    /** Baja lógica del vínculo activo (ACTIVO = 0, FECHA_FIN = hoy). */
    public int desactivar(long idPaciente, long idUsuario) {
        return jdbc.update(
                "UPDATE PC_ASIGNACION_CUIDADO SET ACTIVO = 0, FECHA_FIN = SYSDATE " +
                "WHERE ID_PACIENTE = ? AND ID_USUARIO = ? AND ACTIVO = 1",
                idPaciente, idUsuario);
    }

    private static Long nullableLong(ResultSet rs, String col) throws SQLException {
        BigDecimal v = rs.getBigDecimal(col);
        return v == null ? null : v.longValue();
    }
}
