package com.piyush.vault.data.local.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_2_3: Migration = object : Migration(2,3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS sms_envelopes (id TEXT NOT NULL PRIMARY KEY,fingerprint TEXT NOT NULL,sender TEXT NOT NULL,rawBody TEXT,sentAtEpochMillis INTEGER NOT NULL,receivedAtEpochMillis INTEGER NOT NULL,providerRowId TEXT,consentGeneration INTEGER NOT NULL,processingState TEXT NOT NULL,attempts INTEGER NOT NULL,errorCode TEXT,expiresAtEpochMillis INTEGER NOT NULL)")
        db.execSQL("CREATE UNIQUE INDEX index_sms_envelopes_fingerprint ON sms_envelopes(fingerprint)")
        db.execSQL("CREATE INDEX index_sms_envelopes_processingState ON sms_envelopes(processingState)")
        db.execSQL("CREATE INDEX index_sms_envelopes_expiresAtEpochMillis ON sms_envelopes(expiresAtEpochMillis)")
        db.execSQL("ALTER TABLE source_records ADD COLUMN smsEnvelopeId TEXT REFERENCES sms_envelopes(id) ON UPDATE NO ACTION ON DELETE SET NULL")
        db.execSQL("ALTER TABLE source_records ADD COLUMN matchedAccountId TEXT REFERENCES accounts(id) ON UPDATE NO ACTION ON DELETE SET NULL")
        listOf("detectedInstitution TEXT","maskedLastFour TEXT","observedAmountMinor INTEGER","observedCurrency TEXT","observedDirection TEXT","observedDate TEXT","observedTime TEXT","dateBasis TEXT","suggestedKind TEXT","externalReference TEXT","reviewReason TEXT","parserId TEXT","parserVersion INTEGER").forEach {
            db.execSQL("ALTER TABLE source_records ADD COLUMN $it")
        }
        db.execSQL("CREATE UNIQUE INDEX index_source_records_smsEnvelopeId ON source_records(smsEnvelopeId)")
        db.execSQL("CREATE INDEX index_source_records_matchedAccountId ON source_records(matchedAccountId)")
        db.execSQL("CREATE INDEX index_source_records_sourceType_state ON source_records(sourceType,state)")
    }
}
