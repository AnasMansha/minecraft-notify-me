package com.notifyme;

import java.io.File;

import net.minecraftforge.common.config.Configuration;

public class Config {

    public static boolean linkEnabled = true;
    public static String bindAddress = "0.0.0.0";
    public static int linkPort = 25570;
    public static boolean discoveryEnabled = true;
    public static int discoveryPort = 25571;
    public static boolean requireApproval = true;
    public static int maxQueuedAlerts = 100;
    public static int queuedAlertTtlMinutes = 24 * 60;
    public static boolean ownerOnly = true;
    public static int minCooldownSeconds = 1;
    public static int maxAlertsPerMinutePerBlock = 12;

    public static void synchronizeConfiguration(File configFile) {
        Configuration c = new Configuration(configFile);
        String net = "link";
        linkEnabled = c.getBoolean("enabled", net, linkEnabled, "Run the phone link server (WebSocket).");
        bindAddress = c.getString("bindAddress", net, bindAddress, "Address the link server binds to.");
        linkPort = c.getInt("port", net, linkPort, 1, 65535, "TCP port phones connect to.");
        discoveryEnabled = c.getBoolean(
            "discovery",
            net,
            discoveryEnabled,
            "Broadcast a UDP beacon so phones on the LAN find this world.");
        discoveryPort = c.getInt("discoveryPort", net, discoveryPort, 1, 65535, "UDP port of the LAN beacon.");
        requireApproval = c.getBoolean(
            "requireApproval",
            net,
            requireApproval,
            "New phones must be approved in-game with /notifyme approve <callsign>.");
        maxQueuedAlerts = c.getInt("maxQueuedAlerts", net, maxQueuedAlerts, 0, 10000, "Alerts kept per offline phone.");
        queuedAlertTtlMinutes = c.getInt(
            "queuedAlertTtlMinutes",
            net,
            queuedAlertTtlMinutes,
            1,
            7 * 24 * 60,
            "How long alerts are kept for an offline phone.");

        String gen = Configuration.CATEGORY_GENERAL;
        ownerOnly = c.getBoolean(
            "ownerOnly",
            gen,
            ownerOnly,
            "Only the player who placed a notifier (or an op) can open its GUI.");
        minCooldownSeconds = c.getInt("minCooldownSeconds", gen, minCooldownSeconds, 0, 3600, "Minimum cooldown.");
        maxAlertsPerMinutePerBlock = c.getInt(
            "maxAlertsPerMinutePerBlock",
            gen,
            maxAlertsPerMinutePerBlock,
            1,
            600,
            "Hard anti-spam cap per notifier.");

        if (c.hasChanged()) c.save();
    }
}
