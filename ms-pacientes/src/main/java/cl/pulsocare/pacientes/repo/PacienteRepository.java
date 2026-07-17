package cl.pulsocare.pacientes.repo;

import cl.pulsocare.pacientes.model.Paciente;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Acceso a PC_PACIENTE. */
@Repository
public class PacienteRepository {

    private final JdbcTemplate jdbc;

    public PacienteRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static final RowMapper<Paciente> MAPPER = (rs, n) -> new Paciente(
            rs.getLong("ID_PACIENTE"),
            rs.getLong("SUBJECT_ID"),
            rs.getString("NOMBRE"),
            rs.getString("APELLIDO_PATERNO"),
            rs.getString("APELLIDO_MATERNO"),
            rs.getDate("FECHA_NACIMIENTO") == null ? null : rs.getDate("FECHA_NACIMIENTO").toLocalDate(),
            rs.getString("SEXO"),
            nullableLong(rs, "ID_COMUNA"),
            nullableLong(rs, "ID_MODALIDAD"),
            nullableLong(rs, "ID_ESTADO_PACIENTE"));

    /** Oracle devuelve NUMBER como BigDecimal; lo convertimos a Long respetando NULL. */
    private static Long nullableLong(java.sql.ResultSet rs, String col) throws java.sql.SQLException {
        java.math.BigDecimal v = rs.getBigDecimal(col);
        return v == null ? null : v.longValue();
    }

    private static final String COLS =
            "ID_PACIENTE, SUBJECT_ID, NOMBRE, APELLIDO_PATERNO, APELLIDO_MATERNO, " +
            "FECHA_NACIMIENTO, SEXO, ID_COMUNA, ID_MODALIDAD, ID_ESTADO_PACIENTE";

    public List<Paciente> listar() {
        return jdbc.query("SELECT " + COLS + " FROM PC_PACIENTE ORDER BY ID_PACIENTE", MAPPER);
    }

    /** Todos menos los que estan en el estado indicado (para excluir a los dados de alta). */
    public List<Paciente> listarExcluyendoEstado(long idEstado) {
        return jdbc.query(
                "SELECT " + COLS + " FROM PC_PACIENTE " +
                "WHERE ID_ESTADO_PACIENTE IS NULL OR ID_ESTADO_PACIENTE <> ? ORDER BY ID_PACIENTE",
                MAPPER, idEstado);
    }

    /** ID_ESTADO_PACIENTE del catalogo por su codigo (ESTABLE, ALTA, ...); null si no existe. */
    public Long idEstadoPorCodigo(String codigo) {
        return jdbc.query("SELECT ID_ESTADO_PACIENTE FROM PC_ESTADO_PACIENTE WHERE CODIGO = ?",
                (rs, n) -> rs.getLong("ID_ESTADO_PACIENTE"), codigo).stream().findFirst().orElse(null);
    }

    /** Cambia solo el estado clinico del paciente (alta / reactivacion). */
    public int actualizarEstado(long idPaciente, long idEstado) {
        return jdbc.update("UPDATE PC_PACIENTE SET ID_ESTADO_PACIENTE = ? WHERE ID_PACIENTE = ?",
                idEstado, idPaciente);
    }

    public Optional<Paciente> buscar(long id) {
        return jdbc.query("SELECT " + COLS + " FROM PC_PACIENTE WHERE ID_PACIENTE = ?", MAPPER, id)
                .stream().findFirst();
    }

    public Set<Long> subjectsUsados() {
        return new HashSet<>(jdbc.queryForList("SELECT SUBJECT_ID FROM PC_PACIENTE", Long.class));
    }

    public boolean existeSubject(long subjectId) {
        Integer n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM PC_PACIENTE WHERE SUBJECT_ID = ?", Integer.class, subjectId);
        return n != null && n > 0;
    }

    public long insertar(Paciente p) {
        KeyHolder kh = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO PC_PACIENTE (SUBJECT_ID, NOMBRE, APELLIDO_PATERNO, APELLIDO_MATERNO, " +
                    "FECHA_NACIMIENTO, SEXO, ID_COMUNA, ID_MODALIDAD, ID_ESTADO_PACIENTE) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    new String[]{"ID_PACIENTE"});
            ps.setLong(1, p.subjectId());
            ps.setString(2, p.nombre());
            ps.setString(3, p.apellidoPaterno());
            ps.setString(4, p.apellidoMaterno());
            ps.setDate(5, p.fechaNacimiento() == null ? null : Date.valueOf(p.fechaNacimiento()));
            ps.setString(6, p.sexo());
            setNullableLong(ps, 7, p.idComuna());
            setNullableLong(ps, 8, p.idModalidad());
            setNullableLong(ps, 9, p.idEstadoPaciente());
            return ps;
        }, kh);
        Number key = (kh.getKeys() != null && kh.getKeys().size() == 1)
                ? (Number) kh.getKeys().values().iterator().next()
                : kh.getKey();
        return key.longValue();
    }

    // Umbrales clinicos por defecto (mismos rangos que la Lambda y el seed):
    // {id_signo_vital, valor_min, valor_max, valor_min_critico, valor_max_critico}.
    private static final double[][] UMBRALES_DEFECTO = {
            {1, 60, 100, 40, 130},    // Frecuencia cardiaca (bpm)
            {2, 95, 100, 90, 100},    // Saturacion de oxigeno (%)
            {3, 90, 120, 70, 180},    // Presion sistolica (mmHg)
            {4, 60, 80, 40, 110},     // Presion diastolica (mmHg)
            {5, 36, 37.5, 35, 39},    // Temperatura (C)
            {6, 12, 20, 8, 30},       // Frecuencia respiratoria (insp/min)
    };

    /** Crea los 6 umbrales por defecto para un paciente recien dado de alta. */
    public void crearUmbralesPorDefecto(long idPaciente) {
        List<Object[]> filas = new ArrayList<>();
        for (double[] u : UMBRALES_DEFECTO) {
            filas.add(new Object[]{idPaciente, (long) u[0], u[1], u[2], u[3], u[4]});
        }
        jdbc.batchUpdate(
                "INSERT INTO PC_UMBRAL (ID_PACIENTE, ID_SIGNO_VITAL, VALOR_MIN, VALOR_MAX, " +
                "VALOR_MIN_CRITICO, VALOR_MAX_CRITICO) VALUES (?, ?, ?, ?, ?, ?)",
                filas);
    }

    public int actualizar(long id, Paciente p) {
        return jdbc.update(
                "UPDATE PC_PACIENTE SET NOMBRE=?, APELLIDO_PATERNO=?, APELLIDO_MATERNO=?, " +
                "FECHA_NACIMIENTO=?, SEXO=?, ID_COMUNA=?, ID_MODALIDAD=?, ID_ESTADO_PACIENTE=? " +
                "WHERE ID_PACIENTE=?",
                p.nombre(), p.apellidoPaterno(), p.apellidoMaterno(),
                p.fechaNacimiento() == null ? null : Date.valueOf(p.fechaNacimiento()),
                p.sexo(), p.idComuna(), p.idModalidad(), p.idEstadoPaciente(), id);
    }

    public int eliminar(long id) {
        return jdbc.update("DELETE FROM PC_PACIENTE WHERE ID_PACIENTE = ?", id);
    }

    private static void setNullableLong(PreparedStatement ps, int idx, Long v) throws java.sql.SQLException {
        if (v == null) ps.setNull(idx, java.sql.Types.NUMERIC); else ps.setLong(idx, v);
    }
}
