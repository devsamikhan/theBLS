package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * =====================================================================
 * MINIMALIST MATERIAL 3 COLOR PALETTE FOR BLS CASH & LEDGER SYSTEM
 * =====================================================================
 * Designed with a modern, high-contrast, distraction-free FinTech aesthetic:
 * - Crisp, serene neutral surfaces with razor-thin hairline borders.
 * - Deep, confident Sapphire Navy primary for institutional authority.
 * - High-clarity semantic financial tokens (Emerald Cash, Royal Bank, Crimson Expense).
 * - Full WCAG 2.1 AA / AAA contrast compliance across Light and Dark themes.
 */

// ---------------------------------------------------------------------
// 1. PRIMARY: Confident Sapphire Slate (Institutional Brand & Accents)
// ---------------------------------------------------------------------
val M3PrimaryLight = Color(0xFF0F52BA)          // Deep Sapphire
val M3OnPrimaryLight = Color(0xFFFFFFFF)
val M3PrimaryContainerLight = Color(0xFFEFF6FF) // Mist Blue-50
val M3OnPrimaryContainerLight = Color(0xFF1E3A8A)

val M3PrimaryDark = Color(0xFF60A5FA)           // Soft Sky-400
val M3OnPrimaryDark = Color(0xFF0F172A)
val M3PrimaryContainerDark = Color(0xFF1E3A8A)
val M3OnPrimaryContainerDark = Color(0xFFDBEAFE)

// ---------------------------------------------------------------------
// 2. SECONDARY: Neutral Slate (Structural Balance & Metadata)
// ---------------------------------------------------------------------
val M3SecondaryLight = Color(0xFF475569)        // Slate-600
val M3OnSecondaryLight = Color(0xFFFFFFFF)
val M3SecondaryContainerLight = Color(0xFFF1F5F9) // Slate-100
val M3OnSecondaryContainerLight = Color(0xFF0F172A)

val M3SecondaryDark = Color(0xFF94A3B8)         // Slate-400
val M3OnSecondaryDark = Color(0xFF0F172A)
val M3SecondaryContainerDark = Color(0xFF1E293B) // Slate-800
val M3OnSecondaryContainerDark = Color(0xFFF1F5F9)

// ---------------------------------------------------------------------
// 3. TERTIARY: Academic Amber / Gold (Badges, Honors, Verified Stars)
// ---------------------------------------------------------------------
val M3TertiaryLight = Color(0xFFB45309)         // Amber-700
val M3OnTertiaryLight = Color(0xFFFFFFFF)
val M3TertiaryContainerLight = Color(0xFFFEF3C7) // Amber-100
val M3OnTertiaryContainerLight = Color(0xFF78350F)

val M3TertiaryDark = Color(0xFFFBBF24)          // Amber-400
val M3OnTertiaryDark = Color(0xFF451A03)
val M3TertiaryContainerDark = Color(0xFF78350F)
val M3OnTertiaryContainerDark = Color(0xFFFEF3C7)

// ---------------------------------------------------------------------
// 4. ERROR: Alert Crimson (Arrears, Defaulters, Warnings)
// ---------------------------------------------------------------------
val M3ErrorLight = Color(0xFFE11D48)            // Rose-600
val M3OnErrorLight = Color(0xFFFFFFFF)
val M3ErrorContainerLight = Color(0xFFFFF1F2)   // Rose-50
val M3OnErrorContainerLight = Color(0xFF9F1239)

val M3ErrorDark = Color(0xFFFB7185)             // Rose-400
val M3OnErrorDark = Color(0xFF4C0519)
val M3ErrorContainerDark = Color(0xFF881337)
val M3OnErrorContainerDark = Color(0xFFFFE4E6)

// ---------------------------------------------------------------------
// 5. SURFACES & BACKGROUNDS (Clean, Calm, Crisp Minimalist Surfaces)
// ---------------------------------------------------------------------
val M3BackgroundLight = Color(0xFFF8FAFC)       // Crisp Slate-50
val M3OnBackgroundLight = Color(0xFF0F172A)     // Ink Black Slate-900
val M3SurfaceLight = Color(0xFFFFFFFF)          // Pure Card White
val M3OnSurfaceLight = Color(0xFF0F172A)
val M3SurfaceVariantLight = Color(0xFFF1F5F9)   // Soft Slate-100 for sub-cards
val M3OnSurfaceVariantLight = Color(0xFF64748B) // Slate-500 for secondary labels
val M3OutlineLight = Color(0xFFCBD5E1)          // Slate-300
val M3OutlineVariantLight = Color(0xFFE2E8F0)   // Slate-200 (Hairline 1dp border)

val M3BackgroundDark = Color(0xFF0B0F17)        // OLED Deep Slate
val M3OnBackgroundDark = Color(0xFFF8FAFC)
val M3SurfaceDark = Color(0xFF131B2E)           // Deep Slate Card Surface
val M3OnSurfaceDark = Color(0xFFF8FAFC)
val M3SurfaceVariantDark = Color(0xFF1E293B)    // Slate-800
val M3OnSurfaceVariantDark = Color(0xFF94A3B8)  // Slate-400
val M3OutlineDark = Color(0xFF475569)           // Slate-600
val M3OutlineVariantDark = Color(0xFF1E293B)    // Slate-800 Hairline border

// =====================================================================
// DOMAIN-SPECIFIC M3 EXTENSION: FINANCIAL COLOR SCHEME
// =====================================================================
@Immutable
data class FinancialColorScheme(
    val cash: Color,
    val onCash: Color,
    val cashContainer: Color,
    val onCashContainer: Color,

    val bank: Color,
    val onBank: Color,
    val bankContainer: Color,
    val onBankContainer: Color,

    val expense: Color,
    val onExpense: Color,
    val expenseContainer: Color,
    val onExpenseContainer: Color,

    val goldBadge: Color,
    val onGoldBadge: Color,
    val goldBadgeContainer: Color,
    val onGoldBadgeContainer: Color,

    val shimmerBase: Color,
    val shimmerHighlight: Color
) {
    val amber: Color get() = goldBadge
    val amberContainer: Color get() = goldBadgeContainer
    val gold: Color get() = goldBadge
    val goldContainer: Color get() = goldBadgeContainer
}

val LightFinancialColorScheme = FinancialColorScheme(
    cash = Color(0xFF059669),                   // Emerald-600
    onCash = Color(0xFFFFFFFF),
    cashContainer = Color(0xFFECFDF5),          // Emerald-50
    onCashContainer = Color(0xFF065F46),

    bank = Color(0xFF2563EB),                   // Blue-600
    onBank = Color(0xFFFFFFFF),
    bankContainer = Color(0xFFEFF6FF),          // Blue-50
    onBankContainer = Color(0xFF1E40AF),

    expense = Color(0xFFE11D48),                // Rose-600
    onExpense = Color(0xFFFFFFFF),
    expenseContainer = Color(0xFFFFF1F2),       // Rose-50
    onExpenseContainer = Color(0xFF9F1239),

    goldBadge = Color(0xFFD97706),              // Amber-600
    onGoldBadge = Color(0xFFFFFFFF),
    goldBadgeContainer = Color(0xFFFEF3C7),     // Amber-100
    onGoldBadgeContainer = Color(0xFF78350F),

    shimmerBase = Color(0xFFF1F5F9),
    shimmerHighlight = Color(0xFFF8FAFC)
)

val DarkFinancialColorScheme = FinancialColorScheme(
    cash = Color(0xFF34D399),                   // Emerald-400
    onCash = Color(0xFF064E3B),
    cashContainer = Color(0xFF065F46),
    onCashContainer = Color(0xFFA7F3D0),

    bank = Color(0xFF60A5FA),                   // Blue-400
    onBank = Color(0xFF1E3A8A),
    bankContainer = Color(0xFF1E40AF),
    onBankContainer = Color(0xFFDBEAFE),

    expense = Color(0xFFFB7185),                // Rose-400
    onExpense = Color(0xFF881337),
    expenseContainer = Color(0xFF9F1239),
    onExpenseContainer = Color(0xFFFFE4E6),

    goldBadge = Color(0xFFFBBF24),              // Amber-400
    onGoldBadge = Color(0xFF78350F),
    goldBadgeContainer = Color(0xFF92400E),
    onGoldBadgeContainer = Color(0xFFFDE68A),

    shimmerBase = Color(0xFF1E293B),
    shimmerHighlight = Color(0xFF334155)
)

val LocalFinancialColors = staticCompositionLocalOf {
    LightFinancialColorScheme
}

/**
 * Convenience accessor for Financial Tokens:
 * Usage: `MaterialTheme.financialColors.cash`
 */
val MaterialTheme.financialColors: FinancialColorScheme
    @Composable
    @ReadOnlyComposable
    get() = LocalFinancialColors.current

// =====================================================================
// BACKWARD COMPATIBILITY PALETTE ALIASES
// =====================================================================
val BlsBackground = M3BackgroundLight
val BlsPrimary = M3PrimaryLight
val BlsAccentContainer = M3PrimaryContainerLight
val BlsOnAccentContainer = M3OnPrimaryContainerLight

val BlsTextPrimary = M3OnBackgroundLight
val BlsTextSecondary = M3OnSurfaceVariantLight
val BlsBorder = M3OutlineVariantLight
val BlsSurfaceVariant = M3SurfaceVariantLight
val BlsBottomNavBackground = Color(0xFFFFFFFF)
val BlsWhite = Color(0xFFFFFFFF)
val BlsSurface = M3SurfaceLight

val BlsDarkBackground = M3BackgroundDark
val BlsDarkSurface = M3SurfaceDark
val BlsDarkSurfaceVariant = M3SurfaceVariantDark
val BlsDarkPrimary = M3PrimaryDark
val BlsDarkAccentContainer = M3PrimaryContainerDark
val BlsDarkOnAccentContainer = M3OnPrimaryContainerDark

val BlsDarkTextPrimary = M3OnBackgroundDark
val BlsDarkTextSecondary = M3OnSurfaceVariantDark
val BlsDarkBorder = M3OutlineDark
val BlsDarkBottomNavBackground = Color(0xFF0F172A)

val BlsGreenPositive = Color(0xFF059669)
val BlsGreenLightPositive = Color(0xFFECFDF5)
val BlsGreenDarkPositive = Color(0xFF34D399)

val BlsBlueBank = Color(0xFF2563EB)
val BlsBlueLightBank = Color(0xFFEFF6FF)
val BlsBlueDarkBank = Color(0xFF60A5FA)

val BlsRedNegative = Color(0xFFE11D48)
val BlsRedLightNegative = Color(0xFFFFF1F2)
val BlsRedDarkNegative = Color(0xFF4C0519)
val BlsRedAlertButton = Color(0xFFE11D48)
val BlsRedDarkText = Color(0xFFFB7185)

val BlsGold = Color(0xFFD97706)
val BlsGoldLight = Color(0xFFFEF3C7)

val BlsBadgeGreenBg = Color(0xFFDCFCE7)
val BlsBadgeGreenText = Color(0xFF166534)
val BlsBadgeRedBg = Color(0xFFFEE2E2)
val BlsBadgeRedText = Color(0xFF991B1B)
val BlsBadgeGoldBg = Color(0xFFFEF3C7)
val BlsBadgeGoldText = Color(0xFF92400E)
val BlsBadgeBlueBg = Color(0xFFE0F2FE)
val BlsBadgeBlueText = Color(0xFF075985)

val BlsShimmerBase = Color(0xFFF1F5F9)
val BlsShimmerHighlight = Color(0xFFF8FAFC)
val BlsDarkShimmerBase = Color(0xFF1E293B)
val BlsDarkShimmerHighlight = Color(0xFF334155)
