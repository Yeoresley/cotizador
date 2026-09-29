package com.assi.cotizadortransporte.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.assi.cotizadortransporte.data.AppDatabase
import com.assi.cotizadortransporte.data.AppRepository
import com.assi.cotizadortransporte.data.CostParametersEntity
import com.assi.cotizadortransporte.data.QuoteEntity
import com.assi.cotizadortransporte.data.VehicleEntity
import com.assi.cotizadortransporte.importer.VehicleImportParser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val repo = AppRepository(AppDatabase.get(application).dao())

    val vehicles = repo.vehicles.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val parameters = repo.parameters.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CostParametersEntity())
    val quotes = repo.quotes.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _message = MutableStateFlow<String?>(null)
    val message = _message.asStateFlow()

    init {
        viewModelScope.launch { repo.ensureDefaults() }
    }

    fun consumeMessage() { _message.value = null }

    fun saveParameters(item: CostParametersEntity) {
        viewModelScope.launch {
            repo.saveParameters(item)
            _message.value = "Parámetros guardados."
        }
    }

    fun saveVehicle(item: VehicleEntity) {
        viewModelScope.launch {
            repo.saveVehicle(item)
            _message.value = "Vehículo guardado."
        }
    }

    fun importVehicles(uri: Uri, replace: Boolean, maxVehiclesTotal: Int? = null) {
        viewModelScope.launch {
            runCatching { VehicleImportParser.parse(getApplication(), uri) }
                .onSuccess { result ->
                    if (result.vehicles.isEmpty()) {
                        _message.value = "No se encontraron vehículos válidos para importar."
                    } else {
                        val imported = if (maxVehiclesTotal == null) {
                            result.vehicles
                        } else if (replace) {
                            result.vehicles.take(maxVehiclesTotal)
                        } else {
                            val currentIds = vehicles.value.map { it.id }.toMutableSet()
                            val accepted = mutableListOf<VehicleEntity>()
                            var total = currentIds.size
                            result.vehicles.forEach { item ->
                                if (item.id in currentIds) {
                                    accepted += item
                                } else if (total < maxVehiclesTotal) {
                                    accepted += item
                                    currentIds += item.id
                                    total++
                                }
                            }
                            accepted
                        }

                        if (imported.isEmpty()) {
                            _message.value = "El límite del modo Demo no permite agregar más vehículos."
                        } else {
                            repo.importVehicles(imported, replace)
                            val limited = if (maxVehiclesTotal != null && imported.size < result.vehicles.size) {
                                " · catálogo limitado por el modo Demo"
                            } else ""
                            val skipped = if (result.skippedRows > 0) " · ${result.skippedRows} fila(s) omitida(s)" else ""
                            _message.value = "${imported.size} vehículo(s) importado(s)$limited$skipped."
                        }
                    }
                }
                .onFailure { _message.value = it.message ?: "Error al importar el archivo." }
        }
    }

    fun saveQuote(item: QuoteEntity) {
        viewModelScope.launch {
            repo.saveQuote(item)
            _message.value = "Cotización guardada en el historial."
        }
    }
}
