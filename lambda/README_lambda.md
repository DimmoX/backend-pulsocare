# Lambda consumidora — PulsoCare

Procesa los signos vitales que llegan por Kinesis: evalúa umbrales, persiste en
Oracle y genera alertas. Archivo principal: `lambda_function.py` →
handler `lambda_function.lambda_handler`.

## Flujo

```
Kinesis (pulsocare-signos-vitales)
        │  trigger automático (event source mapping)
        ▼
Lambda (pulsocare-procesar-signos-vitales)
        ├─ clasifica: NORMAL / ATENCION / CRITICO
        ├─ INSERT en PC_LECTURA_SIGNO_VITAL
        └─ si fuera de rango → INSERT en PC_ALERTA (+ punto para SMS)
```

## Pasos en AWS

### 1. Crear la función
- Crear desde cero · Runtime **Python 3.12**.
- Permisos: en **AWS Academy** usar rol existente **`LabRole`**.

### 2. Conectar el trigger de Kinesis
- En la Lambda → **Agregar desencadenador** → **Kinesis**.
- Stream: `pulsocare-signos-vitales`. Tamaño de lote ~100, ventana 1–5 s.
- El rol necesita permisos de lectura de Kinesis
  (`kinesis:GetRecords`, `GetShardIterator`, `DescribeStream`, `ListShards`).
  La política administrada `AWSLambdaKinesisExecutionRole` los cubre.
  (`LabRole` de Academy normalmente ya puede.)

### 3. Capa (Layer) con `oracledb` + wallet
El runtime de Lambda **no trae `oracledb`**. Hay que empaquetarlo:

```bash
mkdir -p capa/python
pip install oracledb -t capa/python
cd capa && zip -r ../capa_oracledb.zip python && cd ..
```
Sube `capa_oracledb.zip` como **Layer** y asóciala a la función.

El **wallet** (`Wallet_bdPulsoCareTADS.zip`) va dentro del paquete de la función
o en la capa, descomprimido en una carpeta `wallet/`. Apunta `WALLET_DIR` a esa
ruta (ej. `/var/task/wallet`).

### 4. Variables de entorno (Configuración → Variables de entorno)
| Clave | Valor ejemplo |
|---|---|
| `DB_USER` | `ADMIN` (o el usuario de la app) |
| `DB_PASSWORD` | tu contraseña |
| `DB_DSN` | `bdpulsocaretads_high` (alias del tnsnames del wallet) |
| `WALLET_DIR` | `/var/task/wallet` |

### 5. Tiempo de espera y memoria
- **Timeout**: súbelo a ~30 s (el default de 3 s es muy poco para abrir BD).
- **Memoria**: 256–512 MB.

## Antes de probar: la BD debe tener datos
La Lambda hace `INSERT` con FKs, así que en Oracle ya deben existir:
- los **pacientes** (`PC_PACIENTE`) con los `ID_PACIENTE` que envía el replayer (1, 2),
- los **signos vitales** (`PC_SIGNO_VITAL`) con los IDs 1..6 mapeados en el código,
- los **estados de lectura / niveles / estados de alerta** sembrados.

> Ajusta en `lambda_function.py` los IDs (`SIGNOS`, `ESTADO_*`, `NIVEL_*`) para
> que coincidan exactamente con los IDs reales sembrados en tus catálogos.

## Probar
1. Deja el replayer enviando a Kinesis.
2. La Lambda se dispara sola; revisa **CloudWatch Logs** (verás
   "Procesados: N | Alertas generadas: M").
3. Consulta en Oracle `PC_LECTURA_SIGNO_VITAL` y `PC_ALERTA`.
