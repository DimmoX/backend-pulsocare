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
import java.util.ArrayList;
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
                recortar("Signo %d: %s".formatted(req.idSignoVital(), definidos(creado))));

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

        // Solo los limites que realmente cambiaron: repetir los cuatro obliga a quien
        // audita a comparar a ojo para encontrar el unico que se movio.
        bitacora.registrar(req.idDefinidoPor(), antes.idPaciente(), "EDITAR_UMBRAL",
                recortar("Signo %d: %s".formatted(antes.idSignoVital(), cambios(antes, despues))));

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
                recortar("Signo %d vuelve a los valores por defecto (tenía %s)"
                        .formatted(antes.idSignoVital(), definidos(antes))));
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
        // Un umbral tiene que definir sus cuatro limites. Uno a medias deja al sistema
        // clasificando ese signo con una mezcla de valores propios y por defecto, y uno
        // enteramente vacio es un registro que no significa nada: ya se creo uno asi
        // desde la pantalla y dejo la ficha marcando todo como critico.
        if (min == null || max == null || minCritico == null || maxCritico == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Los cuatro limites son obligatorios: minimo y maximo normal, minimo y maximo critico");
        }
        if (min.compareTo(max) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "El minimo normal no puede ser mayor que el maximo normal");
        }
        if (minCritico.compareTo(maxCritico) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "El minimo critico no puede ser mayor que el maximo critico");
        }
        if (minCritico.compareTo(min) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "El minimo critico debe ser menor o igual que el minimo normal");
        }
        if (maxCritico.compareTo(max) < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "El maximo critico debe ser mayor o igual que el maximo normal");
        }
    }

    /**
     * Etiquetas de los cuatro limites. Son las mismas que rotulan las columnas en la
     * pantalla de limites de alarma: quien audita reconoce de inmediato que campo se
     * toco sin tener que traducir nada.
     */
    private static final String MIN_NORMAL = "Mín. normal";
    private static final String MAX_NORMAL = "Máx. normal";
    private static final String MIN_CRITICO = "Mín. crítico";
    private static final String MAX_CRITICO = "Máx. crítico";

    /** Los limites que quedaron definidos, para cuando no hay un estado anterior. */
    private static String definidos(Umbral u) {
        List<String> partes = new ArrayList<>();
        if (u.valorMin() != null) partes.add(MIN_NORMAL + ": " + texto(u.valorMin()));
        if (u.valorMax() != null) partes.add(MAX_NORMAL + ": " + texto(u.valorMax()));
        if (u.valorMinCritico() != null) partes.add(MIN_CRITICO + ": " + texto(u.valorMinCritico()));
        if (u.valorMaxCritico() != null) partes.add(MAX_CRITICO + ": " + texto(u.valorMaxCritico()));
        return partes.isEmpty() ? "sin límites definidos" : String.join(", ", partes);
    }

    /** Solo los limites cuyo valor cambio, con su valor anterior y el nuevo. */
    private static String cambios(Umbral antes, Umbral despues) {
        List<String> partes = new ArrayList<>();
        agregarSiCambio(partes, MIN_NORMAL, antes.valorMin(), despues.valorMin());
        agregarSiCambio(partes, MAX_NORMAL, antes.valorMax(), despues.valorMax());
        agregarSiCambio(partes, MIN_CRITICO, antes.valorMinCritico(), despues.valorMinCritico());
        agregarSiCambio(partes, MAX_CRITICO, antes.valorMaxCritico(), despues.valorMaxCritico());
        return partes.isEmpty() ? "sin cambios" : String.join(", ", partes);
    }

    private static void agregarSiCambio(List<String> partes, String etiqueta,
                                        BigDecimal antes, BigDecimal despues) {
        if (sonIguales(antes, despues)) return;
        partes.add("%s: %s -> %s".formatted(etiqueta, texto(antes), texto(despues)));
    }

    /**
     * compareTo y no equals: la base devuelve NUMBER(6,2), asi que un 60 recien enviado
     * vuelve como 60.00. Con equals serian distintos y la bitacora reportaria un cambio
     * que nunca ocurrio.
     */
    private static boolean sonIguales(BigDecimal a, BigDecimal b) {
        if (a == null || b == null) return a == b;
        return a.compareTo(b) == 0;
    }

    private static String texto(BigDecimal valor) {
        return valor == null ? "-" : valor.stripTrailingZeros().toPlainString();
    }

    private static String recortar(String detalle) {
        return detalle.length() <= MAX_DETALLE ? detalle : detalle.substring(0, MAX_DETALLE);
    }
}
