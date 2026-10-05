package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

data class ClassSummaryStats(
    val className: String,
    val studentCount: Int,
    val totalExpectedFee: Double
)

@Dao
interface AppDao {
    
    // Students Queries
    @Query("SELECT * FROM students WHERE isDeleted = 0 ORDER BY id DESC")
    fun getAllStudentsFlow(): Flow<List<Student>>

    @Query("SELECT * FROM students WHERE isDeleted = 0 ORDER BY id DESC")
    suspend fun getAllStudents(): List<Student>

    @Query("SELECT * FROM students WHERE id = :studentId AND isDeleted = 0")
    suspend fun getStudentById(studentId: Int): Student?

    @Query("SELECT MAX(id) FROM students WHERE isDeleted = 0")
    suspend fun getMaxStudentId(): Int?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStudent(student: Student): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStudents(students: List<Student>)

    @Update
    suspend fun updateStudent(student: Student)

    @Delete
    suspend fun deleteStudent(student: Student)

    @Query("DELETE FROM students WHERE id = :id")
    suspend fun deleteStudentById(id: Int)

    // Soft Delete Support
    @Query("UPDATE students SET isDeleted = 1, updatedAt = :timestamp WHERE id = :id")
    suspend fun softDeleteStudent(id: Int, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE transactions SET isDeleted = 1, updatedAt = :timestamp WHERE id = :id")
    suspend fun softDeleteTransaction(id: Int, timestamp: Long = System.currentTimeMillis())

    // Transactions Queries
    @Query("SELECT * FROM transactions WHERE isDeleted = 0 ORDER BY date DESC")
    fun getAllTransactionsFlow(): Flow<List<Transaction>>

    @Query("SELECT * FROM transactions WHERE isDeleted = 0 ORDER BY date DESC")
    suspend fun getAllTransactions(): List<Transaction>

    @Query("SELECT * FROM transactions WHERE studentId = :studentId AND isDeleted = 0 ORDER BY date DESC")
    fun getTransactionsByStudentFlow(studentId: Int): Flow<List<Transaction>>

    @Query("SELECT * FROM transactions WHERE paymentMode = :mode AND isDeleted = 0 ORDER BY date DESC")
    fun getTransactionsByPaymentModeFlow(mode: String): Flow<List<Transaction>>

    @Query("SELECT * FROM transactions WHERE date >= :startOfDay AND date <= :endOfDay AND isDeleted = 0 ORDER BY date DESC")
    suspend fun getTransactionsByDateRange(startOfDay: Long, endOfDay: Long): List<Transaction>

    @Query("SELECT COALESCE(SUM(amount), 0.0) FROM transactions WHERE paymentMode = :mode AND isIncome = :isIncome AND isDeleted = 0 AND date < :beforeDate")
    suspend fun getHistoricalSumBefore(mode: String, isIncome: Boolean, beforeDate: Long): Double

    @Query("SELECT COALESCE(SUM(amount), 0.0) FROM transactions WHERE paymentMode = :mode AND isIncome = :isIncome AND isDeleted = 0 AND date >= :startOfDay AND date <= :endOfDay")
    suspend fun getDaySum(mode: String, isIncome: Boolean, startOfDay: Long, endOfDay: Long): Double

    @Query("SELECT * FROM transactions WHERE isIncome = 1 AND category = 'Fee Received' AND isDeleted = 0 AND monthOfFee = :month")
    suspend fun getPaidFeeTransactionsForMonth(month: String): List<Transaction>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaction(transaction: Transaction): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransactions(transactions: List<Transaction>)

    @Delete
    suspend fun deleteTransaction(transaction: Transaction)

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun deleteTransactionById(id: Int)

    // Daily Closings Queries
    @Query("SELECT * FROM daily_closings ORDER BY dateString DESC")
    fun getAllDailyClosingsFlow(): Flow<List<DailyClosing>>

    @Query("SELECT * FROM daily_closings WHERE dateString = :dateLimit")
    suspend fun getDailyClosingByDate(dateLimit: String): DailyClosing?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDailyClosing(closing: DailyClosing)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDailyClosings(closings: List<DailyClosing>)

    @Update
    suspend fun updateStudents(students: List<Student>)

    @Query("UPDATE students SET status = :newStatus, updatedAt = :timestamp WHERE id = :id")
    suspend fun updateStudentStatus(id: Int, newStatus: String, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE students SET photoUri = :photoUri, updatedAt = :timestamp WHERE id = :id")
    suspend fun updateStudentPhoto(id: Int, photoUri: String, timestamp: Long = System.currentTimeMillis())

    @Query("SELECT * FROM students WHERE className = :className AND isDeleted = 0")
    suspend fun getStudentsByClass(className: String): List<Student>

    @Query("SELECT COUNT(*) FROM transactions WHERE isIncome = :isIncome AND isDeleted = 0")
    suspend fun getTransactionCountByType(isIncome: Boolean): Int

    // High performance SQL Aggregations
    @Query("SELECT className, COUNT(*) as studentCount, COALESCE(SUM(monthlyFee), 0.0) as totalExpectedFee FROM students WHERE status = 'Active' AND isDeleted = 0 GROUP BY className")
    suspend fun getClassSummaryStats(): List<ClassSummaryStats>

    // Pending Cloud Sync helpers
    @Query("SELECT * FROM students WHERE syncStatus = 'PENDING'")
    suspend fun getPendingSyncStudents(): List<Student>

    @Query("SELECT * FROM transactions WHERE syncStatus = 'PENDING'")
    suspend fun getPendingSyncTransactions(): List<Transaction>

    @Query("UPDATE students SET syncStatus = 'SYNCED' WHERE id = :id")
    suspend fun markStudentSynced(id: Int)

    @Query("UPDATE transactions SET syncStatus = 'SYNCED' WHERE id = :id")
    suspend fun markTransactionSynced(id: Int)

    @Query("DELETE FROM students")
    suspend fun clearAllStudents()

    @Query("DELETE FROM transactions")
    suspend fun clearAllTransactions()

    @Query("DELETE FROM daily_closings")
    suspend fun clearAllDailyClosings()

    // App Users Management Queries
    @Query("SELECT * FROM app_users ORDER BY id DESC")
    fun getAllUsersFlow(): Flow<List<AppUser>>

    @Query("SELECT * FROM app_users ORDER BY id DESC")
    suspend fun getAllUsers(): List<AppUser>

    @Query("SELECT * FROM app_users WHERE LOWER(email) = LOWER(:email) LIMIT 1")
    suspend fun getUserByEmail(email: String): AppUser?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: AppUser): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUsers(users: List<AppUser>)

    @Update
    suspend fun updateUser(user: AppUser)

    @Delete
    suspend fun deleteUser(user: AppUser)

    @Query("UPDATE app_users SET isActive = :isActive, updatedAt = :timestamp WHERE id = :id")
    suspend fun updateUserStatus(id: Int, isActive: Boolean, timestamp: Long = System.currentTimeMillis())

    @Query("DELETE FROM app_users")
    suspend fun clearAllUsers()
}

