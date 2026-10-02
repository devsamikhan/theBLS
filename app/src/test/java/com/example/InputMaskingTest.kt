package com.example

import com.example.util.InputFormatUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InputMaskingTest {

    @Test
    fun testFormatPhoneDisplay() {
        val raw = "03001234567"
        val formatted = InputFormatUtils.formatPhoneDisplay(raw)
        assertEquals("0300-1234567", formatted)
    }

    @Test
    fun testIsValidPhone() {
        assertTrue(InputFormatUtils.isValidPhone("03001234567"))
        assertTrue(InputFormatUtils.isValidPhone("03459876543"))
        assertFalse(InputFormatUtils.isValidPhone("02001234567")) // Invalid prefix
        assertFalse(InputFormatUtils.isValidPhone("0300123456"))  // Only 10 digits
        assertFalse(InputFormatUtils.isValidPhone("030012345678")) // 12 digits
    }

    @Test
    fun testFormatCnicDisplay() {
        val raw = "3520112345671"
        val formatted = InputFormatUtils.formatCnicDisplay(raw)
        assertEquals("35201-1234567-1", formatted)
    }

    @Test
    fun testIsValidCnic() {
        assertTrue(InputFormatUtils.isValidCnic("3520112345671"))
        assertTrue(InputFormatUtils.isValidCnic("")) // Optional
        assertFalse(InputFormatUtils.isValidCnic("35201123456")) // Incomplete
    }

    @Test
    fun testFormatCurrency() {
        val amount = 15000.0
        val formatted = InputFormatUtils.formatCurrency(amount)
        assertEquals("15,000", formatted)
    }
}
