package cl.pulsocare.pacientes.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Punto critico: SubjectPool asigna el subject_id real de MIMIC a cada paciente.
 * Si repitiera subjects o entregara uno fuera del pool, el replayer no tendria
 * serie que reproducir y el paciente quedaria sin signos vitales.
 */
class SubjectPoolTest {

    private SubjectPool pool;

    @BeforeEach
    void setUp() throws Exception {
        pool = new SubjectPool();
        pool.cargar();   // carga subjects_disponibles.csv del classpath
    }

    @Test
    @DisplayName("Carga el pool completo de subjects desde el CSV")
    void cargaElPool() {
        assertThat(pool.todos()).isNotEmpty();
        assertThat(pool.todos()).doesNotContainNull();
    }

    @Test
    @DisplayName("Asigna un subject que pertenece al pool")
    void asignaDesdeElPool() {
        Long asignado = pool.asignar(Set.of());
        assertThat(pool.todos()).contains(asignado);
    }

    @Test
    @DisplayName("Prefiere un subject NO usado cuando queda solo uno libre")
    void prefiereNoUsados() {
        List<Long> todos = pool.todos();
        Long libreEsperado = todos.get(0);
        Set<Long> usados = new HashSet<>(todos);
        usados.remove(libreEsperado);         // todos usados menos uno

        assertThat(pool.asignar(usados)).isEqualTo(libreEsperado);
    }

    @Test
    @DisplayName("Si todos estan usados, reutiliza uno del pool (no devuelve null)")
    void fallbackCuandoTodosUsados() {
        Set<Long> todosUsados = new HashSet<>(pool.todos());
        Long asignado = pool.asignar(todosUsados);
        assertThat(asignado).isNotNull();
        assertThat(pool.todos()).contains(asignado);
    }
}
