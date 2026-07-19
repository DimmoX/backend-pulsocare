package cl.pulsocare.config.repo;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Escritura en PC_BITACORA_ACCESO, el libro de auditoria de la plataforma.
 *
 * ms-consultas tiene su propio acceso a esta tabla para registrar lecturas de datos
 * clinicos. Aqui se escribe directo en vez de llamar a ese servicio por HTTP a
 * proposito: un cambio de umbral altera cuando suena una alarma, asi que su registro
 * no puede depender de que otro microservicio este arriba en ese momento.
 */
@Repository
public class BitacoraRepository {

    private final JdbcTemplate jdbc;

    public BitacoraRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void registrar(Long idUsuario, Long idPaciente, String accion, String detalle) {
        jdbc.update(
                """
                INSERT INTO PC_BITACORA_ACCESO (ID_USUARIO, ID_PACIENTE, ACCION, DETALLE)
                VALUES (?, ?, ?, ?)
                """,
                idUsuario, idPaciente, accion, detalle);
    }
}
