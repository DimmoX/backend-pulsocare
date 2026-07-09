# PulsoCare — ms-consultas (Spring Boot)

Microservicio REST del *cold path* (`:8084`) que **alimenta el dashboard**. Es
principalmente de lectura sobre los datos que el *hot path* deja en Oracle:

- **Lecturas** de signos vitales (`PC_LECTURA_SIGNO_VITAL`): histórico y últimos valores.
- **Alertas** (`PC_ALERTA`): consulta y **reconocimiento** por parte del médico.

Los resultados vienen enriquecidos con el código/nombre del signo, el nivel
(amarillo/rojo) y el estado (generada/notificada/reconocida/resuelta), para que
el frontend reciba etiquetas legibles y no sólo IDs.

## Endpoints

| Método | Ruta | Descripción |
|---|---|---|
| `GET` | `/api/pacientes/{id}/lecturas` | Histórico. Filtros: `idSignoVital`, `desde`, `hasta`, `limite` |
| `GET` | `/api/pacientes/{id}/lecturas/ultimas` | Última lectura de cada signo (tiles en vivo) |
| `GET` | `/api/alertas` | Lista alertas. Filtros: `idPaciente`, `estado` |
| `GET` | `/api/alertas/{id}` | Obtiene una alerta (404 si no existe) |
| `PUT` | `/api/alertas/{id}/reconocer` | El médico la marca `RECONOCIDA` (registra quién y cuándo) |

Ejemplos:
```bash
# Histórico de FC del paciente 1 desde una fecha, máximo 200 filas
curl "http://localhost:8084/api/pacientes/1/lecturas?idSignoVital=1&desde=2026-07-01T00:00:00&limite=200"

# Alertas abiertas (aún no reconocidas) de un paciente
curl "http://localhost:8084/api/alertas?idPaciente=1&estado=GENERADA"

# Reconocer una alerta (médico id 1)
curl -X PUT http://localhost:8084/api/alertas/5/reconocer \
  -H 'Content-Type: application/json' -d '{"idUsuario":1}'
```

## Variables de entorno
| Variable | Ejemplo |
|---|---|
| `DB_USER` | `ADMIN` |
| `DB_PASSWORD` | (contraseña de la BD) |
| `DB_DSN` | `bdpulsocaretads_low` |
| `WALLET_DIR` | ruta al wallet descomprimido (TNS_ADMIN) |
| `SERVER_PORT` | `8084` (opcional) |

> El wallet usa `cwallet.sso` (auto-login): el JDBC no necesita password de wallet.

## Compilar y ejecutar
```bash
mvn -q package -DskipTests
export DB_USER=ADMIN DB_PASSWORD='...' DB_DSN=bdpulsocaretads_low
export WALLET_DIR="/ruta/a/Wallet_bdPulsoCareTADS"
java -jar target/ms-consultas-1.0.0.jar
```

## Notas
- El histórico ordena por `FECHA_MEDICION` descendente y acota las filas al tope
  `pulsocare.consultas.limite-maximo` (1000 por defecto).
- `reconocer` resuelve el estado `RECONOCIDA` por código, no por ID fijo del catálogo.
