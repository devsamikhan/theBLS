package com.example.ui.ledger

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import com.example.data.DomainConstants
import com.example.ui.theme.financialColors
import com.example.util.CurrencyVisualTransformation

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddExpenseDialog(
    onDismiss: () -> Unit,
    onSubmit: (amount: Double, category: String, mode: String, details: String, payeeName: String, invoiceNo: String) -> Unit
) {
    var expenseAmount by remember { mutableStateOf("") }
    var expenseCategory by remember { mutableStateOf("") }
    var categoryExpanded by remember { mutableStateOf(false) }
    var paymentMode by remember { mutableStateOf(DomainConstants.MODE_CASH) }
    var payeeNameInput by remember { mutableStateOf("") }
    var invoiceNoInput by remember { mutableStateOf("") }
    var descInput by remember { mutableStateOf("") }

    val categories: List<String> = DomainConstants.EXPENSE_CATEGORIES

    LaunchedEffect(Unit) {
        if (categories.isNotEmpty()) {
            expenseCategory = categories[0]
        }
    }

    var staffName by remember { mutableStateOf("") }
    var salaryMonth by remember { mutableStateOf("") }
    var customExpenseHead by remember { mutableStateOf("") }

    val isSalaryCategory = expenseCategory == DomainConstants.CAT_TEACHERS_PAY || expenseCategory == DomainConstants.CAT_STAFF_SALARY
    val isOtherCategory = expenseCategory == DomainConstants.CAT_OTHER_EXPENSE
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
                            .background(MaterialTheme.financialColors.expenseContainer, RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Payments, contentDescription = null, tint = MaterialTheme.financialColors.expense, modifier = Modifier.size(22.dp))
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text("Record Expense", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                        Text("Operating expense & payment voucher", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                OutlinedTextField(
                    value = expenseAmount,
                    onValueChange = { expenseAmount = it.filter { ch -> ch.isDigit() } },
                    label = { Text("Expense cash out amount (Rs) *") },
                    placeholder = { Text("e.g. 5000") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    visualTransformation = CurrencyVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = expenseCategory,
                        onValueChange = {},
                        label = { Text("Expense Category *") },
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
                        categories.forEach { cat: String ->
                            DropdownMenuItem(
                                text = { Text(text = cat) },
                                onClick = { expenseCategory = cat; categoryExpanded = false }
                            )
                        }
                    }
                }

                if (isSalaryCategory) {
                    OutlinedTextField(
                        value = staffName,
                        onValueChange = { staffName = it },
                        label = { Text("Staff / Teacher Name *") },
                        placeholder = { Text("e.g. Sir Ali / Ma'am Fatima") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    OutlinedTextField(
                        value = salaryMonth,
                        onValueChange = { salaryMonth = it },
                        label = { Text("Salary Period / Month") },
                        placeholder = { Text("e.g. September 2026") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                }

                if (isOtherCategory) {
                    OutlinedTextField(
                        value = customExpenseHead,
                        onValueChange = { customExpenseHead = it },
                        label = { Text("Specific Head / Sub-Category") },
                        placeholder = { Text("e.g. Generator Fuel, Sanitation, Lab Assets") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                }

                // Payee / Recipient Name
                OutlinedTextField(
                    value = payeeNameInput,
                    onValueChange = { payeeNameInput = it },
                    label = { Text("Paid To / Vendor / Person (Optional)") },
                    placeholder = { Text("e.g. LESCO / Ali Stationery / Mr. Imran") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                // Bill / Invoice Number
                OutlinedTextField(
                    value = invoiceNoInput,
                    onValueChange = { invoiceNoInput = it },
                    label = { Text("Bill / Invoice / Receipt # (Optional)") },
                    placeholder = { Text("e.g. INV-9812 / Bill-044") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                Text("Deduct balance from:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Card(
                        border = BorderStroke(1.dp, if (paymentMode == DomainConstants.MODE_CASH) MaterialTheme.financialColors.expense else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                        modifier = Modifier
                            .weight(1f)
                            .clickable { paymentMode = DomainConstants.MODE_CASH },
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = if (paymentMode == DomainConstants.MODE_CASH) MaterialTheme.financialColors.expenseContainer.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surface)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(Icons.Default.Payments, contentDescription = null, tint = if (paymentMode == DomainConstants.MODE_CASH) MaterialTheme.financialColors.expense else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Cash Box", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = if (paymentMode == DomainConstants.MODE_CASH) MaterialTheme.financialColors.expense else MaterialTheme.colorScheme.onSurfaceVariant)
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
                    value = descInput,
                    onValueChange = { descInput = it },
                    label = { Text("Details / Remarks *") },
                    placeholder = { Text("e.g. Electricity bill / Stationery purchase") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))

            // Footer Actions
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

                val amt = expenseAmount.toDoubleOrNull() ?: 0.0
                val canSubmit = amt > 0 && expenseCategory.isNotBlank() && (descInput.isNotBlank() || staffName.isNotBlank() || customExpenseHead.isNotBlank() || payeeNameInput.isNotBlank())
                Button(
                    onClick = {
                        val finalCategory = if (isOtherCategory && customExpenseHead.isNotBlank()) customExpenseHead.trim() else expenseCategory
                        val finalDesc = if (isSalaryCategory && staffName.isNotBlank()) {
                            "Teacher: $staffName | Period: ${salaryMonth.ifBlank { "Current" }} | $descInput".trim()
                        } else if (isOtherCategory && customExpenseHead.isNotBlank()) {
                            "[$customExpenseHead] $descInput".trim()
                        } else {
                            descInput
                        }
                        if (amt > 0 && finalCategory.isNotBlank()) {
                            val resolvedPayee = if (isSalaryCategory && payeeNameInput.isBlank() && staffName.isNotBlank()) staffName.trim() else payeeNameInput.trim()
                            onSubmit(
                                amt,
                                finalCategory,
                                paymentMode,
                                if (finalDesc.isNotBlank()) finalDesc else "Expense disbursement",
                                resolvedPayee,
                                invoiceNoInput.trim()
                            )
                        }
                    },
                    modifier = Modifier
                        .weight(1.5f)
                        .height(42.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.financialColors.expense),
                    shape = RoundedCornerShape(10.dp),
                    enabled = canSubmit
                ) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.White)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Confirm Expense", color = Color.White, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
