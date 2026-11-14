package com.notifyme.block;

import java.util.UUID;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;

import com.notifyme.NotifyMe;
import com.notifyme.data.NotifierEntry;
import com.notifyme.data.NotifierRegistry;
import com.notifyme.link.AlertEngine;
import com.notifyme.link.LinkManager;

/**
 * Holds only the notifier's uuid. All config and state live in {@link NotifierRegistry} so phones can see
 * notifiers even when their chunk is unloaded.
 */
public class TileNotifier extends TileEntity {

    private UUID uuid;
    private boolean attached;
    private int lastStrength = -1;

    public UUID getUuid() {
        return uuid;
    }

    public void bind(UUID id) {
        this.uuid = id;
        this.attached = false;
        markDirty();
    }

    public NotifierEntry entry() {
        NotifierRegistry reg = NotifyMe.registry();
        return reg == null || uuid == null ? null : reg.get(uuid);
    }

    @Override
    public void updateEntity() {
        if (attached || worldObj == null || worldObj.isRemote) return;
        NotifierRegistry reg = NotifyMe.registry();
        if (reg == null) return;
        if (uuid == null) uuid = UUID.randomUUID();
        NotifierEntry e = reg.getOrCreate(uuid);
        e.dim = worldObj.provider.dimensionId;
        e.x = xCoord;
        e.y = yCoord;
        e.z = zCoord;
        e.loaded = true;
        reg.markDirty();
        attached = true;
        lastStrength = -1;
        refreshSignal();
        LinkManager.markDirty(e);
    }

    /** Re-reads the redstone input; called on neighbour changes and after config edits. */
    public void refreshSignal() {
        if (worldObj == null || worldObj.isRemote) return;
        NotifierEntry e = entry();
        if (e == null) return;
        int s = worldObj.getStrongestIndirectPower(xCoord, yCoord, zCoord);
        if (s != lastStrength || (s >= e.threshold) != e.active) {
            lastStrength = s;
            AlertEngine.onSignal(e, s);
        }
        int old = worldObj.getBlockMetadata(xCoord, yCoord, zCoord);
        net.minecraft.block.Block b = worldObj.getBlock(xCoord, yCoord, zCoord);
        int meta = b instanceof BlockNotifier ? ((BlockNotifier) b).metaWithState(old, e.active) : (e.active ? 1 : 0);
        if (old != meta) worldObj.setBlockMetadataWithNotify(xCoord, yCoord, zCoord, meta, 2);
    }

    @Override
    public void onChunkUnload() {
        NotifierEntry e = entry();
        if (e != null) {
            e.loaded = false;
            LinkManager.markDirty(e);
        }
        attached = false;
    }

    @Override
    public void writeToNBT(NBTTagCompound t) {
        super.writeToNBT(t);
        if (uuid != null) t.setString("uuid", uuid.toString());
    }

    @Override
    public void readFromNBT(NBTTagCompound t) {
        super.readFromNBT(t);
        if (t.hasKey("uuid")) {
            try {
                uuid = UUID.fromString(t.getString("uuid"));
            } catch (IllegalArgumentException ignored) {}
        }
    }
}
