package com.example.leadhunters.data.local.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "leads")
data class Lead(
    @PrimaryKey val id: String,
    val name: String,
    val phoneNumber: String,
    val status: String = "PENDING",
    val businessOwnerId: String,
    @ColumnInfo(name = "businessOwnerName") val campaignName: String,
    val lastCallTimestamp: Long? = null,
    val callCount: Int = 0,
    val additionalData: Map<String, Any>? = null
)
