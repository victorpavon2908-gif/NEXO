package ni.nexo.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import io.github.jan.supabase.auth.handleDeeplinks
import ni.nexo.app.data.SupabaseClientProvider
import ni.nexo.app.ui.NexoApp
import ni.nexo.app.ui.theme.NexoTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        createNotificationChannels()
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
        val calls = NotificationChannel(
            CHANNEL_CALLS,
            "Llamadas",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Llamadas de voz y video de NEXO"
            enableVibration(true)
        }
        manager.createNotificationChannels(listOf(messages, calls))
    }

    companion object {
        const val CHANNEL_MESSAGES = "nexo_messages"
        const val CHANNEL_CALLS = "nexo_calls"
    }
}
