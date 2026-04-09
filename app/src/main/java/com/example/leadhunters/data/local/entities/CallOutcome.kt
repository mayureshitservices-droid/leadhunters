package com.example.leadhunters.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "call_outcomes")
data class CallOutcome(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val callLogId: Long,
    val leadId: String,
    val outcomeType: String, // Interested, Not Interested, Callback, etc.
    val notes: String? = null,
    val nextReminderTime: Long? = null,
    val timestamp: Long = System.currentTimeMillis()
)
