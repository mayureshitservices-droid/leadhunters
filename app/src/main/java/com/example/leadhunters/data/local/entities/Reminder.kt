package com.example.leadhunters.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "reminders")
data class Reminder(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val customerName: String,
    val phoneNumber: String,
    val reminderTime: Long,
    val status: String = "PENDING", // PENDING, COMPLETED, DISMISSED
    val closingFormat: String? = null,
    val ptpAmount: Double? = null,
    val createdAt: Long = System.currentTimeMillis()
)
