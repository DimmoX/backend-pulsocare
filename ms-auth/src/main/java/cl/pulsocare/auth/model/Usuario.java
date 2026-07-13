package cl.pulsocare.auth.model;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Vista publica de PC_USUARIO (nunca expone HASH_CONTRASENA).
 *
 * passwordTemporal solo se rellena en la respuesta cuando el admin crea un
 * usuario y este se crea en B2C; en el resto de casos es null y se omite del
 * JSON (para que el admin pueda entregarsela al medico/familiar).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
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
        String estado,
        String passwordTemporal
) {
    /** Copia el usuario agregando la contrasena temporal (para la respuesta al admin). */
    public Usuario conPassword(String passwordTemporal) {
        return new Usuario(idUsuario, idRol, rol, nombre, apellidoPaterno, apellidoMaterno,
                correo, telefono, entraOid, idParentesco, estado, passwordTemporal);
    }
}
