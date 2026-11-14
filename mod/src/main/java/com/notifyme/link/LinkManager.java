package com.notifyme.link;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.event.ClickEvent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatStyle;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.notifyme.Config;
import com.notifyme.NotifyMe;
import com.notifyme.data.NotifierEntry;
import com.notifyme.data.NotifierRegistry;
import com.notifyme.data.NotifierRegistry.PhoneBinding;
import com.notifyme.util.Callsign;

import io.netty.channel.Channel;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;

/**
 * Phone sessions, authentication, alert delivery and offline queues. Everything here runs on the server thread
 * (Netty handlers hand messages over via {@link NotifyMe#runOnServer}).
 *
 * <pre>
 * phone -> hello{callsign,key,device}      server -> challenge{nonce,serverId,server}
 * phone -> auth{sig}                        server -> welcome | pending | rejected{reason}
 * server -> snapshot{blocks[]} / block{block} / removed{uuid} / alert{...} / acked{id,by} / bye{reason}
 * phone -> ack{id} / config{uuid,...} / test{uuid} / unlink{uuid} / ping
 * </pre>
 *
 * See protocol/PROTOCOL.md.
 */
public final class LinkManager {

    public static final int PROTOCOL = 1;

    static final class Session {

        final Channel channel;
        String callsign;
        String keyB64;
        String device = "";
        byte[] nonce;
        int state; // 0 = new, 1 = challenged, 2 = pending approval, 3 = authed

        Session(Channel c) {
            channel = c;
        }

        void send(JsonObject o) {
            if (channel.isActive()) channel.writeAndFlush(new TextWebSocketFrame(o.toString()));
        }
    }

    private static final class AlertRecord {

        final String id;
        final UUID block;
        final Set<String> recipients;
        final JsonObject json;
        final long ts;
        boolean acked;

        AlertRecord(String id, UUID block, Set<String> recipients, JsonObject json) {
            this.id = id;
            this.block = block;
            this.recipients = recipients;
            this.json = json;
            this.ts = System.currentTimeMillis();
        }
    }

    private static final SecureRandom RNG = new SecureRandom();
    private static final Map<Channel, Session> sessions = new HashMap<>();
    private static final Map<String, Deque<AlertRecord>> queues = new HashMap<>();
    private static final Set<NotifierEntry> dirty = new LinkedHashSet<>();
    private static final Map<String, AlertRecord> alerts = new LinkedHashMap<String, AlertRecord>() {

        @Override
        protected boolean removeEldestEntry(Map.Entry<String, AlertRecord> eldest) {
            return size() > 500;
        }
    };

    private static LinkServer server;
    private static Beacon beacon;
    private static String serverName = "Minecraft";
    private static String status = "stopped";

    private LinkManager() {}

    // ================================================================== lifecycle

    public static void start(MinecraftServer mc) {
        serverName = mc.isDedicatedServer() ? mc.getMOTD()
            : mc.worldServers[0].getWorldInfo()
                .getWorldName();
        if (!Config.linkEnabled) {
            status = "disabled in config";
            return;
        }
        server = new LinkServer();
        try {
            server.start(Config.bindAddress, Config.linkPort);
            status = "listening on port " + Config.linkPort;
            NotifyMe.LOG.info("Notify Me link server listening on {}:{}", Config.bindAddress, Config.linkPort);
        } catch (Exception e) {
            status = "FAILED: " + e.getMessage();
            NotifyMe.LOG.error("Notify Me could not open port " + Config.linkPort, e);
            server.stop();
            server = null;
            return;
        }
        if (Config.discoveryEnabled) {
            beacon = new Beacon(
                Config.discoveryPort,
                serverName,
                NotifyMe.registry()
                    .getServerId(),
                Config.linkPort);
            beacon.start();
        }
    }

    public static void stop() {
        JsonObject bye = msg("bye");
        bye.addProperty("reason", "server_stopping");
        for (Session s : new ArrayList<>(sessions.values())) {
            s.send(bye);
            s.channel.close();
        }
        sessions.clear();
        queues.clear();
        alerts.clear();
        dirty.clear();
        if (beacon != null) beacon.shutdown();
        beacon = null;
        if (server != null) server.stop();
        server = null;
        status = "stopped";
    }

    public static String status() {
        return status;
    }

    public static String serverName() {
        return serverName;
    }

    /**
     * Callsigns linked on some notifier but not connected right now. Broadcast in the LAN beacon so those phones
     * can offer "Accept" and add this world by themselves.
     */
    public static void updateInvites() {
        NotifierRegistry reg = NotifyMe.registry();
        if (beacon == null || reg == null) return;
        java.util.TreeSet<String> wanted = new java.util.TreeSet<>();
        for (NotifierEntry e : reg.all()) for (String cs : e.links) if (!isOnline(cs)) wanted.add(cs);
        beacon.setInvites(wanted);
    }

    /**
     * Phones the GUI can offer for one-click linking: connected (incl. awaiting approval) first, then known ones.
     * Each entry: callsign, device, status.
     */
    public static List<String[]> knownPhones() {
        List<String[]> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (Session s : sessions.values()) {
            if ((s.state == 3 || s.state == 2) && s.callsign != null && seen.add(s.callsign))
                out.add(new String[] { s.callsign, s.device, s.state == 3 ? "online" : "pending" });
        }
        NotifierRegistry reg = NotifyMe.registry();
        if (reg != null) {
            List<PhoneBinding> rest = new ArrayList<>(reg.phones());
            rest.sort((a, b) -> Long.compare(b.lastSeen, a.lastSeen));
            for (PhoneBinding b : rest) if (Callsign.normalize(b.callsign) != null && seen.add(b.callsign))
                out.add(new String[] { b.callsign, b.device, b.approved ? "offline" : "pending" });
        }
        return out;
    }

    // ================================================================== incoming

    static void onClosed(Channel ch) {
        Session s = sessions.remove(ch);
        NotifierRegistry reg = NotifyMe.registry();
        if (s != null && s.state == 3 && reg != null) {
            PhoneBinding b = reg.phone(s.callsign);
            if (b != null) {
                b.lastSeen = System.currentTimeMillis();
                reg.markDirty();
            }
        }
    }

    static void onMessage(Channel ch, String text) {
        NotifierRegistry reg = NotifyMe.registry();
        if (reg == null) {
            ch.close();
            return;
        }
        Session s = sessions.get(ch);
        if (s == null) {
            s = new Session(ch);
            sessions.put(ch, s);
        }
        JsonObject m;
        String t;
        try {
            m = new JsonParser().parse(text)
                .getAsJsonObject();
            t = m.get("t")
                .getAsString();
        } catch (Exception e) {
            reject(s, "bad_message");
            return;
        }
        try {
            switch (t) {
                case "hello":
                    onHello(s, m, reg);
                    break;
                case "auth":
                    onAuth(s, m, reg);
                    break;
                case "ping":
                    s.send(msg("pong"));
                    break;
                default:
                    if (s.state != 3) {
                        reject(s, "not_authenticated");
                        return;
                    }
                    onAuthed(s, t, m, reg);
            }
        } catch (Exception e) {
            NotifyMe.LOG.warn("Notify Me: bad '{}' message from {}: {}", t, s.callsign, e.toString());
            JsonObject err = msg("error");
            err.addProperty("message", "Bad request: " + e.getMessage());
            s.send(err);
        }
    }

    private static void onHello(Session s, JsonObject m, NotifierRegistry reg) throws Exception {
        if (s.state != 0) return;
        String key = m.get("key")
            .getAsString();
        byte[] keyBytes = Base64.getDecoder()
            .decode(key);
        KeyFactory.getInstance("EC")
            .generatePublic(new X509EncodedKeySpec(keyBytes)); // validates
        String derived = Callsign.fromPublicKey(keyBytes);
        String claimed = Callsign.normalize(
            m.get("callsign")
                .getAsString());
        if (!derived.equals(claimed)) {
            reject(s, "callsign_mismatch");
            return;
        }
        PhoneBinding b = reg.phone(derived);
        if (b != null && !b.publicKey.equals(key)) {
            reject(s, "callsign_taken");
            return;
        }
        s.callsign = derived;
        s.keyB64 = key;
        s.device = m.has("device") ? NotifierEntry.trim(
            m.get("device")
                .getAsString(),
            40) : "";
        s.nonce = new byte[32];
        RNG.nextBytes(s.nonce);
        s.state = 1;
        JsonObject c = msg("challenge");
        c.addProperty(
            "nonce",
            Base64.getEncoder()
                .encodeToString(s.nonce));
        c.addProperty("serverId", reg.getServerId());
        c.addProperty("server", serverName);
        c.addProperty("v", PROTOCOL);
        s.send(c);
    }

    /** The phone signs "notifyme-auth|nonce(base64)|serverId" with SHA256withECDSA. */
    public static String authPayload(String nonceB64, String serverId) {
        return "notifyme-auth|" + nonceB64 + "|" + serverId;
    }

    private static void onAuth(Session s, JsonObject m, NotifierRegistry reg) throws Exception {
        if (s.state != 1) return;
        PublicKey pk = KeyFactory.getInstance("EC")
            .generatePublic(
                new X509EncodedKeySpec(
                    Base64.getDecoder()
                        .decode(s.keyB64)));
        Signature v = Signature.getInstance("SHA256withECDSA");
        v.initVerify(pk);
        v.update(
            authPayload(
                Base64.getEncoder()
                    .encodeToString(s.nonce),
                reg.getServerId()).getBytes(StandardCharsets.UTF_8));
        boolean ok = v.verify(
            Base64.getDecoder()
                .decode(
                    m.get("sig")
                        .getAsString()));
        if (!ok) {
            reject(s, "bad_signature");
            return;
        }
        PhoneBinding b = reg.phone(s.callsign);
        if (b == null) {
            b = reg.bind(s.callsign, s.keyB64, s.device);
            b.approved = !Config.requireApproval;
        }
        // A block owner typed / clicked this callsign into a notifier: that is the approval.
        if (!b.approved && !reg.linkedTo(s.callsign)
            .isEmpty()) b.approved = true;
        b.device = s.device;
        b.lastSeen = System.currentTimeMillis();
        reg.markDirty();
        if (!b.approved) {
            s.state = 2;
            JsonObject p = msg("pending");
            p.addProperty(
                "message",
                "Approve this phone in-game: click the chat message or run /notifyme approve " + s.callsign);
            s.send(p);
            askApproval(s.callsign, s.device, reg);
            return;
        }
        welcome(s, reg);
    }

    private static void welcome(Session s, NotifierRegistry reg) {
        s.state = 3;
        JsonObject w = msg("welcome");
        w.addProperty("serverId", reg.getServerId());
        w.addProperty("server", serverName);
        w.addProperty("callsign", s.callsign);
        w.addProperty("v", PROTOCOL);
        s.send(w);
        sendSnapshot(s, reg);
        Deque<AlertRecord> q = queues.remove(s.callsign);
        if (q != null) {
            long cutoff = System.currentTimeMillis() - Config.queuedAlertTtlMinutes * 60_000L;
            for (AlertRecord r : q) {
                if (r.acked || r.ts < cutoff) continue;
                JsonObject copy = deepCopy(r.json);
                copy.addProperty("missed", true);
                s.send(copy);
            }
        }
    }

    private static void sendSnapshot(Session s, NotifierRegistry reg) {
        JsonObject snap = msg("snapshot");
        JsonArray arr = new JsonArray();
        for (NotifierEntry e : reg.linkedTo(s.callsign)) arr.add(e.toJson());
        snap.add("blocks", arr);
        s.send(snap);
    }

    private static void onAuthed(Session s, String t, JsonObject m, NotifierRegistry reg) {
        switch (t) {
            case "ack": {
                AlertRecord r = alerts.get(
                    m.get("id")
                        .getAsString());
                if (r == null || r.acked || !r.recipients.contains(s.callsign)) return;
                r.acked = true;
                JsonObject a = msg("acked");
                a.addProperty("id", r.id);
                a.addProperty("by", s.callsign);
                for (String cs : r.recipients) {
                    sendTo(cs, a);
                    Deque<AlertRecord> q = queues.get(cs);
                    if (q != null) q.remove(r);
                }
                return;
            }
            case "config": {
                NotifierEntry e = linkedEntry(s, m, reg);
                if (e == null) return;
                e.applyJson(m);
                reg.markDirty();
                AlertEngine.reevaluate(e);
                markDirty(e);
                return;
            }
            case "test": {
                NotifierEntry e = linkedEntry(s, m, reg);
                if (e != null) AlertEngine.fireTest(e);
                return;
            }
            case "unlink": {
                NotifierEntry e = linkedEntry(s, m, reg);
                if (e == null) return;
                e.links.remove(s.callsign);
                reg.markDirty();
                JsonObject r = msg("removed");
                r.addProperty("uuid", e.uuid.toString());
                s.send(r);
                return;
            }
            case "snapshot":
                sendSnapshot(s, reg);
                return;
            default:
                JsonObject err = msg("error");
                err.addProperty("message", "Unknown message type " + t);
                s.send(err);
        }
    }

    private static NotifierEntry linkedEntry(Session s, JsonObject m, NotifierRegistry reg) {
        NotifierEntry e = reg.get(
            UUID.fromString(
                m.get("uuid")
                    .getAsString()));
        if (e == null || !e.links.contains(s.callsign)) {
            JsonObject err = msg("error");
            err.addProperty("message", "That notifier is not linked to this phone.");
            s.send(err);
            return null;
        }
        return e;
    }

    private static void reject(Session s, String reason) {
        JsonObject r = msg("rejected");
        r.addProperty("reason", reason);
        s.send(r);
        s.channel.close();
        sessions.remove(s.channel);
    }

    // ================================================================== approval

    private static void askApproval(String callsign, String device, NotifierRegistry reg) {
        MinecraftServer mc = MinecraftServer.getServer();
        if (mc == null) return;
        Set<String> owners = new HashSet<>();
        for (NotifierEntry e : reg.linkedTo(callsign)) owners.add(e.ownerUuid);
        IChatComponent line = new ChatComponentText(
            EnumChatFormatting.GOLD + "[Notify Me] "
                + EnumChatFormatting.WHITE
                + "Phone "
                + callsign
                + (device.isEmpty() ? "" : " (" + device + ")")
                + " wants to connect. ");
        IChatComponent approve = new ChatComponentText(EnumChatFormatting.GREEN + "[Approve]");
        approve.setChatStyle(
            new ChatStyle()
                .setChatClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/notifyme approve " + callsign)));
        IChatComponent deny = new ChatComponentText(EnumChatFormatting.RED + " [Deny]");
        deny.setChatStyle(
            new ChatStyle()
                .setChatClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/notifyme deny " + callsign)));
        line.appendSibling(approve);
        line.appendSibling(deny);
        for (Object o : mc.getConfigurationManager().playerEntityList) {
            EntityPlayerMP p = (EntityPlayerMP) o;
            boolean op = mc.getConfigurationManager()
                .func_152596_g(p.getGameProfile());
            if (mc.isSinglePlayer() || op
                || owners.contains(
                    p.getUniqueID()
                        .toString()))
                p.addChatMessage(line);
        }
    }

    /** Approves a phone and upgrades any of its sessions waiting for approval. */
    public static boolean approve(String callsign) {
        NotifierRegistry reg = NotifyMe.registry();
        PhoneBinding b = reg.phone(callsign);
        if (b == null) return false;
        b.approved = true;
        reg.markDirty();
        for (Session s : new ArrayList<>(sessions.values()))
            if (s.state == 2 && callsign.equals(s.callsign)) welcome(s, reg);
        return true;
    }

    /** Removes a phone's binding and disconnects it. */
    public static boolean forget(String callsign) {
        NotifierRegistry reg = NotifyMe.registry();
        boolean had = reg.forget(callsign);
        for (Session s : new ArrayList<>(sessions.values())) if (callsign.equals(s.callsign)) reject(s, "denied");
        queues.remove(callsign);
        return had;
    }

    // ================================================================== outgoing

    public static boolean isOnline(String callsign) {
        for (Session s : sessions.values()) if (s.state == 3 && callsign.equals(s.callsign)) return true;
        return false;
    }

    public static int onlineCount() {
        Set<String> cs = new HashSet<>();
        for (Session s : sessions.values()) if (s.state == 3) cs.add(s.callsign);
        return cs.size();
    }

    /** "online", "pending", "offline" (known but not connected) or "never" (never connected). */
    public static String phoneStatus(String callsign) {
        if (isOnline(callsign)) return "online";
        NotifierRegistry reg = NotifyMe.registry();
        PhoneBinding b = reg == null ? null : reg.phone(callsign);
        if (b == null) return "never";
        return b.approved ? "offline" : "pending";
    }

    private static int sendTo(String callsign, JsonObject o) {
        int n = 0;
        for (Session s : sessions.values()) {
            if (s.state == 3 && callsign.equals(s.callsign)) {
                s.send(o);
                n++;
            }
        }
        return n;
    }

    /** Queue a state update for this block; flushed every half second. */
    public static void markDirty(NotifierEntry e) {
        dirty.add(e);
    }

    public static void flushDirtyBlocks() {
        if (dirty.isEmpty()) return;
        for (NotifierEntry e : dirty) {
            JsonObject b = msg("block");
            b.add("block", e.toJson());
            for (String cs : e.links) sendTo(cs, b);
        }
        dirty.clear();
    }

    /** Called after links changed in the GUI so dropped phones remove the block and new phones get it. */
    public static void onLinksChanged(NotifierEntry e, List<String> before) {
        JsonObject removed = msg("removed");
        removed.addProperty("uuid", e.uuid.toString());
        for (String cs : before) if (!e.links.contains(cs)) sendTo(cs, removed);
        markDirty(e);
    }

    public static void broadcastRemoved(NotifierEntry e) {
        dirty.remove(e);
        JsonObject removed = msg("removed");
        removed.addProperty("uuid", e.uuid.toString());
        for (String cs : e.links) sendTo(cs, removed);
    }

    /** Delivers an alert to every linked phone; queues it for phones that are offline. */
    static void deliver(NotifierEntry e, JsonObject alert) {
        String id = alert.get("id")
            .getAsString();
        AlertRecord r = new AlertRecord(id, e.uuid, new LinkedHashSet<>(e.links), alert);
        alerts.put(id, r);
        int tier = alert.get("tier")
            .getAsInt();
        for (String cs : e.links) {
            if (sendTo(cs, alert) == 0 && tier > 0 && Config.maxQueuedAlerts > 0) {
                Deque<AlertRecord> q = queues.computeIfAbsent(cs, k -> new ArrayDeque<>());
                q.addLast(r);
                while (q.size() > Config.maxQueuedAlerts) q.removeFirst();
            }
        }
    }

    static void pruneQueues() {
        long cutoff = System.currentTimeMillis() - Config.queuedAlertTtlMinutes * 60_000L;
        for (Iterator<Deque<AlertRecord>> it = queues.values()
            .iterator(); it.hasNext();) {
            Deque<AlertRecord> q = it.next();
            while (!q.isEmpty() && (q.peekFirst().ts < cutoff || q.peekFirst().acked)) q.removeFirst();
            if (q.isEmpty()) it.remove();
        }
    }

    public static List<String> sessionSummary() {
        List<String> out = new ArrayList<>();
        for (Session s : sessions.values()) {
            String st = s.state == 3 ? "online" : s.state == 2 ? "awaiting approval" : "handshake";
            out.add((s.callsign == null ? "?" : s.callsign) + " - " + st + " - " + s.channel.remoteAddress());
        }
        Collections.sort(out);
        return out;
    }

    // ================================================================== helpers

    static JsonObject msg(String type) {
        JsonObject o = new JsonObject();
        o.addProperty("t", type);
        return o;
    }

    private static JsonObject deepCopy(JsonObject o) {
        return new JsonParser().parse(o.toString())
            .getAsJsonObject();
    }
}
