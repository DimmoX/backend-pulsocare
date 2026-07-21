# Infraestructura como código — PulsoCare

Respaldo reproducible de la infraestructura AWS del backend. Sirve para dos
cosas:

1. **No perder nada** cuando el lab de AWS Academy caduque.
2. **Reconstruir todo** en una cuenta AWS real con un `terraform apply`.

## Qué cubre

Terraform declara el hot path completo y su entorno:

| Recurso | Archivo | Notas |
|---|---|---|
| Kinesis Data Stream | `terraform/kinesis.tf` | `pulsocare-signos-vitales` |
| Lambda + trigger + capa | `terraform/lambda.tf` | consume Kinesis, encola en SQS |
| SQS + DLQ | `terraform/sqs.tf` | `pulsocare-alertas` (+ redrive) |
| SNS + suscripciones | `terraform/sns.tf` | tópico **standard** (email) |
| ElastiCache Redis | `terraform/elasticache.tf` | TLS en tránsito |
| API Gateway (HTTP) | `terraform/apigateway.tf` | `ANY /{proxy+}` → EC2:8080 |
| EC2 ×2 + Security Groups | `terraform/ec2.tf` | corren docker-compose |
| Roles IAM | `terraform/iam.tf` | solo en cuenta real (ver abajo) |

## Qué NO cubre (y por qué)

- **Código de la app** — ya está versionado (microservicios, Lambda, replayer,
  frontend, `docker-compose.*.yml`).
- **Base de datos Oracle** — la estructura está en los scripts SQL
  (`documentos/pulsocare_ddl_completo.sql`). Vive en Oracle Cloud, fuera del
  lab; no depende de este Terraform.
- **Azure AD B2C** — el tenant de identidad vive en Azure, sobrevive al lab.
  Conviene exportar aparte los *user flows* y el *app registration*.
- **Imágenes Docker** — publicadas en Docker Hub, fuera del lab.

## Antes de empezar

- Terraform ≥ 1.5 y AWS CLI configurado.
- **Empaquetar la Lambda** (el `.zip` con el código):
  ```bash
  mkdir -p build
  cd ../lambda && zip -j ../infra/build/funcion.zip lambda_function.py && cd -
  ```
  La capa (`../lambda/capa_oracledb_linux.zip`) ya existe y lleva `oracledb` +
  el wallet.
- Copiar y completar las variables:
  ```bash
  cd terraform
  cp terraform.tfvars.example terraform.tfvars   # editar: secretos y correos
  ```

## Capturar el estado actual del lab (antes de que caduque)

Con las credenciales temporales de Academy activas:

```bash
export AWS_ACCESS_KEY_ID=...  AWS_SECRET_ACCESS_KEY=...  AWS_SESSION_TOKEN=...
./inventario.sh
```

Deja los valores reales (shards, políticas, batch size, endpoints) en
`inventario/<fecha>/*.json`. Compáralos con los defaults de
`terraform/variables.tf` y ajusta si algo difiere.

## Levantar en una cuenta AWS real

```bash
cd terraform
terraform init
terraform plan
terraform apply
```

Al terminar, `terraform output` te da lo que necesitas para el resto:

- `api_url` → `environment.ts` del frontend y `API_URL` del replayer.
- `kinesis_stream_name` → `KINESIS_STREAM` del replayer.
- `sqs_queue_url`, `sns_topic_arn`, `redis_endpoint` → `.env` de las instancias.

Después, el despliegue de los contenedores sigue igual que en
[`../DESPLIEGUE-AWS.md`](../DESPLIEGUE-AWS.md): copiar el wallet y el `.env` al
EC2 y `docker compose up`, o dejar que el CI/CD lo haga por SSM.

## Levantar dentro de AWS Academy (limitación IAM)

El **LabRole no permite crear roles IAM**, así que Terraform no puede crear los
roles de `iam.tf`. En `terraform.tfvars`:

```hcl
create_iam_roles          = false
lambda_execution_role_arn = "arn:aws:iam::<ACCOUNT_ID>:role/LabRole"
ec2_instance_profile_name = "LabInstanceProfile"
```

Aun así, Academy puede bloquear otras acciones (crear ElastiCache, ciertas
redes). Dentro del lab el objetivo real es **capturar** con `inventario.sh`; el
`apply` completo está pensado para tu cuenta AWS real.

## Estado y secretos

- `terraform.tfstate`, `terraform.tfvars` y `.terraform/` están en `.gitignore`:
  llevan secretos y **no se versionan**.
- Para trabajo en equipo, usa el backend S3 comentado en `terraform/main.tf`.
