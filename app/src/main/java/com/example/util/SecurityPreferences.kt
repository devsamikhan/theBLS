package com.example.util

import android.content.Context
import android.content.SharedPreferences
import com.example.data.DomainConstants
import java.security.MessageDigest

class SecurityPreferences(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "bls_security_prefs"
        private const val KEY_ADMIN_PIN_HASH = "key_admin_pin_hash"
        private const val KEY_ACCOUNTANT_PIN_HASH = "key_accountant_pin_hash"
        private const val KEY_FAILED_ATTEMPTS = "key_failed_attempts"
        private const val KEY_LOCKOUT_UNTIL = "key_lockout_until"
        private const val KEY_SOUND_ENABLED = "key_sound_enabled"
        private const val KEY_FIREBASE_URL = "key_firebase_url"
        private const val KEY_FIREBASE_AUTH_SECRET = "key_firebase_auth_secret"
        private const val KEY_LAST_READ_ACTIVITY = "key_last_read_activity"

        const val DEFAULT_ADMIN_PIN = "8888"
        const val DEFAULT_ACCOUNTANT_PIN = "1111"
        const val DEFAULT_SUPER_ADMIN_KEY = "BLS@SuperAdmin786"
        const val DEFAULT_FIREBASE_URL = "https://bls-school-system-default-rtdb.firebaseio.com"
        private const val PIN_SALT = "BLS_LEDGER_SALT_2026#"
        private const val MAX_FAILED_ATTEMPTS = 5
        private const val LOCKOUT_DURATION_MS = 30_000L // 30 seconds cooldown
    }

    private fun hashPin(pin: String): String {
        val input = "$PIN_SALT$pin"
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    init {
        // Initialize default hashed PINs if first run or legacy format
        if (!prefs.contains(KEY_ADMIN_PIN_HASH)) {
            val legacyAdmin = prefs.getString("key_admin_pin", DEFAULT_ADMIN_PIN) ?: DEFAULT_ADMIN_PIN
            setAdminPin(legacyAdmin)
        }
        if (!prefs.contains(KEY_ACCOUNTANT_PIN_HASH)) {
            val legacyAccountant = prefs.getString("key_accountant_pin", DEFAULT_ACCOUNTANT_PIN) ?: DEFAULT_ACCOUNTANT_PIN
            setAccountantPin(legacyAccountant)
        }
    }

    fun getAdminPin(): String {
        // Return masked indicator or verify hash
        return if (prefs.getString(KEY_ADMIN_PIN_HASH, "") == hashPin(DEFAULT_ADMIN_PIN)) DEFAULT_ADMIN_PIN else "••••"
    }

    fun getAccountantPin(): String {
        return if (prefs.getString(KEY_ACCOUNTANT_PIN_HASH, "") == hashPin(DEFAULT_ACCOUNTANT_PIN)) DEFAULT_ACCOUNTANT_PIN else "••••"
    }

    fun setAdminPin(newPin: String) {
        prefs.edit().putString(KEY_ADMIN_PIN_HASH, hashPin(newPin.trim())).apply()
    }

    fun setAccountantPin(newPin: String) {
        prefs.edit().putString(KEY_ACCOUNTANT_PIN_HASH, hashPin(newPin.trim())).apply()
    }

    /**
     * Checks if user is currently locked out due to repeated wrong PIN attempts.
     */
    fun isLockedOut(): Boolean {
        val lockoutUntil = prefs.getLong(KEY_LOCKOUT_UNTIL, 0L)
        return System.currentTimeMillis() < lockoutUntil
    }

    fun getRemainingLockoutSeconds(): Int {
        val lockoutUntil = prefs.getLong(KEY_LOCKOUT_UNTIL, 0L)
        val diff = lockoutUntil - System.currentTimeMillis()
        return if (diff > 0) (diff / 1000).toInt() else 0
    }

    fun recordFailedAttempt() {
        val attempts = prefs.getInt(KEY_FAILED_ATTEMPTS, 0) + 1
        if (attempts >= MAX_FAILED_ATTEMPTS) {
            prefs.edit()
                .putLong(KEY_LOCKOUT_UNTIL, System.currentTimeMillis() + LOCKOUT_DURATION_MS)
                .putInt(KEY_FAILED_ATTEMPTS, 0)
                .apply()
        } else {
            prefs.edit().putInt(KEY_FAILED_ATTEMPTS, attempts).apply()
        }
    }

    fun resetFailedAttempts() {
        prefs.edit()
            .putInt(KEY_FAILED_ATTEMPTS, 0)
            .putLong(KEY_LOCKOUT_UNTIL, 0L)
            .apply()
    }

    fun validatePin(pin: String): String {
        if (isLockedOut()) {
            return DomainConstants.ROLE_NONE
        }

        val trimmed = pin.trim()
        val hashedInput = hashPin(trimmed)

        val adminHash = prefs.getString(KEY_ADMIN_PIN_HASH, hashPin(DEFAULT_ADMIN_PIN))
        val accountantHash = prefs.getString(KEY_ACCOUNTANT_PIN_HASH, hashPin(DEFAULT_ACCOUNTANT_PIN))

        return when {
            hashedInput == adminHash -> {
                resetFailedAttempts()
                DomainConstants.ROLE_ADMIN
            }
            hashedInput == accountantHash -> {
                resetFailedAttempts()
                DomainConstants.ROLE_ACCOUNTANT
            }
            else -> {
                recordFailedAttempt()
                DomainConstants.ROLE_NONE
            }
        }
    }

    // ==================== APP SETTINGS / SOUND PREFERENCES ====================
    fun isSoundEnabled(): Boolean {
        return prefs.getBoolean(KEY_SOUND_ENABLED, true)
    }

    fun setSoundEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SOUND_ENABLED, enabled).apply()
    }

    // ==================== ACTIVITY FEED & CLOUD SYNC PREFERENCES ====================
    fun getLastReadActivityTimestamp(): Long {
        return prefs.getLong(KEY_LAST_READ_ACTIVITY, 0L)
    }

    fun setLastReadActivityTimestamp(timestamp: Long) {
        prefs.edit().putLong(KEY_LAST_READ_ACTIVITY, timestamp).apply()
    }

    fun getFirebaseUrl(): String {
        return prefs.getString(KEY_FIREBASE_URL, DEFAULT_FIREBASE_URL)?.trim().takeIf { !it.isNullOrBlank() } ?: DEFAULT_FIREBASE_URL
    }

    fun setFirebaseUrl(url: String) {
        prefs.edit().putString(KEY_FIREBASE_URL, url.trim()).apply()
    }

    fun getFirebaseAuthSecret(): String {
        return prefs.getString(KEY_FIREBASE_AUTH_SECRET, "")?.trim() ?: ""
    }

    fun setFirebaseAuthSecret(secret: String) {
        prefs.edit().putString(KEY_FIREBASE_AUTH_SECRET, secret.trim()).apply()
    }

    fun resetToDefaults() {
        prefs.edit()
            .putString(KEY_ADMIN_PIN_HASH, hashPin(DEFAULT_ADMIN_PIN))
            .putString(KEY_ACCOUNTANT_PIN_HASH, hashPin(DEFAULT_ACCOUNTANT_PIN))
            .putString(KEY_FIREBASE_URL, DEFAULT_FIREBASE_URL)
            .putString(KEY_FIREBASE_AUTH_SECRET, "")
            .putLong(KEY_LAST_READ_ACTIVITY, 0L)
            .putInt(KEY_FAILED_ATTEMPTS, 0)
            .putLong(KEY_LOCKOUT_UNTIL, 0L)
            .apply()
    }
}
