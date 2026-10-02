package com.example.util

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import com.example.data.DomainConstants
import java.text.DecimalFormat
import java.util.Locale

/**
 * VisualTransformation for Pakistani mobile numbers: 03XX-XXXXXXX (11 digits)
 */
class PhoneVisualTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val trimmed = if (text.text.length >= 11) text.text.substring(0, 11) else text.text
        val out = StringBuilder()

        for (i in trimmed.indices) {
            out.append(trimmed[i])
            if (i == 3 && i != trimmed.lastIndex) {
                out.append("-")
            }
        }

        val offsetMapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int {
                return when {
                    offset <= 0 -> 0
                    offset <= 4 -> offset
                    offset <= 11 -> (offset + 1).coerceAtMost(out.length)
                    else -> out.length
                }
            }

            override fun transformedToOriginal(offset: Int): Int {
                return when {
                    offset <= 0 -> 0
                    offset <= 4 -> offset
                    offset == 5 -> 4
                    offset <= 12 -> (offset - 1).coerceAtMost(trimmed.length)
                    else -> trimmed.length
                }
            }
        }

        return TransformedText(AnnotatedString(out.toString()), offsetMapping)
    }
}

/**
 * VisualTransformation for Pakistani CNIC / B-Form: XXXXX-XXXXXXX-X (13 digits)
 */
class CnicVisualTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val trimmed = if (text.text.length >= 13) text.text.substring(0, 13) else text.text
        val out = StringBuilder()

        for (i in trimmed.indices) {
            out.append(trimmed[i])
            if ((i == 4 || i == 11) && i != trimmed.lastIndex) {
                out.append("-")
            }
        }

        val offsetMapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int {
                return when {
                    offset <= 0 -> 0
                    offset <= 5 -> offset
                    offset <= 12 -> (offset + 1).coerceAtMost(out.length)
                    offset <= 13 -> (offset + 2).coerceAtMost(out.length)
                    else -> out.length
                }
            }

            override fun transformedToOriginal(offset: Int): Int {
                return when {
                    offset <= 0 -> 0
                    offset <= 5 -> offset
                    offset == 6 -> 5
                    offset <= 13 -> (offset - 1).coerceAtMost(trimmed.length)
                    offset == 14 -> 12
                    offset <= 15 -> (offset - 2).coerceAtMost(trimmed.length)
                    else -> trimmed.length
                }
            }
        }

        return TransformedText(AnnotatedString(out.toString()), offsetMapping)
    }
}

/**
 * VisualTransformation for Currency/Numbers with comma grouping: 15000 -> 15,000
 */
class CurrencyVisualTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val raw = text.text.filter { it.isDigit() }
        if (raw.isEmpty()) {
            return TransformedText(text, OffsetMapping.Identity)
        }

        val parsed = raw.toLongOrNull() ?: return TransformedText(text, OffsetMapping.Identity)
        val formatted = DecimalFormat("#,###").format(parsed)

        val offsetMapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int {
                if (offset <= 0) return 0
                val safeOffset = offset.coerceAtMost(raw.length)
                val rawPrefix = raw.substring(0, safeOffset)
                val prefixValue = rawPrefix.toLongOrNull() ?: return offset
                val formattedPrefix = DecimalFormat("#,###").format(prefixValue)
                return formattedPrefix.length.coerceAtMost(formatted.length)
            }

            override fun transformedToOriginal(offset: Int): Int {
                if (offset <= 0) return 0
                val safeOffset = offset.coerceAtMost(formatted.length)
                val transformedPrefix = formatted.substring(0, safeOffset)
                val digitCount = transformedPrefix.count { it.isDigit() }
                return digitCount.coerceAtMost(raw.length)
            }
        }

        return TransformedText(AnnotatedString(formatted), offsetMapping)
    }
}

object InputFormatUtils {
    fun cleanDigits(input: String): String {
        return input.filter { it.isDigit() }
    }

    fun cleanCnic(input: String): String {
        return input.filter { it.isDigit() }.take(13)
    }

    /**
     * Sanitizes any Pakistani mobile number format (e.g., "0300-1234567", "+92 300 1234567")
     * into the standard international WhatsApp format: "923001234567".
     */
    fun sanitizePakistanPhoneForWhatsApp(input: String): String {
        val digits = cleanDigits(input)
        return when {
            digits.startsWith("92") && digits.length >= 12 -> digits.take(12)
            digits.startsWith("03") && digits.length == 11 -> "92" + digits.substring(1)
            digits.startsWith("3") && digits.length == 10 -> "92$digits"
            else -> digits
        }
    }

    fun formatCurrency(amount: Double): String {
        return String.format(Locale.US, "%,.0f", amount)
    }

    fun formatCurrencyInWords(amount: Double): String {
        return DomainConstants.numberToWordsRupees(amount)
    }

    fun formatPhoneDisplay(raw: String): String {
        val digits = cleanDigits(raw)
        return if (digits.length == 11) {
            "${digits.substring(0, 4)}-${digits.substring(4)}"
        } else {
            raw
        }
    }

    fun formatCnicDisplay(raw: String): String {
        val digits = cleanDigits(raw)
        return if (digits.length == 13) {
            "${digits.substring(0, 5)}-${digits.substring(5, 12)}-${digits.substring(12)}"
        } else {
            raw
        }
    }

    fun isValidPhone(raw: String): Boolean {
        val digits = cleanDigits(raw)
        return digits.length == 11 && digits.startsWith("03")
    }

    fun isValidCnic(raw: String): Boolean {
        val digits = cleanDigits(raw)
        return digits.isEmpty() || digits.length == 13
    }
}
