# ASSI Cotizador Transporte — Android V1

Aplicación Android nativa, offline, basada en la planilla de costos de transporte.

## V1 incluida

- Catálogo local de vehículos.
- Importación de vehículos desde `.xlsx` (hoja `Vehiculos`) o `.csv`.
- Modos de importación: agregar/actualizar y reemplazar catálogo.
- Parámetros generales editables:
  - margen comercial;
  - depreciación anual;
  - mantenimiento anual;
  - precio de combustible;
  - lubricantes y grasas;
  - administración/indirectos;
  - km anuales de referencia;
  - redondeo de oferta;
  - salario diario;
  - dieta diaria.
- Cotizador por vehículo, km, días y cantidad de choferes.
- Viaje sencillo o redondo.
- Costo detallado y precio de oferta.
- Resumen compacto para cliente.
- Compartir oferta como PNG o PDF.
- Historial local de cotizaciones.
- Base de datos Room; no necesita Internet para operar.

## Fórmulas

- Litros = km / índice km/L.
- Combustible = litros × precio USD/L.
- Salarios = días × choferes × salario diario.
- Dietas = días × choferes × dieta diaria.
- Depreciación USD/km = AFT × % depreciación anual / km anuales.
- Mantenimiento USD/km = AFT × % mantenimiento anual / km anuales.
- Lubricantes = combustible × % lubricantes.
- Subtotal operativo = suma de costos operativos.
- Administración = subtotal × % indirectos.
- Costo total = subtotal + administración.
- Recargo comercial = costo total × margen.
- Precio calculado = costo total + recargo.
- Precio oferta = precio calculado redondeado siempre hacia arriba al múltiplo configurado.

## Estructura XLSX/CSV esperada

La app reconoce los encabezados de la hoja `Vehiculos` del Excel suministrado:

1. ID
2. Vehículo / configuración
3. Tipo de servicio
4. Valor vehículo (USD)
5. Valor equipo / remolque (USD)
6. Valor total AFT (USD)
7. Índice consumo (km/L)
8. Observaciones

Si `Valor total AFT` viene vacío, la app calcula `Valor vehículo + Valor equipo/remolque`.

## Abrir y compilar

1. Abrir la carpeta del proyecto con Android Studio.
2. Usar JDK 17.
3. Sincronizar Gradle.
4. Ejecutar en un teléfono Android 8.0 (API 26) o superior.
5. Para APK: `Build > Build App Bundle(s) / APK(s) > Build APK(s)`.

> Este entorno de entrega no contiene Android SDK/Gradle, por lo que el código fuente está preparado y validado estructuralmente, pero el APK no se compila aquí.

## Compilación online con GitHub Actions

El proyecto incluye `.github/workflows/build-android.yml`. Al subirlo completo a GitHub, la acción ejecuta los tests, compila `app-debug.apk` y lo publica como artefacto descargable. Consulta `COMPILAR_ONLINE.md` para el procedimiento exacto.
