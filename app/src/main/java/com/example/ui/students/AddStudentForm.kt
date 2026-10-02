package com.example.ui.students

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.data.DomainConstants
import com.example.data.Student
import com.example.ui.theme.*
import com.example.util.*
import java.text.NumberFormat
import java.util.*

/**
 * Minimalist, Modern 3-Step Sectioned Admission Form for BLS Cash Record.
 * Features:
 *  1. Student & Guardian Profile with Hairline Squircle Photo Picker
 *  2. Verification & Attached Documents Checklist
 *  3. Fee Structure, Smart Discount Calculation & Instant Counter Receipt
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddStudentForm(
    initialRegNo: String = "",
    onDismiss: () -> Unit,
    onSubmit: (
        regNo: String,
        name: String,
        fatherName: String,
        className: String,
        contact: String,
        admFee: Double,
        mthFee: Double,
        annCharges: Double,
        discountType: String,
        othCharges: Double,
        idCardNumber: String,
        prevSchool: String,
        fatherIdCard: String,
        schoolCertAttached: Boolean,
        formBAttached: Boolean,
        fatherCnicAttached: Boolean,
        picsAttached: Boolean,
        initialFeeCollected: Double,
        initialFeeMode: String,
        photoUri: String
    ) -> Unit
) {
    // Basic Details
    var regNo by remember { mutableStateOf(initialRegNo) }
    var name by remember { mutableStateOf("") }
    var father by remember { mutableStateOf("") }
    var classGroup by remember { mutableStateOf("") }
    var contact by remember { mutableStateOf("") }
    var previousSchoolName by remember { mutableStateOf("") }
    var photoUri by remember { mutableStateOf("") }

    // NADRA / Documents
    var idCardNumber by remember { mutableStateOf("") }
    var fatherIdCardNumber by remember { mutableStateOf("") }
    var schoolCertAttached by remember { mutableStateOf(false) }
    var formBAttached by remember { mutableStateOf(false) }
    var fatherCnicAttached by remember { mutableStateOf(false) }
    var picsAttached by remember { mutableStateOf(false) }

    // Fee Structure
    var monFee by remember { mutableStateOf("") }
    var admFee by remember { mutableStateOf("") }
    var annCharges by remember { mutableStateOf("") }
    var otherCharges by remember { mutableStateOf("") }
    var discountMode by remember { mutableStateOf("None") }
    var discountExpanded by remember { mutableStateOf(false) }
    var classExpanded by remember { mutableStateOf(false) }

    // On-the-Spot Instant Fee Collection
    var receiveInitialFeeNow by remember { mutableStateOf(false) }
    var initialPaymentAmount by remember { mutableStateOf("") }
    var initialPaymentMode by remember { mutableStateOf(DomainConstants.MODE_CASH) }

    val classesList = DomainConstants.CLASS_LEVELS

    // Photo picker launcher
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            photoUri = uri.toString()
            picsAttached = true
        }
    }

    // Live Fee Calculations
    val parsedMonthly = monFee.toDoubleOrNull() ?: 0.0
    val parsedAdmission = admFee.toDoubleOrNull() ?: 0.0
    val parsedAnnual = annCharges.toDoubleOrNull() ?: 0.0
    val parsedOther = otherCharges.toDoubleOrNull() ?: 0.0

    val discountPercentage = when (discountMode) {
        "Special Discount 10%" -> 0.10
        "Sibling 20%" -> 0.20
        "Sibling 40%" -> 0.40
        "Orphan 50%", "Teacher son 50%" -> 0.50
        "Poor Free", "Owner Discount 100%" -> 1.00
        else -> 0.00
    }
    val discountedMonthlyFee = (parsedMonthly * (1.0 - discountPercentage)).coerceAtLeast(0.0)
    val monthlyConcessionAmount = (parsedMonthly - discountedMonthlyFee).coerceAtLeast(0.0)
    val totalInitialPayable = parsedAdmission + discountedMonthlyFee + parsedAnnual + parsedOther

    // Automatically update initial payment field if user turns on instant fee collection
    LaunchedEffect(receiveInitialFeeNow, totalInitialPayable) {
        if (receiveInitialFeeNow && (initialPaymentAmount.isBlank() || initialPaymentAmount == "0")) {
            initialPaymentAmount = totalInitialPayable.toInt().toString()
        }
    }

    // Validation checks
    val isNameValid = name.trim().length >= 2
    val isFatherValid = father.trim().length >= 2
    val isClassValid = classGroup.isNotBlank()
    val isContactValid = InputFormatUtils.isValidPhone(contact)
    val isMonthlyFeeValid = parsedMonthly > 0.0

    val isFormValid = isNameValid && isFatherValid && isClassValid && isContactValid && isMonthlyFeeValid

    val textFieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = MaterialTheme.colorScheme.onSurface,
        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
        focusedBorderColor = MaterialTheme.colorScheme.primary,
        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
        focusedContainerColor = Color.Transparent,
        unfocusedContainerColor = Color.Transparent
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        modifier = Modifier.imePadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.94f)
                .padding(horizontal = 20.dp)
                .padding(bottom = 16.dp)
        ) {
            // Header Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
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
                            Icons.Default.School,
                            contentDescription = null,
                            tint = MaterialTheme.financialColors.cash,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Column {
                        Text(
                            "New Student Admission",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            "Registration & tuition fee package setup",
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

            // Scrollable 3-Card Sections
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {

                // ==========================================
                // CARD 1: 👤 STUDENT & GUARDIAN BASIC INFO
                // ==========================================
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f), RoundedCornerShape(8.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.Person,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                Column {
                                    Text(
                                        "Student Profile",
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        style = MaterialTheme.typography.titleSmall
                                    )
                                    Text(
                                        "Basic bio-data and academic class level",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Surface(
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    "Step 1",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))

                        // Student Avatar Picker Row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(60.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(MaterialTheme.colorScheme.surface)
                                    .border(
                                        1.5.dp,
                                        if (photoUri.isNotBlank()) MaterialTheme.financialColors.cash else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                        RoundedCornerShape(14.dp)
                                    )
                                    .clickable { photoPickerLauncher.launch("image/*") },
                                contentAlignment = Alignment.Center
                            ) {
                                if (photoUri.isNotBlank()) {
                                    AsyncImage(
                                        model = photoUri,
                                        contentDescription = "Student Avatar",
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Icon(
                                            Icons.Default.AddAPhoto,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(22.dp)
                                        )
                                        Text(
                                            "Photo",
                                            fontSize = 9.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    if (photoUri.isNotBlank()) "Photo Attached ✓" else "Student Photograph",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (photoUri.isNotBlank()) MaterialTheme.financialColors.cash else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    "Tap squircle to choose passport size photo",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (photoUri.isNotBlank()) {
                                    Text(
                                        "Remove Photo",
                                        fontSize = 10.5.sp,
                                        color = MaterialTheme.financialColors.expense,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier
                                            .padding(top = 2.dp)
                                            .clickable { photoUri = "" }
                                    )
                                }
                            }
                        }

                        // Registration No
                        OutlinedTextField(
                            value = regNo,
                            onValueChange = { regNo = it },
                            label = { Text("Registration No *") },
                            trailingIcon = {
                                Icon(Icons.Default.Badge, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            colors = textFieldColors,
                            modifier = Modifier.fillMaxWidth()
                        )

                        // Student Name
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            label = { Text("Student Full Name *") },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Face,
                                    contentDescription = null,
                                    tint = if (isNameValid) MaterialTheme.financialColors.cash else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            isError = name.isNotEmpty() && !isNameValid,
                            supportingText = {
                                if (name.isNotEmpty() && !isNameValid) {
                                    Text("Minimum 2 characters required", color = MaterialTheme.financialColors.expense, fontSize = 10.sp)
                                }
                            },
                            colors = textFieldColors,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("add_name_field")
                        )

                        // Father Name
                        OutlinedTextField(
                            value = father,
                            onValueChange = { father = it },
                            label = { Text("Father / Guardian Name *") },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.PersonOutline,
                                    contentDescription = null,
                                    tint = if (isFatherValid) MaterialTheme.financialColors.cash else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            isError = father.isNotEmpty() && !isFatherValid,
                            supportingText = {
                                if (father.isNotEmpty() && !isFatherValid) {
                                    Text("Minimum 2 characters required", color = MaterialTheme.financialColors.expense, fontSize = 10.sp)
                                }
                            },
                            colors = textFieldColors,
                            modifier = Modifier.fillMaxWidth()
                        )

                        // Class Level Dropdown
                        Box(modifier = Modifier.fillMaxWidth()) {
                            OutlinedTextField(
                                value = classGroup,
                                onValueChange = {},
                                label = { Text("Class Level / Grade *") },
                                readOnly = true,
                                shape = RoundedCornerShape(12.dp),
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.Class,
                                        contentDescription = null,
                                        tint = if (isClassValid) MaterialTheme.financialColors.cash else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                },
                                trailingIcon = {
                                    IconButton(onClick = { classExpanded = !classExpanded }) {
                                        Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    }
                                },
                                isError = classGroup.isEmpty() && (name.isNotEmpty() || father.isNotEmpty()),
                                colors = textFieldColors,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Box(
                                modifier = Modifier
                                    .matchParentSize()
                                    .clickable { classExpanded = !classExpanded }
                            )
                            DropdownMenu(
                                expanded = classExpanded,
                                onDismissRequest = { classExpanded = false },
                                modifier = Modifier.fillMaxWidth(0.85f)
                            ) {
                                classesList.forEach { className ->
                                    DropdownMenuItem(
                                        text = {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(className, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Medium)
                                                if (classGroup == className) {
                                                    Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                                }
                                            }
                                        },
                                        onClick = {
                                            classGroup = className
                                            classExpanded = false
                                        }
                                    )
                                }
                            }
                        }

                        // Phone Number with Pakistani Visual Transformation
                        OutlinedTextField(
                            value = contact,
                            onValueChange = {
                                val digits = it.filter { ch -> ch.isDigit() }
                                if (digits.length <= 11) contact = digits
                            },
                            label = { Text("Guardian Mobile *") },
                            placeholder = { Text("03XX-XXXXXXX") },
                            visualTransformation = PhoneVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            shape = RoundedCornerShape(12.dp),
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Phone,
                                    contentDescription = null,
                                    tint = if (isContactValid) MaterialTheme.financialColors.cash else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            trailingIcon = {
                                if (isContactValid) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = "Valid", tint = MaterialTheme.financialColors.cash)
                                }
                            },
                            supportingText = {
                                if (contact.isNotEmpty() && !isContactValid) {
                                    Text("Must be 11 digits starting with 03 (e.g. 03001234567)", color = MaterialTheme.financialColors.expense, fontSize = 10.sp)
                                } else if (isContactValid) {
                                    Text("✓ Valid Pakistan Mobile Number", color = MaterialTheme.financialColors.cash, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            },
                            colors = textFieldColors,
                            modifier = Modifier.fillMaxWidth()
                        )

                        // Previous School Name (Optional)
                        OutlinedTextField(
                            value = previousSchoolName,
                            onValueChange = { previousSchoolName = it },
                            label = { Text("Previous School Name (Optional)") },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            leadingIcon = {
                                Icon(Icons.Default.HistoryEdu, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            },
                            colors = textFieldColors,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                // ==========================================
                // CARD 2: 📋 NADRA ID & ATTACHMENTS
                // ==========================================
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .background(Color(0xFFE0F7FA), RoundedCornerShape(8.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.FactCheck, contentDescription = null, tint = Color(0xFF00838F), modifier = Modifier.size(16.dp))
                                }
                                Column {
                                    Text("NADRA & Verification", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleSmall)
                                    Text("B-Form, CNIC & Document Attachments", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            Surface(
                                color = Color(0xFF00838F).copy(alpha = 0.08f),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text("Step 2", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF00838F), modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))

                        // Student B-Form
                        OutlinedTextField(
                            value = idCardNumber,
                            onValueChange = {
                                val digits = it.filter { ch -> ch.isDigit() }
                                if (digits.length <= 13) idCardNumber = digits
                            },
                            label = { Text("Child Form-B / CNIC No") },
                            placeholder = { Text("XXXXX-XXXXXXX-X") },
                            visualTransformation = CnicVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            shape = RoundedCornerShape(12.dp),
                            leadingIcon = {
                                Icon(Icons.Default.Badge, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            },
                            colors = textFieldColors,
                            modifier = Modifier.fillMaxWidth()
                        )

                        // Father CNIC
                        OutlinedTextField(
                            value = fatherIdCardNumber,
                            onValueChange = {
                                val digits = it.filter { ch -> ch.isDigit() }
                                if (digits.length <= 13) fatherIdCardNumber = digits
                            },
                            label = { Text("Father CNIC No") },
                            placeholder = { Text("XXXXX-XXXXXXX-X") },
                            visualTransformation = CnicVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            shape = RoundedCornerShape(12.dp),
                            leadingIcon = {
                                Icon(Icons.Default.CreditCard, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            },
                            colors = textFieldColors,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Text(
                            "Verified Attachments at Admission:",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(top = 4.dp)
                        )

                        // Document Checklist Checkboxes
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            DocumentCheckItem(
                                title = "School Leaving Certificate Attached",
                                checked = schoolCertAttached,
                                onCheckedChange = { schoolCertAttached = it }
                            )
                            DocumentCheckItem(
                                title = "Child Form-B / ID Card Attached",
                                checked = formBAttached,
                                onCheckedChange = { formBAttached = it }
                            )
                            DocumentCheckItem(
                                title = "Father CNIC / ID Copy Attached",
                                checked = fatherCnicAttached,
                                onCheckedChange = { fatherCnicAttached = it }
                            )
                            DocumentCheckItem(
                                title = "Recent Photograph(s) Attached (2 Copies)",
                                checked = picsAttached,
                                onCheckedChange = { picsAttached = it }
                            )
                        }
                    }
                }

                // ==========================================
                // CARD 3: 💰 FEE PACKAGE & ON-THE-SPOT PAYMENT
                // ==========================================
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .background(MaterialTheme.financialColors.cashContainer, RoundedCornerShape(8.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.Payments,
                                        contentDescription = null,
                                        tint = MaterialTheme.financialColors.cash,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                Column {
                                    Text("Fee Package & Counter Slip", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleSmall)
                                    Text("Monthly fee schedule & instant counter receipt", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            Surface(
                                color = MaterialTheme.financialColors.cash.copy(alpha = 0.08f),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text("Step 3", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.financialColors.cash, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))

                        // Monthly & Admission Fee
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedTextField(
                                value = monFee,
                                onValueChange = { monFee = it.filter { ch -> ch.isDigit() } },
                                label = { Text("Monthly Fee *") },
                                placeholder = { Text("Rs. 3000") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                visualTransformation = CurrencyVisualTransformation(),
                                shape = RoundedCornerShape(12.dp),
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.AttachMoney,
                                        contentDescription = null,
                                        tint = if (isMonthlyFeeValid) MaterialTheme.financialColors.cash else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                },
                                isError = monFee.isNotEmpty() && !isMonthlyFeeValid,
                                colors = textFieldColors,
                                modifier = Modifier.weight(1f)
                            )

                            OutlinedTextField(
                                value = admFee,
                                onValueChange = { admFee = it.filter { ch -> ch.isDigit() } },
                                label = { Text("Admission Fee") },
                                placeholder = { Text("Rs. 2000") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                visualTransformation = CurrencyVisualTransformation(),
                                shape = RoundedCornerShape(12.dp),
                                colors = textFieldColors,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        // Annual & Other Charges
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedTextField(
                                value = annCharges,
                                onValueChange = { annCharges = it.filter { ch -> ch.isDigit() } },
                                label = { Text("Annual Charges") },
                                placeholder = { Text("0") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                visualTransformation = CurrencyVisualTransformation(),
                                shape = RoundedCornerShape(12.dp),
                                colors = textFieldColors,
                                modifier = Modifier.weight(1f)
                            )

                            OutlinedTextField(
                                value = otherCharges,
                                onValueChange = { otherCharges = it.filter { ch -> ch.isDigit() } },
                                label = { Text("Other Charges") },
                                placeholder = { Text("0") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                visualTransformation = CurrencyVisualTransformation(),
                                shape = RoundedCornerShape(12.dp),
                                colors = textFieldColors,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        // Discount Category Dropper
                        Box(modifier = Modifier.fillMaxWidth()) {
                            OutlinedTextField(
                                value = discountMode,
                                onValueChange = {},
                                label = { Text("Discount Category") },
                                readOnly = true,
                                shape = RoundedCornerShape(12.dp),
                                trailingIcon = {
                                    IconButton(onClick = { discountExpanded = !discountExpanded }) {
                                        Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    }
                                },
                                colors = textFieldColors,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Box(
                                modifier = Modifier
                                    .matchParentSize()
                                    .clickable { discountExpanded = !discountExpanded }
                            )
                            DropdownMenu(
                                expanded = discountExpanded,
                                onDismissRequest = { discountExpanded = false },
                                modifier = Modifier.fillMaxWidth(0.85f)
                            ) {
                                DomainConstants.DISCOUNT_TYPES.forEach { dType ->
                                    DropdownMenuItem(
                                        text = {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(dType, color = MaterialTheme.colorScheme.onSurface)
                                                if (discountMode == dType) {
                                                    Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                                }
                                            }
                                        },
                                        onClick = {
                                            discountMode = dType
                                            discountExpanded = false
                                        }
                                    )
                                }
                            }
                        }

                        // Live Smart Fee Summary Box (Transparent itemized breakdown)
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.financialColors.cashContainer.copy(alpha = 0.35f)),
                            border = BorderStroke(1.dp, MaterialTheme.financialColors.cash.copy(alpha = 0.3f)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                Text(
                                    "Allocated Fee Package Breakdown:",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )

                                HorizontalDivider(color = MaterialTheme.financialColors.cash.copy(alpha = 0.2f), modifier = Modifier.padding(vertical = 2.dp))

                                // Base Monthly Fee
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Base Monthly Tuition:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text("Rs. ${parsedMonthly.toInt()}", fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
                                }

                                // Concession on Monthly Tuition (if any)
                                if (discountPercentage > 0.0) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            "Monthly Concession ($discountMode):",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.financialColors.cash
                                        )
                                        Text(
                                            "-Rs. ${monthlyConcessionAmount.toInt()} (${(discountPercentage * 100).toInt()}% off)",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp,
                                            color = MaterialTheme.financialColors.cash
                                        )
                                    }
                                    Text(
                                        "Note: Discount applies strictly to monthly tuition only.",
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                                    )
                                }

                                // Net Monthly Tuition
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Net Monthly Tuition (Recurring):", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.financialColors.cash)
                                    Text("Rs. ${discountedMonthlyFee.toInt()} / month", fontWeight = FontWeight.Bold, fontSize = 12.5.sp, color = MaterialTheme.financialColors.cash)
                                }

                                HorizontalDivider(color = MaterialTheme.financialColors.cash.copy(alpha = 0.2f), modifier = Modifier.padding(vertical = 2.dp))

                                // Admission Fee (One-Time)
                                if (parsedAdmission > 0.0) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("Admission Fee (One-Time Only):", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text("Rs. ${parsedAdmission.toInt()}", fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
                                    }
                                }

                                // Annual Charges
                                if (parsedAnnual > 0.0) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("Annual Session Charges:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text("Rs. ${parsedAnnual.toInt()}", fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
                                    }
                                }

                                // Other Charges
                                if (parsedOther > 0.0) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("Other / Prospectus Charges:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text("Rs. ${parsedOther.toInt()}", fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
                                    }
                                }

                                HorizontalDivider(color = MaterialTheme.financialColors.cash.copy(alpha = 0.35f), modifier = Modifier.padding(vertical = 2.dp))

                                // Grand Total at Admission
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text("Total Payable at Admission:", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                        Text("Admission + 1st Month + Annual + Other", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
                                    }
                                    Text("Rs. ${totalInitialPayable.toInt()}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))

                        // Instant On-The-Spot Fee Collection Switch
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(if (receiveInitialFeeNow) MaterialTheme.financialColors.cashContainer.copy(alpha = 0.35f) else Color.Transparent, RoundedCornerShape(12.dp))
                                .padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Receive Initial Fee at Counter Now?",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (receiveInitialFeeNow) MaterialTheme.financialColors.cash else MaterialTheme.colorScheme.onSurface
                                 )
                                 Text(
                                     "Collect initial admission fee & tuition into cash box now",
                                     style = MaterialTheme.typography.labelSmall,
                                     color = MaterialTheme.colorScheme.onSurfaceVariant
                                 )
                            }
                            Switch(
                                checked = receiveInitialFeeNow,
                                onCheckedChange = { receiveInitialFeeNow = it },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = MaterialTheme.financialColors.cash
                                )
                            )
                        }

                        // If Instant Collection is enabled, show Amount & Mode options
                        AnimatedVisibility(visible = receiveInitialFeeNow) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 4.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                OutlinedTextField(
                                    value = initialPaymentAmount,
                                    onValueChange = { initialPaymentAmount = it.filter { ch -> ch.isDigit() } },
                                    label = { Text("Amount Received at Counter (Rs) *") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    visualTransformation = CurrencyVisualTransformation(),
                                    shape = RoundedCornerShape(12.dp),
                                    leadingIcon = {
                                        Icon(Icons.Default.Receipt, contentDescription = null, tint = MaterialTheme.financialColors.cash)
                                    },
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                                        focusedBorderColor = MaterialTheme.financialColors.cash,
                                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Payment Mode:", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)

                                    FilterChip(
                                        selected = initialPaymentMode == DomainConstants.MODE_CASH,
                                        onClick = { initialPaymentMode = DomainConstants.MODE_CASH },
                                        label = { Text("Cash Box", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                                        leadingIcon = {
                                            if (initialPaymentMode == DomainConstants.MODE_CASH) {
                                                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp))
                                            }
                                        },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = MaterialTheme.financialColors.cash.copy(alpha = 0.15f),
                                            selectedLabelColor = MaterialTheme.financialColors.cash
                                        ),
                                        shape = RoundedCornerShape(8.dp)
                                    )

                                    FilterChip(
                                        selected = initialPaymentMode == DomainConstants.MODE_BANK,
                                        onClick = { initialPaymentMode = DomainConstants.MODE_BANK },
                                        label = { Text("Bank", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                                        leadingIcon = {
                                            if (initialPaymentMode == DomainConstants.MODE_BANK) {
                                                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp))
                                            }
                                        },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = MaterialTheme.financialColors.bank.copy(alpha = 0.15f),
                                            selectedLabelColor = MaterialTheme.financialColors.bank
                                        ),
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Validation Helper Hint (if any mandatory field is missing)
            if (!isFormValid) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.financialColors.expense, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    val missingMsg = when {
                        !isNameValid -> "Student name is required"
                        !isFatherValid -> "Father / Guardian name is required"
                        !isClassValid -> "Class Grade must be selected"
                        !isContactValid -> "Valid 11-digit mobile (03XX-XXXXXXX) is required"
                        !isMonthlyFeeValid -> "Monthly fee must be greater than 0"
                        else -> "Please complete all mandatory fields (*)"
                    }
                    Text(missingMsg, color = MaterialTheme.financialColors.expense, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                }
            }

            // Big Save Button
            Button(
                onClick = {
                    if (isFormValid) {
                        val collectedAmount = if (receiveInitialFeeNow) {
                            initialPaymentAmount.toDoubleOrNull() ?: 0.0
                        } else 0.0

                        onSubmit(
                            regNo.ifBlank { initialRegNo },
                            name.trim(),
                            father.trim(),
                            classGroup,
                            contact.trim(),
                            parsedAdmission,
                            parsedMonthly,
                            parsedAnnual,
                            discountMode,
                            parsedOther,
                            idCardNumber.trim(),
                            previousSchoolName.trim(),
                            fatherIdCardNumber.trim(),
                            schoolCertAttached,
                            formBAttached,
                            fatherCnicAttached,
                            picsAttached,
                            collectedAmount,
                            initialPaymentMode,
                            photoUri
                        )
                    }
                },
                enabled = isFormValid,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    disabledContainerColor = MaterialTheme.colorScheme.outlineVariant
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("submit_admission_button")
            ) {
                Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    if (receiveInitialFeeNow) "Save Admission & Receive Fee" else "Register Student",
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    fontSize = 13.5.sp
                )
            }
        }
    }
}

/**
 * Overload 1: Supports callers passing 18 parameters (without photoUri).
 */
@Composable
fun AddStudentForm(
    initialRegNo: String = "",
    onDismiss: () -> Unit,
    onSubmit: (
        regNo: String,
        name: String,
        fatherName: String,
        className: String,
        contact: String,
        admFee: Double,
        mthFee: Double,
        annCharges: Double,
        discountType: String,
        othCharges: Double,
        idCardNumber: String,
        prevSchool: String,
        fatherIdCard: String,
        schoolCertAttached: Boolean,
        formBAttached: Boolean,
        fatherCnicAttached: Boolean,
        picsAttached: Boolean,
        initialFeeCollected: Double,
        initialFeeMode: String
    ) -> Unit
) {
    AddStudentForm(
        initialRegNo = initialRegNo,
        onDismiss = onDismiss,
        onSubmit = { reg, n, fn, c, ph, adm, mon, ann, disc, oth, idCard, prevSchool, fatherIdCard, schoolCertSel, formBSel, fatherCnicSel, picsSel, initFee, initMode, _ ->
            onSubmit(
                reg, n, fn, c, ph, adm, mon, ann, disc, oth, idCard, prevSchool, fatherIdCard, schoolCertSel, formBSel, fatherCnicSel, picsSel, initFee, initMode
            )
        }
    )
}

/**
 * Overload 2: Preserves 100% backward compatibility with legacy 16-parameter callers.
 */
@Composable
fun AddStudentForm(
    initialRegNo: String = "",
    onDismiss: () -> Unit,
    onSubmit: (
        regNo: String,
        name: String,
        fatherName: String,
        className: String,
        contact: String,
        admFee: Double,
        mthFee: Double,
        annCharges: Double,
        discountType: String,
        othCharges: Double,
        idCardNumber: String,
        prevSchool: String,
        fatherIdCard: String,
        schoolCertAttached: Boolean,
        formBAttached: Boolean,
        fatherCnicAttached: Boolean,
        picsAttached: Boolean
    ) -> Unit
) {
    AddStudentForm(
        initialRegNo = initialRegNo,
        onDismiss = onDismiss,
        onSubmit = { reg, n, fn, c, ph, adm, mon, ann, disc, oth, idCard, prevSchool, fatherIdCard, schoolCertSel, formBSel, fatherCnicSel, picsSel, _, _, _ ->
            onSubmit(
                reg, n, fn, c, ph, adm, mon, ann, disc, oth, idCard, prevSchool, fatherIdCard, schoolCertSel, formBSel, fatherCnicSel, picsSel
            )
        }
    )
}

/**
 * Reusable Checklist Row Item with Custom Styling
 */
@Composable
private fun DocumentCheckItem(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(title, color = MaterialTheme.colorScheme.onSurface, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

/**
 * Immediate Post-Admission Success Dialog.
 * Offers 4 instant options:
 *  1. Print Official Admission Form PDF
 *  2. Print 3-Copy Fee Challan / Receipt PDF
 *  3. Share Official Admission Welcome Slip via WhatsApp
 *  4. Send Direct SMS Notice (GSM Fallback without internet)
 */
@Composable
fun AdmissionSuccessDialog(
    student: Student,
    initialFeePaid: Double,
    initialFeeMode: String,
    onDismiss: () -> Unit,
    onPrintAdmissionForm: () -> Unit,
    onPrintChallan: () -> Unit,
    onShareWhatsApp: () -> Unit,
    onSendSms: () -> Unit = {}
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Success Badge
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .background(MaterialTheme.financialColors.cashContainer, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.financialColors.cash,
                        modifier = Modifier.size(32.dp)
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "Admission Registered Successfully!",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        "Student admission has been successfully processed",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Summary Card with Photo
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (student.photoUri.isNotBlank()) {
                            AsyncImage(
                                model = student.photoUri,
                                contentDescription = "Student Photo",
                                modifier = Modifier
                                    .size(54.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .border(1.5.dp, MaterialTheme.financialColors.cash, RoundedCornerShape(12.dp)),
                                contentScale = ContentScale.Crop
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                        }

                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Student Name:", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(student.studentName, fontWeight = FontWeight.Bold, fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurface)
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Reg No:", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(student.regNo, fontWeight = FontWeight.Bold, fontSize = 11.5.sp, color = MaterialTheme.colorScheme.primary)
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Class & Grade:", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(student.className, fontWeight = FontWeight.Bold, fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurface)
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Monthly Tuition:", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("Rs. ${student.monthlyFee.toInt()}", fontWeight = FontWeight.Bold, fontSize = 11.5.sp, color = MaterialTheme.financialColors.cash)
                            }

                            if (initialFeePaid > 0) {
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f), modifier = Modifier.padding(vertical = 4.dp))
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Payment Collected:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.financialColors.cash)
                                    Text("Rs. ${initialFeePaid.toInt()} ($initialFeeMode)", fontWeight = FontWeight.Bold, fontSize = 11.5.sp, color = MaterialTheme.financialColors.cash)
                                }
                            }
                        }
                    }
                }

                // Action Buttons
                Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = onPrintAdmissionForm,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.financialColors.cash),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Print Official Admission Form", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = onPrintChallan,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.ReceiptLong, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Print 3-Copy Fee Challan", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = onShareWhatsApp,
                        border = BorderStroke(1.dp, Color(0xFF25D366)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF1E7E34)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, tint = Color(0xFF25D366), modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Send WhatsApp Welcome Slip", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = onSendSms,
                        border = BorderStroke(1.dp, MaterialTheme.financialColors.bank),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.financialColors.bank),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Message, contentDescription = null, tint = MaterialTheme.financialColors.bank, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Send Direct SMS Notice", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }

                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Done", color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
