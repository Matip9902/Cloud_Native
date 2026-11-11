# ZooKeeper: tres nodos y migración desde standalone

El Compose Kafka declara `zookeeper` (ID 1), `zookeeper-2` (ID 2) y
`zookeeper-3` (ID 3), cada uno con volúmenes de datos y logs independientes.
Los tres usan el mismo conjunto `ZOOKEEPER_SERVERS`; los brokers y Kafka UI
usan los tres puertos cliente internos. No se abren puertos nuevos en la EC2.
El primer nodo conserva los volúmenes existentes `zookeeper_data` y
`zookeeper_log`. Los brokers mantienen sus volúmenes y broker IDs.

El quórum requiere dos de los tres miembros. Un proceso actúa como líder y
dos como seguidores. Tolera perder un nodo mientras los otros dos se
comuniquen, pero todos los nodos están en una EC2: perder esa máquina
interrumpe el conjunto completo. No es alta disponibilidad entre máquinas.
Cada ZooKeeper tiene heap de 128–256 MiB, además del consumo nativo.

## Instalación nueva

Desde la raíz, iniciar los tres ZooKeeper juntos antes de los brokers:

```bash
docker compose -f docker-compose.yml -f docker-compose.kafka.yml \
  up -d --no-build --wait zookeeper zookeeper-2 zookeeper-3 kafka-1 kafka-2 kafka-3
```

No iniciar un nodo y esperar que se vuelva saludable por sí solo: necesita
al menos otro votante. Los healthchecks abren una sesión de ZooKeeper.

## AWS existente: migración con parada breve

No ejecutar un `up` general para convertir directamente la instalación anterior.
No utilizar `down -v`, borrar volúmenes, resetear offsets ni generar otro cluster ID.
Programar una pausa de operaciones de productos durante la migración. RabbitMQ,
RDS y los servicios ajenos al cambio no necesitan reiniciarse.

Antes de empezar, guardar el estado Git, comprobar RAM/espacio y conservar
las credenciales en el `.env` existente; nunca copiarlas al chat. El script
rechaza nodos/volúmenes nuevos preexistentes y una migración ya iniciada:
no está diseñado para ejecutarse repetidamente tras un fallo.

```bash
cd ~/Cloud_Native
git status --short --branch
free -h
df -h /
```

Con el repositorio actualizado sin perder modificaciones locales:

```bash
set -o pipefail
python3 scripts/migrate-zookeeper-ensemble.py --project cloud-native-aws \
  2>&1 | tee ~/evidencia-migracion-zookeeper.txt
```

El script usa los cuatro archivos Compose del backend AWS y:

1. Comprueba los tres brokers originales, sus cluster IDs y el nodo standalone.
2. Descarga la imagen requerida antes de detener servicios.
3. Guarda referencias de tópicos/grupos, archivos Compose sin expandir secretos
   y el Compose standalone conocido del commit `b10296d` para recuperación.
4. Detiene Inventory, Analytics, Kafka Admin y Kafka UI si estaban ejecutándose;
   captura offsets sin escritores activos. Revisa espacio para los respaldos.
5. Detiene brokers y ZooKeeper con cierre normal; respalda sus volúmenes y el
   volumen de Analytics, si existe, en `~/cloud-native-zk-backup-<fecha>/`.
   Comprueba que los archivos tar se puedan leer. La carpeta tiene modo 700.
6. Arranca el miembro original y **solo un** miembro nuevo. Nunca permite que
   dos nodos vacíos formen mayoría antes de que participe el original.
7. Cuando ese par abre sesiones, incorpora el tercer miembro y después los brokers.
8. Comprueba los cluster IDs, IDs/cantidades de tópicos y offsets confirmados.
   Si cambian, no reanuda escritores. Si coinciden, reanuda los servicios de
   aplicación que estaban activos. Spring puede tardar varios minutos.

La comparación de offsets no prueba el contenido de todos los mensajes;
conservar el respaldo y comprobar los mensajes/estadísticas tras el cambio.
No crear nuevos eventos hasta verificar la recuperación.

## Verificación de los tres nodos

Consulta de solo lectura, sin reiniciar procesos:

```bash
python3 scripts/verify-zookeeper-ensemble.py --project cloud-native-aws
```

Esperado: un `leader`, dos `follower` y PASS. En la salida Compose deben
aparecer tres ZooKeeper y tres brokers saludables. Comprobar Kafka UI,
los tópicos y los dos consumer groups; luego la salud de Inventory,
Analytics y Gateway y los datos previos en Angular.

Si se demuestra tolerancia a la pérdida de un nodo, realizarla en una
instalación aislada o con ventana acordada; no detener el ZooKeeper de AWS
solo para tomar una captura. El verificador de roles no simula una caída.

## Si falla la migración

Conservar la salida y la ruta exacta del respaldo. No repetir el script, borrar
volúmenes ni iniciar escritores si falló una comparación de metadatos/offsets.
Primero diagnosticar los logs de los ZooKeeper y brokers, sin imprimir el `.env`.

El respaldo contiene `volumes.json`, que relaciona cada volumen real con su
archivo tar, `cluster-id.txt`, `topics-before/after.txt`,
`groups-before/after.txt`, los archivos Compose y `running-services.json`.
La alternativa standalone está en `docker-compose.kafka.standalone.yml`.
Usar `--project-directory ~/Cloud_Native` si se referencia desde otra carpeta.

Para volver al punto anterior se debe detener todo escritor y todos los nodos
Kafka/ZooKeeper afectados, conservar una copia del estado fallido y restaurar
los volúmenes originales según `volumes.json` antes de iniciar el standalone
con el mismo nombre de proyecto. **No arrancar la configuración standalone
contra datos revertidos parcialmente.** Una restauración del respaldo descarta
cambios posteriores a su captura: no hacerla automáticamente si ya se
reanudaron operaciones. Los miembros 2/3 se mantienen detenidos durante la
vuelta a standalone. La recuperación se prepara con las rutas del respaldo
real; no se incluye un borrado automático de volúmenes.
