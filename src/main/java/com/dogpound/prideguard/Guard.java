package com.dogpound.prideguard;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;

import com.dogpound.prideguard.checks.*;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.CPacketConfirmTransaction;
import net.minecraft.network.play.server.SPacketConfirmTransaction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.text.Style;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.event.ClickEvent;
import net.minecraft.util.text.event.HoverEvent;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/** The hub: packets in, checks run, flags out (memory feed, chat alerts, MySQL, PridePrism). */
public final class Guard {
    private Guard() {}

    public static final List<Check> CHECKS = new ArrayList<>(Arrays.asList(
        new Reach(), new HitThroughWalls(), new FastBreak(), new Nuker(), new FarInteract(),
        new com.dogpound.prideguard.checks.Timer(), new GroundSpoof(), new BadPackets(), new PacketFlood(),
        new KillAura(), new NoSwingBreak(), new AirPlace(), new Exploits(), new ClientMods(),
        new Speed(), new Fly(), new Step(), new Jesus(), new AutoClicker(), new LavaLock(), new Aim(), new Scaffold(), new com.dogpound.prideguard.checks.Brain()));
    static { for (int i = 0; i < CHECKS.size(); i++) CHECKS.get(i).index = i; }

    public static final List<String> CATEGORIES = Arrays.asList("Combat", "World", "Movement", "Protocol");

    // global settings (every one is an Opt: config file, menu, /pg — see declareGlobals)
    public static boolean safeMode = true;     // never block/kick anything, whatever a check's mode says
    public static boolean alertOps = true;
    public static boolean logDb = true;
    public static int alertCooldownMs = 2000;
    public static boolean alertConsole = true, alertShowPing = true, alertShowHeld = true, alertSound, alertClickTp = true, auditToChat = true;
    public static double alertMinVl;
    public static String kickMessage = "You were kicked for {check}.\\nIf this was a mistake, tell an admin — it's logged.";
    public static int feedMax = 800, historyTicks = 30, historyRadius = 10, lagTickMs = 100, lagGraceTicks = 40, pingCap = 200, menuRefreshSec = 2;
    public static double trustGreenHours = 20, trustYellowHours = 3, trustOrangeHours = 0.5;
    public static boolean opsAreTrusted = true, kickBroadcast, perfTiming = true;
    public static double tpsNow = 20;                           // refreshed once a second
    public static final Deque<String[]> AUDIT = new ArrayDeque<>();   // {time, who, what}

    static void declareGlobals() {
        String g = "global";
        Opt.bool(g, "safeMode", "Enforcement", "Safe Mode", safeMode, "ON = checks never block or kick, whatever their mode (watch + alert only). Turn off once the checks are tuned for the pack.").onChange(() -> safeMode = Opt.get(g, "safeMode").on());
        Opt.bool(g, "kickBroadcast", "Enforcement", "Announce kicks", kickBroadcast, "tell everyone in chat when PrideGuard kicks someone").onChange(() -> kickBroadcast = Opt.get(g, "kickBroadcast").on());
        Opt.text(g, "kickMessage", "Enforcement", "Kick message", kickMessage, "what a kicked player sees. {check} = the check's name, {vl} = their violation level. \\n = new line").onChange(() -> kickMessage = Opt.get(g, "kickMessage").raw());
        Opt.bool(g, "alertOps", "Alerts", "Alert staff", alertOps, "ops get a chat message when a check set to Alert or higher fires").onChange(() -> alertOps = Opt.get(g, "alertOps").on());
        Opt.integer(g, "alertCooldownMs", "Alerts", "Alert cooldown (ms)", alertCooldownMs, 0, 60000, 250, "at most one chat alert per player per check this often").onChange(() -> alertCooldownMs = Opt.get(g, "alertCooldownMs").i());
        Opt.num(g, "alertMinVl", "Alerts", "Alert from VL", alertMinVl, 0, 1000, 1, "no chat alert until the player's violation level for that check reaches this").onChange(() -> alertMinVl = Opt.get(g, "alertMinVl").d());
        Opt.bool(g, "alertShowPing", "Alerts", "Show ping in alerts", alertShowPing, "put the player's ping in the alert line").onChange(() -> alertShowPing = Opt.get(g, "alertShowPing").on());
        Opt.bool(g, "alertShowHeld", "Alerts", "Show held item in alerts", alertShowHeld, "put what they're holding / riding in the hover text").onChange(() -> alertShowHeld = Opt.get(g, "alertShowHeld").on());
        Opt.bool(g, "alertClickTp", "Alerts", "Click alert to teleport", alertClickTp, "clicking an alert teleports you to the player").onChange(() -> alertClickTp = Opt.get(g, "alertClickTp").on());
        Opt.bool(g, "alertSound", "Alerts", "Alert sound", alertSound, "play a small ding to staff with each alert").onChange(() -> alertSound = Opt.get(g, "alertSound").on());
        Opt.bool(g, "alertConsole", "Alerts", "Alerts in server console", alertConsole, "also write every alert to the server log").onChange(() -> alertConsole = Opt.get(g, "alertConsole").on());
        Opt.bool(g, "auditToChat", "Alerts", "Tell staff about setting changes", auditToChat, "when someone changes a PrideGuard setting, other ops see it").onChange(() -> auditToChat = Opt.get(g, "auditToChat").on());
        Opt.bool(g, "logToDatabase", "Logging", "Log to MySQL", logDb, "every flag goes into PridePrism's database (pg_flags) for searching and the website").onChange(() -> logDb = Opt.get(g, "logToDatabase").on());
        Opt.integer(g, "feedMax", "Logging", "Flags kept in memory", feedMax, 100, 5000, 100, "how many recent flags the Live Feed keeps").onChange(() -> feedMax = Opt.get(g, "feedMax").i());
        Opt.integer(g, "lagTickMs", "Lag", "Slow tick (ms)", lagTickMs, 50, 1000, 10, "a server tick longer than this counts as lag; movement timing checks relax for a moment").onChange(() -> lagTickMs = Opt.get(g, "lagTickMs").i());
        Opt.integer(g, "lagGraceTicks", "Lag", "Grace after lag (ticks)", lagGraceTicks, 0, 400, 5, "how long movement timing stays relaxed after a slow tick").onChange(() -> lagGraceTicks = Opt.get(g, "lagGraceTicks").i());
        Opt.integer(g, "pingCap", "Lag", "Ping probes per player", pingCap, 20, 1000, 10, "silent ping probes allowed in flight per player before we stop sending").onChange(() -> pingCap = Opt.get(g, "pingCap").i());
        Opt.integer(g, "historyTicks", "Lag", "Reach history (ticks)", historyTicks, 5, 100, 1, "how far back entity positions are kept, for judging hits at the attacker's ping").onChange(() -> historyTicks = Opt.get(g, "historyTicks").i());
        Opt.integer(g, "historyRadius", "Performance", "History radius (blocks)", historyRadius, 4, 32, 1, "entities this close to a player get their positions remembered").onChange(() -> historyRadius = Opt.get(g, "historyRadius").i());
        Opt.bool(g, "perfTiming", "Performance", "Measure check cost", perfTiming, "time every check (shown on the Performance page). Tiny cost itself").onChange(() -> perfTiming = Opt.get(g, "perfTiming").on());
        Opt.num(g, "trustGreenHours", "Trust", "Green after (hours)", trustGreenHours, 0, 1000, 1, "play time for green trust").onChange(() -> trustGreenHours = Opt.get(g, "trustGreenHours").d());
        Opt.num(g, "trustYellowHours", "Trust", "Yellow after (hours)", trustYellowHours, 0, 1000, 0.5, "play time for yellow trust").onChange(() -> trustYellowHours = Opt.get(g, "trustYellowHours").d());
        Opt.num(g, "trustOrangeHours", "Trust", "Orange after (hours)", trustOrangeHours, 0, 1000, 0.25, "play time for orange trust (below that: red)").onChange(() -> trustOrangeHours = Opt.get(g, "trustOrangeHours").d());
        Opt.bool(g, "opsAreTrusted", "Trust", "Ops count as green", opsAreTrusted, "server operators always have green trust").onChange(() -> opsAreTrusted = Opt.get(g, "opsAreTrusted").on());
        Opt.integer(g, "menuRefreshSec", "Menu", "Menu refresh (s)", menuRefreshSec, 1, 30, 1, "how often the open menu asks for fresh numbers").onChange(() -> menuRefreshSec = Opt.get(g, "menuRefreshSec").i());
    }

    static {
        declareGlobals();
        Escalation.declare();
        for (Check c : CHECKS) c.declare();
    }

    private static final Map<UUID, PlayerData> DATA = new HashMap<>();
    public static final Deque<Flag> FEED = new ArrayDeque<>();   // newest first, for the menu
    static final int FEED_MAX = 800;
    public static final Set<UUID> ALERTS_OFF = new HashSet<>();

    public static int tick;
    private static long lastTickNanos;
    public static long lagUntilTick;
    public static long slowTicks, lagPausedTicks;
    public static String debugCheck = "";                      // /pg debug <check id>: log every decision that check makes
    public static boolean debug(Check c) { return !debugCheck.isEmpty() && debugCheck.equals(c.id); }               // shown by /pg checks: how often lag paused movement checks                            // server was slow: movement timing isn't trustworthy
    private static Configuration cfg;
    private static boolean warned;

    public static final class Flag {
        public long time = System.currentTimeMillis();
        public String player, uuid, check, category, detail, mode, held, riding, trust, sources = "";
        public double vl, tps;
        public int dim, x, y, z, ping;
    }

    public static PlayerData data(EntityPlayerMP p) {
        PlayerData d = DATA.get(p.getUniqueID());
        if (d == null) DATA.put(p.getUniqueID(), d = new PlayerData(p));
        d.player = p;
        return d;
    }

    public static Collection<PlayerData> online() { return DATA.values(); }

    public static PlayerData byName(String name) {
        for (PlayerData d : DATA.values()) if (d.player.getName().equalsIgnoreCase(name)) return d;
        return null;
    }

    public static Mode effective(Check c) {
        if (c.mode == Mode.OFF) return Mode.OFF;
        if (safeMode && !c.policy && c.mode.ordinal() > Mode.ALERT.ordinal()) return Mode.ALERT;
        return c.mode;
    }

    // ---------------- packets ----------------
    /** from the mixin: true = cancel the packet */
    public static boolean in(EntityPlayerMP p, Packet<?> packet) {
        if (p == null || p.connection == null || p instanceof net.minecraftforge.common.util.FakePlayer) return false;
        try {
            PlayerData d = data(p);
            d.packetsThisSecond++;
            if (packet instanceof net.minecraft.network.play.client.CPacketConfirmTeleport) d.tpConfirmTick = tick;
            if (packet instanceof net.minecraft.network.play.client.CPacketAnimation) d.swingsThisDig++;   // shared: No-Swing Breaking reads it
            if (packet instanceof CPacketConfirmTransaction) {
                CPacketConfirmTransaction t = (CPacketConfirmTransaction) packet;
                if (t.getWindowId() == 0 && t.getUid() < 0) { ack(d, t.getUid()); return true; } // ours: vanilla needn't see it
            }
            boolean cancel = false;
            for (Check c : CHECKS) {
                if (c.mode == Mode.OFF || !c.active(d)) continue;
                long t0 = perfTiming ? System.nanoTime() : 0;
                if (c.onPacket(d, packet)) cancel = true;
                if (perfTiming) { c.nanos += System.nanoTime() - t0; c.runs++; }
            }
            if (packet instanceof net.minecraft.network.play.client.CPacketPlayer) moved(d, (net.minecraft.network.play.client.CPacketPlayer) packet, cancel);
            return cancel;
        } catch (Throwable t) {
            if (!warned) { warned = true; PrideGuard.LOG.error("[PrideGuard] a check threw (further errors silenced)", t); }
            return false;
        }
    }

    /** remember where the client says it is, for the next movement check; a blocked move sends them back */
    private static void moved(PlayerData d, net.minecraft.network.play.client.CPacketPlayer m, boolean cancelled) {
        EntityPlayerMP p = d.player;
        if (cancelled && d.hasLast) {                    // setback: put them where they last legitimately were
            p.connection.setPlayerLocation(d.lastX, d.lastY, d.lastZ, p.rotationYaw, p.rotationPitch);
            return;
        }
        d.lastX = m.getX(p.posX); d.lastY = m.getY(p.posY); d.lastZ = m.getZ(p.posZ);
        d.lastGround = m.isOnGround();
        d.hasLast = true;
    }

    /** from the teleport mixin */
    public static void teleported(EntityPlayerMP p) {
        if (p == null || p instanceof net.minecraftforge.common.util.FakePlayer) return;
        PlayerData d = data(p);
        d.lastTeleportTick = tick;
        d.hasLast = false;
        d.airTicks = d.hoverTicks = 0;
        d.speedExcess = 0;
    }

    private static void ack(PlayerData d, short id) {
        long now = System.nanoTime();
        while (!d.pendingTx.isEmpty()) {
            long[] e = d.pendingTx.poll();
            if (e[0] == id) {
                double ms = (now - e[1]) / 1e6;
                d.pingMs = d.pingMs < 0 ? ms : d.pingMs * 0.8 + ms * 0.2;
                d.lastAck = now;
                return;
            }
        }
    }

    // ---------------- every tick ----------------
    public static final class Ticker {
        @SubscribeEvent
        public void tick(TickEvent.ServerTickEvent e) {
            if (e.phase != TickEvent.Phase.END) return;
            tick++;
            long now = System.nanoTime();
            if (lastTickNanos != 0 && now - lastTickNanos > lagTickMs * 1_000_000L) { lagUntilTick = tick + lagGraceTicks; slowTicks++; } // a slow tick
            if (tick < lagUntilTick) lagPausedTicks++;
            lastTickNanos = now;
            MinecraftServer s = FMLCommonHandler.instance().getMinecraftServerInstance();
            if (s == null) return;
            boolean second = tick % 20 == 0;
            if (second) tpsNow = tps();
            for (EntityPlayerMP p : s.getPlayerList().getPlayers()) {
                PlayerData d = data(p);
                // the latency ping
                if (d.pendingTx.size() < pingCap) {
                    short id = d.nextTx;
                    d.nextTx = (short) (id <= -32000 ? -1 : id - 1);
                    d.pendingTx.add(new long[]{id, now});
                    p.connection.sendPacket(new SPacketConfirmTransaction(0, id, false));
                }
                History.record(p);
                for (Check c : CHECKS) {
                    if (c.mode == Mode.OFF || !c.active(d)) continue;
                    long t0 = perfTiming ? System.nanoTime() : 0;
                    c.onTick(d, tick);
                    if (perfTiming) { c.nanos += System.nanoTime() - t0; c.runs++; }
                }
                d.startsThisTick = 0;
                if (second) {
                    for (Check c : CHECKS) d.vl[c.index] = Math.max(0, d.vl[c.index] - c.decayPerMinute / 60.0);
                    d.packetsThisSecond = d.flyingThisSecond = d.clicksThisSecond = d.instantThisSecond = 0;
                }
            }
            if (tick % 40 == 0) Db.flush();
        }

        /** the mod list their game reported in the Forge handshake */
        @SubscribeEvent
        public void joined(PlayerEvent.PlayerLoggedInEvent e) {
            if (!(e.player instanceof EntityPlayerMP) || e.player instanceof net.minecraftforge.common.util.FakePlayer) return;
            PlayerData d = data((EntityPlayerMP) e.player);
            d.lastTeleportTick = tick;
            String banned = Escalation.joinCheck(d.uuid);                 // punishment ladder: still temp-banned?
            if (banned != null) { ((EntityPlayerMP) e.player).connection.disconnect(new TextComponentString("§d✦ PrideGuard§r\n" + banned)); return; }
            String warn = Escalation.warning(d.uuid);
            if (warn != null) e.player.sendMessage(new TextComponentString(warn));
            try {
                net.minecraftforge.fml.common.network.handshake.NetworkDispatcher nd = net.minecraftforge.fml.common.network.handshake.NetworkDispatcher.get(((EntityPlayerMP) e.player).connection.netManager);
                if (nd != null && nd.getModList() != null) d.mods = new java.util.TreeMap<>(nd.getModList());
            } catch (Throwable ignored) {}
            Check c = check("clientmods");
            if (c != null && c.mode != Mode.OFF) ((ClientMods) c).joined(d);
        }

        @SubscribeEvent
        public void knocked(net.minecraftforge.event.entity.living.LivingKnockBackEvent e) {
            if (!(e.getEntityLiving() instanceof EntityPlayerMP)) return;
            PlayerData d = data((EntityPlayerMP) e.getEntityLiving());
            d.lastKnockTick = tick;
            d.knockSpeed = Math.max(0.4, e.getStrength() * 1.5);
        }

        @SubscribeEvent
        public void exploded(net.minecraftforge.event.world.ExplosionEvent.Detonate e) {
            for (Entity en : e.getAffectedEntities())
                if (en instanceof EntityPlayerMP) { PlayerData d = data((EntityPlayerMP) en); d.lastKnockTick = tick; d.knockSpeed = 3.0; }
        }

        @SubscribeEvent
        public void fell(net.minecraftforge.event.entity.living.LivingFallEvent e) {
            if (e.getEntityLiving() instanceof EntityPlayerMP && e.getEntityLiving().world.getBlockState(e.getEntityLiving().getPosition().down()).getBlock() == net.minecraft.init.Blocks.SLIME_BLOCK)
                data((EntityPlayerMP) e.getEntityLiving()).lastBounceTick = tick;
        }

        @SubscribeEvent
        public void left(PlayerEvent.PlayerLoggedOutEvent e) { DATA.remove(e.player.getUniqueID()); History.forget(e.player); }

        @SubscribeEvent
        public void teleported(PlayerEvent.PlayerChangedDimensionEvent e) {
            if (e.player instanceof EntityPlayerMP) data((EntityPlayerMP) e.player).lastTeleportTick = tick;
        }

        @SubscribeEvent
        public void respawned(PlayerEvent.PlayerRespawnEvent e) {
            if (e.player instanceof EntityPlayerMP) data((EntityPlayerMP) e.player).lastTeleportTick = tick;
        }
    }

    /** where entities have been, so reach is judged against what the player could actually see (their ping ago) */
    public static final class History {
        private static final Map<Integer, ArrayDeque<Object[]>> BOXES = new HashMap<>();   // entity id -> {tick, box}

        static void record(EntityPlayerMP p) {
            for (Entity e : p.world.getEntitiesWithinAABBExcludingEntity(p, p.getEntityBoundingBox().grow(historyRadius))) {
                ArrayDeque<Object[]> q = BOXES.computeIfAbsent(e.getEntityId(), k -> new ArrayDeque<>());
                if (!q.isEmpty() && (int) q.peekLast()[0] == tick) continue;
                q.add(new Object[]{tick, e.getEntityBoundingBox()});
                while (q.size() > historyTicks) q.poll();
            }
            if (tick % 200 == 0) BOXES.values().removeIf(q -> q.isEmpty() || (int) q.peekLast()[0] < tick - 100);
        }

        static void forget(net.minecraft.entity.player.EntityPlayer p) { BOXES.remove(p.getEntityId()); }

        /** the target's boxes over the last `ticks` ticks, plus now */
        public static List<AxisAlignedBB> boxes(Entity target, int ticks) {
            List<AxisAlignedBB> out = new ArrayList<>();
            out.add(target.getEntityBoundingBox());
            ArrayDeque<Object[]> q = BOXES.get(target.getEntityId());
            if (q != null) for (Object[] o : q) if ((int) o[0] >= tick - ticks) out.add((AxisAlignedBB) o[1]);
            return out;
        }
    }

    // ---------------- a flag happened ----------------
    static void record(Check c, PlayerData d, String detail, Mode m) {
        EntityPlayerMP p = d.player;
        Flag f = new Flag();
        f.player = p.getName(); f.uuid = p.getUniqueID().toString(); f.check = c.name; f.category = c.category;
        f.detail = detail; f.mode = m.label; f.vl = d.vl[c.index];
        f.dim = p.dimension; f.x = (int) Math.floor(p.posX); f.y = (int) Math.floor(p.posY); f.z = (int) Math.floor(p.posZ);
        f.ping = (int) Math.max(0, d.pingMs); f.tps = tps();
        f.held = p.getHeldItemMainhand().isEmpty() ? "-" : String.valueOf(p.getHeldItemMainhand().getItem().getRegistryName());
        f.riding = p.getRidingEntity() == null ? "-" : String.valueOf(net.minecraft.entity.EntityList.getKey(p.getRidingEntity()));
        f.trust = d.trust();
        if (!c.category.equals("Protocol")) f.sources = String.join(",", Sources.of(p));
        synchronized (FEED) { FEED.addFirst(f); while (FEED.size() > feedMax) FEED.removeLast(); }
        if (logDb) Db.queue(f);
        if (m.ordinal() >= Mode.ALERT.ordinal() && alertOps) alert(c, d, f);
    }

    private static void alert(Check c, PlayerData d, Flag f) {
        long now = System.currentTimeMillis();
        if (f.vl < Math.max(alertMinVl, c.alertMinVl)) return;
        if (now - d.lastAlert[c.index] < (c.alertCooldownMs >= 0 ? c.alertCooldownMs : alertCooldownMs)) return;
        d.lastAlert[c.index] = now;
        String line = String.format("§d✦ PrideGuard §f%s §7failed §c%s §7(%s) §8VL %.1f", f.player, c.name, f.detail, f.vl) + (alertShowPing ? " · " + f.ping + " ms" : "");
        TextComponentString msg = new TextComponentString(line);
        String hover = "§7" + c.about + (alertShowHeld ? "\n§8holding " + f.held + (f.riding.equals("-") ? "" : ", riding " + f.riding) : "") + (alertClickTp ? "\n§bclick to teleport there" : "");
        Style st = new Style().setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new TextComponentString(hover)));
        if (alertClickTp) st.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/tp " + f.player));
        msg.setStyle(st);
        if (alertConsole) PrideGuard.LOG.info(line.replaceAll("§.", ""));
        MinecraftServer s = FMLCommonHandler.instance().getMinecraftServerInstance();
        for (EntityPlayerMP op : s.getPlayerList().getPlayers())
            if (Perm.has(op, Perm.ALERTS) && !ALERTS_OFF.contains(op.getUniqueID())) {
                op.sendMessage(msg);
                if (alertSound) op.connection.sendPacket(new net.minecraft.network.play.server.SPacketSoundEffect(net.minecraft.init.SoundEvents.BLOCK_NOTE_PLING, net.minecraft.util.SoundCategory.MASTER, op.posX, op.posY, op.posZ, 0.6f, 1.6f));
            }
    }

    static void kick(PlayerData d, Check c) {
        PrideGuard.LOG.info("[PrideGuard] kicked " + d.player.getName() + " for " + c.name + " (VL " + d.vl[c.index] + ")");
        String why = kickMessage.replace("\\n", "\n").replace("{check}", "§c" + c.name + "§r").replace("{vl}", String.format("%.1f", d.vl[c.index]));
        String ban = Escalation.onKick(d, c);                     // repeat offender? (punishment ladder)
        d.player.connection.disconnect(new TextComponentString("§d✦ PrideGuard§r\n" + (ban != null ? ban : why)));
        if (kickBroadcast) FMLCommonHandler.instance().getMinecraftServerInstance().getPlayerList().sendMessage(new TextComponentString("§d✦ PrideGuard §7kicked §f" + d.player.getName() + " §7for §c" + c.name));
    }

    public static double tps() {
        MinecraftServer s = FMLCommonHandler.instance().getMinecraftServerInstance();
        if (s == null) return 20;
        double mean = 0;
        for (long t : s.tickTimeArray) mean += t;
        mean = mean / s.tickTimeArray.length / 1e6;
        return Math.min(20, 1000.0 / Math.max(1, mean));
    }

    // ---------------- settings: every Opt, one generic path ----------------
    private static String cat(Opt o) { return o.scope.equals("global") ? "general" : "checks." + o.scope; }
    private static net.minecraftforge.common.config.Property.Type ptype(Opt o) {
        switch (o.type) {
            case BOOL: return net.minecraftforge.common.config.Property.Type.BOOLEAN;
            case INT: return net.minecraftforge.common.config.Property.Type.INTEGER;
            case NUM: return net.minecraftforge.common.config.Property.Type.DOUBLE;
            default: return net.minecraftforge.common.config.Property.Type.STRING;
        }
    }

    static void load(File f) {
        cfg = new Configuration(f);
        cfg.load();
        for (Check c : CHECKS) cfg.setCategoryComment("checks." + c.id, c.name + " (" + c.category + "): " + c.about);
        for (Opt o : Opt.ALL) {
            String comment = o.label + ": " + o.about + (o.choices != null ? " [" + String.join(", ", o.choices) + "]" : o.type == Opt.Type.INT || o.type == Opt.Type.NUM ? " [" + Opt.fmt(o.min) + " .. " + Opt.fmt(o.max) + "]" : "");
            String v = cfg.get(cat(o), o.key, o.def, comment, ptype(o)).getString();
            if (o.set(v) != null) o.reset();          // junk in the file: back to the default
        }
        Sources.APPROVED.clear();
        Sources.APPROVED.addAll(java.util.Arrays.asList(cfg.getStringList("approvedSources", "movement", new String[0],
            "Modded items/vehicles approved as legit movement (from the menu's Movement Sources page). item:<id> or ride:<entity id>")));
        LavaLock.hours = cfg.getInt("newPlayerHours", "checks.lavalock", 2, 0, 1000, "hours a player must have played before they can pour lava / start fires outside land they build on");
        if (cfg.hasChanged()) cfg.save();
    }

    public static void save() {
        if (cfg == null) return;
        for (Opt o : Opt.ALL) cfg.get(cat(o), o.key, o.def, null, ptype(o)).set(o.raw());
        cfg.get("movement", "approvedSources", new String[0]).set(Sources.APPROVED.toArray(new String[0]));
        cfg.save();
    }

    /** the Audit Log page: who changed what, newest first */
    static void audit(String who, String what) {
        synchronized (AUDIT) { AUDIT.addFirst(new String[]{String.valueOf(System.currentTimeMillis()), who, what}); while (AUDIT.size() > 300) AUDIT.removeLast(); }
        if (!auditToChat) return;
        MinecraftServer s = FMLCommonHandler.instance().getMinecraftServerInstance();
        if (s == null) return;
        for (EntityPlayerMP op : s.getPlayerList().getPlayers())
            if (Perm.has(op, Perm.ALERTS) && !op.getName().equals(who) && !ALERTS_OFF.contains(op.getUniqueID()))
                op.sendMessage(new TextComponentString("§d✦ PrideGuard §8" + who + " set " + what));
    }

    public static Check check(String id) {
        for (Check c : CHECKS) if (c.id.equalsIgnoreCase(id) || c.name.equalsIgnoreCase(id)) return c;
        return null;
    }

    // ---------------- MySQL (through PridePrism, when it's installed) ----------------
    static final class Db {
        private static final ConcurrentLinkedQueue<Flag> Q = new ConcurrentLinkedQueue<>();
        private static boolean ready, broken;

        static void queue(Flag f) { if (Loader.isModLoaded("prideprism") && !broken) Q.add(f); }

        static void flush() {
            if (Q.isEmpty()) return;
            List<Flag> batch = new ArrayList<>();
            for (Flag f; batch.size() < 2000 && (f = Q.poll()) != null; ) batch.add(f);
            new Thread(() -> write(batch), "PrideGuard-db").start();
        }

        private static synchronized void write(List<Flag> batch) {
            try (Connection c = com.dogpound.prideprism.Db.connect()) {
                if (!ready) {
                    try (Statement s = c.createStatement()) {
                        s.execute("CREATE TABLE IF NOT EXISTS pg_flags (id BIGINT AUTO_INCREMENT PRIMARY KEY, t BIGINT NOT NULL, uuid CHAR(36), name VARCHAR(64),"
                            + " chk VARCHAR(32), category VARCHAR(16), vl DOUBLE, detail VARCHAR(255), mode VARCHAR(12), dim INT, x INT, y INT, z INT, ping INT, tps DOUBLE,"
                            + " held VARCHAR(128), riding VARCHAR(128), trust VARCHAR(8), INDEX who (uuid, t), INDEX chk (chk, t), INDEX loc (dim, x, z)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
                    }
                    ready = true;
                }
                try (PreparedStatement ps = c.prepareStatement("INSERT INTO pg_flags (t,uuid,name,chk,category,vl,detail,mode,dim,x,y,z,ping,tps,held,riding,trust) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)")) {
                    for (Flag f : batch) {
                        ps.setLong(1, f.time); ps.setString(2, f.uuid); ps.setString(3, f.player); ps.setString(4, f.check); ps.setString(5, f.category);
                        ps.setDouble(6, f.vl); ps.setString(7, f.detail.length() > 250 ? f.detail.substring(0, 250) : f.detail); ps.setString(8, f.mode);
                        ps.setInt(9, f.dim); ps.setInt(10, f.x); ps.setInt(11, f.y); ps.setInt(12, f.z); ps.setInt(13, f.ping); ps.setDouble(14, f.tps);
                        ps.setString(15, f.held); ps.setString(16, f.riding); ps.setString(17, f.trust);
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }
            } catch (Throwable t) {
                if (!broken) PrideGuard.LOG.warn("[PrideGuard] can't write flags to MySQL (keeping them in memory only): " + t);
                broken = true;
            }
        }
    }
}
