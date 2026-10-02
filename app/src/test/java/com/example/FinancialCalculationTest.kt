package com.example

import com.example.data.DomainConstants
import org.junit.Assert.assertEquals
import org.junit.Test

class FinancialCalculationTest {

    @Test
    fun testCalculateDiscountedFee_None() {
        val baseFee = 2000.0
        val discounted = DomainConstants.calculateDiscountedFee(baseFee, "None")
        assertEquals(2000.0, discounted, 0.001)
    }

    @Test
    fun testCalculateDiscountedFee_Sibling20() {
        val baseFee = 2000.0
        val discounted = DomainConstants.calculateDiscountedFee(baseFee, "Sibling 20%")
        assertEquals(1600.0, discounted, 0.001)
    }

    @Test
    fun testCalculateDiscountedFee_Sibling40() {
        val baseFee = 2000.0
        val discounted = DomainConstants.calculateDiscountedFee(baseFee, "Sibling 40%")
        assertEquals(1200.0, discounted, 0.001)
    }

    @Test
    fun testCalculateDiscountedFee_Orphan50() {
        val baseFee = 2000.0
        val discounted = DomainConstants.calculateDiscountedFee(baseFee, "Orphan 50%")
        assertEquals(1000.0, discounted, 0.001)
    }

    @Test
    fun testCalculateDiscountedFee_PoorFree() {
        val baseFee = 2000.0
        val discounted = DomainConstants.calculateDiscountedFee(baseFee, "Poor Free")
        assertEquals(0.0, discounted, 0.001)
    }

    @Test
    fun testCalculateDiscountedFee_OwnerDiscount100() {
        val baseFee = 2000.0
        val discounted = DomainConstants.calculateDiscountedFee(baseFee, "Owner Discount 100%")
        assertEquals(0.0, discounted, 0.001)
    }

    @Test
    fun testCalculateDiscountedFee_Special10() {
        val baseFee = 2000.0
        val discounted = DomainConstants.calculateDiscountedFee(baseFee, "Special Discount 10%")
        assertEquals(1800.0, discounted, 0.001)
    }

    @Test
    fun testDailyClosingBalanceFormula() {
        val openingCash = 5000.0
        val openingBank = 15000.0
        val incomeCash = 8000.0
        val incomeBank = 4000.0
        val expenseCash = 3000.0
        val expenseBank = 1000.0

        val closingCash = openingCash + incomeCash - expenseCash
        val closingBank = openingBank + incomeBank - expenseBank
        val netBalance = (closingCash + closingBank)

        assertEquals(10000.0, closingCash, 0.001)
        assertEquals(18000.0, closingBank, 0.001)
        assertEquals(28000.0, netBalance, 0.001)
    }
}
