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
