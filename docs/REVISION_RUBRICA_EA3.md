# Evaluación 3: diseño y relación con la rúbrica

## Dos casos de uso complementarios

1. **Registrar operaciones del inventario como eventos:** Inventory publica en `product.events` después del commit del cambio. La clave es `productId`, para dirigir las operaciones de un mismo producto a la misma partición mientras no cambie el particionado. Analytics consume el evento y lo transforma en una métrica.
2. **Construir estadísticas de operaciones:** Analytics publica la métrica tipada en `product.metrics`. Un consumidor independiente actualiza la proyección persistente que consulta Angular. Cuenta operaciones procesadas, no existencias actuales.

RabbitMQ mantiene sus flujos separados de notificación y auditoría. Ambos sistemas reciben el hecho de negocio, pero sus consumidores realizan tareas diferentes. Estas responsabilidades son decisiones del proyecto, no capacidades exclusivas de cada plataforma.

## Contratos JSON

Entrada a `product.events`:

```json
{
  "eventId": "b328203a-366c-4824-b152-38ae454d5983",
  "eventType": "PRODUCT_CREATED",
  "productId": 7,
  "productName": "Monitor",
  "occurredAt": "2026-11-10T15:00:00Z"
}
```

Salida a `product.metrics` y entrada a la proyección:

```json
{
  "eventId": "b328203a-366c-4824-b152-38ae454d5983",
  "eventType": "PRODUCT_CREATED",
  "productId": 7,
  "occurredAt": "2026-11-10T15:00:00Z"
}
```

El UUID correlaciona ambos mensajes y permite deduplicar la proyección. Solo se admiten creación, actualización y eliminación. La métrica omite el nombre porque no lo necesita para agregar operaciones. JSON sin cabeceras de clases Java desacopla el contrato de los paquetes del productor.

## Justificación de configuración

| Decisión | Motivo y límite |
| --- | --- |
| Tres particiones | Permiten hasta tres consumidores activos con asignaciones en un grupo por tópico. Es un tamaño de demostración; la carga no se ha dimensionado mediante benchmark. |
| Tres réplicas | Cada partición tiene una réplica por broker en el clúster de tres brokers. |
| `acks=all`, `min.insync.replicas=2` | Exigen confirmación del conjunto ISR y al menos dos réplicas en sincronía para escribir. Toleran la pérdida de un broker si los otros dos siguen en sincronía y hay líder disponible. |
| Idempotencia del productor | Reduce duplicados por reintentos del productor; no garantiza exactamente una vez en todo el sistema. |
| Retención de eventos: siete días | Ventana académica de inspección y reprocesamiento sin conservar indefinidamente el historial de origen. |
| Retención de métricas: treinta días | Ventana mayor para reconstruir/inspeccionar la proyección de estadísticas; no es una política validada por volumen real. |
| `cleanup.policy=delete` | Se retienen operaciones individuales. Compactar por producto podría eliminar operaciones necesarias para los conteos. |
| Dos grupos distintos | `product-analytics-v1` transforma eventos; `metrics-projection-v1` persiste métricas. Sus offsets y responsabilidades son independientes. |
| ACK por registro, sin autocommit | El listener termina solo después de publicar la métrica confirmada o finalizar la transacción de persistencia. |
| Dos reintentos y DLT | Registro contextual con tópico/partición/offset/intento; el error no bloquea indefinidamente el flujo. Si falla la DLT, no se marca como recuperado. |

Aumentar las particiones puede cambiar la partición asignada a una clave. No aumentar particiones de los tópicos de negocio durante una demostración de orden por producto; usar un tópico temporal para probar administración.

## Código que se debe mostrar

| Criterio | Peso | Archivos principales / evidencia |
| --- | ---: | --- |
| Configuración cliente | 12 % | `application.yml` de Inventory/Analytics y `docker-compose.kafka.yml`; brokers saludables. |
| Dos tópicos y DTO | 12 % | `ProductEvent`, `ProductMetric`, listeners y flujo visible en ambos tópicos. |
| Administrador | 6 % | `TopicController`, POST/GET/PATCH/DELETE en Postman. |
| Productores | 14 % | `KafkaProductPublisher` y `KafkaMetricPublisher`; JSON y clave de producto. |
| Consumidores separados | 14 % | `ProductAnalytics` convierte y delega; `ProductAnalyticsService` valida/transforma/persiste; `MetricPublisher` es el puerto y `KafkaMetricPublisher` el adaptador Kafka. |
| Errores | 12 % | `KafkaErrorConfig`, pruebas DLT, logs y mensaje defectuoso seguido de uno válido. |
| Parámetros tópicos | 12 % | `KafkaConfiguration`: retención, cleanup, particiones, réplicas y mínimo ISR. |
| Grupos | 12 % | Dos grupos y sus offsets; prueba de reparto/rebalanceo descrita abajo. |
| Administración replicable | 6 % | Compose, API y prueba de aumento de particiones de un tópico temporal. |

## Reparto y rebalanceo

### Prueba aislada automática del clúster

Con Python 3, Docker y los tres brokers del proyecto ya ejecutándose, desde la raíz:

```bash
python scripts/verify-kafka-rebalance.py --project github_upload
```

En Ubuntu puede ser necesario `python3`. En AWS usar `--project cloud-native-aws`. El script crea un tópico y grupo únicos, arranca un consumidor, añade otro y elimina el segundo. Espera las asignaciones 1 → 2 → 1 y comprueba que las tres particiones se asignen una sola vez, con trabajo para cada miembro. Finalmente elimina únicamente sus contenedores, grupo y tópico temporales. No publica eventos ni modifica los offsets de los grupos del negocio. Requiere brokers disponibles, la red Compose por defecto y permisos de administración Kafka de la instalación local de laboratorio. No está pensado para brokers con SASL/TLS.

Esta prueba real acredita el mecanismo de grupos del clúster, **no** el procesamiento de DTO del servicio. Ese procesamiento se verifica por las pruebas y el flujo funcional.

### Demostración con el consumidor de la aplicación

En la instalación local, mantener una sola instancia de Analytics y ampliar únicamente los listeners de `product.events`:

```bash
docker compose -p github_upload -f docker-compose.yml -f docker-compose.kafka.yml -f docker-compose.kafka-rebalance.yml up -d --build analytics-service
```

Kafka UI → Consumers → `product-analytics-v1` debe mostrar dos miembros con tres particiones repartidas. `metrics-projection-v1` mantiene un solo listener/escritor H2. Crear productos para comprobar que las estadísticas se actualizan.

Volver al valor habitual de un miembro:

```bash
docker compose -p github_upload -f docker-compose.yml -f docker-compose.kafka.yml up -d analytics-service
```

Capturar ambos estados y verificar continuidad después del cambio. Esta variante reinicia el contenedor; no demuestra la caída aislada de un miembro. La prueba automática sí elimina uno de sus dos consumidores temporales y observa la reasignación. No escalar Analytics con `--scale`: dos JVM no deben compartir el mismo archivo H2. Para este overlay en AWS se deben conservar además los archivos/variables AWS usados en el despliegue.

## Aumento de particiones mediante la API

Usar un tópico nuevo de prueba, nunca los tópicos activos:

1. POST `/api/v1/kafka/topics` con nombre `demo.rubrica`, tres particiones, tres réplicas y retención de un día. Esperar 201.
2. PATCH `/api/v1/kafka/topics/demo.rubrica/partitions` con `{"partitions":6}`. Esperar 204.
3. GET `/api/v1/kafka/topics/demo.rubrica`. Esperar 200 y seis particiones, conservando las tres réplicas de cada partición.
4. DELETE del tópico de prueba y comprobar 404 mediante GET.

Usar el access token habitual y rol ADMIN para las mutaciones. La prueba automatizada existente de creación comprueba también que PATCH solicita a AdminClient exactamente seis particiones, sin añadir otro caso a la selección de 15 pruebas Kafka.

## Alcance honesto de la entrega

Las pruebas con Mockito comprueban lógica y contratos, no disponibilidad de brokers. El flujo de Inventory no tiene outbox: puede perder una publicación entre el commit y el envío a Kafka, aunque quede registrado el error. La deduplicación de la proyección y el archivo H2 se diseñaron para una sola JVM escritora. Los tres brokers en una EC2 no protegen frente a la caída de esa EC2. El despliegue AWS completo y el frontend público siguen pendientes de validación.

## Resultado de esta revisión

Validación del 10 de noviembre de 2026 en un entorno aislado:

- Analytics: 15 pruebas correctas (cuatro de flujo, dos de errores/DLT y nueve de seguridad JWT).
- Kafka Admin: 15 pruebas correctas (seis de administración, incluida la petición de aumento de particiones, y nueve JWT).
- Inventory: tres pruebas seleccionadas del productor correctas.
- Se mantiene la selección de **15 pruebas Kafka**; las otras 18 anteriores son regresiones JWT de los dos módulos revisados.
- Compose local y combinación AWS + overlay de concurrencia validados estáticamente.
- Tres brokers reales: prueba automática 1 → 2 → 1 correcta, con reparto 2/1 al tener dos miembros y recuperación de las tres particiones al retirar uno. [Salida de la ejecución](evidencias/kafka-rebalance-verificado.txt).

La prueba de concurrencia de los listeners de la aplicación en la instalación del estudiante y la secuencia Postman de aumento de particiones quedan como demostraciones reproducibles, no como nuevas capturas ya obtenidas. No se modificaron los recursos AWS del estudiante.
