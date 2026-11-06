package com.example.ui.ledger

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.DomainConstants
import com.example.data.Student
import com.example.data.Transaction
import com.example.ui.AppViewModel
import com.example.ui.theme.financialColors
import com.example.util.CurrencyVisualTransformation
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReceiveFeeDialogDirect(
    viewModel: AppViewModel,
    onDismiss: () -> Unit,
    onConfirm: (Student, Double, String, String, String, String, Double) -> Unit
) {
    val students by viewModel.studentsList.collectAsStateWithLifecycle()
    val allTx by viewModel.transactionsList.collectAsStateWithLifecycle()

    var studentQuery by remember { mutableStateOf("") }
    var selectedStudentDirect by remember { mutableStateOf<Student?>(null) }

    var feeCategory by remember { mutableStateOf(DomainConstants.CAT_FEE_RECEIVED) }
    var categoryExpanded by remember { mutableStateOf(false) }

    val currentDayOfMonth = remember { Calendar.getInstance().get(Calendar.DAY_OF_MONTH) }
    val isAfter8th = currentDayOfMonth > 8
    var amountInput by remember { mutableStateOf("") }
    var lateFeeFine by remember { mutableStateOf(if (isAfter8th) "300" else "") }
    var selectedMonth by remember { mutableStateOf("") }
    var selectedMonthExpanded by remember { mutableStateOf(false) }
    var paymentMode by remember { mutableStateOf(DomainConstants.MODE_CASH) }
    var remarks by remember { mutableStateOf("") }

    val categoriesMap = mapOf(
        "Monthly Tuition Fee" to DomainConstants.CAT_FEE_RECEIVED,
        "Admission Fee" to DomainConstants.CAT_ADMISSION_FEE,
        "Annual Charges" to DomainConstants.CAT_ANNUAL_CHARGES,
        "Other Charges" to DomainConstants.CAT_OTHER_CHARGES,
        "Exam Fee / Paper Charges" to DomainConstants.CAT_EXAM_FEE
    )
    val displayCategories: List<String> = categoriesMap.keys.toList()

    val filteredList = remember(studentQuery, students) {
        if (studentQuery.isBlank()) emptyList<Student>() else {
            students.filter { s: Student ->
                s.studentName.contains(studentQuery, ignoreCase = true) ||
                s.regNo.contains(studentQuery, ignoreCase = true) ||
                s.fatherName.contains(studentQuery, ignoreCase = true)
            }.take(5)
        }
    }

    val monthsList = remember {
        val list = mutableListOf<String>()
        val cal = Calendar.getInstance()
        val mSdf = SimpleDateFormat("MMMM yyyy", Locale.US)

        cal.add(Calendar.MONTH, -3)
        for (i in 0..6) {
            list.add(mSdf.format(cal.time))
            cal.add(Calendar.MONTH, 1)
        }
        list
    }

    LaunchedEffect(Unit) {
        selectedMonth = SimpleDateFormat("MMMM yyyy", Locale.US).format(Date())
    }

    fun updatePreloadedAmount(student: Student, categoryDisplayName: String) {
        val mappedCategory = categoriesMap[categoryDisplayName] ?: DomainConstants.CAT_FEE_RECEIVED
        val amt = when (mappedCategory) {
            DomainConstants.CAT_FEE_RECEIVED -> {
                val sTx = allTx.filter { tx: Transaction -> tx.studentId == student.id }
                val discounted = DomainConstants.calculateDiscountedFee(student.monthlyFee, student.discountType)
                val arrears = DomainConstants.calculateMultiMonthArrears(student.admissionDate, discounted, sTx)
                discounted + arrears
            }
            DomainConstants.CAT_ADMISSION_FEE -> student.admissionFee
            DomainConstants.CAT_ANNUAL_CHARGES -> student.annualCharges
            DomainConstants.CAT_OTHER_CHARGES -> student.otherCharges
            else -> 0.0
        }
        amountInput = if (amt > 0) amt.toInt().toString() else ""
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        modifier = Modifier.imePadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(MaterialTheme.financialColors.cashContainer, RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.AddCard, contentDescription = null, tint = MaterialTheme.financialColors.cash, modifier = Modifier.size(22.dp))
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text("Receive Fee & Income", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                        Text("Student tuition receipt & revenue entry", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            // Body
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                if (selectedStudentDirect == null) {
                    Text("Select Registered Student *", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    OutlinedTextField(
                        value = studentQuery,
                        onValueChange = { studentQuery = it },
                        placeholder = { Text("Search by student name, father name, or roll #") },
                        modifier = Modifier.fillMaxWidth(),
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        shape = RoundedCornerShape(12.dp)
                    )

                    if (filteredList.isNotEmpty()) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column {
                                filteredList.forEach { s: Student ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                selectedStudentDirect = s
                                                updatePreloadedAmount(s, "Monthly Tuition Fee")
                                            }
                                            .padding(14.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text(s.studentName, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                            Text("Father: ${s.fatherName} | Class: ${s.className}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        if (s.regNo.isNotEmpty()) {
                                            Box(
                                                modifier = Modifier
                                                    .background(MaterialTheme.financialColors.cashContainer, shape = RoundedCornerShape(6.dp))
                                                    .border(1.dp, MaterialTheme.financialColors.cash.copy(alpha = 0.4f), shape = RoundedCornerShape(6.dp))
                                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                                            ) {
                                                Text(s.regNo, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.financialColors.cash)
                                            }
                                        }
                                    }
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                                }
                            }
                        }
                    } else if (studentQuery.isNotBlank()) {
                        Text("No matching students found.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.financialColors.expense)
                    }
                } else {
                    val s = selectedStudentDirect!!
                    val studentTx = remember(s, allTx) { allTx.filter { it.studentId == s.id } }
                    val baseTuition = remember(s) { DomainConstants.calculateDiscountedFee(s.monthlyFee, s.discountType) }
                    val multiMonthArrears = remember(s, studentTx) {
                        DomainConstants.calculateMultiMonthArrears(s.admissionDate, baseTuition, studentTx)
                    }

                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(s.studentName.uppercase(), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                                Text("Class: ${s.className} | Father: ${s.fatherName}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            TextButton(onClick = { selectedStudentDirect = null }) {
                                Text("Change", color = MaterialTheme.financialColors.expense, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    // Arrears & Tuition Breakdown Card
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (multiMonthArrears > 0) MaterialTheme.financialColors.expenseContainer.copy(alpha = 0.35f) else MaterialTheme.financialColors.cashContainer.copy(alpha = 0.35f)
                        ),
                        border = BorderStroke(
                            1.dp,
                            if (multiMonthArrears > 0) MaterialTheme.financialColors.expense.copy(alpha = 0.4f) else MaterialTheme.financialColors.cash.copy(alpha = 0.4f)
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Monthly Tuition Fee:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("Rs. ${baseTuition.toInt()}", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                            }
                            if (multiMonthArrears > 0) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("⚠️ Previous Unpaid Arrears:", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = MaterialTheme.financialColors.expense)
                                    Text("Rs. ${multiMonthArrears.toInt()}", fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.financialColors.expense)
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Total Outstanding Due:", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                    Text("Rs. ${(baseTuition + multiMonthArrears).toInt()}", fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.financialColors.expense)
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = { amountInput = (baseTuition + multiMonthArrears).toInt().toString() },
                                        modifier = Modifier.weight(1f).height(34.dp),
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text("Total (Fee + Arrears)", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                    OutlinedButton(
                                        onClick = { amountInput = baseTuition.toInt().toString() },
                                        modifier = Modifier.weight(1f).height(34.dp),
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text("Tuition Only", fontSize = 11.sp)
                                    }
                                }
                            } else {
                                Text("✅ All past dues cleared. Standard monthly billing.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.financialColors.cash)
                            }
                        }
                    }

                    var displayCategoryText by remember { mutableStateOf("Monthly Tuition Fee") }
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = displayCategoryText,
                            onValueChange = {},
                            label = { Text("Fee Category Type *") },
                            readOnly = true,
                            trailingIcon = {
                                IconButton(onClick = { categoryExpanded = !categoryExpanded }) {
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )
                        DropdownMenu(
                            expanded = categoryExpanded,
                            onDismissRequest = { categoryExpanded = false },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            displayCategories.forEach { dCat: String ->
                                DropdownMenuItem(
                                    text = { Text(text = dCat) },
                                    onClick = {
                                        displayCategoryText = dCat
                                        feeCategory = categoriesMap[dCat] ?: DomainConstants.CAT_FEE_RECEIVED
                                        categoryExpanded = false
                                        updatePreloadedAmount(s, dCat)
                                    }
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = amountInput,
                        onValueChange = { amountInput = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Collect Amount (Rs) *") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        visualTransformation = CurrencyVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )

                    val enteredAmt = amountInput.toDoubleOrNull() ?: 0.0
                    val expectedAmt = if (feeCategory == DomainConstants.CAT_FEE_RECEIVED) (baseTuition + multiMonthArrears) else enteredAmt
                    if (feeCategory == DomainConstants.CAT_FEE_RECEIVED && enteredAmt > 0) {
                        if (enteredAmt < expectedAmt) {
                            val remaining = expectedAmt - enteredAmt
                            Surface(
                                color = MaterialTheme.financialColors.amberContainer.copy(alpha = 0.45f),
                                border = BorderStroke(1.dp, MaterialTheme.financialColors.amber.copy(alpha = 0.5f)),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.financialColors.amber, modifier = Modifier.size(16.dp))
                                    Text(
                                        "⚠️ Partial Payment: Rs. ${remaining.toInt()} will be carried forward as Arrears.",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.financialColors.amber
                                    )
                                }
                            }
                        } else if (enteredAmt == expectedAmt) {
                            Surface(
                                color = MaterialTheme.financialColors.cashContainer.copy(alpha = 0.45f),
                                border = BorderStroke(1.dp, MaterialTheme.financialColors.cash.copy(alpha = 0.5f)),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.financialColors.cash, modifier = Modifier.size(16.dp))
                                    Text(
                                        "✅ Full Clearance: Current tuition and past arrears cleared.",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.financialColors.cash
                                    )
                                }
                            }
                        }
                    }

                    if (isAfter8th) {
                        Surface(
                            color = MaterialTheme.financialColors.amberContainer.copy(alpha = 0.45f),
                            border = BorderStroke(1.dp, MaterialTheme.financialColors.amber.copy(alpha = 0.5f)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.Schedule, contentDescription = null, tint = MaterialTheme.financialColors.amber, modifier = Modifier.size(18.dp))
                                    Text(
                                        "Due date (08th) has passed. Automatic Rs. 300 late fee surcharge is applicable.",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.financialColors.amber,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                                TextButton(
                                    onClick = {
                                        lateFeeFine = if (lateFeeFine.isNotBlank() && lateFeeFine != "0") "" else "300"
                                    }
                                ) {
                                    Text(
                                        if (lateFeeFine.isNotBlank() && lateFeeFine != "0") "Waive Fine" else "Apply Fine",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }

                    OutlinedTextField(
                        value = lateFeeFine,
                        onValueChange = { lateFeeFine = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Late Fee / Fine (Rs) - Surcharge after 8th") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        visualTransformation = CurrencyVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )

                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = selectedMonth,
                            onValueChange = {},
                            label = { Text("Fee Month Period") },
                            readOnly = true,
                            trailingIcon = {
                                IconButton(onClick = { selectedMonthExpanded = !selectedMonthExpanded }) {
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )
                        DropdownMenu(
                            expanded = selectedMonthExpanded,
                            onDismissRequest = { selectedMonthExpanded = false },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            monthsList.forEach { m: String ->
                                DropdownMenuItem(
                                    text = { Text(text = m) },
                                    onClick = { selectedMonth = m; selectedMonthExpanded = false }
                                )
                            }
                        }
                    }

                    Text("Deposit In Ledger:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Card(
                            border = BorderStroke(1.dp, if (paymentMode == DomainConstants.MODE_CASH) MaterialTheme.financialColors.cash else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                            modifier = Modifier
                                .weight(1f)
                                .clickable { paymentMode = DomainConstants.MODE_CASH },
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = if (paymentMode == DomainConstants.MODE_CASH) MaterialTheme.financialColors.cashContainer.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surface)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(Icons.Default.Payments, contentDescription = null, tint = if (paymentMode == DomainConstants.MODE_CASH) MaterialTheme.financialColors.cash else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Cash Box", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = if (paymentMode == DomainConstants.MODE_CASH) MaterialTheme.financialColors.cash else MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }

                        Card(
                            border = BorderStroke(1.dp, if (paymentMode == DomainConstants.MODE_BANK) MaterialTheme.financialColors.bank else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                            modifier = Modifier
                                .weight(1f)
                                .clickable { paymentMode = DomainConstants.MODE_BANK },
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = if (paymentMode == DomainConstants.MODE_BANK) MaterialTheme.financialColors.bankContainer.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surface)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(Icons.Default.AccountBalance, contentDescription = null, tint = if (paymentMode == DomainConstants.MODE_BANK) MaterialTheme.financialColors.bank else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Bank", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = if (paymentMode == DomainConstants.MODE_BANK) MaterialTheme.financialColors.bank else MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }

                    OutlinedTextField(
                        value = remarks,
                        onValueChange = { remarks = it },
                        label = { Text("Receipt Memo Remarks") },
                        placeholder = { Text("e.g. Paid in full via parent") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))

            // Footer
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                ) {
                    Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
                }

                val canConfirm = selectedStudentDirect != null && (amountInput.toDoubleOrNull() ?: 0.0) > 0
                Button(
                    onClick = {
                        val targetStudent = selectedStudentDirect
                        val baseAmt = amountInput.toDoubleOrNull() ?: 0.0
                        val fineAmt = lateFeeFine.toDoubleOrNull() ?: 0.0
                        val totalAmt = baseAmt + fineAmt
                        val finalRemarks = if (fineAmt > 0) {
                            if (remarks.isNotBlank()) "$remarks [Includes Late Fine: Rs. ${fineAmt.toInt()}]" else "Includes Late Fine: Rs. ${fineAmt.toInt()}"
                        } else remarks

                        val remainingArrears = if (feeCategory == DomainConstants.CAT_FEE_RECEIVED) {
                            val sTx = allTx.filter { it.studentId == targetStudent?.id }
                            val discounted = DomainConstants.calculateDiscountedFee(targetStudent?.monthlyFee ?: 0.0, targetStudent?.discountType)
                            val pastArrears = DomainConstants.calculateMultiMonthArrears(targetStudent?.admissionDate ?: 0L, discounted, sTx)
                            val totalOwed = discounted + pastArrears
                            maxOf(0.0, totalOwed - baseAmt)
                        } else 0.0

                        if (targetStudent != null && totalAmt > 0 && selectedMonth.isNotBlank()) {
                            onConfirm(targetStudent, totalAmt, feeCategory, paymentMode, selectedMonth, finalRemarks, remainingArrears)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.financialColors.cash),
                    shape = RoundedCornerShape(10.dp),
                    enabled = canConfirm,
                    modifier = Modifier
                        .weight(1.5f)
                        .height(42.dp)
                ) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.White)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Confirm & Record", color = Color.White, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
