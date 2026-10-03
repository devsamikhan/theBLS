package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import com.example.util.SecurityPreferences
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * =====================================================================
 * APP VIEW MODEL - CORE REACTIVE STATE HOLDER & DOMAIN ORCHESTRATOR
 * =====================================================================
 * Powers the Minimalist UI Design architecture for BLS Cash & Ledger System:
 * - Exposes lean, pre-computed StateFlows to minimize UI recomposition overhead.
 * - Manages reactive database queries, cloud synchronization, and audit feeds.
 * - Handles student enrollment, multi-tier fee reception, and daily closing reconciliation.
 */
class AppViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: AppRepository
    private val securityPrefs = SecurityPreferences(application)

    init {
        val database = AppDatabase.getDatabase(application)
        repository = AppRepository(database.appDao())
        repository.setContext(application)
        repository.configureSync(securityPrefs.getFirebaseUrl(), securityPrefs.getFirebaseAuthSecret())
        repository.startSync(viewModelScope)
    }

    // =================================================================
    // SECTION 1: AUTHENTICATION, SECURITY PINS & ROLES
    // =================================================================

    private val _currentUserRole = MutableStateFlow<String>(DomainConstants.ROLE_NONE)
    val currentUserRole: StateFlow<String> = _currentUserRole.asStateFlow()

    private val _loginError = MutableStateFlow<String?>(null)
    val loginError: StateFlow<String?> = _loginError.asStateFlow()

    fun login(pin: String): Boolean {
        _loginError.value = null
        val role = securityPrefs.validatePin(pin.trim())
        return when (role) {
            DomainConstants.ROLE_ADMIN -> {
                _currentUserRole.value = DomainConstants.ROLE_ADMIN
                repository.setCurrentRole(DomainConstants.ROLE_ADMIN)
                true
            }
            DomainConstants.ROLE_ACCOUNTANT -> {
                _currentUserRole.value = DomainConstants.ROLE_ACCOUNTANT
                repository.setCurrentRole(DomainConstants.ROLE_ACCOUNTANT)
                true
            }
            else -> {
                _loginError.value = "Invalid Pin! Please check your authorization code."
                false
            }
        }
    }

    fun loginWithBiometric(targetRole: String = DomainConstants.ROLE_ADMIN): Boolean {
        _loginError.value = null
        securityPrefs.resetFailedAttempts()
        _currentUserRole.value = targetRole
        repository.setCurrentRole(targetRole)
        return true
    }

    fun updateSecurityPins(currentAdminPin: String, newAdminPin: String, newAccountantPin: String): Boolean {
        if (securityPrefs.validatePin(currentAdminPin.trim()) != DomainConstants.ROLE_ADMIN) {
            return false
        }
        if (newAdminPin.isNotBlank()) {
            securityPrefs.setAdminPin(newAdminPin.trim())
        }
        if (newAccountantPin.isNotBlank()) {
            securityPrefs.setAccountantPin(newAccountantPin.trim())
        }
        return true
    }

    fun getAdminPin(): String = securityPrefs.getAdminPin()
    fun getAccountantPin(): String = securityPrefs.getAccountantPin()

    fun logout() {
        _currentUserRole.value = DomainConstants.ROLE_NONE
        repository.setCurrentRole("")
    }

    // =================================================================
    // SECTION 2: REAL-TIME DATABASE FLOWS & CLOUD SYNC
    // =================================================================

    val studentsList: StateFlow<List<Student>> = repository.allStudentsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val transactionsList: StateFlow<List<Transaction>> = repository.allTransactionsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val dailyClosingsList: StateFlow<List<DailyClosing>> = repository.allDailyClosingsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val syncState: StateFlow<SyncState> = repository.syncManager.syncState
    val lastSyncedAt: StateFlow<Long> = repository.syncManager.lastSyncedAt

    fun flushOfflineSyncQueue() {
        viewModelScope.launch {
            repository.syncManager.flushPendingQueue()
        }
    }

    fun getFirebaseUrl(): String = securityPrefs.getFirebaseUrl()
    fun getFirebaseAuthSecret(): String = securityPrefs.getFirebaseAuthSecret()

    fun updateCloudSyncConfig(url: String, secret: String) {
        securityPrefs.setFirebaseUrl(url.trim())
        securityPrefs.setFirebaseAuthSecret(secret.trim())
        repository.configureSync(url.trim(), secret.trim())
        repository.startSync(viewModelScope)
    }

    suspend fun testCloudSync(url: String, secret: String): Pair<Boolean, String> {
        return repository.testSyncConnection(url.trim(), secret.trim())
    }

    // =================================================================
    // SECTION 3: ACTIVITY FEED & REAL-TIME AUDIT LOG
    // =================================================================

    private val _lastReadActivityTimestamp = MutableStateFlow(securityPrefs.getLastReadActivityTimestamp())
    val lastReadActivityTimestamp: StateFlow<Long> = _lastReadActivityTimestamp.asStateFlow()

    fun markActivitiesAsRead() {
        val now = System.currentTimeMillis()
        securityPrefs.setLastReadActivityTimestamp(now)
        _lastReadActivityTimestamp.value = now
    }

    val activityFeed: StateFlow<List<ActivityFeedItem>> = combine(studentsList, transactionsList) { students, txs ->
        val studentItems = students.map { s ->
            ActivityFeedItem(
                id = "std_${s.id}",
                title = "New Admission: ${s.studentName}",
                subtitle = "Class: ${s.className} | Father: ${s.fatherName}",
                amount = s.admissionFee,
                authorRole = if (s.createdBy.isBlank() || s.createdBy.equals("Staff", true)) DomainConstants.ROLE_ADMIN else s.createdBy,
                type = ActivityType.ADMISSION,
                timestamp = s.createdAt,
                voucherNo = "Roll# ${s.regNo}",
                details = "Monthly Tuition: Rs. ${s.monthlyFee.toInt()}"
            )
        }
        val txItems = txs.map { t ->
            val type = when {
                t.category.contains("Transfer", ignoreCase = true) || (t.paymentMode == "Bank" && t.description.contains("Transfer", ignoreCase = true)) -> ActivityType.BANK_TRANSFER
                t.isIncome -> ActivityType.FEE_COLLECTION
                else -> ActivityType.EXPENSE
            }
            val title = when (type) {
                ActivityType.ADMISSION -> "New Admission: ${t.studentName ?: "Student"}"
                ActivityType.FEE_COLLECTION -> "Fee Collection: ${t.studentName ?: "Student"}"
                ActivityType.EXPENSE -> "Expense: ${t.category}"
                ActivityType.BANK_TRANSFER -> "Bank Deposit / Transfer"
            }
            val subtitle = when (type) {
                ActivityType.ADMISSION -> "${t.description.ifBlank { "New Admission" }} • Roll#: ${t.voucherNo}"
                ActivityType.FEE_COLLECTION -> "Month: ${t.monthOfFee ?: "Current"} • Voucher: #${t.voucherNo}"
                ActivityType.EXPENSE -> "${t.description.ifBlank { t.category }} • Voucher: #${t.voucherNo}"
                ActivityType.BANK_TRANSFER -> "${t.description.ifBlank { "Cash to Bank Deposit" }} • Voucher: #${t.voucherNo}"
            }
            ActivityFeedItem(
                id = "tx_${t.id}",
                title = title,
                subtitle = subtitle,
                amount = t.amount,
                authorRole = if (t.recordedBy.isBlank()) DomainConstants.ROLE_ACCOUNTANT else t.recordedBy,
                type = type,
                timestamp = t.createdAt,
                voucherNo = t.voucherNo,
                details = "${t.paymentMode} • ${t.date}"
            )
        }
        (studentItems + txItems).sortedByDescending { it.timestamp }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val unreadActivityCount: StateFlow<Int> = combine(activityFeed, _currentUserRole, _lastReadActivityTimestamp) { feed, role, lastRead ->
        when (role) {
            DomainConstants.ROLE_ACCOUNTANT -> {
                feed.count { it.authorRole == DomainConstants.ROLE_ADMIN && it.timestamp > lastRead }
            }
            DomainConstants.ROLE_ADMIN -> {
                feed.count { it.authorRole == DomainConstants.ROLE_ACCOUNTANT && it.timestamp > lastRead }
            }
            else -> 0
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    // =================================================================
    // SECTION 4: SEARCH, FILTERING & STUDENT SELECTION
    // =================================================================

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    private val _statusFilter = MutableStateFlow("Active") // "Active", "Left", "All"
    val statusFilter: StateFlow<String> = _statusFilter.asStateFlow()

    fun setStatusFilter(filter: String) {
        _statusFilter.value = filter
    }

    private val _selectedStudent = MutableStateFlow<Student?>(null)
    val selectedStudent: StateFlow<Student?> = _selectedStudent.asStateFlow()

    fun selectStudent(student: Student?) {
        _selectedStudent.value = student
    }

    // Filter students by query and status (Trimmed & case-insensitive)
    val filteredStudents: StateFlow<List<Student>> = combine(studentsList, searchQuery, _statusFilter) { list, query, status ->
        val filteredByStatus = when (status) {
            "Active" -> list.filter { it.status.equals("Active", ignoreCase = true) }
            "Left" -> list.filter { !it.status.equals("Active", ignoreCase = true) }
            else -> list
        }
        val q = query.trim()
        if (q.isBlank()) filteredByStatus else {
            filteredByStatus.filter { 
                it.studentName.contains(q, ignoreCase = true) || 
                it.fatherName.contains(q, ignoreCase = true) ||
                it.className.contains(q, ignoreCase = true) ||
                it.regNo.contains(q, ignoreCase = true)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // =================================================================
    // SECTION 5: DAILY CLOSING & DAY RECONCILIATION
    // =================================================================

    private val _currentDayClosing = MutableStateFlow<DailyClosing?>(null)
    val currentDayClosing: StateFlow<DailyClosing?> = _currentDayClosing.asStateFlow()

    fun refreshDailyClosing() {
        viewModelScope.launch {
            val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
            val existing = repository.getDailyClosing(todayStr)
            if (existing != null && existing.isClosed) {
                _currentDayClosing.value = existing
            } else {
                val calculated = repository.calculateDailyClosingState(todayStr)
                _currentDayClosing.value = calculated
            }
        }
    }

    fun closeDayAction(onAutoBackupReady: ((File) -> Unit)? = null) {
        viewModelScope.launch {
            val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
            val current = _currentDayClosing.value ?: repository.calculateDailyClosingState(todayStr)
            val closedState = current.copy(
                isClosed = true,
                closedBy = _currentUserRole.value
            )
            repository.saveDailyClosing(closedState)
            _currentDayClosing.value = closedState

            if (onAutoBackupReady != null) {
                try {
                    val json = exportDatabaseToJson()
                    val context = getApplication<Application>().applicationContext
                    val backupFile = File(context.cacheDir, "BLS_AutoBackup_${todayStr}.json")
                    backupFile.writeText(json)
                    onAutoBackupReady(backupFile)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    // =================================================================
    // SECTION 6: FINANCIAL LEDGER FLOWS & HIGH-PERFORMANCE METRICS
    // =================================================================

    val cashLedger: StateFlow<List<Transaction>> = transactionsList.map { list ->
        list.filter { it.paymentMode.equals(DomainConstants.MODE_CASH, ignoreCase = true) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val bankLedger: StateFlow<List<Transaction>> = transactionsList.map { list ->
        list.filter { it.paymentMode.equals(DomainConstants.MODE_BANK, ignoreCase = true) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Multi-month automatic fee arrears analysis
    val pendingFeeAlerts: StateFlow<List<PendingFeeAlert>> = combine(studentsList, transactionsList) { students, transactions ->
        val sdf = SimpleDateFormat("MMMM yyyy", Locale.US)
        val now = Date()
        val currentMonthYear = sdf.format(now)
        
        students.filter { it.status.equals("Active", ignoreCase = true) }.mapNotNull { student ->
            val discountedMonthly = DomainConstants.calculateDiscountedFee(student.monthlyFee, student.discountType)
            
            val admCal = Calendar.getInstance().apply { 
                timeInMillis = student.admissionDate 
                set(Calendar.DAY_OF_MONTH, 1)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            
            val tempCal = Calendar.getInstance().apply {
                time = now
                set(Calendar.DAY_OF_MONTH, 1)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            
            val monthsToCheck = mutableListOf<String>()
            for (i in 0 until 6) {
                if (tempCal.before(admCal)) break
                monthsToCheck.add(sdf.format(tempCal.time))
                tempCal.add(Calendar.MONTH, -1)
            }
            if (!monthsToCheck.contains(currentMonthYear)) {
                monthsToCheck.add(0, currentMonthYear)
            }
            
            var currentMonthPending = 0.0
            var arrearsAmount = 0.0
            val unpaidMonths = mutableListOf<String>()
            
            if (discountedMonthly > 0.0) {
                for (mStr in monthsToCheck) {
                    val paidForMonth = transactions.filter { tx ->
                        tx.isIncome && 
                        tx.studentId == student.id && 
                        tx.category == DomainConstants.CAT_FEE_RECEIVED && 
                        tx.monthOfFee.equals(mStr, ignoreCase = true)
                    }.sumOf { it.amount }
                    
                    val dueForMonth = maxOf(0.0, discountedMonthly - paidForMonth)
                    if (dueForMonth > 0.0) {
                        unpaidMonths.add(mStr)
                        if (mStr.equals(currentMonthYear, ignoreCase = true)) {
                            currentMonthPending += dueForMonth
                        } else {
                            arrearsAmount += dueForMonth
                        }
                    }
                }
            }
            
            var unpaidAdmission = 0.0
            if (student.admissionFee > 0.0) {
                val paidAdm = transactions.filter { tx ->
                    tx.isIncome && 
                    tx.studentId == student.id && 
                    tx.category == DomainConstants.CAT_ADMISSION_FEE
                }.sumOf { it.amount }
                val dueAdm = maxOf(0.0, student.admissionFee - paidAdm)
                if (dueAdm > 0.0) {
                    unpaidAdmission = dueAdm
                    arrearsAmount += dueAdm
                    unpaidMonths.add("Admission Fee (Rs. ${dueAdm.toInt()})")
                }
            }

            var unpaidAnnual = 0.0
            if (student.annualCharges > 0.0) {
                val paidAnn = transactions.filter { tx ->
                    tx.isIncome && 
                    tx.studentId == student.id && 
                    tx.category == DomainConstants.CAT_ANNUAL_CHARGES
                }.sumOf { it.amount }
                val dueAnn = maxOf(0.0, student.annualCharges - paidAnn)
                if (dueAnn > 0.0) {
                    unpaidAnnual = dueAnn
                    arrearsAmount += dueAnn
                    unpaidMonths.add("Annual Charges (Rs. ${dueAnn.toInt()})")
                }
            }

            var unpaidOther = 0.0
            if (student.otherCharges > 0.0) {
                val paidOth = transactions.filter { tx ->
                    tx.isIncome && 
                    tx.studentId == student.id && 
                    tx.category == DomainConstants.CAT_OTHER_CHARGES
                }.sumOf { it.amount }
                val dueOth = maxOf(0.0, student.otherCharges - paidOth)
                if (dueOth > 0.0) {
                    unpaidOther = dueOth
                    arrearsAmount += dueOth
                    unpaidMonths.add("Other Charges (Rs. ${dueOth.toInt()})")
                }
            }

            val totalPending = currentMonthPending + arrearsAmount
            if (totalPending > 0.0) {
                PendingFeeAlert(
                    studentId = student.id,
                    studentName = student.studentName,
                    fatherName = student.fatherName,
                    className = student.className,
                    pendingAmount = totalPending,
                    currentMonthPending = currentMonthPending,
                    arrearsAmount = arrearsAmount,
                    totalPendingAmount = totalPending,
                    month = currentMonthYear,
                    contactNumber = student.contactNumber,
                    pendingMonthsList = unpaidMonths,
                    unpaidAdmissionFee = unpaidAdmission,
                    unpaidAnnualCharges = unpaidAnnual,
                    unpaidOtherCharges = unpaidOther
                )
            } else {
                null
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Financial Metrics Flow (Cash In/Out, Bank In/Out, Net Surplus)
    val financialMetrics: StateFlow<FinancialMetrics> = transactionsList.map { list ->
        var incomeCash = 0.0
        var incomeBank = 0.0
        var expenseCash = 0.0
        var expenseBank = 0.0
        var operatingIncome = 0.0
        var operatingExpense = 0.0

        for (tx in list) {
            val isCash = tx.paymentMode.equals(DomainConstants.MODE_CASH, ignoreCase = true)
            val isContra = DomainConstants.isContraTransfer(tx.category)

            if (tx.isIncome) {
                if (isCash) incomeCash += tx.amount else incomeBank += tx.amount
                if (!isContra) operatingIncome += tx.amount
            } else {
                if (isCash) expenseCash += tx.amount else expenseBank += tx.amount
                if (!isContra) operatingExpense += tx.amount
            }
        }

        FinancialMetrics(
            totalCashIncome = incomeCash,
            totalBankIncome = incomeBank,
            totalCashExpense = expenseCash,
            totalBankExpense = expenseBank,
            cashBalance = incomeCash - expenseCash,
            bankBalance = incomeBank - expenseBank,
            totalIncome = operatingIncome,
            totalExpense = operatingExpense,
            netBalance = operatingIncome - operatingExpense
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), FinancialMetrics())

    // Pre-calculated Overall Fee Recovery Rate for Minimalist Headers
    val feeRecoveryRate: StateFlow<Float> = combine(studentsList, transactionsList) { students, txs ->
        val currentMonthYear = SimpleDateFormat("MMMM yyyy", Locale.US).format(Date())
        val expected = students.filter { it.status.equals("Active", ignoreCase = true) }.sumOf { std ->
            DomainConstants.calculateDiscountedFee(std.monthlyFee, std.discountType)
        }
        val collected = txs.filter { tx ->
            tx.isIncome && (
                tx.monthOfFee == currentMonthYear ||
                (tx.monthOfFee.isNullOrBlank() && SimpleDateFormat("MMMM yyyy", Locale.US).format(Date(tx.date)) == currentMonthYear)
            )
        }.sumOf { it.amount }
        if (expected > 0.0) ((collected / expected).toFloat() * 100f).coerceIn(0f, 100f) else 100f
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0f)

    // =================================================================
    // SECTION 7: STUDENT LIFECYCLE OPERATIONS
    // =================================================================

    fun generateNextRegNo(): String {
        val year = SimpleDateFormat("yyyy", Locale.US).format(Date())
        val maxId = studentsList.value.maxOfOrNull { it.id } ?: 0
        return String.format(Locale.US, "BLS-%s-%04d", year, maxId + 1)
    }

    fun addStudent(
        name: String, 
        fName: String, 
        clsName: String, 
        contact: String,
        admFee: Double,
        mthFee: Double,
        annCharges: Double,
        discType: String,
        othCharges: Double,
        idCard: String = "",
        prevSchool: String = "",
        fatherIdCard: String = "",
        schoolCertAttached: Boolean = false,
        formBAttached: Boolean = false,
        fatherCnicAttached: Boolean = false,
        picsAttached: Boolean = false,
        regNo: String = "",
        photoUri: String = "",
        onSuccess: (Student) -> Unit = {}
    ) {
        viewModelScope.launch {
            val assignedRegNo = if (regNo.isNotBlank()) regNo.trim() else generateNextRegNo()
            val student = Student(
                regNo = assignedRegNo,
                studentName = name.trim(),
                fatherName = fName.trim(),
                className = clsName.trim(),
                contactNumber = contact.trim(),
                admissionFee = admFee,
                monthlyFee = mthFee,
                annualCharges = annCharges,
                discountType = discType,
                otherCharges = othCharges,
                idCardNumber = idCard.trim(),
                previousSchoolName = prevSchool.trim(),
                fatherIdCardNumber = fatherIdCard.trim(),
                schoolCertAttached = schoolCertAttached,
                formBAttached = formBAttached,
                fatherCnicAttached = fatherCnicAttached,
                picsAttached = picsAttached,
                photoUri = photoUri,
                createdBy = _currentUserRole.value.ifBlank { DomainConstants.ROLE_ADMIN }
            )
            val newId = repository.insertStudent(student)
            val savedStudent = student.copy(id = newId.toInt())
            refreshDailyClosing()
            onSuccess(savedStudent)
        }
    }

    fun updateStudentStatus(studentId: Int, newStatus: String) {
        viewModelScope.launch {
            repository.updateStudentStatus(studentId, newStatus)
            refreshDailyClosing()
        }
    }

    fun updateStudentPhoto(studentId: Int, photoUri: String) {
        viewModelScope.launch {
            repository.updateStudentPhoto(studentId, photoUri)
        }
    }

    // =================================================================
    // SECTION 8: TRANSACTION OPERATIONS (FEE, EXPENSE, BANK DEPOSIT)
    // =================================================================

    fun recordCashToBankDeposit(
        amount: Double,
        bankDetails: String,
        slipNo: String,
        remarks: String = "",
        onSuccess: () -> Unit = {}
    ) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val expVoucher = repository.generateNextVoucherNo(false)
            val recVoucher = repository.generateNextVoucherNo(true)

            // 1. Cash outflow (expense from Counter Cash Box)
            val cashTx = Transaction(
                isIncome = false,
                paymentMode = DomainConstants.MODE_CASH,
                amount = amount,
                category = DomainConstants.CAT_BANK_DEPOSIT,
                description = "Cash deposited into Bank ($bankDetails). Slip #$slipNo. $remarks".trim(),
                voucherNo = expVoucher,
                date = now
            )
            repository.insertTransaction(cashTx)

            // 2. Bank inflow (income to School Bank Account)
            val bankTx = Transaction(
                isIncome = true,
                paymentMode = DomainConstants.MODE_BANK,
                amount = amount,
                category = DomainConstants.CAT_CASH_DEPOSIT,
                description = "Cash received from Counter Cash Box. Slip #$slipNo. $remarks".trim(),
                voucherNo = recVoucher,
                date = now
            )
            repository.insertTransaction(bankTx)

            refreshDailyClosing()
            onSuccess()
        }
    }

    fun recordBankToCashWithdrawal(
        amount: Double,
        bankDetails: String,
        chequeOrRefNo: String,
        remarks: String = "",
        onSuccess: () -> Unit = {}
    ) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val expVoucher = repository.generateNextVoucherNo(false)
            val recVoucher = repository.generateNextVoucherNo(true)

            // 1. Bank outflow (expense from School Bank Account)
            val bankTx = Transaction(
                isIncome = false,
                paymentMode = DomainConstants.MODE_BANK,
                amount = amount,
                category = DomainConstants.CAT_BANK_WITHDRAWAL,
                description = "Bank withdrawal for Counter Cash Box ($bankDetails). Ref #$chequeOrRefNo. $remarks".trim(),
                voucherNo = expVoucher,
                date = now
            )
            repository.insertTransaction(bankTx)

            // 2. Cash inflow (income into Counter Cash Box)
            val cashTx = Transaction(
                isIncome = true,
                paymentMode = DomainConstants.MODE_CASH,
                amount = amount,
                category = DomainConstants.CAT_CASH_FROM_BANK,
                description = "Cash received from Bank Account withdrawal. Ref #$chequeOrRefNo. $remarks".trim(),
                voucherNo = recVoucher,
                date = now
            )
            repository.insertTransaction(cashTx)

            refreshDailyClosing()
            onSuccess()
        }
    }

    fun recordFeePayment(
        student: Student,
        amount: Double,
        mode: String,
        month: String,
        category: String = DomainConstants.CAT_FEE_RECEIVED,
        remarks: String = "",
        onSuccess: (Transaction) -> Unit = {}
    ) {
        viewModelScope.launch {
            val transaction = Transaction(
                isIncome = true,
                studentId = student.id,
                studentName = student.studentName,
                amount = amount,
                category = category,
                paymentMode = mode,
                monthOfFee = month,
                recordedBy = _currentUserRole.value,
                description = remarks
            )
            val newId = repository.insertTransaction(transaction)
            val savedTx = transaction.copy(id = newId.toInt())
            refreshDailyClosing()
            onSuccess(savedTx)
        }
    }

    /**
     * Records initial admission fee payment by allocating across proper financial heads:
     * 1. One-time Admission Fee (CAT_ADMISSION_FEE)
     * 2. First Month Tuition Fee (CAT_FEE_RECEIVED, monthOfFee = curMonth)
     * 3. Annual Session Charges (CAT_ANNUAL_CHARGES)
     * 4. Other / Prospectus Charges (CAT_OTHER_CHARGES)
     * 5. Any excess amount to advance tuition
     */
    fun recordAdmissionInitialFee(
        student: Student,
        totalPaid: Double,
        mode: String,
        month: String,
        onSuccess: (List<Transaction>) -> Unit = {}
    ) {
        viewModelScope.launch {
            if (totalPaid <= 0.0) {
                onSuccess(emptyList())
                return@launch
            }

            val discountedMonthly = DomainConstants.calculateDiscountedFee(student.monthlyFee, student.discountType)
            val admDue = student.admissionFee
            val tutDue = discountedMonthly
            val annDue = student.annualCharges
            val othDue = student.otherCharges

            var remaining = totalPaid
            val transactionsToInsert = mutableListOf<Transaction>()

            // 1. One-Time Admission Fee
            if (admDue > 0.0 && remaining > 0.0) {
                val paidAdm = minOf(remaining, admDue)
                remaining -= paidAdm
                transactionsToInsert.add(
                    Transaction(
                        isIncome = true,
                        studentId = student.id,
                        studentName = student.studentName,
                        amount = paidAdm,
                        category = DomainConstants.CAT_ADMISSION_FEE,
                        paymentMode = mode,
                        monthOfFee = month,
                        recordedBy = _currentUserRole.value,
                        description = "One-time Admission Fee collected at registration"
                    )
                )
            }

            // 2. First Month Tuition Fee
            if (tutDue > 0.0 && remaining > 0.0) {
                val paidTut = minOf(remaining, tutDue)
                remaining -= paidTut
                transactionsToInsert.add(
                    Transaction(
                        isIncome = true,
                        studentId = student.id,
                        studentName = student.studentName,
                        amount = paidTut,
                        category = DomainConstants.CAT_FEE_RECEIVED,
                        paymentMode = mode,
                        monthOfFee = month,
                        recordedBy = _currentUserRole.value,
                        description = "1st Month Tuition Fee collected at admission ($month)"
                    )
                )
            }

            // 3. Annual Session Charges
            if (annDue > 0.0 && remaining > 0.0) {
                val paidAnn = minOf(remaining, annDue)
                remaining -= paidAnn
                transactionsToInsert.add(
                    Transaction(
                        isIncome = true,
                        studentId = student.id,
                        studentName = student.studentName,
                        amount = paidAnn,
                        category = DomainConstants.CAT_ANNUAL_CHARGES,
                        paymentMode = mode,
                        monthOfFee = month,
                        recordedBy = _currentUserRole.value,
                        description = "Annual Session Charges collected at admission"
                    )
                )
            }

            // 4. Other / Prospectus Charges
            if (othDue > 0.0 && remaining > 0.0) {
                val paidOth = minOf(remaining, othDue)
                remaining -= paidOth
                transactionsToInsert.add(
                    Transaction(
                        isIncome = true,
                        studentId = student.id,
                        studentName = student.studentName,
                        amount = paidOth,
                        category = DomainConstants.CAT_OTHER_CHARGES,
                        paymentMode = mode,
                        monthOfFee = month,
                        recordedBy = _currentUserRole.value,
                        description = "Other / Prospectus Charges collected at admission"
                    )
                )
            }

            // 5. Any excess amount
            if (remaining > 0.0) {
                transactionsToInsert.add(
                    Transaction(
                        isIncome = true,
                        studentId = student.id,
                        studentName = student.studentName,
                        amount = remaining,
                        category = DomainConstants.CAT_FEE_RECEIVED,
                        paymentMode = mode,
                        monthOfFee = month,
                        recordedBy = _currentUserRole.value,
                        description = "Advance fee payment collected at admission"
                    )
                )
            }

            val insertedList = mutableListOf<Transaction>()
            transactionsToInsert.forEach { tx ->
                val newId = repository.insertTransaction(tx)
                insertedList.add(tx.copy(id = newId.toInt()))
            }

            refreshDailyClosing()
            onSuccess(insertedList)
        }
    }

    fun recordBulkFeePayments(
        payments: List<Triple<Student, Double, String>>,
        mode: String,
        remarks: String = "Bulk receipt processing",
        onSuccess: () -> Unit = {}
    ) {
        viewModelScope.launch {
            payments.forEach { (student, amount, month) ->
                val transaction = Transaction(
                    isIncome = true,
                    studentId = student.id,
                    studentName = student.studentName,
                    amount = amount,
                    category = DomainConstants.CAT_FEE_RECEIVED,
                    paymentMode = mode,
                    monthOfFee = month,
                    recordedBy = _currentUserRole.value,
                    description = remarks
                )
                repository.insertTransaction(transaction)
            }
            refreshDailyClosing()
            onSuccess()
        }
    }

    fun addExpense(
        category: String,
        amount: Double,
        mode: String,
        desc: String
    ) {
        viewModelScope.launch {
            repository.insertTransaction(
                Transaction(
                    isIncome = false,
                    amount = amount,
                    category = category,
                    paymentMode = mode,
                    recordedBy = _currentUserRole.value,
                    description = desc
                )
            )
            refreshDailyClosing()
        }
    }

    fun deleteTransaction(transaction: Transaction) {
        viewModelScope.launch {
            if (_currentUserRole.value == DomainConstants.ROLE_ADMIN) {
                repository.deleteTransaction(transaction)
                refreshDailyClosing()
            }
        }
    }

    fun restoreTransaction(transaction: Transaction) {
        viewModelScope.launch {
            if (_currentUserRole.value == DomainConstants.ROLE_ADMIN) {
                repository.insertTransaction(transaction)
                refreshDailyClosing()
            }
        }
    }

    fun recordStaffSalary(
        staffName: String,
        amount: Double,
        month: String,
        mode: String,
        remarks: String = "",
        onSuccess: (Transaction) -> Unit = {}
    ) {
        viewModelScope.launch {
            val desc = if (remarks.isNotBlank()) "Salary to $staffName for $month ($remarks)" else "Salary to $staffName for $month"
            val tx = Transaction(
                isIncome = false,
                amount = amount,
                category = DomainConstants.CAT_STAFF_SALARY,
                paymentMode = mode,
                recordedBy = _currentUserRole.value,
                description = desc,
                monthOfFee = month
            )
            val newId = repository.insertTransaction(tx)
            val savedTx = tx.copy(id = newId.toInt())
            refreshDailyClosing()
            onSuccess(savedTx)
        }
    }

    // =================================================================
    // SECTION 9: BULK OPERATIONS & VOUCHER PREVIEW
    // =================================================================

    fun bulkPromoteClass(currentClass: String, nextClass: String, onComplete: (Int) -> Unit) {
        viewModelScope.launch {
            val count = repository.bulkPromoteClass(currentClass, nextClass)
            refreshDailyClosing()
            onComplete(count)
        }
    }

    fun bulkPromoteClassWithSessionRollover(
        currentClass: String,
        nextClass: String,
        applyAnnualCharges: Boolean,
        annualChargesAmount: Double?,
        onComplete: (Int) -> Unit
    ) {
        viewModelScope.launch {
            val studentsInClass = repository.getAllStudents().filter { 
                it.className.equals(currentClass, ignoreCase = true) && !it.isDeleted 
            }
            val updated = studentsInClass.map { s ->
                val newAnnual = if (applyAnnualCharges && annualChargesAmount != null && annualChargesAmount > 0.0) {
                    annualChargesAmount
                } else s.annualCharges
                s.copy(className = nextClass, annualCharges = newAnnual)
            }
            updated.forEach { repository.updateStudent(it) }
            refreshDailyClosing()
            onComplete(updated.size)
        }
    }

    fun applyAnnualChargesSessionRollover(
        targetClass: String?,
        annualChargesAmount: Double?,
        onComplete: (Int) -> Unit
    ) {
        viewModelScope.launch {
            val allStudents = repository.getAllStudents().filter { 
                it.status.equals(DomainConstants.STATUS_ACTIVE, ignoreCase = true) && !it.isDeleted 
            }
            val targets = if (targetClass == null || targetClass == "All Classes") {
                allStudents
            } else {
                allStudents.filter { it.className.equals(targetClass, ignoreCase = true) }
            }
            val updated = targets.map { s ->
                val newAnnual = if (annualChargesAmount != null && annualChargesAmount > 0.0) annualChargesAmount else s.annualCharges
                s.copy(annualCharges = newAnnual)
            }
            updated.forEach { repository.updateStudent(it) }
            refreshDailyClosing()
            onComplete(updated.size)
        }
    }

    fun getNextVoucherPreview(isIncome: Boolean, onResult: (String) -> Unit) {
        viewModelScope.launch {
            val vNo = repository.generateNextVoucherNo(isIncome)
            onResult(vNo)
        }
    }

    // =================================================================
    // SECTION 10: BACKUP, RESTORE & DATA EXPORT
    // =================================================================

    suspend fun exportDatabaseToJson(): String {
        val root = JSONObject()
        root.put("version", 5)
        root.put("exportTimestamp", System.currentTimeMillis())
        root.put("schoolName", "Bright Light School")

        val sArray = JSONArray()
        val allStudents = repository.getAllStudents()
        allStudents.forEach { s ->
            sArray.put(JSONObject().apply {
                put("id", s.id)
                put("regNo", s.regNo)
                put("studentName", s.studentName)
                put("fatherName", s.fatherName)
                put("className", s.className)
                put("contactNumber", s.contactNumber)
                put("admissionFee", s.admissionFee)
                put("monthlyFee", s.monthlyFee)
                put("annualCharges", s.annualCharges)
                put("discountType", s.discountType)
                put("otherCharges", s.otherCharges)
                put("admissionDate", s.admissionDate)
                put("idCardNumber", s.idCardNumber)
                put("previousSchoolName", s.previousSchoolName)
                put("fatherIdCardNumber", s.fatherIdCardNumber)
                put("schoolCertAttached", s.schoolCertAttached)
                put("formBAttached", s.formBAttached)
                put("fatherCnicAttached", s.fatherCnicAttached)
                put("picsAttached", s.picsAttached)
                put("status", s.status)
                put("photoUri", s.photoUri)
            })
        }
        root.put("students", sArray)

        val tArray = JSONArray()
        val allTx = repository.getAllTransactions()
        allTx.forEach { tx ->
            tArray.put(JSONObject().apply {
                put("id", tx.id)
                put("isIncome", tx.isIncome)
                put("studentId", tx.studentId ?: JSONObject.NULL)
                put("studentName", tx.studentName ?: JSONObject.NULL)
                put("amount", tx.amount)
                put("category", tx.category)
                put("paymentMode", tx.paymentMode)
                put("date", tx.date)
                put("monthOfFee", tx.monthOfFee ?: JSONObject.NULL)
                put("recordedBy", tx.recordedBy)
                put("description", tx.description)
                put("voucherNo", tx.voucherNo)
            })
        }
        root.put("transactions", tArray)

        val cArray = JSONArray()
        val allClosings = dailyClosingsList.value
        allClosings.forEach { c ->
            cArray.put(JSONObject().apply {
                put("dateString", c.dateString)
                put("openingCash", c.openingCash)
                put("openingBank", c.openingBank)
                put("totalIncomeCash", c.totalIncomeCash)
                put("totalIncomeBank", c.totalIncomeBank)
                put("totalExpenseCash", c.totalExpenseCash)
                put("totalExpenseBank", c.totalExpenseBank)
                put("closingCash", c.closingCash)
                put("closingBank", c.closingBank)
                put("isClosed", c.isClosed)
                put("closedBy", c.closedBy)
            })
        }
        root.put("dailyClosings", cArray)

        return root.toString(2)
    }

    fun importDatabaseFromJson(jsonString: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            try {
                val root = JSONObject(jsonString)
                val sList = mutableListOf<Student>()
                val sArray = root.optJSONArray("students")
                if (sArray != null) {
                    for (i in 0 until sArray.length()) {
                        val obj = sArray.getJSONObject(i)
                        sList.add(
                            Student(
                                id = obj.optInt("id", 0),
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
                                photoUri = obj.optString("photoUri", "")
                            )
                        )
                    }
                }

                val tList = mutableListOf<Transaction>()
                val tArray = root.optJSONArray("transactions")
                if (tArray != null) {
                    for (i in 0 until tArray.length()) {
                        val obj = tArray.getJSONObject(i)
                        tList.add(
                            Transaction(
                                id = obj.optInt("id", 0),
                                isIncome = obj.optBoolean("isIncome", true),
                                studentId = if (obj.has("studentId") && !obj.isNull("studentId")) obj.optInt("studentId") else null,
                                studentName = if (obj.has("studentName") && !obj.isNull("studentName")) obj.optString("studentName") else null,
                                amount = obj.optDouble("amount", 0.0),
                                category = obj.optString("category", ""),
                                paymentMode = obj.optString("paymentMode", "Cash"),
                                date = obj.optLong("date", System.currentTimeMillis()),
                                monthOfFee = if (obj.has("monthOfFee") && !obj.isNull("monthOfFee")) obj.optString("monthOfFee") else null,
                                recordedBy = obj.optString("recordedBy", "Staff"),
                                description = obj.optString("description", ""),
                                voucherNo = obj.optString("voucherNo", "")
                            )
                        )
                    }
                }

                val cList = mutableListOf<DailyClosing>()
                val cArray = root.optJSONArray("dailyClosings")
                if (cArray != null) {
                    for (i in 0 until cArray.length()) {
                        val obj = cArray.getJSONObject(i)
                        cList.add(
                            DailyClosing(
                                dateString = obj.optString("dateString", ""),
                                openingCash = obj.optDouble("openingCash", 0.0),
                                openingBank = obj.optDouble("openingBank", 0.0),
                                totalIncomeCash = obj.optDouble("totalIncomeCash", 0.0),
                                totalIncomeBank = obj.optDouble("totalIncomeBank", 0.0),
                                totalExpenseCash = obj.optDouble("totalExpenseCash", 0.0),
                                totalExpenseBank = obj.optDouble("totalExpenseBank", 0.0),
                                closingCash = obj.optDouble("closingCash", 0.0),
                                closingBank = obj.optDouble("closingBank", 0.0),
                                isClosed = obj.optBoolean("isClosed", false),
                                closedBy = obj.optString("closedBy", "")
                            )
                        )
                    }
                }

                repository.restoreDatabase(sList, tList, cList)
                refreshDailyClosing()
                onResult(true, "Restored ${sList.size} students and ${tList.size} transactions successfully!")
            } catch (e: Exception) {
                onResult(false, "Restore failed: ${e.message}")
            }
        }
    }

    // =================================================================
    // SECTION 8: GITHUB IN-APP AUTO-UPDATE SYSTEM
    // =================================================================

    private val _appUpdateInfo = MutableStateFlow<com.example.util.AppUpdateInfo?>(null)
    val appUpdateInfo: StateFlow<com.example.util.AppUpdateInfo?> = _appUpdateInfo.asStateFlow()

    private val _isCheckingUpdate = MutableStateFlow(false)
    val isCheckingUpdate: StateFlow<Boolean> = _isCheckingUpdate.asStateFlow()

    private val _downloadProgress = MutableStateFlow<Float?>(null)
    val downloadProgress: StateFlow<Float?> = _downloadProgress.asStateFlow()

    private val _downloadedApkFile = MutableStateFlow<java.io.File?>(null)
    val downloadedApkFile: StateFlow<java.io.File?> = _downloadedApkFile.asStateFlow()

    fun checkForAppUpdates(currentVersion: String = com.example.BuildConfig.VERSION_NAME, silent: Boolean = false, onComplete: ((Boolean, String) -> Unit)? = null) {
        viewModelScope.launch {
            _isCheckingUpdate.value = true
            val result = com.example.util.GitHubUpdateManager.checkForUpdate(currentVersion)
            _isCheckingUpdate.value = false
            result.onSuccess { info ->
                if (info.isUpdateAvailable) {
                    _appUpdateInfo.value = info
                    onComplete?.invoke(true, "Update found: v${info.latestVersion}")
                } else {
                    if (!silent) {
                        onComplete?.invoke(false, "App is up to date (v$currentVersion).")
                    }
                }
            }.onFailure { err ->
                if (!silent) {
                    onComplete?.invoke(false, "Could not check updates: ${err.message}")
                }
            }
        }
    }

    fun dismissUpdateDialog() {
        _appUpdateInfo.value = null
        _downloadProgress.value = null
        _downloadedApkFile.value = null
    }

    fun downloadAndInstallUpdate(context: android.content.Context, info: com.example.util.AppUpdateInfo) {
        viewModelScope.launch {
            _downloadProgress.value = 0.01f
            val file = com.example.util.GitHubUpdateManager.downloadApk(
                context = context,
                downloadUrl = info.downloadUrl,
                targetVersion = info.latestVersion,
                onProgress = { p ->
                    _downloadProgress.value = p
                }
            )
            if (file != null) {
                _downloadProgress.value = 1.0f
                _downloadedApkFile.value = file
                com.example.util.GitHubUpdateManager.installApk(context, file)
            } else {
                _downloadProgress.value = null
                android.widget.Toast.makeText(context, "Download failed. Please check network connection.", android.widget.Toast.LENGTH_LONG).show()
            }
        }
    }

    fun installDownloadedApk(context: android.content.Context) {
        val file = _downloadedApkFile.value
        if (file != null) {
            com.example.util.GitHubUpdateManager.installApk(context, file)
        }
    }
}

// =====================================================================
// DATA PROJECTION MODELS WITH MINIMALIST UI FORMATTING HELPERS
// =====================================================================

data class PendingFeeAlert(
    val studentId: Int,
    val studentName: String,
    val fatherName: String = "",
    val className: String,
    val pendingAmount: Double,
    val currentMonthPending: Double = pendingAmount,
    val arrearsAmount: Double = 0.0,
    val totalPendingAmount: Double = pendingAmount,
    val month: String,
    val contactNumber: String,
    val pendingMonthsList: List<String> = emptyList(),
    val unpaidAdmissionFee: Double = 0.0,
    val unpaidAnnualCharges: Double = 0.0,
    val unpaidOtherCharges: Double = 0.0
) {
    val hasArrears: Boolean get() = arrearsAmount > 0.0
    val isMultiMonth: Boolean get() = pendingMonthsList.size > 1
    val formattedPendingAmount: String get() = String.format(Locale.US, "%,.0f", pendingAmount)
}

data class FinancialMetrics(
    val totalCashIncome: Double = 0.0,
    val totalBankIncome: Double = 0.0,
    val totalCashExpense: Double = 0.0,
    val totalBankExpense: Double = 0.0,
    val cashBalance: Double = 0.0,
    val bankBalance: Double = 0.0,
    val totalIncome: Double = 0.0,
    val totalExpense: Double = 0.0,
    val netBalance: Double = 0.0
) {
    val isNetPositive: Boolean get() = netBalance >= 0.0
    val formattedNetBalance: String get() = String.format(Locale.US, "%,.0f", netBalance)
    val formattedCashBalance: String get() = String.format(Locale.US, "%,.0f", cashBalance)
    val formattedBankBalance: String get() = String.format(Locale.US, "%,.0f", bankBalance)
}
