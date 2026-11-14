package com.notifyme.block;

import net.minecraft.block.Block;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.IIcon;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Compact Redstone Notifier: a thin 10x10 px plate that sticks to any face of a block, like a button.
 * Same tile entity, GUI and alerts as the full block.
 * <p>
 * Metadata: bits 0-2 = index into {@link #FACES} (the direction the front looks), bit 3 = powered.
 * Index 0 (south) doubles as the default, so meta 0 is still a valid placed state.
 */
public class BlockNotifierCompact extends BlockNotifier {

    /** Front direction per facing index: south, north, east, west, up (on a floor), down (on a ceiling). */
    private static final int[] FACES = { 3, 2, 5, 4, 1, 0 };
    private static final float HALF = 5F / 16F; // 10 px wide
    private static final float THICK = 2F / 16F; // 2 px thin

    @SideOnly(Side.CLIENT)
    private IIcon front, frontOn, casing;

    public BlockNotifierCompact() {
        super();
        setBlockName("notifyme.notifier_compact");
        setBlockTextureName("notifyme:compact_casing");
        setLightOpacity(0);
    }

    static int faceOf(int meta) {
        return FACES[Math.min(meta & 7, FACES.length - 1)];
    }

    private static int indexOf(int side) {
        for (int i = 0; i < FACES.length; i++) if (FACES[i] == side) return i;
        return 0;
    }

    @Override
    public int metaWithState(int oldMeta, boolean active) {
        return (oldMeta & 7) | (active ? 8 : 0);
    }

    // ------------------------------------------------------------------ shape

    @Override
    public boolean isOpaqueCube() {
        return false;
    }

    @Override
    public boolean renderAsNormalBlock() {
        return false;
    }

    @Override
    public boolean isNormalCube(IBlockAccess world, int x, int y, int z) {
        return false;
    }

    @Override
    public boolean isSideSolid(IBlockAccess world, int x, int y, int z, ForgeDirection side) {
        return false;
    }

    @Override
    public AxisAlignedBB getCollisionBoundingBoxFromPool(World world, int x, int y, int z) {
        return null; // walk through it, like a button
    }

    @Override
    public void setBlockBoundsBasedOnState(IBlockAccess world, int x, int y, int z) {
        setBoundsFor(faceOf(world.getBlockMetadata(x, y, z)));
    }

    private void setBoundsFor(int face) {
        float lo = 0.5F - HALF, hi = 0.5F + HALF;
        switch (face) {
            case 0: // on a ceiling, looking down
                setBlockBounds(lo, 1F - THICK, lo, hi, 1F, hi);
                break;
            case 1: // on a floor, looking up
                setBlockBounds(lo, 0F, lo, hi, THICK, hi);
                break;
            case 2: // looking north, attached to the block south of it
                setBlockBounds(lo, lo, 1F - THICK, hi, hi, 1F);
                break;
            case 3: // looking south
                setBlockBounds(lo, lo, 0F, hi, hi, THICK);
                break;
            case 4: // looking west
                setBlockBounds(1F - THICK, lo, lo, 1F, hi, hi);
                break;
            default: // looking east
                setBlockBounds(0F, lo, lo, THICK, hi, hi);
        }
    }

    @Override
    public void setBlockBoundsForItemRender() {
        setBoundsFor(3);
    }

    // ------------------------------------------------------------------ placement

    private static boolean hasSupport(World world, int x, int y, int z, int face) {
        ForgeDirection d = ForgeDirection.getOrientation(face);
        return world.isSideSolid(x - d.offsetX, y - d.offsetY, z - d.offsetZ, d);
    }

    @Override
    public boolean canPlaceBlockOnSide(World world, int x, int y, int z, int side) {
        return hasSupport(world, x, y, z, side);
    }

    @Override
    public boolean canPlaceBlockAt(World world, int x, int y, int z) {
        for (int side = 0; side < 6; side++) if (hasSupport(world, x, y, z, side)) return true;
        return false;
    }

    @Override
    public int onBlockPlaced(World world, int x, int y, int z, int side, float hx, float hy, float hz, int meta) {
        return indexOf(side);
    }

    @Override
    public void onNeighborBlockChange(World world, int x, int y, int z, Block neighbor) {
        if (!world.isRemote && !hasSupport(world, x, y, z, faceOf(world.getBlockMetadata(x, y, z)))) {
            world.setBlockToAir(x, y, z); // breakBlock drops the item with its settings
            return;
        }
        super.onNeighborBlockChange(world, x, y, z, neighbor);
    }

    // ------------------------------------------------------------------ rendering

    @Override
    @SideOnly(Side.CLIENT)
    public void registerBlockIcons(IIconRegister reg) {
        front = reg.registerIcon("notifyme:compact_front");
        frontOn = reg.registerIcon("notifyme:compact_front_on");
        casing = reg.registerIcon("notifyme:compact_casing");
        blockIcon = casing;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIcon(int side, int meta) {
        if (side == faceOf(meta)) return (meta & 8) != 0 ? frontOn : front;
        return casing;
    }

    /** Shown as a flat item icon in inventories (like doors). */
    @Override
    @SideOnly(Side.CLIENT)
    public String getItemIconName() {
        return "notifyme:notifier_compact";
    }
}
