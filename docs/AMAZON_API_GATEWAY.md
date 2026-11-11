# Integración Amazon API Gateway HTTP API

Producción Angular usa `https://xwgnimeoa5.execute-api.us-east-1.amazonaws.com`
para todas las APIs, incluidas auditoría y métricas. Desarrollo mantiene las
rutas locales relativas. Cada solicitud adjunta el token de acceso Bearer.

Flujo: navegador → Amazon API Gateway → Caddy del frontend → Spring Cloud Gateway
privado → microservicios. Es una integración HTTP pública, sin VPC Link. Caddy
sigue accesible directamente; los permisos y JWT de Spring se mantienen activos.
Esta configuración no exige que toda solicitud pase exclusivamente por AWS.

## AWS y Entra

- API `cloud-native-api`, ID `xwgnimeoa5`, etapa `$default`, auto-deploy.
- `ANY /api/{proxy+}`: integración HTTP ANY
  `https://3.228.191.162.sslip.io/api/{proxy}`, autorizador `entra-jwt`.
- `OPTIONS /api/{proxy+}`: misma integración, sin autorizador.
- JWT identity source: `$request.header.Authorization`.
- Issuer: `https://login.microsoftonline.com/94b15b07-9bad-4661-8554-aad0e62b18de/v2.0`.
- Audience: `0f18b5b0-285b-4050-a959-245884ab7670`.
- CORS origin: `https://3.228.191.162.sslip.io`.
- Métodos: GET, POST, PUT, DELETE, PATCH, OPTIONS.
- Allow-Headers: Authorization, Content-Type; credentials false.
- Entra backend-inventory-api: `api.requestedAccessTokenVersion: 2`.
- Entra SPA redirect URI: `https://3.228.191.162.sslip.io`.
- Backend `.env`: `CORS_ALLOWED_ORIGINS=https://3.228.191.162.sslip.io`.

Los tokens nuevos son v2 con audiencia GUID sin `api://`; los scopes solicitados
siguen incluyendo el prefijo `api://` del recurso. Una sesión previa puede
conservar tokens v1: cerrar sesión y usar una ventana privada nueva.

## Actualizar en la EC2 frontend

```bash
cd ~/Cloud_Native
git pull origin main
docker compose -p cloud-native-frontend -f docker-compose.aws-frontend.yml build frontend
docker compose -p cloud-native-frontend -f docker-compose.aws-frontend.yml up -d --no-deps frontend
```

No cambiar el proxy `/api/*` de Caddy para apuntar al endpoint AWS: eso crearía
un bucle porque AWS integra con ese mismo proxy. Solo el navegador cambia de URL.

## Verificación

Ya se observó un OPTIONS 200 con el nuevo origen. Falta comprobar solicitudes
con JWT después de desplegar este frontend. En F12 → Network, GET products debe
usar `https://xwgnimeoa5.execute-api.us-east-1.amazonaws.com/api/v1/products`
y responder 200. Crear, editar y eliminar un producto de prueba; revisar
notificaciones, auditoría y métricas. El GET sin token debe responder 401.
No compartir Authorization, cookies ni archivos HAR sin sanitizarlos.

Ante 401 revisar issuer/aud/exp, scopes requeridos y asociación del autorizador.
Ante 403 revisar CORS y permisos Spring. Ante 502 revisar el certificado HTTPS,
el arranque del gateway y la conectividad privada. Un build exitoso no prueba
la integración JWT remota.
