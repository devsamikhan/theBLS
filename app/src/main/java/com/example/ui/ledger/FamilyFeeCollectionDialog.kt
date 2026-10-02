package com.example.ui.ledger

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.DomainConstants
import com.example.data.Student
import com.example.ui.AppViewModel
import com.example.util.CurrencyVisualTransformation
import com.example.ui.theme.*
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FamilyFeeCollectionDialog(
    viewModel: AppViewModel,
    onDismiss: () -> Unit,
    onSuccess: (fatherName: String, phone: String, familyVoucherNo: String, List<Triple<Student, Double, String>>, Double, String) -> Unit
) {
    val allStudents by viewModel.studentsList.collectAsStateWithLifecycle()
    val allTransactions by viewModel.transactionsList.collectAsStateWithLifecycle()
    val activeStudents = remember(allStudents) { allStudents.filter { it.status == DomainConstants.STATUS_ACTIVE } }

    var searchQuery by remember { mutableStateOf("") }
    var selectedFatherName by remember { mutableStateOf<String?>(null) }
    var selectedMonth by remember { mutableStateOf(SimpleDateFormat("MMMM yyyy", Locale.US).format(Date())) }
    var selectedMonthExpanded by remember { mutableStateOf(false) }
    var paymentMode by remember { mutableStateOf(DomainConstants.MODE_CASH) }

    val currentDay = remember { Calendar.getInstance().get(Calendar.DAY_OF_MONTH) }
    val isAfter8th = currentDay > 8

    // Grouping by father
    val familiesMap = remember(activeStudents) {
        activeStudents.groupBy { it.fatherName.trim().uppercase() }
    }

    // Filter matching families
    val matchingFathers = remember(searchQuery, familiesMap) {
        if (searchQuery.isBlank()) emptyList() else {
            familiesMap.keys.filter { fName ->
                fName.contains(searchQuery, ignoreCase = true) ||
                familiesMap[fName]?.any { s -> 
                    s.fatherIdCardNumber.contains(searchQuery) || 
                    s.contactNumber.contains(searchQuery) ||
                    s.studentName.contains(searchQuery, ignoreCase = true)
                } == true
            }.take(6)
        }
    }

    val selectedFamilyStudents = remember(selectedFatherName, familiesMap) {
        selectedFatherName?.let { familiesMap[it] } ?: emptyList()
    }

    // Multi-month unpaid arrears per sibling
    val siblingArrearsMap = remember(selectedFamilyStudents, allTransactions) {
        selectedFamilyStudents.associate { s ->
            val sTx = allTransactions.filter { it.studentId == s.id }
            val discounted = DomainConstants.calculateDiscountedFee(s.monthlyFee, s.discountType)
            s.id to DomainConstants.calculateMultiMonthArrears(s.admissionDate, discounted, sTx)
        }
    }

    // Map of studentId -> whether selected
    var selectedSiblingsMap by remember(selectedFamilyStudents) {
        mutableStateOf(selectedFamilyStudents.associate { it.id to true })
    }

    // Map of studentId -> entered fee amount (default to tuition + arrears)
    var siblingFeeAmounts by remember(selectedFamilyStudents, siblingArrearsMap) {
        mutableStateOf(selectedFamilyStudents.associate { s ->
            val discounted = DomainConstants.calculateDiscountedFee(s.monthlyFee, s.discountType)
            val arrears = siblingArrearsMap[s.id] ?: 0.0
            s.id to (discounted + arrears).toInt().toString()
        })
    }

    // Map of studentId -> late fine
    var siblingFines by remember(selectedFamilyStudents, isAfter8th) {
        mutableStateOf(selectedFamilyStudents.associate { s ->
            s.id to (if (isAfter8th) "300" else "0")
        })
    }

    val monthsList = remember {
        val list = mutableListOf<String>()
        val cal = Calendar.getInstance()
        val mSdf = SimpleDateFormat("MMMM yyyy", Locale.US)
        cal.add(Calendar.MONTH, -2)
        for (i in 0..5) {
            list.add(mSdf.format(cal.time))
            cal.add(Calendar.MONTH, 1)
        }
        list
    }

    val totalCalculated = remember(selectedSiblingsMap, siblingFeeAmounts, siblingFines) {
        selectedFamilyStudents.filter { selectedSiblingsMap[it.id] == true }.sumOf { s ->
            val fee = siblingFeeAmounts[s.id]?.toDoubleOrNull() ?: 0.0
            val fine = siblingFines[s.id]?.toDoubleOrNull() ?: 0.0
            fee + fine
        }
    }

    val currencyFormat = remember { NumberFormat.getNumberInstance(Locale.US) }

    val textFieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = MaterialTheme.colorScheme.onSurface,
        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
        focusedBorderColor = MaterialTheme.colorScheme.primary,
        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
        focusedContainerColor = Color.Transparent,
        unfocusedContainerColor = Color.Transparent
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.90f),
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
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(MaterialTheme.financialColors.cashContainer, RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Group,
                                contentDescription = null,
                                tint = MaterialTheme.financialColors.cash,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Column {
                            Text(
                                "Family / Sibling Combined Fee",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                "Combined fee collection for enrolled siblings",
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

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 12.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
                )

                if (selectedFatherName == null) {
                    // Search Family Section
                    Text(
                        "Search Family by Father Name, CNIC or Phone:",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search father name, phone, or CNIC...") },
                        leadingIcon = {
                            Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        },
                        singleLine = true,
                        colors = textFieldColors,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    if (matchingFathers.isEmpty() && searchQuery.isNotBlank()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    Icons.Default.PersonSearch,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                    modifier = Modifier.size(40.dp)
                                )
                                Text(
                                    "No family record found. Please verify father name or mobile number.",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    } else if (searchQuery.isBlank()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    Icons.Default.FamilyRestroom,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                    modifier = Modifier.size(48.dp)
                                )
                                Text(
                                    "Enter father name, mobile number, or CNIC above to search",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(matchingFathers) { fName ->
                                val sibs = familiesMap[fName] ?: emptyList()
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { selectedFatherName = fName },
                                    shape = RoundedCornerShape(14.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(14.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                fName,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                style = MaterialTheme.typography.titleSmall
                                            )
                                            Text(
                                                "Contact: ${sibs.firstOrNull()?.contactNumber ?: "N/A"}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                "Enrolled: ${sibs.joinToString(", ") { "${it.studentName} (${it.className})" }}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = MaterialTheme.financialColors.cashContainer,
                                            border = BorderStroke(1.dp, MaterialTheme.financialColors.cash.copy(alpha = 0.3f))
                                        ) {
                                            Text(
                                                "${sibs.size} Students",
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 11.5.sp,
                                                color = MaterialTheme.financialColors.cash
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // Selected Family Sibling List & Collection
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                "Father: $selectedFatherName",
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                "Phone: ${selectedFamilyStudents.firstOrNull()?.contactNumber ?: ""}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        TextButton(onClick = { selectedFatherName = null }) {
                            Text("Change Family", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    if (isAfter8th) {
                        Surface(
                            color = MaterialTheme.financialColors.amber.copy(alpha = 0.12f),
                            border = BorderStroke(1.dp, MaterialTheme.financialColors.amber.copy(alpha = 0.3f)),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Schedule, contentDescription = null, tint = MaterialTheme.financialColors.amber, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    "Due date (08th) has passed. Rs. 300 late fee surcharge applied per student.",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.financialColors.amber,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(selectedFamilyStudents) { student ->
                            val isSelected = selectedSiblingsMap[student.id] ?: true
                            val feeVal = siblingFeeAmounts[student.id] ?: ""
                            val fineVal = siblingFines[student.id] ?: "0"

                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isSelected) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f) else MaterialTheme.colorScheme.surface
                                ),
                                border = BorderStroke(
                                    1.dp,
                                    if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.4f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
                                )
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Checkbox(
                                            checked = isSelected,
                                            onCheckedChange = { checked ->
                                                selectedSiblingsMap = selectedSiblingsMap.toMutableMap().apply { put(student.id, checked) }
                                            },
                                            colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary)
                                        )
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                student.studentName,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                style = MaterialTheme.typography.titleSmall
                                            )
                                            Text(
                                                "Class: ${student.className} • Roll#: ${student.regNo}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            val arr = siblingArrearsMap[student.id] ?: 0.0
                                            val disc = DomainConstants.calculateDiscountedFee(student.monthlyFee, student.discountType)
                                            if (arr > 0) {
                                                Text(
                                                    "Monthly: Rs. ${disc.toInt()} • ⚠️ Arrears: Rs. ${arr.toInt()}",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.financialColors.expense
                                                )
                                            } else {
                                                Text(
                                                    "Monthly Tuition: Rs. ${disc.toInt()} (No Arrears)",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }

                                    if (isSelected) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(top = 8.dp),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            OutlinedTextField(
                                                value = feeVal,
                                                onValueChange = { newFee ->
                                                    siblingFeeAmounts = siblingFeeAmounts.toMutableMap().apply { put(student.id, newFee.filter { it.isDigit() }) }
                                                },
                                                label = { Text("Fee (Rs)", fontSize = 11.sp) },
                                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                                visualTransformation = CurrencyVisualTransformation(),
                                                shape = RoundedCornerShape(10.dp),
                                                colors = textFieldColors,
                                                modifier = Modifier.weight(1f)
                                            )

                                            OutlinedTextField(
                                                value = fineVal,
                                                onValueChange = { newFine ->
                                                    siblingFines = siblingFines.toMutableMap().apply { put(student.id, newFine.filter { it.isDigit() }) }
                                                },
                                                label = { Text("Fine (Rs)", fontSize = 11.sp) },
                                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                                visualTransformation = CurrencyVisualTransformation(),
                                                shape = RoundedCornerShape(10.dp),
                                                colors = textFieldColors,
                                                modifier = Modifier.weight(0.9f)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Month & Mode
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(modifier = Modifier.weight(1f)) {
                            OutlinedTextField(
                                value = selectedMonth,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Billing Month") },
                                trailingIcon = {
                                    IconButton(onClick = { selectedMonthExpanded = !selectedMonthExpanded }) {
                                        Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    }
                                },
                                shape = RoundedCornerShape(12.dp),
                                colors = textFieldColors,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Box(
                                modifier = Modifier
                                    .matchParentSize()
                                    .clickable { selectedMonthExpanded = !selectedMonthExpanded }
                            )
                            DropdownMenu(
                                expanded = selectedMonthExpanded,
                                onDismissRequest = { selectedMonthExpanded = false }
                            ) {
                                monthsList.forEach { m ->
                                    DropdownMenuItem(
                                        text = { Text(m) },
                                        onClick = { selectedMonth = m; selectedMonthExpanded = false }
                                    )
                                }
                            }
                        }

                        SingleChoiceSegmentedButtonRow(modifier = Modifier.weight(1f).align(Alignment.CenterVertically)) {
                            SegmentedButton(
                                selected = paymentMode == DomainConstants.MODE_CASH,
                                onClick = { paymentMode = DomainConstants.MODE_CASH },
                                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                            ) {
                                Text("Cash Box", fontSize = 11.sp)
                            }
                            SegmentedButton(
                                selected = paymentMode == DomainConstants.MODE_BANK,
                                onClick = { paymentMode = DomainConstants.MODE_BANK },
                                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                            ) {
                                Text("Bank", fontSize = 11.sp)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Grand Total & Submit
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Total Sibling Fee:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                "Rs. ${currencyFormat.format(totalCalculated)}",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.financialColors.cash
                            )
                        }

                        Button(
                            onClick = {
                                val selectedSiblings = selectedFamilyStudents.filter { selectedSiblingsMap[it.id] == true }
                                val resultList = selectedSiblings.map { s ->
                                    val f = siblingFeeAmounts[s.id]?.toDoubleOrNull() ?: 0.0
                                    val fine = siblingFines[s.id]?.toDoubleOrNull() ?: 0.0
                                    Triple(s, f + fine, selectedMonth)
                                }
                                val familyVoucher = "FAM-${System.currentTimeMillis().toString().takeLast(6)}"
                                onSuccess(
                                    selectedFatherName ?: "",
                                    selectedFamilyStudents.firstOrNull()?.contactNumber ?: "",
                                    familyVoucher,
                                    resultList,
                                    totalCalculated,
                                    paymentMode
                                )
                            },
                            enabled = totalCalculated > 0.0,
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.financialColors.cash),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.height(48.dp)
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Confirm & Issue Family Slip", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
