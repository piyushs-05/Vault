package com.piyush.vault.data.csv

import com.piyush.vault.domain.model.*
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class VaultCsvTest {
    @Test fun transferRoundTripPreservesBothAccountsAndKind() {
        val a = Account("a","Main bank",LedgerType.ASSET,AccountKind.BANK,lastFour = "0012",createdAtEpochMillis = 0,updatedAtEpochMillis = 0)
        val b = a.copy(id = "b",displayName = "Savings",lastFour = "0045")
        val transaction = Transaction("transfer",50_000,direction = TransactionDirection.EXPENSE,description = "Move, save",categoryId = null,occurredDate = LocalDate.of(2026,11,2),occurredTime = null,
            createdAtEpochMillis = 0,updatedAtEpochMillis = 0,kind = EventKind.TRANSFER,accountId = "a",destinationAccountId = "b",userNote = "A \"note\"")
        val table = CsvDocumentParser.parse(VaultCsv.export(listOf(TransactionListItem(transaction,null)),listOf(a,b)))
        val row = CsvDocumentParser.preview(table,CsvColumnMapping(),emptySet()).single()
        assertNull(row.errorMessage)
        assertEquals(EventKind.TRANSFER,row.kind)
        assertEquals("a",row.importedAccount?.id)
        assertEquals("b",row.importedDestination?.id)
        assertEquals("0012",row.importedAccount?.lastFour)
        assertEquals("Move, save",row.description)
        assertNull(row.occurredTime)
    }
    @Test fun invalidCalendarDateDoesNotNormalizeIntoAnotherDate() {
        val table = CsvDocumentParser.parse("Date,Description,Amount,Direction\n31/02/2026,Coffee,10,expense")
        val row = CsvDocumentParser.preview(table,CsvDocumentParser.defaultMapping(table.headers),emptySet()).single()
        assertNotNull(row.errorMessage)
    }
    @Test fun zeroInOtherDebitCreditColumnIsAccepted() {
        val table = CsvDocumentParser.parse("Date,Description,Debit,Credit\n2026-11-02,Coffee,10,0")
        val row = CsvDocumentParser.preview(table,CsvDocumentParser.defaultMapping(table.headers),emptySet()).single()
        assertNull(row.errorMessage)
        assertEquals(1000L,row.amountMinor)
        assertEquals(TransactionDirection.EXPENSE,row.direction)
    }
}
