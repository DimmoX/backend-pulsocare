package cl.pulsocare.consultas.service;

import cl.pulsocare.consultas.model.EventoBitacora;
import cl.pulsocare.consultas.repo.BitacoraRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class BitacoraService {

    private static final Logger log = LoggerFactory.getLogger(BitacoraService.class);

    // Limites de PC_BITACORA_ACCESO: ACCION VARCHAR2(50), DETALLE VARCHAR2(400).
    private static final int MAX_ACCION = 50;
    private static final int MAX_DETALLE = 400;

    private final BitacoraRepository repo;

    public BitacoraService(BitacoraRepository repo) {
        this.repo = repo;
    }

    /**
     * Registra un acceso en la bitacora. La auditoria es "best-effort": si el INSERT
     * falla (por ejemplo un id_usuario que no existe), se deja registro en el log pero
     * NO se propaga el error, porque no debe romper la accion que el usuario esta
     * haciendo (ver el historico de un paciente). La constancia importa, pero no a costa
     * de tumbar la funcionalidad.
     */
    public void registrarAcceso(long idUsuario, Long idPaciente, String accion, String detalle, String ip) {
        try {
            repo.registrar(idUsuario, idPaciente, recortar(accion, MAX_ACCION), recortar(detalle, MAX_DETALLE), ip);
        } catch (Exception e) {
            log.warn("No se pudo registrar en bitacora (usuario={}, paciente={}, accion={}): {}",
                    idUsuario, idPaciente, accion, e.getMessage());
        }
    }

    private static String recortar(String texto, int max) {
        if (texto == null) return null;
        return texto.length() <= max ? texto : texto.substring(0, max);
    }

    /** Ultimos eventos para el panel del admin; el limite se acota para no traer todo. */
    public List<EventoBitacora> listar(Integer limite) {
        int efectivo = (limite == null || limite <= 0) ? 200 : Math.min(limite, 1000);
        return repo.listar(efectivo);
    }
}
