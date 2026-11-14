package com.notifyme.block;

import java.util.Collections;

import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.ShapelessRecipes;

/**
 * 1 notifier -> 1 notifier of the other kind (full <-> compact), keeping its uuid, name, settings and links.
 * Extends ShapelessRecipes so NEI shows it like any shapeless recipe.
 */
public class ConvertRecipe extends ShapelessRecipes {

    public ConvertRecipe(ItemStack from, ItemStack to) {
        super(to, Collections.singletonList(from));
    }

    @Override
    public ItemStack getCraftingResult(InventoryCrafting inv) {
        ItemStack out = getRecipeOutput().copy();
        for (int i = 0; i < inv.getSizeInventory(); i++) {
            ItemStack in = inv.getStackInSlot(i);
            if (in != null && in.hasTagCompound()) out.setTagCompound(
                (net.minecraft.nbt.NBTTagCompound) in.getTagCompound()
                    .copy());
        }
        return out;
    }
}
