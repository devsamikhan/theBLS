package com.example.ui.students

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.DomainConstants
import com.example.data.Student
import com.example.data.Transaction
import com.example.ui.theme.financialColors
import com.example.util.CurrencyVisualTransformation
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeePaymentDialog(
    student: Student,
    transactions: List<Transaction> = emptyList(),
    onDismiss: () -> Unit,
    onConfirm: (Double, String, String, String) -> Unit
) {
    val financialColors = MaterialTheme.financialColors
    val advanceWallet = remember(student, transactions) {
        DomainConstants.calculateStudentAdvanceWallet(student, transactions)
    }
    val initialAmount = remember(student) {
        val discounted = DomainConstants.calculateDiscountedFee(student.monthlyFee, student.discountType)
        discounted.toInt().toString()
    }
    val currentDayOfMonth = remember { Calendar.getInstance().get(Calendar.DAY_OF_MONTH) }
    val isAfter8th = currentDayOfMonth > 8
    var feeAmount by remember { mutableStateOf(initialAmount) }
    var lateFeeFine by remember { mutableStateOf(if (isAfter8th) "300" else "") }
    var selectedMonth by remember { mutableStateOf("") }
    var selectedMonthExpanded by remember { mutableStateOf(false) }
    var paymentMode by remember { mutableStateOf(DomainConstants.MODE_CASH) }
    var remarks by remember { mutableStateOf("") }

    val discountedExpected = remember(student) {
        DomainConstants.calculateDiscountedFee(student.monthlyFee, student.discountType)
    }
    val enteredFee = feeAmount.toDoubleOrNull() ?: 0.0
    val remainingFee = discountedExpected - enteredFee

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
        val currentMonthYear = SimpleDateFormat("MMMM yyyy", Locale.US).format(Date())
        selectedMonth = currentMonthYear
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        modifier = Modifier.imePadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
        ) {
            // Header with Student Name + Reg + Close
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = student.studentName,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (student.regNo.isNotBlank()) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = MaterialTheme.colorScheme.primaryContainer,
                                border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.primary)
                            ) {
                                Text(
                                    text = student.regNo,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                    Text(
                        "Class: ${student.className} • Father: ${student.fatherName}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            // Scrollable Content
            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Live breakdown card
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = when {
                            enteredFee == 0.0 -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                            remainingFee > 0.0 -> financialColors.amberContainer.copy(alpha = 0.5f)
                            remainingFee == 0.0 -> financialColors.cashContainer.copy(alpha = 0.6f)
                            else -> financialColors.bankContainer.copy(alpha = 0.5f)
                        }
                    ),
                    border = BorderStroke(
                        1.dp,
                        when {
                            enteredFee == 0.0 -> MaterialTheme.colorScheme.outlineVariant
                            remainingFee > 0.0 -> financialColors.amber.copy(alpha = 0.5f)
                            remainingFee == 0.0 -> financialColors.cash.copy(alpha = 0.5f)
                            else -> financialColors.bank.copy(alpha = 0.5f)
                        }
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = when {
                                    enteredFee == 0.0 -> "Expected Tuition"
                                    remainingFee > 0.0 -> "⚠️ Partial Installment"
                                    remainingFee == 0.0 -> "✅ Full Tuition Payment"
                                    else -> "ℹ️ Advance / Excess Payment"
                                },
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.labelMedium,
                                color = when {
                                    enteredFee == 0.0 -> MaterialTheme.colorScheme.onSurface
                                    remainingFee > 0.0 -> financialColors.amber
                                    remainingFee == 0.0 -> financialColors.cash
                                    else -> financialColors.bank
                                }
                            )
                            Text(
                                "Monthly: Rs. ${discountedExpected.toInt()}",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (remainingFee > 0.0 && enteredFee > 0.0) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "Remaining Balance: Rs. ${remainingFee.toInt()} will be recorded as Arrears.",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                color = financialColors.expense
                            )
                        }
                    }
                }

                // Advance Wallet Credit Box
                if (advanceWallet > 0.0) {
                    Surface(
                        color = financialColors.cashContainer.copy(alpha = 0.6f),
                        border = BorderStroke(1.dp, financialColors.cash.copy(alpha = 0.4f)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.Default.AccountBalanceWallet, contentDescription = null, tint = financialColors.cash, modifier = Modifier.size(16.dp))
                                Column {
                                    Text("Advance Wallet Credit Available", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = financialColors.cash)
                                    Text("Surplus: Rs. ${advanceWallet.toInt()}", fontSize = 10.sp, color = financialColors.cash)
                                }
                            }
                            TextButton(
                                onClick = {
                                    val netDue = maxOf(0.0, discountedExpected - advanceWallet)
                                    feeAmount = netDue.toInt().toString()
                                    val applied = minOf(discountedExpected, advanceWallet).toInt()
                                    remarks = "Applied Rs. $applied advance wallet credit"
                                }
                            ) {
                                Text("Apply Credit", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = financialColors.cash)
                            }
                        }
                    }
                }

                // Amount Field
                OutlinedTextField(
                    value = feeAmount,
                    onValueChange = { feeAmount = it.filter { ch -> ch.isDigit() } },
                    label = { Text("Payment Fee Amount (Rs) *") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    visualTransformation = CurrencyVisualTransformation(),
                    trailingIcon = {
                        TextButton(onClick = { feeAmount = discountedExpected.toInt().toString() }) {
                            Text("Full Fee", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }
                    },
                    modifier = Modifier.fillMaxWidth().testTag("fee_amt_input")
                )

                // Late fee / fine with 8th Cutoff & Waive toggle
                if (isAfter8th) {
                    Surface(
                        color = financialColors.amberContainer.copy(alpha = 0.5f),
                        border = BorderStroke(1.dp, financialColors.amber.copy(alpha = 0.4f)),
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
                                Icon(Icons.Default.Schedule, contentDescription = null, tint = financialColors.amber, modifier = Modifier.size(18.dp))
                                Text(
                                    "Due date (08th) has passed. Automatic Rs. 300 late fee surcharge is applicable.",
                                    fontSize = 11.sp,
                                    color = financialColors.amber,
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
                    modifier = Modifier.fillMaxWidth()
                )

                // Month Dropdown
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = selectedMonth,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Fee For Month *") },
                        trailingIcon = {
                            IconButton(onClick = { selectedMonthExpanded = !selectedMonthExpanded }) {
                                Icon(Icons.Default.ArrowDropDown, contentDescription = "Select Month")
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    DropdownMenu(
                        expanded = selectedMonthExpanded,
                        onDismissRequest = { selectedMonthExpanded = false },
                        modifier = Modifier.fillMaxWidth(0.85f)
                    ) {
                        monthsList.forEach { m ->
                            DropdownMenuItem(
                                text = { Text(m) },
                                onClick = {
                                    selectedMonth = m
                                    selectedMonthExpanded = false
                                }
                            )
                        }
                    }
                }

                // Payment Mode Toggle: Cash vs Bank
                Text("Payment Channel", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Card(
                        border = BorderStroke(1.5.dp, if (paymentMode == DomainConstants.MODE_CASH) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier
                            .weight(1f)
                            .clickable { paymentMode = DomainConstants.MODE_CASH },
                        colors = CardDefaults.cardColors(containerColor = if (paymentMode == DomainConstants.MODE_CASH) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(Icons.Default.Payments, contentDescription = null, tint = if (paymentMode == DomainConstants.MODE_CASH) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Cash", fontWeight = FontWeight.Bold, color = if (paymentMode == DomainConstants.MODE_CASH) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    Card(
                        border = BorderStroke(1.5.dp, if (paymentMode == DomainConstants.MODE_BANK) financialColors.bank else MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier
                            .weight(1f)
                            .clickable { paymentMode = DomainConstants.MODE_BANK },
                        colors = CardDefaults.cardColors(containerColor = if (paymentMode == DomainConstants.MODE_BANK) financialColors.bankContainer else MaterialTheme.colorScheme.surface)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(Icons.Default.AccountBalance, contentDescription = null, tint = if (paymentMode == DomainConstants.MODE_BANK) financialColors.bank else MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Bank", fontWeight = FontWeight.Bold, color = if (paymentMode == DomainConstants.MODE_BANK) financialColors.bank else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                // Remarks
                OutlinedTextField(
                    value = remarks,
                    onValueChange = { remarks = it },
                    label = { Text("Remarks / Slip Memo") },
                    placeholder = { Text("e.g. Paid by father / slip #") },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Bottom Confirm Button
            Button(
                onClick = {
                    val baseAmt = feeAmount.toDoubleOrNull() ?: 0.0
                    val fineAmt = lateFeeFine.toDoubleOrNull() ?: 0.0
                    val totalAmt = baseAmt + fineAmt
                    val partialNote = if (remainingFee > 0 && enteredFee > 0) " (Partial - Bal Rs. ${remainingFee.toInt()})" else ""
                    val finalRemarks = when {
                        fineAmt > 0 -> (if (remarks.isNotBlank()) "$remarks (Includes Late Fine: Rs. ${fineAmt.toInt()})" else "Includes Late Fine: Rs. ${fineAmt.toInt()}") + partialNote
                        partialNote.isNotBlank() -> (if (remarks.isNotBlank()) "$remarks$partialNote" else "Partial Installment - Bal: Rs. ${remainingFee.toInt()}")
                        else -> remarks
                    }

                    if (totalAmt > 0 && selectedMonth.isNotBlank()) {
                        onConfirm(totalAmt, paymentMode, selectedMonth, finalRemarks)
                    }
                },
                enabled = (feeAmount.toDoubleOrNull() ?: 0.0) > 0.0,
                colors = ButtonDefaults.buttonColors(containerColor = financialColors.cash),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Confirm & Issue Receipt", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 14.sp)
            }
        }
    }
}
