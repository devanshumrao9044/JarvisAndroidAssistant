package com.jarvis.assistant.voice

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.jarvis.assistant.MainActivity
import com.jarvis.assistant.R

/**
 * Keeps the active Gemini Live voice session alive while the user switches apps during phone automation.
 * Displays the user's chosen assistant name in the notification and provides a direct 'End Call' action.
 */
class VoiceForegroundService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP_VOICE_SERVICE -> {
                onStopRequestedCallback?.invoke()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            else -> {
                val assistantName = intent?.getStringExtra(EXTRA_ASSISTANT_NAME)?.takeIf { it.isNotBlank() }
                    ?: "AI Assistant"
                val statusText = intent?.getStringExtra(EXTRA_STATUS_TEXT)?.takeIf { it.isNotBlank() }
                    ?: "Gemini Live voice session active"

                createChannelIfNeeded(assistantName)
                val notification = buildNotification(assistantName, statusText)
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        startForeground(
                            NOTIFICATION_ID,
                            notification,
                            ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                        )
                    } else {
                        startForeground(NOTIFICATION_ID, notification)
                    }
                } catch (_: Exception) {
                    // If foreground promotion is restricted by OS state, fail gracefully without crashing
                    stopSelf()
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun createChannelIfNeeded(assistantName: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                CHANNEL_ID,
                "$assistantName Voice Session",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Active real-time voice session status for $assistantName"
                setShowBadge(false)
            }
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(assistantName: String, statusText: String): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPending = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, VoiceForegroundService::class.java).apply {
            action = ACTION_STOP_VOICE_SERVICE
        }
        val stopPending = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_assistant_notification)
            .setContentTitle("$assistantName — Active Voice Session")
            .setContentText(statusText)
            .setContentIntent(openPending)
            .setOngoing(true)
            .addAction(0, "End Call", stopPending)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "jarvis_live_voice_channel"
        private const val NOTIFICATION_ID = 1042
        const val ACTION_STOP_VOICE_SERVICE = "com.jarvis.assistant.STOP_VOICE_SERVICE"
        const val EXTRA_ASSISTANT_NAME = "extra_assistant_name"
        const val EXTRA_STATUS_TEXT = "extra_status_text"

        var onStopRequestedCallback: (() -> Unit)? = null

        fun startOrUpdate(context: Context, assistantName: String, statusText: String) {
            val intent = Intent(context, VoiceForegroundService::class.java).apply {
                putExtra(EXTRA_ASSISTANT_NAME, assistantName)
                putExtra(EXTRA_STATUS_TEXT, statusText)
            }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (_: Exception) {}
        }

        fun stop(context: Context) {
            try {
                context.stopService(Intent(context, VoiceForegroundService::class.java))
            } catch (_: Exception) {}
        }
    }
}
