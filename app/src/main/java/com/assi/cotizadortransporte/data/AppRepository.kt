package com.assi.cotizadortransporte.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class AppRepository(private val dao: AppDao) {
    val vehicles: Flow<List<VehicleEntity>> = dao.observeVehicles()
    val parameters: Flow<CostParametersEntity> = dao.observeParameters().map { it ?: CostParametersEntity() }
    val quotes: Flow<List<QuoteEntity>> = dao.observeQuotes()

    suspend fun ensureDefaults() {
        if (dao.getParametersOnce() == null) dao.saveParameters(CostParametersEntity())
    }

    suspend fun saveParameters(item: CostParametersEntity) = dao.saveParameters(item)

    suspend fun importVehicles(items: List<VehicleEntity>, replace: Boolean) {
        if (replace) dao.deleteAllVehicles()
        dao.upsertVehicles(items)
    }

    suspend fun saveQuote(item: QuoteEntity) = dao.insertQuote(item)
}
