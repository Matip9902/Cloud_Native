# Pruebas unitarias Angular

Las seis pruebas de `frontend/angular-app-cloud/src/app/app.spec.ts` verifican el arranque del componente raíz con MSAL simulado. No necesitan credenciales Azure ni los servicios Docker.

Casos:

1. Esperar la inicialización de MSAL y dar prioridad a la cuenta recibida por redirección.
2. Conservar la cuenta activa si no hay resultado de redirección.
3. Seleccionar la primera cuenta almacenada si no hay una activa.
4. Renderizar el router sin seleccionar una cuenta cuando no hay sesión.
5. Registrar el error de inicialización sin procesar la redirección.
6. Registrar el error de redirección sin seleccionar una cuenta.

## Ejecución

Requiere Node.js compatible con Angular 20 (por ejemplo Node 22.12 o posterior de la rama 22), npm y Google Chrome instalado.

Desde la raíz del repositorio:

```bash
cd frontend/angular-app-cloud
npm ci
npm test -- --watch=false --browsers=ChromeHeadless
```

Resultado esperado: `TOTAL: 6 SUCCESS`.

Si Karma no encuentra Chrome, establece `CHROME_BIN` con la ruta del ejecutable del navegador. Estas pruebas verifican la lógica local de autenticación; el inicio de sesión real con Entra ID se comprueba por separado en el navegador.

Validación del 9 de noviembre de 2026: seis pruebas correctas en Chromium Headless, sin fallos. Las 15 pruebas seleccionadas de Kafka siguen siendo una suite independiente.
