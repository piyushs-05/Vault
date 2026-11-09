package com.piyush.vault.data.local.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Frozen version-2 SQL. Future migrations must not alter this contract. */
object LedgerSchemaV2 {
    const val HISTORY_SELECT = "SELECT e.id, ABS(p.amountMinor) AS amountMinor, e.currencyCode, CASE WHEN p.amountMinor<0 THEN 'EXPENSE' ELSE 'INCOME' END AS direction, e.kind,e.description,e.userNote,q.categoryId,c.name AS categoryName, e.occurredDate,e.occurredTime,e.createdAtEpochMillis,e.updatedAtEpochMillis, COALESCE((SELECT s.sourceType FROM source_records s WHERE s.eventId=e.id ORDER BY s.id LIMIT 1),'LEGACY') AS source, (SELECT s.importBatchId FROM source_records s WHERE s.eventId=e.id ORDER BY s.id LIMIT 1) AS importBatchId, CASE WHEN a.systemRole IS NULL THEN a.id ELSE NULL END AS accountId, a.displayName AS accountName, CASE WHEN e.kind IN ('TRANSFER','CASH_WITHDRAWAL') THEN q.accountId ELSE NULL END AS destinationAccountId, CASE WHEN e.kind IN ('TRANSFER','CASH_WITHDRAWAL') THEN b.displayName ELSE NULL END AS destinationAccountName, e.originalEventId,e.revision, CASE WHEN b.ledgerType IN ('EXPENSE','INCOME') THEN b.ledgerType ELSE NULL END AS reportType FROM financial_events e JOIN ledger_entries p ON p.eventId=e.id AND p.position=0 JOIN ledger_entries q ON q.eventId=e.id AND q.position=1 JOIN accounts a ON a.id=p.accountId JOIN accounts b ON b.id=q.accountId LEFT JOIN categories c ON c.id=q.categoryId WHERE e.systemKey IS NULL"

    val CREATE_TABLES = listOf(
        """CREATE TABLE IF NOT EXISTS accounts (id TEXT NOT NULL PRIMARY KEY, displayName TEXT NOT NULL, ledgerType TEXT NOT NULL, accountKind TEXT, currencyCode TEXT NOT NULL, systemRole TEXT, systemKey TEXT, institutionLabel TEXT, lastFour TEXT, origin TEXT NOT NULL, isArchived INTEGER NOT NULL, openingBalanceMinor INTEGER, openingBalanceDate TEXT, createdAtEpochMillis INTEGER NOT NULL, updatedAtEpochMillis INTEGER NOT NULL)""",
        """CREATE TABLE IF NOT EXISTS import_batches (id TEXT NOT NULL PRIMARY KEY, sourceFileName TEXT NOT NULL, fileContentHash TEXT NOT NULL, importedAtEpochMillis INTEGER NOT NULL, acceptedRowCount INTEGER NOT NULL, rejectedRowCount INTEGER NOT NULL, accountId TEXT, FOREIGN KEY(accountId) REFERENCES accounts(id) ON UPDATE NO ACTION ON DELETE SET NULL)""",
        """CREATE TABLE IF NOT EXISTS financial_events (id TEXT NOT NULL PRIMARY KEY, kind TEXT NOT NULL, currencyCode TEXT NOT NULL, description TEXT NOT NULL, userNote TEXT, occurredDate TEXT NOT NULL, occurredTime TEXT, dateBasis TEXT NOT NULL, originalEventId TEXT, systemKey TEXT, revision INTEGER NOT NULL, createdAtEpochMillis INTEGER NOT NULL, updatedAtEpochMillis INTEGER NOT NULL, FOREIGN KEY(originalEventId) REFERENCES financial_events(id) ON UPDATE NO ACTION ON DELETE RESTRICT)""",
        """CREATE TABLE IF NOT EXISTS ledger_entries (id TEXT NOT NULL PRIMARY KEY, eventId TEXT NOT NULL, accountId TEXT NOT NULL, position INTEGER NOT NULL, amountMinor INTEGER NOT NULL, categoryId TEXT, FOREIGN KEY(eventId) REFERENCES financial_events(id) ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY(accountId) REFERENCES accounts(id) ON UPDATE NO ACTION ON DELETE RESTRICT, FOREIGN KEY(categoryId) REFERENCES categories(id) ON UPDATE NO ACTION ON DELETE SET NULL)""",
        """CREATE TABLE IF NOT EXISTS source_records (id TEXT NOT NULL PRIMARY KEY, sourceType TEXT NOT NULL, sourceKey TEXT NOT NULL, eventId TEXT, state TEXT NOT NULL, importBatchId TEXT, sourceRowNumber INTEGER, sourceDescription TEXT NOT NULL, observedAtEpochMillis INTEGER, FOREIGN KEY(eventId) REFERENCES financial_events(id) ON UPDATE NO ACTION ON DELETE SET NULL, FOREIGN KEY(importBatchId) REFERENCES import_batches(id) ON UPDATE NO ACTION ON DELETE SET NULL)""",
    )
    val CREATE_INDEXES = listOf(
        "CREATE UNIQUE INDEX index_accounts_systemKey ON accounts(systemKey)",
        "CREATE INDEX index_accounts_institutionLabel_lastFour ON accounts(institutionLabel,lastFour)",
        "CREATE INDEX index_import_batches_fileContentHash ON import_batches(fileContentHash)",
        "CREATE INDEX index_import_batches_accountId ON import_batches(accountId)",
        "CREATE INDEX index_financial_events_originalEventId ON financial_events(originalEventId)",
        "CREATE UNIQUE INDEX index_financial_events_systemKey ON financial_events(systemKey)",
        "CREATE INDEX index_financial_events_occurredDate_occurredTime ON financial_events(occurredDate,occurredTime)",
        "CREATE INDEX index_financial_events_kind_occurredDate ON financial_events(kind,occurredDate)",
        "CREATE UNIQUE INDEX index_ledger_entries_eventId_position ON ledger_entries(eventId,position)",
        "CREATE INDEX index_ledger_entries_accountId ON ledger_entries(accountId)",
        "CREATE INDEX index_ledger_entries_categoryId ON ledger_entries(categoryId)",
        "CREATE UNIQUE INDEX index_source_records_sourceType_sourceKey ON source_records(sourceType,sourceKey)",
        "CREATE INDEX index_source_records_eventId ON source_records(eventId)",
        "CREATE INDEX index_source_records_importBatchId ON source_records(importBatchId)",
    )
}

val MIGRATION_1_2: Migration = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.query("SELECT COUNT(*) FROM transactions WHERE amountMinor<=0 OR amountMinor>9000000000000000 OR direction NOT IN ('EXPENSE','INCOME')").use {
            check(it.moveToFirst() && it.getLong(0)==0L) { "Legacy financial data needs repair before upgrading." }
        }
        db.execSQL("ALTER TABLE transactions RENAME TO transactions_v1")
        db.execSQL("ALTER TABLE import_batches RENAME TO import_batches_v1")
        LedgerSchemaV2.CREATE_TABLES.forEach(db::execSQL)
        db.execSQL("INSERT INTO import_batches SELECT id,sourceFileName,fileContentHash,importedAtEpochMillis,acceptedRowCount,rejectedRowCount,NULL FROM import_batches_v1")
        val roles = listOf("EXPENSES" to "EXPENSE", "INCOME" to "INCOME", "OPENING_EQUITY" to "EQUITY", "SUSPENSE" to "EQUITY", "LEGACY_UNASSIGNED" to "ASSET", "UNALLOCATED_CASH" to "ASSET")
        // This list is deliberately frozen rather than derived from a future enum version.
        roles.forEach { (role,type) ->
            val label = when(role) { "LEGACY_UNASSIGNED" -> "Unassigned history"; "UNALLOCATED_CASH" -> "Unallocated cash"; else -> role.lowercase().replace('_',' ') }
            db.execSQL("""INSERT INTO accounts SELECT 'system:$role:'||currencyCode, '$label', '$type', NULL, currencyCode, '$role', 'system:$role:'||currencyCode, NULL,NULL,'SYSTEM',0,NULL,NULL,0,0 FROM (SELECT DISTINCT currencyCode FROM transactions_v1 UNION SELECT 'INR')""")
        }
        db.execSQL("""INSERT INTO financial_events SELECT id,direction,currencyCode,description,NULL,occurredDate,occurredTime,'LEGACY_PRESERVED',NULL,NULL,0,createdAtEpochMillis,updatedAtEpochMillis FROM transactions_v1""")
        db.execSQL("""INSERT INTO ledger_entries SELECT id||':0',id,'system:LEGACY_UNASSIGNED:'||currencyCode,0,CASE direction WHEN 'EXPENSE' THEN -amountMinor ELSE amountMinor END,NULL FROM transactions_v1""")
        db.execSQL("""INSERT INTO ledger_entries SELECT id||':1',id,'system:'||CASE direction WHEN 'EXPENSE' THEN 'EXPENSES' ELSE 'INCOME' END||':'||currencyCode,1,CASE direction WHEN 'EXPENSE' THEN amountMinor ELSE -amountMinor END,categoryId FROM transactions_v1""")
        db.execSQL("""INSERT INTO source_records SELECT 'legacy-source:'||id,source,'legacy:'||id,id,'ATTACHED',importBatchId,NULL,description,createdAtEpochMillis FROM transactions_v1""")
        db.query("SELECT id FROM financial_events EXCEPT SELECT eventId FROM ledger_entries GROUP BY eventId HAVING COUNT(*)=2 AND SUM(amountMinor)=0").use {
            check(!it.moveToFirst()) { "Migration produced an incomplete event." }
        }
        db.query("""SELECT direction,currencyCode,categoryId,SUM(amountMinor) FROM transactions_v1 GROUP BY direction,currencyCode,categoryId
            EXCEPT SELECT e.kind,e.currencyCode,l.categoryId,SUM(ABS(l.amountMinor)) FROM financial_events e JOIN ledger_entries l ON l.eventId=e.id AND l.position=1 GROUP BY e.kind,e.currencyCode,l.categoryId""").use {
            check(!it.moveToFirst()) { "Migration changed historical totals." }
        }
        db.execSQL("DROP TABLE transactions_v1")
        db.execSQL("DROP TABLE import_batches_v1")
        LedgerSchemaV2.CREATE_INDEXES.forEach(db::execSQL)
        db.execSQL("CREATE VIEW `transaction_history` AS " + LedgerSchemaV2.HISTORY_SELECT)
        db.query("PRAGMA foreign_key_check").use { check(!it.moveToFirst()) { "Migration left a broken reference." } }
    }
}
