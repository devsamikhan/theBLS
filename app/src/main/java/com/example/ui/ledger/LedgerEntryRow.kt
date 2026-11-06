package com.example.ui.ledger

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.DomainConstants
import com.example.data.Transaction
import com.example.ui.theme.financialColors
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun LedgerEntryRow(
    transaction: Transaction,
    showDelete: Boolean,
    onDelete: () -> Unit,
    onPrintSlip: ((Transaction) -> Unit)? = null,
    onPrintExpenseVoucher: ((Transaction) -> Unit)? = null,
    studentClass: String? = null,
    runningBalance: Double? = null
) {
    val isIncome = transaction.isIncome
    val accentColor = if (isIncome) MaterialTheme.financialColors.cash else MaterialTheme.financialColors.expense
    val containerColor = if (isIncome) MaterialTheme.financialColors.cashContainer else MaterialTheme.financialColors.expenseContainer
    val dateFormatted = remember(transaction.date) {
        SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()).format(Date(transaction.date))
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Left: Compact Squircle Icon + Info
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(containerColor.copy(alpha = 0.7f), RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isIncome) Icons.Default.ArrowDownward else Icons.Default.ArrowUpward,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(16.dp)
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    // Title Line + Voucher Badge
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        val titleText: String = if (isIncome) {
                            transaction.studentName ?: transaction.category
                        } else {
                            transaction.category
                        }
                        Text(
                            text = titleText,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )

                        if (transaction.voucherNo.isNotBlank()) {
                            Surface(
                                color = containerColor.copy(alpha = 0.5f),
                                shape = RoundedCornerShape(4.dp),
                                border = BorderStroke(0.5.dp, accentColor.copy(alpha = 0.3f))
                            ) {
                                Text(
                                    text = "#${transaction.voucherNo}",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = accentColor,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(1.dp))

                    // Subtitle: Mode • Class / Payee • Details
                    val detailLine = buildString {
                        append(transaction.paymentMode)
                        if (isIncome && !studentClass.isNullOrBlank()) {
                            append(" • Cl: $studentClass")
                        } else if (!isIncome && transaction.payeeName.isNotBlank()) {
                            append(" • ${transaction.payeeName}")
                        } else if (transaction.description.isNotBlank()) {
                            append(" • ${transaction.description}")
                        }
                        append(" • $dateFormatted")
                    }
                    Text(
                        text = detailLine,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Right: Amount + Actions
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "${if (isIncome) "+" else "-"} Rs. ${transaction.amount.toInt()}",
                        color = accentColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    if (runningBalance != null) {
                        Text(
                            text = "Bal: ${runningBalance.toInt()}",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                        )
                    }
                }

                if (onPrintSlip != null) {
                    IconButton(
                        onClick = { onPrintSlip(transaction) },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            Icons.Default.Print,
                            contentDescription = "Print Fee Slip",
                            tint = MaterialTheme.financialColors.cash,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                } else if (!isIncome && onPrintExpenseVoucher != null) {
                    IconButton(
                        onClick = { onPrintExpenseVoucher(transaction) },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            Icons.Default.ReceiptLong,
                            contentDescription = "Print Expense Voucher / Payslip",
                            tint = if (transaction.category == DomainConstants.CAT_STAFF_SALARY) MaterialTheme.colorScheme.primary else MaterialTheme.financialColors.expense,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }

                if (showDelete) {
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Delete entry",
                            tint = MaterialTheme.financialColors.expense.copy(alpha = 0.6f),
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
            }
        }
    }
}
