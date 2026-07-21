# ==========================================================================
# Variables. Los valores por defecto reflejan lo que hoy existe en el lab;
# los secretos y lo específico del entorno se pasan por terraform.tfvars.
# ==========================================================================

variable "region" {
  description = "Región AWS donde se despliega todo."
  type        = string
  default     = "us-east-1"
}

variable "entorno" {
  description = "Nombre del entorno (para tags y sufijos)."
  type        = string
  default     = "prod"
}

# --- IAM ------------------------------------------------------------------
variable "create_iam_roles" {
  description = <<-EOT
    true  = Terraform crea los roles IAM (cuenta AWS real).
    false = reutiliza roles existentes; obligatorio en AWS Academy, donde el
            LabRole ya existe y no se pueden crear roles nuevos. En ese caso
            rellena lambda_execution_role_arn y ec2_instance_profile_name.
  EOT
  type        = bool
  default     = true
}

variable "lambda_execution_role_arn" {
  description = "ARN del rol de ejecución de la Lambda cuando create_iam_roles = false (ej. el LabRole)."
  type        = string
  default     = ""
}

variable "ec2_instance_profile_name" {
  description = "Instance profile a adjuntar al EC2 cuando create_iam_roles = false (ej. LabInstanceProfile)."
  type        = string
  default     = ""
}

# --- Kinesis --------------------------------------------------------------
variable "kinesis_stream_name" {
  description = "Nombre del Data Stream que alimenta la Lambda."
  type        = string
  default     = "pulsocare-signos-vitales"
}

variable "kinesis_shard_count" {
  description = "Cantidad de shards (modo PROVISIONED). El volumen del replayer cabe en 1."
  type        = number
  default     = 1
}

variable "kinesis_retention_hours" {
  description = "Horas de retención de registros en el stream."
  type        = number
  default     = 24
}

# --- SQS ------------------------------------------------------------------
variable "sqs_queue_name" {
  description = "Cola de alertas que consume ms-notification."
  type        = string
  default     = "pulsocare-alertas"
}

variable "sqs_max_receive_count" {
  description = "Intentos antes de mover un mensaje a la DLQ (redrive policy)."
  type        = number
  default     = 5
}

variable "sqs_visibility_timeout" {
  description = "Segundos que un mensaje queda invisible tras leerse."
  type        = number
  default     = 30
}

# --- SNS ------------------------------------------------------------------
variable "sns_topic_name" {
  description = "Tópico standard de notificaciones (permite suscripciones email)."
  type        = string
  default     = "pulsocare-alertas"
}

variable "sns_email_subscriptions" {
  description = "Correos de médicos a suscribir al tópico. Cada uno debe confirmar el email."
  type        = list(string)
  default     = []
}

# --- Lambda ---------------------------------------------------------------
variable "lambda_function_name" {
  description = "Nombre de la función consumidora de Kinesis."
  type        = string
  default     = "pulsocare-procesar-signos-vitales"
}

variable "lambda_package_path" {
  description = "Ruta al .zip con lambda_function.py (constrúyelo, ver README)."
  type        = string
  default     = "../build/funcion.zip"
}

variable "lambda_layer_zip_path" {
  description = "Ruta al .zip de la capa con oracledb + wallet."
  type        = string
  default     = "../../lambda/capa_oracledb_linux.zip"
}

variable "lambda_runtime" {
  description = "Runtime de Python de la función."
  type        = string
  default     = "python3.12"
}

variable "lambda_timeout" {
  description = "Timeout en segundos (abrir Oracle tarda; el default de 3 s no basta)."
  type        = number
  default     = 30
}

variable "lambda_memory" {
  description = "Memoria en MB."
  type        = number
  default     = 512
}

variable "lambda_batch_size" {
  description = "Registros de Kinesis por invocación (event source mapping)."
  type        = number
  default     = 100
}

variable "lambda_env" {
  description = <<-EOT
    Variables de entorno de la Lambda. Los secretos (DB_PASSWORD,
    WALLET_PASSWORD) pásalos por tfvars, NUNCA los versiones.
  EOT
  type        = map(string)
  default     = {}
  sensitive   = true
}

# --- ElastiCache ----------------------------------------------------------
variable "elasticache_node_type" {
  description = "Tipo de nodo Redis."
  type        = string
  default     = "cache.t3.micro"
}

variable "elasticache_engine_version" {
  description = "Versión del engine Redis."
  type        = string
  default     = "7.1"
}

# --- EC2 ------------------------------------------------------------------
variable "cantidad_instancias" {
  description = "Instancias EC2 que corren la pila de microservicios (docker-compose)."
  type        = number
  default     = 2
}

variable "ec2_instance_type" {
  description = "Tipo de instancia EC2."
  type        = string
  default     = "t3.medium"
}

variable "ec2_key_name" {
  description = "Nombre del key pair para SSH (opcional; vacío = sin SSH directo)."
  type        = string
  default     = ""
}

variable "ec2_puerto_gateway" {
  description = "Puerto público del ms-gateway."
  type        = number
  default     = 8080
}

variable "ec2_cidr_ingreso_gateway" {
  description = "CIDR permitido hacia el puerto del gateway. Restríngelo al API Gateway/tu IP."
  type        = string
  default     = "0.0.0.0/0"
}

# --- VPC ------------------------------------------------------------------
variable "vpc_id" {
  description = "VPC donde crear SG e instancias. Vacío = VPC por defecto de la cuenta."
  type        = string
  default     = ""
}

variable "subnet_ids" {
  description = "Subredes para EC2 y ElastiCache. Vacío = subredes de la VPC por defecto."
  type        = list(string)
  default     = []
}
