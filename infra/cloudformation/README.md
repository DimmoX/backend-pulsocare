# CloudFormation — PulsoCare

`pulsocare-infra.yaml` declara la misma infraestructura que `../terraform`
(Kinesis, Lambda + trigger, SQS + DLQ, SNS, ElastiCache, API Gateway, EC2 ×2),
en formato nativo de AWS. Usa el que prefieras: **no despliegues los dos a la
vez** o chocarán por los mismos nombres de recurso.

## Diferencias respecto al Terraform

- **El código de la Lambda va en S3.** CloudFormation no admite un `.zip` local:
  sube `funcion.zip` y la capa a un bucket y pasa `LambdaS3Bucket` / `LambdaS3Key`
  / `LayerS3Key`.
- **Dos instancias explícitas** (`BackendInstance1/2`): CFN no tiene `count`.
- **Hasta 3 correos** (`Email1/2/3`) para el SNS, como parámetros opcionales.

## Prerequisitos

```bash
# 1) Empaquetar y subir el código de la Lambda a S3
mkdir -p build && (cd ../lambda && zip -j ../infra/build/funcion.zip lambda_function.py)
aws s3 cp ../build/funcion.zip                  s3://<TU_BUCKET>/lambda/funcion.zip
aws s3 cp ../lambda/capa_oracledb_linux.zip     s3://<TU_BUCKET>/lambda/capa_oracledb_linux.zip
```

## Desplegar (cuenta AWS real)

```bash
aws cloudformation deploy \
  --template-file pulsocare-infra.yaml \
  --stack-name pulsocare-backend \
  --capabilities CAPABILITY_NAMED_IAM \
  --parameter-overrides \
      VpcId=vpc-xxxx \
      SubnetIds=subnet-aaa,subnet-bbb \
      LambdaS3Bucket=<TU_BUCKET> \
      LambdaDbPassword=<SECRETO> \
      Email1=medico1@ejemplo.com
```

Al terminar, mira las salidas del stack (`ApiUrl`, `SqsQueueUrl`, `SnsTopicArn`,
`RedisEndpoint`, `Ec2PublicDns`) para configurar el frontend, el replayer y el
`.env` de las instancias.

## Dentro de AWS Academy (limitación IAM)

El LabRole no permite crear roles. Pasa:

```
CrearRolesIAM=false
LambdaExecutionRoleArn=arn:aws:iam::<ACCOUNT_ID>:role/LabRole
Ec2InstanceProfileName=LabInstanceProfile
```

(Omite `--capabilities CAPABILITY_NAMED_IAM`, ya que no se crean roles.)

## Validación

```bash
cfn-lint pulsocare-infra.yaml
```
