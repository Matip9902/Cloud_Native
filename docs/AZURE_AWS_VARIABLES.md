# Variables para Azure y AWS

Este proyecto deja las variables separadas por responsabilidad:

- Azure AD / Microsoft Entra ID entrega identidad, login y tokens JWT.
- AWS ejecuta backend, API Gateway y base de datos PostgreSQL.
- Angular usa MSAL para pedir tokens y llamar a los endpoints protegidos.

## Azure AD

Valores que debes obtener desde Microsoft Entra ID:

```text
TENANT_ID=94b15b07-9bad-4661-8554-aad0e62b18de
CLIENT_ID=509a3118-7042-47ce-9c36-f7d7f5fda558
JWT_ISSUER_URI=https://login.microsoftonline.com/94b15b07-9bad-4661-8554-aad0e62b18de/v2.0
JWT_AUDIENCES=api://0f18b5b0-285b-4050-a959-245884ab7670,0f18b5b0-285b-4050-a959-245884ab7670
```

Scopes esperados:

```text
api://0f18b5b0-285b-4050-a959-245884ab7670/inventory.read
api://0f18b5b0-285b-4050-a959-245884ab7670/inventory.write
```

En Angular se reemplazan en:

```text
frontend/angular-app-cloud/src/environments/environment.ts
frontend/angular-app-cloud/src/environments/environment.prod.ts
```

## AWS

Variables principales para EC2, ECS o contenedores:

```text
DB_URL
DB_USERNAME
DB_PASSWORD
DB_DRIVER
JWT_ISSUER_URI
JWT_AUDIENCES
CORS_ALLOWED_ORIGINS
```

Si cada microservicio usa su propia base:

```text
CUSTOMER_DB_URL
CUSTOMER_DB_USERNAME
CUSTOMER_DB_PASSWORD
SUPPLIER_DB_URL
SUPPLIER_DB_USERNAME
SUPPLIER_DB_PASSWORD
NOTIFICATION_DB_URL
NOTIFICATION_DB_USERNAME
NOTIFICATION_DB_PASSWORD
```

Para produccion, `CORS_ALLOWED_ORIGINS` debe ser el dominio real del frontend, no `localhost`.

## Archivos de ejemplo

```text
env/backend-local.env.example
env/aws-production.env.example
env/azure-frontend-values.example
```

Estos archivos no contienen secretos reales. Sirven como plantilla para configurar Azure, AWS y el entorno local.
