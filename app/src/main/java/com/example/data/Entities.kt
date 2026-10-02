package com.example.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "students",
    indices = [
        Index(value = ["regNo"]),
        Index(value = ["studentName"]),
        Index(value = ["className"]),
        Index(value = ["status"]),
        Index(value = ["status", "className"]),
        Index(value = ["isDeleted"])
    ]
)
data class Student(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val regNo: String = "",
    val studentName: String,
    val fatherName: String,
    val className: String,
    val contactNumber: String,
    val admissionFee: Double,
    val monthlyFee: Double,
    val annualCharges: Double,
    val discountType: String, // "None", "Sibling Discount", "Scholarship", etc.
    val otherCharges: Double,
    val admissionDate: Long = System.currentTimeMillis(),
    val idCardNumber: String = "",
    val previousSchoolName: String = "",
    val fatherIdCardNumber: String = "",
    val schoolCertAttached: Boolean = false,
    val formBAttached: Boolean = false,
    val fatherCnicAttached: Boolean = false,
    val picsAttached: Boolean = false,
    val status: String = "Active", // "Active", "Left", "Alumni", "Struck Off"
    val photoUri: String = "", // Optional local photo URI for student avatar
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val createdBy: String = "Staff",
    val isDeleted: Boolean = false,
    val syncStatus: String = "SYNCED"
)

@Entity(
    tableName = "transactions",
    indices = [
        Index(value = ["studentId"]),
        Index(value = ["date"]),
        Index(value = ["paymentMode"]),
        Index(value = ["isIncome"]),
        Index(value = ["category"]),
        Index(value = ["isDeleted"]),
        Index(value = ["monthOfFee", "studentId"])
    ]
)
data class Transaction(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val isIncome: Boolean, // true = Income, false = Expense
    val studentId: Int? = null, // null for expenses
    val studentName: String? = null, // Cached student name for simple display
    val amount: Double,
    val category: String, // "Fee Received", "Admission Fee", "Annual Charges", "Other Charges", "Building Rent", etc.
    val paymentMode: String, // "Cash", "Bank"
    val date: Long = System.currentTimeMillis(),
    val monthOfFee: String? = null, // e.g., "June 2026"
    val recordedBy: String = "Accountant", // "Admin" or "Accountant"
    val description: String = "",
    val voucherNo: String = "", // e.g. "BLS-REC-2026-0001" or "BLS-EXP-2026-0001"
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val isDeleted: Boolean = false,
    val syncStatus: String = "SYNCED"
)

@Entity(
    tableName = "daily_closings",
    indices = [
        Index(value = ["dateString"], unique = true)
    ]
)
data class DailyClosing(
    @PrimaryKey val dateString: String, // Format: "yyyy-MM-dd"
    val openingCash: Double,
    val openingBank: Double,
    val totalIncomeCash: Double,
    val totalIncomeBank: Double,
    val totalExpenseCash: Double,
    val totalExpenseBank: Double,
    val closingCash: Double,
    val closingBank: Double,
    val isClosed: Boolean = false,
    val closedBy: String = "",
    val closedAt: Long = System.currentTimeMillis()
)

