package com.example.ui.students

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.financialColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudentsFilterBottomSheet(
    onDismiss: () -> Unit,
    statusFilter: String,
    onStatusFilterChange: (String) -> Unit,
    filterCategory: StudentFilterCategory,
    onFilterCategoryChange: (StudentFilterCategory) -> Unit,
    classFilter: String,
    onClassFilterChange: (String) -> Unit,
    selectedAlphabet: String,
    onSelectAlphabet: (String) -> Unit,
    availableClasses: List<String>,
    studentSortField: StudentSortField,
    studentSortAscending: Boolean,
    onSortChange: (StudentSortField, Boolean) -> Unit,
    onResetFilters: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "Filter & Sort Students",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        "Refine student directory view",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TextButton(onClick = onResetFilters) {
                    Text("Reset All", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))

            // 1. Enrollment Status
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Enrollment Status", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("Active", "Left", "All").forEach { status ->
                        val isSelected = statusFilter == status
                        FilterChip(
                            selected = isSelected,
                            onClick = { onStatusFilterChange(status) },
                            label = { Text(if (status == "Left") "Alumni" else status) },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // 2. Fee Payment Status
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Fee Payment Status", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = filterCategory == StudentFilterCategory.ALL,
                        onClick = { onFilterCategoryChange(StudentFilterCategory.ALL) },
                        label = { Text("All") },
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = filterCategory == StudentFilterCategory.DEFAULTERS_ONLY,
                        onClick = { onFilterCategoryChange(StudentFilterCategory.DEFAULTERS_ONLY) },
                        label = { Text("Overdue") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.financialColors.expenseContainer,
                            selectedLabelColor = MaterialTheme.financialColors.expense
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1.2f)
                    )
                    FilterChip(
                        selected = filterCategory == StudentFilterCategory.PAID_THIS_MONTH,
                        onClick = { onFilterCategoryChange(StudentFilterCategory.PAID_THIS_MONTH) },
                        label = { Text("Paid") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.financialColors.cashContainer,
                            selectedLabelColor = MaterialTheme.financialColors.cash
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // 3. Class Filter
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Class Filter", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    availableClasses.forEach { cls ->
                        FilterChip(
                            selected = classFilter == cls,
                            onClick = { onClassFilterChange(cls) },
                            label = { Text(cls, fontSize = 11.sp) },
                            shape = RoundedCornerShape(8.dp)
                        )
                    }
                }
            }

            // 4. Sort Order
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Sort Order", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        StudentSortField.NAME to "Name",
                        StudentSortField.DATE to "Date",
                        StudentSortField.AMOUNT to "Fee"
                    ).forEach { (field, label) ->
                        val isSelected = studentSortField == field
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                if (isSelected) {
                                    onSortChange(field, !studentSortAscending)
                                } else {
                                    onSortChange(field, true)
                                }
                            },
                            label = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(label)
                                    if (isSelected) {
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Icon(
                                            imageVector = if (studentSortAscending) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                                            contentDescription = null,
                                            modifier = Modifier.size(12.dp)
                                        )
                                    }
                                }
                            },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // 5. Alphabet Initial Strip (Compact)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Name Starting With (A-Z)", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val alphabets = listOf("All") + ('A'..'Z').map { it.toString() }
                    alphabets.forEach { letter ->
                        val isSelected = (letter == "All" && selectedAlphabet.isEmpty()) || letter.equals(selectedAlphabet, ignoreCase = true)
                        Surface(
                            onClick = { onSelectAlphabet(if (letter == "All") "" else letter) },
                            shape = RoundedCornerShape(6.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            border = BorderStroke(0.5.dp, if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)),
                            modifier = Modifier.defaultMinSize(minWidth = 30.dp, minHeight = 26.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)) {
                                Text(
                                    text = letter,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Button(
                onClick = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Apply & Close", fontWeight = FontWeight.Bold)
            }
        }
    }
}
