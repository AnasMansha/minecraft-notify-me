package com.notifyme.app

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class NotifyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Store.init(this)
        Link.init(this)
    }
}

/** Restarts the link after a reboot or app update (if any world is configured). */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Store.init(context)
        if (Store.servers.value.any { it.enabled }) {
            try {
                LinkService.start(context)
            } catch (_: Exception) {
                // Some OEMs block FGS start from boot; the app starts it when opened.
            }
        }
    }
}

/** "Acknowledge" / "Snooze" buttons on alert notifications. */
class AlertActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Store.init(context)
        Link.init(context)
        when (intent.action) {
            ACCEPT_INVITE, IGNORE_INVITE -> {
                val inv = Invite(
                    intent.getStringExtra("name") ?: "Minecraft", intent.getStringExtra("host") ?: return,
                    intent.getIntExtra("port", 25570), intent.getStringExtra("serverId") ?: "",
                )
                if (intent.action == ACCEPT_INVITE) {
                    Store.acceptInvite(inv)
                    LinkService.start(context)
                } else Store.ignoreInvite(inv.key)
                Link.engine.clearInvite(inv.key)
                return
            }
        }
        val id = intent.getStringExtra("id") ?: return
        when (intent.action) {
            ACK -> Link.engine.acknowledge(id)
            SNOOZE -> Link.engine.snooze(id)
        }
    }

    companion object {
        const val ACK = "com.notifyme.app.ACK"
        const val SNOOZE = "com.notifyme.app.SNOOZE"
        const val ACCEPT_INVITE = "com.notifyme.app.ACCEPT_INVITE"
        const val IGNORE_INVITE = "com.notifyme.app.IGNORE_INVITE"
    }
}
