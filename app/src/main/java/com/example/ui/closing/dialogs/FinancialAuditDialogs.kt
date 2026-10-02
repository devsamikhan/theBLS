package com.example.ui.closing.dialogs

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.*
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.*
import com.example.ui.theme.*
import com.example.util.AudioFeedback
import com.example.util.ReportExporter
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonthlyPdfSelectorDialog(
    transactions: List<Transaction>,
    onDismiss: () -> Unit,
    onExport: (String, Boolean) -> Unit
) {
    val monthsList = remember(transactions) {
        val uniqueMonths = transactions.mapNotNull { it.monthOfFee }.filter { it.isNotBlank() }.distinct().toMutableList()
        val df = SimpleDateFormat("MMMM yyyy", Locale.US)
        val cal = Calendar.getInstance()
        for (i in 0..5) {
            val formatted = df.format(cal.time)
            if (!uniqueMonths.contains(formatted)) {
                uniqueMonths.add(formatted)
            }
            cal.add(Calendar.MONTH, -1)
        }
        uniqueMonths
    }

    var selectedMonth by remember { mutableStateOf(monthsList.firstOrNull() ?: "") }
    var expanded by remember { mutableStateOf(false) }

    val monthTransactions = remember(selectedMonth, transactions) {
        transactions.filter { it.monthOfFee == selectedMonth }
    }
    val monthIncome = remember(monthTransactions) {
        monthTransactions.filter { it.isIncome }.sumOf { it.amount }
    }
    val monthExpense = remember(monthTransactions) {
        monthTransactions.filter { !it.isIncome }.sumOf { it.amount }
    }

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
                            .background(MaterialTheme.financialColors.bankContainer, RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.DateRange, contentDescription = null, tint = MaterialTheme.financialColors.bank, modifier = Modifier.size(22.dp))
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text("Monthly Summary Report", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                        Text("Monthly revenue & expenditure overview", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    "Select the billing month to aggregate student fee receipts, operational campus expenses, and net balance statement:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Month Dropdown
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = selectedMonth,
                        onValueChange = {},
                        label = { Text("Billing Cycle Month") },
                        readOnly = true,
                        shape = RoundedCornerShape(12.dp),
                        trailingIcon = {
                            IconButton(onClick = { expanded = !expanded }) {
                                Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { expanded = !expanded }
                    )
                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false },
                        modifier = Modifier.fillMaxWidth(0.88f)
                    ) {
                        monthsList.forEach { m ->
                            DropdownMenuItem(
                                text = {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(m, fontWeight = if (m == selectedMonth) FontWeight.Bold else FontWeight.Normal)
                                        if (m == selectedMonth) {
                                            Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                        }
                                    }
                                },
                                onClick = {
                                    selectedMonth = m
                                    expanded = false
                                }
                            )
                        }
                    }
                }

                // Month Summary Preview Card
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Total Transactions:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("${monthTransactions.size}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodySmall)
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Fee Inflow (+):", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.financialColors.cash)
                            Text("Rs. ${monthIncome.toInt()}", fontWeight = FontWeight.Bold, color = MaterialTheme.financialColors.cash, style = MaterialTheme.typography.bodySmall)
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Expenses Outflow (-):", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.financialColors.expense)
                            Text("Rs. ${monthExpense.toInt()}", fontWeight = FontWeight.Bold, color = MaterialTheme.financialColors.expense, style = MaterialTheme.typography.bodySmall)
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Net Surplus / Deficit:", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                            Text("Rs. ${(monthIncome - monthExpense).toInt()}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }

            // Bottom Actions
            Surface(
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f).height(48.dp)
                    ) {
                        Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }

                    Button(
                        onClick = { onExport(selectedMonth, true) },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.financialColors.cash),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1.3f).height(48.dp)
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Download")
                    }

                    Button(
                        onClick = { onExport(selectedMonth, false) },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1.1f).height(48.dp)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Share")
                    }
                }
            }
        }
    }
}



@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomDateRangeLedgerDialog(
    onDismiss: () -> Unit,
    onExport: (Long, Long, String) -> Unit
) {
    val sdf = remember { SimpleDateFormat("dd-MM-yyyy", Locale.US) }
    val cal = remember { Calendar.getInstance() }
    val todayStr = remember { sdf.format(cal.time) }
    val firstDayMonthStr = remember {
        val c = Calendar.getInstance()
        c.set(Calendar.DAY_OF_MONTH, 1)
        sdf.format(c.time)
    }

    var startDateInput by remember { mutableStateOf(firstDayMonthStr) }
    var endDateInput by remember { mutableStateOf(todayStr) }
    var selectedMode by remember { mutableStateOf("All") }
    var dateError by remember { mutableStateOf<String?>(null) }

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
                            .background(MaterialTheme.financialColors.bankContainer, RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.DateRange, contentDescription = null, tint = MaterialTheme.financialColors.bank, modifier = Modifier.size(22.dp))
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text("Custom Date-Range Ledger", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                        Text("Custom period transaction & cash-flow statement", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    "Generate cashflow statements filtered between any two dates and reconciled by Cash Box or Bank Account.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Quick Presets
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SuggestionChip(
                        onClick = {
                            val c = Calendar.getInstance()
                            c.set(Calendar.DAY_OF_MONTH, 1)
                            startDateInput = sdf.format(c.time)
                            endDateInput = sdf.format(Calendar.getInstance().time)
                            dateError = null
                        },
                        label = { Text("This Month") }
                    )
                    SuggestionChip(
                        onClick = {
                            val c = Calendar.getInstance()
                            c.add(Calendar.DAY_OF_YEAR, -30)
                            startDateInput = sdf.format(c.time)
                            endDateInput = sdf.format(Calendar.getInstance().time)
                            dateError = null
                        },
                        label = { Text("Last 30 Days") }
                    )
                    SuggestionChip(
                        onClick = {
                            val now = sdf.format(Calendar.getInstance().time)
                            startDateInput = now
                            endDateInput = now
                            dateError = null
                        },
                        label = { Text("Today") }
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = startDateInput,
                        onValueChange = { startDateInput = it; dateError = null },
                        label = { Text("From Date") },
                        placeholder = { Text("01-10-2026") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    )

                    OutlinedTextField(
                        value = endDateInput,
                        onValueChange = { endDateInput = it; dateError = null },
                        label = { Text("To Date") },
                        placeholder = { Text("15-10-2026") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    )
                }

                dateError?.let {
                    Text(it, color = MaterialTheme.financialColors.expense, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                }

                Text("Payment Channel Filter:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("All", DomainConstants.MODE_CASH, DomainConstants.MODE_BANK).forEach { mode ->
                        FilterChip(
                            selected = selectedMode == mode,
                            onClick = { selectedMode = mode },
                            label = { Text(if (mode == "All") "All Modes" else mode) },
                            shape = RoundedCornerShape(8.dp)
                        )
                    }
                }
            }

            // Bottom Actions
            Surface(
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f).height(48.dp)
                    ) {
                        Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }

                    Button(
                        onClick = {
                            try {
                                val sDate = sdf.parse(startDateInput.trim()) ?: Date()
                                val eDate = sdf.parse(endDateInput.trim()) ?: Date()
                                val endMillis = eDate.time + (24 * 60 * 60 * 1000L - 1)
                                onExport(sDate.time, endMillis, selectedMode)
                            } catch (e: Exception) {
                                dateError = "Please enter dates in DD-MM-YYYY format (e.g. 01-10-2026)"
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1.8f).height(48.dp)
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Generate Ledger")
                    }
                }
            }
        }
    }
}



@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpenseBreakdownDialog(
    transactions: List<Transaction>,
    onDismiss: () -> Unit,
    onExport: (String, List<Transaction>) -> Unit
) {
    val monthsList = remember(transactions) {
        val uniqueMonths = transactions.filter { !it.isIncome }.mapNotNull { it.monthOfFee }.filter { it.isNotBlank() }.distinct().toMutableList()
        listOf("Full Academic Year (All Expenses)") + uniqueMonths
    }
    var selectedPeriod by remember { mutableStateOf(monthsList.firstOrNull() ?: "Full Academic Year (All Expenses)") }
    var expanded by remember { mutableStateOf(false) }

    val filteredTransactions = remember(selectedPeriod, transactions) {
        if (selectedPeriod.startsWith("Full")) {
            transactions.filter { !it.isIncome }
        } else {
            transactions.filter { !it.isIncome && it.monthOfFee == selectedPeriod }
        }
    }
    val totalExpenseAmount = remember(filteredTransactions) {
        filteredTransactions.sumOf { it.amount }
    }

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
                            .background(MaterialTheme.financialColors.expenseContainer, RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.PieChart, contentDescription = null, tint = MaterialTheme.financialColors.expense, modifier = Modifier.size(22.dp))
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text("Expense Category Breakdown", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                        Text("Campus expenditures & operating cost analysis", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    "Generates visual category breakdown showing exact budget percentage distribution across bills, rent, salaries, and stationeries.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = selectedPeriod,
                        onValueChange = {},
                        label = { Text("Expense Analysis Period") },
                        readOnly = true,
                        shape = RoundedCornerShape(12.dp),
                        trailingIcon = {
                            IconButton(onClick = { expanded = !expanded }) {
                                Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { expanded = !expanded }
                    )
                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false },
                        modifier = Modifier.fillMaxWidth(0.88f)
                    ) {
                        monthsList.forEach { m ->
                            DropdownMenuItem(
                                text = {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(m, fontWeight = if (m == selectedPeriod) FontWeight.Bold else FontWeight.Normal)
                                        if (m == selectedPeriod) {
                                            Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                        }
                                    }
                                },
                                onClick = {
                                    selectedPeriod = m
                                    expanded = false
                                }
                            )
                        }
                    }
                }

                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Total Expense Transactions:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("${filteredTransactions.size} vouchers", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodySmall)
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Total Expenses Sum:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.financialColors.expense)
                            Text("Rs. ${totalExpenseAmount.toInt()}", fontWeight = FontWeight.Bold, color = MaterialTheme.financialColors.expense, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }

            // Bottom Actions
            Surface(
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f).height(48.dp)
                    ) {
                        Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }

                    Button(
                        onClick = { onExport(selectedPeriod, filteredTransactions) },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.financialColors.expense),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1.8f).height(48.dp)
                    ) {
                        Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Generate Report")
                    }
                }
            }
        }
    }
}

