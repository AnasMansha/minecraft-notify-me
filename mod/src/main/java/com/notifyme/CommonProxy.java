package com.notifyme;

import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraftforge.oredict.RecipeSorter;

import com.notifyme.block.BlockNotifier;
import com.notifyme.block.BlockNotifierCompact;
import com.notifyme.block.ConvertRecipe;
import com.notifyme.block.ItemBlockNotifier;
import com.notifyme.block.TileNotifier;
import com.notifyme.net.MsgOpenGui;
import com.notifyme.net.MsgSaveConfig;

import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.registry.GameRegistry;
import cpw.mods.fml.relauncher.Side;

public class CommonProxy {

    public void preInit(FMLPreInitializationEvent event) {
        NotifyMe.notifier = new BlockNotifier();
        GameRegistry.registerBlock(NotifyMe.notifier, ItemBlockNotifier.class, "notifier");
        NotifyMe.notifierCompact = new BlockNotifierCompact();
        GameRegistry.registerBlock(NotifyMe.notifierCompact, ItemBlockNotifier.class, "notifier_compact");
        GameRegistry.registerTileEntity(TileNotifier.class, "notifyme:notifier");

        NotifyMe.NET.registerMessage(MsgOpenGui.Handler.class, MsgOpenGui.class, 0, Side.CLIENT);
        NotifyMe.NET.registerMessage(MsgSaveConfig.Handler.class, MsgSaveConfig.class, 1, Side.SERVER);
    }

    public void init(FMLInitializationEvent event) {
        GameRegistry.addShapedRecipe(
            new ItemStack(NotifyMe.notifier),
            " E ",
            "RNR",
            "ICI",
            'E',
            Items.ender_pearl,
            'R',
            Items.redstone,
            'N',
            Blocks.noteblock,
            'I',
            Items.iron_ingot,
            'C',
            Items.comparator);

        // full <-> compact, keeping the notifier's settings and links
        RecipeSorter.register(
            "notifyme:convert",
            ConvertRecipe.class,
            RecipeSorter.Category.SHAPELESS,
            "after:minecraft:shapeless");
        GameRegistry
            .addRecipe(new ConvertRecipe(new ItemStack(NotifyMe.notifier), new ItemStack(NotifyMe.notifierCompact)));
        GameRegistry
            .addRecipe(new ConvertRecipe(new ItemStack(NotifyMe.notifierCompact), new ItemStack(NotifyMe.notifier)));
    }

    /** Client only: opens the notifier GUI. */
    public void openNotifierGui(MsgOpenGui msg) {}
}
