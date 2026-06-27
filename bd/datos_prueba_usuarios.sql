--------------------------------------------------------------------------------
-- PulsoCare - Datos de prueba: usuarios y equipo de cuidado (semana 5)
--------------------------------------------------------------------------------
-- Crea los destinatarios de las notificaciones (medico y familiar) y los asigna
-- a los pacientes. El notification-service usa PC_ASIGNACION_CUIDADO para saber
-- a quien avisar cuando se genera una alerta.
--   PC_ROL: 1=Medico, 2=Enfermero, 3=Familiar, 4=Administrador
--   PC_PARENTESCO: 1=Hijo/a, 2=Conyuge, 3=Hermano/a, 4=Padre/Madre, 5=Otro
--------------------------------------------------------------------------------

-- 1) Medico (atiende a ambos pacientes)
INSERT INTO PC_USUARIO (ID_ROL, NOMBRE, APELLIDO_PATERNO, APELLIDO_MATERNO,
                        CORREO, TELEFONO, ESTADO)
VALUES (1, 'Carlos', 'Valverde', 'Rojas',
        'carlos.valverde@pulsocare.cl', '+56912345678', 'ACTIVO');

-- 2) Familiar de Rosa (hija)
INSERT INTO PC_USUARIO (ID_ROL, ID_PARENTESCO, NOMBRE, APELLIDO_PATERNO, APELLIDO_MATERNO,
                        CORREO, TELEFONO, ESTADO)
VALUES (3, 1, 'Maria', 'Fuentealba', 'Perez',
        'maria.fuentealba@gmail.com', '+56987654321', 'ACTIVO');

-- 3) Asignaciones de cuidado (quien cuida a quien)
--    Medico -> paciente 1 (Rosa) y paciente 2 (Pedro)
INSERT INTO PC_ASIGNACION_CUIDADO (ID_USUARIO, ID_PACIENTE, FECHA_INICIO, ACTIVO)
SELECT u.ID_USUARIO, 1, SYSDATE, 1 FROM PC_USUARIO u WHERE u.CORREO='carlos.valverde@pulsocare.cl';
INSERT INTO PC_ASIGNACION_CUIDADO (ID_USUARIO, ID_PACIENTE, FECHA_INICIO, ACTIVO)
SELECT u.ID_USUARIO, 2, SYSDATE, 1 FROM PC_USUARIO u WHERE u.CORREO='carlos.valverde@pulsocare.cl';

--    Familiar Maria -> paciente 1 (Rosa)
INSERT INTO PC_ASIGNACION_CUIDADO (ID_USUARIO, ID_PACIENTE, FECHA_INICIO, ACTIVO)
SELECT u.ID_USUARIO, 1, SYSDATE, 1 FROM PC_USUARIO u WHERE u.CORREO='maria.fuentealba@gmail.com';

COMMIT;
