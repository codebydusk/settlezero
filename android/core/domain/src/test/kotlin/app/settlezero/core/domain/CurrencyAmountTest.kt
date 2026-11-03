package app.settlezero.core.domain

import app.settlezero.core.model.CurrencyAmount
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.math.BigDecimal

class CurrencyAmountTest {

    @Test
    fun `arithmetic operations perform exact integer calculation`() {
        val a = CurrencyAmount(1050L) // ₹10.50
        val b = CurrencyAmount(450L)  // ₹4.50

        assertThat((a + b).minorUnits).isEqualTo(1500L)
        assertThat((a - b).minorUnits).isEqualTo(600L)
        assertThat((b * 3L).minorUnits).isEqualTo(1350L)
        assertThat((-a).minorUnits).isEqualTo(-1050L)
    }

    @Test
    fun `comparison operations evaluate correctly`() {
        val small = CurrencyAmount(100L)
        val big = CurrencyAmount(500L)

        assertThat(small).isLessThan(big)
        assertThat(big).isGreaterThan(small)
        assertThat(small.isPositive).isTrue()
        assertThat(CurrencyAmount.ZERO.isZero).isTrue()
        assertThat(CurrencyAmount(-50L).isNegative).isTrue()
    }

    @Test
    fun `format produces expected display string for two decimals`() {
        val amount = CurrencyAmount(125050L) // 1,250.50
        assertThat(amount.format(scale = 2, symbol = "₹")).isEqualTo("₹ 1,250.50")
        assertThat(amount.format(scale = 2, symbol = "$")).isEqualTo("$ 1,250.50")
    }

    @Test
    fun `format produces expected display string for zero decimals`() {
        val yen = CurrencyAmount(2500L)
        assertThat(yen.format(scale = 0, symbol = "¥")).isEqualTo("¥ 2,500")
    }

    @Test
    fun `format produces expected display string for three decimals`() {
        val dinar = CurrencyAmount(12500L) // 12.500 KD
        assertThat(dinar.format(scale = 3, symbol = "KD")).isEqualTo("KD 12.500")
    }

    @Test
    fun `fromMajorUnits correctly converts BigDecimal to minor units`() {
        val major = BigDecimal("150.75")
        val amount = CurrencyAmount.fromMajorUnits(major, scale = 2)
        assertThat(amount.minorUnits).isEqualTo(15075L)
    }
}

