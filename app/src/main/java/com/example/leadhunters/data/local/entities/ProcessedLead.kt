package com.example.leadhunters.data.local.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "processed_leads",
    indices = [
        Index(value = ["callTimestamp"]),
        Index(value = ["leadId"])
    ]
)
data class ProcessedLead(
    @PrimaryKey val callLogId: Long,
    val leadId: String,
    val name: String,
    val phoneNumber: String,
    val campaignName: String,
    val additionalData: Map<String, Any>? = null,
    val callStatus: String,
    val outcome: String? = null,
    val callTimestamp: Long,
    val duration: Long? = null,
    val recordingPath: String? = null
)
