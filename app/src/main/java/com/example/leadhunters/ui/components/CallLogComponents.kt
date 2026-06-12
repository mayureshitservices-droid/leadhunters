package com.example.leadhunters.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallMade
import androidx.compose.material.icons.automirrored.filled.CallMissed
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.Block
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.example.leadhunters.ui.theme.SuccessEmerald
import com.example.leadhunters.ui.theme.ErrorCoral
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun CallStatusBadge(status: String) {
    val (label, color, icon) = when (status.uppercase()) {
        "ANSWERED" -> Triple("Answered", SuccessEmerald, Icons.AutoMirrored.Filled.CallMade)
        "MISSED" -> Triple("Missed", ErrorCoral, Icons.AutoMirrored.Filled.CallMissed)
        "REJECTED", "UNANSWERED" -> Triple("Rejected", Color.Gray, Icons.Default.Block)
        else -> Triple(status, Color.Gray, Icons.AutoMirrored.Filled.HelpOutline)
    }
    
    AppBadge(text = label, icon = icon, backgroundColor = color, contentColor = color)
}

fun formatDuration(seconds: Long): String {
    val mins = seconds / 60
    val secs = seconds % 60
    return String.format(Locale.getDefault(), "%02d:%02d", mins, secs)
}

fun formatDurationMs(ms: Int): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
}

fun formatTimestamp(millis: Long): String {
    val sdf = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
    return sdf.format(Date(millis))
}
