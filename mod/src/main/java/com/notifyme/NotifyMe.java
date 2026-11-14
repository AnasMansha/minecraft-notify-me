package com.notifyme;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import net.minecraft.server.MinecraftServer;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.notifyme.block.BlockNotifier;
import com.notifyme.data.NotifierRegistry;
import com.notifyme.link.AlertEngine;
import com.notifyme.link.LinkManager;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.SidedProxy;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartedEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;
import cpw.mods.fml.common.event.FMLServerStoppingEvent;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper;

@Mod(modid = NotifyMe.MODID, version = Tags.VERSION, name = "Notify Me", acceptedMinecraftVersions = "[1.7.10]")
public class NotifyMe {

    public static final String MODID = "notifyme";
    public static final Logger LOG = LogManager.getLogger(MODID);

    @SidedProxy(clientSide = "com.notifyme.ClientProxy", serverSide = "com.notifyme.CommonProxy")
    public static CommonProxy proxy;

    public static SimpleNetworkWrapper NET;
    public static BlockNotifier notifier;

    private static NotifierRegistry registry;
    private static final Queue<Runnable> serverTasks = new ConcurrentLinkedQueue<>();
    private static int tickCounter;

    /** Null while no server is running. Server thread only. */
    public static NotifierRegistry registry() {
        return registry;
    }

    /** Runs a task on the server thread at the start of the next tick. Safe from any thread. */
    public static void runOnServer(Runnable r) {
        serverTasks.add(r);
    }

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        Config.synchronizeConfiguration(event.getSuggestedConfigurationFile());
        NET = NetworkRegistry.INSTANCE.newSimpleChannel(MODID);
        proxy.preInit(event);
        FMLCommonHandler.instance()
            .bus()
            .register(this);
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        proxy.init(event);
    }

    @Mod.EventHandler
    public void serverStarting(FMLServerStartingEvent event) {
        event.registerServerCommand(new CommandNotifyMe());
    }

    @Mod.EventHandler
    public void serverStarted(FMLServerStartedEvent event) {
        MinecraftServer server = MinecraftServer.getServer();
        registry = NotifierRegistry.get(server.worldServers[0]);
        // Nothing is loaded until tile entities tick again.
        for (com.notifyme.data.NotifierEntry e : registry.all()) e.loaded = false;
        serverTasks.clear();
        LinkManager.start(server);
    }

    @Mod.EventHandler
    public void serverStopping(FMLServerStoppingEvent event) {
        LinkManager.stop();
        registry = null;
        serverTasks.clear();
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.START || registry == null) return;
        Runnable r;
        int budget = 1000;
        while (budget-- > 0 && (r = serverTasks.poll()) != null) {
            try {
                r.run();
            } catch (Exception e) {
                LOG.error("Notify Me task failed", e);
            }
        }
        tickCounter++;
        if (tickCounter % 10 == 0) LinkManager.flushDirtyBlocks();
        if (tickCounter % 20 == 0) AlertEngine.tickSecond();
    }
}
