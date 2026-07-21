# ==========================================================================
# Lambda consumidora — evalúa umbrales, persiste en Oracle y encola alertas.
# El código vive en ../../lambda/lambda_function.py; empaquétalo antes (ver
# README). La capa lleva oracledb + el wallet de Oracle Autonomous.
# ==========================================================================

resource "aws_lambda_layer_version" "oracledb" {
  layer_name          = "${var.lambda_function_name}-oracledb"
  filename            = var.lambda_layer_zip_path
  source_code_hash    = filebase64sha256(var.lambda_layer_zip_path)
  compatible_runtimes = [var.lambda_runtime]
  description         = "oracledb + wallet de Oracle Autonomous para la Lambda."
}

resource "aws_lambda_function" "procesar" {
  function_name = var.lambda_function_name
  role          = local.lambda_role_arn
  runtime       = var.lambda_runtime
  handler       = "lambda_function.lambda_handler"
  timeout       = var.lambda_timeout
  memory_size   = var.lambda_memory

  filename         = var.lambda_package_path
  source_code_hash = filebase64sha256(var.lambda_package_path)

  layers = [aws_lambda_layer_version.oracledb.arn]

  # SQS_QUEUE_URL se inyecta desde el recurso real para no repetirlo a mano;
  # el resto (DB_*, WALLET_*, tuning) viene de var.lambda_env.
  environment {
    variables = merge(
      var.lambda_env,
      { SQS_QUEUE_URL = aws_sqs_queue.alertas.url }
    )
  }

  tags = {
    Componente = "hot-path-procesamiento"
  }
}

# Trigger automático: conecta el stream de Kinesis con la Lambda.
resource "aws_lambda_event_source_mapping" "kinesis" {
  event_source_arn                   = aws_kinesis_stream.signos.arn
  function_name                      = aws_lambda_function.procesar.arn
  starting_position                  = "LATEST"
  batch_size                         = var.lambda_batch_size
  maximum_batching_window_in_seconds = 5
  enabled                            = true
}
