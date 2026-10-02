package com.example.data

object DomainConstants {
    const val ROLE_ADMIN = "Admin"
    const val ROLE_ACCOUNTANT = "Accountant"
    const val ROLE_NONE = "None"

    const val PIN_ADMIN = "8888"
    const val PIN_ACCOUNTANT = "1111"

    const val MODE_CASH = "Cash"
    const val MODE_BANK = "Bank"

    const val CAT_FEE_RECEIVED = "Fee Received"
    const val CAT_ADMISSION_FEE = "Admission Fee"
    const val CAT_ANNUAL_CHARGES = "Annual Charges"
    const val CAT_OTHER_CHARGES = "Other Charges"
    const val CAT_EXAM_FEE = "Exam Fee"

    const val CAT_BUILDING_RENT = "Building Rent"
    const val CAT_TEACHERS_PAY = "Teachers Pay"
    const val CAT_STAFF_SALARY = "Staff Salary"
    const val CAT_UTILITY_BILLS = "Utility Bills"
    const val CAT_ENTERTAINMENT = "Entertainment Expenses"
    const val CAT_STATIONERY_EXAM = "Stationery & Exam Papers"
    const val CAT_REPAIR_MAINTENANCE = "Repair & Maintenance"
    const val CAT_INTERNET_COMM = "Internet & Communication"
    const val CAT_BANK_DEPOSIT = "Bank Deposit"
    const val CAT_CASH_DEPOSIT = "Cash Deposit"
    const val CAT_BANK_WITHDRAWAL = "Bank Withdrawal"
    const val CAT_CASH_FROM_BANK = "Cash From Bank"
    const val CAT_OTHER_EXPENSE = "Other"

    fun isContraTransfer(category: String?): Boolean {
        if (category == null) return false
        return category == CAT_BANK_DEPOSIT ||
               category == CAT_CASH_DEPOSIT ||
               category == CAT_BANK_WITHDRAWAL ||
               category == CAT_CASH_FROM_BANK
    }

    const val STATUS_ACTIVE = "Active"
    const val STATUS_LEFT = "Left"
    const val STATUS_ALUMNI = "Alumni"
    const val STATUS_STRUCK_OFF = "Struck Off"

    val STUDENT_STATUSES = listOf(STATUS_ACTIVE, STATUS_LEFT, STATUS_ALUMNI, STATUS_STRUCK_OFF)

    val EXPENSE_CATEGORIES = listOf(
        CAT_STAFF_SALARY,
        CAT_TEACHERS_PAY,
        CAT_BUILDING_RENT,
        CAT_UTILITY_BILLS,
        CAT_ENTERTAINMENT,
        CAT_STATIONERY_EXAM,
        CAT_REPAIR_MAINTENANCE,
        CAT_INTERNET_COMM,
        CAT_OTHER_EXPENSE
    )

    val DISCOUNT_TYPES = listOf(
        "None",
        "Sibling 20%",
        "Sibling 40%",
        "Orphan 50%",
        "Teacher son 50%",
        "Poor Free",
        "Owner Discount 100%",
        "Special Discount 10%"
    )

    val CLASS_LEVELS = listOf(
        "PG", "Nursery", "Prep", "One", "Two", "Three", "Four", "Five", "Six", "Seven", "Eight"
    )

    const val CURRENT_SESSION = "2026-2027"
    val ACADEMIC_SESSIONS = listOf("2024-2025", "2025-2026", "2026-2027", "2027-2028")

    const val SYNC_STATUS_SYNCED = "SYNCED"
    const val SYNC_STATUS_PENDING = "PENDING"
    const val SYNC_STATUS_FAILED = "FAILED"

    /**
     * Pure business logic calculation for applying discount to standard monthly fee.
     */
    fun calculateDiscountedFee(baseMonthlyFee: Double, discountType: String?): Double {
        return when (discountType?.trim()) {
            "Sibling 20%" -> baseMonthlyFee * 0.80
            "Sibling 40%" -> baseMonthlyFee * 0.60
            "Orphan 50%" -> baseMonthlyFee * 0.50
            "Teacher son 50%" -> baseMonthlyFee * 0.50
            "Poor Free" -> 0.0
            "Owner Discount 100%" -> 0.0
            "Special Discount 10%" -> baseMonthlyFee * 0.90
            else -> baseMonthlyFee
        }
    }

    /**
     * Converts a numeric Rupee amount to English words (e.g. 5200 -> "Five Thousand Two Hundred Rupees Only")
     * Perfect for printing on official fee challans, receipts, and clearance certificates.
     */
    fun convertNumberToWords(amount: Double): String = numberToWordsRupees(amount)

    fun numberToWordsRupees(amount: Double): String {
        val n = amount.toLong()
        if (n <= 0) return "Zero Rupees Only"

        val units = arrayOf(
            "", "One", "Two", "Three", "Four", "Five", "Six", "Seven", "Eight", "Nine",
            "Ten", "Eleven", "Twelve", "Thirteen", "Fourteen", "Fifteen", "Sixteen", "Seventeen", "Eighteen", "Nineteen"
        )
        val tens = arrayOf("", "", "Twenty", "Thirty", "Forty", "Fifty", "Sixty", "Seventy", "Eighty", "Ninety")

        fun convertLessThanThousand(num: Int): String {
            var current = num
            var result = ""
            if (current >= 100) {
                result += "${units[current / 100]} Hundred "
                current %= 100
            }
            if (current in 1..19) {
                result += "${units[current]} "
            } else if (current >= 20) {
                result += "${tens[current / 10]} "
                if (current % 10 > 0) {
                    result += "${units[current % 10]} "
                }
            }
            return result.trim()
        }

        var num = n
        var words = ""

        if (num >= 10000000) { // Crore
            val crore = (num / 10000000).toInt()
            words += "${convertLessThanThousand(crore)} Crore "
            num %= 10000000
        }
        if (num >= 100000) { // Lac
            val lac = (num / 100000).toInt()
            words += "${convertLessThanThousand(lac)} Lac "
            num %= 100000
        }
        if (num >= 1000) { // Thousand
            val thousand = (num / 1000).toInt()
            words += "${convertLessThanThousand(thousand)} Thousand "
            num %= 1000
        }
        if (num > 0) {
            words += convertLessThanThousand(num.toInt())
        }

        return "${words.trim()} Rupees Only"
    }

    /**
     * Calculates previous unpaid months' arrears (up to 6 months) for a given student.
     */
    fun calculateMultiMonthArrears(
        admissionDate: Long,
        discountedMonthlyFee: Double,
        studentTransactions: List<Transaction>
    ): Double {
        if (discountedMonthlyFee <= 0.0) return 0.0
        val sdf = java.text.SimpleDateFormat("MMMM yyyy", java.util.Locale.US)
        val now = java.util.Date()

        val admCal = java.util.Calendar.getInstance().apply {
            timeInMillis = admissionDate
            set(java.util.Calendar.DAY_OF_MONTH, 1)
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }

        val tempCal = java.util.Calendar.getInstance().apply {
            time = now
            set(java.util.Calendar.DAY_OF_MONTH, 1)
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
            add(java.util.Calendar.MONTH, -1) // Past months only
        }

        var arrears = 0.0
        for (i in 0 until 6) {
            if (tempCal.before(admCal)) break
            val mStr = sdf.format(tempCal.time)
            val paidForMonth = studentTransactions.filter { tx ->
                tx.isIncome &&
                tx.category == CAT_FEE_RECEIVED &&
                tx.monthOfFee.equals(mStr, ignoreCase = true)
            }.sumOf { it.amount }

            val dueForMonth = maxOf(0.0, discountedMonthlyFee - paidForMonth)
            arrears += dueForMonth
            tempCal.add(java.util.Calendar.MONTH, -1)
        }
        return arrears
    }

    /**
     * Calculates excess advance credit balance in student wallet.
     * When total payments made exceed total obligations (admission + annual + other + tuition across enrolled months),
     * the surplus is retained as advance fee credit.
     */
    fun calculateStudentAdvanceWallet(
        student: Student,
        studentTransactions: List<Transaction>
    ): Double {
        val discountedMonthly = calculateDiscountedFee(student.monthlyFee, student.discountType)
        val admCal = java.util.Calendar.getInstance().apply {
            timeInMillis = student.admissionDate
            set(java.util.Calendar.DAY_OF_MONTH, 1)
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        val nowCal = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.DAY_OF_MONTH, 1)
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }

        var monthsCount = 0
        val tempCal = admCal.clone() as java.util.Calendar
        while (!tempCal.after(nowCal)) {
            monthsCount++
            tempCal.add(java.util.Calendar.MONTH, 1)
        }
        if (monthsCount < 1) monthsCount = 1

        val totalDemanded = student.admissionFee + student.annualCharges + student.otherCharges + (discountedMonthly * monthsCount)
        val totalPaid = studentTransactions.filter { tx ->
            tx.isIncome && (
                tx.category == CAT_FEE_RECEIVED ||
                tx.category == CAT_ADMISSION_FEE ||
                tx.category == CAT_ANNUAL_CHARGES ||
                tx.category == CAT_OTHER_CHARGES
            )
        }.sumOf { it.amount }

        return maxOf(0.0, totalPaid - totalDemanded)
    }
}

