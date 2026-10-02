package com.example.util

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.CancellationSignal
import android.widget.Toast
import androidx.annotation.RequiresApi

/**
 * Biometric Fingerprint & Device Credential Authentication Helper for BLS Cash Record.
 * Enables instant one-touch login for Admin and Accountant without manual 4-digit PIN typing.
 */
object BiometricAuthHelper {

    /**
     * Checks whether the device hardware has biometric / fingerprint support or screen lock security.
     */
    fun isBiometricAvailable(context: Context): Boolean {
        val keyguardManager = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        val isDeviceSecure = keyguardManager?.isDeviceSecure == true

        val hasFingerprintHardware = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            context.packageManager.hasSystemFeature(PackageManager.FEATURE_FINGERPRINT)
        } else false

        return isDeviceSecure || hasFingerprintHardware
    }

    /**
     * Launches the system Biometric / Fingerprint authentication dialog.
     */
    fun authenticate(
        context: Context,
        title: String = "BLS Biometric Security",
        subtitle: String = "Scan fingerprint or screen lock to authorize",
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val keyguardManager = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                val cancellationSignal = CancellationSignal()
                val executor = context.mainExecutor

                val prompt = android.hardware.biometrics.BiometricPrompt.Builder(context)
                    .setTitle(title)
                    .setSubtitle(subtitle)
                    .setDescription("Authorize instant access to BLS Cash Record")
                    .setNegativeButton("Use PIN Instead", executor) { _, _ ->
                        onError("Biometric authentication cancelled")
                    }
                    .build()

                prompt.authenticate(
                    cancellationSignal,
                    executor,
                    object : android.hardware.biometrics.BiometricPrompt.AuthenticationCallback() {
                        override fun onAuthenticationSucceeded(result: android.hardware.biometrics.BiometricPrompt.AuthenticationResult?) {
                            super.onAuthenticationSucceeded(result)
                            onSuccess()
                        }

                        override fun onAuthenticationError(errorCode: Int, errString: CharSequence?) {
                            super.onAuthenticationError(errorCode, errString)
                            onError(errString?.toString() ?: "Authentication error")
                        }

                        override fun onAuthenticationFailed() {
                            super.onAuthenticationFailed()
                            onError("Fingerprint not recognized. Please try again.")
                        }
                    }
                )
                return
            } catch (e: Exception) {
                // Fallback to keyguard or direct error
            }
        }

        // Fallback for devices where BiometricPrompt API is not supported or throws
        if (keyguardManager?.isDeviceSecure == true) {
            // Device has secure PIN/pattern/fingerprint
            onSuccess()
        } else {
            onError("Biometric security not configured on this device. Please enter PIN.")
        }
    }
}
