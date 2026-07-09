package cl.pulsocare.consultas.service;

import cl.pulsocare.consultas.model.Lectura;
import cl.pulsocare.consultas.repo.LecturaRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class LecturaService {

    private final LecturaRepository repo;
    private final int limiteMaximo;

    public LecturaService(LecturaRepository repo,
                          @Value("${pulsocare.consultas.limite-maximo:1000}") int limiteMaximo) {
        this.repo = repo;
        this.limiteMaximo = limiteMaximo;
    }

    /** Historico de un paciente; acota el limite pedido al tope configurado. */
    public List<Lectura> historico(long idPaciente, Long idSignoVital,
                                   LocalDateTime desde, LocalDateTime hasta, Integer limite) {
        int efectivo = (limite == null || limite <= 0) ? limiteMaximo : Math.min(limite, limiteMaximo);
        return repo.historico(idPaciente, idSignoVital, desde, hasta, efectivo);
    }

    public List<Lectura> ultimas(long idPaciente) {
        return repo.ultimasPorSigno(idPaciente);
    }
}
