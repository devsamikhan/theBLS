package com.example.ui.students

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.ui.theme.financialColors

@Composable
fun BulkClassPromotionDialog(
    availableClasses: List<String>,
    onDismiss: () -> Unit,
    onPromote: (fromClass: String, toClass: String, applyAnnualCharges: Boolean, annualChargesAmount: Double?) -> Unit
) {
    val cleanClasses = remember(availableClasses) {
        availableClasses.filter { it != "All Classes" }
    }
    var fromClass by remember { mutableStateOf(cleanClasses.firstOrNull() ?: "") }
    var toClass by remember { mutableStateOf("") }
    var fromExpanded by remember { mutableStateOf(false) }
    var toExpanded by remember { mutableStateOf(false) }
    var applyAnnualCharges by remember { mutableStateOf(false) }
    var annualChargesText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.School, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Bulk Class Promotion & Session Rollover", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "Promote all enrolled students of a class to the next grade or graduate them to Alumni at the end of the academic session.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Current Class Picker
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = fromClass,
                        onValueChange = {},
                        label = { Text("Current Class (From)") },
                        readOnly = true,
                        trailingIcon = {
                            IconButton(onClick = { fromExpanded = !fromExpanded }) {
                                Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    DropdownMenu(
                        expanded = fromExpanded,
                        onDismissRequest = { fromExpanded = false },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        cleanClasses.forEach { cls ->
                            DropdownMenuItem(
                                text = { Text(cls) },
                                onClick = {
                                    fromClass = cls
                                    fromExpanded = false
                                }
                            )
                        }
                    }
                }

                // Next Class (or Graduation)
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = toClass,
                        onValueChange = { toClass = it },
                        label = { Text("Promoted Class (To) or type new") },
                        placeholder = { Text("e.g. Class Two, Alumni") },
                        trailingIcon = {
                            IconButton(onClick = { toExpanded = !toExpanded }) {
                                Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    DropdownMenu(
                        expanded = toExpanded,
                        onDismissRequest = { toExpanded = false },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        cleanClasses.forEach { cls ->
                            DropdownMenuItem(
                                text = { Text(cls) },
                                onClick = {
                                    toClass = cls
                                    toExpanded = false
                                }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("🎓 Alumni (Graduated)", color = MaterialTheme.financialColors.goldBadge, fontWeight = FontWeight.Bold) },
                            onClick = {
                                toClass = "Alumni"
                                toExpanded = false
                            }
                        )
                    }
                }

                // Annual Session Charges Rollover Option
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable { applyAnnualCharges = !applyAnnualCharges }
                        ) {
                            Checkbox(
                                checked = applyAnnualCharges,
                                onCheckedChange = { applyAnnualCharges = it }
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                "Auto-Bill Annual Session Charges",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        if (applyAnnualCharges) {
                            OutlinedTextField(
                                value = annualChargesText,
                                onValueChange = { annualChargesText = it },
                                label = { Text("Annual Session Charges per Student (Rs.)") },
                                placeholder = { Text("e.g. 2500 (or leave empty to retain current rate)") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "ℹ️ Safe Operation Notice:",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "• All admission records, historical ledgers, and transactions remain intact.\n• Only the class label on each student profile will be transitioned from '$fromClass' to '$toClass'.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (fromClass.isNotBlank() && toClass.isNotBlank() && fromClass != toClass) {
                        onPromote(fromClass, toClass, applyAnnualCharges, annualChargesText.toDoubleOrNull())
                    }
                },
                enabled = fromClass.isNotBlank() && toClass.isNotBlank() && fromClass != toClass,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text("Promote All Students")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
