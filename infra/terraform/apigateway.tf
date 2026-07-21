# ==========================================================================
# API Gateway (HTTP API) — puerta pública del backend. Una sola ruta greedy
# ANY /{proxy+} que reenvía todo al ms-gateway en el EC2. El CORS NO se toca
# aquí: lo maneja el ms-gateway (si se pone en ambos, se duplican cabeceras y
# el navegador rechaza la respuesta).
#
# Nota: la integración apunta a la primera instancia. Con más de una, lo
# correcto es un ALB delante y apuntar la integración al ALB.
# ==========================================================================

resource "aws_apigatewayv2_api" "backend" {
  name          = "pulsocare-backend-${local.sufijo}"
  protocol_type = "HTTP"

  tags = {
    Componente = "api-gateway"
  }
}

resource "aws_apigatewayv2_integration" "gateway" {
  api_id                 = aws_apigatewayv2_api.backend.id
  integration_type       = "HTTP_PROXY"
  integration_method     = "ANY"
  integration_uri        = "http://${aws_instance.backend[0].public_dns}:${var.ec2_puerto_gateway}/{proxy}"
  payload_format_version = "1.0"
}

resource "aws_apigatewayv2_route" "proxy" {
  api_id    = aws_apigatewayv2_api.backend.id
  route_key = "ANY /{proxy+}"
  target    = "integrations/${aws_apigatewayv2_integration.gateway.id}"
}

resource "aws_apigatewayv2_stage" "default" {
  api_id      = aws_apigatewayv2_api.backend.id
  name        = "$default"
  auto_deploy = true
}
