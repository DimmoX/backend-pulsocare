package cl.pulsocare.config.repo;

import cl.pulsocare.config.model.Umbral;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

/** Acceso a PC_UMBRAL. */
@Repository
public class UmbralRepository {

    private final JdbcTemplate jdbc;

    public UmbralRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static final RowMapper<Umbral> MAPPER = (rs, n) -> new Umbral(
            nullableLong(rs, "ID_UMBRAL"),
            nullableLong(rs, "ID_PACIENTE"),
            nullableLong(rs, "ID_SIGNO_VITAL"),
            rs.getBigDecimal("VALOR_MIN"),
            rs.getBigDecimal("VALOR_MAX"),
            rs.getBigDecimal("VALOR_MIN_CRITICO"),
            rs.getBigDecimal("VALOR_MAX_CRITICO"),
            rs.getInt("VIGENTE"),
            rs.getTimestamp("VIGENTE_DESDE") == null ? null : rs.getTimestamp("VIGENTE_DESDE").toLocalDateTime(),
            nullableLong(rs, "ID_DEFINIDO_POR"));

    /** Oracle devuelve NUMBER como BigDecimal; lo convertimos a Long respetando NULL. */
    private static Long nullableLong(ResultSet rs, String col) throws SQLException {
        BigDecimal v = rs.getBigDecimal(col);
        return v == null ? null : v.longValue();
    }

    private static final String COLS =
            "ID_UMBRAL, ID_PACIENTE, ID_SIGNO_VITAL, VALOR_MIN, VALOR_MAX, " +
            "VALOR_MIN_CRITICO, VALOR_MAX_CRITICO, VIGENTE, VIGENTE_DESDE, ID_DEFINIDO_POR";

    public List<Umbral> listar() {
        return jdbc.query("SELECT " + COLS + " FROM PC_UMBRAL ORDER BY ID_UMBRAL", MAPPER);
    }

    public List<Umbral> listarPorPaciente(long idPaciente) {
        return jdbc.query("SELECT " + COLS + " FROM PC_UMBRAL WHERE ID_PACIENTE = ? ORDER BY ID_SIGNO_VITAL",
                MAPPER, idPaciente);
    }

    public Optional<Umbral> buscar(long id) {
        return jdbc.query("SELECT " + COLS + " FROM PC_UMBRAL WHERE ID_UMBRAL = ?", MAPPER, id)
                .stream().findFirst();
    }

    public long insertar(Umbral u) {
        KeyHolder kh = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO PC_UMBRAL (ID_PACIENTE, ID_SIGNO_VITAL, VALOR_MIN, VALOR_MAX, " +
                    "VALOR_MIN_CRITICO, VALOR_MAX_CRITICO, ID_DEFINIDO_POR) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?)",
                    new String[]{"ID_UMBRAL"});
            ps.setLong(1, u.idPaciente());
            ps.setLong(2, u.idSignoVital());
            setNullableDecimal(ps, 3, u.valorMin());
            setNullableDecimal(ps, 4, u.valorMax());
            setNullableDecimal(ps, 5, u.valorMinCritico());
            setNullableDecimal(ps, 6, u.valorMaxCritico());
            setNullableLong(ps, 7, u.idDefinidoPor());
            return ps;
        }, kh);
        Number key = (kh.getKeys() != null && kh.getKeys().size() == 1)
                ? (Number) kh.getKeys().values().iterator().next()
                : kh.getKey();
        return key.longValue();
    }

    public int actualizar(long id, Umbral u) {
        return jdbc.update(
                "UPDATE PC_UMBRAL SET VALOR_MIN=?, VALOR_MAX=?, VALOR_MIN_CRITICO=?, " +
                "VALOR_MAX_CRITICO=?, ID_DEFINIDO_POR=? WHERE ID_UMBRAL=?",
                u.valorMin(), u.valorMax(), u.valorMinCritico(),
                u.valorMaxCritico(), u.idDefinidoPor(), id);
    }

    /** Baja logica: marca el umbral como no vigente (VIGENTE = 0). */
    public int desactivar(long id) {
        return jdbc.update("UPDATE PC_UMBRAL SET VIGENTE = 0 WHERE ID_UMBRAL = ?", id);
    }

    /**
     * Da de baja los umbrales vigentes de ese paciente y signo, y devuelve cuantos eran.
     *
     * Un paciente no puede tener dos limites vigentes para el mismo signo: cual gana
     * dependeria del orden en que la base devuelva las filas. Las anteriores quedan con
     * VIGENTE = 0 en vez de borrarse, para conservar el historial de ajustes.
     */
    public int desactivarVigentesDe(long idPaciente, long idSignoVital) {
        return jdbc.update(
                "UPDATE PC_UMBRAL SET VIGENTE = 0 WHERE ID_PACIENTE = ? AND ID_SIGNO_VITAL = ? AND VIGENTE = 1",
                idPaciente, idSignoVital);
    }

    private static void setNullableLong(PreparedStatement ps, int idx, Long v) throws SQLException {
        if (v == null) ps.setNull(idx, java.sql.Types.NUMERIC); else ps.setLong(idx, v);
    }

    private static void setNullableDecimal(PreparedStatement ps, int idx, BigDecimal v) throws SQLException {
        if (v == null) ps.setNull(idx, java.sql.Types.NUMERIC); else ps.setBigDecimal(idx, v);
    }
}
