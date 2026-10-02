package com.dogpound.prideguard;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** PrideGuard — the DogPound anti-cheat. Safe Mode (watch + alert only) is on until the checks are tuned. */
@Mod(modid = "prideguard", name = "PrideGuard", version = "0.1.0", acceptableRemoteVersions = "*")
public class PrideGuard {
    public static final Logger LOG = LogManager.getLogger("PrideGuard");
    public static final long STARTED = System.currentTimeMillis();

    @Mod.EventHandler
    public void init(net.minecraftforge.fml.common.event.FMLInitializationEvent e) {
        Perm.register();                    // Forge: permission nodes can only be registered from init on
    }

    @Mod.EventHandler
    public void pre(FMLPreInitializationEvent e) {
        Guard.load(new java.io.File(e.getModConfigurationDirectory(), "prideguard.cfg"));
        Escalation.load(new java.io.File(e.getModConfigurationDirectory(), "prideguard-ladder.txt"));   // kick history + temp-bans
        Net.init();
        MinecraftForge.EVENT_BUS.register(new Guard.Ticker());
        SelfTest.register();
        MinecraftForge.EVENT_BUS.register(Guard.check("lavalock"));
        if (FMLCommonHandler.instance().getSide().isClient()) ClientProxy.register();
    }

    @Mod.EventHandler
    public void serverStart(FMLServerStartingEvent e) {
        e.registerServerCommand(new Command());
        com.dogpound.prideguard.checks.Brain.loadSaved();
    }

    static final class Command extends CommandBase {
        @Override public String getName() { return "prideguard"; }
        @Override public List<String> getAliases() { return Collections.singletonList("pg"); }
        @Override public int getRequiredPermissionLevel() { return 2; }
        @Override public String getUsage(ICommandSender s) {
            return "/pg gui | alerts | safe <on|off> | info <player> | reset <player> | exempt <player> <minutes> | mode <check> <off|watch|alert|cancel|kick> | sens <check> <1-100> | checks";
        }

        @Override
        public void execute(MinecraftServer server, ICommandSender sender, String[] a) throws CommandException {
            if (a.length == 0) throw new CommandException(getUsage(sender));
            EntityPlayerMP me = sender instanceof EntityPlayerMP ? (EntityPlayerMP) sender : null;
            switch (a[0].toLowerCase()) {
                case "gui": Net.CH.sendTo(new Net.Open(), getCommandSenderAsPlayer(sender)); return;
                case "alerts": say(sender, Net.Settings.apply(getCommandSenderAsPlayer(sender), "global.myAlerts", String.valueOf(Guard.ALERTS_OFF.contains(me.getUniqueID())))); return;
                case "safe": if (a.length < 2) break; say(sender, set(me, "global.safeMode", String.valueOf(a[1].equalsIgnoreCase("on")))); return;
                case "debug": Guard.debugCheck = a.length < 2 || a[1].equals("off") ? "" : a[1]; say(sender, "§dPrideGuard debug: §f" + (Guard.debugCheck.isEmpty() ? "off" : Guard.debugCheck + " (every decision goes to the server log)")); return;
                case "brain": {                         // /pg brain record <player> cheat|legit | stop <player> | train [epochs] | score <player> | status
                    String sub = a.length > 1 ? a[1] : "status";
                    java.util.Map<java.util.UUID, String> rec = com.dogpound.prideguard.checks.Brain.recording;
                    try {
                        if (sub.equals("record") && a.length > 3) {
                            PlayerData d = Guard.byName(a[2]);
                            if (d == null) { say(sender, "§cNot online."); return; }
                            String l = a[3].equalsIgnoreCase("cheat") ? "cheat" : "legit";
                            rec.put(d.uuid, l);
                            com.dogpound.prideguard.checks.Brain.resetHits(d.uuid);
                            say(sender, "§d✦ Brain: recording " + d.player.getName() + "'s fights as §f" + l + "§d (every 5 hits after the first 20). /pg brain stop " + d.player.getName() + " when done.");
                            return;
                        }
                        if (sub.equals("stop") && a.length > 2) { PlayerData d = Guard.byName(a[2]); if (d != null) { rec.remove(d.uuid); com.dogpound.prideguard.checks.Brain.resetHits(d.uuid); } say(sender, "§d✦ Brain: stopped recording " + a[2]); return; }
                        if (sub.equals("train")) { say(sender, com.dogpound.prideguard.checks.Brain.train(a.length > 2 ? Integer.parseInt(a[2]) : 300)); return; }
                        if (sub.equals("score") && a.length > 2) {
                            PlayerData d = Guard.byName(a[2]);
                            Double sc = d == null ? null : com.dogpound.prideguard.checks.Brain.lastScore.get(d.uuid);
                            say(sender, sc == null ? "§7No score yet (needs a trained brain and 20+ hits)." : String.format("§d✦ %s fights %.0f%% like a cheat", a[2], sc * 100));
                            return;
                        }
                        java.util.List<double[]> dat = com.dogpound.prideguard.checks.Brain.data();
                        int ch = 0; for (double[] r : dat) if (r[com.dogpound.prideguard.checks.Brain.FEATURES] >= 0.5) ch++;
                        BrainNet n = com.dogpound.prideguard.checks.Brain.net;
                        say(sender, String.format("§d✦ AI Cheat Brain: %s · training data %d cheat / %d legit windows · recording %d player(s)",
                                n == null || n.trainedOn == 0 ? "§cnot trained yet§7" : "§atrained on " + n.trainedOn + " windows§7", ch, dat.size() - ch, rec.size()));
                    } catch (Exception ex) { say(sender, "§cBrain: " + ex); }
                    return;
                }
                case "checks":
                    say(sender, "§d✦ PrideGuard checks §7(safe mode " + (Guard.safeMode ? "§aON" : "§cOFF") + "§7)");
                    say(sender, String.format("§7lag: %d slow ticks, movement paused %d of %d ticks (%.0f%%)", Guard.slowTicks, Guard.lagPausedTicks, Guard.tick, 100.0 * Guard.lagPausedTicks / Math.max(1, Guard.tick)));
                    for (Check c : Guard.CHECKS) say(sender, String.format("§f%s §8[%s] §7%s · sens %d · %d flags", c.name, c.category, c.mode.label, c.sensitivity, c.flagsTotal));
                    return;
                case "info": {
                    if (a.length < 2) break;
                    PlayerData d = Guard.byName(a[1]);
                    if (d == null) { say(sender, "§cNot online."); return; }
                    say(sender, String.format("§d✦ %s §7ping %.0f ms · trust %s · total VL %.1f", d.player.getName(), d.pingMs, d.trust(), d.totalVl()));
                    for (Check c : Guard.CHECKS) if (d.flags[c.index] > 0) say(sender, String.format("  §f%s §7VL %.1f · %d flags", c.name, d.vl[c.index], d.flags[c.index]));
                    Double bs = com.dogpound.prideguard.checks.Brain.lastScore.get(d.uuid);
                    if (bs != null) say(sender, String.format("  §dAI brain §7fights %.0f%% like a cheat", bs * 100));
                    return;
                }
                case "pardon": {                        // /pg pardon <player> — forget their kicks and lift a temp-ban
                    if (a.length < 2) break;
                    com.mojang.authlib.GameProfile gp = server.getPlayerProfileCache().getGameProfileForUsername(a[1]);
                    if (gp == null) { say(sender, "§cNo player called " + a[1] + " has ever joined"); return; }
                    Escalation.pardon(gp.getId());
                    say(sender, "§a✦ Pardoned " + gp.getName() + ": kick history cleared, temp-ban lifted");
                    return;
                }
                case "reset": if (a.length < 2) break; say(sender, set(me, "player." + a[1] + ".reset", "")); return;
                case "exempt": if (a.length < 3) break; say(sender, set(me, "player." + a[1] + ".exempt", a[2])); return;
                case "mode": if (a.length < 3) break; say(sender, set(me, "check." + a[1] + ".mode", a[2].toUpperCase())); return;
                case "sens": if (a.length < 3) break; say(sender, set(me, "check." + a[1] + ".sens", a[2])); return;
                case "set": {                           // /pg set reach.survivalReach 3.2   ·   /pg set global.kickMessage Bye {check}
                    if (a.length < 3 || !a[1].contains(".")) break;
                    int dot = a[1].indexOf('.');
                    say(sender, set(me, "opt." + a[1].substring(0, dot) + "." + a[1].substring(dot + 1), String.join(" ", Arrays.copyOfRange(a, 2, a.length))));
                    return;
                }
                case "get": {                           // /pg get reach   → every setting of a check (or "global")
                    if (a.length < 2) break;
                    for (Opt o : Opt.ALL) if (o.scope.equalsIgnoreCase(a[1])) say(sender, (o.changed() ? "§d● " : "§8○ ") + "§f" + o.scope + "." + o.key + " §7= §f" + o.raw() + " §8(" + o.label + ", default " + o.def + ")");
                    return;
                }
                default: break;
            }
            throw new CommandException(getUsage(sender));
        }

        private static String set(EntityPlayerMP me, String key, String value) {
            if (me == null) return "run it as a player";
            return Net.Settings.apply(me, key, value);
        }

        private static void say(ICommandSender s, String m) { s.sendMessage(new TextComponentString(m)); }

        @Override
        public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] a, BlockPos pos) {
            if (a.length == 1) return getListOfStringsMatchingLastWord(a, "gui", "alerts", "safe", "info", "reset", "exempt", "mode", "sens", "checks", "set", "get", "debug", "brain");
            if (a.length == 2 && a[0].equals("set")) { List<String> k = new ArrayList<>(); for (Opt o : Opt.ALL) k.add(o.scope + "." + o.key); return getListOfStringsMatchingLastWord(a, k); }
            if (a.length == 2 && a[0].equals("get")) { java.util.Set<String> k = new java.util.TreeSet<>(); for (Opt o : Opt.ALL) k.add(o.scope); return getListOfStringsMatchingLastWord(a, k); }
            if (a.length == 3 && a[0].equals("set")) { int dot = a[1].indexOf('.'); Opt o = dot < 0 ? null : Opt.get(a[1].substring(0, dot), a[1].substring(dot + 1));
                if (o != null && o.choices != null) return getListOfStringsMatchingLastWord(a, o.choices);
                if (o != null && o.type == Opt.Type.BOOL) return getListOfStringsMatchingLastWord(a, "true", "false"); }
            if (a.length == 2 && Arrays.asList("info", "reset", "exempt").contains(a[0])) return getListOfStringsMatchingLastWord(a, server.getOnlinePlayerNames());
            if (a.length == 2 && Arrays.asList("mode", "sens").contains(a[0])) { String[] ids = new String[Guard.CHECKS.size()]; for (int i = 0; i < ids.length; i++) ids[i] = Guard.CHECKS.get(i).id; return getListOfStringsMatchingLastWord(a, ids); }
            if (a.length == 3 && a[0].equals("mode")) return getListOfStringsMatchingLastWord(a, "off", "watch", "alert", "cancel", "kick");
            if (a.length == 2 && a[0].equals("safe")) return getListOfStringsMatchingLastWord(a, "on", "off");
            return Collections.emptyList();
        }
    }
}
