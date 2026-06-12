package com.example.leadhunters.data.remote.model

import com.google.gson.annotations.SerializedName

@androidx.annotation.Keep
data class LeadDto(
    @SerializedName("id") val id: String,
    @SerializedName("name") val name: String,
    @SerializedName("phone") val phone: String,
    @SerializedName("status") val status: String,
    @SerializedName("business_owner_id") val businessOwnerId: String,
    @SerializedName("business_owner_name") val businessOwnerName: String,
    @SerializedName("campaign_name") val campaignName: String? = null,
    @SerializedName("additional_data") val additionalData: Map<String, Any>? = null
)

@androidx.annotation.Keep
data class CallLogSyncRequest(
    @SerializedName("local_log_id") val localLogId: String,
    @SerializedName("lead_id") val leadId: String,
    @SerializedName("duration_seconds") val durationSeconds: Int,
    @SerializedName("call_status") val callStatus: String,
    @SerializedName("outcome") val outcome: String? = null,
    @SerializedName("notes") val notes: String? = null,
    @SerializedName("next_reminder_time") val nextReminderTime: Long? = null
)

@androidx.annotation.Keep
data class SyncResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("log_id") val logId: String? = null
)

@androidx.annotation.Keep
data class CampaignDto(
    @SerializedName("name") val name: String,
    @SerializedName("total") val total: Int,
    @SerializedName("processed") val processed: Int,
    @SerializedName("pending") val pending: Int,
    @SerializedName("available") val available: Int
)

@androidx.annotation.Keep
data class CampaignsResponse(
    @SerializedName("campaigns") val campaigns: List<CampaignDto>
)

@androidx.annotation.Keep
data class ClaimCampaignRequest(
    @SerializedName("campaign_name") val campaignName: String
)

@androidx.annotation.Keep
data class TelecallerStatusRequest(
    @SerializedName("status") val status: String,
    @SerializedName("timestamp") val timestamp: Long
)

@androidx.annotation.Keep
data class ClaimCampaignResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("claimed_count") val claimedCount: Int,
    @SerializedName("leads") val leads: List<LeadDto>? = null
)

