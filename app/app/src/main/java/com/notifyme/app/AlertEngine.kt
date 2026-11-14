package com.notifyme.app

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.notifyme.app.ui.MainActivity
import org.json.JSONObject
import java.util.Calendar

/**
 * Turns alerts from the mod into phone behaviour:
 *  T0 status only · T1 silent notification · T2 sound + vibration ·
 *  T3 urgent, repeats every 30 s until acknowledged, may bypass DND ·
 *  T4 full-screen alarm (alarm stream, flashing screen, flashlight strobe, text-to-speech).
 */
class AlertEngine(private val context: Context, private val sendAck: (serverLocalId: String, alertId: String) -> Unit) {

    private val nm = context.getSystemService(NotificationManager::class.java)
    private val main = Handler(Looper.getMainLooper())
    private val repeating = mutableMapOf<String, Runnable>()

    init {
        createChannels(context)
    }

    // ------------------------------------------------------------------ incoming

    fun onAlert(server: ServerConfig, m: JSONObject) {
        val item = AlertItem(
            id = m.getString("id"),
            serverLocalId = server.id,
            serverName = m.optString("server", server.name),
            blockUuid = m.optString("uuid"),
            blockName = m.optString("name", "Notifier"),
            tier = m.optInt("tier", 2).coerceIn(0, 4),
            message = m.optString("message"),
            active = m.optBoolean("active"),
            ts = m.optLong("ts", System.currentTimeMillis()),
            missed = m.optBoolean("missed"),
            test = m.optBoolean("test"),
            color = m.optInt("color"),
            showState = m.optBoolean("showState", true),
        )
        if (Store.alerts.value.any { it.id == item.id && it.acked }) return
        Store.addAlert(item)
        present(item)
    }

    fun onRemoteAck(id: String, by: String) {
        Store.markAcked(id, by)
        dismissLocal(id)
    }

    /** A world on the LAN linked this phone in a notifier: offer one-tap Accept. */
    fun onInvite(inv: Invite) {
        if (!hasNotificationPermission()) return
        val n = base(CH_INVITE, "${inv.name} wants to link this phone", "A Redstone Notifier there was linked to you. Tap Accept to add the world.")
            .addAction(0, "Accept", inviteIntent(AlertActionReceiver.ACCEPT_INVITE, inv))
            .addAction(0, "Ignore", inviteIntent(AlertActionReceiver.IGNORE_INVITE, inv))
            .build()
        nm.notify(inviteNotificationId(inv.key), n)
    }

    fun clearInvite(key: String) = nm.cancel(inviteNotificationId(key))

    private fun inviteIntent(action: String, inv: Invite): PendingIntent =
        PendingIntent.getBroadcast(
            context, (action + inv.key).hashCode(),
            Intent(context, AlertActionReceiver::class.java).setAction(action)
                .putExtra("name", inv.name).putExtra("host", inv.host).putExtra("port", inv.port).putExtra("serverId", inv.serverId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    fun onReconnected(server: ServerConfig) {
        nm.cancel(watchdogNotificationId(server.id))
    }

    fun onServerStopped(server: ServerConfig) {
        if (!hasNotificationPermission()) return
        val n = base(CH_T1, "${server.name.ifEmpty { server.host }} stopped", "The world closed cleanly. Notify Me will reconnect when it is back.")
            .build()
        nm.notify(watchdogNotificationId(server.id), n)
    }

    /** The phone lost a world without a clean "bye" for too long (crash, network, PC asleep). */
    fun onWatchdog(server: ServerConfig, minutes: Int) {
        val tier = Store.settings.value.watchdogTier
        val item = AlertItem(
            id = "watchdog-${server.id}-${System.currentTimeMillis()}",
            serverLocalId = server.id,
            serverName = server.name,
            blockUuid = "",
            blockName = "Connection lost",
            tier = tier,
            message = "No contact with ${server.name.ifEmpty { server.host }} for $minutes min. Game crashed, PC asleep or network down?",
            active = true,
            ts = System.currentTimeMillis(),
            missed = false,
            test = false,
        )
        Store.addAlert(item)
        present(item, local = true)
    }

    /** Local preview from Settings, never sent to the server. */
    fun preview(tier: Int) {
        val item = AlertItem(
            "preview-${System.currentTimeMillis()}", "", "Preview", "", "Preview: ${TIER_NAMES[tier]}", tier,
            TIER_HINTS[tier], true, System.currentTimeMillis(), missed = false, test = true,
        )
        Store.addAlert(item)
        present(item, local = true)
    }

    // ------------------------------------------------------------------ acknowledge

    /** User acknowledged on this phone. */
    fun acknowledge(id: String) {
        val item = Store.alerts.value.firstOrNull { it.id == id }
        Store.markAcked(id, Store.identity.callsign)
        dismissLocal(id)
        if (item != null && item.serverLocalId.isNotEmpty() && !id.startsWith("watchdog-") && !id.startsWith("preview-")) {
            sendAck(item.serverLocalId, id)
        }
    }

    fun acknowledgeAll() {
        Store.alerts.value.filter { !it.acked && it.tier >= 3 }.forEach { acknowledge(it.id) }
        AlarmController.stopAll()
    }

    fun snooze(id: String, minutes: Int = 5) {
        val item = Store.alerts.value.firstOrNull { it.id == id } ?: return
        dismissLocal(id)
        main.postDelayed({
            val cur = Store.alerts.value.firstOrNull { it.id == id }
            if (cur != null && !cur.acked) present(cur, local = true)
        }, minutes * 60_000L)
        Store.setError("Snoozed \"${item.blockName}\" for $minutes min")
    }

    private fun dismissLocal(id: String) {
        repeating.remove(id)?.let { main.removeCallbacks(it) }
        nm.cancel(notificationId(id))
        AlarmController.stop(id)
    }

    // ------------------------------------------------------------------ presentation

    private fun effectiveTier(item: AlertItem): Int {
        val s = Store.settings.value
        if (!s.quietEnabled || item.tier == 4 || item.tier <= 1) return item.tier
        val h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val inQuiet = if (s.quietStart <= s.quietEnd) h in s.quietStart until s.quietEnd
        else h >= s.quietStart || h < s.quietEnd
        return if (inQuiet) 1 else item.tier
    }

    private fun present(item: AlertItem, local: Boolean = false) {
        val tier = effectiveTier(item)
        if (tier == 0) return
        if (!hasNotificationPermission()) {
            Store.setError("Notifications are blocked - allow them so alerts can reach you.")
            if (tier < 4) return
        }
        val title = title(item)
        val text = text(item)
        when (tier) {
            1 -> nm.notify(notificationId(item.id), base(CH_T1, title, text).build())
            2 -> nm.notify(notificationId(item.id), base(CH_T2, title, text).build())
            3 -> {
                postUrgent(item, title, text)
                var count = 0
                val r = object : Runnable {
                    override fun run() {
                        val cur = Store.alerts.value.firstOrNull { it.id == item.id }
                        if (cur == null || cur.acked || ++count > 40) {
                            repeating.remove(item.id)
                            return
                        }
                        postUrgent(item, title, text)
                        main.postDelayed(this, 30_000L)
                    }
                }
                repeating[item.id] = r
                main.postDelayed(r, 30_000L)
            }
            4 -> {
                AlarmController.start(context, item)
                val full = PendingIntent.getActivity(
                    context, notificationId(item.id), AlarmActivity.intent(context, item.id),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
                val n = base(CH_T4, title, text)
                    .setCategory(NotificationCompat.CATEGORY_ALARM)
                    .setPriority(NotificationCompat.PRIORITY_MAX)
                    .setFullScreenIntent(full, true)
                    .setOngoing(true)
                    .addAction(0, "Acknowledge", actionIntent(AlertActionReceiver.ACK, item.id))
                    .addAction(0, "Snooze 5 min", actionIntent(AlertActionReceiver.SNOOZE, item.id))
                    .build()
                n.flags = n.flags or Notification.FLAG_INSISTENT
                nm.notify(notificationId(item.id), n)
                // If the app is open, show the alarm screen right away.
                if (AppVisibility.foreground) context.startActivity(AlarmActivity.intent(context, item.id))
            }
        }
    }

    private fun postUrgent(item: AlertItem, title: String, text: String) {
        val n = base(CH_T3, title, text)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setOnlyAlertOnce(false)
            .addAction(0, "Acknowledge", actionIntent(AlertActionReceiver.ACK, item.id))
            .addAction(0, "Snooze 5 min", actionIntent(AlertActionReceiver.SNOOZE, item.id))
            .build()
        nm.notify(notificationId(item.id), n)
    }

    private fun title(item: AlertItem): String {
        val prefix = when {
            item.test -> "[TEST] "
            item.missed -> "[missed] "
            else -> ""
        }
        return prefix + item.title()
    }

    private fun text(item: AlertItem): String =
        listOf(item.message, item.serverName).filter { it.isNotBlank() }.joinToString(" - ")

    private fun base(channel: String, title: String, text: String): NotificationCompat.Builder {
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_stat_notify)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .setWhen(System.currentTimeMillis())
            .setShowWhen(true)
    }

    private fun actionIntent(action: String, id: String): PendingIntent =
        PendingIntent.getBroadcast(
            context, (action + id).hashCode(),
            Intent(context, AlertActionReceiver::class.java).setAction(action).putExtra("id", id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun hasNotificationPermission() = Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    companion object {
        const val CH_SERVICE = "service"
        const val CH_T1 = "t1_quiet"
        const val CH_T2 = "t2_notify"
        const val CH_T3 = "t3_urgent"
        const val CH_T4 = "t4_alarm"
        const val CH_INVITE = "invites"

        fun inviteNotificationId(key: String) = 800 + (key.hashCode() and 0x3f)

        fun notificationId(alertId: String) = 1000 + (alertId.hashCode() and 0x7fffff)
        fun watchdogNotificationId(serverLocalId: String) = 900 + (serverLocalId.hashCode() and 0x3f)

        fun createChannels(context: Context) {
            val nm = context.getSystemService(NotificationManager::class.java)
            val alarmAttrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            nm.createNotificationChannel(
                NotificationChannel(CH_SERVICE, "Connection (always on)", NotificationManager.IMPORTANCE_MIN).apply {
                    description = "Keeps the link to your worlds open"
                    setShowBadge(false)
                })
            nm.createNotificationChannel(
                NotificationChannel(CH_INVITE, "World invites", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "A world on your network linked this phone"
                })
            nm.createNotificationChannel(
                NotificationChannel(CH_T1, "Tier 1 - Quiet", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Silent notifications"
                })
            nm.createNotificationChannel(
                NotificationChannel(CH_T2, "Tier 2 - Notify", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Sound and vibration"
                    enableVibration(true)
                })
            nm.createNotificationChannel(
                NotificationChannel(CH_T3, "Tier 3 - Urgent", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Repeats until acknowledged"
                    enableVibration(true)
                    vibrationPattern = longArrayOf(0, 400, 200, 400, 200, 800)
                    setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION), alarmAttrs)
                    setBypassDnd(true)
                    enableLights(true)
                })
            nm.createNotificationChannel(
                NotificationChannel(CH_T4, "Tier 4 - Alarm", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Full-screen alarm (sound is played by the alarm screen)"
                    setSound(null, null)
                    enableVibration(false)
                    setBypassDnd(true)
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                })
        }
    }
}

/** Tracks whether any activity of the app is in the foreground. */
object AppVisibility {
    @Volatile
    var foreground = false
}
