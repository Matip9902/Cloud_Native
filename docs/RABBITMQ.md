# RabbitMQ: responsabilidades separadas

La observación de la evaluación 2 se corrige separando consumidores por responsabilidad. Antes `notification-service` consumía tres colas y dos de sus consumidores solo escribían logs. Ahora cada cola RabbitMQ activa tiene un microservicio propietario y un resultado persistente diferente:

```text
Inventory Service -> product.events.exchange (routing key product.created/updated/deleted)
                       |-> product.events.queue -> Notification Service -> notificaciones
                       |-> product.audit.queue  -> Audit Service        -> historial de operaciones

Inventory Service -> Kafka product.events -> Analytics Service -> Kafka product.metrics -> estadísticas
```

Las dos colas RabbitMQ reciben copias del mismo evento porque ambas necesitan conocer los cambios de productos. No son consumidores competidores de una sola cola: una operación debe generar tanto una notificación como un registro de auditoría. Cada servicio puede detenerse y recuperar su atraso sin detener el otro.

## Auditoría

- Servicio Java/Spring Boot independiente: `backend/audit-service`, puerto 8088.
- Consume exclusivamente `product.audit.queue`.
- Persiste `eventId`, operación, ID/nombre del producto, fecha del evento y fecha de recepción.
- Base propia H2 persistente en el volumen `audit_data`. Se puede configurar otra base con `AUDIT_DB_URL`, `AUDIT_DB_USERNAME`, `AUDIT_DB_PASSWORD` y `AUDIT_DB_DRIVER`; el driver PostgreSQL está incluido.
- Utiliza `eventId` como clave primaria para no duplicar una entrega reprocesada.
- Confirma con ACK después de que finaliza la transacción de persistencia. Si falla la validación o la escritura, rechaza sin requeue y RabbitMQ lo dirige a `product.audit.dlq`, mediante `product.audit.dlx`.
- Si se pierde la conexión al confirmar el ACK después del commit, el broker puede volver a entregar el evento; la deduplicación permite confirmarlo sin crear otro registro.
- Consulta protegida: `GET /api/v1/audits?page=0&size=20`, mediante Gateway; requiere scope `inventory.read` o rol `ADMIN`, con firma, issuer y audiencia JWT válidos.
- La vista **Auditoría** de Angular muestra los últimos 20 registros y permite actualizarlos manualmente.

No hay registros artificiales de ejemplo en la base de auditoría. El historial empieza con los eventos consumidos por el nuevo servicio, incluidos los que ya estuvieran pendientes en su cola. Los eventos antiguos que el consumidor anterior confirmó solo en logs no pueden recuperarse desde RabbitMQ.

## Actualizar una instalación existente

```sh
git pull origin main
docker compose -f docker-compose.yml -f docker-compose.kafka.yml up --build -d
```

Es importante reconstruir `notification-service`: su nueva imagen ya no contiene los consumidores de auditoría ni métricas. No mantener otra instancia antigua de ese servicio ejecutándose, pues competiría con `audit-service` por la cola de auditoría.

El comando conserva los volúmenes. No ejecutar `down -v` para actualizar.

### Cola de métricas antigua

Las colas durables y sus bindings sobreviven aunque se retire su declaración del código. La nueva versión no elimina recursos del broker automáticamente ni borra mensajes pendientes.

Si la instalación tiene `product.metrics.queue`, abrir RabbitMQ Management y entrar en esa cola → **Bindings**. Retirar únicamente el binding desde `product.events.exchange` con routing key `product.#` hacia esa cola. Así deja de recibir mensajes nuevos; conservar la cola para inspeccionar/exportar su contenido previo. No eliminar la cola ni su DLQ sin revisar y decidir qué hacer con los mensajes existentes. La aplicación nueva no declara ni consume esa cola: las métricas activas se procesan con Kafka.

No retirar los bindings de `product.events.queue` ni `product.audit.queue`.

## Comprobar la corrección

1. En RabbitMQ Management, revisar **Consumers**: `product.events.queue` corresponde a la conexión de `notification-service` y `product.audit.queue` a la de `audit-service`. No debe quedar un consumidor de métricas dentro de notificaciones. Se configuran nombres de conexión explícitos para identificar cada servicio.
2. Crear, modificar y eliminar un producto en Angular.
3. Confirmar tres notificaciones y tres entradas de auditoría para el producto; los UUID identifican los eventos individuales. También deben aumentar los conteos Kafka.
4. Reiniciar solo Audit Service y comprobar que sus entradas siguen disponibles.
5. Detener Audit Service, modificar un producto y comprobar que notificaciones continúa funcionando y que auditoría acumula el mensaje pendiente. Iniciar Audit Service y comprobar que lo persiste y confirma.
6. Enviar un tipo de evento desconocido a la cola de auditoría de prueba y comprobar NACK/DLQ y ausencia de un registro de auditoría.

Pruebas locales automatizadas:

```sh
cd backend/audit-service
mvn test
cd ../notification-service
mvn test
```

H2 en archivo es apropiado para una sola instancia local de auditoría. Para varias instancias, utilizar una base compartida y adaptar el manejo de conflictos de deduplicación. La persistencia no implica una transacción distribuida con Inventory y RabbitMQ.

## Validación de esta corrección

Se ejecutaron 6 pruebas de auditoría (persistencia/deduplicación, validación y ACK/NACK) y 2 de regresión de notificaciones; todas pasaron. Angular compiló y ambos Compose pasaron la validación estática.

Una comprobación aislada del backend creó, modificó y eliminó un producto mediante HTTP. Con tres brokers Kafka y una instancia RabbitMQ real, obtuvo 3 registros de auditoría, 3 notificaciones y 3 métricas (una por operación). La autenticación de ese ejecutor de pruebas se simuló fuera del checkout, sin alterar el código de seguridad del proyecto; no demuestra el login Azure, la integración del gateway, la interfaz Angular ni la salud del clúster RabbitMQ del equipo del usuario. Esas verificaciones deben realizarse al actualizar la instalación local.
