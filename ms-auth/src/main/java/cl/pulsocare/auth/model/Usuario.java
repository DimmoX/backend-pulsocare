package cl.pulsocare.auth.model;

/** Vista publica de PC_USUARIO (nunca expone HASH_CONTRASENA). */
public record Usuario(
        Long idUsuario,
        Long idRol,
        String rol,
        String nombre,
        String apellidoPaterno,
        String apellidoMaterno,
        String correo,
        String telefono,
        String entraOid,
        Long idParentesco,
        String estado
) {}
