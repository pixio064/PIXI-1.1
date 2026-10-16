package com.example.ui.sessions

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.EmptyStateCard
import com.example.usage.AppUsageInfo
import com.example.usage.DetailedSession
import com.example.usage.UsageStatsHelper
import java.text.SimpleDateFormat
import java.util.*

// Samsung One UI Inspired Palette
val OneUiBackground = Color(0xFFF4F6F9)
val OneUiCardBackground = Color(0xFFFFFFFF)
val OneUiNavyText = Color(0xFF0F172A)
val OneUiTealCyan = Color(0xFF0D9488)
val OneUiTealContainer = Color(0xFFCCFBF1)
val OneUiGreenActive = Color(0xFF10B981)
val OneUiPurpleIcon = Color(0xFF8B5CF6)
val OneUiPurpleContainer = Color(0xFFEDE9FE)
val OneUiAmberLabel = Color(0xFFF59E0B)
val OneUiAmberContainer = Color(0xFFFEF3C7)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionsScreen(
    viewModel: SessionsViewModel,
    onNavigateToAppDetail: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val dashboardData by viewModel.dashboardData.collectAsState()
    val selectedCalendar by viewModel.selectedCalendar.collectAsState()
    val selectedTab by viewModel.selectedTab.collectAsState()
    val selectedPeriod by viewModel.selectedPeriod.collectAsState()
    val sessionDetail by viewModel.selectedSessionForDetail.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.loadData(context)
    }

    val data = dashboardData
    val isToday = remember(selectedCalendar) {
        val today = Calendar.getInstance()
        today.get(Calendar.YEAR) == selectedCalendar.get(Calendar.YEAR) &&
                today.get(Calendar.DAY_OF_YEAR) == selectedCalendar.get(Calendar.DAY_OF_YEAR)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Sessions Dashboard",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = OneUiNavyText
                        )
                        Text(
                            text = SimpleDateFormat("EEEE, MMMM d", Locale.getDefault()).format(selectedCalendar.time),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.loadData(context) },
                        modifier = Modifier.testTag("refresh_sessions_btn")
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = OneUiBackground
                )
            )
        },
        containerColor = OneUiBackground,
        modifier = modifier
    ) { innerPadding ->
        LazyColumn(
            contentPadding = PaddingValues(
                top = innerPadding.calculateTopPadding() + 8.dp,
                bottom = innerPadding.calculateBottomPadding() + 24.dp,
                start = 16.dp,
                end = 16.dp
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            // Permission Banner (if not granted)
            if (data != null && !data.hasPermission) {
                item {
                    Card(
                        shape = RoundedCornerShape(22.dp),
                        colors = CardDefaults.cardColors(containerColor = OneUiAmberContainer),
                        modifier = Modifier.fillMaxWidth().testTag("sessions_permission_card")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Security,
                                contentDescription = null,
                                tint = OneUiAmberLabel,
                                modifier = Modifier.size(32.dp)
                            )
                            Spacer(modifier = Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Real Device Sessions Access",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF78350F)
                                )
                                Text(
                                    text = "Previewing sample data. Grant Usage Access to detect exact unlocks, active sessions, and app switches.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFF92400E)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Button(
                                    onClick = { UsageStatsHelper.openUsageAccessSettings(context) },
                                    colors = ButtonDefaults.buttonColors(containerColor = OneUiAmberLabel),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text("Grant Access")
                                }
                            }
                        }
                    }
                }
            }

            // 1. Phone Time vs Away Time Summary Card with App Breakdown Bar
            item {
                PhoneTimeVsAwayCard(
                    screenOnMs = data?.screenOnMs ?: 0L,
                    awayTimeMs = data?.awayTimeMs ?: 0L,
                    screenOnPercent = data?.screenOnPercent ?: 0,
                    awayPercent = data?.awayPercent ?: 100,
                    topAppSlices = data?.topAppsForBar ?: emptyList()
                )
            }

            // 2. Four Statistics Cards in 2-Column Grid (including Last 2 Apps Used)
            item {
                FourStatsGrid(
                    lastPickedUpTime = data?.lastPickedUpTimestamp ?: 0L,
                    lastPickedUpRelative = data?.lastPickedUpRelative ?: "Recently",
                    currentSessionDurationMs = data?.currentSessionDurationMs ?: 0L,
                    isScreenActive = data?.isScreenActive ?: false,
                    inactivityRestMs = data?.inactivityRestMs ?: 0L,
                    lastTwoApps = data?.lastTwoApps ?: emptyList()
                )
            }

            // 3. Two-Tab Header: App Statistics vs Session Timeline
            item {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = OneUiCardBackground),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    TabRow(
                        selectedTabIndex = selectedTab,
                        containerColor = Color.Transparent,
                        contentColor = OneUiTealCyan
                    ) {
                        Tab(
                            selected = selectedTab == 0,
                            onClick = { viewModel.setSelectedTab(0) },
                            text = {
                                Text(
                                    text = "App Statistics",
                                    fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal,
                                    color = if (selectedTab == 0) OneUiTealCyan else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            icon = { Icon(Icons.Default.BarChart, contentDescription = null, tint = if (selectedTab == 0) OneUiTealCyan else MaterialTheme.colorScheme.onSurfaceVariant) },
                            modifier = Modifier.testTag("tab_app_stats")
                        )
                        Tab(
                            selected = selectedTab == 1,
                            onClick = { viewModel.setSelectedTab(1) },
                            text = {
                                Text(
                                    text = "Session Timeline",
                                    fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal,
                                    color = if (selectedTab == 1) OneUiPurpleIcon else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            icon = { Icon(Icons.Default.History, contentDescription = null, tint = if (selectedTab == 1) OneUiPurpleIcon else MaterialTheme.colorScheme.onSurfaceVariant) },
                            modifier = Modifier.testTag("tab_session_timeline")
                        )
                    }
                }
            }

            // Tab 1: App Statistics View
            if (selectedTab == 0) {
                item {
                    // Filter: Daily vs Weekly
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        FilterChip(
                            selected = selectedPeriod == "Daily",
                            onClick = { viewModel.setSelectedPeriod("Daily") },
                            label = { Text("Daily Usage") },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f).testTag("filter_daily")
                        )
                        FilterChip(
                            selected = selectedPeriod == "Weekly",
                            onClick = { viewModel.setSelectedPeriod("Weekly") },
                            label = { Text("7-Day Trend") },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f).testTag("filter_weekly")
                        )
                    }
                }

                val appsList = if (selectedPeriod == "Daily") {
                    data?.dailyAppStats ?: emptyList()
                } else {
                    data?.weeklyAppStats ?: emptyList()
                }

                if (appsList.isEmpty()) {
                    item {
                        EmptyStateCard(
                            title = "No App Usage Recorded",
                            description = "Application activity will populate here as foreground apps are detected.",
                            icon = Icons.Outlined.Apps
                        )
                    }
                } else {
                    val totalAppTime = appsList.sumOf { it.totalTimeInForegroundMs }.coerceAtLeast(1L)
                    items(appsList, key = { it.packageName }) { appInfo ->
                        AppStatRowCard(
                            app = appInfo,
                            totalUsageMs = totalAppTime,
                            onClick = { onNavigateToAppDetail(appInfo.packageName) }
                        )
                    }
                }
            } else {
                // Tab 2: Session Timeline View
                item {
                    // Date Navigation Row
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = OneUiCardBackground),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            IconButton(onClick = { viewModel.navigateDate(context, -1) }) {
                                Icon(Icons.Default.ChevronLeft, contentDescription = "Previous Day")
                            }
                            Text(
                                text = if (isToday) "Today" else SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(selectedCalendar.time),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = OneUiNavyText
                            )
                            IconButton(
                                onClick = { viewModel.navigateDate(context, 1) },
                                enabled = !isToday
                            ) {
                                Icon(Icons.Default.ChevronRight, contentDescription = "Next Day")
                            }
                        }
                    }
                }

                val sessions = data?.sessionTimeline ?: emptyList()
                if (sessions.isEmpty()) {
                    item {
                        EmptyStateCard(
                            title = "No Sessions Found",
                            description = "No usage sessions were detected for this date.",
                            icon = Icons.Outlined.HistoryToggleOff
                        )
                    }
                } else {
                    items(sessions, key = { it.id }) { session ->
                        SessionTimelineCard(
                            session = session,
                            onClick = { viewModel.selectSessionForDetail(session) }
                        )
                    }
                }
            }
        }
    }

    // Session Detail Dialog
    if (sessionDetail != null) {
        val s = sessionDetail!!
        val startStr = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(s.startTimestamp))
        val endStr = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(s.endTimestamp))
        val durMins = s.durationMs / (60 * 1000)
        val inactMins = s.precedingInactivityMs / (60 * 1000)

        AlertDialog(
            onDismissRequest = { viewModel.selectSessionForDetail(null) },
            title = {
                Text(
                    text = "Usage Session Details",
                    fontWeight = FontWeight.Bold,
                    color = OneUiNavyText
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Time Period: $startStr - $endStr (${durMins}m)",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Last App Used: ${s.lastAppName}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = OneUiTealCyan
                    )
                    Text(
                        text = "Preceding Away Time: ${inactMins}m inactive before this session",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Divider(modifier = Modifier.padding(vertical = 4.dp))
                    Text(
                        text = "Apps observed during this session:",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                    s.appsUsed.forEach { appName ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(OneUiPurpleIcon)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = appName, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.selectSessionForDetail(null) },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Close")
                }
            }
        )
    }
}

@Composable
fun PhoneTimeVsAwayCard(
    screenOnMs: Long,
    awayTimeMs: Long,
    screenOnPercent: Int,
    awayPercent: Int,
    topAppSlices: List<com.example.usage.AppUsageSlice>
) {
    val screenOnMins = screenOnMs / (60 * 1000)
    val awayMins = awayTimeMs / (60 * 1000)

    val screenOnHrs = screenOnMins / 60
    val screenOnRemainingMins = screenOnMins % 60

    val awayHrs = awayMins / 60
    val awayRemainingMins = awayMins % 60

    Card(
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = OneUiCardBackground),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth().testTag("phone_vs_away_card")
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Phone Time vs Away Time",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = OneUiNavyText
                )
                Surface(
                    color = OneUiTealContainer,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "Today's Balance",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = OneUiTealCyan,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Multi-app segmented bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(20.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFFE2E8F0))
            ) {
                if (topAppSlices.isNotEmpty()) {
                    topAppSlices.forEach { slice ->
                        Box(
                            modifier = Modifier
                                .weight(slice.durationMs.toFloat().coerceAtLeast(1f))
                                .fillMaxHeight()
                                .background(Color(slice.colorHex))
                        )
                    }
                } else if (screenOnPercent > 0) {
                    Box(
                        modifier = Modifier
                            .weight(screenOnPercent.toFloat().coerceAtLeast(1f))
                            .fillMaxHeight()
                            .background(OneUiTealCyan)
                    )
                }
                if (awayPercent > 0) {
                    Box(
                        modifier = Modifier
                            .weight(awayPercent.toFloat().coerceAtLeast(1f))
                            .fillMaxHeight()
                            .background(Color(0xFF94A3B8))
                    )
                }
            }

            // Visual breakdown chips for apps in the bar
            if (topAppSlices.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(topAppSlices) { slice ->
                        val sliceMins = slice.durationMs / (60 * 1000)
                        val durStr = if (sliceMins >= 60) "${sliceMins / 60}h ${sliceMins % 60}m" else "${sliceMins}m"
                        Surface(
                            color = Color(slice.colorHex).copy(alpha = 0.12f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(Color(slice.colorHex))
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "${slice.appName} ($durStr)",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(slice.colorHex)
                                )
                            }
                        }
                    }
                    item {
                        Surface(
                            color = Color(0xFF94A3B8).copy(alpha = 0.12f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF94A3B8))
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Away (${awayHrs}h ${awayRemainingMins}m)",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF64748B)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Stats Legend Row
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Screen On
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(OneUiTealCyan)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Screen On ($screenOnPercent%)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${screenOnHrs}h ${screenOnRemainingMins}m",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = OneUiNavyText
                    )
                }

                // Away Time
                Column(horizontalAlignment = Alignment.End) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF94A3B8))
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Away / Inactive ($awayPercent%)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${awayHrs}h ${awayRemainingMins}m",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = OneUiNavyText
                    )
                }
            }
        }
    }
}

@Composable
fun FourStatsGrid(
    lastPickedUpTime: Long,
    lastPickedUpRelative: String,
    currentSessionDurationMs: Long,
    isScreenActive: Boolean,
    inactivityRestMs: Long,
    lastTwoApps: List<com.example.usage.RecentAppInfo>
) {
    val timeFormatted = if (lastPickedUpTime > 0) {
        SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(lastPickedUpTime))
    } else {
        "None"
    }

    val currentSessionMins = currentSessionDurationMs / (60 * 1000)
    val currentSessionSecs = (currentSessionDurationMs / 1000) % 60
    val sessionDisplay = if (isScreenActive) {
        "${currentSessionMins}m ${currentSessionSecs}s"
    } else {
        "Screen Off"
    }

    val inactMins = inactivityRestMs / (60 * 1000)
    val inactDisplay = if (inactMins >= 60) "${inactMins / 60}h ${inactMins % 60}m" else "${inactMins}m"

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Row 1: Last Picked Up & Current Session
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            // A. Last Picked Up
            Card(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = OneUiCardBackground),
                modifier = Modifier.weight(1f).testTag("card_last_picked_up")
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(OneUiTealContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.PhoneAndroid, contentDescription = null, tint = OneUiTealCyan, modifier = Modifier.size(18.dp))
                        }
                        Text(
                            text = lastPickedUpRelative,
                            style = MaterialTheme.typography.labelSmall,
                            color = OneUiTealCyan,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = timeFormatted,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = OneUiNavyText
                    )
                    Text(
                        text = "Last Picked Up",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // B. Current Session
            Card(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = OneUiCardBackground),
                modifier = Modifier.weight(1f).testTag("card_current_session")
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFD1FAE5)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Timer, contentDescription = null, tint = OneUiGreenActive, modifier = Modifier.size(18.dp))
                        }
                        if (isScreenActive) {
                            Surface(
                                color = Color(0xFFD1FAE5),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = "● Active",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = OneUiGreenActive,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = sessionDisplay,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (isScreenActive) OneUiGreenActive else OneUiNavyText
                    )
                    Text(
                        text = "Current Session",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Row 2: Inactivity Rest & Last 2 Apps Used
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            // C. Inactivity Rest
            Card(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = OneUiCardBackground),
                modifier = Modifier.weight(1f).testTag("card_inactivity_rest")
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(OneUiAmberContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Spa, contentDescription = null, tint = OneUiAmberLabel, modifier = Modifier.size(18.dp))
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = inactDisplay,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = OneUiNavyText
                    )
                    Text(
                        text = "Inactivity Rest",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Away before session",
                        style = MaterialTheme.typography.labelSmall,
                        color = OneUiAmberLabel
                    )
                }
            }

            // D. Last 2 Apps Used
            Card(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = OneUiCardBackground),
                modifier = Modifier.weight(1f).testTag("card_last_app_used")
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(OneUiPurpleContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Apps, contentDescription = null, tint = OneUiPurpleIcon, modifier = Modifier.size(16.dp))
                        }
                        Surface(
                            color = OneUiPurpleContainer,
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = "Last 2 Apps",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = OneUiPurpleIcon,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))

                    if (lastTwoApps.isNotEmpty()) {
                        lastTwoApps.forEachIndexed { index, app ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                            ) {
                                Surface(
                                    color = if (index == 0) OneUiTealCyan.copy(alpha = 0.15f) else OneUiPurpleIcon.copy(alpha = 0.15f),
                                    shape = CircleShape,
                                    modifier = Modifier.size(18.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = "${index + 1}",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 10.sp,
                                            color = if (index == 0) OneUiTealCyan else OneUiPurpleIcon
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = app.appName,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold,
                                        color = OneUiNavyText,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Text(
                                    text = app.relativeTime,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else {
                        Text(
                            text = "None",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = OneUiNavyText
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Recently active apps",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun AppStatRowCard(
    app: AppUsageInfo,
    totalUsageMs: Long,
    onClick: () -> Unit = {}
) {
    val mins = app.totalTimeInForegroundMs / (60 * 1000)
    val hrs = mins / 60
    val remMins = mins % 60
    val timeFormatted = if (hrs > 0) "${hrs}h ${remMins}m" else "${remMins}m"
    val percent = ((app.totalTimeInForegroundMs.toFloat() / totalUsageMs) * 100).toInt()

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = OneUiCardBackground),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(OneUiTealContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Apps,
                        contentDescription = null,
                        tint = OneUiTealCyan,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = app.appName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = OneUiNavyText
                    )
                    Text(
                        text = "${app.launchCount} opens • ${app.category}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = timeFormatted,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = OneUiNavyText
                    )
                    Text(
                        text = "$percent%",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = OneUiTealCyan
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            LinearProgressIndicator(
                progress = { (percent.toFloat() / 100).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                color = OneUiTealCyan,
                trackColor = Color(0xFFE2E8F0)
            )
        }
    }
}

@Composable
fun SessionTimelineCard(
    session: DetailedSession,
    onClick: () -> Unit
) {
    val startStr = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(session.startTimestamp))
    val endStr = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(session.endTimestamp))
    val durMins = session.durationMs / (60 * 1000)
    val inactMins = session.precedingInactivityMs / (60 * 1000)

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = OneUiCardBackground),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .testTag("session_card_${session.id}")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(16.dp)
        ) {
            // Purple timeline icon
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(OneUiPurpleContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = OneUiPurpleIcon,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "$startStr - $endStr",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = OneUiNavyText
                    )
                    if (session.isCurrentActive) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(color = Color(0xFFD1FAE5), shape = RoundedCornerShape(6.dp)) {
                            Text("● Active", color = OneUiGreenActive, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
                        }
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Ended with: ${session.lastAppName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = OneUiTealCyan,
                    fontWeight = FontWeight.Medium
                )
                if (inactMins > 0) {
                    Text(
                        text = "Preceded by ${inactMins}m away",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Surface(
                color = OneUiPurpleContainer,
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = "${durMins}m",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = OneUiPurpleIcon,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        }
    }
}
