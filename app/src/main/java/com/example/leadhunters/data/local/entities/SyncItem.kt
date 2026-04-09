package com.example.leadhunters.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sync_queue")
data class SyncItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String, // LEAD, CALL_LOG, OUTCOME
    val referenceId: String, // leadId or callLogId
    val operation: String, // CREATE, UPDATE, DELETE
    val payload: String, // JSON payload
    val status: String = "PENDING", // PENDING, SYNCING, COMPLETED, FAILED
    val retryCount: Int = 0,
    val timestamp: Long = System.currentTimeMillis()
)
