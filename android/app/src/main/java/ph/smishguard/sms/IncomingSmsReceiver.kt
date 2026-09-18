package ph.smishguard.sms

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Telephony
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import ph.smishguard.MainActivity
import ph.smishguard.R
import ph.smishguard.SmishGuardApp
import ph.smishguard.model.Detection
import ph.smishguard.model.MAX_MESSAGE_LENGTH

data class PermissionState(val telephony: Boolean, val sms: Boolean, val notifications: Boolean) {
    fun active(consent: Boolean) = consent && telephony && sms
    companion object {
        fun read(context: Context) = PermissionState(
            context.packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY),
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED,
            NotificationManagerCompat.from(context).areNotificationsEnabled() &&
                context.getSystemService(NotificationManager::class.java).getNotificationChannel(WarningNotifications.CHANNEL)?.importance != NotificationManager.IMPORTANCE_NONE
        )
    }
}

object SmsParts {
    /** Android assembles one broadcast's PDUs in segment order. No sender is read. */
    fun combine(parts: List<String?>): String? {
        if (parts.isEmpty() || parts.size > 100 || parts.any { it == null }) return null
        if (parts.sumOf { it!!.length } > MAX_MESSAGE_LENGTH) return null
        return parts.joinToString("").takeIf { it.isNotBlank() }
    }
}

class IncomingSmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        if (!PermissionState.read(context).active(true)) return
        val pending = goAsync()
        val app = context.applicationContext as SmishGuardApp
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                withTimeout(7000) {
                    if (!app.preferences.flow.first().scanning) return@withTimeout
                    val parts = Telephony.Sms.Intents.getMessagesFromIntent(intent)
                    val text = SmsParts.combine(parts.map { it.messageBody }) ?: return@withTimeout
                    val result = app.detector().detect(text)
                    if (result is Detection.Success && result.suspicious && !result.synthetic &&
                        app.preferences.flow.first().scanning && PermissionState.read(context).active(true)) {
                        WarningNotifications.warn(context)
                    }
                }
            } catch (_: Exception) {
                // No input/exception logging: vendor exceptions can contain private SMS data.
            } finally { pending.finish() }
        }
    }
}

object WarningNotifications {
    const val CHANNEL = "suspected_financial_impersonation"
    fun createChannel(context: Context) {
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "Suspicious SMS warnings", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Private warnings without message contents"
                lockscreenVisibility = android.app.Notification.VISIBILITY_SECRET
            })
    }
    fun warn(context: Context) {
        if (!PermissionState.read(context).notifications) return
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context,
                Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val open = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_shield).setContentTitle("Potential financial impersonation")
            .setContentText("Check the recent SMS in your messaging app. Verify through official channels.")
            .setVisibility(NotificationCompat.VISIBILITY_SECRET).setContentIntent(open).setAutoCancel(true).build()
        try { NotificationManagerCompat.from(context).notify(1, notification) } catch (_: SecurityException) { }
    }
}
