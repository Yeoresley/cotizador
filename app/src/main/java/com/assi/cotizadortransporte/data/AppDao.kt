package com.assi.cotizadortransporte.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AppDao {
    @Query("SELECT * FROM vehicles ORDER BY name")
    fun observeVehicles(): Flow<List<VehicleEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertVehicles(items: List<VehicleEntity>)

    @Query("DELETE FROM vehicles")
    suspend fun deleteAllVehicles()

    @Query("SELECT * FROM cost_parameters WHERE id = 1 LIMIT 1")
    fun observeParameters(): Flow<CostParametersEntity?>

    @Query("SELECT * FROM cost_parameters WHERE id = 1 LIMIT 1")
    suspend fun getParametersOnce(): CostParametersEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveParameters(item: CostParametersEntity)

    @Query("SELECT * FROM quotes ORDER BY createdAt DESC")
    fun observeQuotes(): Flow<List<QuoteEntity>>

    @Insert
    suspend fun insertQuote(item: QuoteEntity): Long
}
