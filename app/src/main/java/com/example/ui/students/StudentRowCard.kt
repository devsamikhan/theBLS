package com.example.ui.students

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.DomainConstants
import com.example.data.Student
import com.example.data.Transaction
import com.example.ui.theme.financialColors
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun StudentRowCard(
    student: Student,
    transactions: List<Transaction>,
    isSelected: Boolean = false,
    onClick: () -> Unit,
    onQuickFee: (() -> Unit)? = null,
    onQuickWhatsApp: (() -> Unit)? = null
) {
    val haptic = LocalHapticFeedback.current
    val currentMonth = remember { SimpleDateFormat("MMMM yyyy", Locale.US).format(Date()) }
    val discountedMonthly = remember(student) {
        DomainConstants.calculateDiscountedFee(student.monthlyFee, student.discountType)
    }
    val paidThisMonth = remember(transactions, student, currentMonth, discountedMonthly) {
        val totalPaid = transactions.filter { tx ->
            tx.studentId == student.id && tx.isIncome && (
                tx.monthOfFee == currentMonth ||
                (tx.monthOfFee.isNullOrBlank() && SimpleDateFormat("MMMM yyyy", Locale.US).format(Date(tx.date)) == currentMonth)
            )
        }.sumOf { it.amount }
        totalPaid >= discountedMonthly && discountedMonthly > 0
    }
    val advanceWallet = remember(transactions, student) {
        DomainConstants.calculateStudentAdvanceWallet(student, transactions)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .testTag("student_row_${student.id}"),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
        shape = RoundedCornerShape(14.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Sleek Squircle Avatar
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (paidThisMonth) MaterialTheme.financialColors.cashContainer
                        else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (student.photoUri.isNotBlank()) {
                    AsyncImage(
                        model = student.photoUri,
                        contentDescription = "Photo of ${student.studentName}",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(12.dp))
                    )
                } else {
                    Text(
                        text = student.studentName.take(1).uppercase(),
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = if (paidThisMonth) MaterialTheme.financialColors.cash else MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Student Info Column
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = student.studentName,
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (student.regNo.isNotBlank()) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                        ) {
                            Text(
                                text = student.regNo,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }
                    if (advanceWallet > 0.0) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                        ) {
                            Text(
                                text = "Adv",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = "${student.className} • ${student.fatherName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Right Actions: Status Badge (Clickable for Fee Collection) + WhatsApp Button
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (paidThisMonth) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.financialColors.cashContainer.copy(alpha = 0.8f),
                        border = BorderStroke(0.5.dp, MaterialTheme.financialColors.cash.copy(alpha = 0.35f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.financialColors.cash,
                                modifier = Modifier.size(11.dp)
                            )
                            Text(
                                "Paid",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.financialColors.cash
                            )
                        }
                    }
                } else {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.financialColors.expenseContainer.copy(alpha = 0.8f),
                        border = BorderStroke(0.5.dp, MaterialTheme.financialColors.expense.copy(alpha = 0.35f)),
                        modifier = Modifier.clickable {
                            if (onQuickFee != null) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onQuickFee()
                            }
                        }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Text(
                                text = "Rs. ${discountedMonthly.toInt()}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.financialColors.expense
                            )
                            Icon(
                                Icons.Default.Add,
                                contentDescription = "Collect Fee",
                                tint = MaterialTheme.financialColors.expense,
                                modifier = Modifier.size(11.dp)
                            )
                        }
                    }
                }

                if (onQuickWhatsApp != null && student.contactNumber.isNotBlank()) {
                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onQuickWhatsApp()
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.Send,
                            contentDescription = "WhatsApp reminder",
                            tint = MaterialTheme.financialColors.whatsapp,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}
