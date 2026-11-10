# Maven Central responde HTTP 429 durante el build

Un `Non-resolvable parent POM` acompañado de `Too Many Requests (429)` significa que el repositorio rechazó temporalmente la descarga. La mención `parent.relativePath` es parte del error Maven: no hay que cambiar el padre Spring Boot para solucionar un límite de descargas. La IP de salida compartida de una institución puede contribuir al límite, pero no se determina esa causa solo con el log.

Los Dockerfiles Java aceptan `MAVEN_CENTRAL_MIRROR=central` (predeterminado) o `google` (espejo público de Maven Central mantenido por Google). Mantienen HTTPS y las coordenadas/versiones del proyecto. No se desactiva la verificación TLS ni se cambia el repositorio del proyecto en los POM. La opción afecta únicamente a la construcción de imágenes.

Se añadió una caché BuildKit de `/root/.m2`, compartida entre las compilaciones Java y con acceso bloqueado por build para evitar escrituras simultáneas. También permite conservar artefactos descargados antes de un fallo. No borrar la caché ante un 429. `-U` permite reintentar artefactos previamente marcados como fallidos; no garantiza que desaparezca un límite del servidor.

Desde Git Bash, con Docker Desktop ejecutándose y desde la raíz del repositorio:

```bash
git pull origin main
bash scripts/build-local.sh google
```

El script construye cada microservicio secuencialmente, después el frontend, y arranca la instalación local solo si todas las construcciones terminaron correctamente. Usa el proyecto `github_upload`, salvo que se defina `COMPOSE_PROJECT_NAME`. No usar este script para actualizar el backend AWS: utiliza los archivos del despliegue local e incluye frontend.

Para volver al servidor predeterminado:

```bash
bash scripts/build-local.sh central
```

Comprobación opcional de acceso al espejo desde el PC, sin descargar todas las dependencias:

```bash
curl -I https://maven-central.storage-download.googleapis.com/maven2/org/springframework/boot/spring-boot-starter-parent/3.5.16/spring-boot-starter-parent-3.5.16.pom
```

Un 200 indica disponibilidad de ese archivo desde ese cliente; no demuestra que Docker pueda descargar todas las dependencias. Si el espejo también devuelve 429, respetar Retry-After y detener reintentos repetidos. Si la red institucional bloquea el dominio, solicitar acceso al administrador o usar el repositorio permitido; no desactivar TLS para resolver errores de red/certificados.

La disponibilidad del espejo Google no pudo verificarse desde el entorno de desarrollo porque su proxy rechaza ese dominio. La configuración y el orden del script se verifican por separado; el build completo usando el espejo debe comprobarse en el PC del estudiante. La primera descarga puede seguir siendo lenta.
