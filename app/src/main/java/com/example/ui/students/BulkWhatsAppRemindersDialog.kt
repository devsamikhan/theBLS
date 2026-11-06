package com.example.ui.students

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.example.ui.PendingFeeAlert
import com.example.ui.theme.financialColors

@Composable
fun BulkWhatsAppRemindersDialog(
    alerts: List<PendingFeeAlert>,
    onDismiss: () -> Unit,
    onSendWhatsApp: (PendingFeeAlert) -> Unit
) {
    val financialColors = MaterialTheme.financialColors
    var sentStudentIds by remember { mutableStateOf(emptySet<Int>()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier
            .fillMaxWidth(0.95f)
            .fillMaxHeight(0.85f)
            .padding(16.dp),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, tint = financialColors.whatsapp)
                Spacer(modifier = Modifier.width(8.dp))
                Text("WhatsApp Reminders Queue", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxSize()) {
                Text(
                    "${alerts.size} students with pending dues. Tap 'Send' to dispatch pre-formatted WhatsApp notices to parents one by one.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(10.dp))

                // Progress Bar
                LinearProgressIndicator(
                    progress = { if (alerts.isNotEmpty()) sentStudentIds.size.toFloat() / alerts.size else 0f },
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                    color = financialColors.whatsapp,
                    trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "${sentStudentIds.size} of ${alerts.size} dispatched",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(10.dp))

                if (alerts.isEmpty()) {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text("No pending fee alerts! All students are clear.", color = financialColors.cash, fontWeight = FontWeight.Bold)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(alerts, key = { it.studentId }) { alert ->
                            val isSent = sentStudentIds.contains(alert.studentId)
                            Card(
                                colors = CardDefaults.cardColors(containerColor = if (isSent) financialColors.cashContainer else MaterialTheme.colorScheme.surface),
                                border = BorderStroke(1.dp, if (isSent) financialColors.cash.copy(alpha = 0.4f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(alert.studentName, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                        Text("Class: ${alert.className} | Ph: ${alert.contactNumber}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text("Pending: Rs. ${alert.totalPendingAmount.toInt()} (${alert.month})", color = financialColors.expense, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                                    }
                                    
                                    Button(
                                        onClick = {
                                            onSendWhatsApp(alert)
                                            sentStudentIds = sentStudentIds + alert.studentId
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (isSent) financialColors.cash else financialColors.whatsapp
                                        ),
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                    ) {
                                        Icon(
                                            if (isSent) Icons.Default.Check else Icons.AutoMirrored.Filled.Send,
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp),
                                            tint = Color.White
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(if (isSent) "Sent" else "Send", fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)) {
                Text("Close Queue")
            }
        }
    )
}
