package com.example.leadhunters.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "whatsapp_templates")
data class WhatsAppTemplate(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val message: String
)
