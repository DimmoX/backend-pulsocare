# ==========================================================================
# Kinesis Data Stream — la puerta de entrada del hot path. El replayer publica
# aquí las señales y la Lambda las consume vía event source mapping.
# ==========================================================================

resource "aws_kinesis_stream" "signos" {
  name             = var.kinesis_stream_name
  shard_count      = var.kinesis_shard_count
  retention_period = var.kinesis_retention_hours

  stream_mode_details {
    stream_mode = "PROVISIONED"
  }

  tags = {
    Componente = "hot-path-ingesta"
  }
}
