# Kafka: eventos y estadísticas de productos

Kafka complementa RabbitMQ. RabbitMQ entrega notificaciones a `notification-service` y auditoría a `audit-service`, cada uno con su cola y DLQ. La analítica pertenece a Kafka; ya no hay consumidor de métricas RabbitMQ. Kafka añade un historial de operaciones y una proyección persistente de conteos consultable desde Angular.

## Arranque local

Desde la raíz del repositorio:

```sh
docker compose -f docker-compose.yml -f docker-compose.kafka.yml up --build -d
```

Para detener esta misma instalación sin borrar los datos:

```sh
docker compose -f docker-compose.yml -f docker-compose.kafka.yml down
```

Se requiere Docker Compose v2. RabbitMQ utiliza descubrimiento declarativo de nodos sin hooks de reset. Reservar aproximadamente 8 GB de RAM para la arquitectura completa. No se necesita desplegar AWS para ejecutar este Compose. El login y las APIs de negocio mantienen la configuración Microsoft Entra ID existente y necesitan acceso a ese tenant.

| Componente | Puerto host |
| --- | --- |
| Kafka UI | 8090 |
| Analytics Service | 8086 |
| Kafka Admin Service | 8087 |
| Brokers Kafka, acceso desde host | 19092, 29092, 39092 |

Los nuevos puertos se publican solo en loopback. Kafka UI y los brokers usan acceso local sin autenticación; esta configuración es de desarrollo. No exponerlos directamente en Internet.

Para ejecutar los nuevos servicios mediante Maven en el host, iniciar primero ZooKeeper y los tres brokers con el mismo comando Compose, seleccionando `zookeeper kafka-1 kafka-2 kafka-3`. Iniciar después `kafka-admin-service` (crea los tópicos), `analytics-service` y los servicios existentes desde sus directorios con `mvn spring-boot:run`. Los valores predeterminados de bootstrap apuntan a los puertos del host; Compose utiliza los listeners internos `kafka-N:9092`.

## Flujo

1. Inventory mantiene el envío RabbitMQ y registra el mismo `ProductEvent` como evento de aplicación.
2. Tras confirmar la transacción de productos, publica JSON en `product.events`, con `productId` como clave.
3. Analytics consume con el grupo `product-analytics-v1`, valida el DTO y publica una métrica por operación en `product.metrics`. Espera la confirmación del broker antes de avanzar el offset de entrada.
4. El grupo `metrics-projection-v1` consume esas métricas y las guarda en H2 persistente. `eventId` es la clave primaria: reprocesar un evento no incrementa de nuevo el conteo.
5. Angular muestra los conteos en **Estadísticas Kafka**, mediante `GET /api/v1/metrics`, pasando por el gateway. La actualización es manual y el procesamiento asíncrono.

El DTO de producto contiene `eventId`, `eventType`, `productId`, `productName` y `occurredAt`. La métrica conserva esos datos salvo `productName`. Los tipos admitidos son `PRODUCT_CREATED`, `PRODUCT_UPDATED` y `PRODUCT_DELETED`. Se utiliza JSON sin cabeceras de nombres de clases Java para desacoplar los servicios.

## Tópicos y errores

Kafka Admin declara al arrancar:

| Tópico | Particiones | Réplicas | Retención |
| --- | ---: | ---: | --- |
| `product.events` | 3 | 3 | 7 días |
| `product.metrics` | 3 | 3 | 30 días |
| `product.events.DLT` | 3 | 3 | 7 días |
| `product.metrics.DLT` | 3 | 3 | 7 días |

Todos usan `cleanup.policy=delete` y `min.insync.replicas=2`. Productores: `acks=all`, idempotencia habilitada. Los consumidores no usan autocommit; confirman por registro procesado. Los errores de procesamiento se registran con tópico, partición y offset, se reintentan dos veces con una pausa de un segundo y finalmente se envían a `.DLT`. Si falla el envío a DLT, el error sigue propagándose para no dar el registro por recuperado. Kafka UI permite inspeccionar mensajes, réplicas y grupos. Las DLT no se consumen automáticamente.

La proyección usa el volumen `analytics_data`. Al borrarlo, se pierden los conteos locales; los offsets del grupo siguen en Kafka. Para reconstruir, detener Analytics y resetear el grupo `metrics-projection-v1` al inicio mediante herramientas Kafka, siempre dentro del historial retenido. H2 permite una única instancia del servicio sobre este archivo: no escalar varias instancias con el mismo volumen. Para escalar la proyección, migrar a una base compartida.

## API de administración

Las rutas están disponibles mediante el gateway en `/api/v1/kafka` y directamente en el puerto 8087. Los nuevos servicios validan firma, issuer y audiencia JWT. Las lecturas requieren `inventory.read` o rol `ADMIN`; las mutaciones requieren el rol `ADMIN`. Enviar `Authorization: Bearer <token>` como en las APIs existentes. La clave pública se consulta al validar tokens, no al iniciar el servicio.

| Método y ruta relativa | Función |
| --- | --- |
| `GET /topics` | Listar tópicos |
| `GET /topics/{name}` | Ver líderes, réplicas, ISR y configuración |
| `POST /topics` | Crear tópico |
| `PATCH /topics/{name}/config` | Actualizar configuraciones permitidas |
| `PATCH /topics/{name}/partitions` | Aumentar particiones |
| `DELETE /topics/{name}` | Eliminar tópico |
| `GET /consumer-groups` | Listar grupos y estado |
| `GET /consumer-groups/{id}` | Consultar estado, miembros y offsets |

Ejemplo de cuerpo para crear un tópico de prueba:

```json
{
  "name": "demo.products",
  "partitions": 3,
  "replicationFactor": 3,
  "configs": {"retention.ms": "604800000", "cleanup.policy": "delete", "min.insync.replicas": "2"}
}
```

Para actualizar configuración, enviar por PATCH un objeto como `{"retention.ms":"86400000"}`. Para aumentar particiones, `{"partitions":6}`. No se pueden reducir particiones. El factor de replicación se define al crear; esta API no implementa reasignación de réplicas. No eliminar los tópicos del flujo activo para probar el administrador: utilizar uno de prueba. Los tópicos internos están protegidos.

Variables configurables: `KAFKA_BOOTSTRAP_SERVERS`, `KAFKA_PRODUCT_TOPIC`, `KAFKA_METRICS_TOPIC`, `ANALYTICS_DB_URL`, `ANALYTICS_DB_PASSWORD`, `JWT_ISSUER_URI`, `JWT_JWK_SET_URI`, `JWT_AUDIENCES`. Para cambiar los nombres de los tópicos, establecer los mismos valores en Inventory, Analytics y Kafka Admin.

## Pruebas manuales sugeridas

- Abrir Kafka UI y comprobar tres brokers y los cuatro tópicos, con tres particiones y tres réplicas.
- Crear, modificar y eliminar productos desde Angular; comprobar eventos en `product.events`, métricas en `product.metrics` y los tres conteos en Estadísticas Kafka.
- Confirmar que las notificaciones RabbitMQ, el historial del servicio de auditoría y sus DLQ siguen funcionando.
- Publicar un evento inválido en `product.events`; comprobar logs, reintentos y llegada a `product.events.DLT`; enviar luego uno válido y confirmar que se procesa.
- Reenviar un evento con el mismo `eventId`; comprobar que el total no aumenta dos veces.
- Reiniciar Analytics y comprobar que los conteos permanecen.
- Detener un broker, esperar a la elección de líderes y comprobar continuidad con los dos restantes. Recuperarlo después.
- Usar la API para crear, inspeccionar, modificar y eliminar `demo.products`; consultar ambos grupos de consumidores.

## Alcance y límites

Esta incorporación mantiene la integración RabbitMQ existente. No migra las notificaciones a Kafka. La publicación a Kafka ocurre después del commit de la base de datos, y sus fallos quedan en logs: **no hay outbox transaccional ni recuperación automática de publicaciones perdidas**. Una caída entre commit y publicación puede perder el evento Kafka. RabbitMQ conserva su comportamiento transaccional previo. Los conteos no representan el stock actual, sino operaciones procesadas desde la instalación/reconstrucción de la proyección. No se declara entrega exactamente una vez entre base de datos, RabbitMQ y Kafka.

La compilación y la validación estática Compose se revisan por separado; las pruebas funcionales, de autenticación y de tolerancia a fallos deben ejecutarse con la infraestructura levantada.

## Si un broker termina esperando a ZooKeeper

Si aparece `Timed out waiting for connection to Zookeeper server`, actualizar el repositorio: los brokers ahora esperan un healthcheck de sesión ZooKeeper (`cub zk-ready`), no solo el inicio de su contenedor. Recrear la instalación sin borrar volúmenes:

```sh
git pull origin main
docker compose -f docker-compose.yml -f docker-compose.kafka.yml down
docker compose -f docker-compose.yml -f docker-compose.kafka.yml up --build -d
```

No utilizar `down -v`: no es necesario borrar datos para corregir el orden de arranque. Si ZooKeeper queda `unhealthy`, revisar sus logs; ese estado indica que sigue sin aceptar sesiones y necesita un diagnóstico adicional.

## Si Métricas o Auditoría responden 401 pero Productos funciona

El endpoint de login Entra v2 no garantiza que el token de acceso sea v2: la versión del token depende de la API registrada. Los servicios de analítica, auditoría y administración Kafka aceptan los emisores v1 (`https://sts.windows.net/<tenant>/`) y v2 (`https://login.microsoftonline.com/<tenant>/v2.0`) únicamente del tenant configurado en `JWT_ISSUER_URI`. Mantienen la verificación de firma con JWKS, vencimiento y audiencia. No es necesario cambiar los permisos del usuario ni desactivar seguridad.

Actualizar y reconstruir los servicios que validan esos tokens:

```sh
git pull origin main
docker compose -f docker-compose.yml -f docker-compose.kafka.yml up --build -d analytics-service audit-service kafka-admin-service
```

Después, actualizar las secciones Estadísticas Kafka y Auditoría en Angular. Si persiste el 401, revisar los logs del gateway y de esos servicios para localizar el rechazo; no compartir tokens ni claves privadas en los logs o en capturas.

Pruebas de regresión: cada servicio comprueba con tokens RSA realmente firmados y un JWKS local la aceptación del tenant v1/v2 y el rechazo de emisores de otros tenants, emisor ausente, audiencia incorrecta, token vencido y firma falsa. Estas pruebas no usan credenciales Azure reales ni sustituyen la comprobación del login en el equipo del usuario.

## Pruebas automatizadas Kafka

Los productores, consumidores, DLT y el administrador disponen de 15 casos automatizados seleccionados. Las instrucciones, resultados y distinción respecto a las pruebas de integración están en [PRUEBAS_KAFKA.md](PRUEBAS_KAFKA.md).

```sh
docker compose -f docker-compose.tests.yml run --rm kafka-tests
```

## Recuperación tras reiniciar Analytics

Los registros Eureka de los servicios nuevos usan nombres Docker estables y el gateway limita su caché DNS para resolver cambios de IP. La prueba automatizada, la actualización y el chequeo local están en [RECUPERACION_GATEWAY.md](RECUPERACION_GATEWAY.md).
