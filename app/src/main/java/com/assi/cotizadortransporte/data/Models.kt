package com.assi.cotizadortransporte.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "vehicles")
data class VehicleEntity(
    @PrimaryKey val id: String,
    val name: String,
    val serviceType: String,
    val vehicleValueUsd: Double,
    val equipmentValueUsd: Double,
    val totalAftUsd: Double,
    val fuelKmPerLiter: Double,
    val notes: String = ""
)

@Entity(tableName = "cost_parameters")
data class CostParametersEntity(
    @PrimaryKey val id: Int = 1,
    val commercialMarginPct: Double = 0.30,
    val annualDepreciationPct: Double = 0.20,
    val annualMaintenancePct: Double = 0.12,
    val fuelPriceUsdPerLiter: Double = 2.50,
    val lubricantsPctOfFuel: Double = 0.08,
    val adminIndirectPct: Double = 0.10,
    val annualReferenceKm: Double = 100000.0,
    val offerRoundingUsd: Double = 5.0,
    val standardDailySalaryUsd: Double = 43.15,
    val standardDailyDietUsd: Double = 5.115,
    val outputCurrency: String = "USD",
    val outputExchangeRatePerUsd: Double = 1.0
)

@Entity(tableName = "quotes")
data class QuoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val createdAt: Long,
    val client: String,
    val service: String,
    val origin: String,
    val destination: String,
    val tripType: String,
    val baseDistanceKm: Double,
    val totalDistanceKm: Double,
    val days: Int,
    val drivers: Int,
    val vehicleId: String,
    val vehicleName: String,
    val totalCostUsd: Double,
    val commercialMarginPct: Double,
    val calculatedPriceUsd: Double,
    val offerPriceUsd: Double,
    val offerPricePerKmUsd: Double,
    val currencyCode: String = "USD",
    val currencyRatePerUsd: Double = 1.0
)
