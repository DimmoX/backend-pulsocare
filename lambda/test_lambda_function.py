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


# ---------------------------------------------------------------------------
# Ruido de alertas: una condicion sostenida generaba una alerta y un correo por
# CADA lectura. Estas pruebas fijan las dos reglas que lo cortan: solo se anuncia
# cuando el estado empeora, y solo el nivel critico llega a SQS.
# ---------------------------------------------------------------------------
import base64  # noqa: E402
import json  # noqa: E402


class CursorFalso:
    """Cursor minimo: responde el estado previo y anota que se inserto."""

    def __init__(self, estado_previo=None):
        self.estado_previo = estado_previo
        self.inserciones = []
        self._fila = None

    def var(self, *_args, **_kwargs):
        variable = MagicMock()
        variable.getvalue.return_value = [1]
        return variable

    def execute(self, sql, **_params):
        sql = " ".join(sql.split())
        if sql.startswith("SELECT ID_ESTADO_LECTURA"):
            self._fila = (self.estado_previo,) if self.estado_previo else None
        elif "INSERT INTO PC_ALERTA" in sql:
            self.inserciones.append("ALERTA")
        elif "INSERT INTO PC_LECTURA_SIGNO_VITAL" in sql:
            self.inserciones.append("LECTURA")

    def fetchone(self):
        return self._fila


def _evento_kinesis(itemid, valor):
    cuerpo = {
        "id_paciente": 41,
        "itemid": itemid,
        "valor": valor,
        "unidad": "bpm",
        "fecha_medicion": "2026-07-18T10:00:00",
        "signo": "FC",
    }
    datos = base64.b64encode(json.dumps(cuerpo).encode()).decode()
    return {"Records": [{"kinesis": {"data": datos}}]}


def _correr(monkeypatch, itemid, valor, estado_previo):
    """Corre el handler con un cursor falso y devuelve (cursor, mensajes_sqs)."""
    cursor = CursorFalso(estado_previo)
    conexion = MagicMock()
    conexion.cursor.return_value = cursor
    monkeypatch.setattr(lf, "obtener_conexion", lambda *a, **k: conexion)

    sqs = MagicMock()
    monkeypatch.setattr(lf, "_sqs", sqs)
    monkeypatch.setattr(lf, "SQS_QUEUE_URL", "https://cola-de-prueba")

    resultado = lf.lambda_handler(_evento_kinesis(itemid, valor), None)
    return cursor, sqs, resultado


def test_condicion_sostenida_no_repite_la_alerta(monkeypatch):
    # FC 110 (atencion) cuando la lectura anterior ya estaba en atencion:
    # el episodio ya se anuncio, no debe volver a anunciarse.
    cursor, sqs, resultado = _correr(monkeypatch, "220045", 110, lf.ESTADO_ATENCION)
    assert "ALERTA" not in cursor.inserciones
    assert resultado["alertas"] == 0
    sqs.send_message.assert_not_called()


def test_empeorar_de_atencion_a_critico_si_alerta(monkeypatch):
    # FC 140 (critico) viniendo de atencion: el paciente empeoro, hay que avisar.
    cursor, sqs, resultado = _correr(monkeypatch, "220045", 140, lf.ESTADO_ATENCION)
    assert "ALERTA" in cursor.inserciones
    assert resultado["alertas"] == 1
    sqs.send_message.assert_called_once()


def test_primera_lectura_anomala_si_alerta(monkeypatch):
    # Sin lecturas previas se asume normal, para no perder la primera anomalia.
    cursor, _sqs, resultado = _correr(monkeypatch, "220045", 140, None)
    assert "ALERTA" in cursor.inserciones
    assert resultado["alertas"] == 1


def test_mejorar_no_alerta(monkeypatch):
    # FC 110 (atencion) viniendo de critico: el paciente mejoro, no es un evento nuevo.
    cursor, _sqs, resultado = _correr(monkeypatch, "220045", 110, lf.ESTADO_CRITICO)
    assert "ALERTA" not in cursor.inserciones
    assert resultado["alertas"] == 0


def test_atencion_se_registra_pero_no_notifica(monkeypatch):
    # Nivel atencion desde normal: queda la alerta en la BD para la ficha, sin correo.
    cursor, sqs, resultado = _correr(monkeypatch, "220045", 110, lf.ESTADO_NORMAL)
    assert "ALERTA" in cursor.inserciones
    assert resultado["alertas"] == 1
    sqs.send_message.assert_not_called()


def test_critico_desde_normal_notifica(monkeypatch):
    cursor, sqs, _resultado = _correr(monkeypatch, "220045", 140, lf.ESTADO_NORMAL)
    assert "ALERTA" in cursor.inserciones
    sqs.send_message.assert_called_once()


def test_valor_normal_no_consulta_alerta(monkeypatch):
    cursor, sqs, resultado = _correr(monkeypatch, "220045", 80, lf.ESTADO_NORMAL)
    assert "ALERTA" not in cursor.inserciones
    assert resultado["procesados"] == 1
    sqs.send_message.assert_not_called()
