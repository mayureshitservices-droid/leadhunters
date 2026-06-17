package com.example.leadhunters.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.leadhunters.data.local.dao.TeleCallerDao
import com.example.leadhunters.data.local.entities.AppCallLog
import com.example.leadhunters.data.local.entities.CallOutcome
import com.example.leadhunters.data.local.entities.Lead
import com.example.leadhunters.data.local.entities.ProcessedLead
import com.example.leadhunters.data.local.entities.Reminder
import com.example.leadhunters.data.local.entities.SyncItem
import com.example.leadhunters.data.local.entities.WhatsAppTemplate

import androidx.room.TypeConverters

@Database(
    entities = [Lead::class, AppCallLog::class, CallOutcome::class, SyncItem::class, WhatsAppTemplate::class, Reminder::class, ProcessedLead::class],
    version = 10,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun teleCallerDao(): TeleCallerDao
}
