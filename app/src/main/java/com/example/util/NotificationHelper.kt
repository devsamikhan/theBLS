package com.example.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R

object NotificationHelper {

    const val CHANNEL_ID = "bls_realtime_alerts"
    private const val CHANNEL_NAME = "BLS Real-Time Finance Alerts"
    private const val CHANNEL_DESC = "Instant notifications for fee collections, expenses, and admissions"

    fun init(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, importance).apply {
                description = CHANNEL_DESC
                enableVibration(true)
                enableLights(true)
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            manager?.createNotificationChannel(channel)
        }
    }

    fun showTransactionAlert(
        context: Context,
        title: String,
        message: String,
        isIncome: Boolean
    ) {
        init(context)
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            System.currentTimeMillis().toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        val smallIcon = R.mipmap.ic_launcher

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(smallIcon)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        val notificationId = (System.currentTimeMillis() % 100000).toInt()
        manager?.notify(notificationId, notification)
    }

    fun showStudentAdmissionAlert(
        context: Context,
        studentName: String,
        className: String,
        monthlyFee: Double,
        author: String
    ) {
        val title = "New Admission Alert"
        val message = "$author enrolled $studentName (Class: $className, Monthly Fee: Rs. ${monthlyFee.toInt()})"
        showTransactionAlert(context, title, message, isIncome = true)
    }

    fun showFeeCollectionAlert(
        context: Context,
        studentName: String,
        amount: Double,
        voucherNo: String,
        author: String
    ) {
        val title = "Fee Collection Alert"
        val message = "$author collected fee: Rs. ${amount.toInt()} - $studentName (Voucher: $voucherNo)"
        showTransactionAlert(context, title, message, isIncome = true)
    }

    fun showExpenseAlert(
        context: Context,
        category: String,
        amount: Double,
        voucherNo: String,
        author: String
    ) {
        val title = "Expense Logged Alert"
        val message = "$author logged expense: Rs. ${amount.toInt()} - $category (Voucher: $voucherNo)"
        showTransactionAlert(context, title, message, isIncome = false)
    }
}
