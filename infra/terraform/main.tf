# ==========================================================================
# PulsoCare — Infraestructura como código (Terraform)
#
# Declara TODO lo que hoy vive solo como "clicks" en la consola de AWS
# Academy: Kinesis, Lambda, SQS, SNS, ElastiCache, API Gateway y EC2. El
# objetivo es poder reconstruir la plataforma en una cuenta AWS real con un
# `terraform apply`, cuando el lab de Academy caduque.
#
# Nota sobre AWS Academy: dentro del lab NO se puede `terraform apply`
# completo porque el LabRole bloquea crear roles IAM. Pon `create_iam_roles =
# false` y pasa los ARNs del LabRole; o simplemente usa esto en tu cuenta
# real. Para capturar los valores exactos que hoy existen en el lab, corre
# antes `../inventario.sh`.
# ==========================================================================

terraform {
  required_version = ">= 1.5"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }

  # Estado local por defecto. Para trabajo en equipo, descomenta y usa un
  # bucket S3 con DynamoDB para el lock:
  #
  # backend "s3" {
  #   bucket         = "pulsocare-tfstate"
  #   key            = "backend/terraform.tfstate"
  #   region         = "us-east-1"
  #   dynamodb_table = "pulsocare-tflock"
  # }
}

provider "aws" {
  region = var.region

  default_tags {
    tags = {
      Proyecto = "PulsoCare"
      Entorno  = var.entorno
      GestPor  = "Terraform"
    }
  }
}
