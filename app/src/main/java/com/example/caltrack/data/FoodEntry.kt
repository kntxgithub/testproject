package com.example.caltrack.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Entity(tableName = "food_entries")
data class FoodEntry(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val foodName: String,
    val calories: Int,
    val amount: String,
    val note: String,
    val imagePath: String,
    val timestamp: Long = System.currentTimeMillis()
) {
    fun getFormattedTime(): String {
        val dateTime = LocalDateTime.ofEpochSecond(
            timestamp / 1000, 0,
            java.time.ZoneOffset.systemDefault().getRules().getOffset(
                java.time.Instant.ofEpochMilli(timestamp)
            )
        )
        return dateTime.format(DateTimeFormatter.ofPattern("MM/dd HH:mm"))
    }
}
