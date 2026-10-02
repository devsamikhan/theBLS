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
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
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
    onLoginAttempt: (String) -> Boolean
) {
    var pinInput by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var hasError by remember { mutableStateOf(false) }
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

            Spacer(modifier = Modifier.height(if (isCompactScreen) 16.dp else 22.dp))

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
                    // Lock Icon Badge
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
                        "Secure Authorization",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        "Enter your 4-digit secret authorization PIN",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp),
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(18.dp))

                    // Minimalist 4-Digit Box Input (Unified with BasicTextField)
                    BasicTextField(
                        value = pinInput,
                        onValueChange = { input ->
                            val sanitized = input.filter { it.isDigit() }.take(4)
                            pinInput = sanitized
                            hasError = false
                            if (sanitized.length == 4) {
                                val success = onLoginAttempt(sanitized)
                                hasError = !success
                            }
                        },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                if (pinInput.length == 4) {
                                    val success = onLoginAttempt(pinInput)
                                    hasError = !success
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
                    if (hasError) {
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
                                    "Invalid PIN! Admin pin is 8888, Accountant pin is 1111.",
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
                                val success = onLoginAttempt(pinInput)
                                hasError = !success
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

                        IconButton(
                            onClick = {
                                BiometricAuthHelper.authenticate(
                                    context = context,
                                    onSuccess = {
                                        // Successfully verified via fingerprint or screen lock
                                        if (onBiometricSuccess != null) {
                                            onBiometricSuccess("Admin")
                                        } else {
                                            onLoginAttempt("8888")
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
                    
                    Spacer(modifier = Modifier.height(18.dp))
                    
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                    
                    Spacer(modifier = Modifier.height(14.dp))
                    
                    // Quick Staff Access Shortcuts
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "QUICK ACCESS (INTERNAL STAFF)",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.SemiBold,
                                letterSpacing = 1.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Admin Shortcut
                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable {
                                        pinInput = "8888"
                                        hasError = false
                                        val success = onLoginAttempt("8888")
                                        hasError = !success
                                    },
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column {
                                        Text(
                                            "Admin",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            "PIN: 8888",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontSize = 11.sp
                                        )
                                    }
                                    Icon(
                                        Icons.Default.ArrowForward,
                                        contentDescription = "Login as Admin",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }

                            // Accountant Shortcut
                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable {
                                        pinInput = "1111"
                                        hasError = false
                                        val success = onLoginAttempt("1111")
                                        hasError = !success
                                    },
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column {
                                        Text(
                                            "Accountant",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            "PIN: 1111",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontSize = 11.sp
                                        )
                                    }
                                    Icon(
                                        Icons.Default.ArrowForward,
                                        contentDescription = "Login as Accountant",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(14.dp)
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
