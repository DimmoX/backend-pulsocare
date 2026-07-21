# ==========================================================================
# SNS — tópico de notificaciones. Debe ser STANDARD (no FIFO): las colas FIFO
# no admiten suscripciones por email. ms-notification publica aquí y cada
# médico suscrito recibe el correo tras confirmar la suscripción.
# ==========================================================================

resource "aws_sns_topic" "alertas" {
  name = var.sns_topic_name

  tags = {
    Componente = "notificaciones"
  }
}

# Una suscripción por correo. Cada dirección recibe un email de confirmación
# de AWS que el médico debe aceptar antes de que lleguen alertas.
resource "aws_sns_topic_subscription" "email" {
  for_each = toset(var.sns_email_subscriptions)

  topic_arn = aws_sns_topic.alertas.arn
  protocol  = "email"
  endpoint  = each.value
}
