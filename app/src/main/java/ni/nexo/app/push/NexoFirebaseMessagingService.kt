package ni.nexo.app.push

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import ni.nexo.app.MainActivity
import ni.nexo.app.R

/**
 * Receptor FCM de NEXO.
 *
 * Los push de mensajes son deliberadamente genéricos: el texto real del chat no
 * debe viajar por FCM. Supabase conserva el sobre cifrado y NEXO lo descarga al abrir.
 */
class NexoFirebaseMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        NexoPushManager.onNewToken(applicationContext, token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        ensureNotificationChannels()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val data = message.data
        val event = (data["event_type"] ?: data["event"] ?: data["type"] ?: "message").lowercase()
        val isCall = event.contains("call") || !data["call_id"].isNullOrBlank()
        val senderName = data["sender_name"]
            ?: data["peer_name"]
            ?: data["display_name"]
            ?: "NEXO"

        val contentIntent = Intent(this, MainActivity::class.java).apply {
            action = ACTION_OPEN_PUSH
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_EVENT_TYPE, if (isCall) "call" else "message")
            data["message_id"]?.let { putExtra(EXTRA_MESSAGE_ID, it) }
            data["call_id"]?.let { putExtra(EXTRA_CALL_ID, it) }
            data["sender_id"]?.let { putExtra(EXTRA_SENDER_ID, it) }
            data["call_type"]?.let { putExtra(EXTRA_CALL_TYPE, it) }
        }
        val requestCode = (data["call_id"] ?: data["message_id"] ?: message.messageId ?: message.sentTime.toString())
            .hashCode()
        val pendingIntent = PendingIntent.getActivity(
            this,
            requestCode,
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val manager = getSystemService(NotificationManager::class.java)
        if (isCall) {
            val video = data["call_type"].equals("video", ignoreCase = true) || event.contains("video")
            val notification = Notification.Builder(this, MainActivity.CHANNEL_CALLS)
                .setSmallIcon(R.drawable.ic_nexo_notification)
                .setContentTitle(if (video) "Videollamada entrante" else "Llamada entrante")
                .setContentText(senderName)
                .setCategory(Notification.CATEGORY_CALL)
                .setVisibility(Notification.VISIBILITY_PRIVATE)
                .setPriority(Notification.PRIORITY_MAX)
                .setOngoing(true)
                .setAutoCancel(false)
                .setContentIntent(pendingIntent)
                .setFullScreenIntent(pendingIntent, true)
                .setTimeoutAfter(CALL_NOTIFICATION_TIMEOUT_MS)
                .build()
            manager.notify(notificationId(data["call_id"], message.messageId, 7000), notification)
        } else {
            val notification = Notification.Builder(this, MainActivity.CHANNEL_MESSAGES)
                .setSmallIcon(R.drawable.ic_nexo_notification)
                .setContentTitle(if (senderName == "NEXO") "Nuevo mensaje en NEXO" else senderName)
                .setContentText("Tenés un nuevo mensaje privado.")
                .setCategory(Notification.CATEGORY_MESSAGE)
                .setVisibility(Notification.VISIBILITY_PRIVATE)
                .setPriority(Notification.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .build()
            manager.notify(notificationId(data["message_id"], message.messageId, 3000), notification)
        }
    }

    private fun ensureNotificationChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java)

        val messageChannel = NotificationChannel(
            MainActivity.CHANNEL_MESSAGES,
            "Mensajes y matches",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Mensajes, nuevos matches y avisos importantes de NEXO"
            enableVibration(true)
        }

        val callAudioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
            .build()
        val callChannel = NotificationChannel(
            MainActivity.CHANNEL_CALLS,
            "Llamadas",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Llamadas de voz y video de NEXO"
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 700, 350, 700, 350, 700)
            setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE), callAudioAttributes)
            lockscreenVisibility = Notification.VISIBILITY_PRIVATE
        }
        manager.createNotificationChannels(listOf(messageChannel, callChannel))
    }

    private fun notificationId(primaryId: String?, fallbackId: String?, base: Int): Int {
        val hash = (primaryId ?: fallbackId ?: System.currentTimeMillis().toString()).hashCode()
        return base + (hash and 0x0fffffff)
    }

    companion object {
        const val ACTION_OPEN_PUSH = "ni.nexo.app.action.OPEN_PUSH"
        const val EXTRA_EVENT_TYPE = "nexo_event_type"
        const val EXTRA_MESSAGE_ID = "nexo_message_id"
        const val EXTRA_CALL_ID = "nexo_call_id"
        const val EXTRA_SENDER_ID = "nexo_sender_id"
        const val EXTRA_CALL_TYPE = "nexo_call_type"
        private const val CALL_NOTIFICATION_TIMEOUT_MS = 45_000L
    }
}
