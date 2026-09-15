# Evidencias de rutas, API Gateway y JWT

Este documento resume las rutas expuestas por el sistema, el microservicio que responde cada una y la evidencia esperada para demostrar el funcionamiento ante la rubrica.

## Arquitectura de llamadas

```text
Frontend Angular / Nginx
http://localhost:4200
        |
        | /api/v1/**
        v
BFF / API Gateway
http://localhost:8085
        |
        | Descubrimiento por Eureka
        v
Microservicios Spring Boot
INVENTORY-SERVICE      8080
CUSTOMER-SERVICE       8081
SUPPLIER-SERVICE       8082
NOTIFICATION-SERVICE   8083
```

El frontend no llama directamente a los microservicios. Todas las llamadas funcionales pasan por el API Gateway usando rutas `/api/v1/**`.

## Servicios registrados

Evidencia recomendada: abrir `http://localhost:8761` y capturar Eureka con estos servicios en estado `UP`.

| Aplicacion en Eureka | Puerto interno | Funcion |
| --- | ---: | --- |
| API-GATEWAY | 8085 | Entrada central del backend |
| INVENTORY-SERVICE | 8080 | Productos |
| CUSTOMER-SERVICE | 8081 | Clientes |
| SUPPLIER-SERVICE | 8082 | Proveedores |
| NOTIFICATION-SERVICE | 8083 | Notificaciones |

## Rutas publicadas por API Gateway

| Ruta externa | Metodo | Microservicio destino | Resultado esperado |
| --- | --- | --- | --- |
| `/api/v1/products` | GET | INVENTORY-SERVICE | `200 OK` con lista JSON |
| `/api/v1/products/{id}` | GET | INVENTORY-SERVICE | `200 OK` con un producto o `404` si no existe |
| `/api/v1/products` | POST | INVENTORY-SERVICE | `201 Created` o `200 OK` con producto creado |
| `/api/v1/products/{id}` | PUT | INVENTORY-SERVICE | `200 OK` con producto actualizado |
| `/api/v1/products/{id}` | DELETE | INVENTORY-SERVICE | `204 No Content` o respuesta exitosa equivalente |
| `/api/v1/customers` | GET | CUSTOMER-SERVICE | `200 OK` con lista JSON |
| `/api/v1/customers/{id}` | GET | CUSTOMER-SERVICE | `200 OK` con un cliente o `404` si no existe |
| `/api/v1/customers` | POST | CUSTOMER-SERVICE | `201 Created` o `200 OK` con cliente creado |
| `/api/v1/customers/{id}` | PUT | CUSTOMER-SERVICE | `200 OK` con cliente actualizado |
| `/api/v1/customers/{id}` | DELETE | CUSTOMER-SERVICE | `204 No Content` o respuesta exitosa equivalente |
| `/api/v1/suppliers` | GET | SUPPLIER-SERVICE | `200 OK` con lista JSON |
| `/api/v1/suppliers/{id}` | GET | SUPPLIER-SERVICE | `200 OK` con un proveedor o `404` si no existe |
| `/api/v1/suppliers` | POST | SUPPLIER-SERVICE | `201 Created` o `200 OK` con proveedor creado |
| `/api/v1/suppliers/{id}` | PUT | SUPPLIER-SERVICE | `200 OK` con proveedor actualizado |
| `/api/v1/suppliers/{id}` | DELETE | SUPPLIER-SERVICE | `204 No Content` o respuesta exitosa equivalente |
| `/api/v1/notifications` | GET | NOTIFICATION-SERVICE | `200 OK` con lista JSON |
| `/api/v1/notifications` | POST | NOTIFICATION-SERVICE | `201 Created` o `200 OK` con notificacion creada |
| `/api/v1/notifications/{id}` | DELETE | NOTIFICATION-SERVICE | `204 No Content` o respuesta exitosa equivalente |

## Seguridad JWT

El inicio de sesion se realiza en el frontend con MSAL. Luego Angular obtiene un access token y lo envia al API Gateway usando el encabezado:

```text
Authorization: Bearer <access_token>
```

El API Gateway y los microservicios validan el token emitido por Microsoft Entra ID.

Claims observados durante las pruebas:

```text
tenant: 94b15b07-9bad-4661-8554-aad0e62b18de
aud: api://0f18b5b0-285b-4050-a959-245884ab7670
scp: inventory.read inventory.write
roles: sin roles configurados
```

Resultado esperado:

| Caso | Resultado |
| --- | --- |
| Llamada sin token | `401 Unauthorized` |
| Token con audience incorrecta | `401 Unauthorized` |
| Token valido con scopes esperados | `200 OK`, `201 Created` o respuesta funcional segun la ruta |

## CORS

El API Gateway acepta el origen del frontend local:

```text
http://localhost:4200
```

En Docker, Nginx tambien reenvia `/api/` hacia el API Gateway, por lo que el navegador usa el mismo origen del frontend y se evitan bloqueos CORS en la demostracion.

Evidencia recomendada: abrir DevTools, pestana Network, seleccionar una llamada `/api/v1/**` y mostrar:

- URL llamada desde `http://localhost:4200`.
- Encabezado `Authorization: Bearer ...` presente.
- Respuesta `200`, `201` o `204`.
- Respuesta JSON esperada.

No es necesario mostrar ni copiar el token completo.

## JSON esperado por ruta

### Productos

```json
[
  {
    "id": 1,
    "name": "Mouse gamer",
    "category": "Tecnologia",
    "price": 10000,
    "stock": 5,
    "active": true,
    "createdAt": "2026-09-14T21:00:00-03:00"
  }
]
```

### Clientes

```json
[
  {
    "id": 1,
    "fullName": "Camila Rojas",
    "email": "camila.rojas@example.com",
    "phone": "+56 9 1111 2222"
  },
  {
    "id": 2,
    "fullName": "Diego Morales",
    "email": "diego.morales@example.com",
    "phone": "+56 9 3333 4444"
  }
]
```

### Proveedores

```json
[
  {
    "id": 1,
    "companyName": "TechParts Chile",
    "contactEmail": "ventas@techparts.example.com",
    "phone": "+56 2 2555 0101"
  },
  {
    "id": 2,
    "companyName": "Logistica Andina",
    "contactEmail": "contacto@logisticaandina.example.com",
    "phone": "+56 2 2666 0202"
  }
]
```

### Notificaciones

```json
[
  {
    "id": 1,
    "recipient": "inventario",
    "message": "Producto con stock bajo requiere revision.",
    "readStatus": false,
    "createdAt": "2026-09-14T21:00:00-03:00"
  },
  {
    "id": 2,
    "recipient": "operaciones",
    "message": "Nueva actualizacion disponible para catalogo de proveedores.",
    "readStatus": true,
    "createdAt": "2026-09-14T21:00:00-03:00"
  }
]
```

Las fechas pueden variar porque se generan al iniciar la aplicacion.

## Checklist de capturas para la entrega

1. Eureka en `http://localhost:8761` con API Gateway y los cuatro microservicios en estado `UP`.
2. Frontend en `http://localhost:4200/panel` con usuario autenticado.
3. Bloque de seguridad del frontend mostrando tenant, scopes y roles.
4. Pantalla Productos mostrando carga desde backend y creacion exitosa.
5. Pantalla Clientes mostrando datos cargados.
6. Pantalla Proveedores mostrando datos cargados.
7. Pantalla Notificaciones mostrando datos cargados.
8. DevTools Network con una llamada `/api/v1/**`, status exitoso y encabezado Authorization.
9. Una prueba sin token o con token invalido mostrando `401 Unauthorized`.

## Conclusion para la rubrica

El sistema demuestra un API Gateway como punto unico de entrada, rutas coherentes hacia microservicios registrados en Eureka, configuracion CORS funcional para el frontend, autenticacion con Microsoft Entra ID mediante MSAL y validacion JWT para proteger los endpoints del backend.
