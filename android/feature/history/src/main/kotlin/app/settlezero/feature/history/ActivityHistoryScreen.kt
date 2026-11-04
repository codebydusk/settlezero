package app.settlezero.feature.history

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
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
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.settlezero.core.model.ActivityHistory
import app.settlezero.core.ui.component.EmptyStateView
import app.settlezero.core.ui.component.QrCodeView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivityHistoryScreen(
    viewModel: HistoryViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToClonedGroup: (groupId: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Activity History", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.exportGroup() }) {
                        Icon(Icons.Default.Download, contentDescription = "Export JSON")
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
            Column(modifier = Modifier.fillMaxSize()) {
                // Filter chips row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = uiState.selectedFilter == HistoryFilter.ALL,
                        onClick = { viewModel.onFilterSelected(HistoryFilter.ALL) },
                        label = { Text("All") }
                    )
                    FilterChip(
                        selected = uiState.selectedFilter == HistoryFilter.EXPENSES,
                        onClick = { viewModel.onFilterSelected(HistoryFilter.EXPENSES) },
                        label = { Text("Expenses") }
                    )
                    FilterChip(
                        selected = uiState.selectedFilter == HistoryFilter.SETTLEMENTS,
                        onClick = { viewModel.onFilterSelected(HistoryFilter.SETTLEMENTS) },
                        label = { Text("Settlements") }
                    )
                    FilterChip(
                        selected = uiState.selectedFilter == HistoryFilter.MEMBERS,
                        onClick = { viewModel.onFilterSelected(HistoryFilter.MEMBERS) },
                        label = { Text("Members") }
                    )
                }

                if (uiState.filteredHistory.isEmpty() && !uiState.isLoading) {
                    EmptyStateView(
                        title = "No Activity Logged",
                        subtitle = "Events like expenses, settlements, and new members will show up here permanently.",
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(uiState.filteredHistory, key = { it.id }) { item ->
                            val actor = uiState.members.find { it.id == item.actorMemberId }?.name ?: "Someone"
                            ActivityItemRow(item = item, actorName = actor)
                        }
                    }
                }
            }

            // Export Dialog
            if (uiState.showExportDialog && uiState.exportJsonString != null) {
                val jsonText = uiState.exportJsonString!!
                var showQrView by remember { mutableStateOf(false) }

                AlertDialog(
                    onDismissRequest = { viewModel.dismissExportDialog() },
                    title = { Text("Export Group", fontWeight = FontWeight.Bold) },
                    text = {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Standardized portable JSON backup. Safe for offline storage, scanning, and re-import.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                FilterChip(
                                    selected = !showQrView,
                                    onClick = { showQrView = false },
                                    label = { Text("JSON Document") }
                                )
                                FilterChip(
                                    selected = showQrView,
                                    onClick = { showQrView = true },
                                    label = { Text("QR Code") },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Default.QrCode,
                                            contentDescription = null,
                                            modifier = Modifier.width(18.dp)
                                        )
                                    }
                                )
                            }
                            Spacer(modifier = Modifier.height(12.dp))

                            if (showQrView) {
                                val compactJson = remember(jsonText) {
                                    try {
                                        jsonText.lineSequence().map { it.trim() }.joinToString("")
                                    } catch (_: Exception) {
                                        jsonText
                                    }
                                }
                                Box(
                                    modifier = Modifier.fillMaxWidth(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    QrCodeView(
                                        content = compactJson,
                                        sizeDp = 220.dp
                                    )
                                }
                            } else {
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(180.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                                    )
                                ) {
                                    Text(
                                        text = jsonText,
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 10.sp
                                        ),
                                        modifier = Modifier.padding(10.dp)
                                    )
                                }
                            }
                        }
                    },
                    confirmButton = {
                        Button(onClick = {
                            val sendIntent = Intent().apply {
                                action = Intent.ACTION_SEND
                                putExtra(Intent.EXTRA_TEXT, jsonText)
                                type = "application/json"
                            }
                            context.startActivity(Intent.createChooser(sendIntent, "Share SettleZero Backup"))
                        }) {
                            Icon(Icons.Default.Share, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Share")
                        }
                    },
                    dismissButton = {
                        OutlinedButton(onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("SettleZero Backup JSON", jsonText))
                            Toast.makeText(context, "JSON copied to clipboard!", Toast.LENGTH_SHORT).show()
                        }) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Copy")
                        }
                    }
                )
            }

            // Import Preview / Clone Dialog
            if (uiState.showImportDialog && uiState.importPreview != null) {
                val preview = uiState.importPreview!!
                var cloneName by remember { mutableStateOf("${preview.group.name} (Clone)") }

                AlertDialog(
                    onDismissRequest = { viewModel.dismissImportDialog() },
                    title = { Text("Imported Group Snapshot", fontWeight = FontWeight.Bold) },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "Original Group: ${preview.group.name}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Currency: ${preview.group.currencyCode} • Members: ${preview.members.size} • Expenses: ${preview.expenses.size} • Settlements: ${preview.settlements.size}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Import is read-only. To edit or continue adding expenses, clone it into a new independent group with fresh IDs.",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = cloneName,
                                onValueChange = { cloneName = it },
                                label = { Text("Cloned Group Name") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    },
                    confirmButton = {
                        Button(onClick = {
                            viewModel.cloneImportedGroup(cloneName) { newId ->
                                onNavigateToClonedGroup(newId)
                            }
                        }) {
                            Text("Clone as New Group")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { viewModel.dismissImportDialog() }) {
                            Text("Close")
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun ActivityItemRow(
    item: ActivityHistory,
    actorName: String,
    modifier: Modifier = Modifier
) {
    val dateFormatter = remember { SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()) }
    val formattedDate = remember(item.createdAt) { dateFormatter.format(Date(item.createdAt)) }

    val icon = when {
        item.eventType.contains("EXPENSE_DELETED") -> Icons.Default.Delete
        item.eventType.contains("EXPENSE") -> Icons.Default.ReceiptLong
        item.eventType.contains("SETTLEMENT") -> Icons.Default.Payments
        item.eventType.contains("MEMBER") -> Icons.Default.PersonAdd
        else -> Icons.Default.Group
    }

    val iconTint = when {
        item.eventType.contains("DELETED") -> MaterialTheme.colorScheme.error
        item.eventType.contains("SETTLEMENT") -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .padding(14.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.width(24.dp)
            )
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.summary,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "$actorName • $formattedDate",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

