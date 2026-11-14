package com.notifyme.link;

import java.util.Iterator;
import java.util.UUID;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraftforge.common.DimensionManager;

import com.google.gson.JsonObject;
import com.notifyme.Config;
import com.notifyme.NotifyMe;
import com.notifyme.block.TileNotifier;
import com.notifyme.data.NotifierEntry;
import com.notifyme.data.NotifierRegistry;

/** Turns redstone changes into alerts, applying trigger mode, threshold, cooldown and the anti-spam cap. */
public final class AlertEngine {

    private AlertEngine() {}

    /** New redstone strength measured for a loaded notifier. */
    public static void onSignal(NotifierEntry e, int strength) {
        boolean wasActive = e.active;
        boolean nowActive = strength >= e.threshold;
        boolean strengthChanged = e.strength != strength;
        e.strength = strength;
        if (wasActive != nowActive) {
            e.active = nowActive;
            e.lastChange = System.currentTimeMillis();
            boolean fire;
            switch (e.trigger) {
                case NotifierEntry.TRIGGER_FALLING:
                    fire = !nowActive;
                    break;
                case NotifierEntry.TRIGGER_BOTH:
                    fire = true;
                    break;
                case NotifierEntry.TRIGGER_WHILE_ON:
                    fire = nowActive;
                    e.lastRepeatMs = System.currentTimeMillis();
                    break;
                default:
                    fire = nowActive;
            }
            if (fire) tryAlert(e, false, false);
        }
        if (wasActive != nowActive || strengthChanged) {
            LinkManager.markDirty(e);
            NotifierRegistry reg = NotifyMe.registry();
            if (reg != null) reg.markDirty();
        }
    }

    /** Re-checks the block after its config changed (e.g. a new threshold). */
    public static void reevaluate(NotifierEntry e) {
        if (!e.loaded) return;
        World w = DimensionManager.getWorld(e.dim);
        if (w == null || !w.blockExists(e.x, e.y, e.z)) return;
        TileEntity te = w.getTileEntity(e.x, e.y, e.z);
        if (te instanceof TileNotifier) ((TileNotifier) te).refreshSignal();
    }

    public static void fireTest(NotifierEntry e) {
        tryAlert(e, false, true);
    }

    /** Runs once per second on the server thread. */
    public static void tickSecond() {
        NotifierRegistry reg = NotifyMe.registry();
        if (reg == null) return;
        long now = System.currentTimeMillis();
        for (NotifierEntry e : reg.all()) {
            if (e.trigger == NotifierEntry.TRIGGER_WHILE_ON && e.active
                && e.loaded
                && now - e.lastRepeatMs >= e.repeatSec * 1000L) {
                e.lastRepeatMs = now;
                tryAlert(e, true, false);
            }
        }
        LinkManager.pruneQueues();
        LinkManager.updateInvites();
    }

    private static void tryAlert(NotifierEntry e, boolean repeat, boolean test) {
        long now = System.currentTimeMillis();
        if (!test) {
            long cooldown = Math.max(e.cooldownSec, Config.minCooldownSeconds) * 1000L;
            if (!repeat && now - e.lastAlertMs < cooldown) return;
            for (Iterator<Long> it = e.recentAlerts.iterator(); it.hasNext();) {
                if (now - it.next() > 60_000L) it.remove();
            }
            if (e.recentAlerts.size() >= Config.maxAlertsPerMinutePerBlock) return;
            e.recentAlerts.addLast(now);
            e.lastAlertMs = now;
        }
        JsonObject a = LinkManager.msg("alert");
        a.addProperty(
            "id",
            UUID.randomUUID()
                .toString()
                .substring(0, 12));
        a.addProperty("uuid", e.uuid.toString());
        a.addProperty("name", e.displayName());
        a.addProperty("tier", e.tier);
        a.addProperty("message", test ? "Test alert" : e.message);
        a.addProperty("active", e.active);
        a.addProperty("strength", e.strength);
        a.addProperty("ts", now);
        a.addProperty("server", LinkManager.serverName());
        a.addProperty("repeat", repeat);
        a.addProperty("test", test);
        a.addProperty("color", e.alarmColor);
        a.addProperty("showState", e.showState);
        LinkManager.deliver(e, a);
    }
}
