package com.example.ui.students

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
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
import com.example.util.ReportExporter
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun IndividualStudentDetailCard(
    student: Student,
    transactions: List<Transaction>,
    onClose: () -> Unit,
    onCollectFee: () -> Unit,
    onPrintChallan: () -> Unit = {},
    isWideScreen: Boolean = false,
    onUpdateStatus: ((String) -> Unit)? = null,
    onUpdatePhoto: ((String) -> Unit)? = null,
    onOpenExitSettlement: () -> Unit = {}
) {
    val context = LocalContext.current
    val financialColors = MaterialTheme.financialColors
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { onUpdatePhoto?.invoke(it.toString()) }
    }
    var statusMenuExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(if (isWideScreen) 0.dp else 12.dp)
            .testTag("student_detail_card"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(14.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Header Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f, fill = false),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (!isWideScreen) {
                        IconButton(
                            onClick = onClose,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                    Text(
                        "Student Ledger",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(
                        onClick = onPrintChallan,
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Icon(Icons.Default.Receipt, contentDescription = null, modifier = Modifier.size(13.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(3.dp))
                        Text("Challan", color = MaterialTheme.colorScheme.primary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }

                    Button(
                        onClick = onCollectFee,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Icon(Icons.Default.AddCard, contentDescription = null, modifier = Modifier.size(13.dp), tint = Color.White)
                        Spacer(modifier = Modifier.width(3.dp))
                        Text("Collect Fee", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Student Demographics Card
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Student Photo with Tap to Change
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
                                .clickable { photoPickerLauncher.launch("image/*") },
                            contentAlignment = Alignment.Center
                        ) {
                            if (student.photoUri.isNotBlank()) {
                                AsyncImage(
                                    model = student.photoUri,
                                    contentDescription = "Student Photo",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Default.AddAPhoto, contentDescription = "Add Photo", tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f), modifier = Modifier.size(20.dp))
                                    Text("Photo", fontSize = 9.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    student.studentName.uppercase(),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                
                                // Interactive Status Dropdown Menu
                                Box {
                                    Surface(
                                        color = if (student.status == DomainConstants.STATUS_ACTIVE) financialColors.cashContainer else financialColors.expenseContainer,
                                        shape = RoundedCornerShape(6.dp),
                                        modifier = Modifier.clickable { statusMenuExpanded = true }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                student.status,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = if (student.status == DomainConstants.STATUS_ACTIVE) financialColors.cash else financialColors.expense
                                            )
                                            Spacer(modifier = Modifier.width(2.dp))
                                            Icon(
                                                Icons.Default.ArrowDropDown,
                                                contentDescription = null,
                                                modifier = Modifier.size(14.dp),
                                                tint = if (student.status == DomainConstants.STATUS_ACTIVE) financialColors.cash else financialColors.expense
                                            )
                                        }
                                    }
                                    DropdownMenu(
                                        expanded = statusMenuExpanded,
                                        onDismissRequest = { statusMenuExpanded = false }
                                    ) {
                                        DomainConstants.STUDENT_STATUSES.forEach { st ->
                                            DropdownMenuItem(
                                                text = { 
                                                    Text(
                                                        st, 
                                                        fontWeight = if (st == student.status) FontWeight.Bold else FontWeight.Normal,
                                                        color = if (st == DomainConstants.STATUS_ACTIVE) financialColors.cash else financialColors.expense
                                                    ) 
                                                },
                                                onClick = {
                                                    if (st == DomainConstants.STATUS_LEFT) {
                                                        onOpenExitSettlement()
                                                    } else {
                                                        onUpdateStatus?.invoke(st)
                                                    }
                                                    statusMenuExpanded = false
                                                }
                                            )
                                        }
                                    }
                                }
                            }

                            if (student.regNo.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(3.dp))
                                Surface(
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = "Reg #${student.regNo}",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontSize = 10.sp
                                        ),
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    Spacer(modifier = Modifier.height(10.dp))

                    // Bio Details Grid
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Father / Guardian", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(student.fatherName.ifBlank { "—" }, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Class Group", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(student.className.ifBlank { "—" }, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Contact Number", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(student.contactNumber.ifBlank { "—" }, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
                                if (student.contactNumber.isNotBlank()) {
                                    IconButton(
                                        onClick = {
                                            val currentMonth = SimpleDateFormat("MMMM yyyy", Locale.US).format(Date())
                                            val monthlyFee = DomainConstants.calculateDiscountedFee(student.monthlyFee, student.discountType)
                                            ReportExporter.sendWhatsAppFeeReminder(
                                                context = context,
                                                phoneNumber = student.contactNumber,
                                                studentName = student.studentName,
                                                className = student.className,
                                                month = currentMonth,
                                                currentMonthFee = monthlyFee,
                                                arrears = 0.0,
                                                totalPayable = monthlyFee
                                            )
                                        },
                                        modifier = Modifier
                                            .size(24.dp)
                                            .background(financialColors.whatsapp, RoundedCornerShape(12.dp))
                                    ) {
                                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "WhatsApp Parent", tint = Color.White, modifier = Modifier.size(11.dp))
                                    }
                                }
                            }
                        }
                        if (student.idCardNumber.isNotEmpty()) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("B-Form / ID Card", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(student.idCardNumber, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
                            }
                        }
                    }

                    if (student.fatherIdCardNumber.isNotEmpty() || student.previousSchoolName.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(modifier = Modifier.fillMaxWidth()) {
                            if (student.fatherIdCardNumber.isNotEmpty()) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Father CNIC", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(student.fatherIdCardNumber, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
                                }
                            }
                            if (student.previousSchoolName.isNotEmpty()) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Previous School", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(student.previousSchoolName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Text("Verified Attachments:", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold), color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("School Cert: ${if (student.schoolCertAttached) "✓ Attached" else "— Pending"}", fontSize = 11.sp, color = if (student.schoolCertAttached) financialColors.cash else MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("Form-B: ${if (student.formBAttached) "✓ Attached" else "— Pending"}", fontSize = 11.sp, color = if (student.formBAttached) financialColors.cash else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("Father CNIC: ${if (student.fatherCnicAttached) "✓ Attached" else "— Pending"}", fontSize = 11.sp, color = if (student.fatherCnicAttached) financialColors.cash else MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("Photographs: ${if (student.picsAttached) "✓ Attached" else "— Pending"}", fontSize = 11.sp, color = if (student.picsAttached) financialColors.cash else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                val file = ReportExporter.exportAdmissionFormToPdf(context, student)
                                if (file != null) {
                                    val dest = ReportExporter.savePdfToDownloads(context, file, "BLS_Admission_${student.studentName.replace(" ", "_")}")
                                    Toast.makeText(context, dest.pathMessage, Toast.LENGTH_LONG).show()
                                    ReportExporter.sharePdf(context, file)
                                } else {
                                    Toast.makeText(context, "Failed to compile printable Admission Form", Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
                            modifier = Modifier.weight(1f).height(38.dp)
                        ) {
                            Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(13.dp), tint = Color.White)
                            Spacer(modifier = Modifier.width(3.dp))
                            Text("Admission", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                        }

                        Button(
                            onClick = onOpenExitSettlement,
                            colors = ButtonDefaults.buttonColors(containerColor = financialColors.payroll),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
                            modifier = Modifier.weight(1f).height(38.dp)
                        ) {
                            Icon(Icons.Default.ReceiptLong, contentDescription = null, modifier = Modifier.size(13.dp), tint = Color.White)
                            Spacer(modifier = Modifier.width(3.dp))
                            Text("Clearance", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                        }

                        OutlinedButton(
                            onClick = {
                                val file = ReportExporter.exportSchoolLeavingCertificateToPdf(context, student)
                                if (file != null) {
                                    val dest = ReportExporter.savePdfToDownloads(context, file, "BLS_SLC_${student.studentName.replace(" ", "_")}")
                                    Toast.makeText(context, "School Leaving Certificate generated! ${dest.pathMessage}", Toast.LENGTH_LONG).show()
                                    ReportExporter.sharePdf(context, file)
                                } else {
                                    Toast.makeText(context, "Failed to compile printable SLC Certificate", Toast.LENGTH_SHORT).show()
                                }
                            },
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
                            modifier = Modifier.weight(1f).height(38.dp)
                        ) {
                            Icon(Icons.Default.School, contentDescription = null, modifier = Modifier.size(13.dp), tint = MaterialTheme.colorScheme.onSurface)
                            Spacer(modifier = Modifier.width(3.dp))
                            Text("SLC Cert", color = MaterialTheme.colorScheme.onSurface, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    Spacer(modifier = Modifier.height(8.dp))

                    // Fee Summary metrics
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Monthly Tuition", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("Rs. ${student.monthlyFee.toInt()}", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
                        }
                        Column {
                            Text("Admission Fee", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("Rs. ${student.admissionFee.toInt()}", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
                        }
                        if (student.annualCharges > 0.0) {
                            Column {
                                Text("Annual Chgs", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("Rs. ${student.annualCharges.toInt()}", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
                            }
                        }
                        if (student.otherCharges > 0.0) {
                            Column {
                                Text("Other Chgs", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("Rs. ${student.otherCharges.toInt()}", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
                            }
                        }
                        Column {
                            Text("Discount Mode", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(student.discountType, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = financialColors.goldBadge)
                        }
                    }

                    val advanceBalance = remember(transactions, student) {
                        DomainConstants.calculateStudentAdvanceWallet(student, transactions)
                    }
                    if (advanceBalance > 0.0) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Surface(
                            color = financialColors.cashContainer,
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, financialColors.cash.copy(alpha = 0.4f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Icon(Icons.Default.AccountBalanceWallet, contentDescription = null, tint = financialColors.cash, modifier = Modifier.size(16.dp))
                                    Text("Advance Wallet Credit (Overpayment):", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = financialColors.cash)
                                }
                                Text("Rs. ${advanceBalance.toInt()}", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = financialColors.cash)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Payments Receipts Ledger",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        "${transactions.size} Records",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))

            if (transactions.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No payment transactions recorded yet.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    transactions.forEach { tx ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            tx.category,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 13.sp,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        if (tx.monthOfFee != null) {
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Surface(
                                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                                                shape = RoundedCornerShape(4.dp)
                                            ) {
                                                Text(
                                                    tx.monthOfFee,
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                )
                                            }
                                        }
                                    }
                                    val dateStr = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(Date(tx.date))
                                    Text(
                                        "Mode: ${tx.paymentMode} • $dateStr • ${tx.recordedBy}",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        "Rs. ${tx.amount.toInt()}",
                                        color = financialColors.cash,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    IconButton(
                                        onClick = {
                                            val file = ReportExporter.exportFeeSlipToPdf(context, tx, student)
                                            if (file != null) {
                                                val dest = ReportExporter.savePdfToDownloads(context, file, "BLS_FeeSlip_${tx.id}")
                                                Toast.makeText(context, "Re-issued Slip: ${dest.pathMessage}", Toast.LENGTH_LONG).show()
                                                ReportExporter.sharePdf(context, file)
                                            } else {
                                                Toast.makeText(context, "Failed to regenerate PDF Slip", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Print,
                                            contentDescription = "Print Fee Slip",
                                            tint = financialColors.cash,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(96.dp))
        }
    }
}
