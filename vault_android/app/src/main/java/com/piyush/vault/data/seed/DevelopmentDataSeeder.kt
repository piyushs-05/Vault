package com.piyush.vault.data.seed

/** Supplies optional development-only records during app startup. */
interface DevelopmentDataSeeder {
    suspend fun seedIfNeeded()
}
