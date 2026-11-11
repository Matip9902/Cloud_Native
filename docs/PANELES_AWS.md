# Accesos HTTPS del proyecto en AWS

## Enlaces principales

- Aplicación: https://3.228.191.162
- Kafka UI: https://3.228.191.162/kafka/
- RabbitMQ: https://3.228.191.162/rabbit/

Estos accesos se comprobaron desde la red de Duoc, con HTTPS,
sin túnel SSH y sin visitar sslip.io desde el navegador.

## Configuración

La IP elástica 3.228.191.162 está asociada al frontend.
Caddy usa default_sni y un certificado público ACME para la IP,
con el perfil shortlived de Let's Encrypt.

Caddy gestiona la renovación automática. Conservar sus volúmenes
y permitir las validaciones por los puertos 80/443.
La renovación futura no ha sido comprobada todavía.

Kafka UI utiliza SERVER_SERVLET_CONTEXT_PATH=/kafka y está protegido
por Basic Auth en Caddy. RabbitMQ usa management.path_prefix=/rabbit
y su autenticación propia.

Las contraseñas y sus hashes permanecen en los archivos .env de las
EC2. No subir .env, certificados privados ni contraseñas a GitHub.

## Backend

Utilizar conjuntamente estos cuatro archivos:

docker compose -p cloud-native-aws \
  -f docker-compose.yml \
  -f docker-compose.kafka.yml \
  -f docker-compose.aws-backend.yml \
  -f docker-compose.aws-dashboards.yml \
  up -d --no-build

El Security Group del backend permite 8085, 8090 y 15672 solamente
desde el grupo del frontend. Los puertos de los brokers no se
publican para acceso directo desde Internet.

## Frontend

Utilizar conjuntamente los archivos:

docker compose -p cloud-native-frontend \
  -f docker-compose.aws-frontend.yml \
  -f docker-compose.aws-frontend-dashboards.yml \
  up -d --no-build

No ejecutar configure-dashboards.py para cada arranque: sirve para
configurar o cambiar las credenciales de Kafka UI.

## Entra, CORS y API Gateway

La SPA registra https://3.228.191.162 como URI de redirección.
Amazon API Gateway permite ese origen en CORS, junto con los
métodos y las cabeceras Authorization y Content-Type.

El .env del backend configura CORS_ALLOWED_ORIGINS incluyendo
https://3.228.191.162. Cambiar variables requiere recrear los
contenedores afectados; un simple restart no las actualiza.

Angular sigue llamando a Amazon API Gateway. Su integración HTTP
actual conserva https://3.228.191.162.sslip.io/api/{proxy} entre
servidores. Los bloques sslip.io de Caddy se conservan por ese motivo;
el navegador de Duoc utiliza únicamente la IP para entrar a la web.

Los recursos AWS deben estar encendidos. Los servicios Spring pueden
tardar varios minutos en responder UP después del arranque.
