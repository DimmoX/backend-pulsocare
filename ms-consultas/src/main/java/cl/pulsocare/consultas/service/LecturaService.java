package cl.pulsocare.consultas.service;

import cl.pulsocare.consultas.cache.CacheLecturas;
import cl.pulsocare.consultas.model.Lectura;
import cl.pulsocare.consultas.repo.LecturaRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class LecturaService {

    private final LecturaRepository repo;
    private final CacheLecturas cache;
    private final int limiteMaximo;

    public LecturaService(LecturaRepository repo,
                          CacheLecturas cache,
                          @Value("${pulsocare.consultas.limite-maximo:1000}") int limiteMaximo) {
        this.repo = repo;
        this.cache = cache;
        this.limiteMaximo = limiteMaximo;
    }

    /**
     * Una pagina del historico, mas el total de filas que cumplen los filtros (para
     * saber cuantas paginas hay). Acota el limite al tope configurado.
     */
    public PaginaLecturas historico(long idPaciente, Long idSignoVital, LocalDateTime desde,
                                    LocalDateTime hasta, Integer limite, Integer offset,
                                    String orden, Boolean ascendente) {
        if (!LecturaRepository.ordenValido(orden)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Orden no soportado: " + orden);
        }
        int filas = (limite == null || limite <= 0) ? limiteMaximo : Math.min(limite, limiteMaximo);
        int salto = (offset == null || offset < 0) ? 0 : offset;
        boolean asc = Boolean.TRUE.equals(ascendente);

        long total = repo.contar(idPaciente, idSignoVital, desde, hasta);
        List<Lectura> pagina = total == 0
                ? List.of()   // Sin filas: nos ahorramos la segunda consulta.
                : repo.historico(idPaciente, idSignoVital, desde, hasta, filas, salto, orden, asc);
        return new PaginaLecturas(pagina, total);
    }

    /** Una pagina de resultados junto al total, que el controller expone como cabecera. */
    public record PaginaLecturas(List<Lectura> lecturas, long total) {}

    /**
     * Ultima lectura de cada signo (tiles del dashboard). Cache-aside: intenta
     * el cache (ElastiCache); si no esta, consulta Oracle y lo cachea.
     */
    public List<Lectura> ultimas(long idPaciente) {
        return cache.ultimas(idPaciente).orElseGet(() -> {
            List<Lectura> desdeOracle = repo.ultimasPorSigno(idPaciente);
            cache.guardarUltimas(idPaciente, desdeOracle);
            return desdeOracle;
        });
    }
}
