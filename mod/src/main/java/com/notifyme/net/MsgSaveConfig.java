package com.notifyme.net;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;

import com.notifyme.NotifyMe;
import com.notifyme.block.BlockNotifier;
import com.notifyme.block.TileNotifier;
import com.notifyme.data.NotifierEntry;
import com.notifyme.link.AlertEngine;
import com.notifyme.link.LinkManager;
import com.notifyme.util.Callsign;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/** Client -> server: save the GUI's settings (and optionally fire a test alert, or just refresh). */
public class MsgSaveConfig implements IMessage {

    public static final int ACTION_SAVE = 0;
    public static final int ACTION_SAVE_AND_TEST = 1;
    public static final int ACTION_SAVE_AND_REOPEN = 2;

    public int x, y, z, action;
    public NBTTagCompound data;

    public MsgSaveConfig() {}

    public MsgSaveConfig(int x, int y, int z, int action, NBTTagCompound data) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.action = action;
        this.data = data;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        x = buf.readInt();
        y = buf.readInt();
        z = buf.readInt();
        action = buf.readByte();
        data = ByteBufUtils.readTag(buf);
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(x);
        buf.writeInt(y);
        buf.writeInt(z);
        buf.writeByte(action);
        ByteBufUtils.writeTag(buf, data);
    }

    public static class Handler implements IMessageHandler<MsgSaveConfig, IMessage> {

        @Override
        public IMessage onMessage(final MsgSaveConfig msg, MessageContext ctx) {
            final EntityPlayerMP player = ctx.getServerHandler().playerEntity;
            NotifyMe.runOnServer(() -> apply(msg, player));
            return null;
        }
    }

    private static void apply(MsgSaveConfig msg, EntityPlayerMP player) {
        if (msg.data == null || player.getDistanceSq(msg.x + 0.5, msg.y + 0.5, msg.z + 0.5) > 16 * 16) return;
        TileEntity te = player.worldObj.getTileEntity(msg.x, msg.y, msg.z);
        if (!(te instanceof TileNotifier)) return;
        NotifierEntry e = ((TileNotifier) te).entry();
        if (e == null || !BlockNotifier.canEdit(player, e)) return;

        NBTTagCompound t = msg.data;
        e.name = NotifierEntry.trim(t.getString("name"), 40);
        e.message = NotifierEntry.trim(t.getString("message"), 100);
        e.tier = NotifierEntry.clamp(t.getInteger("tier"), 0, NotifierEntry.MAX_TIER);
        e.trigger = NotifierEntry.clamp(t.getInteger("trigger"), 0, NotifierEntry.TRIGGER_NAMES.length - 1);
        e.threshold = NotifierEntry.clamp(t.getInteger("threshold"), 1, 15);
        e.cooldownSec = NotifierEntry.clamp(t.getInteger("cooldown"), 0, 86400);
        e.repeatSec = NotifierEntry.clamp(t.getInteger("repeat"), 5, 86400);
        e.alarmColor = NotifierEntry.clamp(t.getInteger("alarmColor"), 0, NotifierEntry.ALARM_COLORS - 1);
        e.showState = t.getBoolean("showState");

        List<String> before = new ArrayList<>(e.links);
        List<String> links = new ArrayList<>();
        NBTTagList l = t.getTagList("links", 8);
        for (int i = 0; i < l.tagCount() && links.size() < NotifierEntry.MAX_LINKS; i++) {
            String cs = Callsign.normalize(l.getStringTagAt(i));
            if (cs != null && !links.contains(cs)) links.add(cs);
            else if (cs == null) player.addChatMessage(
                new ChatComponentText(
                    EnumChatFormatting.RED + "[Notify Me] Ignored invalid callsign: " + l.getStringTagAt(i)));
        }
        e.links = links;
        NotifyMe.registry()
            .markDirty();

        ((TileNotifier) te).refreshSignal();
        LinkManager.onLinksChanged(e, before);
        // linking a phone that is waiting for approval approves it (the owner chose it)
        for (String cs : e.links)
            if (!before.contains(cs) && "pending".equals(LinkManager.phoneStatus(cs))) LinkManager.approve(cs);
        LinkManager.updateInvites();
        if (msg.action == ACTION_SAVE_AND_TEST) {
            AlertEngine.fireTest(e);
            int online = 0;
            for (String cs : e.links) if (LinkManager.isOnline(cs)) online++;
            player.addChatMessage(
                new ChatComponentText(
                    EnumChatFormatting.GOLD + "[Notify Me] "
                        + EnumChatFormatting.WHITE
                        + "Test alert sent to "
                        + online
                        + "/"
                        + e.links.size()
                        + " linked phone(s) online"
                        + (online < e.links.size() ? " (offline phones get it when they reconnect)." : ".")));
        }
        if (msg.action == ACTION_SAVE_AND_REOPEN) {
            NotifyMe.NET.sendTo(MsgOpenGui.of(e, msg.x, msg.y, msg.z), player);
        }
    }
}
