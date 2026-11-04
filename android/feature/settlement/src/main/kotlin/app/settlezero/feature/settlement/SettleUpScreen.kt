package app.settlezero.feature.settlement

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
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.settlezero.core.model.CurrencyAmount
import app.settlezero.core.model.DebtTransfer
import app.settlezero.core.model.Member
import app.settlezero.core.ui.component.CurrencyTextField
import app.settlezero.core.ui.component.EmptyStateView
import app.settlezero.core.ui.component.MemberAvatar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettleUpScreen(
    viewModel: SettlementViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Settle Up", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (uiState.simplifiedDebts.isEmpty() && !uiState.isLoading) {
                EmptyStateView(
                    title = "All Settled Up!",
                    subtitle = "No debts to settle. Either everyone is incredibly generous, or nobody has added expenses.",
                    actionLabel = "Back to Group",
                    onActionClick = onNavigateBack,
                    modifier = Modifier.align(Alignment.Center)
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        Text(
                            text = "Recommended Repayments",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Calculated using greedy minimum-cash-flow simplification.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    items(uiState.simplifiedDebts) { transfer ->
                        DebtTransferCard(
                            transfer = transfer,
                            members = uiState.members,
                            scale = uiState.currency.scale,
                            symbol = uiState.currency.symbol,
                            onClick = { viewModel.selectTransfer(transfer) }
                        )
                    }
                }
            }

            if (uiState.showPaymentDialog && uiState.selectedTransfer != null) {
                val transfer = uiState.selectedTransfer!!
                val payer = uiState.members.find { it.id == transfer.fromMemberId }?.name ?: "Someone"
                val recipient = uiState.members.find { it.id == transfer.toMemberId }?.name ?: "Someone"
                val maxDebtFormatted = CurrencyAmount(transfer.amountMinor)
                    .format(uiState.currency.scale, uiState.currency.symbol)

                AlertDialog(
                    onDismissRequest = viewModel::onDismissDialog,
                    title = { Text("Record Settlement", fontWeight = FontWeight.Bold) },
                    text = {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "$payer pays $recipient",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Outstanding balance: $maxDebtFormatted",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            CurrencyTextField(
                                value = uiState.amountInput,
                                onValueChange = viewModel::onAmountChanged,
                                currencySymbol = uiState.currency.symbol,
                                label = "Settlement Amount",
                                isError = !uiState.isAmountValid && uiState.amountInput.isNotBlank(),
                                errorMessage = "Cannot exceed $maxDebtFormatted"
                            )

                            OutlinedTextField(
                                value = uiState.note,
                                onValueChange = viewModel::onNoteChanged,
                                label = { Text("Note (Optional)") },
                                placeholder = { Text("e.g. UPI, Cash, GPay ref") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )

                            if (uiState.errorMessage != null) {
                                Text(
                                    text = uiState.errorMessage ?: "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = { viewModel.recordSettlement(onSuccess = onNavigateBack) },
                            enabled = uiState.isAmountValid && !uiState.isSubmitting
                        ) {
                            Text(if (uiState.isSubmitting) "Recording..." else "Confirm")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = viewModel::onDismissDialog) {
                            Text("Cancel")
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun DebtTransferCard(
    transfer: DebtTransfer,
    members: List<Member>,
    scale: Int,
    symbol: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val payer = members.find { it.id == transfer.fromMemberId }
    val recipient = members.find { it.id == transfer.toMemberId }
    val formattedAmount = CurrencyAmount(transfer.amountMinor).format(scale, symbol)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        )
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                MemberAvatar(name = payer?.name ?: "Someone", size = 36.dp)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = payer?.name ?: "Someone",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = "pays",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                MemberAvatar(name = recipient?.name ?: "Someone", size = 36.dp)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = recipient?.name ?: "Someone",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Text(
                text = formattedAmount,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

