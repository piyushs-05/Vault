package com.piyush.vault.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "sms_envelopes",indices = [Index(value = ["fingerprint"],unique = true),Index(value = ["processingState"]),Index(value = ["expiresAtEpochMillis"])])
data class SmsEnvelopeEntity(
    @PrimaryKey val id: String,
    val fingerprint: String,
    val sender: String,
    val rawBody: String?,
    val sentAtEpochMillis: Long,
    val receivedAtEpochMillis: Long,
    val providerRowId: String?,
    val consentGeneration: Long,
    val processingState: String,
    val attempts: Int,
    val errorCode: String?,
    val expiresAtEpochMillis: Long,
    @ColumnInfo(defaultValue = "'UNKNOWN'") val captureOrigin: String = "UNKNOWN",
    @ColumnInfo(defaultValue = "'NONE'") val promptState: String = "NONE",
)
