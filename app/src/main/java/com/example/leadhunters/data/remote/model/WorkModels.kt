package com.example.leadhunters.data.remote.model

import com.google.gson.annotations.SerializedName

data class LeadsResponse(
    @SerializedName("leads") val leads: List<LeadDto>
)

data class LeadDto(
    @SerializedName("id") val id: String,
    @SerializedName("name") val name: String,
    @SerializedName("phone") val phone: String,
    @SerializedName("status") val status: String,
    @SerializedName("business_owner_id") val businessOwnerId: String,
    @SerializedName("business_owner_name") val businessOwnerName: String
)

data class CallLogSyncRequest(
    @SerializedName("local_log_id") val localLogId: Long,
    @SerializedName("lead_id") val leadId: String,
    @SerializedName("duration_seconds") val durationSeconds: Int,
    @SerializedName("call_status") val callStatus: String,
    @SerializedName("outcome") val outcome: String? = null,
    @SerializedName("notes") val notes: String? = null
)

data class SyncResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("log_id") val logId: String? = null
)
