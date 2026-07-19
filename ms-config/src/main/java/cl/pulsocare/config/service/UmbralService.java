package cl.pulsocare.config.service;

import cl.pulsocare.config.dto.ActualizarUmbralRequest;
import cl.pulsocare.config.dto.CrearUmbralRequest;
import cl.pulsocare.config.model.Umbral;
import cl.pulsocare.config.repo.BitacoraRepository;
import cl.pulsocare.config.repo.UmbralRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;

@Service
public class UmbralService {

    private static final Logger log = LoggerFactory.getLogger(UmbralService.class);

    /** DETALLE de PC_BITACORA_ACCESO es VARCHAR2(400). */
    private static final int MAX_DETALLE = 400;

    private final UmbralRepository repo;
    private final BitacoraRepository bitacora;

    public UmbralService(UmbralRepository repo, BitacoraRepository bitacora) {
        this.repo = repo;
        this.bitacora = bitacora;
    }

    public List<Umbral> listar(Long idPaciente) {
        return idPaciente == null ? repo.listar() : repo.listarPorPaciente(idPaciente);
    }

    public Umbral obtener(long id) {
        return repo.buscar(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Umbral " + id + " no encontrado"));
    }

    /**
     * El registro en bitacora va en la MISMA transaccion que el cambio, no como un
     * aviso best-effort: ajustar un umbral cambia cuando suena una alarma, y un cambio
     * asi no puede quedar sin rastro de quien lo hizo. Si no se puede auditar, no se
     * aplica.
     */
    @Transactional
    public Umbral crear(CrearUmbralRequest req) {
        validarRangos(req.valorMin(), req.valorMax(), req.valorMinCritico(), req.valorMaxCritico());
        // Reemplaza el limite anterior en vez de acumular otro vigente para el mismo
        // signo, que dejaria a la Lambda eligiendo entre dos rangos distintos.
        int reemplazados = repo.desactivarVigentesDe(req.idPaciente(), req.idSignoVital());
        Umbral nuevo = new Umbral(null, req.idPaciente(), req.idSignoVital(),
                req.valorMin(), req.valorMax(), req.valorMinCritico(), req.valorMaxCritico(),
                null, null, req.idDefinidoPor());
        long id = repo.insertar(nuevo);
        Umbral creado = repo.buscar(id).orElseThrow();

        bitacora.registrar(req.idDefinidoPor(), req.idPaciente(),
                reemplazados > 0 ? "EDITAR_UMBRAL" : "CREAR_UMBRAL",
                recortar("Signo %d: normal %s-%s, critico %s-%s".formatted(
                        req.idSignoVital(),
                        texto(req.valorMin()), texto(req.valorMax()),
                        texto(req.valorMinCritico()), texto(req.valorMaxCritico()))));

        log.info("Umbral {} creado para paciente {} signo {} por usuario {}",
                id, req.idPaciente(), req.idSignoVital(), req.idDefinidoPor());
        return creado;
    }

    @Transactional
    public Umbral actualizar(long id, ActualizarUmbralRequest req) {
        Umbral antes = obtener(id);  // 404 si no existe
        validarRangos(req.valorMin(), req.valorMax(), req.valorMinCritico(), req.valorMaxCritico());
        Umbral cambios = new Umbral(id, null, null,
                req.valorMin(), req.valorMax(), req.valorMinCritico(), req.valorMaxCritico(),
                null, null, req.idDefinidoPor());
        repo.actualizar(id, cambios);
        Umbral despues = repo.buscar(id).orElseThrow();

        // Se guarda el antes y el despues: para auditar un cambio de alarma no basta
        // con saber como quedo, hay que poder reconstruir que se modifico.
        bitacora.registrar(req.idDefinidoPor(), antes.idPaciente(), "EDITAR_UMBRAL",
                recortar("Signo %d: normal %s-%s -> %s-%s, critico %s-%s -> %s-%s".formatted(
                        antes.idSignoVital(),
                        texto(antes.valorMin()), texto(antes.valorMax()),
                        texto(despues.valorMin()), texto(despues.valorMax()),
                        texto(antes.valorMinCritico()), texto(antes.valorMaxCritico()),
                        texto(despues.valorMinCritico()), texto(despues.valorMaxCritico()))));

        log.info("Umbral {} actualizado por usuario {}", id, req.idDefinidoPor());
        return despues;
    }

    /** Baja logica: deja el umbral como no vigente y el signo vuelve a su rango por defecto. */
    @Transactional
    public void desactivar(long id, Long idUsuario) {
        Umbral antes = obtener(id);
        if (repo.desactivar(id) == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Umbral " + id + " no encontrado");
        }
        bitacora.registrar(idUsuario, antes.idPaciente(), "ELIMINAR_UMBRAL",
                recortar("Signo %d vuelve al rango por defecto (era normal %s-%s, critico %s-%s)"
                        .formatted(antes.idSignoVital(),
                                texto(antes.valorMin()), texto(antes.valorMax()),
                                texto(antes.valorMinCritico()), texto(antes.valorMaxCritico()))));
        log.info("Umbral {} desactivado por usuario {}", id, idUsuario);
    }

    /**
     * Reglas de coherencia de los limites.
     *
     * Ademas de que cada minimo no supere a su maximo, el rango critico tiene que
     * CONTENER al normal. Si no, los estados se vuelven contradictorios: con un normal
     * de 90-100 y un critico de 95-100, un valor de 92 estaria a la vez dentro de lo
     * que el medico declaro aceptable y fuera del rango critico, y la lectura se
     * clasificaria como critica.
     */
    private void validarRangos(BigDecimal min, BigDecimal max,
                               BigDecimal minCritico, BigDecimal maxCritico) {
        if (min != null && max != null && min.compareTo(max) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "El minimo normal no puede ser mayor que el maximo normal");
        }
        if (minCritico != null && maxCritico != null && minCritico.compareTo(maxCritico) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "El minimo critico no puede ser mayor que el maximo critico");
        }
        if (minCritico != null && min != null && minCritico.compareTo(min) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "El minimo critico debe ser menor o igual que el minimo normal");
        }
        if (maxCritico != null && max != null && maxCritico.compareTo(max) < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "El maximo critico debe ser mayor o igual que el maximo normal");
        }
    }

    private static String texto(BigDecimal valor) {
        return valor == null ? "-" : valor.stripTrailingZeros().toPlainString();
    }

    private static String recortar(String detalle) {
        return detalle.length() <= MAX_DETALLE ? detalle : detalle.substring(0, MAX_DETALLE);
    }
}
