> Actualización: los accesos principales son https://3.228.191.162, https://3.228.191.162/kafka/ y https://3.228.191.162/rabbit/. Consultar [PANELES_AWS.md](PANELES_AWS.md) para la configuración vigente. Los ejemplos anteriores con sslip.io se conservan como referencia.

# Frontend en una EC2 separada

Este despliegue independiente sirve Angular con Nginx detrás de Caddy. Caddy
obtiene y renueva el certificado HTTPS y reenvía `/api/*`, conservando la ruta y
el token Authorization, al Spring Cloud Gateway por su IP privada. El navegador
usa un único origen HTTPS. No necesita acceder públicamente al puerto 8085.

Requisitos: Docker y Compose, ambas EC2 en la misma VPC, puertos públicos 80/443
en el frontend y puerto 8085 del backend permitido desde el grupo de seguridad
del frontend. SSH debe quedar limitado a la IP del administrador.

## Configuración en la EC2 del frontend

El dominio debe resolver a la IP pública de esta EC2. Con dominio propio, crear
un registro A. Para una demostración sin dominio propio se puede usar
`100.53.214.225.sslip.io`, cuyo DNS lo proporciona un tercero. Verificar que
resuelve con `getent ahostsv4 100.53.214.225.sslip.io`; la emisión del certificado
depende de la disponibilidad del DNS y de los límites del emisor ACME.

```bash
cd ~/Cloud_Native
git pull origin main
cat > .env <<'ENV'
FRONTEND_DOMAIN=100.53.214.225.sslip.io
BACKEND_UPSTREAM=172.31.39.224:8085
ENV

docker compose -p cloud-native-frontend -f docker-compose.aws-frontend.yml config --quiet
docker compose -p cloud-native-frontend -f docker-compose.aws-frontend.yml up -d --build
```

Crear este `.env` solamente en la EC2 del frontend; no sobrescribir el `.env`
del backend, que contiene las credenciales de RDS y RabbitMQ. El Compose del
frontend se usa solo, sin los archivos Compose del backend.

## Microsoft Entra ID

En **Registros de aplicaciones → frontend-angular-cloud → Autenticación**, añadir
como URI de redirección de **Aplicación de página única (SPA)**:

```text
https://100.53.214.225.sslip.io
```

Conservar los URI locales que se sigan utilizando. La compilación de producción
usa `window.location.origin` y rutas relativas `/api/...`.

## Comprobación

```bash
docker compose -p cloud-native-frontend -f docker-compose.aws-frontend.yml ps
docker compose -p cloud-native-frontend -f docker-compose.aws-frontend.yml logs --tail=60 caddy
curl --fail https://100.53.214.225.sslip.io/
```

Abrir esa dirección HTTPS, iniciar sesión y verificar productos, clientes,
proveedores, notificaciones, auditoría y estadísticas Kafka. Una respuesta
401/403 en una API protegida sin token no significa que el proxy haya fallado.
Un 502 requiere revisar el gateway y su conectividad privada.

No eliminar el volumen `caddy_data`: guarda la cuenta ACME y los certificados.
Si cambia la IP pública al detener/iniciar la EC2, actualizar el dominio (o usar
una IP elástica), `.env` y la redirección de Entra, y recrear Caddy. No borrar los
volúmenes para solicitar certificados repetidamente.

Este archivo configura Caddy y Spring Cloud Gateway; no crea Amazon API Gateway.
