package com.example.ui.students

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.example.data.DomainConstants
import com.example.data.Student
import com.example.ui.theme.financialColors
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun BulkFeeReceptionDialog(
    studentsList: List<Student>,
    onDismiss: () -> Unit,
    onConfirm: (List<Triple<Student, Double, String>>, String, String) -> Unit
) {
    var selectedStudentIds by remember { mutableStateOf(emptySet<Int>()) }
    var searchBulkQuery by remember { mutableStateOf("") }
    var selectedMonth by remember { mutableStateOf("") }
    var selectedMonthExpanded by remember { mutableStateOf(false) }
    var paymentMode by remember { mutableStateOf(DomainConstants.MODE_CASH) }
    var remarks by remember { mutableStateOf("Bulk fee received") }
    var customAmounts by remember { mutableStateOf(emptyMap<Int, String>()) }

    val monthsList = remember {
        val list = mutableListOf<String>()
        val cal = Calendar.getInstance()
        val mSdf = SimpleDateFormat("MMMM yyyy", Locale.US)
        cal.add(Calendar.MONTH, -3)
        for (i in 0..6) {
            list.add(mSdf.format(cal.time))
            cal.add(Calendar.MONTH, 1)
        }
        list
    }

    LaunchedEffect(Unit) {
        val currentMonthYear = SimpleDateFormat("MMMM yyyy", Locale.US).format(Date())
        selectedMonth = currentMonthYear
    }

    val filteredStudents = remember(studentsList, searchBulkQuery) {
        if (searchBulkQuery.isBlank()) {
            studentsList
        } else {
            studentsList.filter {
                it.studentName.contains(searchBulkQuery, ignoreCase = true) ||
                it.className.contains(searchBulkQuery, ignoreCase = true) ||
                it.id.toString() == searchBulkQuery
            }
        }
    }

    fun getEffectiveAmount(s: Student): Double {
        val standard = DomainConstants.calculateDiscountedFee(s.monthlyFee, s.discountType)
        val customStr = customAmounts[s.id] ?: return standard
        return customStr.toDoubleOrNull() ?: standard
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier
            .imePadding()
            .widthIn(max = 640.dp)
            .fillMaxWidth(0.94f)
            .padding(16.dp),
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Bulk Fee Reception",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close Dialog")
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Select multiple students to batch process and record fee payments for the active cycle.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = searchBulkQuery,
                    onValueChange = { searchBulkQuery = it },
                    placeholder = { Text("Search") },
                    leadingIcon = { Icon(Icons.Default.FilterList, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                    )
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.clickable {
                            if (selectedStudentIds.size == filteredStudents.size && filteredStudents.isNotEmpty()) {
                                selectedStudentIds = emptySet()
                            } else {
                                selectedStudentIds = filteredStudents.map { it.id }.toSet()
                            }
                        },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Checkbox(
                            checked = selectedStudentIds.isNotEmpty() && selectedStudentIds.size == filteredStudents.size,
                            onCheckedChange = { checked ->
                                selectedStudentIds = if (checked) {
                                    filteredStudents.map { it.id }.toSet()
                                } else {
                                    emptySet()
                                }
                            }
                        )
                        Text(
                            text = "Select All (${filteredStudents.size} listed)",
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    if (selectedStudentIds.isNotEmpty()) {
                        TextButton(onClick = { selectedStudentIds = emptySet() }) {
                            Text("Clear Selection (${selectedStudentIds.size})", color = MaterialTheme.financialColors.expense, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(filteredStudents, key = { it.id }) { s ->
                        val isChecked = selectedStudentIds.contains(s.id)
                        val standardFee = remember(s) {
                            DomainConstants.calculateDiscountedFee(s.monthlyFee, s.discountType).toInt()
                        }
                        
                        val overrideText = customAmounts[s.id] ?: standardFee.toString()

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isChecked) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f) else Color.Transparent)
                                .clickable {
                                    selectedStudentIds = if (isChecked) {
                                        selectedStudentIds - s.id
                                    } else {
                                        selectedStudentIds + s.id
                                    }
                                }
                                .padding(vertical = 4.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Checkbox(
                                    checked = isChecked,
                                    onCheckedChange = { checked ->
                                        selectedStudentIds = if (checked) {
                                            selectedStudentIds + s.id
                                        } else {
                                            selectedStudentIds - s.id
                                        }
                                    }
                                )
                                Column {
                                    Text(
                                        text = s.studentName,
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                        color = if (isChecked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "Class: ${s.className} | Disc: ${s.discountType}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier.padding(start = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "Rs.",
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                OutlinedTextField(
                                    value = overrideText,
                                    onValueChange = { valText ->
                                        customAmounts = customAmounts + (s.id to valText)
                                    },
                                    singleLine = true,
                                    textStyle = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.width(76.dp).height(40.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                                    )
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f), thickness = 1.dp)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        OutlinedTextField(
                            value = selectedMonth,
                            onValueChange = {},
                            label = { Text("Fee Month") },
                            readOnly = true,
                            textStyle = MaterialTheme.typography.bodySmall,
                            trailingIcon = {
                                IconButton(onClick = { selectedMonthExpanded = !selectedMonthExpanded }) {
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(16.dp))
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                        DropdownMenu(
                            expanded = selectedMonthExpanded,
                            onDismissRequest = { selectedMonthExpanded = false },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            monthsList.forEach { m ->
                                DropdownMenuItem(
                                    text = { Text(m, style = MaterialTheme.typography.bodySmall) },
                                    onClick = { selectedMonth = m; selectedMonthExpanded = false }
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier
                            .weight(1.2f)
                            .height(48.dp)
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(4.dp))
                            .padding(2.dp),
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Card(
                            onClick = { paymentMode = DomainConstants.MODE_CASH },
                            shape = RoundedCornerShape(4.dp),
                            colors = CardDefaults.cardColors(containerColor = if (paymentMode == DomainConstants.MODE_CASH) MaterialTheme.colorScheme.primary else Color.Transparent),
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        ) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(
                                    text = "Cash Box",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = if (paymentMode == DomainConstants.MODE_CASH) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Card(
                            onClick = { paymentMode = DomainConstants.MODE_BANK },
                            shape = RoundedCornerShape(4.dp),
                            colors = CardDefaults.cardColors(containerColor = if (paymentMode == DomainConstants.MODE_BANK) MaterialTheme.financialColors.bank else Color.Transparent),
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        ) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(
                                    text = "Bank",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = if (paymentMode == DomainConstants.MODE_BANK) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = remarks,
                    onValueChange = { remarks = it },
                    label = { Text("General Remarks Memo") },
                    textStyle = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.fillMaxWidth()
                )

                val totalBatchFeeValue = remember(selectedStudentIds, customAmounts, studentsList) {
                    val targetStudents = studentsList.filter { selectedStudentIds.contains(it.id) }
                    targetStudents.sumOf { getEffectiveAmount(it) }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            if (paymentMode == DomainConstants.MODE_CASH) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f) else MaterialTheme.financialColors.bankContainer.copy(alpha = 0.25f),
                            RoundedCornerShape(6.dp)
                        )
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Total to Receive (${selectedStudentIds.size} Selected):",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Rs. ${totalBatchFeeValue.toInt()}",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                        color = if (paymentMode == DomainConstants.MODE_CASH) MaterialTheme.colorScheme.primary else MaterialTheme.financialColors.bank
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (selectedStudentIds.isNotEmpty() && selectedMonth.isNotBlank()) {
                        val batchList = studentsList.filter { selectedStudentIds.contains(it.id) }.map { s ->
                            Triple(s, getEffectiveAmount(s), selectedMonth)
                        }
                        onConfirm(batchList, paymentMode, remarks)
                    }
                },
                enabled = selectedStudentIds.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (paymentMode == DomainConstants.MODE_CASH) MaterialTheme.colorScheme.primary else MaterialTheme.financialColors.bank
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Process Payments (${selectedStudentIds.size})", color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    )
}
