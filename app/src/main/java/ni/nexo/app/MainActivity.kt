package ni.nexo.app

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import io.github.jan.supabase.auth.handleDeeplinks
import ni.nexo.app.data.SupabaseClientProvider
import ni.nexo.app.push.NexoPushManager
import ni.nexo.app.ui.NexoApp
import ni.nexo.app.ui.theme.NexoTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        createNotificationChannels()
        requestNotificationPermissionIfNeeded()
        NexoPushManager.start(applicationContext)
        SupabaseClientProvider.client?.handleDeeplinks(intent)
        enableEdgeToEdge()
        setContent {
            NexoTheme {
                NexoApp()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        SupabaseClientProvider.client?.handleDeeplinks(intent)
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQUEST_NOTIFICATIONS)
        }
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java)
        val messages = NotificationChannel(
            CHANNEL_MESSAGES,
            "Mensajes y matches",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Mensajes, nuevos matches y avisos importantes de NEXO"
            enableVibration(true)
        }

        val callAudioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
            .build()
        val calls = NotificationChannel(
            CHANNEL_CALLS,
            "Llamadas",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Llamadas de voz y video de NEXO"
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 700, 350, 700, 350, 700)
            setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE), callAudioAttributes)
        }
        manager.createNotificationChannels(listOf(messages, calls))
    }

    companion object {
        const val CHANNEL_MESSAGES = "nexo_messages"
        const val CHANNEL_CALLS = "nexo_calls"
        private const val REQUEST_NOTIFICATIONS = 1001
    }
}
