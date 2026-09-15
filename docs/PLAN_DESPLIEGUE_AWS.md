# Plan de despliegue en AWS

Este documento define la forma recomendada de subir el sistema a AWS para la entrega. La opcion propuesta prioriza simplicidad, evidencia clara y compatibilidad con el proyecto actual.

## Opcion recomendada

Para este proyecto conviene usar:

```text
AWS EC2 + Docker Compose + AWS RDS PostgreSQL
```

Motivo:

- El proyecto ya esta dockerizado.
- Se puede levantar frontend, Eureka, API Gateway y microservicios con un solo comando.
- Es facil mostrar evidencia con puertos, contenedores, Eureka y llamadas HTTP.
- RDS permite demostrar base de datos cloud sin rehacer el backend.
- Evita la complejidad extra de ECS, ECR, Load Balancer y pipelines si el tiempo de entrega es limitado.

## Arquitectura propuesta en AWS

```text
Usuario / Navegador
        |
        | HTTPS o HTTP segun configuracion final
        v
EC2 publica
Frontend Angular / Nginx
Puerto 80 o 4200
        |
        | /api/v1/**
        v
API Gateway Spring Cloud
Puerto 8085
        |
        | Eureka service discovery
        v
Microservicios Spring Boot
inventory-service       8080
customer-service        8081
supplier-service        8082
notification-service    8083
        |
        v
AWS RDS PostgreSQL
```

## Componentes a desplegar

| Componente | Servicio AWS recomendado | Estado actual |
| --- | --- | --- |
| Frontend Angular | EC2 con Nginx en Docker | Dockerizado |
| API Gateway | EC2 con Docker Compose | Dockerizado |
| Eureka Server | EC2 con Docker Compose | Dockerizado |
| Inventory Service | EC2 con Docker Compose | Dockerizado |
| Customer Service | EC2 con Docker Compose | Dockerizado |
| Supplier Service | EC2 con Docker Compose | Dockerizado |
| Notification Service | EC2 con Docker Compose | Dockerizado |
| Base de datos | RDS PostgreSQL | Variables preparadas |

## Puertos necesarios

Para una demo simple:

| Puerto | Uso | Exposicion recomendada |
| ---: | --- | --- |
| 80 | Frontend Angular via Nginx | Publico |
| 4200 | Frontend local/demo alternativa | Publico solo si se usa |
| 8085 | API Gateway | Publico para pruebas, idealmente restringido despues |
| 8761 | Eureka | Solo para evidencia, idealmente restringido |
| 8080 | Inventory Service | No publico |
| 8081 | Customer Service | No publico |
| 8082 | Supplier Service | No publico |
| 8083 | Notification Service | No publico |
| 5432 | RDS PostgreSQL | Solo accesible desde EC2 |

Para la entrega, se puede mostrar Eureka temporalmente. En un entorno mas seguro, Eureka y los microservicios internos no se exponen a internet.

## Variables necesarias

En EC2 se deben configurar variables como estas:

```text
EUREKA_CLIENT_SERVICE_URL_DEFAULTZONE=http://eureka-server:8761/eureka/

JWT_ISSUER_URI=https://login.microsoftonline.com/94b15b07-9bad-4661-8554-aad0e62b18de/v2.0
JWT_AUDIENCES=api://0f18b5b0-285b-4050-a959-245884ab7670,0f18b5b0-285b-4050-a959-245884ab7670

CORS_ALLOWED_ORIGINS=http://DOMINIO_O_IP_PUBLICA

DB_URL=jdbc:postgresql://ENDPOINT_RDS:5432/inventorydb
DB_USERNAME=USUARIO_RDS
DB_PASSWORD=PASSWORD_RDS
DB_DRIVER=org.postgresql.Driver
JPA_DDL_AUTO=update

CUSTOMER_DB_URL=jdbc:postgresql://ENDPOINT_RDS:5432/customerdb
CUSTOMER_DB_USERNAME=USUARIO_RDS
CUSTOMER_DB_PASSWORD=PASSWORD_RDS

SUPPLIER_DB_URL=jdbc:postgresql://ENDPOINT_RDS:5432/supplierdb
SUPPLIER_DB_USERNAME=USUARIO_RDS
SUPPLIER_DB_PASSWORD=PASSWORD_RDS

NOTIFICATION_DB_URL=jdbc:postgresql://ENDPOINT_RDS:5432/notificationdb
NOTIFICATION_DB_USERNAME=USUARIO_RDS
NOTIFICATION_DB_PASSWORD=PASSWORD_RDS
```

Los valores reales no deben subirse al repositorio.

## Cambios necesarios en Azure para AWS

Cuando el frontend ya tenga dominio o IP publica en AWS, hay que agregar esa URL en la App Registration del frontend:

```text
http://IP_PUBLICA_EC2
https://DOMINIO_FINAL
```

Tambien se debe actualizar el frontend productivo:

```text
redirectUri: dominio o IP publica del frontend
```

Y en backend/API Gateway:

```text
CORS_ALLOWED_ORIGINS=dominio o IP publica del frontend
```

## Pasos sugeridos

1. Crear una instancia EC2.
2. Instalar Docker y Docker Compose en EC2.
3. Crear una base AWS RDS PostgreSQL.
4. Crear las bases:
   - `inventorydb`
   - `customerdb`
   - `supplierdb`
   - `notificationdb`
5. Subir el proyecto a EC2 desde GitHub.
6. Crear archivo `.env` productivo en EC2 con las variables reales.
7. Ejecutar `docker compose up --build -d`.
8. Revisar contenedores activos.
9. Abrir Eureka y comprobar servicios `UP`.
10. Entrar al frontend publicado.
11. Iniciar sesion con Microsoft Entra ID.
12. Probar Productos, Clientes, Proveedores y Notificaciones.
13. Tomar capturas para evidencia.

## Evidencias para la rubrica

Capturas recomendadas:

1. EC2 corriendo.
2. Security Group con puertos usados.
3. RDS PostgreSQL creado.
4. Docker Compose con contenedores activos.
5. Eureka con API Gateway y microservicios registrados.
6. Frontend publicado en IP/dominio AWS.
7. Login con Microsoft Entra ID.
8. Panel mostrando tenant, scopes y rol `ADMIN`.
9. Productos cargando desde backend.
10. Clientes, proveedores y notificaciones cargando desde backend.
11. Prueba de endpoint protegido sin token con `401 Unauthorized`.

## Que queda pendiente para produccion real

Para una entrega academica, EC2 + Docker Compose + RDS es suficiente y defendible.

Para una produccion mas formal se recomendaria:

- Dominio propio.
- HTTPS con certificado.
- Load Balancer.
- ECS/Fargate o EKS.
- Secrets Manager para credenciales.
- CloudWatch Logs.
- CI/CD desde GitHub Actions.
- Restringir puertos internos y Eureka.

## Conclusion para la entrega

La estrategia recomendada permite demostrar despliegue cloud, contenedores, API Gateway, microservicios, discovery con Eureka, base de datos PostgreSQL en AWS y autenticacion con Microsoft Entra ID usando tokens JWT.
