package com.assi.cotizadortransporte.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [VehicleEntity::class, CostParametersEntity::class, QuoteEntity::class],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): AppDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE cost_parameters ADD COLUMN outputCurrency TEXT NOT NULL DEFAULT 'USD'")
                db.execSQL("ALTER TABLE cost_parameters ADD COLUMN outputExchangeRatePerUsd REAL NOT NULL DEFAULT 1.0")
                db.execSQL("ALTER TABLE quotes ADD COLUMN currencyCode TEXT NOT NULL DEFAULT 'USD'")
                db.execSQL("ALTER TABLE quotes ADD COLUMN currencyRatePerUsd REAL NOT NULL DEFAULT 1.0")
            }
        }

        fun get(context: Context): AppDatabase = INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "assi_transport_quotes.db"
            )
                .addMigrations(MIGRATION_1_2)
                .build()
                .also { INSTANCE = it }
        }
    }
}
