package com.example.leadhunters.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "call_outcomes")
data class CallOutcome(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val callLogId: Long,
    val leadId: String,
    val customerName: String,
    val outcomeType: String, // Interested, Ordered, Booked, Remind later, Lost
    val remarks: String? = null,
    val nextReminderTime: Long? = null,
    val closingFormat: String? = null,
    val ptpAmount: Double? = null,
    val timestamp: Long = System.currentTimeMillis()
)
