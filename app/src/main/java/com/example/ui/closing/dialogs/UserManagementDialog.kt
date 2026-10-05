package com.example.ui.closing.dialogs

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.AppUser
import com.example.data.DomainConstants
import com.example.ui.AppViewModel
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserManagementDialog(
    viewModel: AppViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val users by viewModel.usersList.collectAsStateWithLifecycle()

    var showAddUserDialog by remember { mutableStateOf(false) }
    var userToShare by remember { mutableStateOf<AppUser?>(null) }
    var showWipeConfirmDialog by remember { mutableStateOf(false) }
    var isWipingData by remember { mutableStateOf(false) }

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
                .padding(bottom = 24.dp)
        ) {
            // Header Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.AdminPanelSettings,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Column {
                        Text(
                            "Staff & Access Control",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            "Super User: ${DomainConstants.SUPER_ADMIN_EMAIL}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp
                        )
                    }
                }

                IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Top Action Buttons (Add Staff Member & Wipe Dummy Data)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { showAddUserDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f).height(40.dp)
                    ) {
                        Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Add Member", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }

                    OutlinedButton(
                        onClick = { showWipeConfirmDialog = true },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.financialColors.expense),
                        border = BorderStroke(1.dp, MaterialTheme.financialColors.expense.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f).height(40.dp)
                    ) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Wipe Test Data", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }

                // Super User Badge Card
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(MaterialTheme.colorScheme.primary, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                            }
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text("Master Super Admin", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
                                    Surface(
                                        color = MaterialTheme.colorScheme.primary,
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text("Fixed", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp))
                                    }
                                }
                                Text(DomainConstants.SUPER_ADMIN_EMAIL, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }

                // Authorized Staff Accounts List
                Text(
                    "AUTHORIZED STAFF ACCOUNTS (${users.size})",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (users.isEmpty()) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(Icons.Default.GroupOff, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f), modifier = Modifier.size(36.dp))
                            Spacer(modifier = Modifier.height(6.dp))
                            Text("No Staff Accounts Added Yet", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                            Text("Click 'Add Member' to create staff access for Admin or Accountant.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                        }
                    }
                } else {
                    users.forEach { user ->
                        StaffUserCard(
                            user = user,
                            onToggleStatus = { isActive -> viewModel.toggleUserStatus(user, isActive) },
                            onShareCredentials = { userToShare = user },
                            onDelete = { viewModel.deleteStaffUser(user) }
                        )
                    }
                }
            }
        }
    }

    // Add Staff User Modal Dialog
    if (showAddUserDialog) {
        AddStaffUserDialog(
            onDismiss = { showAddUserDialog = false },
            onConfirm = { name, email, role, pin ->
                viewModel.createStaffUser(
                    name = name,
                    email = email,
                    role = role,
                    pin = pin,
                    onSuccess = { createdUser ->
                        Toast.makeText(context, "Account created for ${createdUser.name}!", Toast.LENGTH_SHORT).show()
                        showAddUserDialog = false
                        userToShare = createdUser
                    },
                    onError = { err ->
                        Toast.makeText(context, err, Toast.LENGTH_SHORT).show()
                    }
                )
            }
        )
    }

    // Share Credentials Dialog (WhatsApp & Email options)
    userToShare?.let { user ->
        ShareCredentialsDialog(
            user = user,
            onDismiss = { userToShare = null },
            context = context
        )
    }

    // Wipe Dummy Data Confirmation
    if (showWipeConfirmDialog) {
        AlertDialog(
            onDismissRequest = { if (!isWipingData) showWipeConfirmDialog = false },
            title = { Text("Wipe All Dummy Data?") },
            text = {
                Text("This will permanently clear all test students, test fee transactions, and closings from both local storage and Firebase Cloud.\n\nOnly official user accounts will be kept. Are you sure you want to proceed?")
            },
            confirmButton = {
                Button(
                    onClick = {
                        isWipingData = true
                        viewModel.wipeAllDummyData { success ->
                            isWipingData = false
                            showWipeConfirmDialog = false
                            if (success) {
                                Toast.makeText(context, "All dummy records successfully removed!", Toast.LENGTH_LONG).show()
                            } else {
                                Toast.makeText(context, "Failed to completely wipe cloud data.", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.financialColors.expense)
                ) {
                    if (isWipingData) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Text("Confirm & Wipe")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showWipeConfirmDialog = false }, enabled = !isWipingData) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun StaffUserCard(
    user: AppUser,
    onToggleStatus: (Boolean) -> Unit,
    onShareCredentials: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(
                        shape = CircleShape,
                        color = if (user.isActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else MaterialTheme.financialColors.expense.copy(alpha = 0.12f),
                        modifier = Modifier.size(34.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                if (user.role == DomainConstants.ROLE_ADMIN) Icons.Default.AdminPanelSettings else Icons.Default.Badge,
                                contentDescription = null,
                                tint = if (user.isActive) MaterialTheme.colorScheme.primary else MaterialTheme.financialColors.expense,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(user.name, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = if (user.role == DomainConstants.ROLE_ADMIN) Color(0xFFE8F5E9) else Color(0xFFE3F2FD)
                            ) {
                                Text(
                                    user.role,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (user.role == DomainConstants.ROLE_ADMIN) Color(0xFF2E7D32) else Color(0xFF1976D2),
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                )
                            }
                        }
                        Text(user.email, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                    }
                }

                // Active/Disabled Toggle Switch
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        if (user.isActive) "Active" else "Disabled",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (user.isActive) Color(0xFF2E7D32) else MaterialTheme.financialColors.expense
                    )
                    Switch(
                        checked = user.isActive,
                        onCheckedChange = onToggleStatus,
                        modifier = Modifier.height(28.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
            Spacer(modifier = Modifier.height(6.dp))

            // Footer Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "PIN: ${user.pin}",
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(
                        onClick = onShareCredentials,
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier.height(30.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(12.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Send Access", fontSize = 10.5.sp)
                    }

                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = "Delete User", tint = MaterialTheme.financialColors.expense, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun AddStaffUserDialog(
    onDismiss: () -> Unit,
    onConfirm: (name: String, email: String, role: String, pin: String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var role by remember { mutableStateOf(DomainConstants.ROLE_ACCOUNTANT) }
    var pin by remember { mutableStateOf((1000..9999).random().toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.PersonAdd, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text("Create Staff Account", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Staff Full Name") },
                    placeholder = { Text("e.g. Mr. Usama Khan") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Staff Email Address") },
                    placeholder = { Text("e.g. usama@bls.school") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text("System Role:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = role == DomainConstants.ROLE_ADMIN,
                        onClick = { role = DomainConstants.ROLE_ADMIN },
                        label = { Text("Admin (Full Access)") }
                    )
                    FilterChip(
                        selected = role == DomainConstants.ROLE_ACCOUNTANT,
                        onClick = { role = DomainConstants.ROLE_ACCOUNTANT },
                        label = { Text("Accountant") }
                    )
                }

                OutlinedTextField(
                    value = pin,
                    onValueChange = { if (it.length <= 4 && it.all { c -> c.isDigit() }) pin = it },
                    label = { Text("4-Digit Access PIN") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(name, email, role, pin) },
                enabled = name.isNotBlank() && email.isNotBlank() && pin.length == 4
            ) {
                Text("Create Account")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun ShareCredentialsDialog(
    user: AppUser,
    onDismiss: () -> Unit,
    context: Context
) {
    val message = """
🎓 *BLENDED LEARNING SCHOOL (BLS)*
*Finance & Cash Record System*
---------------------------------------
Hello *${user.name}*,

Your official staff login access has been created by Super Admin:
• *Role:* ${user.role}
• *Registered Email:* ${user.email}
• *Secret Login PIN:* ${user.pin}
• *App Download:* https://devsamikhan.github.io/theBLS/download.html

*How to Login:*
1. Open the BLS Cash Record App.
2. Enter your Email and PIN.
3. Keep your PIN secure and confidential.
---------------------------------------
    """.trimIndent()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Send Access Instructions", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Send login details directly to ${user.name}:", style = MaterialTheme.typography.bodySmall)
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(message, fontSize = 11.sp, modifier = Modifier.padding(10.dp))
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    try {
                        val sendIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, message)
                        }
                        context.startActivity(Intent.createChooser(sendIntent, "Share Access via"))
                    } catch (e: Exception) {
                        Toast.makeText(context, "Could not open share sheet: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                    onDismiss()
                }
            ) {
                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Share Access")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Done") }
        }
    )
}
