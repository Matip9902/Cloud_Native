# Paneles publicos para la presentacion

Enlaces tras completar la configuracion:

- Kafka UI: https://kafka.3.228.191.162.sslip.io (usuario/password nuevos).
- RabbitMQ: https://rabbit.3.228.191.162.sslip.io (login propio de RabbitMQ).

Caddy en el frontend emite certificados HTTPS para ambos nombres y usa la red
privada para conectar con los paneles del backend. Kafka UI exige Basic Auth
sobre HTTPS; la clave se guarda solo como hash bcrypt en el .env del frontend.
RabbitMQ mantiene su autenticacion nativa y sus credenciales del backend.
No se abren a Internet los puertos de Kafka, AMQP, Zookeeper ni los paneles HTTP.

## Backend

En el Security Group del backend, agregar TCP 8090 y TCP 15672 con origen
**grupo de seguridad del frontend** sg-0f0b63419bdf78f70. No usar 0.0.0.0/0.

En la EC2 backend 172.31.39.224:

```bash
cd ~/Cloud_Native
git pull origin main
docker compose -p cloud-native-aws \
  -f docker-compose.yml -f docker-compose.kafka.yml \
  -f docker-compose.aws-backend.yml -f docker-compose.aws-dashboards.yml \
  up -d --no-deps kafka-ui rabbitmq
```

Esto recrea el primer nodo RabbitMQ para modificar los puertos publicados. Sus
volumenes se conservan; puede interrumpir brevemente las conexiones y despues
los clientes deben reconectar. No ejecutar down -v. Confirmar cluster_status
y que la app vuelve a entregar notificaciones despues del cambio.

En posteriores comandos Compose del backend, incluir tambien el overlay de
paneles; omitirlo al recrear esos servicios vuelve a dejarlos solo en loopback.

## Frontend

```bash
cd ~/Cloud_Native
git pull origin main
python3 scripts/configure-dashboards.py
docker compose -p cloud-native-frontend \
  -f docker-compose.aws-frontend.yml -f docker-compose.aws-frontend-dashboards.yml \
  up -d --no-deps caddy
```

Guardar la nueva clave Kafka en el gestor de contrasenas, no en Git ni en el
chat. El asistente conserva las variables existentes del .env. No copiar el
.env del backend al frontend. En futuros comandos que recreen Caddy, incluir
ambos archivos. Los volumenes existentes de certificados se conservan.

## Verificacion

Desde el frontend comprobar la red privada:

```bash
curl -I --max-time 10 http://172.31.39.224:8090/
curl -I --max-time 10 http://172.31.39.224:15672/
```

Consultar logs Caddy con ambos archivos Compose si no se emiten certificados.
Kafka publico sin credenciales debe responder 401; con credenciales debe abrir
el panel. RabbitMQ debe mostrar su formulario de login por HTTPS. Verificar en
Kafka UI tres brokers, topicos y consumer groups; en RabbitMQ ambos nodos sin
particiones de red, colas y consumidores. Un 502 indica conectividad privada o
panel aun arrancando. Mantener las claves confidenciales al hacer capturas.

Esta configuracion permite administracion real, no solo lectura. Compartir las
credenciales unicamente con quienes deban administrar el proyecto. Las URLs
dependen del DNS sslip.io y de conservar la Elastic IP y los recursos AWS.
