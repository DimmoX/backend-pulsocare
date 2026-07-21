# ==========================================================================
# EC2 — instancias que corren la pila de microservicios con docker-compose.
# El API Gateway apunta al puerto del ms-gateway. El user_data deja Docker
# listo; el despliegue de los contenedores lo hace el CI/CD vía SSM.
# ==========================================================================

# AMI de Amazon Linux 2023 más reciente.
data "aws_ami" "al2023" {
  most_recent = true
  owners      = ["amazon"]

  filter {
    name   = "name"
    values = ["al2023-ami-2023.*-x86_64"]
  }

  filter {
    name   = "architecture"
    values = ["x86_64"]
  }
}

resource "aws_security_group" "ec2" {
  name        = "pulsocare-backend-${local.sufijo}"
  description = "Puerta del ms-gateway y salida a Kinesis/SQS/SNS/Oracle."
  vpc_id      = local.vpc_id

  ingress {
    description = "ms-gateway (tras el API Gateway)."
    from_port   = var.ec2_puerto_gateway
    to_port     = var.ec2_puerto_gateway
    protocol    = "tcp"
    cidr_blocks = [var.ec2_cidr_ingreso_gateway]
  }

  # SSH solo si defines un key pair.
  dynamic "ingress" {
    for_each = var.ec2_key_name != "" ? [1] : []
    content {
      description = "SSH."
      from_port   = 22
      to_port     = 22
      protocol    = "tcp"
      cidr_blocks = [var.ec2_cidr_ingreso_gateway]
    }
  }

  egress {
    description = "Salida a Oracle Autonomous, Kinesis, SQS y SNS."
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = {
    Componente = "backend"
  }
}

resource "aws_instance" "backend" {
  count = var.cantidad_instancias

  ami                    = data.aws_ami.al2023.id
  instance_type          = var.ec2_instance_type
  subnet_id              = element(tolist(local.subnet_ids), count.index)
  vpc_security_group_ids = [aws_security_group.ec2.id]
  iam_instance_profile   = local.ec2_instance_profile
  key_name               = var.ec2_key_name != "" ? var.ec2_key_name : null

  # Deja Docker + compose listos. El código y el .env los coloca el CI/CD
  # (SSM Send-Command) o tú a mano; ver DESPLIEGUE-AWS.md.
  user_data = <<-EOF
    #!/bin/bash
    set -e
    dnf install -y docker
    systemctl enable --now docker
    usermod -aG docker ec2-user
    curl -SL https://github.com/docker/compose/releases/latest/download/docker-compose-linux-x86_64 \
      -o /usr/local/bin/docker-compose
    chmod +x /usr/local/bin/docker-compose
  EOF

  tags = {
    Name       = "pulsocare-backend-${local.sufijo}-${count.index + 1}"
    Componente = "backend"
  }
}
