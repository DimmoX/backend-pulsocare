# PulsoCare — ms-notification (Spring Boot)

Worker que cierra la cadena de notificaciones del *hot path*:

```
Lambda (alerta fuera de umbral)
   └─ publica "AlarmRaised" ─► SQS (pulsocare-alertas) ──► DLQ (tras 3 reintentos)
                                      │
                                      ▼
                          notification-service (este)
                              1) lee la alerta
                              2) busca el equipo de cuidado del paciente
                                 (PC_ASIGNACION_CUIDADO → PC_USUARIO)
                              3) publica el aviso en SNS (email)
                              4) registra el envío en PC_NOTIFICACION
```

> ℹ️ El SMS real (AWS End User Messaging) está **bloqueado en AWS Academy**
> (el rol `voclabs` no tiene permisos `sms-voice:*`). Por eso el último eslabón
> usa **SNS con email** como entrega real. Cambiar a SMS sería sustituir la
> llamada a SNS por End User Messaging en `NotificationWorker`.

## Requisitos
- Java 17+ y Maven.
- Credenciales AWS en `~/.aws/credentials` (en Academy, copiar las del Learner Lab).
- Suscripción de email **confirmada** en el topic SNS (clic en "Confirm subscription").

## Variables de entorno
| Variable | Ejemplo |
|---|---|
| `DB_USER` | `ADMIN` |
| `DB_PASSWORD` | (contraseña de la BD) |
| `DB_DSN` | `bdpulsocaretads_low` |
| `WALLET_DIR` | ruta a la carpeta del wallet descomprimido (TNS_ADMIN) |
| `AWS_REGION` | `us-east-1` |
| `SQS_QUEUE_URL` | `https://sqs.us-east-1.amazonaws.com/<cuenta>/pulsocare-alertas` |
| `SNS_TOPIC_ARN` | `arn:aws:sns:us-east-1:<cuenta>:pulsocare-notificaciones` |

> El wallet usa `cwallet.sso` (auto-login): el JDBC **no** necesita contraseña de
> wallet, solo `TNS_ADMIN=WALLET_DIR`. Las librerías `oraclepki` + `osdt_core` +
> `osdt_cert` (incluidas en el pom) habilitan ese soporte.

## Compilar y ejecutar
```bash
mvn -q package -DskipTests

export DB_USER=ADMIN DB_PASSWORD='...' DB_DSN=bdpulsocaretads_low
export WALLET_DIR="/ruta/a/Wallet_bdPulsoCareTADS"
export AWS_REGION=us-east-1
export SQS_QUEUE_URL='https://sqs.us-east-1.amazonaws.com/<cuenta>/pulsocare-alertas'
export SNS_TOPIC_ARN='arn:aws:sns:us-east-1:<cuenta>:pulsocare-notificaciones'

java -jar target/ms-notification-1.0.0.jar
```
Queda escuchando SQS (long polling). Detener con Ctrl+C.

## Probar sin la Lambda (mensaje manual a SQS)
```bash
aws sqs send-message --region us-east-1 --queue-url "$SQS_QUEUE_URL" \
  --message-body '{"id_alerta":4,"id_paciente":1,"id_signo_vital":2,"signo":"Saturacion de oxigeno","valor":92,"unidad":"%","nivel":1,"umbral_violado":"MIN","fecha_medicion":"2026-06-27T12:00:02"}'
```
`id_alerta` debe existir en `PC_ALERTA` (FK). Verás en el log "Notificado ..." y
una fila nueva en `PC_NOTIFICACION` por cada destinatario del paciente.
