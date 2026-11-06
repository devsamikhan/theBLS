package com.example.ui.students

import android.content.Intent
import android.net.Uri
import android.widget.Toast
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.Student
import com.example.ui.PendingFeeAlert
import com.example.ui.theme.financialColors
import com.example.util.ReportExporter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudentExitSettlementDialog(
    student: Student,
    alert: PendingFeeAlert?,
    onDismiss: () -> Unit,
    onConfirmStatusLeft: () -> Unit,
    onCollectPayment: () -> Unit
) {
    val context = LocalContext.current
    val financialColors = MaterialTheme.financialColors
    val unpaidTuition = alert?.totalPendingAmount ?: 0.0
    val unpaidMonths = alert?.pendingMonthsList?.joinToString(", ") ?: ""
    val isCleared = unpaidTuition <= 0.0

    var leavingFeeText by remember { mutableStateOf("") }
    var remarks by remember { mutableStateOf("") }
    var conductText by remember { mutableStateOf("Good & Satisfactory") }
    var reasonText by remember { mutableStateOf("Parent Request / Migration") }

    val leavingProcessingFee = leavingFeeText.toDoubleOrNull() ?: 0.0
    val totalSettlementDue = unpaidTuition + leavingProcessingFee
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
                .padding(bottom = 16.dp)
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
                            .background(if (isCleared) financialColors.cashContainer else financialColors.expenseContainer, RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isCleared) Icons.Default.CheckCircle else Icons.Default.Warning,
                            contentDescription = null,
                            tint = if (isCleared) financialColors.cash else financialColors.expense,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text("Student Exit & Clearance", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                        Text("${student.studentName} • Class ${student.className}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Settlement Status Card
                Card(
                    colors = CardDefaults.cardColors(containerColor = if (isCleared) financialColors.cashContainer.copy(alpha = 0.5f) else financialColors.amberContainer.copy(alpha = 0.4f)),
                    border = BorderStroke(1.dp, if (isCleared) financialColors.cash.copy(alpha = 0.4f) else financialColors.amber.copy(alpha = 0.4f)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Clearance Audit", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = if (isCleared) financialColors.cash else financialColors.amber)
                            Surface(
                                color = if (isCleared) financialColors.cashContainer else financialColors.amberContainer,
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = if (isCleared) "ALL DUES CLEARED (NOC READY)" else "OUTSTANDING DUES PENDING",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isCleared) financialColors.cash else financialColors.amber,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        if (!isCleared) {
                            Text(
                                text = "Unpaid Tuition Arrears: Rs. ${unpaidTuition.toInt()}" + if (unpaidMonths.isNotBlank()) " ($unpaidMonths)" else "",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = financialColors.amber
                            )
                        } else {
                            Text(
                                text = "No unpaid dues or arrears found. The student accounts are fully reconciled.",
                                fontSize = 12.sp,
                                color = financialColors.cash
                            )
                        }
                    }
                }

                // SLC Reason & Conduct Fields
                OutlinedTextField(
                    value = reasonText,
                    onValueChange = { reasonText = it },
                    label = { Text("Reason for Leaving") },
                    placeholder = { Text("e.g. Relocating / Completion of Studies") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )

                OutlinedTextField(
                    value = conductText,
                    onValueChange = { conductText = it },
                    label = { Text("Student Conduct & Character") },
                    placeholder = { Text("e.g. Good & Satisfactory") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )

                OutlinedTextField(
                    value = leavingFeeText,
                    onValueChange = { leavingFeeText = it.filter { ch -> ch.isDigit() } },
                    label = { Text("Optional SLC / Migration Fee (Rs)") },
                    placeholder = { Text("e.g. 500 (or leave 0)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Printable Documents Section
                Text("Official Departure Documents", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)

                // 1. Closing Fee Challan (3-Copy Voucher)
                OutlinedCard(
                    modifier = Modifier.fillMaxWidth().clickable {
                        val file = ReportExporter.exportStudentClosingChallanPdf(
                            context = context,
                            student = student,
                            monthlyArrears = unpaidTuition,
                            unpaidMonthsDescription = unpaidMonths,
                            leavingProcessingFee = leavingProcessingFee,
                            remarks = remarks
                        )
                        if (file != null) {
                            val dest = ReportExporter.savePdfToDownloads(context, file, "BLS_Closing_Challan_${student.studentName.replace(" ", "_")}")
                            Toast.makeText(context, "Closing Challan generated! ${dest.pathMessage}", Toast.LENGTH_LONG).show()
                            ReportExporter.sharePdf(context, file)
                        } else {
                            Toast.makeText(context, "Failed to compile Closing Challan", Toast.LENGTH_SHORT).show()
                        }
                    },
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier.size(36.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.ReceiptLong, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("1. Closing Fee Challan (3-Part)", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
                            Text("Landscape 3-copy settlement voucher with clearance status", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(Icons.Default.Print, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    }
                }

                // 2. School Leaving Certificate (SLC)
                OutlinedCard(
                    modifier = Modifier.fillMaxWidth().clickable {
                        val file = ReportExporter.exportSchoolLeavingCertificateToPdf(
                            context = context,
                            student = student,
                            reason = reasonText,
                            conduct = conductText
                        )
                        if (file != null) {
                            val dest = ReportExporter.savePdfToDownloads(context, file, "BLS_SLC_${student.studentName.replace(" ", "_")}")
                            Toast.makeText(context, "School Leaving Certificate generated! ${dest.pathMessage}", Toast.LENGTH_LONG).show()
                            ReportExporter.sharePdf(context, file)
                        } else {
                            Toast.makeText(context, "Failed to compile SLC Certificate", Toast.LENGTH_SHORT).show()
                        }
                    },
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier.size(36.dp).background(financialColors.cashContainer, RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.School, contentDescription = null, tint = financialColors.cash, modifier = Modifier.size(20.dp))
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("2. School Leaving Certificate (SLC)", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
                            Text("Official migration & character clearance certificate", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(Icons.Default.Print, contentDescription = null, tint = financialColors.cash, modifier = Modifier.size(18.dp))
                    }
                }

                // 3. Share WhatsApp Clearance
                if (student.contactNumber.isNotBlank()) {
                    OutlinedCard(
                        modifier = Modifier.fillMaxWidth().clickable {
                            val msg = "Dear Parent, Departure clearance record for ${student.studentName} (Class ${student.className}, Reg #${student.regNo}) has been processed at Blended Learning School (BLS). Outstanding Dues: Rs. ${totalSettlementDue.toInt()}. Please collect the official School Leaving Certificate from the school office."
                            val clean = student.contactNumber.replace(" ", "").replace("-", "")
                            val formatted = if (clean.startsWith("0")) "92" + clean.substring(1) else clean
                            val intent = Intent(Intent.ACTION_VIEW).apply {
                                data = Uri.parse("https://api.whatsapp.com/send?phone=$formatted&text=${Uri.encode(msg)}")
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            try {
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                Toast.makeText(context, "WhatsApp not installed", Toast.LENGTH_SHORT).show()
                            }
                        },
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier.size(36.dp).background(financialColors.whatsappContainer, RoundedCornerShape(8.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Share, contentDescription = null, tint = financialColors.whatsapp, modifier = Modifier.size(20.dp))
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("3. Send Clearance via WhatsApp", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
                                Text("Send departure summary & clearance notice to parent", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))

            // Footer Actions
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f).height(42.dp),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Close", color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
                }

                if (!isCleared) {
                    Button(
                        onClick = {
                            onDismiss()
                            onCollectPayment()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        modifier = Modifier.weight(1.3f).height(42.dp),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.AddCard, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.White)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Collect Due Fee", color = Color.White, fontWeight = FontWeight.SemiBold)
                    }
                }

                Button(
                    onClick = {
                        onConfirmStatusLeft()
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = financialColors.expense),
                    modifier = Modifier.weight(1.3f).height(42.dp),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.White)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Confirm Left", color = Color.White, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
