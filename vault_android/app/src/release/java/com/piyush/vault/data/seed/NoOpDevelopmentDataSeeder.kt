package com.piyush.vault.data.seed

import javax.inject.Inject

class NoOpDevelopmentDataSeeder @Inject constructor() : DevelopmentDataSeeder {
    override suspend fun seedIfNeeded() = Unit
}
