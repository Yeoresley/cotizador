package com.assi.cotizadortransporte.domain

import com.assi.cotizadortransporte.data.CostParametersEntity
import com.assi.cotizadortransporte.data.VehicleEntity
import kotlin.math.ceil

object QuoteCalculator {
    data class Input(
        val distanceKm: Double,
        val days: Int,
        val drivers: Int,
        val dailySalaryUsd: Double,
        val dailyDietUsd: Double,
        val marginPctOverride: Double? = null
    )

    data class Result(
        val liters: Double,
        val fuelUsd: Double,
        val salariesUsd: Double,
        val dietsUsd: Double,
        val depreciationUsd: Double,
        val maintenanceUsd: Double,
        val lubricantsUsd: Double,
        val operatingSubtotalUsd: Double,
        val adminIndirectUsd: Double,
        val totalCostUsd: Double,
        val marginPct: Double,
        val commercialMarkupUsd: Double,
        val calculatedPriceUsd: Double,
        val offerPriceUsd: Double,
        val costPerKmUsd: Double,
        val offerPricePerKmUsd: Double,
        val depreciationPerKmUsd: Double,
        val maintenancePerKmUsd: Double
    )

    fun calculate(vehicle: VehicleEntity, p: CostParametersEntity, i: Input): Result {
        require(i.distanceKm > 0.0) { "Los kilómetros deben ser mayores que cero." }
        require(i.days > 0) { "La cantidad de días debe ser mayor que cero." }
        require(i.drivers > 0) { "La cantidad de choferes debe ser mayor que cero." }
        require(vehicle.fuelKmPerLiter > 0.0) { "El índice de consumo debe ser mayor que cero." }
        require(p.annualReferenceKm > 0.0) { "Los km anuales de referencia deben ser mayores que cero." }

        val liters = i.distanceKm / vehicle.fuelKmPerLiter
        val fuel = liters * p.fuelPriceUsdPerLiter
        val salaries = i.days * i.drivers * i.dailySalaryUsd
        val diets = i.days * i.drivers * i.dailyDietUsd
        val depreciationPerKm = vehicle.totalAftUsd * p.annualDepreciationPct / p.annualReferenceKm
        val depreciation = depreciationPerKm * i.distanceKm
        val maintenancePerKm = vehicle.totalAftUsd * p.annualMaintenancePct / p.annualReferenceKm
        val maintenance = maintenancePerKm * i.distanceKm
        val lubricants = fuel * p.lubricantsPctOfFuel
        val subtotal = fuel + salaries + diets + depreciation + maintenance + lubricants
        val admin = subtotal * p.adminIndirectPct
        val total = subtotal + admin
        val margin = i.marginPctOverride ?: p.commercialMarginPct
        val markup = total * margin
        val calculated = total + markup
        val rounding = p.offerRoundingUsd
        val offer = if (rounding > 0.0) ceil(calculated / rounding) * rounding else calculated

        return Result(
            liters = liters,
            fuelUsd = fuel,
            salariesUsd = salaries,
            dietsUsd = diets,
            depreciationUsd = depreciation,
            maintenanceUsd = maintenance,
            lubricantsUsd = lubricants,
            operatingSubtotalUsd = subtotal,
            adminIndirectUsd = admin,
            totalCostUsd = total,
            marginPct = margin,
            commercialMarkupUsd = markup,
            calculatedPriceUsd = calculated,
            offerPriceUsd = offer,
            costPerKmUsd = total / i.distanceKm,
            offerPricePerKmUsd = offer / i.distanceKm,
            depreciationPerKmUsd = depreciationPerKm,
            maintenancePerKmUsd = maintenancePerKm
        )
    }
}
