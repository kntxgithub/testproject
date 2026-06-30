package com.example.caltrack.data

import androidx.lifecycle.LiveData
import androidx.room.*

@Dao
interface FoodDao {
    @Query("SELECT * FROM food_entries ORDER BY timestamp DESC")
    fun getAllEntries(): LiveData<List<FoodEntry>>

    @Query("SELECT * FROM food_entries ORDER BY timestamp DESC")
    suspend fun getAllEntriesSync(): List<FoodEntry>

    @Query("SELECT SUM(calories) FROM food_entries WHERE timestamp >= :startOfDay")
    fun getTodayCalories(startOfDay: Long): LiveData<Int?>

    @Insert
    suspend fun insert(entry: FoodEntry): Long

    @Delete
    suspend fun delete(entry: FoodEntry)

    @Query("DELETE FROM food_entries")
    suspend fun deleteAll()
}
