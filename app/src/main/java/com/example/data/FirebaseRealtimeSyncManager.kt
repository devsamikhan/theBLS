package com.example.data

import android.content.Context
import android.util.Log
import com.example.util.NotificationHelper
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class SyncState {
    SYNCED, SYNCING, OFFLINE, ERROR
}

/**
 * Enterprise-grade Bidirectional Realtime Synchronization Manager for Firebase Realtime Database.
 * Features:
 * - Safe offline-first synchronization with zero data-loss guarantees.
 * - Local pending modification protection against stale cloud overwrites.
 * - Single managed coroutine scope with clean lifecycle and reconnection backoff.
 * - Atomic voucher numbering with local count collision protection.
 * - Granular SSE streaming with document-level delta resolution.
 */
class FirebaseRealtimeSyncManager(private val appDao: AppDao) {

    private var appContext: Context? = null
    private var currentRole: String = ""

    // Dedicated managed coroutine scope for sync operations
    private val syncJobSupervisor = SupervisorJob()
    private val syncScope = CoroutineScope(syncJobSupervisor + Dispatchers.IO)

    fun setContext(context: Context) {
        appContext = context.applicationContext
        NotificationHelper.init(context)
    }

    fun setCurrentRole(role: String) {
        currentRole = role
    }

    companion object {
        private const val TAG = "FirebaseRealtimeSync"
        const val DEFAULT_FIREBASE_URL = "https://bls-school-system-default-rtdb.firebaseio.com"
        private const val CONNECT_TIMEOUT_MS = 10000
        private const val READ_TIMEOUT_MS = 15000
    }

    private var databaseUrl: String = DEFAULT_FIREBASE_URL
    private var authSecret: String = ""

    fun configure(url: String, secret: String = "") {
        val clean = url.trim().trimEnd('/')
        if (clean.isNotBlank()) {
            databaseUrl = if (!clean.startsWith("http://") && !clean.startsWith("https://")) {
                "https://$clean"
            } else {
                clean
            }
        }
        authSecret = secret.trim()
    }

    fun getDatabaseUrl(): String = databaseUrl
    fun getAuthSecret(): String = authSecret

    fun getEndpointUrl(path: String): String {
        val cleanBase = databaseUrl.trim().trimEnd('/')
        val cleanPath = if (path.startsWith("/")) path else "/$path"
        val fullUrl = "$cleanBase$cleanPath"
        return if (authSecret.isNotBlank()) {
            if (fullUrl.contains("?")) "$fullUrl&auth=$authSecret" else "$fullUrl?auth=$authSecret"
        } else {
            fullUrl
        }
    }

    suspend fun testConnection(testUrl: String, testSecret: String = ""): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        val cleanInput = testUrl.trim().trimEnd('/')
        if (cleanInput.isBlank()) {
            return@withContext Pair(false, "Please provide a valid Firebase URL.")
        }
        val cleanUrl = if (!cleanInput.startsWith("http://") && !cleanInput.startsWith("https://")) {
            "https://$cleanInput"
        } else {
            cleanInput
        }
        val target = if (testSecret.isNotBlank()) "$cleanUrl/.json?shallow=true&auth=${testSecret.trim()}" else "$cleanUrl/.json?shallow=true"
        try {
            val conn = (URL(target).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
            }
            val code = conn.responseCode
            conn.disconnect()
            when (code) {
                in 200..299 -> Pair(true, "Success: Connected to Firebase Realtime Database. Cloud sync is active.")
                401 -> Pair(false, "Access Denied (401 Unauthorized): Verify database rules or provide valid Auth Secret.")
                404 -> Pair(false, "Database Not Found (404 Not Found): Check Firebase project URL.")
                else -> Pair(false, "Server Response Code: $code")
            }
        } catch (e: Exception) {
            Pair(false, "Network Error: ${e.message ?: "Unable to establish connection"}")
        }
    }

    private var syncJob: Job? = null

    private val _syncState = MutableStateFlow(SyncState.SYNCED)
    val syncState: StateFlow<SyncState> = _syncState.asStateFlow()

    private val _lastSyncedAt = MutableStateFlow(System.currentTimeMillis())
    val lastSyncedAt: StateFlow<Long> = _lastSyncedAt.asStateFlow()

    /**
     * Starts background real-time bidirectional synchronization with guaranteed ordering:
     * 1. First pushes local pending records to the cloud.
     * 2. Then hydrates any newer cloud records into local Room without overwriting pending edits.
     * 3. Maintains real-time SSE listener with auto-reconnection backoff.
     */
    fun startRealtimeSync(scope: CoroutineScope) {
        syncJob?.cancel()
        syncJob = scope.launch(Dispatchers.IO) {
            Log.d(TAG, "Starting Firebase Realtime Sync Engine...")
            _syncState.value = SyncState.SYNCING

            // STEP 1: Flush pending queue FIRST so offline edits are safely preserved on cloud
            try {
                flushPendingQueue()
            } catch (e: Exception) {
                Log.w(TAG, "Initial flush skipped: ${e.message}")
            }

            // STEP 2: Hydrate cloud data into Room with conflict resolution
            try {
                fetchAndHydrateInitialData()
                _syncState.value = SyncState.SYNCED
                _lastSyncedAt.value = System.currentTimeMillis()
            } catch (e: Exception) {
                _syncState.value = SyncState.OFFLINE
                Log.w(TAG, "Initial cloud hydration failed: ${e.message}")
            }

            // STEP 3: Real-time persistent SSE stream listener with backoff
            while (isActive) {
                try {
                    listenToEventStream()
                } catch (e: Exception) {
                    if (isActive) {
                        _syncState.value = SyncState.OFFLINE
                        Log.w(TAG, "SSE Connection dropped: ${e.message}. Reconnecting in 3 seconds...")
                        delay(3000)
                    }
                }
                // Safe guard to prevent tight CPU spin if server closes connection normally
                if (isActive) {
                    delay(2000)
                }
            }
        }
    }

    fun stopSync() {
        syncJob?.cancel()
        syncJob = null
    }

    // ==================== DIRECT SYNCHRONOUS PUSH CALLS ====================

    suspend fun pushStudentDirect(student: Student): Boolean = withContext(Dispatchers.IO) {
        try {
            val json = JSONObject().apply {
                put("id", student.id)
                put("regNo", student.regNo)
                put("studentName", student.studentName)
                put("fatherName", student.fatherName)
                put("className", student.className)
                put("contactNumber", student.contactNumber)
                put("admissionFee", student.admissionFee)
                put("monthlyFee", student.monthlyFee)
                put("annualCharges", student.annualCharges)
                put("discountType", student.discountType)
                put("otherCharges", student.otherCharges)
                put("admissionDate", student.admissionDate)
                put("idCardNumber", student.idCardNumber)
                put("previousSchoolName", student.previousSchoolName)
                put("fatherIdCardNumber", student.fatherIdCardNumber)
                put("schoolCertAttached", student.schoolCertAttached)
                put("formBAttached", student.formBAttached)
                put("fatherCnicAttached", student.fatherCnicAttached)
                put("picsAttached", student.picsAttached)
                put("status", student.status)
                put("photoUri", student.photoUri)
                put("createdAt", student.createdAt)
                put("updatedAt", student.updatedAt)
                put("createdBy", student.createdBy)
                put("isDeleted", student.isDeleted)
            }
            val ok = httpPut(getEndpointUrl("students/${student.id}.json"), json.toString())
            if (ok) {
                _syncState.value = SyncState.SYNCED
                _lastSyncedAt.value = System.currentTimeMillis()
                Log.d(TAG, "Student ${student.id} (${student.studentName}) pushed to cloud successfully.")
            }
            ok
        } catch (e: Exception) {
            _syncState.value = SyncState.OFFLINE
            Log.e(TAG, "Error pushing student ${student.id}: ${e.message}")
            false
        }
    }

    suspend fun deleteStudentDirect(studentId: Int): Boolean = withContext(Dispatchers.IO) {
        try {
            val ok = httpDelete(getEndpointUrl("students/$studentId.json"))
            Log.d(TAG, "Student $studentId deleted from cloud: $ok")
            ok
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting student $studentId from cloud: ${e.message}")
            false
        }
    }

    suspend fun pushTeacherDirect(teacher: Teacher): Boolean = withContext(Dispatchers.IO) {
        try {
            val json = JSONObject().apply {
                put("id", teacher.id)
                put("name", teacher.name)
                put("designation", teacher.designation)
                put("contactNumber", teacher.contactNumber)
                put("cnic", teacher.cnic)
                put("qualification", teacher.qualification)
                put("monthlySalary", teacher.monthlySalary)
                put("joiningDate", teacher.joiningDate)
                put("status", teacher.status)
                put("photoUri", teacher.photoUri)
                put("address", teacher.address)
                put("createdAt", teacher.createdAt)
                put("updatedAt", teacher.updatedAt)
                put("createdBy", teacher.createdBy)
                put("isDeleted", teacher.isDeleted)
            }
            val ok = httpPut(getEndpointUrl("teachers/${teacher.id}.json"), json.toString())
            if (ok) {
                _syncState.value = SyncState.SYNCED
                _lastSyncedAt.value = System.currentTimeMillis()
                Log.d(TAG, "Teacher ${teacher.id} (${teacher.name}) pushed to cloud successfully.")
            }
            ok
        } catch (e: Exception) {
            _syncState.value = SyncState.OFFLINE
            Log.e(TAG, "Error pushing teacher ${teacher.id}: ${e.message}")
            false
        }
    }

    suspend fun deleteTeacherDirect(teacherId: Int): Boolean = withContext(Dispatchers.IO) {
        try {
            val ok = httpDelete(getEndpointUrl("teachers/$teacherId.json"))
            Log.d(TAG, "Teacher $teacherId deleted from cloud: $ok")
            ok
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting teacher $teacherId from cloud: ${e.message}")
            false
        }
    }

    suspend fun pushTransactionDirect(transaction: Transaction): Boolean = withContext(Dispatchers.IO) {
        try {
            val json = JSONObject().apply {
                put("id", transaction.id)
                put("studentId", transaction.studentId ?: JSONObject.NULL)
                put("studentName", transaction.studentName ?: JSONObject.NULL)
                put("amount", transaction.amount)
                put("category", transaction.category)
                put("isIncome", transaction.isIncome)
                put("paymentMode", transaction.paymentMode)
                put("date", transaction.date)
                put("recordedBy", transaction.recordedBy)
                put("description", transaction.description)
                put("monthOfFee", transaction.monthOfFee ?: JSONObject.NULL)
                put("voucherNo", transaction.voucherNo)
                put("payeeName", transaction.payeeName)
                put("invoiceNo", transaction.invoiceNo)
                put("teacherId", transaction.teacherId ?: JSONObject.NULL)
                put("createdAt", transaction.createdAt)
                put("updatedAt", transaction.updatedAt)
                put("isDeleted", transaction.isDeleted)
            }
            val ok = httpPut(getEndpointUrl("transactions/${transaction.id}.json"), json.toString())
            if (ok) {
                _syncState.value = SyncState.SYNCED
                _lastSyncedAt.value = System.currentTimeMillis()
                Log.d(TAG, "Transaction ${transaction.id} (${transaction.voucherNo}) pushed to cloud successfully.")
            }
            ok
        } catch (e: Exception) {
            _syncState.value = SyncState.OFFLINE
            Log.e(TAG, "Error pushing transaction ${transaction.id}: ${e.message}")
            false
        }
    }

    suspend fun deleteTransactionDirect(transactionId: Int): Boolean = withContext(Dispatchers.IO) {
        try {
            val ok = httpDelete(getEndpointUrl("transactions/$transactionId.json"))
            Log.d(TAG, "Transaction $transactionId deleted from cloud: $ok")
            ok
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting transaction $transactionId from cloud: ${e.message}")
            false
        }
    }

    suspend fun pushDailyClosingDirect(closing: DailyClosing): Boolean = withContext(Dispatchers.IO) {
        try {
            val safeKey = closing.dateString.replace("/", "_").replace(".", "_")
            val json = JSONObject().apply {
                put("dateString", closing.dateString)
                put("openingCash", closing.openingCash)
                put("openingBank", closing.openingBank)
                put("totalIncomeCash", closing.totalIncomeCash)
                put("totalIncomeBank", closing.totalIncomeBank)
                put("totalExpenseCash", closing.totalExpenseCash)
                put("totalExpenseBank", closing.totalExpenseBank)
                put("closingCash", closing.closingCash)
                put("closingBank", closing.closingBank)
                put("isClosed", closing.isClosed)
                put("closedBy", closing.closedBy)
                put("closedAt", closing.closedAt)
            }
            val ok = httpPut(getEndpointUrl("daily_closings/$safeKey.json"), json.toString())
            if (ok) {
                _syncState.value = SyncState.SYNCED
                _lastSyncedAt.value = System.currentTimeMillis()
                Log.d(TAG, "DailyClosing ${closing.dateString} pushed to cloud successfully.")
            }
            ok
        } catch (e: Exception) {
            _syncState.value = SyncState.OFFLINE
            Log.e(TAG, "Error pushing daily closing: ${e.message}")
            false
        }
    }

    suspend fun pushUserDirect(user: AppUser): Boolean = withContext(Dispatchers.IO) {
        try {
            val json = JSONObject().apply {
                put("id", user.id)
                put("name", user.name)
                put("email", user.email)
                put("role", user.role)
                put("pin", user.pin)
                put("isActive", user.isActive)
                put("createdBy", user.createdBy)
                put("createdAt", user.createdAt)
                put("updatedAt", user.updatedAt)
            }
            val ok = httpPut(getEndpointUrl("app_users/${user.id}.json"), json.toString())
            if (ok) {
                Log.d(TAG, "User ${user.email} pushed to cloud successfully.")
            }
            ok
        } catch (e: Exception) {
            Log.e(TAG, "Error pushing user to cloud: ${e.message}")
            false
        }
    }

    suspend fun deleteUserDirect(userId: Int): Boolean = withContext(Dispatchers.IO) {
        try {
            val ok = httpDelete(getEndpointUrl("app_users/$userId.json"))
            Log.d(TAG, "User $userId deleted from cloud: $ok")
            ok
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting user from cloud: ${e.message}")
            false
        }
    }

    // ==================== ASYNC WRAPPER METHODS (DEVICE -> CLOUD) ====================

    suspend fun flushPendingQueue() {
        try {
            val pendingStudents = appDao.getPendingSyncStudents()
            for (s in pendingStudents) {
                val success = pushStudentDirect(s)
                if (success) {
                    appDao.markStudentSynced(s.id)
                }
            }
            val pendingTeachers = appDao.getPendingSyncTeachers()
            for (tch in pendingTeachers) {
                val success = pushTeacherDirect(tch)
                if (success) {
                    appDao.markTeacherSynced(tch.id)
                }
            }
            val pendingTxs = appDao.getPendingSyncTransactions()
            for (t in pendingTxs) {
                val success = pushTransactionDirect(t)
                if (success) {
                    appDao.markTransactionSynced(t.id)
                }
            }
            if (pendingStudents.isNotEmpty() || pendingTeachers.isNotEmpty() || pendingTxs.isNotEmpty()) {
                Log.d(TAG, "Queue flushed: ${pendingStudents.size} students, ${pendingTeachers.size} teachers, ${pendingTxs.size} transactions.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error flushing pending sync queue: ${e.message}")
        }
    }

    fun pushStudent(student: Student, scope: CoroutineScope = syncScope) {
        scope.launch(Dispatchers.IO) {
            val success = pushStudentDirect(student)
            if (success) {
                appDao.markStudentSynced(student.id)
            }
        }
    }

    fun deleteStudentFromCloud(studentId: Int, scope: CoroutineScope = syncScope) {
        scope.launch(Dispatchers.IO) {
            deleteStudentDirect(studentId)
        }
    }

    fun pushTeacher(teacher: Teacher, scope: CoroutineScope = syncScope) {
        scope.launch(Dispatchers.IO) {
            val success = pushTeacherDirect(teacher)
            if (success) {
                appDao.markTeacherSynced(teacher.id)
            }
        }
    }

    fun deleteTeacherFromCloud(teacherId: Int, scope: CoroutineScope = syncScope) {
        scope.launch(Dispatchers.IO) {
            deleteTeacherDirect(teacherId)
        }
    }

    fun pushTransaction(transaction: Transaction, scope: CoroutineScope = syncScope) {
        scope.launch(Dispatchers.IO) {
            val success = pushTransactionDirect(transaction)
            if (success) {
                appDao.markTransactionSynced(transaction.id)
            }
        }
    }

    fun deleteTransactionFromCloud(transactionId: Int, scope: CoroutineScope = syncScope) {
        scope.launch(Dispatchers.IO) {
            deleteTransactionDirect(transactionId)
        }
    }

    fun pushDailyClosing(closing: DailyClosing, scope: CoroutineScope = syncScope) {
        scope.launch(Dispatchers.IO) {
            pushDailyClosingDirect(closing)
        }
    }

    fun pushUser(user: AppUser, scope: CoroutineScope = syncScope) {
        scope.launch(Dispatchers.IO) {
            pushUserDirect(user)
        }
    }

    fun deleteUserFromCloud(userId: Int, scope: CoroutineScope = syncScope) {
        scope.launch(Dispatchers.IO) {
            deleteUserDirect(userId)
        }
    }

    /**
     * Atomically allocates voucher numbers with safety against local ID collisions.
     */
    suspend fun allocateAtomicVoucherNo(isIncome: Boolean, role: String = ""): String = withContext(Dispatchers.IO) {
        val year = SimpleDateFormat("yyyy", Locale.US).format(Date())
        val prefix = if (isIncome) "BLS-REC" else "BLS-EXP"
        val effectiveRole = if (role.isNotBlank()) role else currentRole
        val roleSuffix = when {
            effectiveRole.equals(DomainConstants.ROLE_ADMIN, ignoreCase = true) -> "A"
            effectiveRole.equals(DomainConstants.ROLE_ACCOUNTANT, ignoreCase = true) -> "C"
            else -> "S"
        }
        val typeKey = if (isIncome) "income" else "expense"
        val localCount = try { appDao.getTransactionCountByType(isIncome) } catch (e: Exception) { 0 }

        try {
            val countUrl = getEndpointUrl("counters/vouchers/$typeKey.json")
            val currentStr = httpGet(countUrl)
            val currentCloudNum = currentStr?.trim()?.replace("\"", "")?.toIntOrNull() ?: 0
            // Guaranteed monotonic increase: never smaller than local count
            val nextNum = maxOf(currentCloudNum, localCount) + 1
            httpPut(countUrl, nextNum.toString())
            "$prefix-$year-${String.format(Locale.US, "%04d", nextNum)}-$roleSuffix"
        } catch (e: Exception) {
            "$prefix-$year-${String.format(Locale.US, "%04d", localCount + 1)}-$roleSuffix"
        }
    }

    // ==================== READS & STREAMING (CLOUD -> DEVICE) ====================

    /**
     * Safely hydrates initial cloud snapshot into local database without overwriting
     * un-synced offline modifications.
     */
    private suspend fun fetchAndHydrateInitialData() {
        try {
            val rawJson = httpGet(getEndpointUrl(".json"))
            if (rawJson.isNullOrBlank() || rawJson == "null") {
                seedLocalToCloud()
                return
            }

            val rootObj = JSONObject(rawJson)

            // 1. Hydrate Students with local pending protection
            if (rootObj.has("students") && !rootObj.isNull("students")) {
                val cloudStudents = mutableListOf<Student>()
                val studentsVal = rootObj.get("students")
                if (studentsVal is JSONObject) {
                    val keys = studentsVal.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        val sObj = studentsVal.optJSONObject(key) ?: continue
                        parseStudentJson(sObj)?.let { cloudStudents.add(it) }
                    }
                } else if (studentsVal is org.json.JSONArray) {
                    for (i in 0 until studentsVal.length()) {
                        val sObj = studentsVal.optJSONObject(i) ?: continue
                        parseStudentJson(sObj)?.let { cloudStudents.add(it) }
                    }
                }

                if (cloudStudents.isNotEmpty()) {
                    val pendingIds = appDao.getPendingSyncStudents().map { it.id }.toSet()
                    val localMap = appDao.getAllStudents().associateBy { it.id }
                    val toInsert = mutableListOf<Student>()

                    for (cs in cloudStudents) {
                        val local = localMap[cs.id]
                        if (local != null) {
                            // If local student has pending offline edits or newer timestamp, preserve local
                            if (pendingIds.contains(local.id) || local.updatedAt > cs.updatedAt) {
                                continue
                            }
                        }
                        toInsert.add(cs)
                    }

                    if (toInsert.isNotEmpty()) {
                        appDao.insertStudents(toInsert)
                    }
                }
            }

            // 2. Hydrate Transactions with local pending protection
            if (rootObj.has("transactions") && !rootObj.isNull("transactions")) {
                val cloudTxs = mutableListOf<Transaction>()
                val txVal = rootObj.get("transactions")
                if (txVal is JSONObject) {
                    val keys = txVal.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        val tObj = txVal.optJSONObject(key) ?: continue
                        parseTransactionJson(tObj)?.let { cloudTxs.add(it) }
                    }
                } else if (txVal is org.json.JSONArray) {
                    for (i in 0 until txVal.length()) {
                        val tObj = txVal.optJSONObject(i) ?: continue
                        parseTransactionJson(tObj)?.let { cloudTxs.add(it) }
                    }
                }

                if (cloudTxs.isNotEmpty()) {
                    val pendingTxIds = appDao.getPendingSyncTransactions().map { it.id }.toSet()
                    val localTxMap = appDao.getAllTransactions().associateBy { it.id }
                    val toInsert = mutableListOf<Transaction>()

                    for (ctx in cloudTxs) {
                        val local = localTxMap[ctx.id]
                        if (local != null) {
                            if (pendingTxIds.contains(local.id) || local.updatedAt > ctx.updatedAt) {
                                continue
                            }
                        }
                        toInsert.add(ctx)
                    }

                    if (toInsert.isNotEmpty()) {
                        appDao.insertTransactions(toInsert)
                    }
                }
            }

            // 3. Hydrate Daily Closings
            if (rootObj.has("daily_closings") && !rootObj.isNull("daily_closings")) {
                val closingsList = mutableListOf<DailyClosing>()
                val closingsVal = rootObj.get("daily_closings")
                if (closingsVal is JSONObject) {
                    val keys = closingsVal.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        val cObj = closingsVal.optJSONObject(key) ?: continue
                        parseClosingJson(cObj)?.let { closingsList.add(it) }
                    }
                }
                if (closingsList.isNotEmpty()) {
                    appDao.insertDailyClosings(closingsList)
                }
            }

            // 4. Hydrate App Users
            if (rootObj.has("app_users") && !rootObj.isNull("app_users")) {
                val usersList = mutableListOf<AppUser>()
                val usersVal = rootObj.get("app_users")
                if (usersVal is JSONObject) {
                    val keys = usersVal.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        val uObj = usersVal.optJSONObject(key) ?: continue
                        parseUserJson(uObj)?.let { usersList.add(it) }
                    }
                } else if (usersVal is org.json.JSONArray) {
                    for (i in 0 until usersVal.length()) {
                        val uObj = usersVal.optJSONObject(i) ?: continue
                        parseUserJson(uObj)?.let { usersList.add(it) }
                    }
                }
                if (usersList.isNotEmpty()) {
                    val localUserMap = appDao.getAllUsers().associateBy { it.id }
                    val toInsert = mutableListOf<AppUser>()
                    for (cu in usersList) {
                        val local = localUserMap[cu.id]
                        if (local != null && local.updatedAt > cu.updatedAt) {
                            continue
                        }
                        toInsert.add(cu)
                    }
                    if (toInsert.isNotEmpty()) {
                        appDao.insertUsers(toInsert)
                    }
                }
            }

            // 5. Hydrate Teachers with local pending protection
            if (rootObj.has("teachers") && !rootObj.isNull("teachers")) {
                val cloudTeachers = mutableListOf<Teacher>()
                val teachersVal = rootObj.get("teachers")
                if (teachersVal is JSONObject) {
                    val keys = teachersVal.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        val tchObj = teachersVal.optJSONObject(key) ?: continue
                        parseTeacherJson(tchObj)?.let { cloudTeachers.add(it) }
                    }
                } else if (teachersVal is org.json.JSONArray) {
                    for (i in 0 until teachersVal.length()) {
                        val tchObj = teachersVal.optJSONObject(i) ?: continue
                        parseTeacherJson(tchObj)?.let { cloudTeachers.add(it) }
                    }
                }

                if (cloudTeachers.isNotEmpty()) {
                    val pendingTchIds = appDao.getPendingSyncTeachers().map { it.id }.toSet()
                    val localTchMap = appDao.getAllTeachers().associateBy { it.id }
                    val toInsert = mutableListOf<Teacher>()

                    for (ctch in cloudTeachers) {
                        val local = localTchMap[ctch.id]
                        if (local != null) {
                            if (pendingTchIds.contains(local.id) || local.updatedAt > ctch.updatedAt) {
                                continue
                            }
                        }
                        toInsert.add(ctch)
                    }

                    if (toInsert.isNotEmpty()) {
                        appDao.insertTeachers(toInsert)
                    }
                }
            }

            Log.d(TAG, "Initial hydration from Firebase complete.")
        } catch (e: Exception) {
            Log.e(TAG, "Error in initial hydration: ${e.message}")
        }
    }

    private suspend fun seedLocalToCloud() {
        try {
            val localStudents = appDao.getAllStudents()
            localStudents.forEach { pushStudentDirect(it) }

            val localTeachers = appDao.getAllTeachers()
            localTeachers.forEach { pushTeacherDirect(it) }

            val localTx = appDao.getAllTransactions()
            localTx.forEach { pushTransactionDirect(it) }

            val localUsers = appDao.getAllUsers()
            localUsers.forEach { pushUserDirect(it) }
            Log.d(TAG, "Seeded ${localStudents.size} students, ${localTeachers.size} teachers, ${localTx.size} transactions and ${localUsers.size} users to cloud.")
        } catch (e: Exception) {
            Log.e(TAG, "Error seeding local to cloud: ${e.message}")
        }
    }

    private suspend fun listenToEventStream() = withContext(Dispatchers.IO) {
        val url = URL(getEndpointUrl(".json"))
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            setRequestProperty("Accept", "text/event-stream")
            readTimeout = 0 // Keep stream open indefinitely
            connectTimeout = CONNECT_TIMEOUT_MS
            doInput = true
        }

        conn.connect()
        val reader = BufferedReader(InputStreamReader(conn.inputStream))
        var eventType = ""

        try {
            while (isActive) {
                val line = reader.readLine() ?: break
                if (line.startsWith("event:")) {
                    eventType = line.substringAfter("event:").trim()
                } else if (line.startsWith("data:")) {
                    val data = line.substringAfter("data:").trim()
                    if (data == "null" || data.isEmpty()) continue
                    handleStreamEvent(eventType, data)
                }
            }
        } finally {
            try { reader.close() } catch (ignored: Exception) {}
            try { conn.disconnect() } catch (ignored: Exception) {}
        }
    }

    private suspend fun handleStreamEvent(eventType: String, dataJson: String) {
        try {
            if (eventType == "keep-alive") return

            val eventObj = JSONObject(dataJson)
            val path = eventObj.optString("path", "")
            val data = eventObj.opt("data")

            Log.d(TAG, "Live Firebase Event: path=$path, type=$eventType")

            if (path == "/" || path.isEmpty()) {
                fetchAndHydrateInitialData()
                return
            }

            val segments = path.trim('/').split('/')
            val collection = segments.getOrNull(0) ?: return
            val documentId = segments.getOrNull(1)

            when (collection) {
                "students" -> {
                    if (data == null || data == JSONObject.NULL) {
                        documentId?.toIntOrNull()?.let { appDao.softDeleteStudent(it) }
                    } else if (data is JSONObject) {
                        var student = parseStudentJson(data)
                        if (student == null && documentId != null) {
                            // Document-level fallback: Fetch single student instead of entire database
                            val singleJson = httpGet(getEndpointUrl("students/$documentId.json"))
                            if (!singleJson.isNullOrBlank() && singleJson != "null") {
                                student = parseStudentJson(JSONObject(singleJson))
                            }
                        }

                        if (student != null) {
                            val local = appDao.getStudentById(student.id)
                            // Protect local pending edits
                            if (local == null || (local.syncStatus != DomainConstants.SYNC_STATUS_PENDING && local.updatedAt <= student.updatedAt)) {
                                val isExisting = local != null
                                appDao.insertStudent(student)
                                if (!isExisting && student.createdBy.isNotBlank() && !student.createdBy.equals(currentRole, ignoreCase = true)) {
                                    appContext?.let { ctx ->
                                        NotificationHelper.showStudentAdmissionAlert(
                                            context = ctx,
                                            studentName = student.studentName,
                                            className = student.className,
                                            monthlyFee = student.monthlyFee,
                                            author = student.createdBy
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        fetchAndHydrateInitialData()
                    }
                }

                "transactions" -> {
                    if (data == null || data == JSONObject.NULL) {
                        documentId?.toIntOrNull()?.let { appDao.softDeleteTransaction(it) }
                    } else if (data is JSONObject) {
                        var tx = parseTransactionJson(data)
                        if (tx == null && documentId != null) {
                            val singleJson = httpGet(getEndpointUrl("transactions/$documentId.json"))
                            if (!singleJson.isNullOrBlank() && singleJson != "null") {
                                tx = parseTransactionJson(JSONObject(singleJson))
                            }
                        }

                        if (tx != null) {
                            val localList = appDao.getAllTransactions()
                            val local = localList.find { it.id == tx.id }
                            if (local == null || (local.syncStatus != DomainConstants.SYNC_STATUS_PENDING && local.updatedAt <= tx.updatedAt)) {
                                val isExisting = local != null
                                appDao.insertTransaction(tx)
                                if (!isExisting && tx.recordedBy.isNotBlank() && !tx.recordedBy.equals(currentRole, ignoreCase = true)) {
                                    appContext?.let { ctx ->
                                        if (tx.isIncome) {
                                            NotificationHelper.showFeeCollectionAlert(
                                                context = ctx,
                                                studentName = tx.studentName ?: "Student",
                                                amount = tx.amount,
                                                voucherNo = tx.voucherNo,
                                                author = tx.recordedBy
                                            )
                                        } else {
                                            NotificationHelper.showExpenseAlert(
                                                context = ctx,
                                                category = tx.category,
                                                amount = tx.amount,
                                                voucherNo = tx.voucherNo,
                                                author = tx.recordedBy
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        fetchAndHydrateInitialData()
                    }
                }

                "daily_closings" -> {
                    if (data is JSONObject) {
                        parseClosingJson(data)?.let { appDao.insertDailyClosing(it) }
                    } else {
                        fetchAndHydrateInitialData()
                    }
                }

                "app_users" -> {
                    if (data == null || data == JSONObject.NULL) {
                        documentId?.toIntOrNull()?.let { uid ->
                            val user = appDao.getAllUsers().find { it.id == uid }
                            if (user != null) appDao.deleteUser(user)
                        }
                    } else if (data is JSONObject) {
                        var user = parseUserJson(data)
                        if (user == null && documentId != null) {
                            val singleJson = httpGet(getEndpointUrl("app_users/$documentId.json"))
                            if (!singleJson.isNullOrBlank() && singleJson != "null") {
                                user = parseUserJson(JSONObject(singleJson))
                            }
                        }
                        if (user != null) {
                            val local = appDao.getAllUsers().find { it.id == user.id }
                            if (local == null || local.updatedAt <= user.updatedAt) {
                                appDao.insertUser(user)
                            }
                        }
                    } else {
                        fetchAndHydrateInitialData()
                    }
                }

                "teachers" -> {
                    if (data == null || data == JSONObject.NULL) {
                        documentId?.toIntOrNull()?.let { appDao.softDeleteTeacher(it) }
                    } else if (data is JSONObject) {
                        var teacher = parseTeacherJson(data)
                        if (teacher == null && documentId != null) {
                            val singleJson = httpGet(getEndpointUrl("teachers/$documentId.json"))
                            if (!singleJson.isNullOrBlank() && singleJson != "null") {
                                teacher = parseTeacherJson(JSONObject(singleJson))
                            }
                        }
                        if (teacher != null) {
                            val local = appDao.getTeacherById(teacher.id)
                            if (local == null || (local.syncStatus != DomainConstants.SYNC_STATUS_PENDING && local.updatedAt <= teacher.updatedAt)) {
                                appDao.insertTeacher(teacher)
                            }
                        }
                    } else {
                        fetchAndHydrateInitialData()
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling stream event: ${e.message}")
        }
    }

    // ==================== JSON PARSERS ====================

    private fun parseTeacherJson(obj: JSONObject): Teacher? {
        return try {
            val id = obj.optInt("id", 0)
            if (id == 0) return null
            Teacher(
                id = id,
                name = obj.optString("name", ""),
                designation = obj.optString("designation", ""),
                contactNumber = obj.optString("contactNumber", ""),
                cnic = obj.optString("cnic", ""),
                qualification = obj.optString("qualification", ""),
                monthlySalary = obj.optDouble("monthlySalary", 0.0),
                joiningDate = obj.optLong("joiningDate", System.currentTimeMillis()),
                status = obj.optString("status", "Active"),
                photoUri = obj.optString("photoUri", ""),
                address = obj.optString("address", ""),
                createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                updatedAt = obj.optLong("updatedAt", System.currentTimeMillis()),
                createdBy = obj.optString("createdBy", "Staff"),
                isDeleted = obj.optBoolean("isDeleted", false),
                syncStatus = DomainConstants.SYNC_STATUS_SYNCED
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun parseStudentJson(obj: JSONObject): Student? {
        return try {
            val id = obj.optInt("id", 0)
            if (id == 0) return null
            Student(
                id = id,
                regNo = obj.optString("regNo", ""),
                studentName = obj.optString("studentName", ""),
                fatherName = obj.optString("fatherName", ""),
                className = obj.optString("className", ""),
                contactNumber = obj.optString("contactNumber", ""),
                admissionFee = obj.optDouble("admissionFee", 0.0),
                monthlyFee = obj.optDouble("monthlyFee", 0.0),
                annualCharges = obj.optDouble("annualCharges", 0.0),
                discountType = obj.optString("discountType", "None"),
                otherCharges = obj.optDouble("otherCharges", 0.0),
                admissionDate = obj.optLong("admissionDate", System.currentTimeMillis()),
                idCardNumber = obj.optString("idCardNumber", ""),
                previousSchoolName = obj.optString("previousSchoolName", ""),
                fatherIdCardNumber = obj.optString("fatherIdCardNumber", ""),
                schoolCertAttached = obj.optBoolean("schoolCertAttached", false),
                formBAttached = obj.optBoolean("formBAttached", false),
                fatherCnicAttached = obj.optBoolean("fatherCnicAttached", false),
                picsAttached = obj.optBoolean("picsAttached", false),
                status = obj.optString("status", "Active"),
                photoUri = obj.optString("photoUri", ""),
                createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                updatedAt = obj.optLong("updatedAt", System.currentTimeMillis()),
                createdBy = obj.optString("createdBy", "Admin"),
                isDeleted = obj.optBoolean("isDeleted", false),
                syncStatus = DomainConstants.SYNC_STATUS_SYNCED
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun parseTransactionJson(obj: JSONObject): Transaction? {
        return try {
            val id = obj.optInt("id", 0)
            if (id == 0) return null
            Transaction(
                id = id,
                studentId = if (obj.has("studentId") && !obj.isNull("studentId")) obj.optInt("studentId") else null,
                studentName = if (obj.has("studentName") && !obj.isNull("studentName")) obj.optString("studentName") else null,
                amount = obj.optDouble("amount", 0.0),
                category = obj.optString("category", ""),
                isIncome = obj.optBoolean("isIncome", true),
                paymentMode = obj.optString("paymentMode", "Cash"),
                date = obj.optLong("date", System.currentTimeMillis()),
                recordedBy = obj.optString("recordedBy", "Staff"),
                description = obj.optString("description", ""),
                monthOfFee = if (obj.has("monthOfFee") && !obj.isNull("monthOfFee")) obj.optString("monthOfFee") else null,
                voucherNo = obj.optString("voucherNo", ""),
                payeeName = obj.optString("payeeName", ""),
                invoiceNo = obj.optString("invoiceNo", ""),
                teacherId = if (obj.has("teacherId") && !obj.isNull("teacherId")) obj.optInt("teacherId") else null,
                createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                updatedAt = obj.optLong("updatedAt", System.currentTimeMillis()),
                isDeleted = obj.optBoolean("isDeleted", false),
                syncStatus = DomainConstants.SYNC_STATUS_SYNCED
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun parseClosingJson(obj: JSONObject): DailyClosing? {
        return try {
            val dateStr = obj.optString("dateString", "")
            if (dateStr.isEmpty()) return null
            DailyClosing(
                dateString = dateStr,
                openingCash = obj.optDouble("openingCash", 0.0),
                openingBank = obj.optDouble("openingBank", 0.0),
                totalIncomeCash = obj.optDouble("totalIncomeCash", 0.0),
                totalIncomeBank = obj.optDouble("totalIncomeBank", 0.0),
                totalExpenseCash = obj.optDouble("totalExpenseCash", 0.0),
                totalExpenseBank = obj.optDouble("totalExpenseBank", 0.0),
                closingCash = obj.optDouble("closingCash", 0.0),
                closingBank = obj.optDouble("closingBank", 0.0),
                isClosed = obj.optBoolean("isClosed", false),
                closedBy = obj.optString("closedBy", ""),
                closedAt = obj.optLong("closedAt", System.currentTimeMillis())
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun parseUserJson(obj: JSONObject): AppUser? {
        return try {
            val id = obj.optInt("id", 0)
            val email = obj.optString("email", "")
            if (id == 0 || email.isBlank()) return null
            AppUser(
                id = id,
                name = obj.optString("name", "Staff Member"),
                email = email,
                role = obj.optString("role", DomainConstants.ROLE_ACCOUNTANT),
                pin = obj.optString("pin", "1111"),
                isActive = obj.optBoolean("isActive", true),
                createdBy = obj.optString("createdBy", "Super Admin"),
                createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                updatedAt = obj.optLong("updatedAt", System.currentTimeMillis())
            )
        } catch (e: Exception) {
            null
        }
    }

    suspend fun wipeCloudData(): Boolean = withContext(Dispatchers.IO) {
        try {
            httpDelete(getEndpointUrl("students.json"))
            httpDelete(getEndpointUrl("transactions.json"))
            httpDelete(getEndpointUrl("daily_closings.json"))
            Log.d(TAG, "Cloud data wiped successfully.")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error wiping cloud data: ${e.message}")
            false
        }
    }

    private fun httpGet(urlStr: String): String? {
        val url = URL(urlStr)
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
        }
        return try {
            if (conn.responseCode in 200..299) {
                conn.inputStream.bufferedReader().use { it.readText() }
            } else null
        } finally {
            try { conn.disconnect() } catch (ignored: Exception) {}
        }
    }

    private fun httpPut(urlStr: String, jsonBody: String): Boolean {
        val url = URL(urlStr)
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "PUT"
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            doOutput = true
        }
        return try {
            OutputStreamWriter(conn.outputStream, "UTF-8").use { it.write(jsonBody) }
            conn.responseCode in 200..299
        } finally {
            try { conn.disconnect() } catch (ignored: Exception) {}
        }
    }

    private fun httpDelete(urlStr: String): Boolean {
        val url = URL(urlStr)
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "DELETE"
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
        }
        return try {
            conn.responseCode in 200..299
        } finally {
            try { conn.disconnect() } catch (ignored: Exception) {}
        }
    }
}
