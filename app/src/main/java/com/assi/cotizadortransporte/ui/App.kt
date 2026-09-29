package com.assi.cotizadortransporte.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.assi.cotizadortransporte.BuildConfig
import com.assi.cotizadortransporte.data.CostParametersEntity
import com.assi.cotizadortransporte.data.QuoteEntity
import com.assi.cotizadortransporte.data.VehicleEntity
import com.assi.cotizadortransporte.demo.DemoManager
import com.assi.cotizadortransporte.domain.QuoteCalculator
import com.assi.cotizadortransporte.license.LicenseManager
import com.assi.cotizadortransporte.profile.BusinessProfileManager
import com.assi.cotizadortransporte.share.QuoteShareUtil
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class Screen(val label: String) {
    HOME("Inicio"), QUOTE("Cotizar"), VEHICLES("Flota"), HISTORY("Historial"), PARAMETERS("Ajustes")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransportCostApp(
    vm: AppViewModel,
    darkMode: Boolean,
    onDarkModeChange: (Boolean) -> Unit,
    showHelp: Boolean,
    onShowHelpChange: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val vehicles by vm.vehicles.collectAsStateWithLifecycle()
    val params by vm.parameters.collectAsStateWithLifecycle()
    val quotes by vm.quotes.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    var screen by rememberSaveable { mutableStateOf(Screen.HOME) }
    var licenseStatus by remember { mutableStateOf(LicenseManager.currentStatus(context)) }
    var demoStatus by remember { mutableStateOf(DemoManager.status(context)) }
    var businessProfile by remember { mutableStateOf(BusinessProfileManager.load(context)) }
    var showAbout by rememberSaveable { mutableStateOf(false) }
    var showLicense by rememberSaveable { mutableStateOf(false) }
    var duplicateQuote by remember { mutableStateOf<QuoteEntity?>(null) }
    val snackbarHost = remember { SnackbarHostState() }

    if (showAbout) {
        AboutDialog(onDismiss = { showAbout = false })
    }

    if (showLicense) {
        LicenseScreen(
            status = licenseStatus,
            onStatusChanged = {
                licenseStatus = it
                showLicense = false
            },
            darkMode = darkMode,
            onDarkModeChange = onDarkModeChange,
            onBack = { showLicense = false }
        )
        return
    }

    val demoMode = !licenseStatus.valid

    LaunchedEffect(message) {
        message?.let {
            snackbarHost.showSnackbar(it)
            vm.consumeMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("CotiRuta", fontWeight = FontWeight.Bold)
                            if (demoMode) {
                                Spacer(Modifier.width(8.dp))
                                AssistChip(
                                    onClick = { showLicense = true },
                                    label = { Text("DEMO") }
                                )
                            }
                        }
                        Text("Costos y tarifas de transporte", style = MaterialTheme.typography.labelSmall)
                    }
                },
                actions = {
                    IconButton(onClick = { onShowHelpChange(!showHelp) }) {
                        Text(if (showHelp) "?✓" else "?", fontWeight = FontWeight.Bold)
                    }
                    IconButton(onClick = { onDarkModeChange(!darkMode) }) {
                        Text(if (darkMode) "☾" else "☀")
                    }
                    IconButton(onClick = { showAbout = true }) {
                        Text("ⓘ")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHost) },
        bottomBar = {
            NavigationBar {
                Screen.entries.forEach { item ->
                    NavigationBarItem(
                        selected = screen == item,
                        onClick = { screen = item },
                        icon = { Text(item.label.take(1)) },
                        label = { Text(item.label) }
                    )
                }
            }
        }
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (screen) {
                Screen.HOME -> HomeScreen(
                    demoMode = demoMode,
                    demoStatus = demoStatus,
                    vehicles = vehicles,
                    quotes = quotes,
                    onNewQuote = {
                        duplicateQuote = null
                        screen = Screen.QUOTE
                    },
                    onOpenLicense = { showLicense = true },
                    onOpenHistory = { screen = Screen.HISTORY }
                )
                Screen.QUOTE -> QuoteScreen(
                    vehicles = vehicles,
                    params = params,
                    showHelp = showHelp,
                    demoMode = demoMode,
                    demoStatus = demoStatus,
                    template = duplicateQuote,
                    businessProfile = businessProfile,
                    onDemoCalculationUsed = { demoStatus = DemoManager.consumeCalculation(context) },
                    onSave = vm::saveQuote
                )
                Screen.VEHICLES -> VehiclesScreen(
                    vehicles = vehicles,
                    showHelp = showHelp,
                    demoMode = demoMode,
                    onImport = { uri, replace ->
                        vm.importVehicles(uri, replace, if (demoMode) DemoManager.MAX_VEHICLES else null)
                    },
                    onSaveVehicle = vm::saveVehicle
                )
                Screen.HISTORY -> HistoryScreen(
                    items = if (demoMode) quotes.take(DemoManager.MAX_HISTORY_VISIBLE) else quotes,
                    showHelp = showHelp,
                    demoMode = demoMode,
                    onDuplicate = { q ->
                        duplicateQuote = q
                        screen = Screen.QUOTE
                    }
                )
                Screen.PARAMETERS -> ParametersScreen(
                    p = params,
                    showHelp = showHelp,
                    profile = businessProfile,
                    onProfileSaved = { businessProfile = it },
                    onOpenLicense = { showLicense = true },
                    onSave = vm::saveParameters
                )
            }
        }
    }
}

@Composable
private fun HomeScreen(
    demoMode: Boolean,
    demoStatus: DemoManager.Status,
    vehicles: List<VehicleEntity>,
    quotes: List<QuoteEntity>,
    onNewQuote: () -> Unit,
    onOpenLicense: () -> Unit,
    onOpenHistory: () -> Unit
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("CotiRuta", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(
            "Conozca el costo real del viaje y defina una tarifa comercial sustentada.",
            style = MaterialTheme.typography.bodyLarge
        )

        if (demoMode) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Modo Demo", fontWeight = FontWeight.Bold)
                    Text(
                        if (demoStatus.exhausted)
                            "Ha utilizado las 10 cotizaciones de demostración."
                        else
                            "${demoStatus.remainingCalculations} de ${DemoManager.MAX_CALCULATIONS} cotizaciones de prueba disponibles."
                    )
                    Text(
                        "Puede probar hasta ${DemoManager.MAX_VEHICLES} vehículos. Los documentos generados incluyen la marca DEMO.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Button(onClick = onOpenLicense, modifier = Modifier.fillMaxWidth()) {
                        Text("ACTIVAR LICENCIA")
                    }
                }
            }
        } else {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("Licencia activa", fontWeight = FontWeight.Bold)
                    Text("Funciones completas y cotizaciones ilimitadas.")
                }
            }
        }

        Button(
            onClick = onNewQuote,
            enabled = !demoMode || !demoStatus.exhausted,
            modifier = Modifier.fillMaxWidth().height(56.dp)
        ) {
            Text(if (demoMode && demoStatus.exhausted) "DEMO FINALIZADA" else "NUEVA COTIZACIÓN")
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ElevatedCard(Modifier.weight(1f)) {
                Column(Modifier.padding(14.dp)) {
                    Text("Flota", style = MaterialTheme.typography.labelLarge)
                    Text("${vehicles.size}", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                }
            }
            ElevatedCard(Modifier.weight(1f)) {
                Column(Modifier.padding(14.dp)) {
                    Text("Historial", style = MaterialTheme.typography.labelLarge)
                    Text(
                        if (demoMode) "${minOf(quotes.size, DemoManager.MAX_HISTORY_VISIBLE)}" else "${quotes.size}",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        if (quotes.isNotEmpty()) {
            Text("Cotización reciente", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            val q = quotes.first()
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(q.service, fontWeight = FontWeight.Bold)
                    Text("${q.origin.ifBlank { "—" }} → ${q.destination.ifBlank { "—" }}")
                    Text(money(q.offerPriceUsd, q.currencyCode, q.currencyRatePerUsd), color = MaterialTheme.colorScheme.primary)
                    TextButton(onClick = onOpenHistory) { Text("VER HISTORIAL") }
                }
            }
        }
    }
}

@Composable
private fun QuoteScreen(
    vehicles: List<VehicleEntity>,
    params: CostParametersEntity,
    showHelp: Boolean,
    demoMode: Boolean,
    demoStatus: DemoManager.Status,
    template: QuoteEntity?,
    businessProfile: BusinessProfileManager.Profile,
    onDemoCalculationUsed: () -> Unit,
    onSave: (QuoteEntity) -> Unit
) {
    val context = LocalContext.current
    var client by rememberSaveable { mutableStateOf("") }
    var service by rememberSaveable { mutableStateOf("Servicio de transporte") }
    var origin by rememberSaveable { mutableStateOf("") }
    var destination by rememberSaveable { mutableStateOf("") }
    var roundTrip by rememberSaveable { mutableStateOf(false) }
    var baseKm by rememberSaveable { mutableStateOf("935") }
    var days by rememberSaveable { mutableStateOf("2") }
    var drivers by rememberSaveable { mutableStateOf("1") }
    var salary by rememberSaveable { mutableStateOf("") }
    var diet by rememberSaveable { mutableStateOf("") }
    var marginOverride by rememberSaveable { mutableStateOf("") }
    var selectedId by rememberSaveable { mutableStateOf("") }
    var result by remember { mutableStateOf<QuoteCalculator.Result?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(template?.id) {
        template?.let { q ->
            client = q.client
            service = q.service
            origin = q.origin
            destination = q.destination
            roundTrip = q.tripType.equals("Redondo", ignoreCase = true)
            baseKm = fmtInput(q.baseDistanceKm)
            days = q.days.toString()
            drivers = q.drivers.toString()
            selectedId = q.vehicleId
            marginOverride = fmtInput(q.commercialMarginPct * 100)
            result = null
            error = null
        }
    }

    LaunchedEffect(params.standardDailySalaryUsd, params.standardDailyDietUsd) {
        if (salary.isBlank()) salary = fmtInput(params.standardDailySalaryUsd)
        if (diet.isBlank()) diet = fmtInput(params.standardDailyDietUsd)
    }
    LaunchedEffect(vehicles) {
        if (selectedId.isBlank() && vehicles.isNotEmpty()) selectedId = vehicles.first().id
        if (vehicles.none { it.id == selectedId } && vehicles.isNotEmpty()) selectedId = vehicles.first().id
    }

    val selected = vehicles.firstOrNull { it.id == selectedId }
    val distance = (parseNumber(baseKm) ?: 0.0) * if (roundTrip) 2.0 else 1.0

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            if (template != null) "Duplicar cotización" else "Nueva oferta",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        if (demoMode) {
            AssistChip(
                onClick = {},
                label = {
                    Text(
                        if (demoStatus.exhausted)
                            "Demo finalizada"
                        else
                            "Demo · ${demoStatus.remainingCalculations} cotizaciones disponibles"
                    )
                }
            )
        }
        if (showHelp) {
            HelpCard(
                "Cómo cotizar",
                "Complete la ruta, seleccione el vehículo y defina distancia, tiempo y personal. CotiRuta calcula costos, margen y tarifa final automáticamente."
            )
        }
        Text(
            "Salida: ${params.outputCurrency.uppercase(Locale.US)} · 1 USD = ${format2(params.outputExchangeRatePerUsd)} ${params.outputCurrency.uppercase(Locale.US)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.secondary
        )
        if (vehicles.isEmpty()) {
            ElevatedCard { Text("Primero cree un vehículo o importe el catálogo desde XLSX o CSV.", Modifier.padding(16.dp)) }
            return@Column
        }

        Field("Cliente", client) { client = it }
        Field("Servicio a ofertar", service) { service = it }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.weight(1f)) { Field("Origen", origin) { origin = it } }
            Box(Modifier.weight(1f)) { Field("Destino", destination) { destination = it } }
        }

        Text("Tipo de viaje", fontWeight = FontWeight.SemiBold)
        Row(verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected = !roundTrip, onClick = { roundTrip = false; result = null })
            Text("Sencillo")
            Spacer(Modifier.width(20.dp))
            RadioButton(selected = roundTrip, onClick = { roundTrip = true; result = null })
            Text("Redondo")
        }

        VehiclePicker(vehicles, selectedId) { selectedId = it; result = null }
        NumberField(if (roundTrip) "Km origen-destino (se duplican)" else "Kilómetros a recorrer", baseKm) { baseKm = it; result = null }
        if (roundTrip) Text("Kilómetros totales calculados: ${format2(distance)} km", color = MaterialTheme.colorScheme.primary)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.weight(1f)) { NumberField("Días", days) { days = it; result = null } }
            Box(Modifier.weight(1f)) { NumberField("Choferes", drivers) { drivers = it; result = null } }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.weight(1f)) { NumberField("Salario/día USD", salary) { salary = it; result = null } }
            Box(Modifier.weight(1f)) { NumberField("Dieta/día USD", diet) { diet = it; result = null } }
        }
        NumberField("Margen específico % (vacío = general ${format1(params.commercialMarginPct * 100)}%)", marginOverride) { marginOverride = it; result = null }

        Button(
            onClick = {
                error = null
                if (demoMode && demoStatus.exhausted) {
                    error = "Ha utilizado las 10 cotizaciones de demostración. Active una licencia para continuar."
                    return@Button
                }
                runCatching {
                    val v = requireNotNull(selected) { "Seleccione un vehículo." }
                    val km = distance
                    val d = days.toIntOrNull() ?: 0
                    val ch = drivers.toIntOrNull() ?: 0
                    val sal = parseNumber(salary) ?: 0.0
                    val die = parseNumber(diet) ?: 0.0
                    val margin = parseNumber(marginOverride)?.div(100.0)
                    QuoteCalculator.calculate(v, params, QuoteCalculator.Input(km, d, ch, sal, die, margin))
                }.onSuccess {
                    result = it
                    if (demoMode) onDemoCalculationUsed()
                }.onFailure { error = it.message }
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("CALCULAR OFERTA") }

        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        result?.let { r ->
            CostBreakdown(r, params)
            ClientSummary(service, origin, destination, if (roundTrip) "Redondo" else "Sencillo", distance, selected!!.name, days.toIntOrNull() ?: 0, r, params)

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        onSave(
                            QuoteEntity(
                                createdAt = System.currentTimeMillis(),
                                client = client,
                                service = service,
                                origin = origin,
                                destination = destination,
                                tripType = if (roundTrip) "Redondo" else "Sencillo",
                                baseDistanceKm = parseNumber(baseKm) ?: 0.0,
                                totalDistanceKm = distance,
                                days = days.toIntOrNull() ?: 0,
                                drivers = drivers.toIntOrNull() ?: 0,
                                vehicleId = selected.id,
                                vehicleName = selected.name,
                                totalCostUsd = r.totalCostUsd,
                                commercialMarginPct = r.marginPct,
                                calculatedPriceUsd = r.calculatedPriceUsd,
                                offerPriceUsd = r.offerPriceUsd,
                                offerPricePerKmUsd = r.offerPricePerKmUsd,
                                currencyCode = params.outputCurrency.uppercase(Locale.US),
                                currencyRatePerUsd = safeRate(params.outputExchangeRatePerUsd)
                            )
                        )
                    },
                    modifier = Modifier.weight(1f)
                ) { Text("Guardar") }
                OutlinedButton(
                    onClick = {
                        QuoteShareUtil.shareImage(context, presentation(client, service, origin, destination, roundTrip, distance, selected, days, r, params, businessProfile, demoMode))
                    },
                    modifier = Modifier.weight(1f)
                ) { Text("Imagen") }
                OutlinedButton(
                    onClick = {
                        QuoteShareUtil.sharePdf(context, presentation(client, service, origin, destination, roundTrip, distance, selected, days, r, params, businessProfile, demoMode))
                    },
                    modifier = Modifier.weight(1f)
                ) { Text("PDF") }
            }
        }
        Spacer(Modifier.height(18.dp))
    }
}

private fun presentation(
    client: String, service: String, origin: String, destination: String,
    roundTrip: Boolean, distance: Double, vehicle: VehicleEntity, days: String,
    r: QuoteCalculator.Result, params: CostParametersEntity,
    businessProfile: BusinessProfileManager.Profile, demoMode: Boolean
) = QuoteShareUtil.Presentation(
    client = client,
    service = service,
    origin = origin,
    destination = destination,
    tripType = if (roundTrip) "Redondo" else "Sencillo",
    totalKm = distance,
    vehicle = vehicle.name,
    days = days.toIntOrNull() ?: 0,
    offerPriceUsd = r.offerPriceUsd,
    pricePerKmUsd = r.offerPricePerKmUsd,
    currencyCode = params.outputCurrency.uppercase(Locale.US),
    currencyRatePerUsd = safeRate(params.outputExchangeRatePerUsd),
    entityName = businessProfile.name,
    entityContact = businessProfile.contact,
    logoUri = businessProfile.logoUri,
    demo = demoMode
)

@Composable
private fun VehiclePicker(vehicles: List<VehicleEntity>, selectedId: String, onSelected: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val selected = vehicles.firstOrNull { it.id == selectedId }
    Column {
        Text("Vehículo / configuración", fontWeight = FontWeight.SemiBold)
        OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
            Text(selected?.let { "${it.id} · ${it.name}" } ?: "Seleccionar")
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            vehicles.forEach { v ->
                DropdownMenuItem(
                    text = { Text("${v.id} · ${v.name}") },
                    onClick = { onSelected(v.id); open = false }
                )
            }
        }
    }
}

@Composable
private fun CostBreakdown(r: QuoteCalculator.Result, p: CostParametersEntity) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Detalle de costo", fontWeight = FontWeight.Bold)
            CostRow("Combustible", r.fuelUsd, p)
            CostRow("Salarios", r.salariesUsd, p)
            CostRow("Dietas", r.dietsUsd, p)
            CostRow("Depreciación", r.depreciationUsd, p)
            CostRow("Mantenimiento", r.maintenanceUsd, p)
            CostRow("Lubricantes y grasas", r.lubricantsUsd, p)
            CostRow("Administración / indirectos", r.adminIndirectUsd, p)
            HorizontalDivider()
            CostRow("Costo total", r.totalCostUsd, p, true)
            Text("Margen comercial: ${format1(r.marginPct * 100)}% · ${outputMoney(r.commercialMarkupUsd, p)}")
            CostRow("Precio calculado", r.calculatedPriceUsd, p)
            Text(
                "PRECIO OFERTA: ${outputMoney(r.offerPriceUsd, p)}",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text("Tarifa: ${outputMoney(r.offerPricePerKmUsd, p)} / km")
        }
    }
}

@Composable
private fun CostRow(label: String, valueUsd: Double, p: CostParametersEntity, bold: Boolean = false) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal)
        Text(outputMoney(valueUsd, p), fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal)
    }
}

@Composable
private fun ClientSummary(
    service: String,
    origin: String,
    destination: String,
    type: String,
    km: Double,
    vehicle: String,
    days: Int,
    r: QuoteCalculator.Result,
    p: CostParametersEntity
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text("RESUMEN PARA CLIENTE", fontWeight = FontWeight.Bold)
            Text("Servicio: $service")
            Text("Origen: ${origin.ifBlank { "—" }}")
            Text("Destino: ${destination.ifBlank { "—" }}")
            Text("Tipo: $type")
            Text("Distancia: ${format2(km)} km")
            Text("Equipo: $vehicle")
            Text("Tiempo estimado: $days día(s)")
            HorizontalDivider()
            Text(
                "PRECIO OFERTA ${outputMoney(r.offerPriceUsd, p)}",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun VehiclesScreen(
    vehicles: List<VehicleEntity>,
    showHelp: Boolean,
    demoMode: Boolean,
    onImport: (Uri, Boolean) -> Unit,
    onSaveVehicle: (VehicleEntity) -> Unit
) {
    var replace by remember { mutableStateOf(false) }
    var showNewVehicle by rememberSaveable { mutableStateOf(false) }
    val demoLimitReached = demoMode && vehicles.size >= DemoManager.MAX_VEHICLES
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { onImport(it, replace) }
    }

    if (showNewVehicle) {
        VehicleFormDialog(
            existingIds = vehicles.map { it.id.uppercase(Locale.getDefault()) }.toSet(),
            onDismiss = { showNewVehicle = false },
            onSave = {
                onSaveVehicle(it)
                showNewVehicle = false
            }
        )
    }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Flota", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            if (demoMode) {
                Spacer(Modifier.width(8.dp))
                AssistChip(onClick = {}, label = { Text("${vehicles.size}/${DemoManager.MAX_VEHICLES} Demo") })
            }
        }
        if (showHelp) {
            HelpCard(
                "Catálogo de flota",
                if (demoMode)
                    "En Demo puede trabajar con hasta ${DemoManager.MAX_VEHICLES} vehículos. Cree equipos manualmente o importe desde Excel/CSV."
                else
                    "Cree cada vehículo manualmente o importe el catálogo desde Excel/CSV. El valor total AFT se calcula con vehículo + equipo/remolque."
            )
        }

        Button(
            onClick = { showNewVehicle = true },
            enabled = !demoLimitReached,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (demoLimitReached) "LÍMITE DEMO ALCANZADO" else "NUEVO VEHÍCULO")
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    replace = false
                    launcher.launch(arrayOf(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                        "text/csv",
                        "text/comma-separated-values"
                    ))
                },
                modifier = Modifier.weight(1f)
            ) {
                Text("Importar / actualizar")
            }
            OutlinedButton(
                onClick = {
                    replace = true
                    launcher.launch(arrayOf(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                        "text/csv",
                        "text/comma-separated-values"
                    ))
                },
                modifier = Modifier.weight(1f)
            ) {
                Text("Reemplazar catálogo")
            }
        }

        HorizontalDivider()
        if (vehicles.isEmpty()) Text("Catálogo vacío. Cree el primer vehículo o importe un archivo.")

        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            vehicles.forEach { v ->
                ElevatedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("${v.id} · ${v.name}", fontWeight = FontWeight.Bold)
                        if (v.serviceType.isNotBlank()) Text(v.serviceType)
                        Text("Vehículo: ${usd(v.vehicleValueUsd)} · Equipo: ${usd(v.equipmentValueUsd)}")
                        Text("AFT: ${usd(v.totalAftUsd)} · Consumo: ${format2(v.fuelKmPerLiter)} km/L")
                        if (v.notes.isNotBlank()) Text(v.notes, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun VehicleFormDialog(
    existingIds: Set<String>,
    onDismiss: () -> Unit,
    onSave: (VehicleEntity) -> Unit
) {
    var id by rememberSaveable { mutableStateOf("") }
    var name by rememberSaveable { mutableStateOf("") }
    var serviceType by rememberSaveable { mutableStateOf("") }
    var vehicleValue by rememberSaveable { mutableStateOf("") }
    var equipmentValue by rememberSaveable { mutableStateOf("0") }
    var fuelConsumption by rememberSaveable { mutableStateOf("") }
    var notes by rememberSaveable { mutableStateOf("") }
    var error by rememberSaveable { mutableStateOf<String?>(null) }

    val vehicleUsd = parseNumber(vehicleValue)
    val equipmentUsd = parseNumber(equipmentValue)
    val calculatedAft = (vehicleUsd ?: 0.0) + (equipmentUsd ?: 0.0)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nuevo vehículo / configuración") },
        text = {
            Column(
                Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Field("ID único", id) { id = it; error = null }
                Field("Vehículo / configuración", name) { name = it; error = null }
                Field("Tipo de servicio", serviceType) { serviceType = it }
                NumberField("Valor vehículo USD", vehicleValue) { vehicleValue = it; error = null }
                NumberField("Valor equipo / remolque USD", equipmentValue) { equipmentValue = it; error = null }
                NumberField("Índice de consumo km/L", fuelConsumption) { fuelConsumption = it; error = null }
                Text(
                    "Valor total AFT: ${usd(calculatedAft)}",
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Observaciones") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 4
                )
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val cleanId = id.trim()
                    val cleanName = name.trim()
                    val v = parseNumber(vehicleValue)
                    val e = parseNumber(equipmentValue)
                    val fuel = parseNumber(fuelConsumption)

                    error = when {
                        cleanId.isBlank() -> "El ID es obligatorio."
                        existingIds.contains(cleanId.uppercase(Locale.getDefault())) -> "Ya existe un vehículo con ese ID."
                        cleanName.isBlank() -> "El nombre o configuración es obligatorio."
                        v == null || v < 0.0 -> "Valor del vehículo inválido."
                        e == null || e < 0.0 -> "Valor del equipo inválido."
                        fuel == null || fuel <= 0.0 -> "El consumo km/L debe ser mayor que cero."
                        else -> null
                    }

                    if (error == null) {
                        onSave(
                            VehicleEntity(
                                id = cleanId,
                                name = cleanName,
                                serviceType = serviceType.trim(),
                                vehicleValueUsd = v!!,
                                equipmentValueUsd = e!!,
                                totalAftUsd = calculatedAft,
                                fuelKmPerLiter = fuel!!,
                                notes = notes.trim()
                            )
                        )
                    }
                }
            ) {
                Text("GUARDAR")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}

@Composable
private fun LicenseScreen(
    status: LicenseManager.Status,
    onStatusChanged: (LicenseManager.Status) -> Unit,
    darkMode: Boolean,
    onDarkModeChange: (Boolean) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var customer by rememberSaveable { mutableStateOf("") }
    var phone by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var requestCode by rememberSaveable { mutableStateOf("") }
    var licenseText by rememberSaveable { mutableStateOf("") }
    var localMessage by rememberSaveable { mutableStateOf("") }

    val licensePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                    ?: error("No se pudo leer el archivo.")
            }.onSuccess { text ->
                licenseText = text.trim()
                val result = LicenseManager.activate(context, licenseText)
                onStatusChanged(result)
                localMessage = result.message
            }.onFailure {
                localMessage = it.message ?: "No se pudo importar la licencia."
            }
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        TextButton(onClick = onBack) { Text("← Volver") }
        Text("Licencia", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)

        ElevatedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    if (status.valid) "LICENCIA ACTIVA" else "MODO DEMO",
                    fontWeight = FontWeight.Bold,
                    color = if (status.valid) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
                )
                Text("Dispositivo: ${LicenseManager.deviceId(context)}")
                if (status.valid) {
                    if (status.customer.isNotBlank()) Text("Cliente: ${status.customer}")
                    if (status.phone.isNotBlank()) Text("Teléfono: ${status.phone}")
                    if (status.licenseId.isNotBlank()) Text("Licencia: ${status.licenseId}")
                    Text("Actualizaciones: " + if (status.updatesUntilEpochSec == 0L) "sin límite configurado" else "según vigencia de licencia")
                } else {
                    val demo = DemoManager.status(context)
                    Text(
                        if (demo.exhausted)
                            "La demostración ha finalizado."
                        else
                            "Puede probar CotiRuta sin pagar: ${demo.remainingCalculations} cotizaciones Demo disponibles."
                    )
                }
            }
        }

        Text("Apariencia", fontWeight = FontWeight.SemiBold)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(if (darkMode) "Modo oscuro" else "Modo claro", modifier = Modifier.weight(1f))
            Switch(checked = darkMode, onCheckedChange = onDarkModeChange)
        }

        if (!status.valid) {
            HorizontalDivider()
            Text("Solicitar licencia", fontWeight = FontWeight.Bold)
            Text(
                "Cuando decida activar la versión completa, genere la solicitud. La licencia se vincula al número de teléfono y al dispositivo.",
                style = MaterialTheme.typography.bodySmall
            )
            Field("Nombre / empresa", customer) { customer = it }
            Field("Teléfono", phone) { phone = it }
            Field("Correo del cliente", email) { email = it }

            Button(
                onClick = {
                    if (customer.isBlank() || phone.isBlank()) {
                        localMessage = "Nombre/empresa y teléfono son obligatorios."
                    } else {
                        requestCode = LicenseManager.buildRequestCode(context, customer, phone, email)
                        localMessage = "Solicitud generada."
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("GENERAR SOLICITUD")
            }

            if (requestCode.isNotBlank()) {
                OutlinedTextField(
                    value = requestCode,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Código de solicitud") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 4,
                    maxLines = 7
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("Solicitud licencia CotiRuta", requestCode))
                            localMessage = "Código copiado."
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text("Copiar") }

                    Button(
                        onClick = {
                            val body = LicenseManager.requestSummary(context, customer, phone, email)
                            val intent = Intent(Intent.ACTION_SENDTO).apply {
                                data = Uri.parse("mailto:${LicenseManager.LICENSE_REQUEST_EMAIL}")
                                putExtra(Intent.EXTRA_SUBJECT, "Solicitud licencia CotiRuta · ${LicenseManager.deviceId(context)}")
                                putExtra(Intent.EXTRA_TEXT, body)
                            }
                            runCatching { context.startActivity(intent) }
                                .onFailure { localMessage = "No hay una aplicación de correo disponible." }
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text("Enviar") }
                }
            }

            HorizontalDivider()
            Text("Activar licencia pagada", fontWeight = FontWeight.Bold)

            OutlinedButton(
                onClick = {
                    licensePicker.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("IMPORTAR ARCHIVO DE LICENCIA")
            }

            OutlinedTextField(
                value = licenseText,
                onValueChange = { licenseText = it },
                label = { Text("O pegar licencia") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 4,
                maxLines = 8
            )
            Button(
                onClick = {
                    val result = LicenseManager.activate(context, licenseText)
                    onStatusChanged(result)
                    localMessage = result.message
                },
                enabled = licenseText.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("ACTIVAR")
            }
        }

        if (localMessage.isNotBlank()) {
            Text(localMessage, color = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun ParametersScreen(
    p: CostParametersEntity,
    showHelp: Boolean,
    profile: BusinessProfileManager.Profile,
    onProfileSaved: (BusinessProfileManager.Profile) -> Unit,
    onOpenLicense: () -> Unit,
    onSave: (CostParametersEntity) -> Unit
) {
    val context = LocalContext.current
    var margin by rememberSaveable { mutableStateOf("") }
    var dep by rememberSaveable { mutableStateOf("") }
    var maint by rememberSaveable { mutableStateOf("") }
    var fuel by rememberSaveable { mutableStateOf("") }
    var lube by rememberSaveable { mutableStateOf("") }
    var admin by rememberSaveable { mutableStateOf("") }
    var annualKm by rememberSaveable { mutableStateOf("") }
    var rounding by rememberSaveable { mutableStateOf("") }
    var salary by rememberSaveable { mutableStateOf("") }
    var diet by rememberSaveable { mutableStateOf("") }
    var outputCurrency by rememberSaveable { mutableStateOf("USD") }
    var outputRate by rememberSaveable { mutableStateOf("1") }
    var entityName by rememberSaveable { mutableStateOf(profile.name) }
    var entityContact by rememberSaveable { mutableStateOf(profile.contact) }
    var logoUri by rememberSaveable { mutableStateOf(profile.logoUri) }

    val logoPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            logoUri = uri.toString()
        }
    }

    LaunchedEffect(p) {
        margin = fmtInput(p.commercialMarginPct * 100)
        dep = fmtInput(p.annualDepreciationPct * 100)
        maint = fmtInput(p.annualMaintenancePct * 100)
        fuel = fmtInput(p.fuelPriceUsdPerLiter)
        lube = fmtInput(p.lubricantsPctOfFuel * 100)
        admin = fmtInput(p.adminIndirectPct * 100)
        annualKm = fmtInput(p.annualReferenceKm)
        rounding = fmtInput(p.offerRoundingUsd)
        salary = fmtInput(p.standardDailySalaryUsd)
        diet = fmtInput(p.standardDailyDietUsd)
        outputCurrency = p.outputCurrency
        outputRate = fmtInput(p.outputExchangeRatePerUsd)
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("Ajustes", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        if (showHelp) {
            HelpCard(
                "Configure CotiRuta",
                "Defina moneda, costos y los datos que aparecerán en PDF e imagen. Los cálculos internos se mantienen en USD y la salida se convierte con su tasa."
            )
        }

        Text("Identidad de la entidad", fontWeight = FontWeight.Bold)
        Field("Nombre comercial / entidad", entityName) { entityName = it }
        Field("Contacto / dirección / teléfono", entityContact) { entityContact = it }
        OutlinedButton(
            onClick = { logoPicker.launch(arrayOf("image/png", "image/jpeg", "image/webp")) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (logoUri.isBlank()) "SELECCIONAR LOGO" else "CAMBIAR LOGO")
        }
        if (logoUri.isNotBlank()) {
            Text("Logo configurado para documentos.", style = MaterialTheme.typography.bodySmall)
        }
        Button(
            onClick = {
                val updated = BusinessProfileManager.Profile(
                    name = entityName.trim(),
                    contact = entityContact.trim(),
                    logoUri = logoUri
                )
                BusinessProfileManager.save(context, updated)
                onProfileSaved(updated)
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("GUARDAR IDENTIDAD")
        }

        HorizontalDivider()
        Text("Moneda de salida", fontWeight = FontWeight.Bold)
        Field("Código de moneda (USD, CUP, EUR, BRL...)", outputCurrency) {
            outputCurrency = it.uppercase(Locale.US).take(5)
        }
        NumberField("Tasa: unidades de salida por 1 USD", outputRate) { outputRate = it }

        HorizontalDivider()
        Text("Parámetros de costo", fontWeight = FontWeight.Bold)
        NumberField("Margen comercial %", margin) { margin = it }
        NumberField("Depreciación anual %", dep) { dep = it }
        NumberField("Mantenimiento anual %", maint) { maint = it }
        NumberField("Precio combustible USD/L", fuel) { fuel = it }
        NumberField("Lubricantes y grasas % del combustible", lube) { lube = it }
        NumberField("Administración / indirectos %", admin) { admin = it }
        NumberField("Km anuales de referencia", annualKm) { annualKm = it }
        NumberField("Redondeo comercial USD", rounding) { rounding = it }
        NumberField("Salario diario estándar USD", salary) { salary = it }
        NumberField("Dieta diaria estándar USD", diet) { diet = it }

        Button(
            onClick = {
                onSave(
                    CostParametersEntity(
                        commercialMarginPct = (parseNumber(margin) ?: 0.0) / 100,
                        annualDepreciationPct = (parseNumber(dep) ?: 0.0) / 100,
                        annualMaintenancePct = (parseNumber(maint) ?: 0.0) / 100,
                        fuelPriceUsdPerLiter = parseNumber(fuel) ?: 0.0,
                        lubricantsPctOfFuel = (parseNumber(lube) ?: 0.0) / 100,
                        adminIndirectPct = (parseNumber(admin) ?: 0.0) / 100,
                        annualReferenceKm = parseNumber(annualKm) ?: 0.0,
                        offerRoundingUsd = parseNumber(rounding) ?: 0.0,
                        standardDailySalaryUsd = parseNumber(salary) ?: 0.0,
                        standardDailyDietUsd = parseNumber(diet) ?: 0.0,
                        outputCurrency = outputCurrency.trim().uppercase(Locale.US).ifBlank { "USD" },
                        outputExchangeRatePerUsd = safeRate(parseNumber(outputRate) ?: 1.0)
                    )
                )
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("GUARDAR PARÁMETROS")
        }

        HorizontalDivider()
        OutlinedButton(onClick = onOpenLicense, modifier = Modifier.fillMaxWidth()) {
            Text("LICENCIA Y ACTIVACIÓN")
        }

        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun HistoryScreen(
    items: List<QuoteEntity>,
    showHelp: Boolean,
    demoMode: Boolean,
    onDuplicate: (QuoteEntity) -> Unit
) {
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Historial", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        if (showHelp) {
            HelpCard(
                "Historial",
                if (demoMode)
                    "En Demo se muestran las ${DemoManager.MAX_HISTORY_VISIBLE} cotizaciones más recientes. Active la licencia para acceder al historial completo."
                else
                    "Cada cotización conserva la moneda y tasa utilizadas. Puede duplicarla para preparar rápidamente una nueva oferta."
            )
        }
        if (items.isEmpty()) Text("No hay cotizaciones guardadas.")

        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items.forEach { q ->
                ElevatedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(q.service, fontWeight = FontWeight.Bold)
                        Text("${q.origin.ifBlank { "—" }} → ${q.destination.ifBlank { "—" }} · ${q.tripType}")
                        Text("${format2(q.totalDistanceKm)} km · ${q.vehicleName}")
                        Text(
                            money(q.offerPriceUsd, q.currencyCode, q.currencyRatePerUsd),
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(q.createdAt)),
                            style = MaterialTheme.typography.bodySmall
                        )
                        TextButton(onClick = { onDuplicate(q) }) {
                            Text("DUPLICAR COTIZACIÓN")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HelpCard(title: String, body: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
            Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
    }
}

@Composable
private fun AboutDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("CotiRuta") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Costos y tarifas de transporte", fontWeight = FontWeight.SemiBold)
                Text("Versión ${BuildConfig.VERSION_NAME}")
                HorizontalDivider()
                Text("Desarrollado por ASSI SURL")
                Text("Herramienta móvil para estimar costos operativos, definir tarifas comerciales y generar cotizaciones profesionales de transporte.")
                Text("Licencia individual por teléfono/dispositivo. Solicitudes: ${LicenseManager.LICENSE_REQUEST_EMAIL}")
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) { Text("Cerrar") }
        }
    )
}

@Composable
private fun Field(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(value = value, onValueChange = onChange, label = { Text(label) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
}

@Composable
private fun NumberField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(value = value, onValueChange = onChange, label = { Text(label) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
}

private fun parseNumber(v: String): Double? = v.trim().replace(',', '.').toDoubleOrNull()
private fun fmtInput(v: Double): String = if (v % 1.0 == 0.0) v.toLong().toString() else String.format(Locale.US, "%.3f", v).trimEnd('0').trimEnd('.')
private fun format1(v: Double): String = String.format(Locale.US, "%.1f", v)
private fun format2(v: Double): String = String.format(Locale.US, "%.2f", v)
private fun usd(v: Double): String = NumberFormat.getCurrencyInstance(Locale.US).format(v)
private fun safeRate(v: Double): Double = if (v > 0.0) v else 1.0
private fun money(valueUsd: Double, code: String, rate: Double): String {
    val normalized = code.trim().uppercase(Locale.US).ifBlank { "USD" }
    return String.format(Locale.US, "%s %,.2f", normalized, valueUsd * safeRate(rate))
}
private fun outputMoney(valueUsd: Double, p: CostParametersEntity): String =
    money(valueUsd, p.outputCurrency, p.outputExchangeRatePerUsd)
