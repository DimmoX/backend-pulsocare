# Despliegue en AWS (EC2 + API Gateway)

Guía para levantar el backend de PulsoCare en una instancia EC2 con
`docker-compose.aws.yml`, con el **AWS API Gateway** como puerta pública.

```
Frontend → AWS API Gateway (ANY /{proxy+}) → http://<ec2>:8080 (ms-gateway) → los 6 microservicios
```

## 1. Preparar la instancia EC2

- AMI con Docker (Amazon Linux 2023 o Ubuntu). Instalar Docker + el plugin de compose:
  ```bash
  sudo yum install -y docker && sudo systemctl enable --now docker   # Amazon Linux
  sudo usermod -aG docker ec2-user
  # plugin compose v2: docker compose version
  ```
- **Rol de instancia**: adjuntar el *instance profile* con **LabRole** (AWS Academy).
  Así `ms-notification` toma las credenciales de AWS del metadata del EC2 y **no
  necesitas poner claves en `.env`** (las temporales de Academy expiran).
- **Security Group**:
  - *Inbound* `8080` desde el API Gateway (o `0.0.0.0/0` para pruebas rápidas).
  - *Outbound* todo (para llegar a Oracle Autonomous, Kinesis, SQS, SNS).

## 2. Subir el proyecto, el wallet y el `.env`

```bash
git clone <repo> && cd backend-pulsocare
# copiar el wallet a ./Wallet_bdPulsoCareTADS  (scp o descarga)
cp .env.example .env
```
Completar en `.env`:
```
DB_PASSWORD=...                 # clave de la BD
SQS_QUEUE_URL=https://sqs...    # cola de alertas
SNS_TOPIC_ARN=arn:aws:sns:...   # topico de notificaciones
FRONTEND_ORIGIN=https://<dominio-del-front>   # para CORS
```
> Las claves `AWS_ACCESS_KEY_ID/SECRET/SESSION_TOKEN` **no** hacen falta si el EC2
> tiene el rol de instancia. Solo úsalas si no pudiste adjuntar el rol.

## 3. Levantar la pila

```bash
docker compose -f docker-compose.aws.yml up -d --build
docker compose -f docker-compose.aws.yml ps
```
Prueba local en el EC2: `curl http://localhost:8080/api/pacientes` debe responder 200.

## 4. Configurar el AWS API Gateway (HTTP API)

1. **Routes → Create**: método `ANY`, ruta `/{proxy+}` (una sola, greedy).
2. **Integrations → Create**: tipo *HTTP URI*, método `ANY`, URL
   `http://<DNS-publico-del-EC2>:8080/{proxy}`.
3. Adjuntar esa integración a la ruta `ANY /{proxy+}`.
4. **CORS**: NO lo configures aquí — ya lo maneja el `ms-gateway`. (Si lo pones en
   ambos, se duplican las cabeceras `Access-Control-Allow-Origin` y el navegador
   rechaza la respuesta.)

## 5. Activar el hot path (Kinesis → Lambda → SQS → notification)

- **Lambda**: setear la variable `SQS_QUEUE_URL` (la misma del `.env`).
- **Kinesis**: activar el *event source mapping* (trigger de la Lambda).
- **Replayer**: dejarlo emitiendo señales del pool MIMIC hacia Kinesis.
- `ms-notification` (ya corriendo en el EC2) consumirá la cola y enviará los emails.

## 6. Apuntar el frontend

En `environment.ts` del frontend:
```ts
apiUrl: 'https://<api-id>.execute-api.us-east-1.amazonaws.com/api'
```
Y en `app.config.ts`, el `protectedResourceMap` de MSAL debe apuntar a esa misma
URL para que adjunte el token de Entra.

## Nota sobre imágenes

Este compose **construye** las imágenes en el EC2 (`build:`). Para producción es
preferible construirlas una vez, subirlas a **ECR** y que el compose haga `pull`
en vez de `build` (arranques más rápidos y reproducibles).
