--------------------------------------------------------------------------------
-- PulsoCare - Datos de prueba (semana 5)
--------------------------------------------------------------------------------
-- Crea 2 pacientes de test, cada uno vinculado a un SUBJECT_ID real de MIMIC-IV
-- (asignacion aleatoria; en produccion la hace Spring Boot al crear el paciente),
-- y sus UMBRALES por signo vital. Con esto la Lambda ya puede persistir lecturas
-- y generar alertas (resuelve la FK ID_PACIENTE -> PC_PACIENTE).
--
-- Catalogos usados:
--   PC_MODALIDAD_ATENCION: 1=Domicilio, 2=Hosp. domiciliaria, 3=Clinica
--   PC_ESTADO_PACIENTE   : 1=Estable, 2=En observacion, 3=Critico, 4=Alta
--   PC_SIGNO_VITAL       : 1=FC, 2=SpO2, 3=Sistolica, 4=Diastolica, 5=Temp, 6=FR
--------------------------------------------------------------------------------

-- 1) Pacientes (ID_PACIENTE es identity; SUBJECT_ID es la clave real de MIMIC)
INSERT INTO PC_PACIENTE (SUBJECT_ID, NOMBRE, APELLIDO_PATERNO, APELLIDO_MATERNO,
                         FECHA_NACIMIENTO, SEXO, ID_MODALIDAD, ID_ESTADO_PACIENTE)
VALUES (10005348, 'Rosa', 'Fuentealba', 'Soto',
        DATE '1948-03-12', 'F', 2, 1);

INSERT INTO PC_PACIENTE (SUBJECT_ID, NOMBRE, APELLIDO_PATERNO, APELLIDO_MATERNO,
                         FECHA_NACIMIENTO, SEXO, ID_MODALIDAD, ID_ESTADO_PACIENTE)
VALUES (10005817, 'Pedro', 'Munoz', 'Reyes',
        DATE '1955-09-30', 'M', 1, 2);

-- 2) Umbrales por paciente y signo vital (normal y critico).
--    Se referencia al paciente por su SUBJECT_ID (unico) para no depender del ID.
INSERT INTO PC_UMBRAL (ID_PACIENTE, ID_SIGNO_VITAL, VALOR_MIN, VALOR_MAX, VALOR_MIN_CRITICO, VALOR_MAX_CRITICO)
SELECT p.ID_PACIENTE, u.s, u.vmin, u.vmax, u.cmin, u.cmax
FROM   PC_PACIENTE p
CROSS  JOIN (
        SELECT 1 s, 60  vmin, 100 vmax, 40  cmin, 130 cmax FROM dual UNION ALL  -- FC (bpm)
        SELECT 2,   95,      100,       90,       100       FROM dual UNION ALL  -- SpO2 (%)
        SELECT 3,   90,      120,       70,       180       FROM dual UNION ALL  -- Sistolica (mmHg)
        SELECT 4,   60,      80,        40,       110       FROM dual UNION ALL  -- Diastolica (mmHg)
        SELECT 5,   36,      37.5,      35,       39        FROM dual UNION ALL  -- Temperatura (C)
        SELECT 6,   12,      20,        8,        30        FROM dual            -- FR (insp/min)
       ) u
WHERE  p.SUBJECT_ID IN (10005348, 10005817);

COMMIT;
