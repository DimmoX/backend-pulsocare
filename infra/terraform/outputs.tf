# ==========================================================================
# Salidas — los valores que necesitas para configurar el resto (frontend,
# replayer, .env de las instancias) una vez levantada la infra.
# ==========================================================================

output "api_url" {
  description = "URL pública del backend. Va en environment.ts del frontend y en API_URL del replayer."
  value       = aws_apigatewayv2_stage.default.invoke_url
}

output "kinesis_stream_name" {
  description = "Stream al que publica el replayer (KINESIS_STREAM)."
  value       = aws_kinesis_stream.signos.name
}

output "sqs_queue_url" {
  description = "Cola de alertas (SQS_QUEUE_URL del .env y de la Lambda)."
  value       = aws_sqs_queue.alertas.url
}

output "sns_topic_arn" {
  description = "Tópico de notificaciones (SNS_TOPIC_ARN del .env)."
  value       = aws_sns_topic.alertas.arn
}

output "redis_endpoint" {
  description = "Endpoint primario de Redis (REDIS_HOST del .env)."
  value       = aws_elasticache_replication_group.redis.primary_endpoint_address
}

output "ec2_public_dns" {
  description = "DNS público de las instancias del backend."
  value       = aws_instance.backend[*].public_dns
}

output "lambda_function_name" {
  description = "Nombre de la Lambda consumidora."
  value       = aws_lambda_function.procesar.function_name
}
