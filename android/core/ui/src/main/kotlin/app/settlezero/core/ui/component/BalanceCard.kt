package app.settlezero.core.ui.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.settlezero.core.model.CurrencyAmount
import app.settlezero.core.ui.theme.NegativeDebt
import app.settlezero.core.ui.theme.PositiveCredit
import app.settlezero.core.ui.theme.SettledZero

@Composable
fun BalanceCard(
    netBalanceMinor: Long,
    currencyScale: Int,
    currencySymbol: String,
    modifier: Modifier = Modifier
) {
    val amount = CurrencyAmount(kotlin.math.abs(netBalanceMinor))
    val formattedAmount = amount.format(currencyScale, currencySymbol)

    val (title, amountColor, subtitle) = when {
        netBalanceMinor > 0 -> Triple(
            "You are owed",
            PositiveCredit,
            "Apparently friendship has a balance sheet."
        )
        netBalanceMinor < 0 -> Triple(
            "You owe",
            NegativeDebt,
            "Time to clear the tab."
        )
        else -> Triple(
            "All settled",
            SettledZero,
            "Miracles do happen."
        )
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (netBalanceMinor == 0L) "$currencySymbol 0" else formattedAmount,
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = amountColor
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
            )
        }
    }
}

