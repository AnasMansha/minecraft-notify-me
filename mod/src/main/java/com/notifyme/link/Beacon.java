package com.notifyme.link;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.Set;

import com.google.gson.JsonObject;
import com.notifyme.NotifyMe;

/** Broadcasts a small UDP packet every 2s so the phone app can find this world on the LAN. */
public class Beacon extends Thread {

    private final int port;
    private final JsonObject base = new JsonObject();
    private volatile byte[] payload;
    private volatile boolean sendNow;
    private volatile boolean running = true;
    private Set<String> invites = java.util.Collections.emptySet();

    public Beacon(int discoveryPort, String serverName, String serverId, int linkPort) {
        super("NotifyMe-Beacon");
        setDaemon(true);
        this.port = discoveryPort;
        base.addProperty("t", "notifyme");
        base.addProperty("v", LinkManager.PROTOCOL);
        base.addProperty("name", serverName);
        base.addProperty("id", serverId);
        base.addProperty("port", linkPort);
        rebuild();
    }

    private void rebuild() {
        JsonObject o = new com.google.gson.JsonParser().parse(base.toString())
            .getAsJsonObject();
        if (!invites.isEmpty()) {
            com.google.gson.JsonArray a = new com.google.gson.JsonArray();
            for (String cs : invites) a.add(new com.google.gson.JsonPrimitive(cs));
            o.add("invite", a); // phones that a notifier wants but that are not connected yet
        }
        payload = o.toString()
            .getBytes(StandardCharsets.UTF_8);
    }

    /** Server thread: callsigns to invite. Re-broadcasts right away when the set changes. */
    public void setInvites(Set<String> callsigns) {
        if (callsigns.equals(invites)) return;
        invites = new java.util.TreeSet<>(callsigns);
        rebuild();
        sendNow = true;
    }

    public void shutdown() {
        running = false;
        interrupt();
    }

    /**
     * Listens on the discovery port so a phone's probe ({"t":"notifyme-probe"}) gets an instant unicast reply,
     * and still broadcasts every 2 s for phones that only listen. Falls back to broadcast-only if the port is taken.
     */
    private static DatagramSocket openSocket(int port) throws Exception {
        try {
            DatagramSocket s = new DatagramSocket(null);
            s.setReuseAddress(true);
            s.bind(new InetSocketAddress(port));
            return s;
        } catch (Exception e) {
            NotifyMe.LOG.info("Notify Me: discovery port {} busy, broadcasting only", port);
            return new DatagramSocket();
        }
    }

    @Override
    public void run() {
        try (DatagramSocket socket = openSocket(port)) {
            socket.setBroadcast(true);
            socket.setSoTimeout(250);
            int round = 0;
            long nextBeacon = 0;
            Set<InetAddress> targets = broadcastAddresses();
            byte[] buf = new byte[512];
            while (running) {
                long now = System.currentTimeMillis();
                if (now >= nextBeacon || sendNow) {
                    sendNow = false;
                    if (++round % 30 == 0) targets = broadcastAddresses(); // network may change
                    for (InetAddress a : targets) {
                        try {
                            socket.send(new DatagramPacket(payload, payload.length, a, port));
                        } catch (Exception ignored) {}
                    }
                    nextBeacon = now + 2000;
                }
                try {
                    DatagramPacket p = new DatagramPacket(buf, buf.length);
                    socket.receive(p);
                    String msg = new String(p.getData(), 0, p.getLength(), StandardCharsets.UTF_8);
                    if (msg.contains("\"notifyme-probe\"")) {
                        socket.send(new DatagramPacket(payload, payload.length, p.getSocketAddress()));
                    }
                } catch (SocketTimeoutException ignored) {}
            }
        } catch (Exception e) {
            if (running) NotifyMe.LOG.warn("Notify Me LAN beacon stopped: {}", e.toString());
        }
    }

    private static Set<InetAddress> broadcastAddresses() {
        Set<InetAddress> out = new LinkedHashSet<>();
        try {
            out.add(InetAddress.getByName("255.255.255.255"));
            Enumeration<NetworkInterface> ifs = NetworkInterface.getNetworkInterfaces();
            while (ifs != null && ifs.hasMoreElements()) {
                NetworkInterface nif = ifs.nextElement();
                if (!nif.isUp() || nif.isLoopback()) continue;
                for (InterfaceAddress ia : nif.getInterfaceAddresses()) {
                    if (ia.getBroadcast() != null) out.add(ia.getBroadcast());
                }
            }
        } catch (Exception ignored) {}
        return out;
    }

    /** Best-effort LAN IPv4 addresses of this machine, for display in the GUI. */
    public static String localAddresses() {
        StringBuilder sb = new StringBuilder();
        try {
            Enumeration<NetworkInterface> ifs = NetworkInterface.getNetworkInterfaces();
            while (ifs != null && ifs.hasMoreElements()) {
                NetworkInterface nif = ifs.nextElement();
                if (!nif.isUp() || nif.isLoopback() || nif.isVirtual()) continue;
                Enumeration<InetAddress> addrs = nif.getInetAddresses();
                while (addrs.hasMoreElements()) {
                    InetAddress a = addrs.nextElement();
                    if (a instanceof java.net.Inet4Address && !a.isLinkLocalAddress()) {
                        if (sb.length() > 0) sb.append(", ");
                        sb.append(a.getHostAddress());
                    }
                }
            }
        } catch (Exception ignored) {}
        return sb.length() == 0 ? "?" : sb.toString();
    }
}
