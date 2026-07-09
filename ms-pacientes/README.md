# PulsoCare — ms-pacientes (Spring Boot)

Microservicio REST del *cold path* para administrar pacientes. Al crear un
paciente sin `subject_id`, le **asigna uno aleatorio del pool de MIMIC-IV**
(prefiriendo los no usados): ese `subject_id` es el vínculo que permite mostrar
sus signos vitales reales en el *hot path*.

## Endpoints

| Método | Ruta | Descripción |
|---|---|---|
| `GET` | `/api/pacientes` | Lista todos los pacientes |
| `GET` | `/api/pacientes/{id}` | Obtiene un paciente (404 si no existe) |
| `POST` | `/api/pacientes` | Crea un paciente (201). Autoasigna `subject_id` si no se envía |
| `PUT` | `/api/pacientes/{id}` | Actualiza datos del paciente |
| `DELETE` | `/api/pacientes/{id}` | Elimina (409 si tiene lecturas/alertas asociadas) |

### Asignación de cuidado (vínculo cuidador ↔ paciente)

Un cuidador es un usuario (médico o familiar). Asignarlo es lo que habilita sus
notificaciones (`ms-notification` busca el equipo de cuidado aquí) y su acceso a
los signos vitales del paciente.

| Método | Ruta | Descripción |
|---|---|---|
| `GET` | `/api/pacientes/{id}/asignaciones` | Cuidadores activos del paciente (con nombre y rol) |
| `POST` | `/api/pacientes/{id}/asignaciones` | Asigna un cuidador (201). Body `{"idUsuario": 5}` |
| `DELETE` | `/api/pacientes/{id}/asignaciones/{idUsuario}` | Quita el vínculo (baja lógica, 204) |
| `GET` | `/api/usuarios/{idUsuario}/pacientes` | Pacientes que un usuario tiene a cargo |

```bash
# Asignar el familiar (usuario 5) al paciente 1
curl -X POST http://localhost:8081/api/pacientes/1/asignaciones \
  -H 'Content-Type: application/json' -d '{"idUsuario":5}'
```

Ejemplo de creación (el `subject_id` se asigna solo):
```bash
curl -X POST http://localhost:8081/api/pacientes -H 'Content-Type: application/json' \
  -d '{"nombre":"Juan","apellidoPaterno":"Soto","fechaNacimiento":"1960-05-20",
       "sexo":"M","idModalidad":1,"idEstadoPaciente":1}'
```

## Variables de entorno
| Variable | Ejemplo |
|---|---|
| `DB_USER` | `ADMIN` |
| `DB_PASSWORD` | (contraseña de la BD) |
| `DB_DSN` | `bdpulsocaretads_low` |
| `WALLET_DIR` | ruta al wallet descomprimido (TNS_ADMIN) |
| `SERVER_PORT` | `8081` (opcional) |

> El wallet usa `cwallet.sso` (auto-login): el JDBC no necesita password de wallet.

## Compilar y ejecutar
```bash
mvn -q package -DskipTests
export DB_USER=ADMIN DB_PASSWORD='...' DB_DSN=bdpulsocaretads_low
export WALLET_DIR="/ruta/a/Wallet_bdPulsoCareTADS"
java -jar target/ms-pacientes-1.0.0.jar
```

## Notas
- El pool de `subject_id` disponibles está en `src/main/resources/subjects_disponibles.csv`
  (100 subjects reales de MIMIC-IV Demo). La regla de asignación vive en
  `SubjectPool` + `PacienteService` (negocio en la app, no en la base de datos).
