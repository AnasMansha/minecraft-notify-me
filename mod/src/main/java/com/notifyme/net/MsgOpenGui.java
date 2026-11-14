package com.notifyme.net;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import com.notifyme.Config;
import com.notifyme.NotifyMe;
import com.notifyme.data.NotifierEntry;
import com.notifyme.data.NotifierRegistry;
import com.notifyme.link.Beacon;
import com.notifyme.link.LinkManager;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/** Server -> client: open the notifier GUI with its current config and status. */
public class MsgOpenGui implements IMessage {

    public int x, y, z;
    public NBTTagCompound data;

    public MsgOpenGui() {}

    public static MsgOpenGui of(NotifierEntry e, int x, int y, int z) {
        MsgOpenGui m = new MsgOpenGui();
        m.x = x;
        m.y = y;
        m.z = z;
        NBTTagCompound t = e.writeConfig(new NBTTagCompound());
        t.setString("name", e.displayName());
        t.setBoolean("active", e.active);
        t.setInteger("strength", e.strength);
        t.setString("address", Beacon.localAddresses() + "  port " + Config.linkPort);
        t.setString("linkStatus", LinkManager.status());
        NBTTagList statuses = new NBTTagList();
        for (String cs : e.links) {
            NBTTagCompound c = new NBTTagCompound();
            c.setString("callsign", cs);
            c.setString("status", LinkManager.phoneStatus(cs));
            NotifierRegistry.PhoneBinding pb = NotifyMe.registry() == null ? null
                : NotifyMe.registry()
                    .phone(cs);
            if (pb != null) {
                c.setString("device", pb.device);
                c.setLong("lastSeen", LinkManager.isOnline(cs) ? System.currentTimeMillis() : pb.lastSeen);
            }
            statuses.appendTag(c);
        }
        t.setTag("statuses", statuses);
        // phones the owner can link with one click (connected / known to this world, not linked yet)
        NBTTagList available = new NBTTagList();
        for (String[] p : LinkManager.knownPhones()) {
            if (e.links.contains(p[0]) || available.tagCount() >= 6) continue;
            NBTTagCompound c = new NBTTagCompound();
            c.setString("callsign", p[0]);
            c.setString("device", p[1] == null ? "" : p[1]);
            c.setString("status", p[2]);
            available.appendTag(c);
        }
        t.setTag("available", available);
        m.data = t;
        return m;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        x = buf.readInt();
        y = buf.readInt();
        z = buf.readInt();
        data = ByteBufUtils.readTag(buf);
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(x);
        buf.writeInt(y);
        buf.writeInt(z);
        ByteBufUtils.writeTag(buf, data);
    }

    public static class Handler implements IMessageHandler<MsgOpenGui, IMessage> {

        @Override
        public IMessage onMessage(MsgOpenGui msg, MessageContext ctx) {
            NotifyMe.proxy.openNotifierGui(msg);
            return null;
        }
    }
}
