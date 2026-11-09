package com.piyush.vault.data.local.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_3_4: Migration = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE sms_envelopes ADD COLUMN captureOrigin TEXT NOT NULL DEFAULT 'UNKNOWN'")
        db.execSQL("ALTER TABLE sms_envelopes ADD COLUMN promptState TEXT NOT NULL DEFAULT 'NONE'")
    }
}
