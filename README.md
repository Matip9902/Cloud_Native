# Proyecto Cloud Native

Este proyecto corresponde a una aplicación web para administrar productos, clientes, proveedores y notificaciones. Está construido con una arquitectura de microservicios y fue desplegado utilizando servicios de AWS y autenticación de Microsoft Entra ID.

## Cómo funciona

El usuario inicia sesión desde el frontend Angular. Microsoft Entra ID entrega un token JWT y Angular lo envía en cada solicitud hacia la API.

Las solicitudes pasan por AWS API Gateway y luego llegan a Spring Cloud Gateway, que funciona como BFF y las dirige al microservicio correspondiente. Los servicios se registran en Eureka y guardan la información en PostgreSQL mediante Amazon RDS.

```text
Usuario
  -> Angular
  -> Microsoft Entra ID
  -> AWS API Gateway
  -> Spring Cloud Gateway
  -> Microservicios
  -> PostgreSQL en Amazon RDS
```

## Tecnologías utilizadas

- Angular y MSAL para el frontend y el inicio de sesión.
- Java 17 y Spring Boot para el backend.
- Spring Cloud Gateway como BFF.
- Eureka para el registro y descubrimiento de servicios.
- Spring Security para validar JWT, scopes y roles.
- JPA e Hibernate para el acceso a datos.
- PostgreSQL en Amazon RDS.
- Docker para ejecutar los componentes.
- RabbitMQ para eventos asincronos, auditoria y metricas.
- AWS EC2 y API Gateway para el despliegue.
- Microsoft Entra ID para autenticación y autorización.

## Servicios

| Servicio | Puerto | Ruta principal |
| --- | ---: | --- |
| Inventory Service | 8080 | `/api/v1/products` |
| Customer Service | 8081 | `/api/v1/customers` |
| Supplier Service | 8082 | `/api/v1/suppliers` |
| Notification Service | 8083 | `/api/v1/notifications` |
| Rabbit Admin Service | 8084 | `/queues`, `/exchanges`, `/bindings` |
| Spring Cloud Gateway | 8085 | `/api/v1/**` |
| Eureka Server | 8761 | `/` |
| RabbitMQ nodo 1 | 5672 / 15672 | AMQP / Dashboard |
| RabbitMQ nodo 2 | 5673 / 15673 | AMQP / Dashboard |
| Frontend Angular | 4200 | `/` |

## Estructura del proyecto

```text
backend/                    Microservicios, gateway y Eureka
frontend/angular-app-cloud Frontend principal en Angular
frontend/react-app-backup  Respaldo del frontend anterior
docs/                       Documentación y evidencias
env/                        Ejemplos de variables de entorno
infra/                      Configuración de infraestructura
```

## Levantar el proyecto con Docker

Desde la carpeta principal del proyecto:

```powershell
docker compose up --build
```

Luego se puede abrir:

- Frontend: `http://localhost:4200`
- Gateway: `http://localhost:8085`
- Eureka: `http://localhost:8761`
- RabbitMQ nodo 1: `http://localhost:15672`
- RabbitMQ nodo 2: `http://localhost:15673`

Las credenciales locales predeterminadas de RabbitMQ son `cloudnative` y
`cloudnative123`. Solo se usan para desarrollo local.

## Mensajeria RabbitMQ

Inventory Service publica eventos `PRODUCT_CREATED`, `PRODUCT_UPDATED` y
`PRODUCT_DELETED` en `product.events.exchange`. El exchange distribuye cada
evento a tres colas independientes:

```text
product.events.queue   -> crea notificaciones
product.audit.queue    -> registra auditoria
product.metrics.queue  -> registra metricas
```

Cada cola principal tiene su propia DLX y DLQ. Los consumidores confirman los
mensajes validos con ACK. Los mensajes no soportados se rechazan con NACK sin
requeue y terminan en la DLQ correspondiente.

Los dos nodos RabbitMQ forman un cluster local. Para comprobarlo:

```powershell
docker exec rabbitmq-1 rabbitmqctl cluster_status
```

Rabbit Admin Service permite crear y eliminar recursos mediante los endpoints
`/queues`, `/exchanges` y `/bindings`.

Para detener los contenedores:

```powershell
docker compose down
```

## Levantar los componentes por separado

Primero se inicia Eureka:

```powershell
cd backend/eureka-server
mvn.cmd spring-boot:run
```

Después se inicia cada microservicio desde su carpeta con el mismo comando:

```powershell
mvn.cmd spring-boot:run
```

Las carpetas de los servicios son:

```text
backend/inventory-service
backend/customer-service
backend/supplier-service
backend/notification-service
backend/api-gateway
```

Finalmente se inicia el frontend:

```powershell
cd frontend/angular-app-cloud
npm.cmd install
npm.cmd start
```

## Seguridad

El backend valida los tokens emitidos por Microsoft Entra ID. Las operaciones de lectura requieren el permiso `inventory.read` y las operaciones de escritura requieren `inventory.write` o el rol `ADMIN`.

El frontend usa el flujo Authorization Code con PKCE, por lo que no necesita guardar un secreto de cliente.

## Base de datos

En desarrollo local se puede utilizar H2. En AWS, los microservicios se conectan a una base PostgreSQL alojada en Amazon RDS.

Las credenciales y direcciones se configuran con variables de entorno. Los archivos dentro de `env/` sirven como ejemplo y no contienen contraseñas reales.

## Pruebas rápidas

El estado del gateway se puede revisar en:

```text
GET http://localhost:8085/actuator/health
```

Para ejecutar las pruebas de un servicio:

```powershell
cd backend/inventory-service
mvn.cmd test
```

## Documentación

En la carpeta `docs/` se encuentran las evidencias y explicaciones sobre las rutas, JWT, Microsoft Entra ID y el despliegue en AWS.

Los archivos `.env`, contraseñas, llaves privadas, carpetas `target`, `node_modules` y archivos generados no deben subirse al repositorio.

Para EC2, `RABBITMQ_ADDRESSES` debe contener las IP privadas o DNS internos de
los nodos. La contraseña y la cookie de Erlang deben configurarse como secretos
del entorno y nunca guardarse en GitHub.
