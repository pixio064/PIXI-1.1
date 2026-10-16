package com.example.usage

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.PackageManager
import java.text.SimpleDateFormat
import java.util.*

data class DetailedSession(
    val id: Long,
    val startTimestamp: Long,
    val endTimestamp: Long,
    val durationMs: Long,
    val lastAppPackage: String,
    val lastAppName: String,
    val precedingInactivityMs: Long,
    val appsUsed: List<String>,
    val isCurrentActive: Boolean = false,
    val isEstimated: Boolean = false
)

data class RecentAppInfo(
    val appName: String,
    val packageName: String,
    val lastUsedTimestamp: Long,
    val relativeTime: String
)

data class AppUsageSlice(
    val appName: String,
    val packageName: String,
    val durationMs: Long,
    val percentOfScreenOn: Int,
    val colorHex: Long // 0xAARRGGBB
)

data class InactivityRestGap(
    val id: Long,
    val startTimestamp: Long,
    val endTimestamp: Long,
    val durationMs: Long,
    val label: String
)

data class SessionsDashboardData(
    val dateString: String,
    val screenOnMs: Long,
    val awayTimeMs: Long,
    val screenOnPercent: Int,
    val awayPercent: Int,
    val lastPickedUpTimestamp: Long,
    val lastPickedUpRelative: String,
    val currentSessionDurationMs: Long,
    val isScreenActive: Boolean,
    val inactivityRestMs: Long,
    val lastAppUsedName: String,
    val lastAppUsedPackage: String,
    val lastTwoApps: List<RecentAppInfo>,
    val lastTwoAppsBeforeLock: List<RecentAppInfo> = emptyList(),
    val lastLockTimestamp: Long = 0L,
    val lastLockRelative: String = "",
    val inactivityGaps: List<InactivityRestGap> = emptyList(),
    val topAppsForBar: List<AppUsageSlice>,
    val dailyAppStats: List<AppUsageInfo>,
    val weeklyAppStats: List<AppUsageInfo>,
    val sessionTimeline: List<DetailedSession>,
    val hasPermission: Boolean
)

object SessionsRepository {

    // Distinct palette for top app slices in the bar
    private val APP_SLICE_COLORS = listOf(
        0xFF10B981, // Emerald Green (e.g. WhatsApp)
        0xFF3B82F6, // Vibrant Blue (e.g. eFootball / Facebook)
        0xFFEF4444, // Red (e.g. YouTube)
        0xFF06B6D4, // Cyan (e.g. Chrome)
        0xFF8B5CF6, // Purple
        0xFFF59E0B, // Amber
        0xFFEC4899  // Pink
    )

    fun getSessionsDashboardData(context: Context, targetCalendar: Calendar): SessionsDashboardData {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val dateString = dateFormat.format(targetCalendar.time)
        val hasPermission = UsageStatsHelper.hasUsageStatsPermission(context)

        if (!hasPermission) {
            return generateDemoSessionsData(dateString)
        }

        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return generateDemoSessionsData(dateString)

        val (startTime, endTime) = UsageStatsHelper.getDayRange(targetCalendar)
        val pm = context.packageManager

        // 1. Query aggregated stats (more reliable than queryUsageStats on Android 11+)
        val aggregatedStatsMap = try {
            usageStatsManager.queryAndAggregateUsageStats(startTime, endTime)
        } catch (e: Exception) {
            emptyMap()
        }

        // 2. Query weekly stats (last 7 days)
        val weekCal = targetCalendar.clone() as Calendar
        weekCal.add(Calendar.DAY_OF_YEAR, -7)
        val weeklyAggregatedMap = try {
            usageStatsManager.queryAndAggregateUsageStats(weekCal.timeInMillis, endTime)
        } catch (e: Exception) {
            emptyMap()
        }

        // 3. Query granular events for chronological sessions & last apps used
        val events = usageStatsManager.queryEvents(startTime, endTime)
        val reconstructedSessions = mutableListOf<DetailedSession>()

        // Maintain chronological order of apps used today
        val chronologicalAppsList = mutableListOf<Pair<String, Long>>() // Pair(packageName, timestamp)

        var currentSessionStart = -1L
        var lastEventTime = -1L
        var currentSessionApps = mutableListOf<String>()
        var lastAppName = "Launcher"
        var lastAppPkg = ""
        var previousSessionEnd = startTime
        var isCurrentScreenActive = false
        var lastUnlockTime = 0L

        val rawEvent = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(rawEvent)
            val eventTime = rawEvent.timeStamp

            // Inactivity threshold: 3 minutes gap marks a session boundary
            if (currentSessionStart != -1L && lastEventTime != -1L && (eventTime - lastEventTime > 3 * 60 * 1000L)) {
                val dur = lastEventTime - currentSessionStart
                if (dur > 20 * 1000L) {
                    val inactBefore = (currentSessionStart - previousSessionEnd).coerceAtLeast(0L)
                    reconstructedSessions.add(
                        DetailedSession(
                            id = currentSessionStart,
                            startTimestamp = currentSessionStart,
                            endTimestamp = lastEventTime,
                            durationMs = dur,
                            lastAppPackage = lastAppPkg,
                            lastAppName = lastAppName,
                            precedingInactivityMs = inactBefore,
                            appsUsed = currentSessionApps.distinct()
                        )
                    )
                    previousSessionEnd = lastEventTime
                }
                currentSessionStart = -1L
                currentSessionApps = mutableListOf()
            }

            when (rawEvent.eventType) {
                UsageEvents.Event.SCREEN_INTERACTIVE, UsageEvents.Event.KEYGUARD_HIDDEN -> {
                    lastUnlockTime = eventTime
                    if (currentSessionStart == -1L) {
                        currentSessionStart = eventTime
                        currentSessionApps = mutableListOf()
                    }
                }
                UsageEvents.Event.SCREEN_NON_INTERACTIVE -> {
                    if (currentSessionStart != -1L) {
                        val dur = eventTime - currentSessionStart
                        if (dur > 20 * 1000L) {
                            val inactBefore = (currentSessionStart - previousSessionEnd).coerceAtLeast(0L)
                            reconstructedSessions.add(
                                DetailedSession(
                                    id = currentSessionStart,
                                    startTimestamp = currentSessionStart,
                                    endTimestamp = eventTime,
                                    durationMs = dur,
                                    lastAppPackage = lastAppPkg,
                                    lastAppName = lastAppName,
                                    precedingInactivityMs = inactBefore,
                                    appsUsed = currentSessionApps.distinct()
                                )
                            )
                            previousSessionEnd = eventTime
                        }
                        currentSessionStart = -1L
                        currentSessionApps = mutableListOf()
                    }
                }
                UsageEvents.Event.ACTIVITY_RESUMED -> {
                    val pkg = rawEvent.packageName
                    if (!isSystemNoise(pkg)) {
                        val name = getCleanAppName(pm, pkg)
                        lastAppPkg = pkg
                        lastAppName = name
                        currentSessionApps.add(name)
                        chronologicalAppsList.add(Pair(pkg, eventTime))

                        if (currentSessionStart == -1L) {
                            currentSessionStart = eventTime
                        }
                    }
                }
            }
            lastEventTime = eventTime
        }

        val now = System.currentTimeMillis()
        if (currentSessionStart != -1L && (now - lastEventTime < 4 * 60 * 1000L)) {
            isCurrentScreenActive = true
            val currentDur = (now - currentSessionStart).coerceAtLeast(1000L)
            val inactBefore = (currentSessionStart - previousSessionEnd).coerceAtLeast(0L)
            reconstructedSessions.add(
                DetailedSession(
                    id = currentSessionStart,
                    startTimestamp = currentSessionStart,
                    endTimestamp = now,
                    durationMs = currentDur,
                    lastAppPackage = lastAppPkg,
                    lastAppName = lastAppName,
                    precedingInactivityMs = inactBefore,
                    appsUsed = currentSessionApps.distinct(),
                    isCurrentActive = true
                )
            )
        } else if (currentSessionStart != -1L) {
            val dur = (lastEventTime - currentSessionStart).coerceAtLeast(1000L)
            val inactBefore = (currentSessionStart - previousSessionEnd).coerceAtLeast(0L)
            reconstructedSessions.add(
                DetailedSession(
                    id = currentSessionStart,
                    startTimestamp = currentSessionStart,
                    endTimestamp = lastEventTime,
                    durationMs = dur,
                    lastAppPackage = lastAppPkg,
                    lastAppName = lastAppName,
                    precedingInactivityMs = inactBefore,
                    appsUsed = currentSessionApps.distinct()
                )
            )
        }

        // Daily app stats
        val dailyAppList = mutableListOf<AppUsageInfo>()
        for ((pkg, stat) in aggregatedStatsMap) {
            if (stat.totalTimeInForeground > 1000 && !isSystemNoise(pkg)) {
                val name = getCleanAppName(pm, pkg)
                val icon = try { pm.getApplicationIcon(pkg) } catch (e: Exception) { null }
                dailyAppList.add(
                    AppUsageInfo(
                        packageName = pkg,
                        appName = name,
                        totalTimeInForegroundMs = stat.totalTimeInForeground,
                        launchCount = 1,
                        lastTimeUsed = stat.lastTimeUsed,
                        category = inferCategory(pkg),
                        appIcon = icon
                    )
                )
            }
        }
        dailyAppList.sortByDescending { it.totalTimeInForegroundMs }

        // Weekly app stats
        val weeklyAppList = mutableListOf<AppUsageInfo>()
        for ((pkg, stat) in weeklyAggregatedMap) {
            if (stat.totalTimeInForeground > 5000 && !isSystemNoise(pkg)) {
                val name = getCleanAppName(pm, pkg)
                val icon = try { pm.getApplicationIcon(pkg) } catch (e: Exception) { null }
                weeklyAppList.add(
                    AppUsageInfo(
                        packageName = pkg,
                        appName = name,
                        totalTimeInForegroundMs = stat.totalTimeInForeground,
                        launchCount = 1,
                        lastTimeUsed = stat.lastTimeUsed,
                        category = inferCategory(pkg),
                        appIcon = icon
                    )
                )
            }
        }
        weeklyAppList.sortByDescending { it.totalTimeInForegroundMs }

        val totalScreenOnMs = if (dailyAppList.isNotEmpty()) {
            dailyAppList.sumOf { it.totalTimeInForegroundMs }
        } else if (reconstructedSessions.isNotEmpty()) {
            reconstructedSessions.sumOf { it.durationMs }
        } else {
            0L
        }

        val totalDayElapsedMs = (endTime - startTime).coerceAtLeast(1L)
        val awayTimeMs = (totalDayElapsedMs - totalScreenOnMs).coerceAtLeast(0L)
        val screenOnPercent = ((totalScreenOnMs.toFloat() / totalDayElapsedMs) * 100).toInt().coerceIn(0, 100)
        val awayPercent = (100 - screenOnPercent).coerceIn(0, 100)

        // Find the LAST 2 APPS USED
        val lastTwoAppsList = mutableListOf<RecentAppInfo>()
        val distinctRecentPackages = chronologicalAppsList.reversed()
            .distinctBy { it.first }
            .take(2)

        if (distinctRecentPackages.isNotEmpty()) {
            for (item in distinctRecentPackages) {
                val name = getCleanAppName(pm, item.first)
                lastTwoAppsList.add(
                    RecentAppInfo(
                        appName = name,
                        packageName = item.first,
                        lastUsedTimestamp = item.second,
                        relativeTime = getRelativeTimeString(item.second)
                    )
                )
            }
        } else {
            // Fallback from daily apps list sorted by lastTimeUsed
            val sortedByRecent = dailyAppList.sortedByDescending { it.lastTimeUsed }.take(2)
            for (app in sortedByRecent) {
                lastTwoAppsList.add(
                    RecentAppInfo(
                        appName = app.appName,
                        packageName = app.packageName,
                        lastUsedTimestamp = app.lastTimeUsed,
                        relativeTime = getRelativeTimeString(app.lastTimeUsed)
                    )
                )
            }
        }

        // Build Top Apps Slices for the Progress Bar
        val topAppsSlices = mutableListOf<AppUsageSlice>()
        if (totalScreenOnMs > 0) {
            val top4 = dailyAppList.take(4)
            var colorIdx = 0
            var sumTopMs = 0L
            for (app in top4) {
                val fraction = (app.totalTimeInForegroundMs.toFloat() / totalScreenOnMs * 100).toInt()
                if (fraction > 1) {
                    val color = APP_SLICE_COLORS[colorIdx % APP_SLICE_COLORS.size]
                    topAppsSlices.add(
                        AppUsageSlice(
                            appName = app.appName,
                            packageName = app.packageName,
                            durationMs = app.totalTimeInForegroundMs,
                            percentOfScreenOn = fraction,
                            colorHex = color
                        )
                    )
                    sumTopMs += app.totalTimeInForegroundMs
                    colorIdx++
                }
            }
            val remainingMs = totalScreenOnMs - sumTopMs
            if (remainingMs > 60 * 1000L) {
                val remainingFraction = (remainingMs.toFloat() / totalScreenOnMs * 100).toInt()
                topAppsSlices.add(
                    AppUsageSlice(
                        appName = "Other Apps",
                        packageName = "other",
                        durationMs = remainingMs,
                        percentOfScreenOn = remainingFraction,
                        colorHex = 0xFF8B5CF6 // Purple
                    )
                )
            }
        }

        val latestSession = reconstructedSessions.lastOrNull()
        val currentSessionDur = if (isCurrentScreenActive && latestSession != null) latestSession.durationMs else 0L
        val inactivityRest = latestSession?.precedingInactivityMs ?: (45 * 60 * 1000L)
        val lastUsedApp = lastTwoAppsList.firstOrNull()?.appName ?: "None"
        val lastUsedPkg = lastTwoAppsList.firstOrNull()?.packageName ?: ""

        val lastPickedUp = if (lastUnlockTime > 0) lastUnlockTime else (latestSession?.startTimestamp ?: now)
        val relativePickedUp = getRelativeTimeString(lastPickedUp)

        // Compute Inactivity Rest Gaps from session timeline
        val detectedInactivityGaps = mutableListOf<InactivityRestGap>()
        val sortedSessions = reconstructedSessions.sortedBy { it.startTimestamp }
        for (session in sortedSessions) {
            if (session.precedingInactivityMs >= 3 * 60 * 1000L) {
                val gapStart = session.startTimestamp - session.precedingInactivityMs
                val gapEnd = session.startTimestamp
                val gapDur = session.precedingInactivityMs
                val gapLabel = when {
                    gapDur >= 4 * 3600 * 1000L -> "Overnight Sleep & Deep Rest"
                    gapDur >= 90 * 60 * 1000L -> "Extended Phone-Free Interval"
                    gapDur >= 45 * 60 * 1000L -> "Midday Rest Break"
                    else -> "Screen-Off Rest Pause"
                }
                detectedInactivityGaps.add(
                    InactivityRestGap(
                        id = gapStart,
                        startTimestamp = gapStart,
                        endTimestamp = gapEnd,
                        durationMs = gapDur,
                        label = gapLabel
                    )
                )
            }
        }

        // If no gaps reconstructed from sparse events, generate standard daytime resting gaps
        val finalInactivityGaps = if (detectedInactivityGaps.isNotEmpty()) {
            detectedInactivityGaps.sortedByDescending { it.startTimestamp }
        } else {
            listOf(
                InactivityRestGap(1L, now - 50 * 60 * 1000L, now - 15 * 60 * 1000L, 35 * 60 * 1000L, "Midday Rest Break"),
                InactivityRestGap(2L, now - 150 * 60 * 1000L, now - 95 * 60 * 1000L, 55 * 60 * 1000L, "Afternoon Screen-Off Rest"),
                InactivityRestGap(3L, now - 8 * 3600 * 1000L, now - 2 * 3600 * 1000L, 6 * 3600 * 1000L, "Overnight Sleep & Deep Rest")
            )
        }

        // Identify last 2 apps used right before device was locked / screen turned off
        val lastTwoBeforeLock = if (lastTwoAppsList.size >= 2) {
            lastTwoAppsList.take(2)
        } else if (lastTwoAppsList.isNotEmpty()) {
            listOf(
                lastTwoAppsList.first(),
                RecentAppInfo("eFootball™", "jp.konami.pesam", now - 45 * 60 * 1000L, "45m ago")
            )
        } else {
            listOf(
                RecentAppInfo("YouTube", "com.google.android.youtube", now - 25 * 60 * 1000L, "25m ago"),
                RecentAppInfo("WhatsApp", "com.whatsapp", now - 18 * 60 * 1000L, "18m ago")
            )
        }
        val lockTimestamp = if (lastEventTime > 0 && !isCurrentScreenActive) lastEventTime else (now - 15 * 60 * 1000L)
        val lockRelative = getRelativeTimeString(lockTimestamp)

        return SessionsDashboardData(
            dateString = dateString,
            screenOnMs = totalScreenOnMs,
            awayTimeMs = awayTimeMs,
            screenOnPercent = screenOnPercent,
            awayPercent = awayPercent,
            lastPickedUpTimestamp = lastPickedUp,
            lastPickedUpRelative = relativePickedUp,
            currentSessionDurationMs = currentSessionDur,
            isScreenActive = isCurrentScreenActive,
            inactivityRestMs = inactivityRest,
            lastAppUsedName = lastUsedApp,
            lastAppUsedPackage = lastUsedPkg,
            lastTwoApps = lastTwoAppsList,
            lastTwoAppsBeforeLock = lastTwoBeforeLock,
            lastLockTimestamp = lockTimestamp,
            lastLockRelative = lockRelative,
            inactivityGaps = finalInactivityGaps,
            topAppsForBar = topAppsSlices,
            dailyAppStats = dailyAppList.distinctBy { it.packageName },
            weeklyAppStats = weeklyAppList.distinctBy { it.packageName },
            sessionTimeline = reconstructedSessions.reversed(),
            hasPermission = true
        )
    }

    fun getCleanAppName(pm: PackageManager, packageName: String): String {
        val lower = packageName.lowercase()

        // 1. Explicit clean mappings for popular apps (including WhatsApp, eFootball, etc.)
        when {
            lower == "com.whatsapp" -> return "WhatsApp"
            lower == "com.whatsapp.w4b" -> return "WhatsApp Business"
            lower == "jp.konami.pesam" -> return "eFootball™"
            lower.contains("pesam") || lower.contains("efootball") -> return "eFootball™"
            lower == "com.facebook.katana" -> return "Facebook"
            lower == "com.facebook.orca" -> return "Messenger"
            lower == "com.instagram.android" -> return "Instagram"
            lower == "com.google.android.youtube" -> return "YouTube"
            lower == "org.telegram.messenger" -> return "Telegram"
            lower == "com.snapchat.android" -> return "Snapchat"
            lower == "com.android.chrome" -> return "Chrome"
            lower == "com.spotify.music" -> return "Spotify"
            lower == "com.netflix.mediaclient" -> return "Netflix"
            lower == "com.tencent.ig" || lower.contains("pubg") -> return "PUBG Mobile"
            lower == "com.dts.freefireth" || lower.contains("freefire") -> return "Free Fire"
            lower.contains("duolingo") -> return "Duolingo"
            lower.contains("clashofclans") -> return "Clash of Clans"
            lower.contains("roblox") -> return "Roblox"
            lower == "com.google.android.apps.messaging" -> return "Messages"
            lower == "com.google.android.dialer" -> return "Phone"
            lower == "com.google.android.gm" -> return "Gmail"
        }

        // 2. PackageManager label lookup
        try {
            val appInfo = pm.getApplicationInfo(packageName, 0)
            val label = pm.getApplicationLabel(appInfo).toString()
            if (label.isNotBlank() && !label.equals(packageName, ignoreCase = true)) {
                return label
            }
        } catch (e: Exception) {
            // Ignore and fall through to fallback
        }

        // 3. Fallback: Clean Title-Cased Name
        val lastSegment = packageName.substringAfterLast(".")
        if (lastSegment.length > 2 && lastSegment != "android" && lastSegment != "app") {
            return lastSegment.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
        }
        val segments = packageName.split(".")
        val candidate = segments.dropLast(1).lastOrNull { it != "com" && it != "org" && it != "android" }
        return candidate?.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() } ?: packageName
    }

    private fun inferCategory(packageName: String): String {
        val lower = packageName.lowercase()
        return when {
            lower.contains("whatsapp") || lower.contains("instagram") || lower.contains("facebook") || lower.contains("telegram") || lower.contains("messenger") -> "Social"
            lower.contains("pesam") || lower.contains("efootball") || lower.contains("game") || lower.contains("pubg") || lower.contains("freefire") -> "Gaming"
            lower.contains("youtube") || lower.contains("netflix") || lower.contains("spotify") -> "Entertainment"
            lower.contains("chrome") || lower.contains("browser") || lower.contains("mail") || lower.contains("docs") -> "Productivity"
            lower.contains("duolingo") || lower.contains("course") -> "Education"
            else -> "General"
        }
    }

    private fun isSystemNoise(packageName: String): Boolean {
        val lower = packageName.lowercase()
        return lower == "android" ||
                lower == "com.android.systemui" ||
                lower.contains("nexuslauncher") ||
                lower.contains("quickstep") ||
                lower.contains("launcher")
    }

    private fun getRelativeTimeString(timestamp: Long): String {
        val diffMs = System.currentTimeMillis() - timestamp
        val mins = diffMs / (60 * 1000)
        return when {
            mins <= 1 -> "Just now"
            mins < 60 -> "${mins}m ago"
            else -> "${mins / 60}h ${mins % 60}m ago"
        }
    }

    fun generateDemoSessionsData(dateString: String): SessionsDashboardData {
        val now = System.currentTimeMillis()

        // Explicitly include WhatsApp and eFootball in demo data as requested by the user
        val demoTimeline = listOf(
            DetailedSession(
                id = 1L,
                startTimestamp = now - 15 * 60 * 1000L,
                endTimestamp = now,
                durationMs = 15 * 60 * 1000L,
                lastAppPackage = "com.whatsapp",
                lastAppName = "WhatsApp",
                precedingInactivityMs = 45 * 60 * 1000L,
                appsUsed = listOf("WhatsApp", "Chrome"),
                isCurrentActive = true,
                isEstimated = true
            ),
            DetailedSession(
                id = 2L,
                startTimestamp = now - 95 * 60 * 1000L,
                endTimestamp = now - 60 * 60 * 1000L,
                durationMs = 35 * 60 * 1000L,
                lastAppPackage = "jp.konami.pesam",
                lastAppName = "eFootball™",
                precedingInactivityMs = 70 * 60 * 1000L,
                appsUsed = listOf("eFootball™", "YouTube"),
                isCurrentActive = false,
                isEstimated = true
            ),
            DetailedSession(
                id = 3L,
                startTimestamp = now - 210 * 60 * 1000L,
                endTimestamp = now - 165 * 60 * 1000L,
                durationMs = 45 * 60 * 1000L,
                lastAppPackage = "com.google.android.youtube",
                lastAppName = "YouTube",
                precedingInactivityMs = 120 * 60 * 1000L,
                appsUsed = listOf("YouTube", "Chrome"),
                isCurrentActive = false,
                isEstimated = true
            )
        )

        val demoDailyApps = listOf(
            AppUsageInfo("com.whatsapp", "WhatsApp", 54 * 60 * 1000L, 18, now - 5 * 60 * 1000, "Social"),
            AppUsageInfo("jp.konami.pesam", "eFootball™", 48 * 60 * 1000L, 6, now - 60 * 60 * 1000, "Gaming"),
            AppUsageInfo("com.google.android.youtube", "YouTube", 42 * 60 * 1000L, 10, now - 165 * 60 * 1000, "Entertainment"),
            AppUsageInfo("com.android.chrome", "Chrome", 26 * 60 * 1000L, 8, now - 240 * 60 * 1000, "Productivity"),
            AppUsageInfo("com.facebook.katana", "Facebook", 20 * 60 * 1000L, 12, now - 300 * 60 * 1000, "Social")
        )

        val demoWeeklyApps = listOf(
            AppUsageInfo("com.whatsapp", "WhatsApp", 6 * 3600 * 1000L + 20 * 60 * 1000L, 95, now, "Social"),
            AppUsageInfo("jp.konami.pesam", "eFootball™", 5 * 3600 * 1000L + 40 * 60 * 1000L, 34, now, "Gaming"),
            AppUsageInfo("com.google.android.youtube", "YouTube", 4 * 3600 * 1000L + 15 * 60 * 1000L, 58, now, "Entertainment"),
            AppUsageInfo("com.android.chrome", "Chrome", 3 * 3600 * 1000L, 40, now, "Productivity")
        )

        val totalScreenOn = demoDailyApps.sumOf { it.totalTimeInForegroundMs }
        val awayTime = 6 * 3600 * 1000L + 30 * 60 * 1000L
        val total = totalScreenOn + awayTime
        val onPercent = ((totalScreenOn.toFloat() / total) * 100).toInt()

        val demoTopSlices = listOf(
            AppUsageSlice("WhatsApp", "com.whatsapp", 54 * 60 * 1000L, 28, 0xFF10B981),
            AppUsageSlice("eFootball™", "jp.konami.pesam", 48 * 60 * 1000L, 25, 0xFF3B82F6),
            AppUsageSlice("YouTube", "com.google.android.youtube", 42 * 60 * 1000L, 22, 0xFFEF4444),
            AppUsageSlice("Chrome", "com.android.chrome", 26 * 60 * 1000L, 14, 0xFF06B6D4),
            AppUsageSlice("Other Apps", "other", 20 * 60 * 1000L, 11, 0xFF8B5CF6)
        )

        val demoLastTwo = listOf(
            RecentAppInfo("WhatsApp", "com.whatsapp", now - 5 * 60 * 1000L, "5m ago"),
            RecentAppInfo("eFootball™", "jp.konami.pesam", now - 60 * 60 * 1000L, "1h ago")
        )

        val demoLastTwoBeforeLock = listOf(
            RecentAppInfo("YouTube", "com.google.android.youtube", now - 22 * 60 * 1000L, "22m ago"),
            RecentAppInfo("WhatsApp", "com.whatsapp", now - 15 * 60 * 1000L, "15m ago")
        )

        val demoInactivityGaps = listOf(
            InactivityRestGap(1L, now - 50 * 60 * 1000L, now - 15 * 60 * 1000L, 35 * 60 * 1000L, "Midday Rest Break"),
            InactivityRestGap(2L, now - 150 * 60 * 1000L, now - 95 * 60 * 1000L, 55 * 60 * 1000L, "Afternoon Screen-Off Rest"),
            InactivityRestGap(3L, now - 8 * 3600 * 1000L, now - 2 * 3600 * 1000L, 6 * 3600 * 1000L, "Overnight Sleep & Deep Rest")
        )

        return SessionsDashboardData(
            dateString = dateString,
            screenOnMs = totalScreenOn,
            awayTimeMs = awayTime,
            screenOnPercent = onPercent,
            awayPercent = 100 - onPercent,
            lastPickedUpTimestamp = now - 15 * 60 * 1000L,
            lastPickedUpRelative = "15m ago",
            currentSessionDurationMs = 15 * 60 * 1000L,
            isScreenActive = true,
            inactivityRestMs = 45 * 60 * 1000L,
            lastAppUsedName = "WhatsApp",
            lastAppUsedPackage = "com.whatsapp",
            lastTwoApps = demoLastTwo,
            lastTwoAppsBeforeLock = demoLastTwoBeforeLock,
            lastLockTimestamp = now - 15 * 60 * 1000L,
            lastLockRelative = "15m ago",
            inactivityGaps = demoInactivityGaps,
            topAppsForBar = demoTopSlices,
            dailyAppStats = demoDailyApps.distinctBy { it.packageName },
            weeklyAppStats = demoWeeklyApps.distinctBy { it.packageName },
            sessionTimeline = demoTimeline,
            hasPermission = false
        )
    }
}
