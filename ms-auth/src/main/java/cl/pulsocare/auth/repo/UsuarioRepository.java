package cl.pulsocare.auth.repo;

import cl.pulsocare.auth.model.Usuario;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.util.List;
import java.util.Optional;

/** Acceso a PC_USUARIO. */
@Repository
public class UsuarioRepository {

    private final JdbcTemplate jdbc;

    public UsuarioRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static final String SELECT =
            "SELECT u.ID_USUARIO, u.ID_ROL, r.NOMBRE AS ROL, u.NOMBRE, u.APELLIDO_PATERNO, " +
            "u.APELLIDO_MATERNO, u.CORREO, u.TELEFONO, u.ENTRA_OID, u.ID_PARENTESCO, u.ESTADO " +
            "FROM PC_USUARIO u JOIN PC_ROL r ON r.ID_ROL = u.ID_ROL ";

    private static final RowMapper<Usuario> MAPPER = (rs, n) -> new Usuario(
            rs.getLong("ID_USUARIO"),
            rs.getLong("ID_ROL"),
            rs.getString("ROL"),
            rs.getString("NOMBRE"),
            rs.getString("APELLIDO_PATERNO"),
            rs.getString("APELLIDO_MATERNO"),
            rs.getString("CORREO"),
            rs.getString("TELEFONO"),
            rs.getString("ENTRA_OID"),
            nullableLong(rs, "ID_PARENTESCO"),
            rs.getString("ESTADO"),
            null);   // passwordTemporal: solo se rellena al crear via B2C

    public List<Usuario> listar() {
        return jdbc.query(SELECT + "ORDER BY u.ID_USUARIO", MAPPER);
    }

    public Optional<Usuario> buscarPorId(long id) {
        return jdbc.query(SELECT + "WHERE u.ID_USUARIO = ?", MAPPER, id).stream().findFirst();
    }

    public Optional<Usuario> buscarPorCorreo(String correo) {
        return jdbc.query(SELECT + "WHERE u.CORREO = ?", MAPPER, correo).stream().findFirst();
    }

    public Optional<String> hashPorCorreo(String correo) {
        return jdbc.query("SELECT HASH_CONTRASENA FROM PC_USUARIO WHERE CORREO = ?",
                (rs, n) -> rs.getString(1), correo).stream().findFirst();
    }

    public boolean existeCorreo(String correo) {
        Integer c = jdbc.queryForObject(
                "SELECT COUNT(*) FROM PC_USUARIO WHERE CORREO = ?", Integer.class, correo);
        return c != null && c > 0;
    }

    public long insertar(long idRol, String nombre, String apellidoPaterno, String correo,
                         String telefono, String entraOid, Long idParentesco, String hash) {
        KeyHolder kh = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO PC_USUARIO (ID_ROL, ID_PARENTESCO, NOMBRE, APELLIDO_PATERNO, " +
                    "CORREO, TELEFONO, ENTRA_OID, HASH_CONTRASENA, ESTADO) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'ACTIVO')",
                    new String[]{"ID_USUARIO"});
            ps.setLong(1, idRol);
            if (idParentesco == null) ps.setNull(2, java.sql.Types.NUMERIC); else ps.setLong(2, idParentesco);
            ps.setString(3, nombre);
            ps.setString(4, apellidoPaterno);
            ps.setString(5, correo);
            ps.setString(6, telefono);
            ps.setString(7, entraOid);
            ps.setString(8, hash);
            return ps;
        }, kh);
        Number key = (kh.getKeys() != null && kh.getKeys().size() == 1)
                ? (Number) kh.getKeys().values().iterator().next()
                : kh.getKey();
        return key.longValue();
    }

    public void actualizarSincronizacion(String correo, String nombre, String apellidoPaterno,
                                         String telefono, String hash) {
        jdbc.update(
                "UPDATE PC_USUARIO SET NOMBRE=?, APELLIDO_PATERNO=?, " +
                "TELEFONO=COALESCE(?, TELEFONO), HASH_CONTRASENA=? WHERE CORREO=?",
                nombre, apellidoPaterno, telefono, hash, correo);
    }

    /**
     * Da de baja o rehabilita a un usuario. Es una baja logica, no un DELETE: siete
     * claves foraneas apuntan a PC_USUARIO sin ON DELETE CASCADE (asignaciones,
     * alertas reconocidas, bitacora...), asi que borrarlo fallaria con ORA-02292 y,
     * si pudiera, destruiria la trazabilidad clinica de quien atendio que alerta.
     */
    public int actualizarEstado(long idUsuario, String estado) {
        return jdbc.update("UPDATE PC_USUARIO SET ESTADO = ? WHERE ID_USUARIO = ?", estado, idUsuario);
    }

    private static Long nullableLong(java.sql.ResultSet rs, String col) throws java.sql.SQLException {
        java.math.BigDecimal v = rs.getBigDecimal(col);
        return v == null ? null : v.longValue();
    }
}
