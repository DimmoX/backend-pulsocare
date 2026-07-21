# ==========================================================================
# ElastiCache (Redis) — caché de lecturas/umbrales que consultan los
# microservicios (CACHE_ENABLED=true). Cifrado en tránsito (TLS) activado, que
# es lo que espera el backend con REDIS_SSL=true.
# ==========================================================================

resource "aws_elasticache_subnet_group" "redis" {
  name       = "pulsocare-redis-${local.sufijo}"
  subnet_ids = local.subnet_ids
}

resource "aws_security_group" "redis" {
  name        = "pulsocare-redis-${local.sufijo}"
  description = "Acceso a Redis solo desde las instancias del backend."
  vpc_id      = local.vpc_id

  ingress {
    description     = "Redis desde el backend (EC2)."
    from_port       = 6379
    to_port         = 6379
    protocol        = "tcp"
    security_groups = [aws_security_group.ec2.id]
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = {
    Componente = "cache"
  }
}

resource "aws_elasticache_replication_group" "redis" {
  replication_group_id = "pulsocare-redis-${local.sufijo}"
  description          = "Caché de PulsoCare (lecturas y umbrales)."

  engine         = "redis"
  engine_version = var.elasticache_engine_version
  node_type      = var.elasticache_node_type
  port           = 6379

  num_cache_clusters = 1

  subnet_group_name  = aws_elasticache_subnet_group.redis.name
  security_group_ids = [aws_security_group.redis.id]

  transit_encryption_enabled = true
  at_rest_encryption_enabled = true

  tags = {
    Componente = "cache"
  }
}
