# Compilar el APK online con GitHub Actions

Este proyecto ya incluye `.github/workflows/build-android.yml`.

## Primera compilación

1. Crea un repositorio vacío en GitHub.
2. Sube **todo el contenido de esta carpeta**, incluida la carpeta oculta `.github`.
3. En GitHub abre **Actions**.
4. Selecciona **Build Android APK**.
5. Pulsa **Run workflow** y confirma.
6. Espera a que el proceso termine en verde.
7. Abre la ejecución terminada y baja hasta **Artifacts**.
8. Descarga `ASSI-Cotizador-Transporte-debug-APK`.
9. Descomprime el artefacto: dentro estará `app-debug.apk`.

## Qué hace automáticamente

- Prepara Java 17.
- Prepara Android SDK y API 35.
- Usa Gradle 8.9.
- Ejecuta los tests unitarios.
- Compila `assembleDebug`.
- Publica el APK como artefacto descargable durante 30 días.

## Compilaciones siguientes

Cada `push` a `main` o `master` vuelve a ejecutar pruebas y genera un APK nuevo.
También puedes compilar manualmente desde **Actions > Build Android APK > Run workflow**.

## Instalación en Android

El APK generado es de **depuración**, adecuado para pruebas e instalación directa en tu teléfono.
Android puede pedir autorización para **instalar aplicaciones desconocidas** desde el navegador o gestor de archivos utilizado.

## Versión comercial

Para distribución estable conviene crear posteriormente una variante **release firmada** con keystore y secretos de GitHub. No se incluyó una clave privada dentro del ZIP.
