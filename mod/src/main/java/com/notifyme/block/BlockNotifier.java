package com.notifyme.block;

import java.util.ArrayList;
import java.util.Random;
import java.util.UUID;

import net.minecraft.block.Block;
import net.minecraft.block.BlockContainer;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IIcon;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

import com.notifyme.Config;
import com.notifyme.NotifyMe;
import com.notifyme.data.NotifierEntry;
import com.notifyme.data.NotifierRegistry;
import com.notifyme.link.LinkManager;
import com.notifyme.net.MsgOpenGui;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

public class BlockNotifier extends BlockContainer {

    @SideOnly(Side.CLIENT)
    private IIcon side, sideOn, top, topOn;

    public BlockNotifier() {
        super(Material.iron);
        setHardness(3.0F);
        setResistance(10.0F);
        setStepSound(soundTypeMetal);
        setBlockName("notifyme.notifier");
        setCreativeTab(CreativeTabs.tabRedstone);
        setBlockTextureName("notifyme:notifier_side");
    }

    @Override
    public TileEntity createNewTileEntity(World world, int meta) {
        return new TileNotifier();
    }

    // ------------------------------------------------------------------ rendering

    @Override
    @SideOnly(Side.CLIENT)
    public void registerBlockIcons(IIconRegister reg) {
        side = reg.registerIcon("notifyme:notifier_side");
        sideOn = reg.registerIcon("notifyme:notifier_side_on");
        top = reg.registerIcon("notifyme:notifier_top");
        topOn = reg.registerIcon("notifyme:notifier_top_on");
        blockIcon = side;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIcon(int s, int meta) {
        boolean on = (meta & 1) != 0;
        if (s == 0 || s == 1) return on ? topOn : top;
        return on ? sideOn : side;
    }

    /** Metadata that shows the ON/OFF state. The full block uses meta 0/1; the compact one also stores its facing. */
    public int metaWithState(int oldMeta, boolean active) {
        return active ? 1 : 0;
    }

    // ------------------------------------------------------------------ redstone

    @Override
    public boolean canConnectRedstone(IBlockAccess world, int x, int y, int z, int side) {
        return true;
    }

    @Override
    public void onNeighborBlockChange(World world, int x, int y, int z, Block neighbor) {
        if (world.isRemote) return;
        TileEntity te = world.getTileEntity(x, y, z);
        if (te instanceof TileNotifier) ((TileNotifier) te).refreshSignal();
    }

    // ------------------------------------------------------------------ placing / breaking

    @Override
    public void onBlockPlacedBy(World world, int x, int y, int z, EntityLivingBase placer, ItemStack stack) {
        if (world.isRemote) return;
        NotifierRegistry reg = NotifyMe.registry();
        TileEntity te = world.getTileEntity(x, y, z);
        if (reg == null || !(te instanceof TileNotifier)) return;
        TileNotifier tile = (TileNotifier) te;

        NBTTagCompound tag = stack.hasTagCompound() ? stack.getTagCompound()
            .getCompoundTag(ItemBlockNotifier.TAG) : null;
        UUID id = null;
        if (tag != null && tag.hasKey("uuid")) {
            try {
                id = UUID.fromString(tag.getString("uuid"));
            } catch (IllegalArgumentException ignored) {}
            // A copied stack must not steal a notifier that still exists in the world.
            if (id != null && reg.get(id) != null) id = null;
        }
        if (id == null) id = UUID.randomUUID();

        NotifierEntry e = reg.getOrCreate(id);
        if (tag != null) e.readConfig(tag);
        if (placer instanceof EntityPlayer) {
            EntityPlayer p = (EntityPlayer) placer;
            if (e.ownerUuid.isEmpty() || tag == null) {
                e.ownerUuid = p.getUniqueID()
                    .toString();
                e.ownerName = p.getCommandSenderName();
            }
        }
        e.dim = world.provider.dimensionId;
        e.x = x;
        e.y = y;
        e.z = z;
        e.active = false;
        e.strength = 0;
        reg.markDirty();
        tile.bind(id);
    }

    @Override
    public void breakBlock(World world, int x, int y, int z, Block block, int meta) {
        if (!world.isRemote) {
            TileEntity te = world.getTileEntity(x, y, z);
            NotifierRegistry reg = NotifyMe.registry();
            if (te instanceof TileNotifier && reg != null && ((TileNotifier) te).getUuid() != null) {
                NotifierEntry e = reg.remove(((TileNotifier) te).getUuid());
                ItemStack drop = new ItemStack(this);
                if (e != null) {
                    NBTTagCompound root = new NBTTagCompound();
                    root.setTag(ItemBlockNotifier.TAG, e.writeConfig(new NBTTagCompound()));
                    drop.setTagCompound(root);
                    LinkManager.broadcastRemoved(e);
                }
                dropStack(world, x, y, z, drop);
            } else {
                dropStack(world, x, y, z, new ItemStack(this));
            }
        }
        super.breakBlock(world, x, y, z, block, meta);
    }

    private static void dropStack(World world, int x, int y, int z, ItemStack stack) {
        EntityItem item = new EntityItem(world, x + 0.5, y + 0.5, z + 0.5, stack);
        item.delayBeforeCanPickup = 10;
        world.spawnEntityInWorld(item);
    }

    /** Drops are spawned in {@link #breakBlock} so the item keeps its uuid and settings. */
    @Override
    public ArrayList<ItemStack> getDrops(World world, int x, int y, int z, int metadata, int fortune) {
        return new ArrayList<>();
    }

    @Override
    public int quantityDropped(Random random) {
        return 0;
    }

    // ------------------------------------------------------------------ GUI

    @Override
    public boolean onBlockActivated(World world, int x, int y, int z, EntityPlayer player, int side, float hx, float hy,
        float hz) {
        if (world.isRemote) return true;
        TileEntity te = world.getTileEntity(x, y, z);
        NotifierRegistry reg = NotifyMe.registry();
        if (!(te instanceof TileNotifier) || reg == null || !(player instanceof EntityPlayerMP)) return true;
        TileNotifier tile = (TileNotifier) te;
        NotifierEntry e = tile.entry();
        if (e == null) return true;
        if (!canEdit(player, e)) {
            player.addChatMessage(
                new ChatComponentText(EnumChatFormatting.RED + "This notifier belongs to " + e.ownerName + "."));
            return true;
        }
        NotifyMe.NET.sendTo(MsgOpenGui.of(e, x, y, z), (EntityPlayerMP) player);
        return true;
    }

    public static boolean canEdit(EntityPlayer player, NotifierEntry e) {
        if (!Config.ownerOnly || e.ownerUuid.isEmpty()) return true;
        if (e.ownerUuid.equals(
            player.getUniqueID()
                .toString()))
            return true;
        MinecraftServer s = MinecraftServer.getServer();
        if (s != null && s.isSinglePlayer()) return true;
        return s != null && s.getConfigurationManager()
            .func_152596_g(player.getGameProfile()); // isOp
    }
}
