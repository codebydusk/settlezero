package app.settlezero.core.ui.component

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType

@Composable
fun CurrencyTextField(
    value: String,
    onValueChange: (String) -> Unit,
    currencySymbol: String,
    label: String = "Amount",
    isError: Boolean = false,
    errorMessage: String? = null,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = { input ->
            // Allow numbers with at most one decimal point
            if (input.isEmpty() || input.matches("^\\d*\\.?\\d*$".toRegex())) {
                onValueChange(input)
            }
        },
        label = { Text(label) },
        leadingIcon = { Text(currencySymbol) },
        isError = isError,
        supportingText = if (isError && errorMessage != null) {
            { Text(errorMessage) }
        } else null,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true,
        modifier = modifier.fillMaxWidth()
    )
}

