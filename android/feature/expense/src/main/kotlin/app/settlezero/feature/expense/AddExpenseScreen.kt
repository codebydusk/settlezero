package app.settlezero.feature.expense

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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.settlezero.core.model.CurrencyAmount
import app.settlezero.core.model.SplitType
import app.settlezero.core.ui.component.CurrencyTextField
import app.settlezero.core.ui.component.MemberAvatar
import app.settlezero.core.ui.theme.NegativeDebt
import app.settlezero.core.ui.theme.PositiveCredit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddExpenseScreen(
    viewModel: ExpenseViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    var payerMenuExpanded by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Add Expense", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        },
        bottomBar = {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier
                        .padding(16.dp)
                        .fillMaxWidth()
                ) {
                    if (uiState.errorMessage != null) {
                        Text(
                            text = uiState.errorMessage ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    Button(
                        onClick = { viewModel.submitExpense(onSuccess = onNavigateBack) },
                        enabled = uiState.isInputValid && !uiState.isSubmitting,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                    ) {
                        Text(
                            text = if (uiState.isSubmitting) "Saving..." else "Save Expense",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Description input
            item {
                OutlinedTextField(
                    value = uiState.description,
                    onValueChange = viewModel::onDescriptionChanged,
                    label = { Text("Description") },
                    placeholder = { Text("e.g. Dinner, Groceries, Flight tickets") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Total Amount input
            item {
                CurrencyTextField(
                    value = uiState.amountInput,
                    onValueChange = viewModel::onAmountChanged,
                    currencySymbol = uiState.currency.symbol,
                    label = "Total Amount"
                )
            }

            // Paid By dropdown
            item {
                val payer = uiState.members.find { it.id == uiState.payerMemberId }
                ExposedDropdownMenuBox(
                    expanded = payerMenuExpanded,
                    onExpandedChange = { payerMenuExpanded = !payerMenuExpanded }
                ) {
                    OutlinedTextField(
                        value = payer?.name ?: "Select payer",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Paid By") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = payerMenuExpanded) },
                        modifier = Modifier
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                            .fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = payerMenuExpanded,
                        onDismissRequest = { payerMenuExpanded = false }
                    ) {
                        uiState.members.forEach { member ->
                            DropdownMenuItem(
                                text = { Text(member.name) },
                                onClick = {
                                    viewModel.onPayerSelected(member.id)
                                    payerMenuExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            // Split Type selector tabs
            item {
                Text(
                    text = "Split Method",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = uiState.splitType == SplitType.EQUAL,
                        onClick = { viewModel.onSplitTypeChanged(SplitType.EQUAL) },
                        label = { Text("Equally") }
                    )
                    FilterChip(
                        selected = uiState.splitType == SplitType.UNEQUAL,
                        onClick = { viewModel.onSplitTypeChanged(SplitType.UNEQUAL) },
                        label = { Text("Exact Amounts") }
                    )
                }
            }

            // Discrepancy indicator for UNEQUAL split
            if (uiState.splitType == SplitType.UNEQUAL) {
                item {
                    val rem = uiState.remainingMinor
                    val remFormatted = CurrencyAmount(kotlin.math.abs(rem))
                        .format(uiState.currency.scale, uiState.currency.symbol)

                    val text = when {
                        rem == 0L -> "Exact match! All allocated."
                        rem > 0L -> "Remaining to allocate: $remFormatted"
                        else -> "Over-allocated by: $remFormatted"
                    }
                    val color = if (rem == 0L) PositiveCredit else NegativeDebt

                    Text(
                        text = text,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = color
                    )
                }
            }

            // Participant rows
            item {
                Text(
                    text = "Participants (${uiState.selectedParticipantIds.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }

            items(uiState.members, key = { it.id }) { member ->
                val isSelected = uiState.selectedParticipantIds.contains(member.id)
                val allocation = uiState.computedAllocations.find { it.memberId == member.id }
                val allocatedFormatted = allocation?.let {
                    CurrencyAmount(it.amountMinor).format(uiState.currency.scale, uiState.currency.symbol)
                } ?: ""

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSelected) {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                        } else {
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
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
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { viewModel.toggleParticipant(member.id) },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = isSelected,
                                onCheckedChange = { viewModel.toggleParticipant(member.id) }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            MemberAvatar(name = member.name, role = member.role)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = member.name,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                            )
                        }

                        if (isSelected) {
                            if (uiState.splitType == SplitType.EQUAL) {
                                Text(
                                    text = allocatedFormatted,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            } else {
                                OutlinedTextField(
                                    value = uiState.customAmounts[member.id] ?: "",
                                    onValueChange = { viewModel.onCustomAmountChanged(member.id, it) },
                                    placeholder = { Text("0") },
                                    singleLine = true,
                                    modifier = Modifier.width(110.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Note input
            item {
                OutlinedTextField(
                    value = uiState.note,
                    onValueChange = viewModel::onNoteChanged,
                    label = { Text("Note (Optional)") },
                    placeholder = { Text("Any details or comments") },
                    singleLine = false,
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

