package com.piyush.vault.data.local.dao

import androidx.room.*
import com.piyush.vault.data.local.entity.*
import kotlinx.coroutines.flow.Flow

@Dao
interface SmsDao {
    @Query("SELECT * FROM source_records WHERE id=:id AND sourceType='SMS'")
    fun observeSource(id: String): Flow<SourceRecordEntity?>
    @Query("SELECT * FROM sms_envelopes WHERE promptState IN ('READY','POSTED') ORDER BY receivedAtEpochMillis,id")
    suspend fun prompts(): List<SmsEnvelopeEntity>
    @Query("UPDATE sms_envelopes SET promptState=:state WHERE id=:id")
    suspend fun promptState(id: String,state: String)

    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun stage(envelope: SmsEnvelopeEntity): Long
    @Query("SELECT * FROM sms_envelopes WHERE id=:id") suspend fun envelope(id: String): SmsEnvelopeEntity?
    @Query("SELECT * FROM sms_envelopes WHERE processingState='PENDING' ORDER BY receivedAtEpochMillis,id LIMIT 50") suspend fun pending(): List<SmsEnvelopeEntity>
    @Query("SELECT * FROM source_records WHERE sourceType='SMS' AND state IN ('NEEDS_ACCOUNT','NEEDS_REVIEW') ORDER BY observedAtEpochMillis DESC,id DESC") fun observeCandidates(): Flow<List<SourceRecordEntity>>
    @Query("UPDATE sms_envelopes SET rawBody=NULL,processingState=:state,errorCode=:errorCode WHERE id=:id") suspend fun finish(id: String,state: String,errorCode: String? = null)
    @Query("UPDATE sms_envelopes SET attempts=attempts+1 WHERE id=:id") suspend fun attempted(id: String)
    @Query("UPDATE sms_envelopes SET rawBody=NULL,processingState='EXPIRED',errorCode='RAW_TEXT_EXPIRED' WHERE rawBody IS NOT NULL AND expiresAtEpochMillis<=:now") suspend fun expire(now: Long)
    @Query("UPDATE sms_envelopes SET rawBody=NULL,processingState='DISABLED' WHERE processingState='PENDING'") suspend fun clearPending()
    @Query("UPDATE source_records SET state='REJECTED',sourceDescription='',matchedAccountId=NULL,detectedInstitution=NULL,maskedLastFour=NULL,observedAmountMinor=NULL,observedCurrency=NULL,observedDirection=NULL,observedDate=NULL,observedTime=NULL,dateBasis=NULL,suggestedKind=NULL,externalReference=NULL,reviewReason=NULL WHERE sourceType='SMS' AND eventId IS NULL AND state IN ('NEEDS_ACCOUNT','NEEDS_REVIEW')") suspend fun clearCandidates()
}
