package com.notifyme.util;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.world.World;
import net.minecraftforge.common.DimensionManager;

/** Human-readable dimension names ("Overworld", "The Nether", modded planets...), cached. Server thread. */
public final class DimNames {

    private static final Map<Integer, String> cache = new HashMap<>();

    private DimNames() {}

    public static String name(int dim) {
        String n = cache.get(dim);
        if (n != null) return n;
        try {
            World w = DimensionManager.getWorld(dim);
            if (w != null && w.provider != null) n = w.provider.getDimensionName();
            if (n == null && DimensionManager.isDimensionRegistered(dim)) n = DimensionManager.createProviderFor(dim)
                .getDimensionName();
        } catch (Throwable ignored) {}
        if (n == null || n.isEmpty()) n = "Dim " + dim;
        cache.put(dim, n);
        return n;
    }
}
