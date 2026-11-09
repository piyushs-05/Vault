package com.piyush.vault.presentation.navigation

import android.net.Uri
import java.time.LocalDate

/** Route patterns and builders shared by the app's navigation graph and screen actions. */
object VaultRoutes {
    const val HOME = "home"
    const val HISTORY = "history"
    const val HISTORY_PATTERN = "history?account={account}"
    const val ACCOUNTS = "accounts"
    const val SUMMARY = "summary"
    const val MORE = "more"
    const val TRANSACTION_NEW = "transaction/new"
    const val TRANSACTION_EDIT_PATTERN = "transaction/edit/{id}"
    const val ACCOUNT_NEW_PATTERN = "account/new?bank={bank}&suffix={suffix}"
    const val ACCOUNT_EDIT_PATTERN = "account/edit/{id}"
    const val SMS = "sms"
    const val SMS_REVIEW_PATTERN = "sms-review/{id}"
    const val CATEGORIES = "categories"
    const val DRILL_PATTERN = "drill/{start}/{end}?category={category}&name={name}"
    const val CSV = "csv"

    val topLevelRoutes = setOf(HOME, HISTORY_PATTERN, ACCOUNTS, SUMMARY, MORE)

    fun history(accountId: String? = null): String = accountId
        ?.let { "history?account=${Uri.encode(it)}" }
        ?: HISTORY

    fun editTransaction(id: String) = "transaction/edit/${Uri.encode(id)}"

    fun newAccount(bank: String = "", suffix: String = "") =
        "account/new?bank=${Uri.encode(bank)}&suffix=${Uri.encode(suffix)}"

    fun editAccount(id: String) = "account/edit/${Uri.encode(id)}"

    fun reviewSms(id: String) = "sms-review/${Uri.encode(id)}"

    fun categoryDrillDown(start: LocalDate, end: LocalDate, categoryId: String?, categoryName: String) =
        "drill/$start/$end?category=${Uri.encode(categoryId.orEmpty())}&name=${Uri.encode(categoryName)}"
}
