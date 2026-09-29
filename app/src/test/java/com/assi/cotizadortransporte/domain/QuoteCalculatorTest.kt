package com.assi.cotizadortransporte.domain

import com.assi.cotizadortransporte.data.CostParametersEntity
import com.assi.cotizadortransporte.data.VehicleEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class QuoteCalculatorTest {
    @Test
    fun actros_935km_matches_excel_model() {
        val vehicle = VehicleEntity(
            id = "MCV-ACTROS-40",
            name = "Cuña MCV Actros C/Semi",
            serviceType = "Contenedor 40 pies",
            vehicleValueUsd = 139120.0,
            equipmentValueUsd = 50689.0,
            totalAftUsd = 189809.0,
            fuelKmPerLiter = 2.2
        )
        val p = CostParametersEntity()
        val r = QuoteCalculator.calculate(
            vehicle, p,
            QuoteCalculator.Input(
                distanceKm = 935.0,
                days = 2,
                drivers = 1,
                dailySalaryUsd = 43.15,
                dailyDietUsd = 5.115
            )
        )
        assertEquals(1062.50, r.fuelUsd, 0.01)
        assertEquals(354.94, r.depreciationUsd, 0.01)
        assertEquals(212.97, r.maintenanceUsd, 0.01)
        assertEquals(1993.13, r.totalCostUsd, 0.01)
        assertEquals(2591.07, r.calculatedPriceUsd, 0.01)
        assertEquals(2595.00, r.offerPriceUsd, 0.01)
    }

    @Test
    fun round_up_never_quotes_below_calculated_price() {
        val vehicle = VehicleEntity("V1", "Test", "", 10000.0, 0.0, 10000.0, 10.0)
        val p = CostParametersEntity(offerRoundingUsd = 5.0)
        val r = QuoteCalculator.calculate(vehicle, p, QuoteCalculator.Input(100.0, 1, 1, 10.0, 5.0))
        assert(r.offerPriceUsd >= r.calculatedPriceUsd)
        assertEquals(0.0, r.offerPriceUsd % 5.0, 0.0001)
    }
}
