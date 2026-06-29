# PulsoCare — ms-auth (Spring Boot)

Microservicio de **autenticación y gestión de usuarios** (cold path).

El login se realiza en el frontend contra **Azure Entra ID**, que captura el
*nombre a mostrar*, *correo* y *contraseña* y los envía a este servicio para
**registrar/sincronizar** al usuario en `PC_USUARIO` y para **autenticar**.

```
Frontend (Azure Entra ID) ──{displayName, correo, pass}──► ms-auth ──► PC_USUARIO
```

## Endpoints

| Método | Ruta | Descripción |
|---|---|---|
| `POST` | `/api/auth/registro` | Registra o sincroniza un usuario (idempotente por correo). Hashea la contraseña con BCrypt |
| `POST` | `/api/auth/login` | Autentica por correo + contraseña (401 si no coincide) |
| `GET` | `/api/auth/usuarios` | Lista usuarios |
| `GET` | `/api/auth/usuarios/{id}` | Obtiene un usuario |

Ejemplo de registro:
```bash
curl -X POST http://localhost:8082/api/auth/registro -H 'Content-Type: application/json' \
  -d '{"displayName":"Ana Torres","correo":"ana.torres@duocuc.cl","pass":"Secreta123","idRol":1}'
```
El `displayName` se separa en `NOMBRE` + `APELLIDO_PATERNO`. Si no se envía
`idRol`, se usa el rol por defecto (`pulsocare.auth.rol-por-defecto`, Familiar).
La contraseña nunca se devuelve; se guarda hasheada (BCrypt) en `HASH_CONTRASENA`.

## Variables de entorno
| Variable | Ejemplo |
|---|---|
| `DB_USER` | `ADMIN` |
| `DB_PASSWORD` | (contraseña de la BD) |
| `DB_DSN` | `bdpulsocaretads_low` |
| `WALLET_DIR` | ruta al wallet descomprimido (TNS_ADMIN) |
| `SERVER_PORT` | `8082` (opcional) |

> El wallet usa `cwallet.sso` (auto-login): el JDBC no necesita password de wallet.

## Compilar y ejecutar
```bash
mvn -q package -DskipTests
export DB_USER=ADMIN DB_PASSWORD='...' DB_DSN=bdpulsocaretads_low
export WALLET_DIR="/ruta/a/Wallet_bdPulsoCareTADS"
java -jar target/ms-auth-1.0.0.jar
```

## Nota sobre Azure Entra ID
La federación (emisión y firma del JWT) la hace Azure Entra ID en el frontend.
Este servicio recibe la identidad ya autenticada y mantiene el registro local del
usuario (incluyendo `ENTRA_OID` si se envía). La validación de la firma del JWT
corresponde al API Gateway.
