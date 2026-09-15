# Evidencias Azure AD, MSAL, roles y JWT

Este documento resume la configuracion de Microsoft Entra ID, MSAL en Angular y validacion JWT en el backend. Sirve como evidencia para los puntos de rubrica asociados a tenant, flujo de usuario, Authorization Code con PKCE, scopes, roles y proteccion de rutas.

## Tenant Microsoft Entra ID

| Elemento | Valor |
| --- | --- |
| Nombre del tenant | `Entra02` |
| Tenant ID | `94b15b07-9bad-4661-8554-aad0e62b18de` |
| Dominio principal | `TestingEntra.onmicrosoft.com` |
| Usuario de prueba | `MATIAS IMIL GONZALEZ` |

Evidencia recomendada: captura de la pantalla de informacion general del tenant donde se vea el nombre, dominio e ID del inquilino.

## Aplicacion frontend

| Elemento | Valor |
| --- | --- |
| App Registration | `frontend-angular-cloud` |
| Client ID | `509a3118-7042-47ce-9c36-f7d7f5fda558` |
| Redirect URI local | `http://localhost:4200` |
| Flujo usado | OIDC Authorization Code con PKCE mediante MSAL |

Configuracion usada por Angular:

```text
clientId: 509a3118-7042-47ce-9c36-f7d7f5fda558
authority: https://login.microsoftonline.com/94b15b07-9bad-4661-8554-aad0e62b18de
redirectUri: http://localhost:4200
```

Evidencia recomendada:

- Captura de Redirect URIs del frontend.
- Captura del inicio de sesion funcionando.
- Captura del cierre de sesion funcionando.

## Aplicacion backend/API

| Elemento | Valor |
| --- | --- |
| App Registration | `backend-inventory-api` |
| Client ID | `0f18b5b0-285b-4050-a959-245884ab7670` |
| Application ID URI | `api://0f18b5b0-285b-4050-a959-245884ab7670` |

Scopes expuestos por la API:

```text
inventory.read
inventory.write
```

Scopes solicitados por el frontend:

```text
api://0f18b5b0-285b-4050-a959-245884ab7670/inventory.read
api://0f18b5b0-285b-4050-a959-245884ab7670/inventory.write
```

Evidencia recomendada:

- Captura de `Expose an API` con los scopes creados.
- Captura de permisos API en la aplicacion frontend.
- Captura del token o panel de seguridad mostrando `inventory.read` e `inventory.write`.

## Roles de aplicacion

Roles creados en la aplicacion backend:

| Rol | Valor | Estado |
| --- | --- | --- |
| `ADMIN` | `ADMIN` | Habilitado |
| `USER` | `USER` | Habilitado |

Asignacion realizada:

| Usuario | Rol asignado |
| --- | --- |
| `MATIAS IMIL GONZALEZ` | `ADMIN` |

Evidencia recomendada:

- Captura de App roles mostrando `ADMIN` y `USER`.
- Captura de Enterprise applications > Users and groups mostrando el usuario con rol `ADMIN`.
- Captura del frontend mostrando `ROLES: ADMIN`.

## MSAL en Angular

El frontend usa MSAL Angular para iniciar sesion, proteger rutas y adjuntar el access token automaticamente a las llamadas contra el backend.

Configuracion principal:

```text
cacheLocation: SessionStorage
interactionType: Redirect
protectedResourceMap: /api/ -> scopes inventory.read, inventory.write
```

La ruta `/panel` queda protegida por `MsalGuard`, por lo que el usuario debe iniciar sesion antes de entrar al panel operativo.

El `MsalInterceptor` adjunta el token a las llamadas del frontend hacia:

```text
/api/v1/products
/api/v1/customers
/api/v1/suppliers
/api/v1/notifications
```

Evidencia recomendada:

- Captura de la app en `/panel` luego de iniciar sesion.
- Captura de DevTools Network mostrando una llamada `/api/v1/**` con encabezado `Authorization: Bearer ...`.
- No se debe mostrar el token completo en la entrega; basta con evidenciar que el encabezado existe.

## Claims validados

Claims observados en el token despues de asignar el rol:

```text
tenant: 94b15b07-9bad-4661-8554-aad0e62b18de
aud: api://0f18b5b0-285b-4050-a959-245884ab7670
scp: inventory.read inventory.write
roles: ADMIN
```

Estos claims demuestran que:

- El token pertenece al tenant correcto.
- El token fue emitido para la API correcta.
- El usuario tiene permisos delegados de lectura y escritura.
- El usuario tiene rol de aplicacion `ADMIN`.

## Validacion JWT en API Gateway

El API Gateway valida:

| Validacion | Estado |
| --- | --- |
| Firma del token | Configurada mediante Microsoft Entra ID |
| Expiracion | Validada por Spring Security |
| Issuer | Validado contra issuers permitidos |
| Audience | Validado contra audiences permitidos |
| Rutas `/api/v1/**` | Requieren autenticacion |
| `OPTIONS` | Permitido para CORS/preflight |
| `/actuator/health` | Publico para health check |

Issuers permitidos:

```text
https://login.microsoftonline.com/94b15b07-9bad-4661-8554-aad0e62b18de/v2.0
https://sts.windows.net/94b15b07-9bad-4661-8554-aad0e62b18de/
```

Audiences permitidos:

```text
api://0f18b5b0-285b-4050-a959-245884ab7670
0f18b5b0-285b-4050-a959-245884ab7670
```

## Comportamiento esperado

| Escenario | Resultado esperado |
| --- | --- |
| Usuario no autenticado intenta entrar a `/panel` | Redireccion a login |
| Usuario autenticado entra a `/panel` | Acceso permitido |
| Frontend llama `/api/v1/**` con token valido | Respuesta funcional `200`, `201` o `204` |
| Llamada directa sin token a `/api/v1/**` | `401 Unauthorized` |
| Token con audience incorrecta | `401 Unauthorized` |
| Token con issuer incorrecto | `401 Unauthorized` |

## Checklist de capturas para la entrega

1. Tenant `Entra02` con Tenant ID visible.
2. App Registration frontend con Redirect URI `http://localhost:4200`.
3. App Registration backend con Application ID URI y scopes.
4. App roles `ADMIN` y `USER` habilitados.
5. Enterprise application con `MATIAS IMIL GONZALEZ` asignado a `ADMIN`.
6. Frontend `/panel` mostrando usuario autenticado.
7. Bloque de seguridad mostrando tenant, scopes y rol `ADMIN`.
8. DevTools Network con llamada `/api/v1/**` y encabezado Authorization.
9. Prueba de endpoint protegido sin token devolviendo `401`.

## Conclusion para la rubrica

El sistema implementa autenticacion con Microsoft Entra ID mediante MSAL, protege el panel con guard, obtiene access tokens para consumir el API Gateway, adjunta tokens automaticamente con el interceptor y valida JWT en el backend. Ademas, el tenant incluye scopes y roles de aplicacion, con el usuario de prueba asignado al rol `ADMIN`.
