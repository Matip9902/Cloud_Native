# Pruebas unitarias y de componentes Kafka

Se añadieron 54 casos de prueba para el flujo Kafka. Se utilizan JUnit 5, Mockito, el serializador JSON real, sincronización transaccional Spring y MockMvc standalone. Las pruebas no requieren brokers, RabbitMQ, una cuenta Azure ni una base cloud, y no crean ni eliminan tópicos reales.

## Ejecutar con Docker (sin instalar Java ni Maven)

Desde la raíz del proyecto:

```sh
docker compose -f docker-compose.tests.yml run --rm kafka-tests
```

La primera ejecución descarga la imagen Maven y sus dependencias; las siguientes reutilizan un volumen de caché. Este Compose es independiente del de la aplicación. No hay que detener los contenedores del proyecto ni borrar sus volúmenes. Los reportes se generan en `backend/<servicio>/target/surefire-reports/`.

El runner termina con error si un módulo falla. Para considerar la ejecución correcta, los tres módulos deben mostrar `BUILD SUCCESS`, sin fallos, errores ni casos omitidos. Algunos escenarios de prueba provocan errores controlados y escriben stack traces en los logs: esos mensajes por sí solos no implican una prueba fallida; revisar el resumen `Tests run` y el resultado final.

## Ejecutar con Java 17 y Maven instalados

Desde la raíz del proyecto:

```sh
mvn -B -ntp -f backend/inventory-service/pom.xml test
mvn -B -ntp -f backend/analytics-service/pom.xml test
mvn -B -ntp -f backend/kafka-admin-service/pom.xml test
```

Para ejecutar únicamente los 54 casos nuevos:

```sh
mvn -B -ntp -f backend/inventory-service/pom.xml -Dtest=KafkaProductPublisherTests test
mvn -B -ntp -f backend/analytics-service/pom.xml -Dtest=ProductAnalyticsTests,KafkaErrorConfigTests test
mvn -B -ntp -f backend/kafka-admin-service/pom.xml -Dtest=TopicControllerTests test
```

## Qué se comprueba

| Clase | Casos | Comportamiento verificado |
| --- | ---: | --- |
| `KafkaProductPublisherTests` | 8 | Publicación de los tres tipos de evento con `productId` como clave; errores de envío síncronos y asíncronos registrados con `eventId`; publicación después del commit; ausencia de envío al hacer rollback o sin transacción. |
| `ProductAnalyticsTests` | 21 | Transformación evento-métrica; confirmación del envío; propagación de fallos del broker; rechazo de JSON mal formado, eventos incompletos y tipos desconocidos; persistencia de métricas válidas; deduplicación de entregas; propagación de fallos de base de datos; conteos y proyección vacía. |
| `KafkaErrorConfigTests` | 2 | Dos intentos de reentrega antes de recuperar el registro; destino `.DLT` en la misma partición con clave, payload y cabeceras de origen; si falla la publicación DLT, el registro no se considera recuperado. |
| `TopicControllerTests` | 23 | Listar, crear, describir, configurar y eliminar tópicos; aumentar particiones; validar solicitudes antes de llamar al broker; proteger tópicos internos; consultar grupos y offsets; traducir errores Kafka a HTTP 400/404/409/503. |
| **Total nuevo** | **54** | Pruebas automatizadas sin infraestructura de mensajería externa. |

En los tests de productores/consumidores y administrador, KafkaTemplate, AdminClient y repositorios son dobles de prueba. MockMvc standalone verifica validación de solicitudes y respuestas del controlador, sin la cadena de autenticación de producción. Las pruebas JWT existentes usan firmas RSA y un JWKS HTTP local para validar emisor, audiencia, vigencia y firma. La sincronización de commit/rollback utiliza un gestor de transacciones de prueba y las reglas reales de eventos transaccionales Spring.

Estas pruebas no sustituyen las de integración con brokers reales, el login Entra ID, la persistencia tras reinicios ni la caída de brokers, realizadas por separado.

## Resultado registrado

La ejecución de las suites completas de los tres módulos el 9 de noviembre de 2026 incluye los 54 casos nuevos y 23 existentes de regresión:

| Módulo | Ejecutadas | Fallos | Errores | Omitidas |
| --- | ---: | ---: | ---: | ---: |
| Inventory Service | 13 | 0 | 0 | 0 |
| Analytics Service | 32 | 0 | 0 | 0 |
| Kafka Admin Service | 32 | 0 | 0 | 0 |
| **Total** | **77** | **0** | **0** | **0** |

La ejecución registrada se realizó con Maven 3.9.11 y JDK 17, usando dependencias ya instaladas en modo offline. El Compose de pruebas invoca los mismos tres comandos Maven y se valida por separado como configuración; no se presenta como una ejecución Docker realizada en el equipo del usuario.

Para la entrega, guardar capturas de los resúmenes de los tres módulos y sus reportes Surefire. El porcentaje de cobertura de código no se ha medido; los conteos de casos no implican cobertura del 100 %.
