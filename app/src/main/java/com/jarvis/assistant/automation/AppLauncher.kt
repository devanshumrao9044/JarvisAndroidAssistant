package com.jarvis.assistant.automation

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings

data class InstalledAppEntry(
    val appName: String,
    val normalizedName: String,
    val packageName: String
)

/**
 * Resolves natural-language app names against the device's actually installed launcher apps
 * and launches them using standard Android Intents.
 */
class AppLauncher(private val context: Context) {

    private val commonSynonyms = mapOf(
        "youtube" to listOf("com.google.android.youtube"),
        "chrome" to listOf("com.android.chrome", "com.chrome.beta", "com.chrome.dev"),
        "chrome browser" to listOf("com.android.chrome"),
        "browser" to listOf("com.android.chrome", "org.mozilla.firefox", "com.sec.android.app.sbrowser"),
        "whatsapp" to listOf("com.whatsapp", "com.whatsapp.w4b"),
        "gmail" to listOf("com.google.android.gm"),
        "maps" to listOf("com.google.android.apps.maps"),
        "google maps" to listOf("com.google.android.apps.maps"),
        "camera" to listOf("com.android.camera", "com.google.android.GoogleCamera", "com.sec.android.app.camera"),
        "phone" to listOf("com.google.android.dialer", "com.android.dialer", "com.samsung.android.dialer"),
        "messages" to listOf("com.google.android.apps.messaging", "com.android.mms", "com.samsung.android.messaging"),
        "calculator" to listOf("com.google.android.calculator", "com.android.calculator2"),
        "clock" to listOf("com.google.android.deskclock", "com.android.deskclock"),
        "calendar" to listOf("com.google.android.calendar", "com.android.calendar"),
        "files" to listOf("com.google.android.apps.nbu.files", "com.android.documentsui"),
        "play store" to listOf("com.android.vending"),
        "spotify" to listOf("com.spotify.music"),
        "instagram" to listOf("com.instagram.android"),
        "telegram" to listOf("org.telegram.messenger")
    )

    fun openApp(requestedAppName: String): ActionExecutionResult {
        val query = normalizeAppQuery(requestedAppName)
        if (query.isEmpty()) {
            return ActionExecutionResult(
                status = ActionExecutionStatus.ACTION_FAILED,
                detailMessage = "App name was empty."
            )
        }

        // Special handling for Android System Settings
        if (query == "settings" || query == "system settings" || query == "phone settings") {
            return try {
                val settingsIntent = Intent(Settings.ACTION_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(settingsIntent)
                ActionExecutionResult(
                    status = ActionExecutionStatus.ACTION_COMPLETED,
                    detailMessage = "Opened Android Settings.",
                    actionSummary = "Opening Settings..."
                )
            } catch (e: Exception) {
                ActionExecutionResult(
                    status = ActionExecutionStatus.ACTION_FAILED,
                    detailMessage = "Could not launch Settings: ${e.localizedMessage}"
                )
            }
        }

        val installedApps = queryInstalledLauncherApps()

        // 1. Exact label match
        val exactMatch = installedApps.firstOrNull { it.normalizedName == query }

        // 2. Known package synonym check (only if that package is actually installed)
        val synonymMatch = if (exactMatch == null) {
            val candidatePackages = commonSynonyms[query].orEmpty()
            installedApps.firstOrNull { it.packageName in candidatePackages }
        } else null

        // 3. Prefix or substring match on installed app labels
        val fuzzyMatch = if (exactMatch == null && synonymMatch == null) {
            installedApps.firstOrNull { it.normalizedName.startsWith(query) }
                ?: installedApps.firstOrNull { it.normalizedName.contains(query) || query.contains(it.normalizedName) }
        } else null

        val resolved = exactMatch ?: synonymMatch ?: fuzzyMatch
            ?: return ActionExecutionResult(
                status = ActionExecutionStatus.TARGET_NOT_FOUND,
                detailMessage = "App '$requestedAppName' is not installed or has no launchable screen on this phone."
            )

        return launchPackage(resolved)
    }

    private fun launchPackage(app: InstalledAppEntry): ActionExecutionResult {
        return try {
            val launchIntent = context.packageManager.getLaunchIntentForPackage(app.packageName)
                ?: return ActionExecutionResult(
                    status = ActionExecutionStatus.ACTION_FAILED,
                    detailMessage = "Package '${app.appName}' does not expose a launch intent."
                )
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            context.startActivity(launchIntent)
            ActionExecutionResult(
                status = ActionExecutionStatus.ACTION_COMPLETED,
                detailMessage = "Launched ${app.appName}.",
                actionSummary = "Opening ${app.appName}..."
            )
        } catch (e: Exception) {
            ActionExecutionResult(
                status = ActionExecutionStatus.ACTION_FAILED,
                detailMessage = "Failed to launch ${app.appName}: ${e.localizedMessage ?: "System blocked launch."}"
            )
        }
    }

    fun queryInstalledLauncherApps(): List<InstalledAppEntry> {
        val pm = context.packageManager
        val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val resolveInfos = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentActivities(mainIntent, PackageManager.ResolveInfoFlags.of(0L))
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(mainIntent, 0)
        }

        return resolveInfos.mapNotNull { info ->
            val pkg = info.activityInfo?.packageName ?: return@mapNotNull null
            val label = info.loadLabel(pm)?.toString()?.trim().orEmpty()
            if (label.isEmpty()) return@mapNotNull null
            InstalledAppEntry(
                appName = label,
                normalizedName = normalizeAppQuery(label),
                packageName = pkg
            )
        }.distinctBy { it.packageName }
    }

    private fun normalizeAppQuery(raw: String): String {
        return raw.lowercase()
            .replace(Regex("^(open|launch|start|run)\\s+"), "")
            .replace(Regex("\\s+(app|application|browser)$"), "")
            .replace(Regex("[^\\p{L}\\p{N} ]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }
}
