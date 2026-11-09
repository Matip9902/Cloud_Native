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

## Revisión de dependencias

Se actualizaron Angular a 20.3.35 y CLI/build a 20.3.42, junto con las dependencias compatibles del archivo de bloqueo. La validación posterior obtuvo:

- Seis pruebas correctas en Chromium Headless.
- Compilación de producción correcta; permanece el aviso de tamaño inicial (653,50 kB frente al presupuesto de aviso de 500 kB).
- `npm audit`: 16 avisos moderados, cero altos y cero críticos, frente a los 35 avisos originales.
- `npm audit --omit=dev`: cero vulnerabilidades notificadas.

Los avisos restantes proceden de `glob` y `minimatch` en la cadena de herramientas del Angular CLI. El informe propone migrar a Angular CLI 22; esa migración requiere revisar compatibilidad y queda pendiente. No se aplicó `--force` ni se forzaron dependencias transitivas fuera de los rangos de sus consumidores. El informe de npm es una comprobación de avisos conocidos, no una auditoría completa de seguridad de la aplicación.

Para reproducir los resultados después de actualizar el repositorio:

```bash
npm ci
npm audit
npm audit --omit=dev
npm test -- --watch=false --browsers=ChromeHeadless
npm run build
```

`npm audit` termina con código distinto de cero mientras queden avisos. Los resultados pueden cambiar al publicarse nuevos avisos.
