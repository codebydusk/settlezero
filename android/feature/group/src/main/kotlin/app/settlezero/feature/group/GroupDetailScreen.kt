package app.settlezero.feature.group

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.settlezero.core.model.Currency
import app.settlezero.core.model.CurrencyAmount
import app.settlezero.core.model.Expense
import app.settlezero.core.ui.component.BalanceCard
import app.settlezero.core.ui.component.MemberAvatar
import app.settlezero.core.ui.component.QrCodeView
import app.settlezero.core.ui.component.StatusBadge
import app.settlezero.core.ui.theme.NegativeDebt
import app.settlezero.core.ui.theme.PositiveCredit
import app.settlezero.core.ui.theme.SettledZero
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupDetailScreen(
    viewModel: GroupDetailViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToAddExpense: (groupId: String) -> Unit,
    onNavigateToSettleUp: (groupId: String) -> Unit,
    onNavigateToHistory: (groupId: String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var showMenu by remember { mutableStateOf(false) }
    var showInviteDialog by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = uiState.group?.name ?: "Group",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        uiState.group?.let { g ->
                            val curr = Currency.findByCode(g.currencyCode)
                            Text(
                                text = "${curr.code} (${curr.symbol})",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showInviteDialog = true }) {
                        Icon(Icons.Default.Share, contentDescription = "Share Invite")
                    }
                    IconButton(onClick = { showMenu = !showMenu }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More")
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Activity History & Export") },
                            onClick = {
                                showMenu = false
                                onNavigateToHistory(uiState.group?.id ?: return@DropdownMenuItem)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Archive Group") },
                            onClick = {
                                showMenu = false
                                viewModel.archiveGroup(onSuccess = onNavigateBack)
                            }
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        val group = uiState.group
        if (group == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Text("Loading group details...")
            }
        } else {
            val currency = Currency.findByCode(group.currencyCode)

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Hero Balance Card
                item {
                    BalanceCard(
                        netBalanceMinor = uiState.myNetBalanceMinor,
                        currencyScale = group.currencyScale,
                        currencySymbol = currency.symbol
                    )
                }

                // Quick Action Buttons
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { onNavigateToAddExpense(group.id) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Expense")
                        }

                        Button(
                            onClick = { onNavigateToSettleUp(group.id) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Payments, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Settle Up")
                        }

                        OutlinedButton(
                            onClick = { viewModel.onShowAddMemberDialog(true) }
                        ) {
                            Icon(Icons.Default.PersonAdd, contentDescription = null)
                        }
                    }
                }

                // Simplified Debts Summary Section
                if (uiState.simplifiedDebts.isNotEmpty()) {
                    item {
                        Text(
                            text = "Simplified Debts",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    items(uiState.simplifiedDebts) { transfer ->
                        val payer = uiState.members.find { it.member.id == transfer.fromMemberId }?.member?.name ?: "Someone"
                        val recipient = uiState.members.find { it.member.id == transfer.toMemberId }?.member?.name ?: "Someone"
                        val formattedAmount = CurrencyAmount(transfer.amountMinor)
                            .format(group.currencyScale, currency.symbol)

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .padding(12.dp)
                                    .fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "$payer ➔ $recipient",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = formattedAmount,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }

                // Members List
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Members (${uiState.members.size})",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Tap to switch persona",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                items(uiState.members, key = { it.member.id }) { item ->
                    val isCurrent = item.member.id == uiState.currentMemberId
                    val amount = CurrencyAmount(kotlin.math.abs(item.netBalanceMinor))
                    val formatted = amount.format(group.currencyScale, currency.symbol)
                    val balanceColor = when {
                        item.netBalanceMinor > 0 -> PositiveCredit
                        item.netBalanceMinor < 0 -> NegativeDebt
                        else -> SettledZero
                    }
                    val balanceText = when {
                        item.netBalanceMinor > 0 -> "+$formatted"
                        item.netBalanceMinor < 0 -> "-$formatted"
                        else -> "Settled"
                    }

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.selectCurrentMember(item.member.id) },
                        colors = CardDefaults.cardColors(
                            containerColor = if (isCurrent) {
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                            } else {
                                MaterialTheme.colorScheme.surface
                            }
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .padding(12.dp)
                                .fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                MemberAvatar(name = item.member.name, role = item.member.role)
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = item.member.name + if (isCurrent) " (You)" else "",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        StatusBadge(status = item.member.status)
                                    }
                                    Text(
                                        text = item.member.role.name.lowercase().replace('_', ' '),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Text(
                                text = balanceText,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = balanceColor
                            )
                        }
                    }
                }

                // Expenses Section
                item {
                    val totalSpendingFormatted = CurrencyAmount(uiState.totalSpendingMinor)
                        .format(group.currencyScale, currency.symbol)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Expenses (${uiState.expenses.size})",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Total: $totalSpendingFormatted",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (uiState.expenses.isEmpty()) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                            )
                        ) {
                            Text(
                                text = "No expenses recorded yet. Someone needs to pay for something.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }
                } else {
                    items(uiState.expenses, key = { it.id }) { expense ->
                        ExpenseItemRow(
                            expense = expense,
                            currencyScale = group.currencyScale,
                            currencySymbol = currency.symbol,
                            payerName = uiState.members.find { it.member.id == expense.payerMemberId }?.member?.name ?: "Someone",
                            onDelete = { viewModel.deleteExpense(expense.id) }
                        )
                    }
                }
            }

            if (uiState.showAddMemberDialog) {
                AlertDialog(
                    onDismissRequest = { viewModel.onShowAddMemberDialog(false) },
                    title = { Text("Add Group Member") },
                    text = {
                        Column {
                            OutlinedTextField(
                                value = uiState.newMemberName,
                                onValueChange = viewModel::onNewMemberNameChanged,
                                label = { Text("Member Name") },
                                placeholder = { Text("e.g. Priyanshu") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            if (uiState.errorMessage != null) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = uiState.errorMessage ?: "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    },
                    confirmButton = {
                        Button(onClick = viewModel::addMember) {
                            Text("Add")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { viewModel.onShowAddMemberDialog(false) }) {
                            Text("Cancel")
                        }
                    }
                )
            }

            if (showInviteDialog && uiState.group != null) {
                val group = uiState.group!!
                val inviteUrl = viewModel.generateInviteLink()

                AlertDialog(
                    onDismissRequest = { showInviteDialog = false },
                    title = {
                        Text("Invite to ${group.name}", fontWeight = FontWeight.Bold)
                    },
                    text = {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Scan this QR code with another device to join immediately, or share the link.",
                                style = MaterialTheme.typography.bodySmall,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            QrCodeView(
                                content = inviteUrl,
                                sizeDp = 200.dp
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = inviteUrl,
                                style = MaterialTheme.typography.labelSmall,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    },
                    confirmButton = {
                        Button(onClick = {
                            val sendIntent = Intent().apply {
                                action = Intent.ACTION_SEND
                                putExtra(Intent.EXTRA_TEXT, "Join my expense group '${group.name}' on SettleZero: $inviteUrl")
                                type = "text/plain"
                            }
                            context.startActivity(Intent.createChooser(sendIntent, "Share Group Invite"))
                        }) {
                            Icon(Icons.Default.Share, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Share")
                        }
                    },
                    dismissButton = {
                        OutlinedButton(onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("SettleZero Invite", inviteUrl))
                            Toast.makeText(context, "Invite link copied to clipboard!", Toast.LENGTH_SHORT).show()
                            showInviteDialog = false
                        }) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Copy Link")
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun ExpenseItemRow(
    expense: Expense,
    currencyScale: Int,
    currencySymbol: String,
    payerName: String,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val formattedAmount = CurrencyAmount(expense.amountMinor).format(currencyScale, currencySymbol)
    val dateFormatter = remember { SimpleDateFormat("dd MMM, yyyy", Locale.getDefault()) }
    val formattedDate = remember(expense.expenseDate) { dateFormatter.format(Date(expense.expenseDate)) }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = expense.description,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Paid by $payerName • $formattedDate",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = formattedAmount,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete expense",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}
