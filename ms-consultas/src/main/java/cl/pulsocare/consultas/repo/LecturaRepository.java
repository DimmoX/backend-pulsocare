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
     * Columnas por las que se puede ordenar. Es una lista blanca a proposito: el
     * nombre de columna no puede ir como parametro de JDBC, asi que se concatena al
     * SQL; aceptar texto libre del cliente seria inyeccion.
     */
    private static final java.util.Map<String, String> COLUMNAS_ORDEN = java.util.Map.of(
            "fecha", "l.FECHA_MEDICION",
            "valor", "l.VALOR_NUM",
            "signo", "s.CODIGO",
            "origen", "l.ORIGEN");

    public static boolean ordenValido(String orden) {
        return orden == null || COLUMNAS_ORDEN.containsKey(orden);
    }

    /** Arma el WHERE comun a historico() y contar(), para que no puedan divergir. */
    private static void filtros(StringBuilder sql, List<Object> args, long idPaciente,
                                Long idSignoVital, LocalDateTime desde, LocalDateTime hasta) {
        sql.append("WHERE l.ID_PACIENTE = ? ");
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
    }

    /**
     * Una pagina del historico. idSignoVital, desde y hasta son filtros opcionales;
     * orden/ascendente definen el criterio y offset/limite la ventana.
     *
     * La paginacion la hace Oracle (OFFSET ... FETCH NEXT) y no el cliente: un dia de
     * monitoreo son ~84.000 lecturas por paciente, asi que traerlas todas para que el
     * navegador recorte 50 dejaria la conexion retenida durante minutos.
     */
    public List<Lectura> historico(long idPaciente, Long idSignoVital, LocalDateTime desde,
                                   LocalDateTime hasta, int limite, int offset,
                                   String orden, boolean ascendente) {
        StringBuilder sql = new StringBuilder(SELECT);
        List<Object> args = new ArrayList<>();
        filtros(sql, args, idPaciente, idSignoVital, desde, hasta);

        // Map.of() es inmutable y rechaza claves null: getOrDefault(null, ...) lanza
        // NPE en vez de devolver el defecto, asi que el null se resuelve antes.
        String columna = orden == null
                ? "l.FECHA_MEDICION"
                : COLUMNAS_ORDEN.getOrDefault(orden, "l.FECHA_MEDICION");
        sql.append("ORDER BY ").append(columna).append(ascendente ? " ASC" : " DESC");
        // Desempate estable: sin el, dos filas con el mismo valor pueden cambiar de
        // pagina entre consultas y verse repetidas o perdidas al navegar.
        if (!columna.equals("l.FECHA_MEDICION")) {
            sql.append(", l.FECHA_MEDICION DESC");
        }
        sql.append(", l.ID_LECTURA DESC OFFSET ? ROWS FETCH NEXT ? ROWS ONLY");
        args.add(offset);
        args.add(limite);
        return jdbc.query(sql.toString(), MAPPER, args.toArray());
    }

    /** Total de filas que cumplen los filtros, para saber cuantas paginas hay. */
    public long contar(long idPaciente, Long idSignoVital, LocalDateTime desde, LocalDateTime hasta) {
        StringBuilder sql = new StringBuilder(
                "SELECT COUNT(*) FROM PC_LECTURA_SIGNO_VITAL l ");
        List<Object> args = new ArrayList<>();
        filtros(sql, args, idPaciente, idSignoVital, desde, hasta);
        Long n = jdbc.queryForObject(sql.toString(), Long.class, args.toArray());
        return n == null ? 0 : n;
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
