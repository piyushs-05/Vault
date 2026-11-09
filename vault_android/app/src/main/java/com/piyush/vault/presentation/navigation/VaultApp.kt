package com.piyush.vault.presentation.navigation

import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.piyush.vault.presentation.ui.theme.VaultPrimary
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.navArgument
import com.piyush.vault.domain.model.*
import com.piyush.vault.presentation.accounts.*
import com.piyush.vault.presentation.sms.*
import com.piyush.vault.presentation.categories.*
import com.piyush.vault.presentation.csv.*
import com.piyush.vault.presentation.summary.*
import com.piyush.vault.presentation.transactions.*
import com.piyush.vault.presentation.home.HomeScreen
import com.piyush.vault.presentation.home.MoreScreen
import com.piyush.vault.presentation.ui.components.VaultMascot
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun VaultApp(reviewSourceId: String? = null,onReviewRequestConsumed: () -> Unit = {}) {
    val nav = rememberNavController()
    LaunchedEffect(reviewSourceId) {
        if (reviewSourceId != null) {
            nav.navigate(VaultRoutes.reviewSms(reviewSourceId)) { launchSingleTop = true }
            onReviewRequestConsumed()
        }
    }
    val currentEntry by nav.currentBackStackEntryAsState()
    val currentRoute = currentEntry?.destination?.route
    val topLevelRoutes = VaultRoutes.topLevelRoutes
    Scaffold(
        containerColor = androidx.compose.material3.MaterialTheme.colorScheme.background,
        topBar = { VaultTopBar() },
        bottomBar = {
            if (currentRoute in topLevelRoutes) {
                Surface(color = androidx.compose.material3.MaterialTheme.colorScheme.surface) {
                    Row(
                        Modifier.fillMaxWidth().navigationBarsPadding().height(62.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        listOf(
                            Triple(VaultRoutes.HOME, "⌂", "Home"),
                            Triple(VaultRoutes.HISTORY, "↕", "Activity"),
                            Triple(VaultRoutes.ACCOUNTS, "▣", "Accounts"),
                            Triple(VaultRoutes.SUMMARY, "◔", "Insights"),
                            Triple(VaultRoutes.MORE, "•••", "More"),
                        ).forEach { (route, icon, label) ->
                            val selected = if (route == VaultRoutes.HISTORY) currentRoute == VaultRoutes.HISTORY_PATTERN else currentRoute == route
                            Column(
                                Modifier.weight(1f).height(62.dp).clickable {
                                        nav.navigate(if (route == VaultRoutes.HISTORY) VaultRoutes.history() else route) {
                                        popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                            ) {
                                Box(
                                    Modifier.clip(RoundedCornerShape(14.dp))
                                        .background(if (selected) androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant else androidx.compose.ui.graphics.Color.Transparent)
                                        .padding(horizontal = 12.dp, vertical = 1.dp),
                                ) {
                                    Text(icon, color = if (selected) VaultPrimary else androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 16.sp)
                                }
                                Text(label, color = if (selected) VaultPrimary else androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 9.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium)
                            }
                        }
                    }
                }
            }
        },
    ) { padding ->
    NavHost(navController = nav,startDestination = VaultRoutes.HOME,modifier = androidx.compose.ui.Modifier.padding(padding)) {
        composable(VaultRoutes.HOME) {
            val transactionsVm: TransactionsViewModel = hiltViewModel()
            val accountsVm: AccountsViewModel = hiltViewModel()
            val summaryVm: PeriodSummaryViewModel = hiltViewModel()
            val transactions by transactionsVm.uiState.collectAsStateWithLifecycle()
            val accounts by accountsVm.state.collectAsStateWithLifecycle()
            val summary by summaryVm.uiState.collectAsStateWithLifecycle()
            HomeScreen(
                transactions = transactions,
                accounts = accounts,
                summary = summary,
                onAddTransaction = { nav.navigate(VaultRoutes.TRANSACTION_NEW) },
                onOpenActivity = { nav.navigate(VaultRoutes.history()) },
                onOpenAccounts = { nav.navigate(VaultRoutes.ACCOUNTS) },
                onOpenSummary = { nav.navigate(VaultRoutes.SUMMARY) },
                onOpenCategories = { nav.navigate(VaultRoutes.CATEGORIES) },
                onImportCsv = { nav.navigate(VaultRoutes.CSV) },
                onOpenSms = { nav.navigate(VaultRoutes.SMS) },
                onTransactionClick = { nav.navigate(VaultRoutes.editTransaction(it)) },
            )
        }
        composable(VaultRoutes.HISTORY_PATTERN,arguments = listOf(navArgument("account") { type = NavType.StringType; nullable = true; defaultValue = null })) { entry ->
            val vm: TransactionsViewModel = hiltViewModel()
            val state by vm.uiState.collectAsStateWithLifecycle()
            val snackbar = remember { SnackbarHostState() }
            LaunchedEffect(entry) { vm.updateAccount(entry.arguments?.getString("account")) }
            LaunchedEffect(vm) {
                vm.events.collect { event ->
                    when(event) {
                        is TransactionsEvent.Deleted -> if (snackbar.showSnackbar("Whole transaction deleted",actionLabel = "Undo") == SnackbarResult.ActionPerformed) vm.undoDelete(event.transaction)
                        TransactionsEvent.DeleteFailed -> snackbar.showSnackbar("Couldn't delete. Handle linked refunds/reversals first.")
                        TransactionsEvent.UndoFailed -> snackbar.showSnackbar("Couldn't restore this transaction. Its related data may have changed.")
                    }
                }
            }
            TransactionsScreen(uiState = state,snackbarHostState = snackbar,
                onAdd = { nav.navigate(VaultRoutes.TRANSACTION_NEW) },onTransactionClick = { nav.navigate(VaultRoutes.editTransaction(it)) },
                onImportCsv = { nav.navigate(VaultRoutes.CSV) },
                onDeleteTransaction = vm::deleteTransaction,onSearch = vm::updateSearchQuery,onDirectionFilter = vm::updateDirection,
                onCategoryFilter = vm::updateCategory,onStartDateFilter = vm::updateStartDate,onEndDateFilter = vm::updateEndDate,
                onClearFilters = vm::clearFilters,onAccountFilter = vm::updateAccount,onKindFilter = vm::updateKind)
        }
        composable(VaultRoutes.TRANSACTION_NEW) { TransactionEditor(null) { nav.popBackStack() } }
        composable(VaultRoutes.TRANSACTION_EDIT_PATTERN) { TransactionEditor(it.arguments?.getString("id")) { nav.popBackStack() } }
        composable(VaultRoutes.ACCOUNTS) {
            val vm: AccountsViewModel = hiltViewModel()
            val state by vm.state.collectAsStateWithLifecycle()
            AccountsScreen(state,{ nav.navigate(VaultRoutes.newAccount()) },{ nav.navigate(VaultRoutes.editAccount(it)) },
                { nav.navigate(VaultRoutes.history(it)) },vm::archive,vm::select,vm::selectAccount,vm::assignSelected)
        }
        composable(VaultRoutes.MORE) {
            val txVm: TransactionsViewModel = hiltViewModel()
            val csvVm: CsvTransferViewModel = hiltViewModel()
            val transactions by txVm.uiState.collectAsStateWithLifecycle()
            val context = LocalContext.current
            val scope = rememberCoroutineScope()
            val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
                if (uri != null) scope.launch {
                    try {
                        val text = csvVm.createExportCsv(transactions.transactions)
                        withContext(Dispatchers.IO) { requireNotNull(context.contentResolver.openOutputStream(uri, "w")).bufferedWriter(Charsets.UTF_8).use { it.write(text) } }
                        Toast.makeText(context, "Exported ${transactions.transactions.size} events", Toast.LENGTH_SHORT).show()
                    } catch (e: CancellationException) { throw e }
                    catch (_: Exception) { Toast.makeText(context, "Couldn't export these transactions.", Toast.LENGTH_LONG).show() }
                }
            }
            MoreScreen(
                onCategories = { nav.navigate(VaultRoutes.CATEGORIES) },
                onImportCsv = { nav.navigate(VaultRoutes.CSV) },
                onExportCsv = { export.launch("vault-events-v2.csv") },
                onSms = { nav.navigate(VaultRoutes.SMS) },
                onAccounts = { nav.navigate(VaultRoutes.ACCOUNTS) },
            )
        }
        composable(VaultRoutes.ACCOUNT_NEW_PATTERN,arguments = listOf(navArgument("bank") { defaultValue = "" },navArgument("suffix") { defaultValue = "" })) { AccountEditor(null) { nav.popBackStack() } }
        composable(VaultRoutes.ACCOUNT_EDIT_PATTERN) { AccountEditor(it.arguments?.getString("id")) { nav.popBackStack() } }
        composable(VaultRoutes.SMS) {
            val vm: SmsViewModel = hiltViewModel()
            val state by vm.state.collectAsStateWithLifecycle()
            SmsScreen(state,vm,{ nav.popBackStack() },{ nav.navigate(VaultRoutes.reviewSms(it)) },{ nav.navigate(VaultRoutes.newAccount()) })
        }
        composable(VaultRoutes.SMS_REVIEW_PATTERN) { entry ->
            val vm: SmsViewModel = hiltViewModel()
            val state by vm.form.collectAsStateWithLifecycle()
            LaunchedEffect(entry) { vm.begin(requireNotNull(entry.arguments?.getString("id"))) }
            LaunchedEffect(vm) { vm.completed.collect { nav.popBackStack() } }
            SmsReviewScreen(state,vm,{ nav.popBackStack() },{
                val observation = vm.state.value.candidates.firstOrNull { it.source.id == entry.arguments?.getString("id") }?.observation
                nav.navigate(VaultRoutes.newAccount(observation?.institution.orEmpty(),observation?.lastFour.orEmpty()))
            })
        }
        composable(VaultRoutes.CATEGORIES) {
            val vm: CategoryManagementViewModel = hiltViewModel()
            val state by vm.uiState.collectAsStateWithLifecycle()
            CategoryManagementScreen(state = state,onBack = { nav.popBackStack() },onNameChange = vm::updateName,
                onApplicabilityChange = vm::updateApplicability,onAddCategory = vm::addCategory,onArchiveOrRestore = vm::archiveOrRestore,
                onRequestDelete = vm::requestPermanentDelete,onCancelDelete = vm::cancelPermanentDelete,onConfirmDelete = vm::confirmPermanentDelete)
        }
        composable(VaultRoutes.SUMMARY) {
            val vm: PeriodSummaryViewModel = hiltViewModel()
            val state by vm.uiState.collectAsStateWithLifecycle()
            PeriodSummaryScreen(state,vm::previousMonth,vm::nextMonth,{ cat ->
                nav.navigate(VaultRoutes.categoryDrillDown(state.month.atDay(1),state.month.atEndOfMonth(),cat.categoryId,cat.categoryName ?: "Uncategorized"))
            },onAddTransaction = { nav.navigate(VaultRoutes.TRANSACTION_NEW) })
        }
        composable(VaultRoutes.DRILL_PATTERN,arguments = listOf(navArgument("category") { defaultValue = "" },navArgument("name") { defaultValue = "Uncategorized" })) { entry ->
            val vm: CategoryDrillDownViewModel = hiltViewModel()
            val state by vm.uiState.collectAsStateWithLifecycle()
            val start = requireNotNull(entry.arguments?.getString("start"))
            val end = requireNotNull(entry.arguments?.getString("end"))
            LaunchedEffect(entry) {
                val cat = entry.arguments?.getString("category")?.takeIf { it.isNotEmpty() }
                vm.show(TransactionFilter(LocalDate.parse(start),LocalDate.parse(end),cat,cat == null,TransactionDirection.EXPENSE))
            }
            CategoryDrillDownScreen(state,entry.arguments?.getString("name").orEmpty(),"$start — $end",{ nav.popBackStack() })
        }
        composable(VaultRoutes.CSV) {
            val vm: CsvTransferViewModel = hiltViewModel()
            val state by vm.uiState.collectAsStateWithLifecycle()
            val context = LocalContext.current
            val scope = rememberCoroutineScope()
            val choose = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                if (uri != null) scope.launch {
                    try {
                        val result = withContext(Dispatchers.IO) {
                            val name = context.contentResolver.query(uri,arrayOf(OpenableColumns.DISPLAY_NAME),null,null,null)?.use { cursor ->
                                if (cursor.moveToFirst()) cursor.getString(cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME)) else null
                            } ?: "transactions.csv"
                            val bytes = requireNotNull(context.contentResolver.openInputStream(uri)).use { input ->
                                val output = ByteArrayOutputStream(); val buffer = ByteArray(8192)
                                while (true) { val count = input.read(buffer); if (count < 0) break; require(output.size()+count <= 10*1024*1024) { "Choose a CSV under 10 MB." }; output.write(buffer,0,count) }
                                output.toByteArray()
                            }
                            name to bytes
                        }
                        vm.loadFile(result.first,result.second)
                    } catch(e: CancellationException) { throw e }
                    catch (_: Exception) { Toast.makeText(context,"Couldn't open CSV. Use a valid file under 10 MB.",Toast.LENGTH_LONG).show() }
                }
            }
            LaunchedEffect(vm) { vm.events.collect { nav.popBackStack() } }
            CsvImportScreen(state,{ nav.popBackStack() },{ choose.launch(arrayOf("text/*","application/csv","application/vnd.ms-excel")) },
                vm::updateMapping,vm::toggleRow,vm::updateRowTime,vm::importSelectedRows,onAccountChange = vm::updateAccount)
        }
    }
    }
}

@Composable
private fun VaultTopBar() {
    Surface(color = androidx.compose.material3.MaterialTheme.colorScheme.background) {
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().height(48.dp).padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            VaultMascot(Modifier.size(30.dp))
            Spacer(Modifier.width(8.dp))
            Text("vault", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.5).sp, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
private fun TransactionEditor(id: String?,onBack: ()->Unit) {
    val vm: TransactionFormViewModel = hiltViewModel()
    val state by vm.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(id) { if (id == null) vm.startNew() else vm.startEditing(id) }
    LaunchedEffect(vm) { vm.events.collect { onBack() } }
    TransactionFormScreen(state,vm::updateAmount,vm::updateDescription,vm::updateDirection,vm::updateCategory,vm::updateDate,vm::updateTime,vm::save,onBack,
        onKindChange = vm::updateKind,onAccountChange = vm::updateAccount,onDestinationChange = vm::updateDestination,onOriginalChange = vm::updateOriginal,onNoteChange = vm::updateNote)
}

@Composable
private fun AccountEditor(id: String?,onBack: ()->Unit) {
    val vm: AccountsViewModel = hiltViewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(id) { vm.begin(id) }
    LaunchedEffect(vm) { vm.savedEvents.collect { onBack() } }
    AccountFormScreen(state,vm,onBack)
}
