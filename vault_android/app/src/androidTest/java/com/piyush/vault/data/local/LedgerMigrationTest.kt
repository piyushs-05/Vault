package com.piyush.vault.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import com.piyush.vault.data.local.migration.MIGRATION_1_2
import com.piyush.vault.data.local.migration.MIGRATION_2_3
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class LedgerMigrationTest {
    @get:Rule val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(),VaultDatabase::class.java.canonicalName!!,FrameworkSQLiteOpenHelperFactory())
    @Test fun migrationPreservesMoneyDatesCategoriesAndCsvSource() {
        val name = "ledger-migration"
        helper.createDatabase(name,1).apply {
            execSQL("INSERT INTO categories VALUES ('food','Food','EXPENSE',0,1)")
            execSQL("INSERT INTO import_batches VALUES ('batch','bank.csv','hash',1,1,0)")
            execSQL("INSERT INTO transactions VALUES ('old',12550,'INR','EXPENSE','Coffee','food','2026-11-01',NULL,1,2,'CSV','batch')")
            close()
        }
        val db = helper.runMigrationsAndValidate(name,2,true,MIGRATION_1_2)
        db.query("SELECT amountMinor,categoryId,occurredTime,source,importBatchId,accountId FROM transaction_history WHERE id='old'").use {
            assertTrue(it.moveToFirst()); assertEquals(12550L,it.getLong(0)); assertEquals("food",it.getString(1)); assertTrue(it.isNull(2))
            assertEquals("CSV",it.getString(3)); assertEquals("batch",it.getString(4)); assertTrue(it.isNull(5))
        }
        db.query("SELECT COUNT(*),SUM(amountMinor) FROM ledger_entries WHERE eventId='old'").use {
            assertTrue(it.moveToFirst()); assertEquals(2,it.getInt(0)); assertEquals(0L,it.getLong(1))
        }
        db.query("SELECT isArchived FROM categories WHERE id='food'").use { assertTrue(it.moveToFirst()); assertEquals(1,it.getInt(0)) }
        db.close()
    }
    @Test fun emptyDatabaseAlsoMigratesWithoutInventingAUserAccount() {
        val name = "ledger-empty"
        helper.createDatabase(name,1).close()
        val db = helper.runMigrationsAndValidate(name,2,true,MIGRATION_1_2)
        db.query("SELECT COUNT(*) FROM accounts WHERE systemRole IS NULL").use { assertTrue(it.moveToFirst()); assertEquals(0,it.getInt(0)) }
        db.close()
    }
    @Test fun originalDatabaseMigratesThroughSmsSchema() {
        val name = "ledger-sms-chain"
        helper.createDatabase(name,1).close()
        val db = helper.runMigrationsAndValidate(name,3,true,MIGRATION_1_2,MIGRATION_2_3)
        db.query("SELECT COUNT(*) FROM sms_envelopes").use { assertTrue(it.moveToFirst()); assertEquals(0,it.getInt(0)) }
        db.query("PRAGMA foreign_key_check").use { assertFalse(it.moveToFirst()) }
        db.close()
    }
}
