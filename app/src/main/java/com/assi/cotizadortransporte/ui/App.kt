package com.assi.cotizadortransporte.ui

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
import com.assi.cotizadortransporte.data.CostParametersEntity
import com.assi.cotizadortransporte.data.QuoteEntity
import com.assi.cotizadortransporte.data.VehicleEntity
import com.assi.cotizadortransporte.domain.QuoteCalculator
import com.assi.cotizadortransporte.share.QuoteShareUtil
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class Screen(val label: String) {
    QUOTE("Cotizar"), VEHICLES("Vehículos"), PARAMETERS("Parámetros"), HISTORY("Historial")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransportCostApp(vm: AppViewModel) {
    val vehicles by vm.vehicles.collectAsStateWithLifecycle()
    val params by vm.parameters.collectAsStateWithLifecycle()
    val quotes by vm.quotes.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    var screen by rememberSaveable { mutableStateOf(Screen.QUOTE) }
    val snackbarHost = remember { SnackbarHostState() }

    LaunchedEffect(message) {
        message?.let {
            snackbarHost.showSnackbar(it)
            vm.consumeMessage()
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("ASSI · Cotizador Transporte") }) },
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
                Screen.QUOTE -> QuoteScreen(vehicles, params, vm::saveQuote)
                Screen.VEHICLES -> VehiclesScreen(vehicles, vm::importVehicles)
                Screen.PARAMETERS -> ParametersScreen(params, vm::saveParameters)
                Screen.HISTORY -> HistoryScreen(quotes)
            }
        }
    }
}

@Composable
private fun QuoteScreen(
    vehicles: List<VehicleEntity>,
    params: CostParametersEntity,
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
        Text("Nueva oferta", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        if (vehicles.isEmpty()) {
            ElevatedCard { Text("Primero importe el catálogo de vehículos desde XLSX o CSV.", Modifier.padding(16.dp)) }
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
                runCatching {
                    val v = requireNotNull(selected) { "Seleccione un vehículo." }
                    val km = distance
                    val d = days.toIntOrNull() ?: 0
                    val ch = drivers.toIntOrNull() ?: 0
                    val sal = parseNumber(salary) ?: 0.0
                    val die = parseNumber(diet) ?: 0.0
                    val margin = parseNumber(marginOverride)?.div(100.0)
                    QuoteCalculator.calculate(v, params, QuoteCalculator.Input(km, d, ch, sal, die, margin))
                }.onSuccess { result = it }.onFailure { error = it.message }
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("CALCULAR OFERTA") }

        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        result?.let { r ->
            CostBreakdown(r)
            ClientSummary(service, origin, destination, if (roundTrip) "Redondo" else "Sencillo", distance, selected!!.name, days.toIntOrNull() ?: 0, r)

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
                                offerPricePerKmUsd = r.offerPricePerKmUsd
                            )
                        )
                    },
                    modifier = Modifier.weight(1f)
                ) { Text("Guardar") }
                OutlinedButton(
                    onClick = {
                        QuoteShareUtil.shareImage(context, presentation(client, service, origin, destination, roundTrip, distance, selected, days, r))
                    },
                    modifier = Modifier.weight(1f)
                ) { Text("Imagen") }
                OutlinedButton(
                    onClick = {
                        QuoteShareUtil.sharePdf(context, presentation(client, service, origin, destination, roundTrip, distance, selected, days, r))
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
    r: QuoteCalculator.Result
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
    pricePerKmUsd = r.offerPricePerKmUsd
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
private fun CostBreakdown(r: QuoteCalculator.Result) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Detalle de costo", fontWeight = FontWeight.Bold)
            CostRow("Combustible", r.fuelUsd)
            CostRow("Salarios", r.salariesUsd)
            CostRow("Dietas", r.dietsUsd)
            CostRow("Depreciación", r.depreciationUsd)
            CostRow("Mantenimiento", r.maintenanceUsd)
            CostRow("Lubricantes y grasas", r.lubricantsUsd)
            CostRow("Administración / indirectos", r.adminIndirectUsd)
            HorizontalDivider()
            CostRow("Costo total", r.totalCostUsd, true)
            Text("Margen comercial: ${format1(r.marginPct * 100)}% · ${usd(r.commercialMarkupUsd)}")
            CostRow("Precio calculado", r.calculatedPriceUsd)
            Text("PRECIO OFERTA: ${usd(r.offerPriceUsd)}", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Text("Tarifa: ${usd(r.offerPricePerKmUsd)} / km")
        }
    }
}

@Composable
private fun CostRow(label: String, value: Double, bold: Boolean = false) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal)
        Text(usd(value), fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal)
    }
}

@Composable
private fun ClientSummary(service: String, origin: String, destination: String, type: String, km: Double, vehicle: String, days: Int, r: QuoteCalculator.Result) {
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
            Text("PRECIO OFERTA ${usd(r.offerPriceUsd)}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun VehiclesScreen(vehicles: List<VehicleEntity>, onImport: (Uri, Boolean) -> Unit) {
    var replace by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { onImport(it, replace) }
    }
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Vehículos", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("Importa la hoja Vehiculos del Excel o un CSV con los mismos encabezados.")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { replace = false; launcher.launch(arrayOf("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "text/csv", "text/comma-separated-values")) }) {
                Text("Importar / actualizar")
            }
            OutlinedButton(onClick = { replace = true; launcher.launch(arrayOf("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "text/csv", "text/comma-separated-values")) }) {
                Text("Reemplazar catálogo")
            }
        }
        HorizontalDivider()
        if (vehicles.isEmpty()) Text("Catálogo vacío.")
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            vehicles.forEach { v ->
                ElevatedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text("${v.id} · ${v.name}", fontWeight = FontWeight.Bold)
                        if (v.serviceType.isNotBlank()) Text(v.serviceType)
                        Text("AFT: ${usd(v.totalAftUsd)} · Consumo: ${format2(v.fuelKmPerLiter)} km/L")
                    }
                }
            }
        }
    }
}

@Composable
private fun ParametersScreen(p: CostParametersEntity, onSave: (CostParametersEntity) -> Unit) {
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
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Parámetros generales", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
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
                        standardDailyDietUsd = parseNumber(diet) ?: 0.0
                    )
                )
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("GUARDAR PARÁMETROS") }
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun HistoryScreen(items: List<QuoteEntity>) {
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Historial", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        if (items.isEmpty()) Text("No hay cotizaciones guardadas.")
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items.forEach { q ->
                ElevatedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text(q.service, fontWeight = FontWeight.Bold)
                        Text("${q.origin.ifBlank { "—" }} → ${q.destination.ifBlank { "—" }} · ${q.tripType}")
                        Text("${format2(q.totalDistanceKm)} km · ${q.vehicleName}")
                        Text("${usd(q.offerPriceUsd)}", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                        Text(SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(q.createdAt)), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
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
