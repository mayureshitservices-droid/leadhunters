package com.example.leadhunters.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

import androidx.room.Index

@Entity(
    tableName = "app_call_logs",
    indices = [
        Index(value = ["startTime"]),
        Index(value = ["phoneNumber"]),
        Index(value = ["isReconciled"]),
        Index(value = ["leadId"]),
        Index(value = ["systemCallLogId"])
    ]
)
data class AppCallLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val leadId: String,
    val phoneNumber: String,
    val startTime: Long,
    val endTime: Long? = null,
    val duration: Long? = null,
    val type: String, // OUTGOING, INCOMING, etc.
    val status: String, // ANSWERED, MISSED, REJECTED, CANCELLED
    val recordingPath: String? = null,
    val systemCallLogId: Long? = null,
    val isReconciled: Boolean = false
)
