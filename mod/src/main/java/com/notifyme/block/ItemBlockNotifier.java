package com.notifyme.block;

import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumChatFormatting;

import com.notifyme.data.NotifierEntry;

public class ItemBlockNotifier extends ItemBlock {

    public static final String TAG = "notifier";

    public ItemBlockNotifier(Block block) {
        super(block);
    }

    @Override
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public void addInformation(ItemStack stack, EntityPlayer player, List tooltip, boolean advanced) {
        tooltip.add(EnumChatFormatting.GRAY + "Alerts your phone on redstone signal.");
        if (!stack.hasTagCompound() || !stack.getTagCompound()
            .hasKey(TAG)) return;
        NBTTagCompound t = stack.getTagCompound()
            .getCompoundTag(TAG);
        tooltip.add(EnumChatFormatting.AQUA + "Name: " + EnumChatFormatting.WHITE + t.getString("name"));
        int tier = NotifierEntry.clamp(t.getInteger("tier"), 0, NotifierEntry.MAX_TIER);
        tooltip.add(EnumChatFormatting.AQUA + "Tier: " + EnumChatFormatting.WHITE + NotifierEntry.TIER_NAMES[tier]);
        int links = t.getTagList("links", 8)
            .tagCount();
        tooltip.add(EnumChatFormatting.AQUA + "Linked phones: " + EnumChatFormatting.WHITE + links);
    }
}
