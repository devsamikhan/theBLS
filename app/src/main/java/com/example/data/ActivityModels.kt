package com.example.data

enum class ActivityType {
    ADMISSION,
    FEE_COLLECTION,
    EXPENSE,
    BANK_TRANSFER
}

data class ActivityFeedItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val amount: Double = 0.0,
    val authorRole: String, // "ADMIN", "ACCOUNTANT", or "Staff"
    val type: ActivityType,
    val timestamp: Long,
    val voucherNo: String = "",
    val details: String = ""
)
