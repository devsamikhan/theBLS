package com.example.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.DomainConstants
import com.example.ui.auth.LoginScreen
import com.example.ui.components.*
import com.example.ui.closing.ReportsTab
import com.example.ui.closing.dialogs.UserManagementDialog
import com.example.ui.ledger.LedgerTab
import com.example.ui.ledger.AddExpenseDialog
import com.example.ui.ledger.ReceiveFeeDialogDirect
import com.example.ui.ledger.CashToBankDepositDialog
import com.example.ui.ledger.FamilyFeeCollectionDialog
import com.example.ui.students.StudentsTab
import com.example.ui.students.AddStudentForm
import com.example.ui.students.AdmissionSuccessDialog
import com.example.data.Student
import com.example.ui.theme.*
import com.example.util.AudioFeedback
import com.example.util.ReportExporter
import com.example.util.InputFormatUtils
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun BLSApp(viewModel: AppViewModel) {
    val currentRole by viewModel.currentUserRole.collectAsStateWithLifecycle()

    val loginError by viewModel.loginError.collectAsStateWithLifecycle()

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Crossfade(targetState = currentRole, label = "RoleScreenTransition") { role ->
            when (role) {
                DomainConstants.ROLE_NONE -> LoginScreen(
                    onBiometricSuccess = { targetRole ->
                        viewModel.loginWithBiometric(targetRole)
                    },
                    onLoginAttempt = { pin ->
                        viewModel.login(pin)
                    },
                    onEmailLoginAttempt = { email, pin ->
                        viewModel.loginWithEmailAndPin(email, pin)
                    },
                    onSuperAdminLoginAttempt = { email, key ->
                        viewModel.loginSuperAdmin(email, key)
                    },
                    errorMessage = loginError
                )
                else -> DashboardContainer(viewModel = viewModel)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardContainer(viewModel: AppViewModel) {
    val role by viewModel.currentUserRole.collectAsStateWithLifecycle()
    val metrics by viewModel.financialMetrics.collectAsStateWithLifecycle()
    val pendingCount by viewModel.pendingFeeAlerts.collectAsStateWithLifecycle()
    val syncState by viewModel.syncState.collectAsStateWithLifecycle()
    val unreadActivityCount by viewModel.unreadActivityCount.collectAsStateWithLifecycle()
    val activityFeed by viewModel.activityFeed.collectAsStateWithLifecycle()
    val updateInfo by viewModel.appUpdateInfo.collectAsStateWithLifecycle()
    val downloadProgress by viewModel.downloadProgress.collectAsStateWithLifecycle()
    val downloadedApkFile by viewModel.downloadedApkFile.collectAsStateWithLifecycle()
    
    var currentTab by remember { mutableIntStateOf(0) } // 0: Students, 1: Ledger, 2: Reports
    var showSystemOverdueNotification by rememberSaveable { mutableStateOf(true) }
    var showActivityDialog by remember { mutableStateOf(false) }
    var showCloudSyncDialog by remember { mutableStateOf(false) }
    var showUserMenu by remember { mutableStateOf(false) }
    var showUserManagementDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.checkForAppUpdates(silent = true)
    }

    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    var showQuickActionSheet by remember { mutableStateOf(false) }
    var showSpeedDialAdmission by remember { mutableStateOf(false) }
    var showSpeedDialFee by remember { mutableStateOf(false) }
    var showSpeedDialExpense by remember { mutableStateOf(false) }
    var showSpeedDialDeposit by remember { mutableStateOf(false) }
    var showFamilyFeeDialog by remember { mutableStateOf(false) }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isWideScreen = maxWidth >= 840.dp

        if (isWideScreen) {
            // ==================== TABLET / DESKTOP MINIMALIST NAVIGATION RAIL ====================
            Row(modifier = Modifier.fillMaxSize()) {
                NavigationRail(
                    containerColor = MaterialTheme.colorScheme.surface,
                    header = {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(top = 20.dp, bottom = 16.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                                modifier = Modifier.size(50.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    BLSLogo(customSize = 34.dp)
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "BLS Ledger",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                                modifier = Modifier.height(24.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = if (role == DomainConstants.ROLE_ADMIN) Icons.Default.AdminPanelSettings else Icons.Default.Person,
                                        contentDescription = role,
                                        modifier = Modifier.size(12.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        role,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    },
                    modifier = Modifier.fillMaxHeight()
                ) {
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    NavigationRailItem(
                        selected = currentTab == 0,
                        onClick = { currentTab = 0 },
                        icon = { Icon(Icons.Default.School, contentDescription = "Students") },
                        label = { Text("Students", fontWeight = if (currentTab == 0) FontWeight.Bold else FontWeight.Normal) },
                        colors = NavigationRailItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                    NavigationRailItem(
                        selected = currentTab == 1,
                        onClick = { currentTab = 1 },
                        icon = { Icon(Icons.AutoMirrored.Filled.ReceiptLong, contentDescription = "Ledger") },
                        label = { Text("Ledger", fontWeight = if (currentTab == 1) FontWeight.Bold else FontWeight.Normal) },
                        colors = NavigationRailItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                    NavigationRailItem(
                        selected = currentTab == 2,
                        onClick = { currentTab = 2 },
                        icon = { 
                            BadgedBox(badge = {
                                if (pendingCount.isNotEmpty()) {
                                    Badge(
                                        containerColor = MaterialTheme.financialColors.expense,
                                        contentColor = Color.White
                                    ) {
                                        Text(if (pendingCount.size > 99) "99+" else pendingCount.size.toString())
                                    }
                                }
                            }) {
                                Icon(Icons.AutoMirrored.Filled.Assignment, contentDescription = "Reports") 
                            }
                        },
                        label = { Text("Reports", fontWeight = if (currentTab == 2) FontWeight.Bold else FontWeight.Normal) },
                        colors = NavigationRailItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )

                    Spacer(modifier = Modifier.weight(1f))

                    IconButton(
                        onClick = { showActivityDialog = true }
                    ) {
                        BadgedBox(badge = {
                            if (unreadActivityCount > 0) {
                                Badge(
                                    containerColor = MaterialTheme.financialColors.expense,
                                    contentColor = Color.White
                                ) {
                                    Text(unreadActivityCount.toString())
                                }
                            }
                        }) {
                            Icon(
                                imageVector = if (unreadActivityCount > 0) Icons.Default.NotificationsActive else Icons.Default.Notifications,
                                contentDescription = "Notifications",
                                tint = if (unreadActivityCount > 0) MaterialTheme.financialColors.amber else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    IconButton(
                        onClick = { viewModel.logout() },
                        modifier = Modifier.padding(bottom = 16.dp)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ExitToApp,
                            contentDescription = "Logout",
                            tint = MaterialTheme.financialColors.expense
                        )
                    }
                }

                VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f), thickness = 1.dp)

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.background)
                ) {
                    if (currentTab == 0) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            DailyClosingBalanceCard(
                                netBalance = metrics.netBalance,
                                cashBalance = metrics.cashBalance,
                                bankBalance = metrics.bankBalance,
                                onCashClick = { currentTab = 1 },
                                onBankClick = { currentTab = 1 },
                                modifier = Modifier.weight(1.1f)
                            )
                            Column(modifier = Modifier.weight(0.9f)) {
                                QuickStatsGrid(
                                    totalIncome = metrics.totalIncome,
                                    totalExpense = metrics.totalExpense,
                                    onIncomeClick = { currentTab = 1 },
                                    onExpenseClick = { currentTab = 1 }
                                )
                                if (pendingCount.isNotEmpty()) {
                                    Box(modifier = Modifier.padding(top = 4.dp)) {
                                        PendingFeeAlertBanner(
                                            pendingCount = pendingCount.size,
                                            onReviewClick = { currentTab = 2 }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Box(modifier = Modifier.weight(1f)) {
                        Crossfade(targetState = currentTab, label = "TabletTabTransition") { tab ->
                            when (tab) {
                                0 -> StudentsTab(viewModel)
                                1 -> LedgerTab(viewModel, showAdminPanel = role == DomainConstants.ROLE_ADMIN || role == DomainConstants.ROLE_SUPER_ADMIN)
                                2 -> ReportsTab(viewModel, showAdminPanel = role == DomainConstants.ROLE_ADMIN || role == DomainConstants.ROLE_SUPER_ADMIN)
                            }
                        }
                    }
                }

                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.BottomEnd
                ) {
                    FloatingActionButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            showQuickActionSheet = true
                        },
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = Color.White,
                        shape = RoundedCornerShape(16.dp),
                        elevation = FloatingActionButtonDefaults.elevation(0.dp, pressedElevation = 2.dp),
                        modifier = Modifier
                            .padding(28.dp)
                            .size(54.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Quick Actions", modifier = Modifier.size(26.dp))
                    }
                }
            }
        } else {
            // ==================== SMARTPHONE MINIMALIST SCAFFOLD ====================
            Scaffold(
                floatingActionButton = {
                    FloatingActionButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            showQuickActionSheet = true
                        },
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = Color.White,
                        shape = RoundedCornerShape(16.dp),
                        elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 2.dp, pressedElevation = 4.dp),
                        modifier = Modifier.size(52.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Quick Actions", modifier = Modifier.size(24.dp))
                    }
                },
                topBar = {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        TopAppBar(
                            title = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                                        modifier = Modifier.size(38.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            BLSLogo(customSize = 25.dp)
                                        }
                                    }
                                    Column {
                                        Text(
                                            "BLS Cash Record",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        // Minimalist Sync Capsule Pill
                                        Surface(
                                            onClick = {
                                                if (role == DomainConstants.ROLE_ADMIN) {
                                                    showCloudSyncDialog = true
                                                } else {
                                                    viewModel.flushOfflineSyncQueue()
                                                }
                                            },
                                            shape = RoundedCornerShape(100.dp),
                                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                            border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                when (syncState) {
                                                    com.example.data.SyncState.SYNCING -> {
                                                        CircularProgressIndicator(
                                                            modifier = Modifier.size(8.dp),
                                                            strokeWidth = 1.5.dp,
                                                            color = MaterialTheme.colorScheme.primary
                                                        )
                                                        Text(
                                                            "Syncing...",
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = MaterialTheme.colorScheme.primary,
                                                            fontSize = 10.sp
                                                        )
                                                    }
                                                    com.example.data.SyncState.OFFLINE -> {
                                                        Box(
                                                            modifier = Modifier
                                                                .size(6.dp)
                                                                .background(MaterialTheme.financialColors.amber, CircleShape)
                                                        )
                                                        Text(
                                                            "Offline (Tap to Sync)",
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = MaterialTheme.financialColors.amber,
                                                            fontSize = 10.sp
                                                        )
                                                    }
                                                    com.example.data.SyncState.ERROR -> {
                                                        Box(
                                                            modifier = Modifier
                                                                .size(6.dp)
                                                                .background(MaterialTheme.financialColors.expense, CircleShape)
                                                        )
                                                        Text(
                                                            "Sync Alert",
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = MaterialTheme.financialColors.expense,
                                                            fontSize = 10.sp
                                                        )
                                                    }
                                                    com.example.data.SyncState.SYNCED -> {
                                                        Box(
                                                            modifier = Modifier
                                                                .size(6.dp)
                                                                .background(MaterialTheme.financialColors.cash, CircleShape)
                                                        )
                                                        Text(
                                                            "Live Synced",
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                            fontSize = 10.sp
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            },
                            actions = {
                                // Notifications Icon
                                IconButton(
                                    onClick = { showActivityDialog = true },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    BadgedBox(
                                        badge = {
                                            if (unreadActivityCount > 0) {
                                                Badge(
                                                    containerColor = MaterialTheme.financialColors.expense,
                                                    contentColor = Color.White
                                                ) {
                                                    Text(
                                                        if (unreadActivityCount > 9) "9+" else unreadActivityCount.toString(),
                                                        fontSize = 9.sp
                                                    )
                                                }
                                            }
                                        }
                                    ) {
                                        Icon(
                                            imageVector = if (unreadActivityCount > 0) Icons.Default.NotificationsActive else Icons.Default.Notifications,
                                            contentDescription = "Notifications",
                                            tint = if (unreadActivityCount > 0) MaterialTheme.financialColors.amber else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(4.dp))

                                // Minimalist Profile & Settings Capsule Pill
                                Box {
                                    Surface(
                                        onClick = { showUserMenu = true },
                                        shape = RoundedCornerShape(100.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                                        modifier = Modifier.height(32.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 9.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = if (role == DomainConstants.ROLE_ADMIN) Icons.Default.AdminPanelSettings else Icons.Default.Person,
                                                contentDescription = role,
                                                modifier = Modifier.size(14.dp),
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                role,
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                fontSize = 11.5.sp
                                            )
                                            Spacer(modifier = Modifier.width(2.dp))
                                            Icon(
                                                Icons.Default.ArrowDropDown,
                                                contentDescription = null,
                                                modifier = Modifier.size(15.dp),
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }

                                    DropdownMenu(
                                        expanded = showUserMenu,
                                        onDismissRequest = { showUserMenu = false },
                                        modifier = Modifier.widthIn(min = 190.dp)
                                    ) {
                                        DropdownMenuItem(
                                            text = {
                                                Column {
                                                    Text("Current Role", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                    Text(role, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                                                }
                                            },
                                            onClick = { showUserMenu = false },
                                            leadingIcon = {
                                                Icon(
                                                    if (role == DomainConstants.ROLE_ADMIN) Icons.Default.AdminPanelSettings else Icons.Default.Person,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                        )

                                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))

                                        DropdownMenuItem(
                                            text = { Text("Cloud Sync Settings") },
                                            onClick = {
                                                showUserMenu = false
                                                if (role == DomainConstants.ROLE_ADMIN) {
                                                    showCloudSyncDialog = true
                                                } else {
                                                    viewModel.flushOfflineSyncQueue()
                                                }
                                            },
                                            leadingIcon = {
                                                Icon(Icons.Default.CloudSync, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                        )

                                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))

                                        if (role == DomainConstants.ROLE_ADMIN || role == DomainConstants.ROLE_SUPER_ADMIN) {
                                            DropdownMenuItem(
                                                text = { 
                                                    Column {
                                                        Text("Staff & Access Control", fontWeight = FontWeight.SemiBold)
                                                        Text("Manage users, PINs & permissions", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                    }
                                                },
                                                onClick = {
                                                    showUserMenu = false
                                                    showUserManagementDialog = true
                                                },
                                                leadingIcon = {
                                                    Icon(Icons.Default.ManageAccounts, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                                }
                                            )

                                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
                                        }

                                        DropdownMenuItem(
                                            text = { Text("Sign Out / Lock", color = MaterialTheme.financialColors.expense, fontWeight = FontWeight.SemiBold) },
                                            onClick = {
                                                showUserMenu = false
                                                viewModel.logout()
                                            },
                                            leadingIcon = {
                                                Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = null, tint = MaterialTheme.financialColors.expense)
                                            }
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(8.dp))
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = MaterialTheme.colorScheme.surface,
                                titleContentColor = MaterialTheme.colorScheme.onSurface
                            )
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
                    }
                },
                bottomBar = {
                    val borderLineColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface,
                        tonalElevation = 2.dp,
                        windowInsets = NavigationBarDefaults.windowInsets,
                        modifier = Modifier
                            .fillMaxWidth()
                            .drawBehind {
                                drawLine(
                                    color = borderLineColor,
                                    start = Offset(0f, 0f),
                                    end = Offset(size.width, 0f),
                                    strokeWidth = 1f
                                )
                            }
                            .zIndex(100f)
                    ) {
                        NavigationBarItem(
                            selected = currentTab == 0,
                            onClick = { currentTab = 0 },
                            icon = { Icon(Icons.Default.School, contentDescription = "Students") },
                            label = { Text("Students", fontWeight = if (currentTab == 0) FontWeight.Bold else FontWeight.Normal) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                        NavigationBarItem(
                            selected = currentTab == 1,
                            onClick = { currentTab = 1 },
                            icon = { Icon(Icons.AutoMirrored.Filled.ReceiptLong, contentDescription = "Ledger") },
                            label = { Text("Ledger", fontWeight = if (currentTab == 1) FontWeight.Bold else FontWeight.Normal) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                        NavigationBarItem(
                            selected = currentTab == 2,
                            onClick = { currentTab = 2 },
                            icon = { 
                                BadgedBox(
                                    badge = {
                                        if (pendingCount.isNotEmpty()) {
                                            Badge(
                                                containerColor = MaterialTheme.financialColors.expense,
                                                contentColor = Color.White
                                            ) {
                                                Text(
                                                    if (pendingCount.size > 99) "99+" else pendingCount.size.toString(),
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.Assignment, contentDescription = "Reports") 
                                }
                            },
                            label = { Text("Reports", fontWeight = if (currentTab == 2) FontWeight.Bold else FontWeight.Normal) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    }
                }
            ) { innerPadding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .background(MaterialTheme.colorScheme.background)
                ) {
                    if (currentTab == 0 && pendingCount.isNotEmpty()) {
                        Box(modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
                            PendingFeeAlertBanner(
                                pendingCount = pendingCount.size,
                                onReviewClick = { currentTab = 2 }
                            )
                        }
                    }

                    Box(modifier = Modifier.weight(1f)) {
                        Crossfade(
                            targetState = currentTab,
                            animationSpec = tween(180),
                            label = "TabContentCrossfade"
                        ) { tab ->
                            when (tab) {
                                0 -> StudentsTab(viewModel)
                                1 -> LedgerTab(viewModel, showAdminPanel = role == DomainConstants.ROLE_ADMIN || role == DomainConstants.ROLE_SUPER_ADMIN)
                                2 -> ReportsTab(viewModel, showAdminPanel = role == DomainConstants.ROLE_ADMIN || role == DomainConstants.ROLE_SUPER_ADMIN)
                            }
                        }
                    }
                }
            }
        }
    }

    var speedDialAdmittedStudent by remember { mutableStateOf<Student?>(null) }
    var speedDialFeePaid by remember { mutableStateOf(0.0) }
    var speedDialFeeMode by remember { mutableStateOf(DomainConstants.MODE_CASH) }

    if (showSpeedDialAdmission) {
        val nextRegNo = remember { viewModel.generateNextRegNo() }
        AddStudentForm(
            initialRegNo = nextRegNo,
            onDismiss = { showSpeedDialAdmission = false },
            onSubmit = { reg, n, fn, c, ph, adm, mon, ann, disc, oth, idCard, prevSchool, fatherIdCard, schoolCertSel, formBSel, fatherCnicSel, picsSel, initFee, initMode, photo ->
                viewModel.addStudent(
                    name = n,
                    fName = fn,
                    clsName = c,
                    contact = ph,
                    admFee = adm,
                    mthFee = mon,
                    annCharges = ann,
                    discType = disc,
                    othCharges = oth,
                    idCard = idCard,
                    prevSchool = prevSchool,
                    fatherIdCard = fatherIdCard,
                    schoolCertAttached = schoolCertSel,
                    formBAttached = formBSel,
                    fatherCnicAttached = fatherCnicSel,
                    picsAttached = picsSel,
                    regNo = reg,
                    photoUri = photo,
                    onSuccess = { savedStudent ->
                        AudioFeedback.playSuccessChime()
                        if (initFee > 0) {
                            val curMonth = SimpleDateFormat("MMMM yyyy", Locale.US).format(Date())
                            viewModel.recordAdmissionInitialFee(
                                student = savedStudent,
                                totalPaid = initFee,
                                mode = initMode,
                                month = curMonth,
                                onSuccess = {
                                    android.widget.Toast.makeText(context, "Rs. ${initFee.toInt()} received at counter ($initMode)!", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            )
                        }
                        speedDialAdmittedStudent = savedStudent
                        speedDialFeePaid = initFee
                        speedDialFeeMode = initMode
                    }
                )
                showSpeedDialAdmission = false
                currentTab = 0
            }
        )
    }

    if (speedDialAdmittedStudent != null) {
        val curMonth = remember { SimpleDateFormat("MMMM yyyy", Locale.US).format(Date()) }
        AdmissionSuccessDialog(
            student = speedDialAdmittedStudent!!,
            initialFeePaid = speedDialFeePaid,
            initialFeeMode = speedDialFeeMode,
            onDismiss = { speedDialAdmittedStudent = null },
            onPrintAdmissionForm = {
                val file = ReportExporter.exportAdmissionFormToPdf(context, speedDialAdmittedStudent!!)
                if (file != null) {
                    val dest = ReportExporter.savePdfToDownloads(context, file, "BLS_Admission_${speedDialAdmittedStudent!!.studentName.replace(" ", "_")}")
                    android.widget.Toast.makeText(context, "Admission Form! ${dest.pathMessage}", android.widget.Toast.LENGTH_LONG).show()
                    ReportExporter.sharePdf(context, file)
                }
            },
            onPrintChallan = {
                val std = speedDialAdmittedStudent!!
                val discountedMonthly = DomainConstants.calculateDiscountedFee(std.monthlyFee, std.discountType)
                val file = ReportExporter.exportAdmissionChallanToPdf(
                    context = context,
                    student = std,
                    monthYear = curMonth,
                    admissionFee = std.admissionFee,
                    monthlyFee = std.monthlyFee,
                    discountedMonthlyFee = discountedMonthly,
                    discountType = std.discountType,
                    annualCharges = std.annualCharges,
                    otherCharges = std.otherCharges,
                    paidAtCounter = speedDialFeePaid,
                    dueDateStr = if (speedDialFeePaid > 0) "Paid at Counter ($speedDialFeeMode)" else "Due at Admission"
                )
                if (file != null) {
                    val dest = ReportExporter.savePdfToDownloads(context, file, "BLS_Admission_Challan_${std.studentName.replace(" ", "_")}")
                    android.widget.Toast.makeText(context, "Admission Challan Created! ${dest.pathMessage}", android.widget.Toast.LENGTH_LONG).show()
                    ReportExporter.sharePdf(context, file)
                } else {
                    android.widget.Toast.makeText(context, "Failed to compile Admission Challan PDF", android.widget.Toast.LENGTH_SHORT).show()
                }
            },
            onShareWhatsApp = {
                val cleanNumber = InputFormatUtils.sanitizePakistanPhoneForWhatsApp(speedDialAdmittedStudent!!.contactNumber)
                val paidText = if (speedDialFeePaid > 0) "💵 *Admission Fee Paid:* Rs. ${speedDialFeePaid.toInt()} ($speedDialFeeMode)\n" else ""
                val message = """
*BLENDED LEARNING SCHOOL (BLS)*
*Official Admission Confirmation*
---------------------------------------
Dear Parent / Guardian,
We are pleased to inform you that your child's admission has been successfully confirmed at BLS School.

👤 *Student Name:* ${speedDialAdmittedStudent!!.studentName}
🆔 *Registration No:* ${speedDialAdmittedStudent!!.regNo}
📚 *Class Level:* ${speedDialAdmittedStudent!!.className}
👨‍👦 *Father / Guardian:* ${speedDialAdmittedStudent!!.fatherName}
📅 *Monthly Tuition Package:* Rs. ${speedDialAdmittedStudent!!.monthlyFee.toInt()}
$paidText---------------------------------------
Welcome to the BLS School Community!

Warm regards,
*BLS Admissions Office*
                """.trimIndent()

                try {
                    val uri = android.net.Uri.parse("https://api.whatsapp.com/send?phone=$cleanNumber&text=${android.net.Uri.encode(message)}")
                    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, uri).apply {
                        flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(intent)
                } catch (e: Exception) {
                    android.widget.Toast.makeText(context, "Could not open WhatsApp: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                }
            },
            onSendSms = {
                ReportExporter.sendSmsAdmissionWelcome(
                    context = context,
                    student = speedDialAdmittedStudent!!,
                    initialPaid = speedDialFeePaid
                )
            }
        )
    }

    if (showSpeedDialFee) {
        ReceiveFeeDialogDirect(
            viewModel = viewModel,
            onDismiss = { showSpeedDialFee = false },
            onConfirm = { student, amount, category, mode, month, remarks, remainingArrears ->
                viewModel.recordFeePayment(
                    student = student,
                    amount = amount,
                    mode = mode,
                    month = month,
                    category = category,
                    remarks = remarks,
                    onSuccess = { savedTx ->
                        AudioFeedback.playSuccessChime()
                        val file = ReportExporter.exportFeeSlipToPdf(context, savedTx, student)
                        if (file != null) {
                            val dest = ReportExporter.savePdfToDownloads(context, file, "BLS_FeeSlip_${savedTx.id}")
                            android.widget.Toast.makeText(context, "Fee Payment Received! ${dest.pathMessage}", android.widget.Toast.LENGTH_LONG).show()
                            ReportExporter.sharePdf(context, file)
                        }
                    }
                )
                showSpeedDialFee = false
                currentTab = 1
            }
        )
    }

    if (showSpeedDialExpense) {
        AddExpenseDialog(
            onDismiss = { showSpeedDialExpense = false },
            onSubmit = { amount, category, mode, details ->
                viewModel.addExpense(category, amount, mode, details)
                AudioFeedback.playSuccessChime()
                android.widget.Toast.makeText(context, "Expense Rs. ${amount.toInt()} logged!", android.widget.Toast.LENGTH_SHORT).show()
                showSpeedDialExpense = false
                currentTab = 1
            }
        )
    }

    if (showSpeedDialDeposit) {
        CashToBankDepositDialog(
            availableCash = metrics.cashBalance,
            availableBank = metrics.bankBalance,
            onDismiss = { showSpeedDialDeposit = false },
            onConfirm = { amount, bankDetails, slipNo, remarks ->
                viewModel.recordCashToBankDeposit(
                    amount = amount,
                    bankDetails = bankDetails,
                    slipNo = slipNo,
                    remarks = remarks,
                    onSuccess = {
                        AudioFeedback.playSuccessChime()
                        android.widget.Toast.makeText(context, "Deposited Rs. ${amount.toInt()} to Bank!", android.widget.Toast.LENGTH_SHORT).show()
                    }
                )
                showSpeedDialDeposit = false
                currentTab = 1
            },
            onConfirmWithdrawal = { amount, bankDetails, slipNo, remarks ->
                viewModel.recordBankToCashWithdrawal(
                    amount = amount,
                    bankDetails = bankDetails,
                    chequeOrRefNo = slipNo,
                    remarks = remarks,
                    onSuccess = {
                        AudioFeedback.playSuccessChime()
                        android.widget.Toast.makeText(context, "Withdrawn Rs. ${amount.toInt()} from Bank to Cash Box!", android.widget.Toast.LENGTH_SHORT).show()
                    }
                )
                showSpeedDialDeposit = false
                currentTab = 1
            }
        )
    }

    if (showFamilyFeeDialog) {
        FamilyFeeCollectionDialog(
            viewModel = viewModel,
            onDismiss = { showFamilyFeeDialog = false },
            onSuccess = { fatherName, phone, familyVoucherNo, payments, grandTotal, mode ->
                viewModel.recordBulkFeePayments(
                    payments = payments,
                    mode = mode,
                    remarks = "Family fee collection (Voucher: $familyVoucherNo)",
                    onSuccess = {
                        AudioFeedback.playSuccessChime()
                        val file = ReportExporter.exportFamilyFeeSlipPdf(
                            context = context,
                            fatherName = fatherName,
                            fatherPhone = phone,
                            familyVoucherNo = familyVoucherNo,
                            siblingItems = payments,
                            totalPaid = grandTotal,
                            paymentMode = mode
                        )
                        if (file != null) {
                            val res = ReportExporter.savePdfToDownloads(context, file, "BLS_FamilyFee_${fatherName.replace(" ", "_")}")
                            android.widget.Toast.makeText(context, "Family Fee Slip Generated! ${res.pathMessage}", android.widget.Toast.LENGTH_LONG).show()
                            ReportExporter.sharePdf(context, file)
                        } else {
                            android.widget.Toast.makeText(context, "Family fee recorded! Rs. ${grandTotal.toInt()}", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                )
                showFamilyFeeDialog = false
                currentTab = 1
            }
        )
    }

    if (showQuickActionSheet) {
        QuickActionBottomSheet(
            onDismiss = { showQuickActionSheet = false },
            onNewAdmission = { showSpeedDialAdmission = true },
            onReceiveFee = { showSpeedDialFee = true },
            onLogExpense = { showSpeedDialExpense = true },
            onCashToBank = { showSpeedDialDeposit = true },
            onFamilyFee = { showFamilyFeeDialog = true }
        )
    }

    if (showSystemOverdueNotification && pendingCount.isNotEmpty() && (role == DomainConstants.ROLE_ADMIN || role == DomainConstants.ROLE_ACCOUNTANT)) {
        OverdueFeesNotificationDialog(
            alerts = pendingCount,
            onDismiss = { showSystemOverdueNotification = false },
            onReview = {
                showSystemOverdueNotification = false
                currentTab = 2
            }
        )
    }

    if (showActivityDialog) {
        ActivityFeedDialog(
            feedItems = activityFeed,
            currentRole = role,
            onDismiss = { showActivityDialog = false },
            onMarkAllRead = { viewModel.markActivitiesAsRead() }
        )
    }

    if (showCloudSyncDialog) {
        CloudSyncSettingsDialog(
            initialUrl = viewModel.getFirebaseUrl(),
            initialSecret = viewModel.getFirebaseAuthSecret(),
            onDismiss = { showCloudSyncDialog = false },
            onTestConnection = { url, secret ->
                viewModel.testCloudSync(url, secret)
            },
            onSaveConfig = { url, secret ->
                viewModel.updateCloudSyncConfig(url, secret)
            }
        )
    }

    if (showUserManagementDialog) {
        UserManagementDialog(
            viewModel = viewModel,
            onDismiss = { showUserManagementDialog = false }
        )
    }

    if (updateInfo != null) {
        AppUpdateDialog(
            updateInfo = updateInfo!!,
            downloadProgress = downloadProgress,
            isDownloading = downloadProgress != null && downloadedApkFile == null,
            isDownloaded = downloadedApkFile != null,
            onDismiss = { viewModel.dismissUpdateDialog() },
            onStartDownload = {
                viewModel.downloadAndInstallUpdate(context, updateInfo!!)
            },
            onInstallNow = {
                viewModel.installDownloadedApk(context)
            }
        )
    }
}
