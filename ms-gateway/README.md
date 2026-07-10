# PulsoCare — ms-gateway (Spring Cloud Gateway)

Puerta de entrada única del backend. El frontend habla con **un solo host**
(`:8080`) y el gateway enruta cada ruta `/api/**` al microservicio que
corresponde. Además resuelve **CORS** para el dev server de Angular.

- **En local**: reemplaza al AWS API Gateway, para poder probar sin depender de AWS.
- **En AWS**: puede ir detrás del AWS API Gateway (que reenvía a este gateway en el EC2).

## Tabla de enrutamiento

| Ruta | Destino | Microservicio |
|---|---|---|
| `/api/auth/**` | `MS_AUTH_URI` | ms-auth (8082) |
| `/api/umbrales/**` | `MS_CONFIG_URI` | ms-config (8083) |
| `/api/alertas/**` | `MS_CONSULTAS_URI` | ms-consultas (8084) |
| `/api/pacientes/*/lecturas/**` | `MS_CONSULTAS_URI` | ms-consultas (8084) |
| `/api/usuarios/**` | `MS_PACIENTES_URI` | ms-pacientes (8081) |
| `/api/pacientes/**` | `MS_PACIENTES_URI` | ms-pacientes (8081) |

> El orden está pensado para que las rutas específicas (lecturas, alertas) ganen
> sobre la genérica de pacientes.

## Variables de entorno

| Variable | Default (local) | En docker-compose |
|---|---|---|
| `MS_AUTH_URI` | `http://localhost:8082` | `http://ms-auth:8082` |
| `MS_PACIENTES_URI` | `http://localhost:8081` | `http://ms-pacientes:8081` |
| `MS_CONFIG_URI` | `http://localhost:8083` | `http://ms-config:8083` |
| `MS_CONSULTAS_URI` | `http://localhost:8084` | `http://ms-consultas:8084` |
| `FRONTEND_ORIGIN` | `http://localhost:4200` | dominio del frontend |
| `SERVER_PORT` | `8080` | `8080` |

## Compilar y ejecutar
```bash
mvn -q package -DskipTests
java -jar target/ms-gateway-1.0.0.jar
# el frontend apunta su environment.apiUrl a http://localhost:8080/api
```

## Notas
- Es un servicio **reactivo** (WebFlux); no accede a la base de datos, solo enruta.
- No hace *rewrite* del path: el microservicio recibe la ruta completa (`/api/...`)
  tal como la definen sus controllers.
