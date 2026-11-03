package app.settlezero.core.model

import kotlinx.serialization.Serializable

@Serializable
data class Currency(
    val code: String,
    val scale: Int,
    val symbol: String,
    val displayName: String
) {
    companion object {
        val INR = Currency("INR", 2, "₹", "Indian Rupee")
        val USD = Currency("USD", 2, "$", "US Dollar")
        val EUR = Currency("EUR", 2, "€", "Euro")
        val GBP = Currency("GBP", 2, "£", "British Pound")
        val JPY = Currency("JPY", 0, "¥", "Japanese Yen")
        val KWD = Currency("KWD", 3, "KD", "Kuwaiti Dinar")
        val AED = Currency("AED", 2, "AED", "UAE Dirham")
        val CAD = Currency("CAD", 2, "C$", "Canadian Dollar")
        val AUD = Currency("AUD", 2, "A$", "Australian Dollar")
        val SGD = Currency("SGD", 2, "S$", "Singapore Dollar")

        val POPULAR_CURRENCIES: List<Currency> = listOf(
            INR, USD, EUR, GBP, JPY, KWD, AED, CAD, AUD, SGD
        )

        fun findByCode(code: String): Currency {
            return POPULAR_CURRENCIES.find { it.code.equals(code, ignoreCase = true) }
                ?: Currency(code.uppercase(), 2, code.uppercase(), code.uppercase())
        }
    }
}

