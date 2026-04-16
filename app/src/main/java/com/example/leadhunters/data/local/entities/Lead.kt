package com.example.leadhunters.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "leads")
data class Lead(
    @PrimaryKey val id: String,
    val name: String,
    val phoneNumber: String,
    val status: String = "PENDING",
    val businessOwnerId: String,
    val businessOwnerName: String,
    val lastCallTimestamp: Long? = null,
    val callCount: Int = 0
)
