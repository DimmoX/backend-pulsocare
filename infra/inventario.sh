#!/usr/bin/env bash
# ==========================================================================
# Inventario de la infraestructura PulsoCare en AWS.
#
# Vuelca a JSON la configuración REAL de los recursos que hoy existen en el
# lab de Academy, para no perder los valores exactos (shards, políticas,
# batch size, endpoints...) cuando el lab caduque. Correlo DENTRO del lab,
# con las credenciales temporales activas:
#
#     export AWS_ACCESS_KEY_ID=...  AWS_SECRET_ACCESS_KEY=...  AWS_SESSION_TOKEN=...
#     ./inventario.sh
#
# Los .json quedan en ./inventario/<fecha>/ y sí se pueden versionar (no
# llevan secretos, salvo las env vars de la Lambda: revísalo antes de subir).
# ==========================================================================
set -euo pipefail

REGION="${AWS_REGION:-us-east-1}"
STREAM="${KINESIS_STREAM:-pulsocare-signos-vitales}"
LAMBDA="${LAMBDA_NAME:-pulsocare-procesar-signos-vitales}"
COLA="${SQS_QUEUE:-pulsocare-alertas}"
TOPICO="${SNS_TOPIC:-pulsocare-alertas}"

DESTINO="inventario/$(date +%Y-%m-%d)"
mkdir -p "$DESTINO"
echo "Guardando inventario en $DESTINO (región $REGION)"

vuelca() { # <archivo> <comando...>
  local archivo="$1"; shift
  echo "  - $archivo"
  if "$@" > "$DESTINO/$archivo" 2>"$DESTINO/$archivo.err"; then
    rm -f "$DESTINO/$archivo.err"
  else
    echo "    (falló: revisa $archivo.err)"
  fi
}

# --- Kinesis --------------------------------------------------------------
vuelca kinesis.json aws kinesis describe-stream-summary \
  --stream-name "$STREAM" --region "$REGION"

# --- Lambda: config + trigger ---------------------------------------------
vuelca lambda.json aws lambda get-function-configuration \
  --function-name "$LAMBDA" --region "$REGION"
vuelca lambda-triggers.json aws lambda list-event-source-mappings \
  --function-name "$LAMBDA" --region "$REGION"

# --- SQS: cola + DLQ ------------------------------------------------------
COLA_URL=$(aws sqs get-queue-url --queue-name "$COLA" --region "$REGION" \
  --query QueueUrl --output text 2>/dev/null || echo "")
if [[ -n "$COLA_URL" ]]; then
  vuelca sqs.json aws sqs get-queue-attributes --queue-url "$COLA_URL" \
    --attribute-names All --region "$REGION"
fi
DLQ_URL=$(aws sqs get-queue-url --queue-name "${COLA}-dlq" --region "$REGION" \
  --query QueueUrl --output text 2>/dev/null || echo "")
if [[ -n "$DLQ_URL" ]]; then
  vuelca sqs-dlq.json aws sqs get-queue-attributes --queue-url "$DLQ_URL" \
    --attribute-names All --region "$REGION"
fi

# --- SNS: tópico + suscripciones ------------------------------------------
TOPIC_ARN=$(aws sns list-topics --region "$REGION" \
  --query "Topics[?ends_with(TopicArn, ':${TOPICO}')].TopicArn | [0]" \
  --output text 2>/dev/null || echo "")
if [[ -n "$TOPIC_ARN" && "$TOPIC_ARN" != "None" ]]; then
  vuelca sns.json aws sns get-topic-attributes --topic-arn "$TOPIC_ARN" --region "$REGION"
  vuelca sns-suscripciones.json aws sns list-subscriptions-by-topic \
    --topic-arn "$TOPIC_ARN" --region "$REGION"
fi

# --- ElastiCache ----------------------------------------------------------
vuelca elasticache.json aws elasticache describe-replication-groups --region "$REGION"

# --- API Gateway (HTTP APIs) ----------------------------------------------
vuelca apigateway.json aws apigatewayv2 get-apis --region "$REGION"

# --- EC2: instancias + security groups ------------------------------------
vuelca ec2-instancias.json aws ec2 describe-instances --region "$REGION" \
  --filters "Name=tag:Proyecto,Values=PulsoCare"
vuelca ec2-security-groups.json aws ec2 describe-security-groups --region "$REGION"

echo "Listo. Compara estos valores con los defaults de terraform/variables.tf."
