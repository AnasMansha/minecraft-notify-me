package com.notifyme;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChunkCoordinates;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.world.WorldServer;

import com.notifyme.block.TileNotifier;
import com.notifyme.data.NotifierEntry;
import com.notifyme.data.NotifierRegistry;
import com.notifyme.data.NotifierRegistry.PhoneBinding;
import com.notifyme.link.Beacon;
import com.notifyme.link.LinkManager;
import com.notifyme.util.Callsign;

/** /notifyme [status|phones|list|approve <callsign>|deny <callsign>] */
public class CommandNotifyMe extends CommandBase {

    @Override
    public String getCommandName() {
        return "notifyme";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/notifyme [status|phones|list|approve <callsign>|deny <callsign>]";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }

    @Override
    public boolean canCommandSenderUseCommand(ICommandSender sender) {
        return true;
    }

    @Override
    @SuppressWarnings("rawtypes")
    public List addTabCompletionOptions(ICommandSender sender, String[] args) {
        return args.length == 1
            ? getListOfStringsMatchingLastWord(args, "status", "phones", "list", "approve", "deny", "selftest")
            : null;
    }

    private static void say(ICommandSender s, String text) {
        s.addChatMessage(new ChatComponentText(text));
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        NotifierRegistry reg = NotifyMe.registry();
        if (reg == null) return;
        String sub = args.length == 0 ? "status" : args[0].toLowerCase();
        switch (sub) {
            case "status":
                say(sender, EnumChatFormatting.GOLD + "[Notify Me] " + EnumChatFormatting.WHITE + LinkManager.status());
                say(sender, "Phone app address: " + Beacon.localAddresses() + "  port " + Config.linkPort);
                say(
                    sender,
                    "Phones online: " + LinkManager.onlineCount()
                        + ", notifiers: "
                        + reg.all()
                            .size());
                break;
            case "phones":
                if (reg.phones()
                    .isEmpty()) say(sender, "No phones have connected yet.");
                for (PhoneBinding b : reg.phones()) {
                    String st = LinkManager.phoneStatus(b.callsign);
                    say(
                        sender,
                        (st.equals("online") ? EnumChatFormatting.GREEN
                            : st.equals("pending") ? EnumChatFormatting.GOLD : EnumChatFormatting.GRAY) + b.callsign
                            + EnumChatFormatting.GRAY
                            + " - "
                            + st
                            + (b.device.isEmpty() ? "" : " - " + b.device));
                }
                break;
            case "list":
                if (reg.all()
                    .isEmpty()) say(sender, "No notifiers placed.");
                for (NotifierEntry e : reg.all()) {
                    say(
                        sender,
                        (e.active ? EnumChatFormatting.GREEN + "ON  " : EnumChatFormatting.GRAY + "OFF ")
                            + EnumChatFormatting.WHITE
                            + e.displayName()
                            + EnumChatFormatting.GRAY
                            + " dim "
                            + e.dim
                            + " @ "
                            + e.x
                            + ","
                            + e.y
                            + ","
                            + e.z
                            + " - "
                            + NotifierEntry.TIER_NAMES[e.tier]
                            + " - "
                            + e.links.size()
                            + " phone(s)"
                            + (e.loaded ? "" : " (unloaded)"));
                }
                break;
            case "approve":
            case "deny": {
                String cs = Callsign.normalize(String.join(" ", Arrays.copyOfRange(args, 1, args.length)));
                if (cs == null) {
                    say(sender, EnumChatFormatting.RED + "Not a valid callsign.");
                    return;
                }
                if (!mayManage(sender, cs, reg)) {
                    say(sender, EnumChatFormatting.RED + "Only an op or the owner of a linked notifier can do that.");
                    return;
                }
                if (sub.equals("approve")) {
                    say(
                        sender,
                        LinkManager.approve(cs) ? EnumChatFormatting.GREEN + "Approved " + cs + "."
                            : EnumChatFormatting.RED + cs + " has not tried to connect yet.");
                } else {
                    LinkManager.forget(cs);
                    say(
                        sender,
                        EnumChatFormatting.YELLOW + "Removed " + cs + ". It must be approved again to connect.");
                }
                break;
            }
            case "selftest":
                selfTest(sender, args, reg);
                break;
            default:
                say(sender, getCommandUsage(sender));
        }
    }

    /**
     * Op-only: places a notifier at world spawn linked to a callsign and toggles a redstone block next to it, so an
     * admin can check that a phone receives alerts without building anything.
     * /notifyme selftest place <callsign> | on | off | remove
     */
    private static void selfTest(ICommandSender sender, String[] args, NotifierRegistry reg) {
        MinecraftServer mc = MinecraftServer.getServer();
        if (sender instanceof EntityPlayerMP && !mc.isSinglePlayer()
            && !mc.getConfigurationManager()
                .func_152596_g(((EntityPlayerMP) sender).getGameProfile())) {
            say(sender, EnumChatFormatting.RED + "Only ops can run the self test.");
            return;
        }
        WorldServer w = mc.worldServers[0];
        ChunkCoordinates sp = w.getSpawnPoint();
        int x = sp.posX, z = sp.posZ;
        int y = Math.min(250, w.getHeightValue(x, z) + 3);
        if (selfTestPos != null) {
            x = selfTestPos[0];
            y = selfTestPos[1];
            z = selfTestPos[2];
        }
        String action = args.length > 1 ? args[1].toLowerCase() : "";
        switch (action) {
            case "place": {
                String cs = Callsign.normalize(String.join(" ", Arrays.copyOfRange(args, 2, args.length)));
                if (cs == null) {
                    say(sender, EnumChatFormatting.RED + "Usage: /notifyme selftest place <callsign>");
                    return;
                }
                w.setBlock(x, y, z, NotifyMe.notifier);
                TileEntity te = w.getTileEntity(x, y, z);
                if (!(te instanceof TileNotifier)) {
                    say(sender, EnumChatFormatting.RED + "Could not place the notifier.");
                    return;
                }
                UUID id = UUID.randomUUID();
                NotifierEntry e = reg.getOrCreate(id);
                e.name = "Self test";
                e.message = "Self test from /notifyme selftest";
                e.links.add(cs);
                e.dim = w.provider.dimensionId;
                e.x = x;
                e.y = y;
                e.z = z;
                reg.markDirty();
                ((TileNotifier) te).bind(id);
                selfTestPos = new int[] { x, y, z };
                say(sender, "Self-test notifier placed at " + x + "," + y + "," + z + " linked to " + cs);
                break;
            }
            case "on":
                w.setBlock(x + 1, y, z, Blocks.redstone_block);
                say(sender, "Redstone ON next to the self-test notifier.");
                break;
            case "off":
                w.setBlockToAir(x + 1, y, z);
                say(sender, "Redstone OFF.");
                break;
            case "remove":
                w.setBlockToAir(x + 1, y, z);
                w.setBlockToAir(x, y, z);
                selfTestPos = null;
                say(sender, "Self-test notifier removed.");
                break;
            default:
                say(sender, "/notifyme selftest place <callsign> | on | off | remove");
        }
    }

    private static int[] selfTestPos;

    private static boolean mayManage(ICommandSender sender, String callsign, NotifierRegistry reg) {
        MinecraftServer mc = MinecraftServer.getServer();
        if (!(sender instanceof EntityPlayerMP)) return true; // console
        EntityPlayerMP p = (EntityPlayerMP) sender;
        if (mc.isSinglePlayer() || mc.getConfigurationManager()
            .func_152596_g(p.getGameProfile())) return true;
        String id = p.getUniqueID()
            .toString();
        for (NotifierEntry e : reg.linkedTo(callsign)) if (id.equals(e.ownerUuid)) return true;
        return false;
    }
}
