package com.example.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AppRepository(private val appDao: AppDao) {

    val syncManager = FirebaseRealtimeSyncManager(appDao)

    fun setContext(context: android.content.Context) {
        syncManager.setContext(context)
    }

    fun setCurrentRole(role: String) {
        syncManager.setCurrentRole(role)
    }

    fun configureSync(url: String, secret: String = "") {
        syncManager.configure(url, secret)
    }

    suspend fun testSyncConnection(url: String, secret: String = ""): Pair<Boolean, String> {
        return syncManager.testConnection(url, secret)
    }

    fun startSync(scope: CoroutineScope) {
        syncManager.startRealtimeSync(scope)
    }

    // Students
    val allStudentsFlow: Flow<List<Student>> = appDao.getAllStudentsFlow()
    
    suspend fun getAllStudents(): List<Student> = appDao.getAllStudents()
    
    suspend fun getStudentById(id: Int): Student? = appDao.getStudentById(id)
    
    suspend fun insertStudent(student: Student): Long {
        val now = System.currentTimeMillis()
        val studentWithAudit = student.copy(
            createdAt = if (student.createdAt == 0L) now else student.createdAt,
            updatedAt = now,
            isDeleted = false,
            syncStatus = DomainConstants.SYNC_STATUS_SYNCED
        )
        val rowId = appDao.insertStudent(studentWithAudit)
        val targetStudent = if (studentWithAudit.id == 0) studentWithAudit.copy(id = rowId.toInt()) else studentWithAudit
        syncManager.pushStudent(targetStudent)
        return rowId
    }
    
    suspend fun updateStudent(student: Student) {
        val studentWithAudit = student.copy(updatedAt = System.currentTimeMillis())
        appDao.updateStudent(studentWithAudit)
        syncManager.pushStudent(studentWithAudit)
    }
    
    suspend fun deleteStudent(student: Student) {
        appDao.softDeleteStudent(student.id)
        syncManager.deleteStudentFromCloud(student.id)
    }

    // Transactions
    val allTransactionsFlow: Flow<List<Transaction>> = appDao.getAllTransactionsFlow()
    
    suspend fun getAllTransactions(): List<Transaction> = appDao.getAllTransactions()
    
    fun getTransactionsByStudent(studentId: Int): Flow<List<Transaction>> = 
        appDao.getTransactionsByStudentFlow(studentId)
        
    fun getTransactionsByPaymentMode(mode: String): Flow<List<Transaction>> = 
        appDao.getTransactionsByPaymentModeFlow(mode)

    suspend fun generateNextVoucherNo(isIncome: Boolean, role: String = ""): String {
        return syncManager.allocateAtomicVoucherNo(isIncome, role)
    }

    suspend fun insertTransaction(transaction: Transaction): Long {
        val now = System.currentTimeMillis()
        val txWithVoucher = if (transaction.voucherNo.isBlank()) {
            transaction.copy(voucherNo = generateNextVoucherNo(transaction.isIncome))
        } else {
            transaction
        }
        val txWithAudit = txWithVoucher.copy(
            createdAt = if (txWithVoucher.createdAt == 0L) now else txWithVoucher.createdAt,
            updatedAt = now,
            isDeleted = false,
            syncStatus = DomainConstants.SYNC_STATUS_SYNCED
        )
        val rowId = appDao.insertTransaction(txWithAudit)
        val targetTx = if (txWithAudit.id == 0) txWithAudit.copy(id = rowId.toInt()) else txWithAudit
        syncManager.pushTransaction(targetTx)
        return rowId
    }
    
    suspend fun deleteTransaction(transaction: Transaction) {
        appDao.softDeleteTransaction(transaction.id)
        syncManager.deleteTransactionFromCloud(transaction.id)
    }

    suspend fun getClassSummaryStats(): List<ClassSummaryStats> = appDao.getClassSummaryStats()

    suspend fun updateStudentStatus(studentId: Int, status: String) {
        appDao.updateStudentStatus(studentId, status)
        val student = appDao.getStudentById(studentId)
        if (student != null) {
            syncManager.pushStudent(student)
        }
    }

    suspend fun updateStudentPhoto(studentId: Int, photoUri: String) {
        appDao.updateStudentPhoto(studentId, photoUri)
        val student = appDao.getStudentById(studentId)
        if (student != null) {
            syncManager.pushStudent(student)
        }
    }

    suspend fun bulkPromoteClass(currentClass: String, nextClass: String): Int {
        val studentsInClass = appDao.getStudentsByClass(currentClass)
        val updated = studentsInClass.map { it.copy(className = nextClass) }
        appDao.updateStudents(updated)
        updated.forEach { syncManager.pushStudent(it) }
        return updated.size
    }

    suspend fun restoreDatabase(students: List<Student>, transactions: List<Transaction>, closings: List<DailyClosing>) {
        appDao.clearAllStudents()
        appDao.clearAllTransactions()
        appDao.clearAllDailyClosings()
        if (students.isNotEmpty()) appDao.insertStudents(students)
        if (transactions.isNotEmpty()) appDao.insertTransactions(transactions)
        if (closings.isNotEmpty()) appDao.insertDailyClosings(closings)
        students.forEach { syncManager.pushStudent(it) }
        transactions.forEach { syncManager.pushTransaction(it) }
        closings.forEach { syncManager.pushDailyClosing(it) }
    }

    // Daily Closings
    val allDailyClosingsFlow: Flow<List<DailyClosing>> = appDao.getAllDailyClosingsFlow()
    
    suspend fun getDailyClosing(dateStr: String): DailyClosing? = appDao.getDailyClosingByDate(dateStr)
    
    suspend fun saveDailyClosing(closing: DailyClosing) {
        appDao.insertDailyClosing(closing)
        syncManager.pushDailyClosing(closing)
    }

    /**
     * Helper: Compute closing balances automatically using fast SQL aggregation queries.
     * Prevents O(N) memory allocation and lag when transaction records scale.
     */
    suspend fun calculateDailyClosingState(dateStr: String): DailyClosing {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val targetDay: Date
        try {
            targetDay = sdf.parse(dateStr) ?: Date()
        } catch (e: Exception) {
            return DailyClosing(dateStr, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0)
        }
        
        val startOfDay = targetDay.time // 00:00
        val endOfDay = startOfDay + 24 * 60 * 60 * 1000 - 1 // 23:59:59.999
        
        // Fast SQL Aggregations for historical balances before start of today
        val prevIncomeCash = appDao.getHistoricalSumBefore(DomainConstants.MODE_CASH, isIncome = true, beforeDate = startOfDay)
        val prevExpenseCash = appDao.getHistoricalSumBefore(DomainConstants.MODE_CASH, isIncome = false, beforeDate = startOfDay)
        val openCash = prevIncomeCash - prevExpenseCash

        val prevIncomeBank = appDao.getHistoricalSumBefore(DomainConstants.MODE_BANK, isIncome = true, beforeDate = startOfDay)
        val prevExpenseBank = appDao.getHistoricalSumBefore(DomainConstants.MODE_BANK, isIncome = false, beforeDate = startOfDay)
        val openBank = prevIncomeBank - prevExpenseBank

        // Fast SQL Aggregations for current day transactions
        val totalIncomeCash = appDao.getDaySum(DomainConstants.MODE_CASH, isIncome = true, startOfDay = startOfDay, endOfDay = endOfDay)
        val totalIncomeBank = appDao.getDaySum(DomainConstants.MODE_BANK, isIncome = true, startOfDay = startOfDay, endOfDay = endOfDay)
        val totalExpenseCash = appDao.getDaySum(DomainConstants.MODE_CASH, isIncome = false, startOfDay = startOfDay, endOfDay = endOfDay)
        val totalExpenseBank = appDao.getDaySum(DomainConstants.MODE_BANK, isIncome = false, startOfDay = startOfDay, endOfDay = endOfDay)
        
        val closingCash = openCash + totalIncomeCash - totalExpenseCash
        val closingBank = openBank + totalIncomeBank - totalExpenseBank

        return DailyClosing(
            dateString = dateStr,
            openingCash = openCash,
            openingBank = openBank,
            totalIncomeCash = totalIncomeCash,
            totalIncomeBank = totalIncomeBank,
            totalExpenseCash = totalExpenseCash,
            totalExpenseBank = totalExpenseBank,
            closingCash = closingCash,
            closingBank = closingBank,
            isClosed = false,
            closedBy = ""
        )
    }

    // App Users Management
    val allUsersFlow: Flow<List<AppUser>> = appDao.getAllUsersFlow()

    suspend fun getAllUsers(): List<AppUser> = appDao.getAllUsers()

    suspend fun getUserByEmail(email: String): AppUser? = appDao.getUserByEmail(email)

    suspend fun insertUser(user: AppUser): Long {
        val rowId = appDao.insertUser(user)
        val target = if (user.id == 0) user.copy(id = rowId.toInt()) else user
        syncManager.pushUser(target)
        return rowId
    }

    suspend fun updateUser(user: AppUser) {
        val updated = user.copy(updatedAt = System.currentTimeMillis())
        appDao.updateUser(updated)
        syncManager.pushUser(updated)
    }

    suspend fun updateUserStatus(id: Int, isActive: Boolean) {
        appDao.updateUserStatus(id, isActive)
        appDao.getAllUsers().find { it.id == id }?.let { syncManager.pushUser(it) }
    }

    suspend fun deleteUser(user: AppUser) {
        appDao.deleteUser(user)
        syncManager.deleteUserFromCloud(user.id)
    }

    suspend fun wipeAllDummyData(): Boolean {
        appDao.clearAllStudents()
        appDao.clearAllTransactions()
        appDao.clearAllDailyClosings()
        appDao.clearAllTeachers()
        return syncManager.wipeCloudData()
    }

    // Teachers Management
    val allTeachersFlow: Flow<List<Teacher>> = appDao.getAllTeachersFlow()

    suspend fun getAllTeachers(): List<Teacher> = appDao.getAllTeachers()

    suspend fun getTeacherById(id: Int): Teacher? = appDao.getTeacherById(id)

    suspend fun insertTeacher(teacher: Teacher): Long {
        val now = System.currentTimeMillis()
        val teacherWithAudit = teacher.copy(
            createdAt = if (teacher.createdAt == 0L) now else teacher.createdAt,
            updatedAt = now,
            isDeleted = false,
            syncStatus = DomainConstants.SYNC_STATUS_SYNCED
        )
        val rowId = appDao.insertTeacher(teacherWithAudit)
        val target = if (teacherWithAudit.id == 0) teacherWithAudit.copy(id = rowId.toInt()) else teacherWithAudit
        syncManager.pushTeacher(target)
        return rowId
    }

    suspend fun updateTeacher(teacher: Teacher) {
        val updated = teacher.copy(updatedAt = System.currentTimeMillis())
        appDao.updateTeacher(updated)
        syncManager.pushTeacher(updated)
    }

    suspend fun deleteTeacher(teacher: Teacher) {
        appDao.softDeleteTeacher(teacher.id)
        syncManager.deleteTeacherFromCloud(teacher.id)
    }

    suspend fun updateTeacherStatus(teacherId: Int, status: String) {
        appDao.updateTeacherStatus(teacherId, status)
        appDao.getTeacherById(teacherId)?.let { syncManager.pushTeacher(it) }
    }

    fun getTransactionsByTeacher(teacherId: Int): Flow<List<Transaction>> =
        appDao.getTransactionsByTeacherFlow(teacherId)

    suspend fun getTeacherSalaryTransactions(teacherId: Int): List<Transaction> =
        appDao.getTransactionsByTeacher(teacherId)
}
