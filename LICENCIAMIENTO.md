# Licenciamiento ASSI Cotizador Transporte

## Diseño actual (v1.2.0)

- Licencia individual por dispositivo.
- El número de teléfono se usa como dato comercial/identificación del cliente, no como identificador técnico del equipo.
- El dispositivo se identifica con un hash derivado de ANDROID_ID + packageName.
- La app genera una solicitud con formato `ASSI-REQ-1...`.
- El administrador genera una licencia firmada digitalmente con ECDSA P-256.
- La app incluye solo la clave pública y valida la firma localmente.
- La clave privada nunca se incluye en la APK ni debe subirse al repositorio.
- La licencia se guarda localmente y puede validarse sin Internet después de activarse.

## Flujo comercial

1. Cliente instala la app.
2. Cliente abre Licencia y genera una solicitud.
3. Envía la solicitud al propietario.
4. Se verifica el pago.
5. El propietario usa `tools/license_generator.html` o `tools/license_generator.py`.
6. Se genera un archivo `.lic` ligado al Device ID solicitado.
7. El cliente importa o pega la licencia en la app.
8. La aplicación queda activada para ese teléfono.

## Campos de licencia

- licenseId
- customer
- phone
- email
- deviceId
- edition
- issuedAtEpochSec
- expiresAtEpochSec (0 = sin vencimiento)
- updatesUntilEpochSec (0 = sin límite configurado)

## Claves

La versión actual usa una clave pública de DESARROLLO. Antes de comercializar:

1. Generar una nueva clave ECDSA P-256 de producción.
2. Guardar la clave privada fuera de GitHub y hacer al menos dos copias de seguridad cifradas.
3. Sustituir la clave pública embebida en `LicenseManager.kt`.
4. Compilar una APK/AAB release firmada de forma persistente.

## Firma de la APK

Para actualizaciones reales en el mismo teléfono no debe distribuirse la APK debug. Se necesita un keystore release permanente y estable. Si cambia la firma de la APK, Android no aceptará una actualización directa sobre la instalación anterior.

## Repositorio

Antes de comercializar conviene hacer privado el repositorio. La firma criptográfica evita falsificar licencias con la clave pública, pero un repositorio público facilita modificar y recompilar la app para retirar el control de licencia.

## Pendiente

- Configurar el correo definitivo al que llegan las solicitudes.
- Rotar a claves de producción.
- Configurar compilación release firmada.
- Decidir política de traslado de licencia por pérdida/cambio de teléfono.
- Opcional: backend/API para solicitud y activación totalmente automáticas.
