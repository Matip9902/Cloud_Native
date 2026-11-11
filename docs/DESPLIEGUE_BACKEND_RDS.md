# Backend EC2 con RDS y frontend en otra EC2

Esta variante mantiene Eureka, gateway y todos los microservicios en la EC2 backend. El frontend se despliega por separado. No combinar con los archivos antiguos `entry/services`.

Usar siempre el mismo proyecto Compose (`cloud-native-aws`) y, tras configurar RDS, los tres archivos en este orden:

```bash
docker compose -p cloud-native-aws -f docker-compose.yml -f docker-compose.kafka.yml -f docker-compose.aws-backend.yml config --quiet
```

## Preparación

Crear en RDS PostgreSQL la base `cloud_native` y los esquemas `inventory`, `customers`, `suppliers`, `notifications` y `audit`. El usuario configurado necesita permisos para crear tablas en ellos. En esta instalación académica se utiliza inicialmente el propietario de esos esquemas; para producción, separar usuarios y privilegios por servicio.

Descargar el CA bundle oficial de RDS y comprobar una conexión con `psql`, `sslmode=verify-full` y ese certificado. Copiar después el mismo certificado al directorio de despliegue:

```bash
mkdir -p infra/rds
cp ~/global-bundle.pem infra/rds/global-bundle.pem
python3 scripts/configure-rds.py
```

El asistente conserva las variables RabbitMQ existentes, solicita la contraseña sin mostrarla y actualiza `.env` con permisos 600. No subir `.env` ni publicar la salida de `docker compose config` sin `--quiet`, pues puede revelar contraseñas. El certificado público y `.env` están excluidos por `.gitignore`.

Las conexiones JDBC verifican certificado y hostname, y seleccionan un esquema por servicio. Cada pool tiene máximo cinco conexiones y una mínima, para reducir la carga sobre RDS micro. Todos los drivers PostgreSQL ya están incluidos. Analytics conserva su H2 persistente y el volumen `analytics_data`; este overlay no migra datos H2 anteriores a RDS. Las bases cloud nuevas comienzan con sus propios datos.

## Construcción y arranque gradual

Construir un servicio a la vez en la EC2 de 8 GiB:

```bash
docker compose -p cloud-native-aws -f docker-compose.yml -f docker-compose.kafka.yml -f docker-compose.aws-backend.yml build inventory-service
docker compose -p cloud-native-aws -f docker-compose.yml -f docker-compose.kafka.yml -f docker-compose.aws-backend.yml up -d inventory-service
```

Repetir gradualmente con los demás servicios y comprobar sus logs/estado. Los builds existentes omiten la ejecución de pruebas; la suite automatizada se ejecuta por separado. No levantar el frontend en este host. El perfil `frontend-local-only` lo excluye del arranque por defecto.

Este overlay limita los puertos de administración y microservicios a loopback. El gateway escucha en `BACKEND_PRIVATE_IP:8085`: permitir ese puerto en el Security Group solo desde el grupo de seguridad de la EC2 frontend. La conexión interna Nginx → gateway debe apuntar a esa IP privada. Mantener SSH restringido al operador y RDS privado con 5432 permitido solo desde el backend. Administrar dashboards mediante túneles SSH. El despliegue completo y las pruebas desde el frontend siguen siendo comprobaciones en AWS, no resultados deducibles de una validación estática.

Los servicios indicados reciben un heap Java máximo de 256 MiB como punto de partida para esta demo. Vigilar memoria y ajustar según el consumo; no garantiza que el stack entero o sus builds simultáneos quepan en 8 GiB. Comprobar disco con `df -h /` y `docker system df`, sin borrar volúmenes.

## Origen HTTPS del frontend y errores 403 al escribir

Configurar en el `.env` del backend el origen público exacto, sin ruta ni barra
final. El overlay lo propaga al gateway y a los servicios con base de datos:

```dotenv
CORS_ALLOWED_ORIGINS=https://100.53.214.225.sslip.io
```

Si se necesita también acceso local, separar los orígenes con coma, sin espacios.
No utilizar `*` con credenciales. Conservar las demás variables y secretos del
`.env`. Después recrear solamente los contenedores afectados, sin rebuild:

```bash
docker compose -p cloud-native-aws \
  -f docker-compose.yml -f docker-compose.kafka.yml \
  -f docker-compose.aws-backend.yml \
  up -d --no-deps api-gateway inventory-service customer-service supplier-service notification-service
```

Esperar a que sus endpoints de salud vuelvan a responder `UP`. El cambio de
variables requiere recrear contenedores; `restart` solo no lo aplica.

Para comprobar CORS sin compartir un token ni crear productos:

```bash
curl -i -X OPTIONS http://172.31.39.224:8085/api/v1/products \
  -H 'Origin: https://100.53.214.225.sslip.io' \
  -H 'Access-Control-Request-Method: POST' \
  -H 'Access-Control-Request-Headers: authorization,content-type'
```

Ejecutar desde una EC2 autorizada. Debe devolver 2xx y
`Access-Control-Allow-Origin: https://100.53.214.225.sslip.io`.
Una prueba con un origen ajeno debe devolver 403. Esta prueba verifica el filtro
CORS del gateway; confirmar también la escritura real iniciando sesión en la web.

El proxy reenvía `Origin` y la conexión interna es HTTP. Aunque el navegador use
un solo origen HTTPS, Spring puede evaluar esa cabecera como origen cruzado.
Un GET sin `Origin` puede funcionar mientras un POST del navegador resulta
rechazado por CORS. No atribuir automáticamente todo 403 a roles del usuario;
revisar el cuerpo de la respuesta y los logs. Las verificaciones JWT y de
`inventory.write` o rol `ADMIN` se mantienen activas.
