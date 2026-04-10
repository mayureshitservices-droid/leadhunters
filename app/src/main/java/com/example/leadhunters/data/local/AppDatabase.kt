package com.example.leadhunters.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.leadhunters.data.local.dao.TeleCallerDao
import com.example.leadhunters.data.local.entities.AppCallLog
import com.example.leadhunters.data.local.entities.CallOutcome
import com.example.leadhunters.data.local.entities.Lead
import com.example.leadhunters.data.local.entities.Reminder
import com.example.leadhunters.data.local.entities.SyncItem
import com.example.leadhunters.data.local.entities.WhatsAppTemplate

@Database(
    entities = [Lead::class, AppCallLog::class, CallOutcome::class, SyncItem::class, WhatsAppTemplate::class, Reminder::class],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun teleCallerDao(): TeleCallerDao
}
