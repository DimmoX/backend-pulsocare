# ==========================================================================
# IAM — roles de ejecución. En una cuenta AWS real, Terraform los crea. En AWS
# Academy no se pueden crear roles: pon create_iam_roles = false y reutiliza el
# LabRole (lambda_execution_role_arn / ec2_instance_profile_name).
# ==========================================================================

locals {
  # Rol efectivo de la Lambda: el que crea Terraform o el que pasas a mano.
  lambda_role_arn = var.create_iam_roles ? aws_iam_role.lambda[0].arn : var.lambda_execution_role_arn

  # Instance profile efectivo del EC2.
  ec2_instance_profile = var.create_iam_roles ? aws_iam_instance_profile.ec2[0].name : var.ec2_instance_profile_name
}

# --- Rol de la Lambda -----------------------------------------------------
data "aws_iam_policy_document" "lambda_assume" {
  count = var.create_iam_roles ? 1 : 0

  statement {
    actions = ["sts:AssumeRole"]
    principals {
      type        = "Service"
      identifiers = ["lambda.amazonaws.com"]
    }
  }
}

resource "aws_iam_role" "lambda" {
  count              = var.create_iam_roles ? 1 : 0
  name               = "${var.lambda_function_name}-rol"
  assume_role_policy = data.aws_iam_policy_document.lambda_assume[0].json
}

# Logs en CloudWatch.
resource "aws_iam_role_policy_attachment" "lambda_logs" {
  count      = var.create_iam_roles ? 1 : 0
  role       = aws_iam_role.lambda[0].name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AWSLambdaBasicExecutionRole"
}

# Leer del stream de Kinesis (GetRecords, GetShardIterator, DescribeStream...).
resource "aws_iam_role_policy_attachment" "lambda_kinesis" {
  count      = var.create_iam_roles ? 1 : 0
  role       = aws_iam_role.lambda[0].name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AWSLambdaKinesisExecutionRole"
}

# Encolar alertas en SQS.
data "aws_iam_policy_document" "lambda_sqs" {
  count = var.create_iam_roles ? 1 : 0

  statement {
    actions   = ["sqs:SendMessage", "sqs:GetQueueAttributes", "sqs:GetQueueUrl"]
    resources = [aws_sqs_queue.alertas.arn]
  }
}

resource "aws_iam_role_policy" "lambda_sqs" {
  count  = var.create_iam_roles ? 1 : 0
  name   = "enviar-a-sqs"
  role   = aws_iam_role.lambda[0].id
  policy = data.aws_iam_policy_document.lambda_sqs[0].json
}

# --- Rol / instance profile del EC2 (ms-notification) ---------------------
data "aws_iam_policy_document" "ec2_assume" {
  count = var.create_iam_roles ? 1 : 0

  statement {
    actions = ["sts:AssumeRole"]
    principals {
      type        = "Service"
      identifiers = ["ec2.amazonaws.com"]
    }
  }
}

resource "aws_iam_role" "ec2" {
  count              = var.create_iam_roles ? 1 : 0
  name               = "pulsocare-ec2-${local.sufijo}-rol"
  assume_role_policy = data.aws_iam_policy_document.ec2_assume[0].json
}

# ms-notification: consumir SQS y publicar en SNS.
data "aws_iam_policy_document" "ec2_worker" {
  count = var.create_iam_roles ? 1 : 0

  statement {
    actions   = ["sqs:ReceiveMessage", "sqs:DeleteMessage", "sqs:GetQueueAttributes"]
    resources = [aws_sqs_queue.alertas.arn]
  }

  statement {
    actions   = ["sns:Publish"]
    resources = [aws_sns_topic.alertas.arn]
  }
}

resource "aws_iam_role_policy" "ec2_worker" {
  count  = var.create_iam_roles ? 1 : 0
  name   = "sqs-consumir-sns-publicar"
  role   = aws_iam_role.ec2[0].id
  policy = data.aws_iam_policy_document.ec2_worker[0].json
}

# Permite gestionar la instancia por SSM (el CI/CD usa Send-Command).
resource "aws_iam_role_policy_attachment" "ec2_ssm" {
  count      = var.create_iam_roles ? 1 : 0
  role       = aws_iam_role.ec2[0].name
  policy_arn = "arn:aws:iam::aws:policy/AmazonSSMManagedInstanceCore"
}

resource "aws_iam_instance_profile" "ec2" {
  count = var.create_iam_roles ? 1 : 0
  name  = "pulsocare-ec2-${local.sufijo}-perfil"
  role  = aws_iam_role.ec2[0].name
}
