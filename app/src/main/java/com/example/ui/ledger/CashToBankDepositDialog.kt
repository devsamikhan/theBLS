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
import com.example.ui.theme.financialColors
import com.example.util.CurrencyVisualTransformation

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CashToBankDepositDialog(
    availableCash: Double,
    availableBank: Double = 0.0,
    onDismiss: () -> Unit,
    onConfirm: (amount: Double, bankDetails: String, slipNo: String, remarks: String) -> Unit,
    onConfirmWithdrawal: ((amount: Double, bankDetails: String, slipNo: String, remarks: String) -> Unit)? = null
) {
    var isDepositMode by remember { mutableStateOf(true) }
    var amountText by remember { mutableStateOf("") }
    var bankAccountDetails by remember { mutableStateOf("School Main Bank Account") }
    var slipNo by remember { mutableStateOf("") }
    var remarks by remember { mutableStateOf("") }

    val enteredAmount = amountText.toDoubleOrNull() ?: 0.0
    val maxAvailable = if (isDepositMode) availableCash else availableBank
    val isOverLimit = enteredAmount > maxAvailable && maxAvailable > 0
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
                            .background(if (isDepositMode) MaterialTheme.financialColors.bankContainer else MaterialTheme.financialColors.cashContainer, RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isDepositMode) Icons.Default.AccountBalance else Icons.Default.Payments,
                            contentDescription = null,
                            tint = if (isDepositMode) MaterialTheme.financialColors.bank else MaterialTheme.financialColors.cash,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = if (isDepositMode) "Deposit Cash to Bank" else "Withdraw Cash from Bank",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (isDepositMode) "Counter cash transfer to school bank account" else "Petty cash replenishment from bank account",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))

            // Body
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Two-way Direction Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Card(
                        modifier = Modifier.weight(1f).clickable { isDepositMode = true },
                        colors = CardDefaults.cardColors(containerColor = if (isDepositMode) MaterialTheme.financialColors.bank else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(vertical = 10.dp).fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.ArrowForward, contentDescription = null, modifier = Modifier.size(14.dp), tint = if (isDepositMode) Color.White else MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Cash to Bank", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = if (isDepositMode) Color.White else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    Card(
                        modifier = Modifier.weight(1f).clickable { isDepositMode = false },
                        colors = CardDefaults.cardColors(containerColor = if (!isDepositMode) MaterialTheme.financialColors.cash else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(vertical = 10.dp).fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.ArrowBack, contentDescription = null, modifier = Modifier.size(14.dp), tint = if (!isDepositMode) Color.White else MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Bank to Cash", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = if (!isDepositMode) Color.White else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                // Balance Info Card
                Card(
                    colors = CardDefaults.cardColors(containerColor = if (isDepositMode) MaterialTheme.financialColors.cashContainer.copy(alpha = 0.4f) else MaterialTheme.financialColors.bankContainer.copy(alpha = 0.4f)),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = if (isDepositMode) "Available Cash Box Balance:" else "Available Bank Balance:",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "Rs. ${maxAvailable.toInt()}",
                                fontWeight = FontWeight.Bold,
                                color = if (isDepositMode) MaterialTheme.financialColors.cash else MaterialTheme.financialColors.bank,
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                        Icon(
                            imageVector = if (isDepositMode) Icons.Default.Payments else Icons.Default.AccountBalance,
                            contentDescription = null,
                            tint = if (isDepositMode) MaterialTheme.financialColors.cash else MaterialTheme.financialColors.bank,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }

                Text(
                    text = if (isDepositMode)
                        "Transfer physical cash from the school cash counter into the bank account. Automatically updates both balances."
                    else
                        "Withdraw funds from bank account to replenish the school cash drawer. Automatically updates both balances.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it.filter { ch -> ch.isDigit() } },
                    label = { Text(if (isDepositMode) "Deposit Amount (Rs) *" else "Withdrawal Amount (Rs) *") },
                    placeholder = { Text("e.g. 25000") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    visualTransformation = CurrencyVisualTransformation(),
                    isError = isOverLimit,
                    supportingText = {
                        if (isOverLimit) {
                            Text("Warning: Amount exceeds available balance (Rs. ${maxAvailable.toInt()})", color = MaterialTheme.financialColors.expense, fontSize = 11.sp)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )

                OutlinedTextField(
                    value = bankAccountDetails,
                    onValueChange = { bankAccountDetails = it },
                    label = { Text("Bank & Branch Details *") },
                    placeholder = { Text("e.g. HBL / Meezan School Branch") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )

                OutlinedTextField(
                    value = slipNo,
                    onValueChange = { slipNo = it },
                    label = { Text(if (isDepositMode) "Deposit Slip # / Ref" else "Cheque # / ATM Ref") },
                    placeholder = { Text(if (isDepositMode) "e.g. SLIP-89214" else "e.g. CHQ-55014") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )

                OutlinedTextField(
                    value = remarks,
                    onValueChange = { remarks = it },
                    label = { Text("Authorized By / Remarks") },
                    placeholder = { Text("e.g. Authorized by Principal / Accountant") },
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

                Button(
                    onClick = {
                        if (enteredAmount > 0) {
                            if (isDepositMode) {
                                onConfirm(enteredAmount, bankAccountDetails, slipNo, remarks)
                            } else {
                                onConfirmWithdrawal?.invoke(enteredAmount, bankAccountDetails, slipNo, remarks) ?: onConfirm(enteredAmount, bankAccountDetails, slipNo, remarks)
                            }
                        }
                    },
                    enabled = enteredAmount > 0,
                    colors = ButtonDefaults.buttonColors(containerColor = if (isDepositMode) MaterialTheme.financialColors.bank else MaterialTheme.financialColors.cash),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .weight(1.5f)
                        .height(42.dp)
                ) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.White)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (isDepositMode) "Confirm Deposit" else "Confirm Withdrawal", color = Color.White, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
