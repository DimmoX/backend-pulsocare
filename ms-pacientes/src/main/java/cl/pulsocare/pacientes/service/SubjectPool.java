package cl.pulsocare.pacientes.service;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Pool de subject_id reales de MIMIC-IV disponibles para asignar a un paciente.
 * Se carga desde el recurso subjects_disponibles.csv (formato "subject_id;n_lecturas").
 */
@Component
public class SubjectPool {

    private static final Logger log = LoggerFactory.getLogger(SubjectPool.class);
    private final List<Long> subjects = new ArrayList<>();

    @PostConstruct
    void cargar() throws Exception {
        try (BufferedReader r = new BufferedReader(new InputStreamReader(
                new ClassPathResource("subjects_disponibles.csv").getInputStream(), StandardCharsets.UTF_8))) {
            String linea = r.readLine(); // cabecera
            while ((linea = r.readLine()) != null) {
                String s = linea.split(";")[0].trim();
                if (!s.isEmpty()) subjects.add(Long.parseLong(s));
            }
        }
        log.info("Pool de subjects de MIMIC cargado: {} disponibles", subjects.size());
    }

    /**
     * Elige un subject_id aleatorio prefiriendo los que aun no estan usados.
     * Si ya se usaron todos, reutiliza uno al azar del pool completo.
     */
    public Long asignar(Set<Long> yaUsados) {
        List<Long> libres = new ArrayList<>(subjects);
        libres.removeAll(yaUsados);
        List<Long> origen = libres.isEmpty() ? subjects : libres;
        return origen.get(new java.util.Random().nextInt(origen.size()));
    }

    public List<Long> todos() {
        return Collections.unmodifiableList(subjects);
    }
}
