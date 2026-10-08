package com.example.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.example.data.Student
import com.example.data.Transaction
import com.example.ui.PendingFeeAlert
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun ShimmerBrush(): Brush {
    val shimmerBase = MaterialTheme.financialColors.shimmerBase
    val shimmerHighlight = MaterialTheme.financialColors.shimmerHighlight
    val shimmerColors = listOf(
        shimmerBase,
        shimmerHighlight,
        shimmerBase
    )
    val transition = rememberInfiniteTransition(label = "ShimmerTransition")
    val translateAnim = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ShimmerTranslate"
    )
    return Brush.linearGradient(
        colors = shimmerColors,
        start = Offset.Zero,
        end = Offset(x = translateAnim.value, y = translateAnim.value)
    )
}

@Composable
fun ShimmerStudentRow(modifier: Modifier = Modifier) {
    val brush = ShimmerBrush()
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(brush)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Box(
                    modifier = Modifier
                        .height(14.dp)
                        .fillMaxWidth(0.5f)
                        .clip(RoundedCornerShape(4.dp))
                        .background(brush)
                )
                Spacer(modifier = Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .height(11.dp)
                        .fillMaxWidth(0.75f)
                        .clip(RoundedCornerShape(3.dp))
                        .background(brush)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .width(56.dp)
                    .height(20.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(brush)
            )
        }
    }
}

@Composable
private fun LedgerSubCard(
    title: String,
    actionText: String?,
    actionColor: Color,
    amount: Double,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
        modifier = modifier
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(actionColor, CircleShape)
                    )
                    Text(
                        text = title,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 10.sp,
                        letterSpacing = 0.5.sp
                    )
                }
                if (actionText != null && onClick != null) {
                    Text(
                        text = actionText,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = actionColor,
                        fontSize = 10.sp
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Rs. ${String.format(Locale.US, "%,.0f", amount)}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 15.sp
            )
        }
    }
}

@Composable
fun DailyClosingBalanceCard(
    netBalance: Double,
    cashBalance: Double,
    bankBalance: Double,
    modifier: Modifier = Modifier,
    onCashClick: (() -> Unit)? = null,
    onBankClick: (() -> Unit)? = null
) {
    val dateStr = remember { 
        SimpleDateFormat("MMMM dd", Locale.US).format(Date()).uppercase() 
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .background(MaterialTheme.financialColors.cash, CircleShape)
                    )
                    Text(
                        text = "Net Balance Today",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Surface(
                    shape = RoundedCornerShape(100.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                ) {
                    Text(
                        text = dateStr,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp),
                        fontSize = 9.5.sp
                    )
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Rs. ${String.format(Locale.US, "%,.0f", netBalance)}",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                letterSpacing = (-0.75).sp
            )
            Spacer(modifier = Modifier.height(14.dp))
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val isNarrow = maxWidth < 330.dp
                if (isNarrow) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        LedgerSubCard(
                            title = "CASH IN HAND",
                            actionText = if (onCashClick != null) "VIEW →" else null,
                            actionColor = MaterialTheme.financialColors.cash,
                            amount = cashBalance,
                            onClick = onCashClick,
                            modifier = Modifier.fillMaxWidth()
                        )
                        LedgerSubCard(
                            title = "BANK ACCOUNT",
                            actionText = if (onBankClick != null) "VIEW →" else null,
                            actionColor = MaterialTheme.financialColors.bank,
                            amount = bankBalance,
                            onClick = onBankClick,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        LedgerSubCard(
                            title = "CASH IN HAND",
                            actionText = if (onCashClick != null) "VIEW →" else null,
                            actionColor = MaterialTheme.financialColors.cash,
                            amount = cashBalance,
                            onClick = onCashClick,
                            modifier = Modifier.weight(1f)
                        )
                        LedgerSubCard(
                            title = "BANK ACCOUNT",
                            actionText = if (onBankClick != null) "VIEW →" else null,
                            actionColor = MaterialTheme.financialColors.bank,
                            amount = bankBalance,
                            onClick = onBankClick,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatCard(
    title: String,
    amount: Double,
    indicatorColor: Color,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = modifier
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(indicatorColor)
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Rs. ${String.format(Locale.US, "%,.0f", amount)}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 14.5.sp
            )
        }
    }
}

@Composable
fun QuickStatsGrid(
    totalIncome: Double,
    totalExpense: Double,
    modifier: Modifier = Modifier,
    onIncomeClick: (() -> Unit)? = null,
    onExpenseClick: (() -> Unit)? = null
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        val isNarrow = maxWidth < 330.dp
        if (isNarrow) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                StatCard(
                    title = "Total Income",
                    amount = totalIncome,
                    indicatorColor = MaterialTheme.financialColors.cash,
                    onClick = onIncomeClick,
                    modifier = Modifier.fillMaxWidth()
                )
                StatCard(
                    title = "Total Expense",
                    amount = totalExpense,
                    indicatorColor = MaterialTheme.financialColors.expense,
                    onClick = onExpenseClick,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatCard(
                    title = "Total Income",
                    amount = totalIncome,
                    indicatorColor = MaterialTheme.financialColors.cash,
                    onClick = onIncomeClick,
                    modifier = Modifier.weight(1f)
                )
                StatCard(
                    title = "Total Expense",
                    amount = totalExpense,
                    indicatorColor = MaterialTheme.financialColors.expense,
                    onClick = onExpenseClick,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
fun PendingFeeAlertBanner(
    pendingCount: Int,
    onReviewClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.financialColors.expenseContainer.copy(alpha = 0.35f),
        border = BorderStroke(1.dp, MaterialTheme.financialColors.expense.copy(alpha = 0.3f)),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.financialColors.expense,
                    modifier = Modifier.size(17.dp)
                )
                Text(
                    text = "$pendingCount Pending Fee Alerts",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.financialColors.onExpenseContainer,
                    fontSize = 12.5.sp
                )
            }
            TextButton(
                onClick = onReviewClick,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                modifier = Modifier.height(28.dp)
            ) {
                Text(
                    text = "REVIEW →",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.financialColors.expense
                )
            }
        }
    }
}

@Composable
fun OverdueFeesNotificationDialog(
    alerts: List<PendingFeeAlert>,
    onDismiss: () -> Unit,
    onReview: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier
            .imePadding()
            .widthIn(max = 520.dp)
            .fillMaxWidth(0.92f)
            .padding(16.dp),
        icon = {
            Surface(
                modifier = Modifier.size(44.dp),
                shape = CircleShape,
                color = MaterialTheme.financialColors.expenseContainer.copy(alpha = 0.4f),
                border = BorderStroke(1.dp, MaterialTheme.financialColors.expense.copy(alpha = 0.3f))
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.NotificationsActive,
                        contentDescription = null,
                        tint = MaterialTheme.financialColors.expense,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        },
        title = {
            Text(
                text = "Automated Pending Fee Alert",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "The automated alert system has identified student fees that are overdue for this month cycle.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Surface(
                    color = MaterialTheme.financialColors.expenseContainer.copy(alpha = 0.35f),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = "Attention: Admin & Accountant",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.financialColors.expense,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f), thickness = 1.dp)

                Text(
                    text = "Overdue Student Alert List:",
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 200.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(alerts) { alert ->
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = alert.studentName,
                                        fontWeight = FontWeight.SemiBold,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "Class: ${alert.className}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 10.5.sp
                                    )
                                }
                                Text(
                                    text = "Rs. ${alert.pendingAmount.toInt()}",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.financialColors.expense
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f), thickness = 1.dp)

                val totalOutstanding = alerts.sumOf { alert -> alert.pendingAmount }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Total Outstanding Overdue:",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Rs. ${totalOutstanding.toInt()}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.financialColors.expense,
                        fontSize = 15.sp
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onReview,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                modifier = Modifier.height(34.dp)
            ) {
                Text("Review & Actions", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                modifier = Modifier.height(34.dp)
            ) {
                Text("Dismiss", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            }
        }
    )
}

@Composable
fun PaymentSparkline(
    student: Student,
    transactions: List<Transaction>,
    modifier: Modifier = Modifier
) {
    val last6Months = remember {
        val result = mutableListOf<String>()
        val sdf = SimpleDateFormat("MMMM yyyy", Locale.US)
        val cal = Calendar.getInstance()
        for (i in 0 until 6) {
            result.add(sdf.format(cal.time))
            cal.add(Calendar.MONTH, -1)
        }
        result.reversed()
    }

    val dataPoints = remember(transactions, student.id) {
        last6Months.map { monthStr ->
            val monthTxs = transactions.filter { tx ->
                tx.studentId == student.id &&
                tx.isIncome &&
                ((tx.monthOfFee == monthStr) ||
                 (tx.monthOfFee.isNullOrBlank() && SimpleDateFormat("MMMM yyyy", Locale.US).format(Date(tx.date)) == monthStr))
            }
            if (monthTxs.isNotEmpty()) {
                monthTxs.sumOf { it.amount }.toFloat()
            } else {
                0f
            }
        }
    }

    val positiveColor = MaterialTheme.financialColors.cash
    val inactiveColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)

    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        if (width <= 0 || height <= 0) return@Canvas

        val maxVal = dataPoints.maxOrNull() ?: 0f
        val valRange = if (maxVal > 0f) maxVal else 1000f

        val points = dataPoints.indices.map { index ->
            val x = index * (width / (dataPoints.size - 1))
            val y = height - (dataPoints[index] / valRange) * (height - 4.dp.toPx()) - 2.dp.toPx()
            Offset(x, y)
        }

        val strokeColor = if (dataPoints.any { it > 0f }) positiveColor else inactiveColor
        val fillBrush = Brush.verticalGradient(
            colors = listOf(
                strokeColor.copy(alpha = 0.25f),
                strokeColor.copy(alpha = 0.0f)
            )
        )

        val path = Path().apply {
            if (points.isNotEmpty()) {
                moveTo(points.first().x, points.first().y)
                for (i in 1 until points.size) {
                    val prev = points[i - 1]
                    val curr = points[i]
                    val cp1x = prev.x + (curr.x - prev.x) / 2f
                    cubicTo(cp1x, prev.y, cp1x, curr.y, curr.x, curr.y)
                }
            }
        }

        val fillPath = Path().apply {
            addPath(path)
            if (points.isNotEmpty()) {
                lineTo(points.last().x, height)
                lineTo(points.first().x, height)
                close()
            }
        }

        drawPath(
            path = fillPath,
            brush = fillBrush
        )

        drawPath(
            path = path,
            color = strokeColor,
            style = Stroke(
                width = 2.dp.toPx(),
                cap = androidx.compose.ui.graphics.StrokeCap.Round,
                join = androidx.compose.ui.graphics.StrokeJoin.Round
            )
        )

        points.forEachIndexed { idx, pt ->
            val isPaid = dataPoints[idx] > 0f
            val dotColor = if (isPaid) positiveColor else inactiveColor
            val dotRadius = if (isPaid) 2.5.dp.toPx() else 1.5.dp.toPx()
            drawCircle(
                color = dotColor,
                radius = dotRadius,
                center = pt
            )
        }
    }
}

@Composable
fun FinanceSummaryCard(
    title: String,
    value: String,
    subtitle: String,
    icon: ImageVector,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.testTag("finance_card_${title.lowercase().replace(" ", "_")}"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .background(contentColor.copy(alpha = 0.12f), RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = contentColor,
                        modifier = Modifier.size(16.dp)
                    )
                }
                
                Surface(
                    shape = RoundedCornerShape(100.dp),
                    color = containerColor.copy(alpha = 0.45f),
                    border = BorderStroke(0.5.dp, contentColor.copy(alpha = 0.25f))
                ) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = contentColor,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        textAlign = TextAlign.End
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(10.dp))
            
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 14.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun ExecutiveRecoveryTargetCard(
    expectedTotal: Long,
    collectedTotal: Long,
    lowestClassRecovery: Pair<String, Int>?,
    modifier: Modifier = Modifier
) {
    val rate = if (expectedTotal > 0L) (collectedTotal.toFloat() / expectedTotal.toFloat()).coerceIn(0f, 1f) else 0f
    val percentage = (rate * 100).toInt()

    val animatedProgress by animateFloatAsState(
        targetValue = rate,
        animationSpec = tween(durationMillis = 1000, easing = FastOutSlowInEasing),
        label = "RecoveryProgress"
    )

    val financialColors = MaterialTheme.financialColors

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("executive_recovery_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        modifier = Modifier.size(34.dp),
                        shape = RoundedCornerShape(8.dp),
                        color = financialColors.goldBadgeContainer
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.NotificationsActive,
                                contentDescription = null,
                                tint = financialColors.goldBadge,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    Column {
                        Text(
                            text = "Fee Recovery Target",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Monthly Recovery Benchmark",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 10.sp
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = when {
                        percentage >= 85 -> financialColors.cashContainer
                        percentage >= 60 -> financialColors.bankContainer
                        else -> financialColors.expenseContainer
                    }
                ) {
                    Text(
                        text = "$percentage% Achieved",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = when {
                            percentage >= 85 -> financialColors.cash
                            percentage >= 60 -> financialColors.bank
                            else -> financialColors.expense
                        },
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier.size(68.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        progress = { animatedProgress },
                        modifier = Modifier.fillMaxSize(),
                        color = when {
                            percentage >= 85 -> financialColors.cash
                            percentage >= 60 -> financialColors.goldBadge
                            else -> financialColors.expense
                        },
                        strokeWidth = 6.dp,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                    Text(
                        text = "$percentage%",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Target Expected:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                        Text("Rs. $expectedTotal", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodySmall, fontSize = 11.sp)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Total Collected:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                        Text("Rs. $collectedTotal", fontWeight = FontWeight.Bold, color = financialColors.cash, style = MaterialTheme.typography.bodySmall, fontSize = 11.sp)
                    }
                    val remaining = (expectedTotal - collectedTotal).coerceAtLeast(0L)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Remaining Due:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                        Text("Rs. $remaining", fontWeight = FontWeight.Bold, color = if (remaining > 0) financialColors.expense else financialColors.cash, style = MaterialTheme.typography.bodySmall, fontSize = 11.sp)
                    }
                }
            }

            if (lowestClassRecovery != null && lowestClassRecovery.second < 75) {
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = financialColors.expenseContainer.copy(alpha = 0.4f),
                    border = BorderStroke(1.dp, financialColors.expense.copy(alpha = 0.25f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = financialColors.expense,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = "Attention: ${lowestClassRecovery.first} recovery is lowest at ${lowestClassRecovery.second}%",
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Medium,
                            color = financialColors.expense
                        )
                    }
                }
            }
        }
    }
}

/**
 * Universal Empty State illustration view for cleanly conveying zero results / empty lists.
 */
@Composable
fun EmptyStateView(
    icon: ImageVector,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    actionText: String? = null,
    onActionClick: (() -> Unit)? = null
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            modifier = Modifier.size(56.dp),
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(28.dp))
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center)
        Spacer(modifier = Modifier.height(4.dp))
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        if (actionText != null && onActionClick != null) {
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = onActionClick,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                modifier = Modifier.height(34.dp)
            ) {
                Text(actionText, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

/**
 * Modern Quick Action Bottom Sheet replacing the clunky multi-tier Floating Speed Dial.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickActionBottomSheet(
    onDismiss: () -> Unit,
    onNewAdmission: () -> Unit,
    onReceiveFee: () -> Unit,
    onLogExpense: () -> Unit,
    onCashToBank: () -> Unit,
    onFamilyFee: () -> Unit = {}
) {
    val financialColors = MaterialTheme.financialColors
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "Quick Actions",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        "Financial Transactions & Shortcuts",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Close",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(2.dp))

            // 1. Receive Fee
            QuickActionItem(
                title = "Receive Student Fee",
                description = "Collect monthly tuition, admission, or annual charges",
                icon = Icons.Default.AddCard,
                accentColor = financialColors.cash,
                bgColor = financialColors.cashContainer,
                onClick = {
                    onDismiss()
                    onReceiveFee()
                }
            )

            // 2. Family / Sibling Combined Fee
            QuickActionItem(
                title = "Family / Sibling Fee",
                description = "Collect fees for multiple siblings on a single unified receipt",
                icon = Icons.Default.Groups,
                accentColor = MaterialTheme.colorScheme.tertiary,
                bgColor = MaterialTheme.colorScheme.tertiaryContainer,
                onClick = {
                    onDismiss()
                    onFamilyFee()
                }
            )

            // 3. New Admission
            QuickActionItem(
                title = "New Student Admission",
                description = "Register a new student with monthly tuition package",
                icon = Icons.Default.PersonAdd,
                accentColor = MaterialTheme.colorScheme.primary,
                bgColor = MaterialTheme.colorScheme.primaryContainer,
                onClick = {
                    onDismiss()
                    onNewAdmission()
                }
            )

            // 4. Record Expense
            QuickActionItem(
                title = "Record Expense (Cash Out)",
                description = "Log rent, utility bills, staff salary, or campus expenses",
                icon = Icons.Default.Payments,
                accentColor = financialColors.expense,
                bgColor = financialColors.expenseContainer,
                onClick = {
                    onDismiss()
                    onLogExpense()
                }
            )

            // 5. Cash to Bank
            QuickActionItem(
                title = "Deposit Cash to Bank",
                description = "Transfer cash in hand to official school bank account",
                icon = Icons.Default.AccountBalance,
                accentColor = financialColors.bank,
                bgColor = financialColors.bankContainer,
                onClick = {
                    onDismiss()
                    onCashToBank()
                }
            )
        }
    }
}

@Composable
fun QuickActionItem(
    title: String,
    description: String,
    icon: ImageVector,
    accentColor: Color,
    bgColor: Color,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = bgColor.copy(alpha = 0.35f),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.2f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Surface(
                modifier = Modifier.size(42.dp),
                shape = RoundedCornerShape(10.dp),
                color = accentColor
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.5.sp
                )
            }
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
