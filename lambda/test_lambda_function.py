# -*- coding: utf-8 -*-
"""
Punto critico: clasificar() decide NORMAL / ATENCION / CRITICO y el nivel de la
alerta. Es el cerebro del hot path: si clasifica mal, se generan alertas falsas
o -peor- no se genera una alerta real y nadie avisa al medico.

Ejecutar:  cd lambda && python3 -m pytest test_lambda_function.py -v

Se stubbean oracledb y boto3 (no instalados en local ni necesarios para probar
la logica de clasificacion, que es pura).
"""
import sys
from unittest.mock import MagicMock

sys.modules.setdefault("oracledb", MagicMock())
sys.modules.setdefault("boto3", MagicMock())

import lambda_function as lf  # noqa: E402


def test_valor_normal_no_genera_alerta():
    # FC 80 bpm esta en rango normal (60-100)
    assert lf.clasificar("220045", 80) == (lf.ESTADO_NORMAL, None)


def test_valor_en_atencion():
    # FC 110 bpm: fuera de normal (>100) pero no critico (<130)
    assert lf.clasificar("220045", 110) == (lf.ESTADO_ATENCION, lf.NIVEL_ATENCION)


def test_valor_critico_por_arriba():
    # FC 135 bpm: supera el maximo critico (130)
    assert lf.clasificar("220045", 135) == (lf.ESTADO_CRITICO, lf.NIVEL_CRITICO)


def test_valor_critico_por_abajo():
    # FC 35 bpm: por debajo del minimo critico (40)
    assert lf.clasificar("220045", 35) == (lf.ESTADO_CRITICO, lf.NIVEL_CRITICO)


def test_spo2_baja_es_atencion():
    # SpO2 92%: bajo el normal (95) pero sobre el critico (90)
    assert lf.clasificar("220277", 92) == (lf.ESTADO_ATENCION, lf.NIVEL_ATENCION)


def test_spo2_muy_baja_es_critico():
    # SpO2 85%: bajo el minimo critico (90)
    assert lf.clasificar("220277", 85) == (lf.ESTADO_CRITICO, lf.NIVEL_CRITICO)


def test_diastolica_21_es_critico():
    # Caso del evento de prueba: diastolica 21 mmHg (< 40) -> critico
    assert lf.clasificar("220180", 21) == (lf.ESTADO_CRITICO, lf.NIVEL_CRITICO)


def test_signo_desconocido_se_trata_como_normal():
    # Un itemid fuera del catalogo no debe generar alerta
    assert lf.clasificar("999999", 50) == (lf.ESTADO_NORMAL, None)


# --- los dos parametros que completan NEWS2 ---------------------------------
# Son categoricos, no magnitudes continuas: un rango mal puesto haria que un
# paciente con una canula de oxigeno figure como critico.

def test_glasgow_15_es_normal():
    assert lf.clasificar("GCS", 15) == (lf.ESTADO_NORMAL, None)


def test_glasgow_14_es_atencion():
    # NEWS2 no gradua la conciencia, pero el estado de la lectura si: 14 no es
    # "alerta" y tampoco es una emergencia.
    assert lf.clasificar("GCS", 14) == (lf.ESTADO_ATENCION, lf.NIVEL_ATENCION)


def test_glasgow_bajo_13_es_critico():
    assert lf.clasificar("GCS", 12) == (lf.ESTADO_CRITICO, lf.NIVEL_CRITICO)


def test_aire_ambiente_es_normal():
    assert lf.clasificar("O2SUP", 0) == (lf.ESTADO_NORMAL, None)


def test_con_oxigeno_es_atencion_no_critico():
    # Necesitar oxigeno nunca es "normal", pero tampoco es por si solo una urgencia.
    assert lf.clasificar("O2SUP", 1) == (lf.ESTADO_ATENCION, lf.NIVEL_ATENCION)
# ---------------------------------------------------------------------------
# Ruido de alertas
#
# Una condicion sostenida generaba una alerta y un correo por CADA lectura. Estas
# pruebas fijan las tres reglas que lo cortan: la condicion debe sostenerse para
# confirmarse, se anuncia una sola vez por episodio, y solo lo critico llega a SQS.
# ---------------------------------------------------------------------------
import base64  # noqa: E402
import json  # noqa: E402
from datetime import datetime, timedelta  # noqa: E402

INICIO = datetime(2026, 7, 18, 10, 0, 0)


class CursorFalso:
    """
    Cursor minimo con memoria de las lecturas del paciente.

    Recibe el historial previo (estados, del mas antiguo al mas reciente), agrega la
    lectura que inserta el handler y responde la consulta de confirmacion como lo
    haria Oracle: las N mas recientes primero.
    """

    def __init__(self, historial=(), separacion_seg=7):
        self.separacion = separacion_seg
        self.lecturas = [
            (estado, INICIO + timedelta(seconds=i * separacion_seg))
            for i, estado in enumerate(historial)
        ]
        self.inserciones = []
        self._filas = []

    def var(self, *_args, **_kwargs):
        variable = MagicMock()
        variable.getvalue.return_value = [1]
        return variable

    def execute(self, sql, **params):
        sql = " ".join(sql.split())
        if "INSERT INTO PC_LECTURA_SIGNO_VITAL" in sql:
            self.inserciones.append("LECTURA")
            siguiente = (
                self.lecturas[-1][1] + timedelta(seconds=self.separacion)
                if self.lecturas
                else INICIO
            )
            self.lecturas.append((params["id_estado"], siguiente))
        elif sql.startswith("SELECT ID_ESTADO_LECTURA"):
            self._filas = list(reversed(self.lecturas))[: params["cuantas"]]
        elif "INSERT INTO PC_ALERTA" in sql:
            self.inserciones.append("ALERTA")

    def fetchall(self):
        return self._filas

    def fetchone(self):
        return self._filas[0] if self._filas else None


def _correr(monkeypatch, itemid, valor, historial=(), separacion_seg=7):
    """Corre el handler sobre un historial dado. Devuelve (cursor, sqs, resultado)."""
    cursor = CursorFalso(historial, separacion_seg)
    conexion = MagicMock()
    conexion.cursor.return_value = cursor
    monkeypatch.setattr(lf, "obtener_conexion", lambda *a, **k: conexion)

    sqs = MagicMock()
    monkeypatch.setattr(lf, "_sqs", sqs)
    monkeypatch.setattr(lf, "SQS_QUEUE_URL", "https://cola-de-prueba")

    cuerpo = {
        "id_paciente": 41,
        "itemid": itemid,
        "valor": valor,
        "unidad": "%",
        "fecha_medicion": "2026-07-18T10:00:00",
        "signo": "SPO2",
    }
    datos = base64.b64encode(json.dumps(cuerpo).encode()).decode()
    evento = {"Records": [{"kinesis": {"data": datos}}]}
    return cursor, sqs, lf.lambda_handler(evento, None)


# --- confirmacion de lo critico -------------------------------------------------
# SpO2 89 es critico (rango critico 90-100). El caso real: 95, luego 89, luego 99.

def test_pico_critico_aislado_no_alerta(monkeypatch):
    # Es el artefacto que se observo 80 veces en 24h: una sola lectura fuera de rango.
    cursor, sqs, resultado = _correr(monkeypatch, "220277", 89, historial=(1, 1, 1))
    assert "ALERTA" not in cursor.inserciones
    assert resultado["alertas"] == 0
    sqs.send_message.assert_not_called()


def test_critico_sostenido_alerta_y_notifica(monkeypatch):
    # Tercera lectura critica seguida: la condicion se confirma y recien ahi se avisa.
    cursor, sqs, resultado = _correr(monkeypatch, "220277", 89, historial=(1, 1, 3, 3))
    assert "ALERTA" in cursor.inserciones
    assert resultado["alertas"] == 1
    sqs.send_message.assert_called_once()


def test_critico_ya_confirmado_no_repite(monkeypatch):
    # Cuarta lectura critica: el episodio ya se anuncio, no se vuelve a anunciar.
    cursor, sqs, resultado = _correr(monkeypatch, "220277", 89, historial=(1, 3, 3, 3))
    assert "ALERTA" not in cursor.inserciones
    sqs.send_message.assert_not_called()


def test_racha_separada_por_un_corte_no_confirma(monkeypatch):
    # Tres criticas seguidas pero espaciadas 10 min: hubo un corte del monitoreo,
    # no es una condicion sostenida.
    cursor, sqs, _resultado = _correr(
        monkeypatch, "220277", 89, historial=(1, 1, 3, 3), separacion_seg=600
    )
    assert "ALERTA" not in cursor.inserciones
    sqs.send_message.assert_not_called()


def test_la_lectura_critica_siempre_se_guarda(monkeypatch):
    # Aunque no amerite alerta, el pico debe quedar en el historico y en la ficha.
    cursor, _sqs, resultado = _correr(monkeypatch, "220277", 89, historial=(1, 1, 1))
    assert "LECTURA" in cursor.inserciones
    assert resultado["procesados"] == 1
    assert cursor.lecturas[-1][0] == lf.ESTADO_CRITICO


# --- atencion: se registra, no interrumpe ---------------------------------------

def test_atencion_alerta_sin_confirmacion_pero_no_notifica(monkeypatch):
    # SpO2 94 es atencion. No necesita sostenerse porque no genera correo.
    cursor, sqs, resultado = _correr(monkeypatch, "220277", 94, historial=(1,))
    assert "ALERTA" in cursor.inserciones
    assert resultado["alertas"] == 1
    sqs.send_message.assert_not_called()


def test_atencion_sostenida_no_repite(monkeypatch):
    cursor, _sqs, resultado = _correr(monkeypatch, "220277", 94, historial=(1, 2))
    assert "ALERTA" not in cursor.inserciones
    assert resultado["alertas"] == 0


def test_empeorar_de_atencion_a_critico_se_confirma_igual(monkeypatch):
    # Subir de atencion a critico no salta la confirmacion: el artefacto de 89 puede
    # aparecer igual mientras el paciente ya estaba en atencion.
    cursor, sqs, _resultado = _correr(monkeypatch, "220277", 89, historial=(2, 2, 2))
    assert "ALERTA" not in cursor.inserciones
    sqs.send_message.assert_not_called()


def test_valor_normal_no_alerta(monkeypatch):
    cursor, sqs, resultado = _correr(monkeypatch, "220277", 98, historial=(1,))
    assert "ALERTA" not in cursor.inserciones
    assert resultado["procesados"] == 1
    sqs.send_message.assert_not_called()


def test_glasgow_critico_alerta_a_la_primera(monkeypatch):
    """
    Un deterioro de conciencia no puede esperar a "confirmarse".

    El Glasgow se evalua cada varias horas (cadencia medida: 300s contra 4-5s de los
    signos de sensor), asi que exigirle 3 lecturas dentro de la ventana de 120s
    significaria que NUNCA alerta. Este test existe para que nadie lo agregue a la
    confirmacion sin darse cuenta.
    """
    cursor, sqs, resultado = _correr(
        monkeypatch, "GCS", 10, historial=(1,), separacion_seg=300
    )
    assert "ALERTA" in cursor.inserciones
    assert resultado["alertas"] == 1
    sqs.send_message.assert_called_once()
