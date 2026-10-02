package com.example.util

import android.media.AudioManager
import android.media.ToneGenerator
import android.util.Log

object AudioFeedback {

    private const val TAG = "AudioFeedback"
    private var sharedToneGen: ToneGenerator? = null

    var isSoundEnabled: Boolean = true

    @Synchronized
    private fun getOrCreateToneGenerator(): ToneGenerator? {
        return try {
            if (sharedToneGen == null) {
                sharedToneGen = ToneGenerator(AudioManager.STREAM_MUSIC, 85)
            }
            sharedToneGen
        } catch (e: Exception) {
            Log.w(TAG, "Failed to initialize shared ToneGenerator: ${e.message}")
            null
        }
    }

    /**
     * Crisp cash-register style success tone for fee receipts and voucher recordings.
     */
    fun playSuccessChime() {
        if (!isSoundEnabled) return
        try {
            val gen = getOrCreateToneGenerator()
            gen?.startTone(ToneGenerator.TONE_PROP_BEEP2, 160)
        } catch (e: Exception) {
            Log.w(TAG, "Error playing success chime: ${e.message}")
        }
    }

    /**
     * Subtle click tick for button taps and list selections.
     */
    fun playClickTick() {
        if (!isSoundEnabled) return
        try {
            val gen = getOrCreateToneGenerator()
            gen?.startTone(ToneGenerator.TONE_PROP_ACK, 50)
        } catch (e: Exception) {
            Log.w(TAG, "Error playing click tick: ${e.message}")
        }
    }

    /**
     * Distinct warning tone for fee overdue, balance mismatch, or validation errors.
     */
    fun playAlertWarning() {
        if (!isSoundEnabled) return
        try {
            val gen = getOrCreateToneGenerator()
            gen?.startTone(ToneGenerator.TONE_PROP_NACK, 220)
        } catch (e: Exception) {
            Log.w(TAG, "Error playing alert warning: ${e.message}")
        }
    }

    /**
     * Safe cleanup on application termination or backgrounding.
     */
    @Synchronized
    fun release() {
        try {
            sharedToneGen?.release()
            sharedToneGen = null
        } catch (e: Exception) {
            Log.w(TAG, "Error releasing ToneGenerator: ${e.message}")
        }
    }
}
