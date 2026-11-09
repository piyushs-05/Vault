package com.piyush.vault.data.seed

import android.content.Context
import com.piyush.vault.domain.model.Transaction
import com.piyush.vault.domain.model.TransactionDirection
import com.piyush.vault.domain.model.TransactionFilter
import com.piyush.vault.domain.repository.TransactionRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject
import kotlinx.coroutines.flow.first

class DebugDevelopmentDataSeeder @Inject constructor(
    @ApplicationContext private val context: Context,
    private val transactionRepository: TransactionRepository,
) : DevelopmentDataSeeder {

    override suspend fun seedIfNeeded() {
        val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
        if (preferences.getBoolean(SEED_COMPLETED_KEY, false)) return

        val existingTransactions = transactionRepository
            .observeTransactions(TransactionFilter())
            .first()
        val existingIds = existingTransactions.mapTo(mutableSetOf()) { it.transaction.id }
        val hasRealTransactions = existingTransactions.any {
            !it.transaction.id.startsWith(DEMO_ID_PREFIX)
        }

        if (!hasRealTransactions) {
            val today = LocalDate.now()
            val createdAt = System.currentTimeMillis()
            demoTransactions(today, createdAt)
                .filterNot { it.id in existingIds }
                .forEach { transaction ->
                    transactionRepository.save(transaction)
                }
        }

        preferences.edit().putBoolean(SEED_COMPLETED_KEY, true).apply()
    }

    private fun demoTransactions(today: LocalDate, createdAt: Long): List<Transaction> = listOf(
        Transaction(
            id = "debug-demo-coffee",
            amountMinor = 18_000,
            direction = TransactionDirection.EXPENSE,
            description = "Demo: Coffee",
            categoryId = FOOD_CATEGORY_ID,
            occurredDate = today,
            occurredTime = LocalTime.of(9, 15),
            createdAtEpochMillis = createdAt,
            updatedAtEpochMillis = createdAt,
        ),
        Transaction(
            id = "debug-demo-groceries",
            amountMinor = 124_550,
            direction = TransactionDirection.EXPENSE,
            description = "Demo: Groceries",
            categoryId = GROCERIES_CATEGORY_ID,
            occurredDate = today.minusDays(1),
            occurredTime = LocalTime.of(18, 40),
            createdAtEpochMillis = createdAt,
            updatedAtEpochMillis = createdAt,
        ),
        Transaction(
            id = "debug-demo-metro",
            amountMinor = 3_500,
            direction = TransactionDirection.EXPENSE,
            description = "Demo: Metro recharge",
            categoryId = TRANSPORT_CATEGORY_ID,
            occurredDate = today.minusDays(2),
            occurredTime = LocalTime.of(8, 5),
            createdAtEpochMillis = createdAt,
            updatedAtEpochMillis = createdAt,
        ),
        Transaction(
            id = "debug-demo-freelance",
            amountMinor = 2_500_000,
            direction = TransactionDirection.INCOME,
            description = "Demo: Freelance payment",
            categoryId = OTHER_INCOME_CATEGORY_ID,
            occurredDate = today.minusDays(4),
            occurredTime = null,
            createdAtEpochMillis = createdAt,
            updatedAtEpochMillis = createdAt,
        ),
        Transaction(
            id = "debug-demo-salary",
            amountMinor = 8_500_000,
            direction = TransactionDirection.INCOME,
            description = "Demo: Salary",
            categoryId = SALARY_CATEGORY_ID,
            occurredDate = today.minusDays(7),
            occurredTime = LocalTime.of(10, 0),
            createdAtEpochMillis = createdAt,
            updatedAtEpochMillis = createdAt,
        ),
    )

    private companion object {
        const val PREFERENCES_NAME = "debug_demo_data"
        const val SEED_COMPLETED_KEY = "seed_completed"
        const val DEMO_ID_PREFIX = "debug-demo-"

        const val FOOD_CATEGORY_ID = "00000000-0000-0000-0000-000000000001"
        const val GROCERIES_CATEGORY_ID = "00000000-0000-0000-0000-000000000002"
        const val TRANSPORT_CATEGORY_ID = "00000000-0000-0000-0000-000000000003"
        const val SALARY_CATEGORY_ID = "00000000-0000-0000-0000-000000000008"
        const val OTHER_INCOME_CATEGORY_ID = "00000000-0000-0000-0000-000000000009"
    }
}
