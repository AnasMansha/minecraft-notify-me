package com.notifyme.data;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.UUID;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/** Everything the mod knows about one notifier block. The registry copy is the source of truth. */
public class NotifierEntry {

    public static final int TRIGGER_RISING = 0;
    public static final int TRIGGER_FALLING = 1;
    public static final int TRIGGER_BOTH = 2;
    public static final int TRIGGER_WHILE_ON = 3;
    public static final String[] TRIGGER_NAMES = { "Turns ON", "Turns OFF", "ON and OFF", "While ON (repeat)" };
    public static final String[] TRIGGER_IDS = { "rising", "falling", "both", "while_on" };

    public static final String[] TIER_NAMES = { "Status only", "Quiet", "Notify", "Urgent", "Alarm" };
    public static final int MAX_TIER = 4;
    public static final int MAX_LINKS = 8;
    /** Must match ALARM_COLORS in the app (Redstone, Lava, Gold, Emerald, Diamond, Ender). */
    public static final String[] ALARM_COLOR_NAMES = { "Redstone", "Lava", "Gold", "Emerald", "Diamond", "Ender" };
    public static final int[] ALARM_COLOR_RGB = { 0xFFFF2D1A, 0xFFFF8A00, 0xFFFFC233, 0xFF17DD62, 0xFF1EE3FF,
        0xFFA23BFF };
    public static final int ALARM_COLORS = ALARM_COLOR_NAMES.length;

    public UUID uuid;
    public int dim, x, y, z;

    // ---- config (travels with the item when broken) ----
    public String name = "";
    public String message = "";
    public int tier = 2;
    public int trigger = TRIGGER_RISING;
    public int threshold = 1;
    public int cooldownSec = 5;
    public int repeatSec = 60;
    /** Alarm screen colour on the phone (index into the app's palette, 0 = redstone red). */
    public int alarmColor = 0;
    /** Phone shows "Name is ON" / "Name is OFF" instead of just the name. */
    public boolean showState = true;
    public List<String> links = new ArrayList<>();
    public String ownerUuid = "";
    public String ownerName = "";

    // ---- live state ----
    public boolean active;
    public int strength;
    public long lastChange;
    public transient boolean loaded;
    public transient long lastAlertMs;
    public transient long lastRepeatMs;
    public transient final Deque<Long> recentAlerts = new ArrayDeque<>();

    public NotifierEntry(UUID uuid) {
        this.uuid = uuid;
        this.name = defaultName(uuid);
    }

    public static String defaultName(UUID uuid) {
        return "Notifier " + uuid.toString()
            .substring(0, 4)
            .toUpperCase();
    }

    public String displayName() {
        return name == null || name.isEmpty() ? defaultName(uuid) : name;
    }

    // ------------------------------------------------------------------ NBT

    /** Config only - used for the dropped item and for client GUI sync. */
    public NBTTagCompound writeConfig(NBTTagCompound t) {
        t.setString("uuid", uuid.toString());
        t.setString("name", name);
        t.setString("message", message);
        t.setInteger("tier", tier);
        t.setInteger("trigger", trigger);
        t.setInteger("threshold", threshold);
        t.setInteger("cooldown", cooldownSec);
        t.setInteger("repeat", repeatSec);
        t.setInteger("alarmColor", alarmColor);
        t.setBoolean("showState", showState);
        NBTTagList l = new NBTTagList();
        for (String s : links) l.appendTag(new NBTTagString(s));
        t.setTag("links", l);
        t.setString("ownerUuid", ownerUuid);
        t.setString("ownerName", ownerName);
        return t;
    }

    public void readConfig(NBTTagCompound t) {
        if (t.hasKey("name")) name = t.getString("name");
        if (t.hasKey("message")) message = t.getString("message");
        if (t.hasKey("tier")) tier = clamp(t.getInteger("tier"), 0, MAX_TIER);
        if (t.hasKey("trigger")) trigger = clamp(t.getInteger("trigger"), 0, TRIGGER_NAMES.length - 1);
        if (t.hasKey("threshold")) threshold = clamp(t.getInteger("threshold"), 1, 15);
        if (t.hasKey("cooldown")) cooldownSec = clamp(t.getInteger("cooldown"), 0, 86400);
        if (t.hasKey("repeat")) repeatSec = clamp(t.getInteger("repeat"), 5, 86400);
        if (t.hasKey("alarmColor")) alarmColor = clamp(t.getInteger("alarmColor"), 0, ALARM_COLORS - 1);
        if (t.hasKey("showState")) showState = t.getBoolean("showState");
        if (t.hasKey("links")) {
            links = new ArrayList<>();
            NBTTagList l = t.getTagList("links", 8);
            for (int i = 0; i < l.tagCount() && links.size() < MAX_LINKS; i++) links.add(l.getStringTagAt(i));
        }
        if (t.hasKey("ownerUuid")) ownerUuid = t.getString("ownerUuid");
        if (t.hasKey("ownerName")) ownerName = t.getString("ownerName");
    }

    public NBTTagCompound writeFull(NBTTagCompound t) {
        writeConfig(t);
        t.setInteger("dim", dim);
        t.setInteger("x", x);
        t.setInteger("y", y);
        t.setInteger("z", z);
        t.setBoolean("active", active);
        t.setInteger("strength", strength);
        t.setLong("lastChange", lastChange);
        return t;
    }

    public static NotifierEntry readFull(NBTTagCompound t) {
        NotifierEntry e = new NotifierEntry(UUID.fromString(t.getString("uuid")));
        e.readConfig(t);
        e.dim = t.getInteger("dim");
        e.x = t.getInteger("x");
        e.y = t.getInteger("y");
        e.z = t.getInteger("z");
        e.active = t.getBoolean("active");
        e.strength = t.getInteger("strength");
        e.lastChange = t.getLong("lastChange");
        return e;
    }

    // ------------------------------------------------------------------ JSON

    public JsonObject toJson() {
        JsonObject o = new JsonObject();
        o.addProperty("uuid", uuid.toString());
        o.addProperty("name", displayName());
        o.addProperty("message", message);
        o.addProperty("tier", tier);
        o.addProperty("trigger", TRIGGER_IDS[trigger]);
        o.addProperty("threshold", threshold);
        o.addProperty("cooldown", cooldownSec);
        o.addProperty("repeat", repeatSec);
        o.addProperty("alarmColor", alarmColor);
        o.addProperty("showState", showState);
        o.addProperty("active", active);
        o.addProperty("strength", strength);
        o.addProperty("lastChange", lastChange);
        o.addProperty("loaded", loaded);
        o.addProperty("dim", dim);
        o.addProperty("dimName", com.notifyme.util.DimNames.name(dim));
        o.addProperty("x", x);
        o.addProperty("y", y);
        o.addProperty("z", z);
        o.addProperty("owner", ownerName);
        JsonArray l = new JsonArray();
        for (String s : links) l.add(new com.google.gson.JsonPrimitive(s));
        o.add("links", l);
        return o;
    }

    /** Applies an edit coming from a phone. Unknown / missing fields are left alone. */
    public void applyJson(JsonObject o) {
        if (o.has("name")) name = trim(
            o.get("name")
                .getAsString(),
            40);
        if (o.has("message")) message = trim(
            o.get("message")
                .getAsString(),
            100);
        if (o.has("tier")) tier = clamp(
            o.get("tier")
                .getAsInt(),
            0,
            MAX_TIER);
        if (o.has("trigger")) {
            String id = o.get("trigger")
                .getAsString();
            for (int i = 0; i < TRIGGER_IDS.length; i++) if (TRIGGER_IDS[i].equals(id)) trigger = i;
        }
        if (o.has("threshold")) threshold = clamp(
            o.get("threshold")
                .getAsInt(),
            1,
            15);
        if (o.has("cooldown")) cooldownSec = clamp(
            o.get("cooldown")
                .getAsInt(),
            0,
            86400);
        if (o.has("repeat")) repeatSec = clamp(
            o.get("repeat")
                .getAsInt(),
            5,
            86400);
        if (o.has("alarmColor")) alarmColor = clamp(
            o.get("alarmColor")
                .getAsInt(),
            0,
            ALARM_COLORS - 1);
        if (o.has("showState")) showState = o.get("showState")
            .getAsBoolean();
    }

    public static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    public static String trim(String s, int max) {
        if (s == null) return "";
        s = s.trim();
        return s.length() > max ? s.substring(0, max) : s;
    }
}
