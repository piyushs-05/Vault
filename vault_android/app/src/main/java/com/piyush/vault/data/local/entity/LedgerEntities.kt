package com.piyush.vault.data.local.entity

import androidx.room.*
import com.piyush.vault.domain.model.*
import java.time.LocalDate
import java.time.LocalTime

@Entity(tableName = "accounts", indices = [Index(value = ["systemKey"], unique = true), Index(value = ["institutionLabel", "lastFour"])])
data class AccountEntity(
    @PrimaryKey val id: String,
    val displayName: String,
    val ledgerType: LedgerType,
    val accountKind: AccountKind?,
    val currencyCode: String,
    val systemRole: SystemAccountRole?,
    val systemKey: String?,
    val institutionLabel: String?,
    val lastFour: String?,
    val origin: AccountOrigin,
    val isArchived: Boolean,
    val openingBalanceMinor: Long?,
    val openingBalanceDate: LocalDate?,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
)

@Entity(tableName = "financial_events",
    foreignKeys = [ForeignKey(entity = FinancialEventEntity::class, parentColumns = ["id"], childColumns = ["originalEventId"], onDelete = ForeignKey.RESTRICT)],
    indices = [Index(value = ["originalEventId"]), Index(value = ["systemKey"], unique = true), Index(value = ["occurredDate", "occurredTime"]), Index(value = ["kind", "occurredDate"])])
data class FinancialEventEntity(
    @PrimaryKey val id: String,
    val kind: EventKind,
    val currencyCode: String,
    val description: String,
    val userNote: String?,
    val occurredDate: LocalDate,
    val occurredTime: LocalTime?,
    val dateBasis: String,
    val originalEventId: String?,
    val systemKey: String?,
    val revision: Long,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
)

@Entity(tableName = "ledger_entries", foreignKeys = [
    ForeignKey(entity = FinancialEventEntity::class, parentColumns = ["id"], childColumns = ["eventId"], onDelete = ForeignKey.CASCADE),
    ForeignKey(entity = AccountEntity::class, parentColumns = ["id"], childColumns = ["accountId"], onDelete = ForeignKey.RESTRICT),
    ForeignKey(entity = CategoryEntity::class, parentColumns = ["id"], childColumns = ["categoryId"], onDelete = ForeignKey.SET_NULL)],
    indices = [Index(value = ["eventId", "position"], unique = true), Index(value = ["accountId"]), Index(value = ["categoryId"])])
data class LedgerEntryEntity(
    @PrimaryKey val id: String,
    val eventId: String,
    val accountId: String,
    val position: Int,
    val amountMinor: Long,
    val categoryId: String?,
)

@Entity(tableName = "source_records", foreignKeys = [
    ForeignKey(entity = FinancialEventEntity::class, parentColumns = ["id"], childColumns = ["eventId"], onDelete = ForeignKey.SET_NULL),
    ForeignKey(entity = ImportBatchEntity::class, parentColumns = ["id"], childColumns = ["importBatchId"], onDelete = ForeignKey.SET_NULL),
    ForeignKey(entity = SmsEnvelopeEntity::class, parentColumns = ["id"], childColumns = ["smsEnvelopeId"], onDelete = ForeignKey.SET_NULL),
    ForeignKey(entity = AccountEntity::class, parentColumns = ["id"], childColumns = ["matchedAccountId"], onDelete = ForeignKey.SET_NULL)],
    indices = [Index(value = ["sourceType", "sourceKey"], unique = true), Index(value = ["eventId"]), Index(value = ["importBatchId"]), Index(value = ["smsEnvelopeId"],unique = true), Index(value = ["matchedAccountId"]), Index(value = ["sourceType","state"])])
data class SourceRecordEntity(
    @PrimaryKey val id: String,
    val sourceType: TransactionSource,
    val sourceKey: String,
    val eventId: String?,
    val state: String,
    val importBatchId: String?,
    val sourceRowNumber: Int?,
    val sourceDescription: String,
    val observedAtEpochMillis: Long?,
    val smsEnvelopeId: String? = null,
    val matchedAccountId: String? = null,
    val detectedInstitution: String? = null,
    val maskedLastFour: String? = null,
    val observedAmountMinor: Long? = null,
    val observedCurrency: String? = null,
    val observedDirection: TransactionDirection? = null,
    val observedDate: LocalDate? = null,
    val observedTime: LocalTime? = null,
    val dateBasis: String? = null,
    val suggestedKind: EventKind? = null,
    val externalReference: String? = null,
    val reviewReason: String? = null,
    val parserId: String? = null,
    val parserVersion: Int? = null,
)

data class AccountBalanceRow(@Embedded val account: AccountEntity, val balanceMinor: Long)

fun AccountEntity.toDomain() = Account(id, displayName, ledgerType, accountKind, currencyCode, systemRole, institutionLabel, lastFour, origin, isArchived, openingBalanceMinor, openingBalanceDate, createdAtEpochMillis, updatedAtEpochMillis)
fun Account.toEntity() = AccountEntity(id, displayName, ledgerType, accountKind, currencyCode, systemRole, systemRole?.id(currencyCode), institutionLabel, lastFour, origin, isArchived, openingBalanceMinor, openingBalanceDate, createdAtEpochMillis, updatedAtEpochMillis)
fun FinancialEventEntity.toDomain() = LedgerEvent(id, kind, currencyCode, description, userNote, occurredDate, occurredTime, dateBasis, originalEventId, systemKey, revision, createdAtEpochMillis, updatedAtEpochMillis)
fun LedgerEvent.toEntity() = FinancialEventEntity(id, kind, currencyCode, description, userNote, occurredDate, occurredTime, dateBasis, originalEventId, systemKey, revision, createdAtEpochMillis, updatedAtEpochMillis)
fun LedgerEntryEntity.toDomain() = LedgerEntry(id, eventId, accountId, position, amountMinor, categoryId)
fun LedgerEntry.toEntity() = LedgerEntryEntity(id, eventId, accountId, position, amountMinor, categoryId)
fun SourceRecordEntity.toDomain() = SourceRecord(
    id,sourceType,sourceKey,eventId,state,importBatchId,sourceRowNumber,sourceDescription,observedAtEpochMillis,
    smsEnvelopeId,matchedAccountId,
    if (observedDate != null && suggestedKind != null) ParsedFinancialSms(sourceDescription,detectedInstitution,maskedLastFour,observedAmountMinor,
        observedCurrency ?: "INR",observedDirection,observedDate,observedTime,dateBasis ?: "SMS_DATE_UNCERTAIN",suggestedKind,externalReference,reviewReason ?: "Confirm this alert") else null,
    parserId,parserVersion,
)
fun SourceRecord.toEntity() = SourceRecordEntity(
    id,sourceType,sourceKey,eventId,state,importBatchId,sourceRowNumber,sourceDescription,observedAtEpochMillis,
    smsEnvelopeId,matchedAccountId,observation?.institution,observation?.lastFour,observation?.amountMinor,observation?.currencyCode,
    observation?.direction,observation?.date,observation?.time,observation?.dateBasis,observation?.kind,observation?.reference,observation?.reviewReason,parserId,parserVersion,
)
