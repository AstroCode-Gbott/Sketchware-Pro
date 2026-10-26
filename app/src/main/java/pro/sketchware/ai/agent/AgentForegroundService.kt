package pro.sketchware.ai.agent

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import pro.sketchware.R

/**
 * Keeps the Sketchware AI Agent alive during long-horizon tasks (multi-screen generation,
 * LLM reasoning, and headless APK compilation).
 */
class AgentForegroundService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        ensureChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP_AGENT) {
            AgentOrchestrator.cancelRun()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        val status = intent?.getStringExtra(EXTRA_STATUS) ?: "Menjalankan tugas AI Agent..."
        startForeground(NOTIFICATION_ID, buildNotification(status))
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        if (instance === this) {
            instance = null
        }
        super.onDestroy()
    }

    private fun ensureChannel() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Sketchware AI Agent",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Menampilkan progres Long-Horizon Task AI Agent di Sketchware Pro"
        }
        nm.createNotificationChannel(channel)
    }

    private fun buildNotification(statusText: String): Notification {
        val stopIntent = Intent(this, AgentForegroundService::class.java).apply {
            action = ACTION_STOP_AGENT
        }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val stopPendingIntent = PendingIntent.getService(this, 0, stopIntent, flags)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_mtrl_code)
            .setContentTitle("Sketchware Pro AI Agent")
            .setContentText(statusText)
            .setOngoing(true)
            .setProgress(0, 0, true)
            .addAction(R.drawable.ic_cancel_white_96dp, "Stop Agent", stopPendingIntent)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "sketchware_ai_agent_channel"
        private const val NOTIFICATION_ID = 7701
        private const val ACTION_STOP_AGENT = "pro.sketchware.ai.ACTION_STOP_AGENT"
        private const val EXTRA_SC_ID = "sc_id"
        private const val EXTRA_STATUS = "status"

        @Volatile
        private var instance: AgentForegroundService? = null

        @JvmStatic
        fun start(context: Context, scId: String, initialStatus: String) {
            try {
                val intent = Intent(context, AgentForegroundService::class.java).apply {
                    putExtra(EXTRA_SC_ID, scId)
                    putExtra(EXTRA_STATUS, initialStatus)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (_: Throwable) {
            }
        }

        @JvmStatic
        fun updateNotification(statusText: String) {
            val srv = instance ?: return
            try {
                val nm = srv.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                nm.notify(NOTIFICATION_ID, srv.buildNotification(statusText))
            } catch (_: Throwable) {
            }
        }

        @JvmStatic
        fun stop(context: Context) {
            try {
                context.stopService(Intent(context, AgentForegroundService::class.java))
            } catch (_: Throwable) {
            }
        }
    }
}
