package com.example.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.ActivityFeedItem
import com.example.data.ActivityType
import com.example.data.DomainConstants
import com.example.ui.theme.*
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivityFeedDialog(
    feedItems: List<ActivityFeedItem>,
    currentRole: String,
    onDismiss: () -> Unit,
    onMarkAllRead: () -> Unit
) {
    var selectedFilterIndex by remember { mutableIntStateOf(0) } // 0: All, 1: Admin, 2: Accountant
    val filterTabs = listOf("All Activity", "Admin Actions", "Accountant")

    val filteredList = remember(feedItems, selectedFilterIndex) {
        when (selectedFilterIndex) {
            1 -> feedItems.filter { it.authorRole.equals(DomainConstants.ROLE_ADMIN, ignoreCase = true) }
            2 -> feedItems.filter { it.authorRole.equals(DomainConstants.ROLE_ACCOUNTANT, ignoreCase = true) }
            else -> feedItems
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.85f),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.NotificationsActive,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Column {
                            Text(
                                "Live System Activity & Audit Log",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                "Real-time institutional operations trail",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(34.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), CircleShape)
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Close",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Actions & Filter Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.weight(1f)) {
                        filterTabs.forEachIndexed { index, title ->
                            SegmentedButton(
                                selected = selectedFilterIndex == index,
                                onClick = { selectedFilterIndex = index },
                                shape = SegmentedButtonDefaults.itemShape(index = index, count = filterTabs.size)
                            ) {
                                Text(title, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    FilledTonalButton(
                        onClick = onMarkAllRead,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.DoneAll, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Mark All Read", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f), thickness = 1.dp)
                Spacer(modifier = Modifier.height(10.dp))

                // Items list or Empty state
                if (filteredList.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Default.EventNote,
                                contentDescription = null,
                                modifier = Modifier.size(44.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                            )
                            Text(
                                "No recent activity records found",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(bottom = 12.dp)
                    ) {
                        items(filteredList, key = { it.id }) { item ->
                            ActivityFeedCard(item = item)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ActivityFeedCard(item: ActivityFeedItem) {
    val currencyFormat = remember { NumberFormat.getNumberInstance(Locale.US) }
    val timeFormat = remember { SimpleDateFormat("hh:mm a", Locale.getDefault()) }
    val dateFormat = remember { SimpleDateFormat("dd MMM", Locale.getDefault()) }

    val formattedTime = remember(item.timestamp) {
        val now = System.currentTimeMillis()
        val isToday = (now - item.timestamp) < 24 * 60 * 60 * 1000L
        if (isToday) {
            "Today, ${timeFormat.format(Date(item.timestamp))}"
        } else {
            "${dateFormat.format(Date(item.timestamp))} ${timeFormat.format(Date(item.timestamp))}"
        }
    }

    val (iconColor, bgColor, iconVector) = when (item.type) {
        ActivityType.ADMISSION -> Triple(MaterialTheme.financialColors.cash, MaterialTheme.financialColors.cashContainer, Icons.Default.School)
        ActivityType.FEE_COLLECTION -> Triple(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f), Icons.Default.Paid)
        ActivityType.EXPENSE -> Triple(MaterialTheme.financialColors.expense, MaterialTheme.financialColors.expenseContainer, Icons.Default.ReceiptLong)
        ActivityType.BANK_TRANSFER -> Triple(MaterialTheme.financialColors.bank, MaterialTheme.financialColors.bankContainer, Icons.Default.AccountBalance)
    }

    val isAdminAuthor = item.authorRole.equals(DomainConstants.ROLE_ADMIN, ignoreCase = true)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(bgColor),
                contentAlignment = Alignment.Center
            ) {
                Icon(iconVector, contentDescription = null, tint = iconColor, modifier = Modifier.size(20.dp))
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        item.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    // Role Badge
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (isAdminAuthor) MaterialTheme.financialColors.amber.copy(alpha = 0.12f) else MaterialTheme.financialColors.bank.copy(alpha = 0.12f),
                        border = BorderStroke(0.5.dp, if (isAdminAuthor) MaterialTheme.financialColors.amber.copy(alpha = 0.4f) else MaterialTheme.financialColors.bank.copy(alpha = 0.4f))
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Icon(
                                if (isAdminAuthor) Icons.Default.AdminPanelSettings else Icons.Default.Person,
                                contentDescription = null,
                                tint = if (isAdminAuthor) MaterialTheme.financialColors.amber else MaterialTheme.financialColors.bank,
                                modifier = Modifier.size(11.dp)
                            )
                            Text(
                                if (isAdminAuthor) "ADMIN" else "ACCOUNTANT",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isAdminAuthor) MaterialTheme.financialColors.amber else MaterialTheme.financialColors.bank
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    item.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (item.amount > 0.0) {
                        Text(
                            "Rs ${currencyFormat.format(item.amount)}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = when (item.type) {
                                ActivityType.EXPENSE -> MaterialTheme.financialColors.expense
                                else -> MaterialTheme.financialColors.cash
                            }
                        )
                    } else {
                        Text(
                            item.voucherNo,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Text(
                        formattedTime,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )
                }
            }
        }
    }
}
