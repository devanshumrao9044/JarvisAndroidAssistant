package com.jarvis.assistant.permissions

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.jarvis.assistant.automation.AccessibilityAutomationService

data class DevicePermissionSnapshot(
    val hasRecordAudioPermission: Boolean,
    val hasNotificationPermission: Boolean,
    val isAccessibilitySettingEnabled: Boolean,
    val isAccessibilityServiceBound: Boolean,
    val isNetworkAvailable: Boolean
) {
    val isPhoneControlReady: Boolean
        get() = isAccessibilitySettingEnabled && isAccessibilityServiceBound
}

class PermissionManager(private val context: Context) {

    fun hasRecordAudioPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    /**
     * Checks Android Secure Settings to verify if the user has enabled AccessibilityAutomationService.
     */
    fun isAccessibilityServiceEnabledInSettings(): Boolean {
        val expectedComponent = ComponentName(context, AccessibilityAutomationService::class.java)
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        return enabledServices.split(':')
            .mapNotNull { ComponentName.unflattenFromString(it) }
            .any { it == expectedComponent }
    }

    fun isAccessibilityServiceCurrentlyBound(): Boolean {
        return AccessibilityAutomationService.isServiceConnected.value
    }

    fun isNetworkConnected(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    fun getPermissionSnapshot(): DevicePermissionSnapshot {
        return DevicePermissionSnapshot(
            hasRecordAudioPermission = hasRecordAudioPermission(),
            hasNotificationPermission = hasNotificationPermission(),
            isAccessibilitySettingEnabled = isAccessibilityServiceEnabledInSettings(),
            isAccessibilityServiceBound = isAccessibilityServiceCurrentlyBound(),
            isNetworkAvailable = isNetworkConnected()
        )
    }

    fun createOpenAccessibilitySettingsIntent(): Intent {
        return Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }
}
