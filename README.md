# Proyecto Cloud Native

Proyecto organizado para una evaluacion Cloud Native. Incluye backend con microservicios Spring Boot, frontend separado, persistencia con JPA, configuracion de base de datos cloud y validacion de JWT emitido por un IDaaS como Azure AD/MSAL.

## Versiones usadas

- Java/JDK: 17
- Spring Boot: 3.5.16
- Maven: compatible con Maven 3.6.3 o superior
- Spring Cloud: 2025.0.3
- Base de datos objetivo: PostgreSQL cloud
- Base de datos local por defecto: H2 en memoria

Aunque Spring Boot 4.1.1 aparece como version estable actual en la documentacion oficial, se usa Spring Boot 3.5.16 porque mantiene compatibilidad madura con Java 17 y el ecosistema Spring Security 6 usado habitualmente en cursos Cloud Native.

## Microservicios incluidos

| Microservicio | Carpeta | Puerto | Endpoint principal |
| --- | --- | --- | --- |
| Inventario | `backend/inventory-service` | 8080 | `/api/v1/products` |
| Clientes | `backend/customer-service` | 8081 | `/api/v1/customers` |
| Proveedores | `backend/supplier-service` | 8082 | `/api/v1/suppliers` |
| Notificaciones | `backend/notification-service` | 8083 | `/api/v1/notifications` |
| Eureka Server | `backend/eureka-server` | 8761 | `/` |
| BFF/API Gateway | `backend/api-gateway` | 8085 | `/api/v1/**` |

El microservicio de inventario es el principal y tiene seguridad JWT mas completa, incluyendo validacion de audience y lectura de scopes/roles. Los otros tres microservicios son mas sencillos: tienen JPA, H2 local, datos iniciales de ejemplo y proteccion JWT como Resource Server.

Eureka Server registra los microservicios y el BFF/API Gateway enruta las llamadas del frontend hacia cada servicio usando service discovery.

## Frontend

El frontend principal quedo ubicado en:

```text
frontend/angular-app-cloud
```

Este frontend esta construido con Angular y MSAL. El proyecto anterior React + Vite quedo como respaldo en:

```text
frontend/react-app-backup
```

La configuracion de Azure AD/MSAL esta en:

```text
frontend/angular-app-cloud/src/environments/environment.ts
frontend/angular-app-cloud/src/environments/environment.prod.ts
```

Valores que se deben reemplazar al crear la App Registration en Microsoft Entra ID:

```text
REEMPLAZAR_CLIENT_ID
REEMPLAZAR_TENANT_ID
REEMPLAZAR_DOMINIO_FRONTEND
REEMPLAZAR_API_GATEWAY
```

El frontend solicita los scopes:

```text
api://0f18b5b0-285b-4050-a959-245884ab7670/inventory.read
api://0f18b5b0-285b-4050-a959-245884ab7670/inventory.write
```

## Ejecutar con Docker Compose

Con Docker Desktop abierto, se puede levantar todo con un solo comando desde la raiz del proyecto:

```powershell
docker compose up --build
```

Entradas principales:

```text
Frontend Angular: http://localhost:4200
BFF/API Gateway: http://localhost:8085
Eureka Server:    http://localhost:8761
```

Para detener todo:

```powershell
docker compose down
```

## Ejecutar localmente sin Docker

Eureka Server:

```powershell
cd backend/eureka-server
mvn.cmd spring-boot:run
```

Microservicio de inventario:

```powershell
cd backend/inventory-service
mvn.cmd spring-boot:run
```

Microservicio de clientes:

```powershell
cd backend/customer-service
mvn.cmd spring-boot:run
```

Microservicio de proveedores:

```powershell
cd backend/supplier-service
mvn.cmd spring-boot:run
```

Microservicio de notificaciones:

```powershell
cd backend/notification-service
mvn.cmd spring-boot:run
```

BFF/API Gateway:

```powershell
cd backend/api-gateway
mvn.cmd spring-boot:run
```

Frontend:

```powershell
cd frontend/angular-app-cloud
npm.cmd install
npm.cmd start
```

El frontend consume el BFF/API Gateway:

```text
http://localhost:8085/api/v1/products
http://localhost:8085/api/v1/customers
http://localhost:8085/api/v1/suppliers
http://localhost:8085/api/v1/notifications
```

Health check publico:

```text
GET /actuator/health
```

Endpoints protegidos:

```text
GET    /api/v1/products
GET    /api/v1/products/{id}
POST   /api/v1/products
PUT    /api/v1/products/{id}
DELETE /api/v1/products/{id}
```

## Configuracion de base de datos

La base queda configurada de inmediato. Para desarrollo local se usa H2 automaticamente. Para AWS RDS PostgreSQL, la configuracion recomendada es una base compartida `cloud_native` y variables comunes en la EC2 de servicios:

```powershell
$env:RDS_JDBC_URL="jdbc:postgresql://ENDPOINT_RDS:5432/cloud_native"
$env:RDS_USERNAME="USUARIO"
$env:RDS_PASSWORD="PASSWORD"
$env:RDS_DRIVER="org.postgresql.Driver"
$env:JPA_DDL_AUTO="update"
```

`docker-compose.services.yml` propaga esas variables a inventario, clientes, proveedores y notificaciones. Si se necesitan bases separadas, cada microservicio mantiene variables propias:

```text
DB_URL, DB_USERNAME, DB_PASSWORD, DB_DRIVER
CUSTOMER_DB_URL, CUSTOMER_DB_USERNAME, CUSTOMER_DB_PASSWORD
SUPPLIER_DB_URL, SUPPLIER_DB_USERNAME, SUPPLIER_DB_PASSWORD
NOTIFICATION_DB_URL, NOTIFICATION_DB_USERNAME, NOTIFICATION_DB_PASSWORD
```

Para RDS en AWS, usar PostgreSQL Single-AZ `db.t3.micro` o `db.t4g.micro` si esta disponible en capa gratuita/creditos, sin acceso publico, y permitir el puerto `5432` solo desde el security group de la EC2 de servicios.

## Variables listas para Azure y AWS

Se agregaron plantillas de variables en:

```text
env/backend-local.env.example
env/aws-production.env.example
env/azure-frontend-values.example
```

Tambien hay una guia corta en:

```text
docs/AZURE_AWS_VARIABLES.md
```

No pongas secretos reales en GitHub. Usa estos archivos como referencia y configura los valores reales en Azure, AWS, EC2, ECS, API Gateway o el servicio donde publiques el frontend.

## Evidencias para la entrega

La guia de rutas, respuestas JSON, CORS, JWT y capturas recomendadas esta en:

```text
docs/EVIDENCIAS_RUTAS.md
```

La guia de Azure AD, MSAL, scopes, roles y validacion JWT esta en:

```text
docs/EVIDENCIAS_AZURE_MSAL_JWT.md
```

El plan recomendado para desplegar en AWS esta en:

```text
docs/PLAN_DESPLIEGUE_AWS.md
```

## Configuracion JWT / Azure AD

El backend valida firma, issuer, expiracion y audience del token usando Spring Security Resource Server.

Variables recomendadas:

```powershell
$env:JWT_ISSUER_URI="https://login.microsoftonline.com/TENANT_ID/v2.0"
$env:JWT_AUDIENCES="api://0f18b5b0-285b-4050-a959-245884ab7670,0f18b5b0-285b-4050-a959-245884ab7670"
```

Permisos esperados:

- Lectura: scope `inventory.read` o rol `ADMIN`
- Escritura: scope `inventory.write` o rol `ADMIN`

## Pruebas

```powershell
cd backend/inventory-service
mvn test
```

## Entrega GitHub

Sube al repositorio las carpetas `backend`, `frontend`, `README.md`, `.gitignore` y el PDF si el docente lo solicita. No subas `target`, `node_modules`, `dist`, archivos `.env`, logs ni configuraciones locales del IDE.
