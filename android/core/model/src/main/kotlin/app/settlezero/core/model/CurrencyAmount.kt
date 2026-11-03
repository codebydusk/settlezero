package app.settlezero.core.model

import kotlinx.serialization.Serializable
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Locale

/**
 * Immutable value representation of a monetary value stored strictly as integer minor units
 * (e.g. paise, cents, fils).
 *
 * Floating-point arithmetic (Float, Double) is strictly prohibited across all financial logic.
 */
@Serializable
@JvmInline
value class CurrencyAmount(val minorUnits: Long) : Comparable<CurrencyAmount> {

    operator fun plus(other: CurrencyAmount): CurrencyAmount =
        CurrencyAmount(Math.addExact(minorUnits, other.minorUnits))

    operator fun minus(other: CurrencyAmount): CurrencyAmount =
        CurrencyAmount(Math.subtractExact(minorUnits, other.minorUnits))

    operator fun times(scalar: Long): CurrencyAmount =
        CurrencyAmount(Math.multiplyExact(minorUnits, scalar))

    operator fun unaryMinus(): CurrencyAmount =
        CurrencyAmount(Math.negateExact(minorUnits))

    override fun compareTo(other: CurrencyAmount): Int =
        minorUnits.compareTo(other.minorUnits)

    val isZero: Boolean get() = minorUnits == 0L
    val isPositive: Boolean get() = minorUnits > 0L
    val isNegative: Boolean get() = minorUnits < 0L

    /**
     * Formats this minor unit amount into a displayable string for a given currency scale.
     * Uses BigDecimal strictly for string formatting and representation.
     */
    fun format(scale: Int, symbol: String? = null): String {
        require(scale >= 0) { "Currency scale cannot be negative: $scale" }
        val divisor = BigDecimal.TEN.pow(scale)
        val decimalValue = BigDecimal(minorUnits).divide(divisor, scale, RoundingMode.UNNECESSARY)
        val formatted = if (scale == 0) {
            String.format(Locale.US, "%,d", minorUnits)
        } else {
            String.format(Locale.US, "%,." + scale + "f", decimalValue)
        }
        return if (!symbol.isNullOrBlank()) "$symbol $formatted" else formatted
    }

    companion object {
        val ZERO = CurrencyAmount(0L)

        /**
         * Converts user decimal input string to CurrencyAmount minor units.
         */
        fun fromMajorUnits(amountMajor: BigDecimal, scale: Int): CurrencyAmount {
            require(scale >= 0) { "Currency scale cannot be negative: $scale" }
            val multiplier = BigDecimal.TEN.pow(scale)
            val minor = amountMajor.multiply(multiplier).setScale(0, RoundingMode.UNNECESSARY).longValueExact()
            return CurrencyAmount(minor)
        }
    }
}

