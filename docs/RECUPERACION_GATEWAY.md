# Recuperación del gateway tras reiniciar servicios

## Problema observado

Después de reiniciar Analytics, el servicio respondía directamente en el puerto 8086 con los conteos conservados, pero el gateway devolvía 500 con `Connection refused` contra una dirección anterior. Reiniciar el gateway recuperaba el acceso. Por tanto, los ceros que mostraba Angular durante el error no demostraban pérdida de datos.

En Compose, Analytics y Kafka Admin no tenían un hostname de registro estable: Eureka podía anunciar el identificador efímero del contenedor. Además, el cliente HTTP Reactor Netty podía mantener en su caché DNS la IP previa después de un reinicio o recreación.

## Cambio

- Analytics, Audit y Kafka Admin anuncian en Eureka sus nombres de servicio Docker (`analytics-service`, `audit-service`, `kafka-admin-service`), con preferencia por hostname y un ID de instancia estable.
- `GatewayDnsConfiguration` limita a 5 segundos la retención de respuestas DNS positivas, permite TTL mínimos de cero y no almacena resultados negativos de DNS. Una consulta nueva puede resolver la nueva IP cuando el servicio vuelva a estar disponible.
- Las rutas siguen utilizando `lb://...` y Eureka. No se desactiva autenticación ni se sustituyen las rutas por IP fijas.
- Se puede ajustar el máximo con `app.gateway.dns-cache-ttl-seconds` (por defecto 5); la configuración YAML también acepta `GATEWAY_DNS_TTL_SECONDS` si está disponible en el proceso del gateway.
- Los nombres estables están configurados para el Compose local en una misma red Docker. El Compose de servicios separados conserva su configuración de IP de EC2.

Durante el apagado o arranque de un servicio puede existir un error temporal de conexión; esto no implementa disponibilidad ininterrumpida. Una vez disponible el backend y su registro, las siguientes peticiones deben recuperarse sin reiniciar el gateway manualmente.

## Pruebas automatizadas

```sh
docker compose -f docker-compose.tests.yml run --rm gateway-tests
```

O con Java 17 y Maven:

```sh
mvn -B -ntp -f backend/api-gateway/pom.xml test
```

Se ejecutan 2 pruebas de componentes:

1. La ruta protegida devuelve 401 cuando falta el token.
2. El gateway real enruta una petición al backend inicial; este se detiene y el DNS anuncia otra IP bajo el mismo hostname. La siguiente instancia responde sin reiniciar el gateway. El servidor DNS de prueba anuncia un TTL de 60 segundos y la prueba configura el límite del gateway a 1 segundo para agilizar su ejecución.

La regresión se reprodujo antes de la corrección: el gateway mantuvo la IP anterior y devolvió 500 durante los 10 segundos de espera. Con la corrección, ambas pruebas pasaron (0 fallos, 0 errores, 0 omitidas), y el módulo compiló y generó su JAR.

Se utilizan servidores DNS/HTTP locales reales. La lista de instancias que Eureka suministra y el decodificador JWT se sustituyen exclusivamente en el contexto de prueba. Por ello, esta prueba demuestra la recuperación del enrutamiento y la caché DNS, no una ejecución con el tenant Azure ni con un servidor Eureka externo. Los errores provocados al detener el backend aparecen intencionalmente en los logs.

Estas dos pruebas no forman parte de la selección de 15 casos Kafka: el comando `kafka-tests` conserva esos 15 casos.

## Actualizar el equipo local

Desde la raíz del proyecto:

```sh
git pull origin main
docker compose -f docker-compose.yml -f docker-compose.kafka.yml up --build -d api-gateway analytics-service audit-service kafka-admin-service
```

Se recrearán los contenedores que tengan cambios de imagen o entorno; se conservan los volúmenes de auditoría y estadísticas. Esperar a que terminen de arrancar y aparezcan en Eureka.

## Comprobación en la instalación del usuario

1. Abrir Estadísticas Kafka y Auditoría en Angular, pulsar Actualizar y anotar los valores.
2. Ejecutar solo:

```sh
docker compose -f docker-compose.yml -f docker-compose.kafka.yml restart analytics-service audit-service
```

3. Esperar a que `http://localhost:8086/actuator/health` y `http://localhost:8088/actuator/health` respondan UP. Dar unos segundos adicionales a la resolución DNS y actualización del registro.
4. Actualizar ambas vistas sin reiniciar el gateway. Deben conservar los valores y volver a responder.
5. Si persiste un error, recoger `logs --tail=60 api-gateway analytics-service audit-service` y el estado de sus registros en Eureka. No borrar volúmenes para corregir un problema de conexión.

La actualización y esta última comprobación en el PC del usuario son independientes de los resultados automatizados registrados arriba.
