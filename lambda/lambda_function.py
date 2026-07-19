# -*- coding: utf-8 -*-
"""
PulsoCare — Lambda consumidora de signos vitales (Kinesis -> Oracle)
====================================================================

Se dispara automaticamente cuando llegan registros al Data Stream de Kinesis
(event source mapping). Por cada lectura:

  1) decodifica el evento (Kinesis entrega los records en base64),
  2) evalua la lectura contra los umbrales del paciente (NORMAL/ATENCION/CRITICO),
  3) persiste en PC_LECTURA_SIGNO_VITAL,
  4) si el estado EMPEORO respecto de la lectura anterior, genera PC_ALERTA,
  5) y solo si esa alerta es critica, la publica a SQS para que se notifique.

Los pasos 4 y 5 existen para no saturar al equipo medico. Antes se alertaba en cada
lectura fuera de rango, asi que un signo levemente alterado durante horas producia un
correo por lectura y el destinatario terminaba ignorandolos todos. Ahora un episodio
se anuncia cuando empieza o cuando empeora, y el nivel de atencion queda registrado
y visible en la ficha sin interrumpir a nadie.

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
    # Los dos parametros que completan NEWS2. No son itemid de MIMIC: el replayer los
    # deriva de varias filas de texto (los tres componentes del Glasgow, y el
    # dispositivo de oxigeno) y los publica ya numericos. IDs 21 y 22 verificados
    # contra PC_SIGNO_VITAL (2026-07-19): la secuencia ya habia avanzado, no son 7 y 8.
    "GCS": (21, 15, 15, 13, 15),         # Glasgow 3-15: solo 15 es "alerta"
    "O2SUP": (22, 0, 0, 0, 1),           # 0 = aire ambiente, 1 = recibe oxigeno
}

# Codigos de estado de lectura (PC_ESTADO_LECTURA verificado en la BD)
ESTADO_NORMAL = 1
ESTADO_ATENCION = 2
ESTADO_CRITICO = 3

# Nivel de alerta (PC_NIVEL_ALERTA verificado: 1=Atencion, 2=Critico)
NIVEL_ATENCION = 1
NIVEL_CRITICO = 2
ESTADO_ALERTA_NUEVA = 1  # PC_ESTADO_ALERTA: 1='Generada'

# Lecturas consecutivas que debe durar una condicion antes de generar alerta.
# Medido sobre 24h reales del paciente 41 (35.230 lecturas): TODAS sus lecturas
# criticas fueron SpO2=89 aisladas, con 95 antes y 99 seis segundos despues. Una
# saturacion no cae 10 puntos y se recupera en segundos: son artefactos de medicion.
# Atencion no exige confirmacion porque no interrumpe a nadie (no genera correo).
LECTURAS_CONFIRMACION = {
    ESTADO_ATENCION: 1,
    ESTADO_CRITICO: int(os.environ.get("LECTURAS_CONFIRMACION_CRITICA", "3")),
}

# La racha debe caber en esta ventana. Sin esto, tras un corte del monitoreo (se han
# visto huecos de 4 horas) tres lecturas separadas por horas contarian como una
# condicion sostenida. Los signos de sensor tienen una cadencia medida de 4-5s, asi
# que 3 lecturas son ~15s y entran de sobra.
VENTANA_CONFIRMACION_SEG = int(os.environ.get("VENTANA_CONFIRMACION_SEG", "120"))

# Signos que alertan a la primera, sin exigir que la condicion se sostenga.
#
# La confirmacion existe para descartar artefactos de sensor (el dedo que se movio,
# la canula suelta). Estos dos no salen de un sensor continuo: los evalua una persona
# del equipo clinico cada varias horas —su cadencia medida es de 300s contra los 4-5s
# de los demas—, asi que no hay artefacto que filtrar y un valor aislado es un
# hallazgo real. Exigirles una racha dentro de la ventana significaria que un
# deterioro de conciencia no alerta NUNCA, que es el peor fallo posible aqui.
SIN_CONFIRMACION = {"GCS", "O2SUP"}


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
def condicion_confirmada(cur, id_paciente, id_signo, estado, itemid=None):
    """
    True si el estado recien alcanzado es un evento nuevo que amerita alerta.

    Exige dos cosas. Primero, que la condicion se SOSTENGA las lecturas que pide
    LECTURAS_CONFIRMACION para ese estado: un valor aislado suele ser un artefacto de
    medicion (sensor movido, mala lectura), no un cambio clinico. Los signos de
    SIN_CONFIRMACION quedan exentos, porque no salen de un sensor. Segundo, que la
    lectura anterior a esa racha fuera mejor, de modo que un episodio se anuncie una
    sola vez al empezar y no en cada lectura mientras dura.

    Debe llamarse DESPUES de insertar la lectura actual, que cuenta como la primera
    de la racha.
    """
    minimo = 1 if itemid in SIN_CONFIRMACION else LECTURAS_CONFIRMACION.get(estado, 1)
    cur.execute(
        """
        SELECT ID_ESTADO_LECTURA, FECHA_MEDICION
          FROM PC_LECTURA_SIGNO_VITAL
         WHERE ID_PACIENTE = :id_paciente
           AND ID_SIGNO_VITAL = :id_signo
         ORDER BY FECHA_MEDICION DESC
         FETCH FIRST :cuantas ROWS ONLY
        """,
        id_paciente=id_paciente,
        id_signo=id_signo,
        cuantas=minimo + 1,
    )
    filas = cur.fetchall()
    if len(filas) < minimo:
        return False  # el paciente aun no tiene suficientes lecturas

    racha = filas[:minimo]
    if any(int(f[0]) != estado for f in racha):
        return False  # la condicion no se sostuvo

    # Tras un corte del monitoreo las lecturas quedan separadas por horas, y N de
    # ellas ya no significan "sostenido". La racha tiene que caber en una ventana
    # corta para que confirmar tenga sentido.
    if minimo > 1:
        lapso = (racha[0][1] - racha[-1][1]).total_seconds()
        if lapso > VENTANA_CONFIRMACION_SEG:
            return False

    # Si no hay lectura previa a la racha, el paciente recien empieza: se asume que
    # venia normal para no perder su primera anomalia.
    anterior = int(filas[minimo][0]) if len(filas) > minimo else ESTADO_NORMAL
    return anterior < estado


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

    # Solo el nivel critico interrumpe al equipo medico. Atencion queda registrada y
    # visible en la ficha, pero no genera correo: es el mismo criterio de prioridad de
    # los monitores clinicos, donde la baja prioridad se muestra y no anuncia. Sin esto
    # un signo levemente fuera de rango durante horas manda un correo por lectura.
    if nivel != NIVEL_CRITICO:
        return id_alerta

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

            # La lectura SIEMPRE se guarda con su estado real, aunque despues no
            # amerite alerta: el pico sigue viendose en la ficha y en el historico.
            id_lectura = guardar_lectura(cur, evento, id_signo, id_estado)

            if nivel is not None and condicion_confirmada(
                cur, int(evento["id_paciente"]), id_signo, id_estado, itemid
            ):
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
