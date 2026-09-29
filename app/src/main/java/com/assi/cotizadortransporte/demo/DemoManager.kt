package com.assi.cotizadortransporte.demo

import android.content.Context

object DemoManager {
    private const val PREFS = "cotiruta_demo_prefs"
    private const val KEY_CALCULATIONS = "calculations_used"
    const val MAX_CALCULATIONS = 10
    const val MAX_VEHICLES = 2
    const val MAX_HISTORY_VISIBLE = 3

    data class Status(
        val usedCalculations: Int,
        val remainingCalculations: Int,
        val exhausted: Boolean
    )

    fun status(context: Context): Status {
        val used = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getInt(KEY_CALCULATIONS, 0)
            .coerceIn(0, MAX_CALCULATIONS)
        return Status(
            usedCalculations = used,
            remainingCalculations = (MAX_CALCULATIONS - used).coerceAtLeast(0),
            exhausted = used >= MAX_CALCULATIONS
        )
    }

    fun consumeCalculation(context: Context): Status {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val current = prefs.getInt(KEY_CALCULATIONS, 0).coerceAtLeast(0)
        val next = (current + 1).coerceAtMost(MAX_CALCULATIONS)
        prefs.edit().putInt(KEY_CALCULATIONS, next).apply()
        return status(context)
    }
}
