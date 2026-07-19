package cl.pulsocare.consultas.service;

import cl.pulsocare.consultas.model.PuntajeNews2;
import org.springframework.stereotype.Service;

/**
 * Calcula el puntaje NEWS2 de un paciente a partir de su ultima lectura de cada signo.
 *
 * Se apoya en LecturaService.ultimas(), que ya resuelve el cache-aside: asi el puntaje
 * se obtiene sin consultas extra a Oracle cuando el dashboard ya tiene los datos frescos.
 */
@Service
public class News2Service {

    private final LecturaService lecturas;

    public News2Service(LecturaService lecturas) {
        this.lecturas = lecturas;
    }

    public PuntajeNews2 calcular(long idPaciente) {
        return CalculadoraNews2.calcular(lecturas.ultimas(idPaciente));
    }
}
