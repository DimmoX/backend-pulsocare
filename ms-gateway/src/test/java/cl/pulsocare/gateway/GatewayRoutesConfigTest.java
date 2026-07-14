package cl.pulsocare.gateway;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ms-gateway es la puerta de entrada: su unica logica es el enrutamiento
 * declarado en application.yml. Estas pruebas validan ese contrato de ruteo
 * (id, predicado Path y destino de cada ruta), que es el punto critico del
 * componente: un Path mal escrito manda las peticiones al microservicio
 * equivocado o las deja sin ruta.
 */
class GatewayRoutesConfigTest {

    private static List<Map<String, Object>> rutas;
    private static Map<String, Object> gateway;

    @BeforeAll
    @SuppressWarnings("unchecked")
    static void cargarConfig() {
        try (InputStream in = GatewayRoutesConfigTest.class.getClassLoader()
                .getResourceAsStream("application.yml")) {
            Map<String, Object> root = new Yaml().load(in);
            Map<String, Object> spring = (Map<String, Object>) root.get("spring");
            Map<String, Object> cloud = (Map<String, Object>) spring.get("cloud");
            gateway = (Map<String, Object>) cloud.get("gateway");
            rutas = (List<Map<String, Object>>) gateway.get("routes");
        } catch (Exception e) {
            throw new RuntimeException("No se pudo leer application.yml", e);
        }
    }

    /** Primer predicado Path de la ruta con ese id. */
    @SuppressWarnings("unchecked")
    private String pathDe(String id) {
        Map<String, Object> ruta = rutas.stream()
                .filter(r -> id.equals(r.get("id"))).findFirst().orElseThrow();
        return ((List<String>) ruta.get("predicates")).stream()
                .filter(p -> p.startsWith("Path=")).findFirst().orElseThrow()
                .substring("Path=".length());
    }

    private String uriDe(String id) {
        return rutas.stream().filter(r -> id.equals(r.get("id")))
                .map(r -> (String) r.get("uri")).findFirst().orElseThrow();
    }

    private int indiceDe(String id) {
        for (int i = 0; i < rutas.size(); i++) if (id.equals(rutas.get(i).get("id"))) return i;
        return -1;
    }

    @Test @DisplayName("El gateway define las 6 rutas del backend")
    void definidasSeisRutas() {
        assertThat(rutas).hasSize(6);
        assertThat(rutas).allSatisfy(r -> assertThat(r.get("id")).isNotNull());
    }

    @Test @DisplayName("Ruta auth -> ms-auth (/api/auth/**, puerto 8082)")
    void rutaAuth() {
        assertThat(pathDe("auth")).isEqualTo("/api/auth/**");
        assertThat(uriDe("auth")).contains("8082");
    }

    @Test @DisplayName("Ruta umbrales -> ms-config (/api/umbrales/**, puerto 8083)")
    void rutaUmbrales() {
        assertThat(pathDe("umbrales")).isEqualTo("/api/umbrales/**");
        assertThat(uriDe("umbrales")).contains("8083");
    }

    @Test @DisplayName("Ruta alertas -> ms-consultas (/api/alertas/**, puerto 8084)")
    void rutaAlertas() {
        assertThat(pathDe("alertas")).isEqualTo("/api/alertas/**");
        assertThat(uriDe("alertas")).contains("8084");
    }

    @Test @DisplayName("Ruta lecturas -> ms-consultas (/api/pacientes/*/lecturas/**, puerto 8084)")
    void rutaLecturas() {
        assertThat(pathDe("lecturas")).isEqualTo("/api/pacientes/*/lecturas/**");
        assertThat(uriDe("lecturas")).contains("8084");
    }

    @Test @DisplayName("Ruta usuarios -> ms-pacientes (/api/usuarios/**, puerto 8081)")
    void rutaUsuarios() {
        assertThat(pathDe("usuarios")).isEqualTo("/api/usuarios/**");
        assertThat(uriDe("usuarios")).contains("8081");
    }

    @Test @DisplayName("Ruta pacientes -> ms-pacientes (/api/pacientes/**, puerto 8081)")
    void rutaPacientes() {
        assertThat(pathDe("pacientes")).isEqualTo("/api/pacientes/**");
        assertThat(uriDe("pacientes")).contains("8081");
    }

    @Test @DisplayName("La ruta especifica de lecturas va ANTES que la generica de pacientes")
    void ordenLecturasAntesDePacientes() {
        // Si /api/pacientes/** se evaluara primero, las lecturas nunca llegarian a ms-consultas.
        assertThat(indiceDe("lecturas")).isLessThan(indiceDe("pacientes"));
    }

    @Test @DisplayName("Toda ruta tiene un predicado Path y un destino http")
    void todasTienenPathYDestino() {
        for (Map<String, Object> r : rutas) {
            String id = (String) r.get("id");
            assertThat(pathDe(id)).startsWith("/api/");
            assertThat(uriDe(id)).contains("http");
        }
    }

    @Test @DisplayName("CORS habilitado con metodos y origen configurables")
    @SuppressWarnings("unchecked")
    void corsConfigurado() {
        Map<String, Object> cors = (Map<String, Object>) gateway.get("globalcors");
        assertThat(cors).as("globalcors debe estar definido").isNotNull();
        Map<String, Object> confs = (Map<String, Object>) cors.get("cors-configurations");
        Map<String, Object> global = (Map<String, Object>) confs.get("[/**]");
        assertThat((List<String>) global.get("allowedMethods")).contains("GET", "POST", "OPTIONS");
        assertThat(global.get("allowedOrigins").toString()).contains("FRONTEND_ORIGIN");
    }
}
