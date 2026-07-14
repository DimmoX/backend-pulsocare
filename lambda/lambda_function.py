# -*- coding: utf-8 -*-
"""
PulsoCare — Lambda consumidora de signos vitales (Kinesis -> Oracle)
====================================================================

Se dispara automaticamente cuando llegan registros al Data Stream de Kinesis
(event source mapping). Por cada lectura:

  1) decodifica el evento (Kinesis entrega los records en base64),
  2) evalua la lectura contra los umbrales del paciente (NORMAL/ATENCION/CRITICO),
  3) persiste en PC_LECTURA_SIGNO_VITAL,
  4) si esta fuera de rango, genera PC_ALERTA (+ deja el punto para el SMS).

Variables de entorno esperadas (configurar en la consola de Lambda):
    DB_USER          usuario de la BD (ej. ADMIN o el usuario de la app)
    DB_PASSWORD      contrasena del usuario
    DB_DSN           alias TNS del wallet (ej. bdpulsocaretads_high)
    WALLET_DIR       carpeta del wallet dentro del paquete (ej. /var/task/wallet)
    WALLET_PASSWORD  (opcional) contrasena del wallet si aplica

Dependencias (van en un Lambda Layer): oracledb
"""

import base64
import json
import os
import time

import boto3      # incluido en el runtime de Lambda
import oracledb   # se provee via Lambda Layer

# Cola SQS donde se publican las alertas (AlarmRaised). El notification-service
# (Spring Boot) la consume. Se configura por variable de entorno SQS_QUEUE_URL.
SQS_QUEUE_URL = os.environ.get("SQS_QUEUE_URL")
_sqs = boto3.client("sqs") if SQS_QUEUE_URL else None

# ---------------------------------------------------------------------------
# 1) Catalogo de signos vitales y rangos de referencia
#    (en produccion los umbrales POR PACIENTE salen de PC_UMBRAL / ElastiCache;
#     estos son el fallback por defecto)
# ---------------------------------------------------------------------------
# itemid -> (id_signo_vital_en_BD, normal_min, normal_max, critico_min, critico_max)
# IDs verificados contra PC_SIGNO_VITAL en la BD (2026-06-27).
SIGNOS = {
    "220045": (1, 60, 100, 40, 130),     # Frecuencia cardiaca (bpm)
    "220277": (2, 95, 100, 90, 100),     # SpO2 (%)
    "220179": (3, 90, 120, 70, 180),     # Presion sistolica (mmHg)
    "220180": (4, 60, 80, 40, 110),      # Presion diastolica (mmHg)
    "223762": (5, 36.0, 37.5, 35.0, 39.0),  # Temperatura (C)
    "220210": (6, 12, 20, 8, 30),        # Frecuencia respiratoria (insp/min)
}

# Codigos de estado de lectura (PC_ESTADO_LECTURA verificado en la BD)
ESTADO_NORMAL = 1
ESTADO_ATENCION = 2
ESTADO_CRITICO = 3

# Nivel de alerta (PC_NIVEL_ALERTA verificado: 1=Atencion, 2=Critico)
NIVEL_ATENCION = 1
NIVEL_CRITICO = 2
ESTADO_ALERTA_NUEVA = 1  # PC_ESTADO_ALERTA: 1='Generada'


# ---------------------------------------------------------------------------
# 2) Conexion a Oracle Autonomous (con wallet)
# ---------------------------------------------------------------------------
def obtener_conexion(intentos=3, espera_seg=2):
    """Conecta a Oracle Autonomous con reintentos.

    La Autonomous cierra conexiones ociosas y a veces rechaza el primer intento
    con DPY-4011 ("the database or network closed the connection"). Un connect de
    un solo tiro tumbaria el batch entero (y con el, la alerta y su notificacion);
    reintentar con backoff da la misma resiliencia que el pool Hikari de los
    microservicios."""
    ultimo_error = None
    for intento in range(1, intentos + 1):
        try:
            return oracledb.connect(
                user=os.environ["DB_USER"],
                password=os.environ["DB_PASSWORD"],
                dsn=os.environ["DB_DSN"],
                config_dir=os.environ.get("WALLET_DIR", "/var/task/wallet"),
                wallet_location=os.environ.get("WALLET_DIR", "/var/task/wallet"),
                wallet_password=os.environ.get("WALLET_PASSWORD"),
            )
        except oracledb.DatabaseError as e:
            ultimo_error = e
            print("Conexion a Oracle fallo (intento %d/%d): %s" % (intento, intentos, e))
            if intento < intentos:
                time.sleep(espera_seg)
    raise ultimo_error


# ---------------------------------------------------------------------------
# 3) Clasificacion de la lectura segun los umbrales
# ---------------------------------------------------------------------------
def clasificar(itemid, valor):
    """Devuelve (id_estado_lectura, nivel_alerta_o_None)."""
    cfg = SIGNOS.get(itemid)
    if cfg is None:
        return ESTADO_NORMAL, None
    _, n_min, n_max, c_min, c_max = cfg
    if valor < c_min or valor > c_max:
        return ESTADO_CRITICO, NIVEL_CRITICO
    if valor < n_min or valor > n_max:
        return ESTADO_ATENCION, NIVEL_ATENCION
    return ESTADO_NORMAL, None


# ---------------------------------------------------------------------------
# 4) Persistencia
# ---------------------------------------------------------------------------
def guardar_lectura(cur, evento, id_signo, id_estado):
    """Inserta en PC_LECTURA_SIGNO_VITAL y devuelve el ID generado."""
    id_var = cur.var(oracledb.NUMBER)
    cur.execute(
        """
        INSERT INTO PC_LECTURA_SIGNO_VITAL
            (ID_PACIENTE, ID_SIGNO_VITAL, VALOR_NUM, UNIDAD,
             FECHA_MEDICION, FECHA_REGISTRO, ID_ESTADO_LECTURA, ORIGEN)
        VALUES
            (:id_paciente, :id_signo, :valor, :unidad,
             TO_TIMESTAMP(:fecha_medicion, 'YYYY-MM-DD"T"HH24:MI:SS'),
             SYSTIMESTAMP, :id_estado, :origen)
        RETURNING ID_LECTURA INTO :id_out
        """,
        id_paciente=int(evento["id_paciente"]),
        id_signo=id_signo,
        valor=float(evento["valor"]),
        unidad=evento["unidad"],
        # recorta la fecha ISO (quita zona/microsegundos) para el TO_TIMESTAMP
        fecha_medicion=evento["fecha_medicion"][:19],
        id_estado=id_estado,
        origen=evento.get("origen", "MIMIC-IV"),
        id_out=id_var,
    )
    return int(id_var.getvalue()[0])


def generar_alerta(cur, evento, id_lectura, id_signo, nivel):
    """Inserta en PC_ALERTA y publica el evento AlarmRaised a SQS."""
    cfg = SIGNOS.get(str(evento.get("itemid")))
    n_min, n_max = (cfg[1], cfg[2]) if cfg else (None, None)
    valor = float(evento["valor"])
    # UMBRAL_VIOLADO es VARCHAR2(10): codigo corto del limite cruzado
    umbral_violado = "MIN" if (n_min is not None and valor < n_min) else "MAX"
    id_var = cur.var(oracledb.NUMBER)
    cur.execute(
        """
        INSERT INTO PC_ALERTA
            (ID_LECTURA, ID_PACIENTE, ID_SIGNO_VITAL, ID_NIVEL_ALERTA,
             ID_ESTADO_ALERTA, VALOR_REGISTRADO, UMBRAL_VIOLADO, FECHA_GENERACION)
        VALUES
            (:id_lectura, :id_paciente, :id_signo, :nivel,
             :estado, :valor, :umbral, SYSTIMESTAMP)
        RETURNING ID_ALERTA INTO :id_out
        """,
        id_lectura=id_lectura,
        id_paciente=int(evento["id_paciente"]),
        id_signo=id_signo,
        nivel=nivel,
        estado=ESTADO_ALERTA_NUEVA,
        valor=valor,
        umbral=umbral_violado,
        id_out=id_var,
    )
    id_alerta = int(id_var.getvalue()[0])

    # Publica AlarmRaised a SQS para que el notification-service envie el aviso.
    if _sqs is not None:
        mensaje = {
            "id_alerta": id_alerta,
            "id_paciente": int(evento["id_paciente"]),
            "id_signo_vital": id_signo,
            "signo": evento.get("signo"),
            "valor": valor,
            "unidad": evento.get("unidad"),
            "nivel": nivel,  # 1=Atencion, 2=Critico
            "umbral_violado": umbral_violado,
            "fecha_medicion": evento.get("fecha_medicion"),
        }
        _sqs.send_message(
            QueueUrl=SQS_QUEUE_URL,
            MessageBody=json.dumps(mensaje),
        )
    return id_alerta


# ---------------------------------------------------------------------------
# 5) Handler
# ---------------------------------------------------------------------------
def lambda_handler(event, context):
    registros = event.get("Records", [])
    procesados, alertas = 0, 0

    conn = obtener_conexion()
    try:
        cur = conn.cursor()
        for rec in registros:
            # Kinesis entrega el payload en base64
            payload = base64.b64decode(rec["kinesis"]["data"])
            evento = json.loads(payload)

            itemid = str(evento.get("itemid"))
            cfg = SIGNOS.get(itemid)
            if cfg is None:
                continue  # signo vital desconocido, se ignora
            id_signo = cfg[0]

            valor = float(evento["valor"])
            id_estado, nivel = clasificar(itemid, valor)

            id_lectura = guardar_lectura(cur, evento, id_signo, id_estado)
            if nivel is not None:
                generar_alerta(cur, evento, id_lectura, id_signo, nivel)
                alertas += 1
            procesados += 1

        conn.commit()  # un solo commit por batch (transaccion ACID)
    except Exception:
        conn.rollback()
        raise
    finally:
        conn.close()

    print("Procesados: %d | Alertas generadas: %d" % (procesados, alertas))
    return {"procesados": procesados, "alertas": alertas}
