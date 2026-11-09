# Arranque seguro y recuperación de RabbitMQ 4.2

## Defecto corregido

El Compose local ejecutaba `stop_app`, `reset`, `join_cluster` y `start_app` en un hook `post_start` del segundo nodo. Se repetía al iniciar una instalación existente. Detener y resetear un miembro de un clúster Khepri de dos nodos puede dejar los metadatos sin mayoría y bloquear ambos arranques.

El hook se eliminó. Los dos nodos montan `infra/rabbitmq/cluster.conf` y utilizan el descubrimiento nativo `classic_config`. Los directorios nuevos forman el clúster; los existentes conservan su membresía persistida. El segundo contenedor espera al inicio del primero, no a que toda su aplicación termine de arrancar: ambos deben poder iniciarse para recuperar la mayoría. Los healthchecks verifican `check_running`, no solo que responda la VM Erlang.

Esto previene el reset repetido. No repara automáticamente un almacenamiento Khepri que ya quedó bloqueado.

## Proyecto Compose correcto

Si aparece un conflicto por `eureka-server` u otro nombre de contenedor, consultar su propietario antes de crear otro proyecto:

```sh
docker inspect eureka-server --format '{{ index .Config.Labels "com.docker.compose.project" }}'
```

Los ejemplos siguientes corresponden a la instalación diagnosticada `github_upload`. En otra instalación, sustituir ese nombre por el obtenido. Cambiar el nombre de proyecto también cambia los volúmenes predeterminados.

## Antes de recuperar datos existentes

Conservar ambos nodos detenidos durante la copia. En Git Bash:

```sh
docker stop rabbitmq-2 rabbitmq-1
mkdir -p ../respaldo-rabbitmq-20261109/nodo1 ../respaldo-rabbitmq-20261109/nodo2
docker cp rabbitmq-1:/var/lib/rabbitmq ../respaldo-rabbitmq-20261109/nodo1/
docker cp rabbitmq-2:/var/lib/rabbitmq ../respaldo-rabbitmq-20261109/nodo2/
```

Confirmar que ambas copias terminaron sin errores. Los respaldos pueden contener cookies de Erlang, credenciales y mensajes: mantenerlos locales y fuera de Git. No eliminarlos al terminar la recuperación.

## Recuperación del nodo 1 en una minoría Khepri confirmada

Solo después de respaldar ambos nodos y elegir el nodo 1 como origen de recuperación:

```sh
docker start rabbitmq-1
```

Esperar unos segundos hasta que responda la VM Erlang y ejecutar:

```sh
docker exec rabbitmq-1 rabbitmqctl force_standalone_khepri_boot
docker exec rabbitmq-1 rabbitmq-diagnostics check_running
```

`force_standalone_khepri_boot` conserva los metadatos locales, pero reduce la membresía del nodo recuperado a sí mismo. No es un comando para reinicios normales ni garantiza recuperar datos que solo existan en otro nodo. Si la aplicación aún está arrancando, esperar y repetir únicamente el diagnóstico. No utilizar `force_boot` de Mnesia como sustituto: la implementación Khepri no lo soporta para este caso.

## Reincorporar el segundo nodo conservando su volumen anterior

Actualizar el repositorio y añadir o actualizar esta variable en `.env` (sin cambiar otras variables):

```text
RABBITMQ_NODE2_DATA_VOLUME=github_upload_rabbitmq_node2_recovered_data
```

El Compose conserva por defecto el nombre original del volumen. La variable es una opción explícita para usar almacenamiento nuevo del nodo 2. El volumen original `github_upload_rabbitmq_node2_data` sigue existiendo y no se elimina.

Crear el segundo nodo sin reiniciar todavía el primero:

```sh
git pull origin main
docker compose -p github_upload -f docker-compose.yml -f docker-compose.kafka.yml up -d --no-deps rabbitmq-2
```

Esperar el arranque y comprobar:

```sh
docker exec rabbitmq-2 rabbitmq-diagnostics check_running
docker exec rabbitmq-1 rabbitmqctl cluster_status
```

Deben aparecer los dos nodos en ejecución. Este procedimiento no recupera automáticamente mensajes que solo estuvieran en el almacenamiento antiguo del nodo 2: ese volumen y el respaldo se preservan para una recuperación específica si fuera necesaria.

Después, aplicar la configuración actual al resto de servicios:

```sh
docker compose -p github_upload -f docker-compose.yml -f docker-compose.kafka.yml up --build -d
```

Mantener la variable de volumen en `.env` para las siguientes ejecuciones y no ejecutar `down -v`. Revisar las colas, mensajes y el flujo de productos después de la recuperación.

## Validación realizada en el entorno de prueba

Con RabbitMQ 4.2 y contenedores/volúmenes desechables, separados de los datos existentes:

- Dos directorios nuevos formaron el clúster mediante descubrimiento declarativo, sin resets.
- Ambos nodos reiniciaron conservando un vhost de control `recovery_probe`.
- Se reprodujo el arranque del nodo 1 sin mayoría y se recuperó con `force_standalone_khepri_boot`.
- El vhost de control siguió presente.
- Un segundo nodo con almacenamiento nuevo se incorporó al primero; el volumen antiguo se preservó.
- Se comprobó un nuevo arranque conjunto del clúster recuperado.

Esta validación acredita formación, membresía y conservación del metadato de control. No prueba la integridad de los mensajes del almacenamiento del usuario. La revisión de sus colas y mensajes debe hacerse en su instalación.
