# ==========================================================================
# SQS — cola de alertas del hot path. La Lambda encola aquí las alertas
# críticas confirmadas; ms-notification (en el EC2) las consume y envía email.
# La DLQ recoge lo que falla tras var.sqs_max_receive_count intentos.
# ==========================================================================

resource "aws_sqs_queue" "alertas_dlq" {
  name                      = "${var.sqs_queue_name}-dlq"
  message_retention_seconds = 1209600 # 14 días, el máximo

  tags = {
    Componente = "hot-path-alertas-dlq"
  }
}

resource "aws_sqs_queue" "alertas" {
  name                       = var.sqs_queue_name
  visibility_timeout_seconds = var.sqs_visibility_timeout

  redrive_policy = jsonencode({
    deadLetterTargetArn = aws_sqs_queue.alertas_dlq.arn
    maxReceiveCount     = var.sqs_max_receive_count
  })

  tags = {
    Componente = "hot-path-alertas"
  }
}

# Permite que la DLQ solo reciba redrive desde su cola fuente.
resource "aws_sqs_queue_redrive_allow_policy" "dlq" {
  queue_url = aws_sqs_queue.alertas_dlq.id

  redrive_allow_policy = jsonencode({
    redrivePermission = "byQueue"
    sourceQueueArns   = [aws_sqs_queue.alertas.arn]
  })
}
