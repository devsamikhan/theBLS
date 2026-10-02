package com.example.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.Student
import com.example.data.Transaction
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

import androidx.compose.foundation.clickable
import androidx.compose.ui.graphics.Color
import com.example.data.DomainConstants

data class ClassRecoveryStat(
    val className: String,
    val totalStudents: Int,
    val expectedRevenue: Double,
    val collectedRevenue: Double,
    val recoveryPercentage: Float,
    val defaultersCount: Int = 0,
    val paidCount: Int = 0
)

/**
 * Minimalist Visual Progress Dashboard for Class-wise Fee Recoveries.
 * Displays target versus collected amounts with smooth animations and semantic status hues.
 */
@Composable
fun ClassRecoveryProgressSection(
    students: List<Student>,
    transactions: List<Transaction>,
    modifier: Modifier = Modifier
) {
    val currentMonthYear = remember { SimpleDateFormat("MMMM yyyy", Locale.US).format(Date()) }
    var sortByLowestRecovery by remember { mutableStateOf(false) }

    val recoveryStats = remember(students, transactions, currentMonthYear, sortByLowestRecovery) {
        val activeStudents = students.filter { it.status.equals(DomainConstants.STATUS_ACTIVE, ignoreCase = true) }
        val groupedStudents = activeStudents.groupBy { it.className.trim() }

        val list = groupedStudents.map { (className, classStudents) ->
            val expectedTotal = classStudents.sumOf { std ->
                DomainConstants.calculateDiscountedFee(std.monthlyFee, std.discountType)
            }

            val studentIds = classStudents.map { it.id }.toSet()
            val collectedForMonth = transactions.filter { tx ->
                tx.isIncome && tx.studentId in studentIds && (
                    tx.monthOfFee == currentMonthYear ||
                    (tx.monthOfFee.isNullOrBlank() && SimpleDateFormat("MMMM yyyy", Locale.US).format(Date(tx.date)) == currentMonthYear)
                )
            }.sumOf { it.amount }

            var defaulters = 0
            var paidStudents = 0
            classStudents.forEach { std ->
                val stdDue = DomainConstants.calculateDiscountedFee(std.monthlyFee, std.discountType)
                val stdPaid = transactions.filter { tx ->
                    tx.isIncome && tx.studentId == std.id && (
                        tx.monthOfFee == currentMonthYear ||
                        (tx.monthOfFee.isNullOrBlank() && SimpleDateFormat("MMMM yyyy", Locale.US).format(Date(tx.date)) == currentMonthYear)
                    )
                }.sumOf { it.amount }

                if (stdDue > 0.0 && stdPaid < stdDue) {
                    defaulters++
                } else if (stdDue > 0.0) {
                    paidStudents++
                }
            }

            val pct = if (expectedTotal > 0.0) {
                ((collectedForMonth / expectedTotal).toFloat() * 100f).coerceIn(0f, 100f)
            } else 100f

            ClassRecoveryStat(
                className = if (className.isBlank()) "Unassigned" else className,
                totalStudents = classStudents.size,
                expectedRevenue = expectedTotal,
                collectedRevenue = collectedForMonth,
                recoveryPercentage = pct,
                defaultersCount = defaulters,
                paidCount = paidStudents
            )
        }

        if (sortByLowestRecovery) {
            list.sortedBy { it.recoveryPercentage }
        } else {
            list.sortedByDescending { it.totalStudents }
        }
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Class-wise Fee Recovery Rate",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Target vs Actual Collection • $currentMonthYear",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = if (sortByLowestRecovery) MaterialTheme.financialColors.expenseContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.clickable { sortByLowestRecovery = !sortByLowestRecovery }
                    ) {
                        Text(
                            text = if (sortByLowestRecovery) "Sort: Lowest %" else "Sort: Size",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (sortByLowestRecovery) MaterialTheme.financialColors.expense else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                        )
                    }

                    Surface(
                        color = MaterialTheme.financialColors.cashContainer,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = "${recoveryStats.size} Classes",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.financialColors.cash,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            if (recoveryStats.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No active registered students found to track recovery.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    recoveryStats.forEach { stat ->
                        ClassRecoveryItem(stat = stat)
                    }
                }
            }
        }
    }
}

@Composable
private fun ClassRecoveryItem(stat: ClassRecoveryStat) {
    val animatedProgress by animateFloatAsState(
        targetValue = stat.recoveryPercentage / 100f,
        animationSpec = tween(durationMillis = 600),
        label = "ClassRecoveryProgress"
    )

    val progressColor = when {
        stat.recoveryPercentage >= 80f -> MaterialTheme.financialColors.cash
        stat.recoveryPercentage >= 50f -> MaterialTheme.financialColors.goldBadge
        else -> MaterialTheme.financialColors.expense
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f))
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stat.className,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.width(6.dp))
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = "${stat.totalStudents} Students",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 9.5.sp,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                    )
                }
            }

            Text(
                text = "${stat.recoveryPercentage.toInt()}%",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.bodyMedium,
                color = progressColor
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        LinearProgressIndicator(
            progress = { animatedProgress },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
            color = progressColor,
            trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )

        Spacer(modifier = Modifier.height(6.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Collected: Rs. ${stat.collectedRevenue.toInt()}",
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.financialColors.cash
                )
                Text(
                    text = "Target: Rs. ${stat.expectedRevenue.toInt()}",
                    fontSize = 10.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                if (stat.defaultersCount > 0) {
                    Surface(
                        color = Color(0xFFFEE2E2),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = "${stat.defaultersCount} Unpaid",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF991B1B),
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                        )
                    }
                }
                if (stat.paidCount > 0) {
                    Surface(
                        color = Color(0xFFDCFCE7),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = "${stat.paidCount} Paid",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF166534),
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                        )
                    }
                }
            }
        }
    }
}
