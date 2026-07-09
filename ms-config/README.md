# PulsoCare — ms-config (Spring Boot)

Microservicio REST del *cold path* para administrar los **umbrales de signos
vitales por paciente** (`PC_UMBRAL`): los límites normales y críticos que la
Lambda usa para clasificar cada lectura (NORMAL / ATENCION / CRITICO) y disparar
alertas.

## Endpoints

| Método | Ruta | Descripción |
|---|---|---|
| `GET` | `/api/umbrales` | Lista todos los umbrales |
| `GET` | `/api/umbrales?idPaciente={id}` | Lista los umbrales de un paciente |
| `GET` | `/api/umbrales/{id}` | Obtiene un umbral (404 si no existe) |
| `POST` | `/api/umbrales` | Crea un umbral (201) |
| `PUT` | `/api/umbrales/{id}` | Actualiza los valores de un umbral |
| `DELETE` | `/api/umbrales/{id}` | Baja lógica: lo deja no vigente (`VIGENTE = 0`) |

Ejemplo de creación:
```bash
curl -X POST http://localhost:8083/api/umbrales -H 'Content-Type: application/json' \
  -d '{"idPaciente":1,"idSignoVital":1,"valorMin":60,"valorMax":100,
       "valorMinCritico":40,"valorMaxCritico":130,"idDefinidoPor":1}'
```

Los cuatro valores son opcionales (un signo puede controlar solo mínimo, solo
máximo, o ambos). El servicio valida que `valorMin <= valorMax` y
`valorMinCritico <= valorMaxCritico` (misma regla que `CK_UMBRAL_RANGO`).

## Variables de entorno
| Variable | Ejemplo |
|---|---|
| `DB_USER` | `ADMIN` |
| `DB_PASSWORD` | (contraseña de la BD) |
| `DB_DSN` | `bdpulsocaretads_low` |
| `WALLET_DIR` | ruta al wallet descomprimido (TNS_ADMIN) |
| `SERVER_PORT` | `8083` (opcional) |

> El wallet usa `cwallet.sso` (auto-login): el JDBC no necesita password de wallet.

## Compilar y ejecutar
```bash
mvn -q package -DskipTests
export DB_USER=ADMIN DB_PASSWORD='...' DB_DSN=bdpulsocaretads_low
export WALLET_DIR="/ruta/a/Wallet_bdPulsoCareTADS"
java -jar target/ms-config-1.0.0.jar
```
