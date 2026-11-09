# 15 pruebas Kafka

La suite Kafka se redujo a 15 casos seleccionados. Las pruebas previas de seguridad y negocio permanecen en el repositorio y se ejecutan por separado con `mvn test`; el comando Docker de esta guía ejecuta únicamente los 15 casos Kafka.

## Ejecutar sin instalar Java ni Maven

Desde la raíz del proyecto:

```sh
docker compose -f docker-compose.tests.yml run --rm kafka-tests
```

La primera ejecución descarga Maven y dependencias. El contenedor de pruebas es independiente de los servicios del proyecto: no requiere brokers, RabbitMQ ni credenciales Azure. Los reportes quedan en `backend/<servicio>/target/surefire-reports/`.

Los tres módulos deben terminar con `BUILD SUCCESS`, sin fallos, errores ni casos omitidos. Los escenarios de error generan logs intencionales; el resultado de las pruebas se determina por el resumen Maven, no por la sola aparición de un stack trace.

## Casos seleccionados

| N.º | Componente | Caso |
| --- | --- | --- |
| 1 | Productor | Publica el evento con la clave de producto únicamente después del commit. |
| 2 | Productor | No publica cambios de una transacción revertida. |
| 3 | Productor | Registra el fallo asíncrono del envío con el identificador del evento. |
| 4 | Analytics | Transforma un evento válido en una métrica tipada y conserva sus datos. |
| 5 | Analytics | Propaga el fallo del broker para permitir reintentos. |
| 6 | Analytics | Rechaza un tipo de evento desconocido sin producir una métrica. |
| 7 | Analytics | Guarda la métrica una sola vez ante una entrega duplicada. |
| 8 | Errores Kafka | Reintenta dos veces y envía el registro a la misma partición de la DLT con clave, payload y cabeceras de origen. |
| 9 | Errores Kafka | No considera recuperado el mensaje si falla el envío a la DLT. |
| 10 | Administrador | Lista los tópicos devueltos por AdminClient. |
| 11 | Administrador | Crea un tópico con particiones, réplicas y retención solicitadas. |
| 12 | Administrador | Modifica la retención mediante una operación incremental. |
| 13 | Administrador | Rechaza una retención inválida antes de contactar al broker. |
| 14 | Administrador | Elimina únicamente el tópico solicitado. |
| 15 | Administrador | Lista los grupos y representa correctamente su estado. |

## Ejecutar con Java 17 y Maven instalados

Desde la raíz del proyecto:

```sh
mvn -B -ntp -f backend/inventory-service/pom.xml -Dtest=KafkaProductPublisherTests test
mvn -B -ntp -f backend/analytics-service/pom.xml -Dtest=ProductAnalyticsTests,KafkaErrorConfigTests test
mvn -B -ntp -f backend/kafka-admin-service/pom.xml -Dtest=TopicControllerTests test
```

## Resultado registrado

| Módulo | Ejecutadas | Fallos | Errores | Omitidas |
| --- | ---: | ---: | ---: | ---: |
| Inventory Service | 3 | 0 | 0 | 0 |
| Analytics Service | 6 | 0 | 0 | 0 |
| Kafka Admin Service | 6 | 0 | 0 | 0 |
| **Total Kafka** | **15** | **0** | **0** | **0** |

La ejecución registrada el 9 de noviembre de 2026 utiliza JDK 17 y Maven 3.9.11 con las dependencias disponibles en modo offline. El Compose invoca los mismos comandos y se valida por separado como configuración; no se presenta como una ejecución realizada en el equipo del usuario.

KafkaTemplate, AdminClient y repositorios son dobles Mockito. Se utiliza el serializador JSON real y la sincronización transaccional Spring con un gestor de transacciones de prueba. MockMvc standalone verifica las peticiones del administrador sin cargar la autenticación de producción. Las pruebas JWT existentes se conservan fuera de esta selección.

Estas comprobaciones no sustituyen las pruebas de integración con Kafka/RabbitMQ reales, login Entra ID, persistencia o caída de brokers. No se declara cobertura de código del 100 %.
