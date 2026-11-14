package com.notifyme.data;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;
import net.minecraft.world.WorldSavedData;

/**
 * Global (all dimensions) save data: every notifier and every known phone.
 * Lives in data/notifyme.dat of the world save. Only touched on the server thread.
 */
public class NotifierRegistry extends WorldSavedData {

    public static final String KEY = "notifyme";

    public static class PhoneBinding {

        public String callsign;
        public String publicKey; // base64 X.509
        public boolean approved;
        public String device = "";
        public long lastSeen;
    }

    private final Map<UUID, NotifierEntry> entries = new LinkedHashMap<>();
    private final Map<String, PhoneBinding> phones = new LinkedHashMap<>();
    private String serverId = UUID.randomUUID()
        .toString();

    public NotifierRegistry(String name) {
        super(name);
    }

    public static NotifierRegistry get(World world) {
        NotifierRegistry r = (NotifierRegistry) world.mapStorage.loadData(NotifierRegistry.class, KEY);
        if (r == null) {
            r = new NotifierRegistry(KEY);
            world.mapStorage.setData(KEY, r);
            r.markDirty();
        }
        return r;
    }

    public String getServerId() {
        return serverId;
    }

    public NotifierEntry get(UUID id) {
        return entries.get(id);
    }

    public NotifierEntry getOrCreate(UUID id) {
        NotifierEntry e = entries.get(id);
        if (e == null) {
            e = new NotifierEntry(id);
            entries.put(id, e);
            markDirty();
        }
        return e;
    }

    public NotifierEntry remove(UUID id) {
        NotifierEntry e = entries.remove(id);
        if (e != null) markDirty();
        return e;
    }

    public Collection<NotifierEntry> all() {
        return entries.values();
    }

    public List<NotifierEntry> linkedTo(String callsign) {
        List<NotifierEntry> out = new ArrayList<>();
        for (NotifierEntry e : entries.values()) if (e.links.contains(callsign)) out.add(e);
        return out;
    }

    public PhoneBinding phone(String callsign) {
        return phones.get(callsign);
    }

    public Collection<PhoneBinding> phones() {
        return phones.values();
    }

    public PhoneBinding bind(String callsign, String key, String device) {
        PhoneBinding b = new PhoneBinding();
        b.callsign = callsign;
        b.publicKey = key;
        b.device = device == null ? "" : device;
        b.lastSeen = System.currentTimeMillis();
        phones.put(callsign, b);
        markDirty();
        return b;
    }

    public boolean forget(String callsign) {
        boolean r = phones.remove(callsign) != null;
        if (r) markDirty();
        return r;
    }

    @Override
    public void readFromNBT(NBTTagCompound t) {
        entries.clear();
        phones.clear();
        if (t.hasKey("serverId")) serverId = t.getString("serverId");
        NBTTagList l = t.getTagList("notifiers", 10);
        for (int i = 0; i < l.tagCount(); i++) {
            try {
                NotifierEntry e = NotifierEntry.readFull(l.getCompoundTagAt(i));
                entries.put(e.uuid, e);
            } catch (Exception ignored) {}
        }
        NBTTagList p = t.getTagList("phones", 10);
        for (int i = 0; i < p.tagCount(); i++) {
            NBTTagCompound c = p.getCompoundTagAt(i);
            PhoneBinding b = new PhoneBinding();
            b.callsign = c.getString("callsign");
            b.publicKey = c.getString("key");
            b.approved = c.getBoolean("approved");
            b.device = c.getString("device");
            b.lastSeen = c.getLong("lastSeen");
            phones.put(b.callsign, b);
        }
    }

    @Override
    public void writeToNBT(NBTTagCompound t) {
        t.setString("serverId", serverId);
        NBTTagList l = new NBTTagList();
        for (NotifierEntry e : entries.values()) l.appendTag(e.writeFull(new NBTTagCompound()));
        t.setTag("notifiers", l);
        NBTTagList p = new NBTTagList();
        for (PhoneBinding b : phones.values()) {
            NBTTagCompound c = new NBTTagCompound();
            c.setString("callsign", b.callsign);
            c.setString("key", b.publicKey);
            c.setBoolean("approved", b.approved);
            c.setString("device", b.device);
            c.setLong("lastSeen", b.lastSeen);
            p.appendTag(c);
        }
        t.setTag("phones", p);
    }
}
