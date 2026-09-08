package ni.nexo.app

import android.content.Intent
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
}
