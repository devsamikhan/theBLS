package com.example.ui.auth

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.BLSLogo
import com.example.ui.theme.*
import com.example.util.BiometricAuthHelper

@Composable
fun LoginScreen(
    onBiometricSuccess: ((String) -> Unit)? = null,
    onLoginAttempt: (String) -> Boolean,
    onEmailLoginAttempt: ((String, String) -> Boolean)? = null,
    onSuperAdminLoginAttempt: ((String, String) -> Boolean)? = null,
    errorMessage: String? = null
) {
    var isSuperAdminMode by remember { mutableStateOf(false) }
    var staffEmailInput by remember { mutableStateOf("") }
    var pinInput by remember { mutableStateOf("") }
    var superAdminKeyInput by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var hasError by remember { mutableStateOf(false) }
    var localError by remember { mutableStateOf<String?>(null) }
    val focusRequester = remember { FocusRequester() }
    val context = LocalContext.current

    val configuration = LocalConfiguration.current
    val screenHeight = configuration.screenHeightDp.dp
    val isCompactScreen = screenHeight < 680.dp

    // Auto-focus on entry
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .imePadding(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .widthIn(max = 420.dp)
                .fillMaxWidth(0.92f)
                .verticalScroll(rememberScrollState())
                .padding(
                    vertical = if (isCompactScreen) 12.dp else 24.dp,
                    horizontal = 8.dp
                )
        ) {
            // Brand Logo & Header
            BLSLogo(scale = if (isCompactScreen) 0.85f else 1.0f)
            
            Spacer(modifier = Modifier.height(if (isCompactScreen) 10.dp else 14.dp))
            
            Text(
                "BLENDED LEARNING SCHOOL",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    letterSpacing = 0.8.sp
                ),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(4.dp))

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
            ) {
                Text(
                    "FINANCE & LEDGER SYSTEM",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        letterSpacing = 1.5.sp
                    ),
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(if (isCompactScreen) 12.dp else 16.dp))

            // Mode Selector Pill (Staff Access vs Super Admin)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Surface(
                    onClick = {
                        isSuperAdminMode = false
                        localError = null
                    },
                    shape = RoundedCornerShape(9.dp),
                    color = if (!isSuperAdminMode) MaterialTheme.colorScheme.surface else Color.Transparent,
                    shadowElevation = if (!isSuperAdminMode) 1.dp else 0.dp,
                    modifier = Modifier.weight(1f).height(36.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            Icons.Default.Badge,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp),
                            tint = if (!isSuperAdminMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            "Staff Login",
                            fontSize = 11.5.sp,
                            fontWeight = if (!isSuperAdminMode) FontWeight.Bold else FontWeight.Medium,
                            color = if (!isSuperAdminMode) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Surface(
                    onClick = {
                        isSuperAdminMode = true
                        localError = null
                    },
                    shape = RoundedCornerShape(9.dp),
                    color = if (isSuperAdminMode) MaterialTheme.colorScheme.surface else Color.Transparent,
                    shadowElevation = if (isSuperAdminMode) 1.dp else 0.dp,
                    modifier = Modifier.weight(1f).height(36.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            Icons.Default.VerifiedUser,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp),
                            tint = if (isSuperAdminMode) Color(0xFFE65100) else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            "Super User",
                            fontSize = 11.5.sp,
                            fontWeight = if (isSuperAdminMode) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSuperAdminMode) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Auth Card
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(if (isCompactScreen) 16.dp else 22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (isSuperAdminMode) {
                        // ==================== SUPER ADMIN LOGIN VIEW ====================
                        Surface(
                            modifier = Modifier.size(44.dp),
                            shape = CircleShape,
                            color = Color(0xFFFFF3E0),
                            border = BorderStroke(1.dp, Color(0xFFFFB74D))
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.Security,
                                    contentDescription = null,
                                    tint = Color(0xFFE65100),
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Text(
                            "Super User Verification",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            "Exclusive master administrator control portal",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 2.dp),
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        // Locked Super Admin Email Field
                        OutlinedTextField(
                            value = com.example.data.DomainConstants.SUPER_ADMIN_EMAIL,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Super Admin Gmail") },
                            leadingIcon = {
                                Icon(Icons.Default.Mail, contentDescription = null, tint = Color(0xFFE65100), modifier = Modifier.size(18.dp))
                            },
                            trailingIcon = {
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = Color(0xFFE8F5E9)
                                ) {
                                    Text("VERIFIED", color = Color(0xFF2E7D32), fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                }
                            },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        OutlinedTextField(
                            value = superAdminKeyInput,
                            onValueChange = {
                                superAdminKeyInput = it
                                localError = null
                            },
                            label = { Text("Master Passkey or PIN") },
                            placeholder = { Text("Enter Super Admin Key") },
                            leadingIcon = {
                                Icon(Icons.Default.Key, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                            },
                            trailingIcon = {
                                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                    Icon(
                                        if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            },
                            visualTransformation = if (passwordVisible) androidx.compose.ui.text.input.VisualTransformation.None else androidx.compose.ui.text.input.PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(
                                onDone = {
                                    val success = onSuperAdminLoginAttempt?.invoke(com.example.data.DomainConstants.SUPER_ADMIN_EMAIL, superAdminKeyInput)
                                        ?: onLoginAttempt(superAdminKeyInput)
                                    if (!success) {
                                        localError = "Invalid Super Admin Security Key."
                                    }
                                }
                            ),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        Button(
                            onClick = {
                                val success = onSuperAdminLoginAttempt?.invoke(com.example.data.DomainConstants.SUPER_ADMIN_EMAIL, superAdminKeyInput)
                                    ?: onLoginAttempt(superAdminKeyInput)
                                if (!success) {
                                    localError = "Invalid Super Admin Security Key."
                                }
                            },
                            modifier = Modifier.fillMaxWidth().height(46.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE65100)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.LockOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Sign In as Super User", fontWeight = FontWeight.Bold)
                        }

                    } else {
                        // ==================== STAFF LOGIN VIEW (EMAIL + PIN) ====================
                        Surface(
                            modifier = Modifier.size(44.dp),
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.Lock,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Text(
                            "Staff Authorization",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            "Enter your assigned email address and 4-digit PIN",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 2.dp),
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        OutlinedTextField(
                            value = staffEmailInput,
                            onValueChange = {
                                staffEmailInput = it
                                localError = null
                            },
                            label = { Text("Registered Email Address") },
                            placeholder = { Text("e.g. accountant@bls.school") },
                            leadingIcon = {
                                Icon(Icons.Default.MailOutline, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            "4-DIGIT PIN",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                    // Minimalist 4-Digit Box Input (Unified with BasicTextField)
                    BasicTextField(
                        value = pinInput,
                        onValueChange = { input ->
                            val sanitized = input.filter { it.isDigit() }.take(4)
                            pinInput = sanitized
                            hasError = false
                            localError = null
                            if (sanitized.length == 4) {
                                val email = staffEmailInput.trim()
                                if (email.isBlank()) {
                                    localError = "Please enter your registered email address first."
                                    hasError = true
                                } else {
                                    val success = onEmailLoginAttempt?.invoke(email, sanitized)
                                        ?: onLoginAttempt(sanitized)
                                    hasError = !success
                                    if (!success) {
                                        localError = "Invalid email or PIN! Please verify credentials."
                                    }
                                }
                            }
                        },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                val email = staffEmailInput.trim()
                                if (email.isBlank()) {
                                    localError = "Please enter your registered email address."
                                    hasError = true
                                } else if (pinInput.length == 4) {
                                    val success = onEmailLoginAttempt?.invoke(email, pinInput)
                                        ?: onLoginAttempt(pinInput)
                                    hasError = !success
                                    if (!success) {
                                        localError = "Invalid email or PIN! Please verify credentials."
                                    }
                                }
                            }
                        ),
                        modifier = Modifier
                            .focusRequester(focusRequester)
                            .testTag("pin_input_field"),
                        singleLine = true,
                        decorationBox = { innerTextField ->
                            Box(contentAlignment = Alignment.Center) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(if (isCompactScreen) 10.dp else 14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    for (i in 0 until 4) {
                                        val digit = pinInput.getOrNull(i)
                                        val isFocused = pinInput.length == i
                                        val boxBorderColor = when {
                                            hasError -> MaterialTheme.financialColors.expense
                                            isFocused -> MaterialTheme.colorScheme.primary
                                            digit != null -> MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                                            else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                                        }
                                        val boxBgColor = when {
                                            hasError -> MaterialTheme.financialColors.expenseContainer.copy(alpha = 0.25f)
                                            isFocused -> MaterialTheme.colorScheme.primary.copy(alpha = 0.05f)
                                            digit != null -> MaterialTheme.colorScheme.primary.copy(alpha = 0.04f)
                                            else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
                                        }

                                        Box(
                                            modifier = Modifier
                                                .size(if (isCompactScreen) 48.dp else 54.dp)
                                                .clip(RoundedCornerShape(14.dp))
                                                .background(boxBgColor)
                                                .border(
                                                    width = if (isFocused || hasError) 2.dp else 1.dp,
                                                    color = boxBorderColor,
                                                    shape = RoundedCornerShape(14.dp)
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (digit != null) {
                                                if (passwordVisible) {
                                                    Text(
                                                        text = digit.toString(),
                                                        fontSize = 22.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.colorScheme.onSurface
                                                    )
                                                } else {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(12.dp)
                                                            .clip(CircleShape)
                                                            .background(MaterialTheme.colorScheme.primary)
                                                    )
                                                }
                                            } else if (isFocused) {
                                                Box(
                                                    modifier = Modifier
                                                        .width(2.dp)
                                                        .height(18.dp)
                                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.7f))
                                                )
                                            }
                                        }
                                    }
                                }
                                // Invisible placement so Compose text engine maintains keyboard state
                                Box(modifier = Modifier.size(0.dp)) {
                                    innerTextField()
                                }
                            }
                        }
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Controls below PIN: Show/Hide Toggle & Quick Clear
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color.Transparent,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { passwordVisible = !passwordVisible }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = "Toggle PIN Visibility",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(15.dp)
                                )
                                Text(
                                    if (passwordVisible) "Hide PIN" else "Show PIN",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        if (pinInput.isNotEmpty()) {
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                "•",
                                color = MaterialTheme.colorScheme.outlineVariant,
                                fontSize = 12.sp
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color.Transparent,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        pinInput = ""
                                        hasError = false
                                        focusRequester.requestFocus()
                                    }
                            ) {
                                Text(
                                    "Clear",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.financialColors.expense,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }

                    // Error Alert Banner
                    val activeError = localError ?: errorMessage
                    if (activeError != null) {
                        Surface(
                            color = MaterialTheme.financialColors.expenseContainer.copy(alpha = 0.35f),
                            border = BorderStroke(1.dp, MaterialTheme.financialColors.expense.copy(alpha = 0.4f)),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 10.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = MaterialTheme.financialColors.expense,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    activeError,
                                    color = MaterialTheme.financialColors.expense,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    // Action Buttons Row (Verify & Biometric)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = {
                                localError = null
                                val email = staffEmailInput.trim()
                                if (email.isBlank()) {
                                    localError = "Please enter your registered email address."
                                    hasError = true
                                } else if (pinInput.length < 4) {
                                    localError = "Please enter your complete 4-digit PIN."
                                    hasError = true
                                } else {
                                    val success = onEmailLoginAttempt?.invoke(email, pinInput)
                                        ?: onLoginAttempt(pinInput)
                                    hasError = !success
                                    if (!success) {
                                        localError = "Invalid email or PIN! Please verify credentials."
                                    }
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                                .testTag("submit_login_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                "Verify Authentication",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }

                        if (onBiometricSuccess != null) {
                            IconButton(
                                onClick = {
                                    BiometricAuthHelper.authenticate(
                                        context = context,
                                        onSuccess = {
                                            val email = staffEmailInput.trim()
                                            if (email.isNotBlank() && onEmailLoginAttempt != null) {
                                                onBiometricSuccess(email)
                                            } else {
                                                onBiometricSuccess("Admin")
                                            }
                                        },
                                        onError = { errMsg ->
                                            Toast.makeText(context, errMsg, Toast.LENGTH_SHORT).show()
                                        }
                                    )
                                },
                                modifier = Modifier
                                    .size(46.dp)
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
                                    .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
                            ) {
                                Icon(
                                    Icons.Default.Fingerprint,
                                    contentDescription = "Biometric Fingerprint Login",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                        }
                    }
                }
            }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                "BLS Secure Portal • End-to-End Encrypted",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                fontSize = 10.sp
            )
        }
    }
}
