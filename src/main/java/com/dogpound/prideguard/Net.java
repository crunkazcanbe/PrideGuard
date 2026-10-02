package com.dogpound.prideguard;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import net.minecraftforge.fml.relauncher.Side;

/** menu <-> server. Only ops (permission 2) get data or can change anything. */
public final class Net {
    public static final SimpleNetworkWrapper CH = NetworkRegistry.INSTANCE.newSimpleChannel("prideguard");

    static void init() {
        CH.registerMessage(Ask.Handler.class, Ask.class, 0, Side.SERVER);
        CH.registerMessage(Snapshot.Handler.class, Snapshot.class, 1, Side.CLIENT);
        CH.registerMessage(Set.Handler.class, Set.class, 2, Side.SERVER);
        CH.registerMessage(Open.Handler.class, Open.class, 3, Side.CLIENT);
    }

    static boolean admin(EntityPlayerMP p) { return Perm.has(p, Perm.ADMIN); }

    static void str(ByteBuf b, String s) {
        byte[] d = (s == null ? "" : s).getBytes(StandardCharsets.UTF_8);
        int n = Math.min(d.length, 30000);
        b.writeShort(n); b.writeBytes(d, 0, n);
    }
    static String str(ByteBuf b) { byte[] d = new byte[b.readUnsignedShort()]; b.readBytes(d); return new String(d, StandardCharsets.UTF_8); }

    /** client: "send me everything" */
    public static class Ask implements IMessage {
        boolean opts;                       // also send every option (big: only when an options page needs them)
        public Ask() {}
        public Ask(boolean opts) { this.opts = opts; }
        @Override public void toBytes(ByteBuf b) { b.writeBoolean(opts); }
        @Override public void fromBytes(ByteBuf b) { opts = b.readBoolean(); }
        public static class Handler implements IMessageHandler<Ask, IMessage> {
            @Override public IMessage onMessage(Ask m, MessageContext ctx) {
                EntityPlayerMP p = ctx.getServerHandler().player;
                p.getServerWorld().addScheduledTask(() -> { if (admin(p)) CH.sendTo(Snapshot.build(p, m.opts), p); });
                return null;
            }
        }
    }

    /** the server opens the menu (from /pg gui) */
    public static class Open implements IMessage {
        public Open() {}
        @Override public void toBytes(ByteBuf b) {}
        @Override public void fromBytes(ByteBuf b) {}
        public static class Handler implements IMessageHandler<Open, IMessage> {
            @Override public IMessage onMessage(Open m, MessageContext ctx) {
                net.minecraft.client.Minecraft.getMinecraft().addScheduledTask(ClientProxy::openMenu);
                return null;
            }
        }
    }

    /** everything the menu shows */
    public static class Snapshot implements IMessage {
        public boolean safeMode, alertOps, logDb, myAlerts;
        public int cooldown, refreshSec = 2;
        public boolean hasOpts;
        public List<String[]> checks = new ArrayList<>();   // id,name,category,about,mode,sens,buffer,decay,kickAt,flags,cancels
        public List<String[]> players = new ArrayList<>();  // name,uuid,ping,totalVl,trust,topCheck,vls(csv),flags(csv),exemptSecs
        public List<String[]> feed = new ArrayList<>();     // time,player,check,category,detail,vl,ping,mode,dim,x,y,z,held,riding,trust,tps,sources
        public List<String[]> approved = new ArrayList<>(); // approved movement sources
        public List<String[]> opts = new ArrayList<>();     // every option: see Opt.row() (only when asked)
        public List<String[]> audit = new ArrayList<>();    // time,who,what
        public List<String[]> server = new ArrayList<>();   // one row: tps,tickMs,online,feedSize,uptimeMin,maxMemMb,usedMemMb

        public Snapshot() {}

        static Snapshot build(EntityPlayerMP viewer) { return build(viewer, false); }

        static Snapshot build(EntityPlayerMP viewer, boolean withOpts) {
            Snapshot s = new Snapshot();
            s.safeMode = Guard.safeMode; s.alertOps = Guard.alertOps; s.logDb = Guard.logDb; s.cooldown = Guard.alertCooldownMs;
            s.refreshSec = Guard.menuRefreshSec;
            s.hasOpts = withOpts;
            if (withOpts) for (Opt o : Opt.ALL) s.opts.add(o.row());
            synchronized (Guard.AUDIT) { for (String[] a : Guard.AUDIT) s.audit.add(a); }
            net.minecraft.server.MinecraftServer ms = viewer.getServer();
            double tickMs = 0;
            if (ms != null) { for (long t : ms.tickTimeArray) tickMs += t; tickMs = tickMs / ms.tickTimeArray.length / 1e6; }
            Runtime rt = Runtime.getRuntime();
            s.server.add(new String[]{String.format("%.1f", Guard.tpsNow), String.format("%.1f", tickMs), String.valueOf(Guard.online().size()), String.valueOf(Guard.FEED.size()),
                String.valueOf((System.currentTimeMillis() - PrideGuard.STARTED) / 60000), String.valueOf(rt.maxMemory() >> 20), String.valueOf((rt.totalMemory() - rt.freeMemory()) >> 20)});
            s.myAlerts = !Guard.ALERTS_OFF.contains(viewer.getUniqueID());
            for (Check c : Guard.CHECKS)
            {
                StringBuilder hrs = new StringBuilder();
                for (int h = 0; h < 24; h++) hrs.append(h == 0 ? "" : ",").append(c.flagsByHour[h]);
                s.checks.add(new String[]{c.id, c.name, c.category, c.about, c.mode.name(), c.sensitivity + "", c.bufferNeeded + "", c.decayPerMinute + "", c.kickAt + "", c.flagsTotal + "", c.cancelsTotal + "",
                    String.format("%.1f", c.runs == 0 ? 0 : c.nanos / 1000.0 / c.runs), String.valueOf(c.runs), String.format("%.1f", c.nanos / 1e6), String.valueOf(c.flaggedPlayers.size()), hrs.toString(), String.valueOf(c.opts.size())});
            }
            for (PlayerData d : Guard.online()) {
                StringBuilder v = new StringBuilder(), f = new StringBuilder();
                int top = -1;
                for (int i = 0; i < d.vl.length; i++) {
                    v.append(i == 0 ? "" : ",").append(String.format("%.1f", d.vl[i]));
                    f.append(i == 0 ? "" : ",").append(d.flags[i]);
                    if (d.vl[i] > 0 && (top < 0 || d.vl[i] > d.vl[top])) top = i;
                }
                EntityPlayerMP pl = d.player;
                double hours = pl.getStatFile().readStat(net.minecraft.stats.StatList.PLAY_ONE_MINUTE) / 20.0 / 3600.0;
                s.players.add(new String[]{pl.getName(), d.uuid.toString(), String.valueOf((int) Math.max(0, d.pingMs)), String.format("%.1f", d.totalVl()),
                    d.trust(), top < 0 ? "-" : Guard.CHECKS.get(top).name, v.toString(), f.toString(), String.valueOf(Math.max(0, (d.exemptUntil - System.currentTimeMillis()) / 1000)),
                    String.valueOf((System.currentTimeMillis() - d.joined) / 60000), String.valueOf(pl.dimension), pl.interactionManager.getGameType().getName(),
                    (int) pl.posX + " " + (int) pl.posY + " " + (int) pl.posZ, String.format("%.1f", hours), String.valueOf(d.clicksThisSecond), String.format("%.1f", pl.getHealth())});
            }
            synchronized (Guard.FEED) {
                int n = 0;
                for (Guard.Flag x : Guard.FEED) {
                    if (n++ >= 300) break;
                    s.feed.add(new String[]{x.time + "", x.player, x.check, x.category, x.detail, String.format("%.1f", x.vl), x.ping + "", x.mode,
                        x.dim + "", x.x + "", x.y + "", x.z + "", x.held, x.riding, x.trust, String.format("%.1f", x.tps), x.sources});
                }
            }
            for (String a : Sources.APPROVED) s.approved.add(new String[]{a});
            return s;
        }

        private static void rows(ByteBuf b, List<String[]> rows) { b.writeShort(rows.size()); for (String[] r : rows) { b.writeByte(r.length); for (String x : r) str(b, x); } }
        private static List<String[]> rows(ByteBuf b) {
            List<String[]> out = new ArrayList<>();
            int n = b.readUnsignedShort();
            for (int i = 0; i < n; i++) { String[] r = new String[b.readUnsignedByte()]; for (int k = 0; k < r.length; k++) r[k] = str(b); out.add(r); }
            return out;
        }

        @Override public void toBytes(ByteBuf b) {
            b.writeBoolean(safeMode); b.writeBoolean(alertOps); b.writeBoolean(logDb); b.writeBoolean(myAlerts); b.writeInt(cooldown);
            b.writeByte(refreshSec); b.writeBoolean(hasOpts);
            rows(b, checks); rows(b, players); rows(b, feed); rows(b, approved); rows(b, opts); rows(b, audit); rows(b, server);
        }
        @Override public void fromBytes(ByteBuf b) {
            safeMode = b.readBoolean(); alertOps = b.readBoolean(); logDb = b.readBoolean(); myAlerts = b.readBoolean(); cooldown = b.readInt();
            refreshSec = b.readUnsignedByte(); hasOpts = b.readBoolean();
            checks = rows(b); players = rows(b); feed = rows(b); approved = rows(b); opts = rows(b); audit = rows(b); server = rows(b);
        }

        public static class Handler implements IMessageHandler<Snapshot, IMessage> {
            @Override public IMessage onMessage(Snapshot m, MessageContext ctx) {
                net.minecraft.client.Minecraft.getMinecraft().addScheduledTask(() -> {
                    if (!m.hasOpts && ClientProxy.snapshot != null) m.opts = ClientProxy.snapshot.opts;   // keep the last full option list
                    ClientProxy.snapshot = m;
                });
                return null;
            }
        }
    }

    /** client changes something: "global.safeMode"=true, "check.reach.mode"=ALERT, "player.Steve.reset", … */
    public static class Set implements IMessage {
        String key, value;
        public Set() {}
        public Set(String key, String value) { this.key = key; this.value = value; }
        @Override public void toBytes(ByteBuf b) { str(b, key); str(b, value); }
        @Override public void fromBytes(ByteBuf b) { key = str(b); value = str(b); }

        public static class Handler implements IMessageHandler<Set, IMessage> {
            @Override public IMessage onMessage(Set m, MessageContext ctx) {
                EntityPlayerMP p = ctx.getServerHandler().player;
                p.getServerWorld().addScheduledTask(() -> {
                    if (!admin(p)) return;
                    Settings.apply(p, m.key, m.value);
                    CH.sendTo(Snapshot.build(p, true), p);
                });
                return null;
            }
        }
    }

    /** one place that changes settings — the menu and /pg both use it */
    public static final class Settings {
        public static String apply(EntityPlayerMP by, String key, String value) {
            String[] k = key.split("\\.");
            String done;
            try {
                // the old short keys the menu/commands still use → their option
                if (k[0].equals("global") && k.length == 2 && !k[1].equals("myAlerts")) {
                    String ok = k[1].equals("logDb") ? "logToDatabase" : k[1].equals("cooldown") ? "alertCooldownMs" : k[1];
                    return setOpt(by, "global", ok, value);
                }
                if (k[0].equals("check") && k.length == 3) {
                    Check c = Guard.check(k[1]);
                    if (c == null) return "no such check";
                    String ok = k[2].equals("sens") ? "sensitivity" : k[2].equals("decay") ? "decayPerMinute" : k[2];
                    return setOpt(by, c.id, ok, value);
                }
                if (k[0].equals("opt")) {                      // opt.<scope>.<key>  (scope = global or a check id)
                    if (k.length < 3) return "opt.<scope>.<key>";
                    if (k[2].equals("*reset")) {               // opt.<scope>.*reset = put a whole page back to defaults
                        int n = 0;
                        for (Opt o : Opt.ALL) if (o.scope.equals(k[1]) && o.changed()) { o.reset(); n++; }
                        Guard.save(); Guard.audit(by.getName(), k[1] + ": " + n + " settings back to default");
                        return n + " settings reset";
                    }
                    return setOpt(by, k[1], key.substring(k[0].length() + k[1].length() + 2), value);
                }
                switch (k[0]) {
                    case "global":
                        switch (k[1]) {
                            case "safeMode": Guard.safeMode = Boolean.parseBoolean(value); break;
                            case "alertOps": Guard.alertOps = Boolean.parseBoolean(value); break;
                            case "logDb": Guard.logDb = Boolean.parseBoolean(value); break;
                            case "cooldown": Guard.alertCooldownMs = Math.max(0, Math.min(60000, Integer.parseInt(value))); break;
                            case "myAlerts": if (Boolean.parseBoolean(value)) Guard.ALERTS_OFF.remove(by.getUniqueID()); else Guard.ALERTS_OFF.add(by.getUniqueID()); return "your alerts " + (Boolean.parseBoolean(value) ? "on" : "off");
                            default: return "unknown setting";
                        }
                        done = k[1] + " = " + value;
                        break;
                    case "category": {                 // category.Combat.mode = ALERT
                        Mode md = Mode.valueOf(value);
                        for (Check c : Guard.CHECKS) if (c.category.equals(k[1])) Opt.get(c.id, "mode").set(md.name());
                        done = "every " + k[1] + " check → " + md.label;
                        break;
                    }
                    case "check": {
                        Check c = Guard.check(k[1]);
                        if (c == null) return "no such check";
                        switch (k[2]) {
                            case "mode": c.mode = Mode.valueOf(value); break;
                            case "sens": c.sensitivity = Math.max(1, Math.min(100, Integer.parseInt(value))); break;
                            case "buffer": c.bufferNeeded = Math.max(1, Math.min(20, Double.parseDouble(value))); break;
                            case "decay": c.decayPerMinute = Math.max(0, Math.min(100, Double.parseDouble(value))); break;
                            case "kickAt": c.kickAt = Math.max(1, Math.min(1000, Double.parseDouble(value))); break;
                            default: return "unknown check setting";
                        }
                        done = c.name + " " + k[2] + " = " + value;
                        break;
                    }
                    case "source": {                   // source.approve|revoke = item:modid:thing
                        if (k[1].equals("approve")) Sources.APPROVED.add(value); else Sources.APPROVED.remove(value);
                        done = (k[1].equals("approve") ? "approved " : "un-approved ") + value + " as a movement source";
                        break;
                    }
                    case "player": {                   // player.<name>.reset|exempt|kick
                        PlayerData d = Guard.byName(k[1]);
                        if (d == null) return "they're not online";
                        switch (k[2]) {
                            case "reset": java.util.Arrays.fill(d.vl, 0); java.util.Arrays.fill(d.buffer, 0); done = "reset " + k[1]; break;
                            case "exempt": d.exemptUntil = System.currentTimeMillis() + Math.max(0, Integer.parseInt(value)) * 60_000L; done = "exempted " + k[1] + " for " + value + " min"; break;
                            case "kick": d.player.connection.disconnect(new TextComponentString("§d✦ PrideGuard§r: kicked by " + by.getName())); done = "kicked " + k[1]; break;
                            default: return "unknown player action";
                        }
                        break;
                    }
                    default: return "unknown";
                }
            } catch (RuntimeException e) {
                return "bad value: " + value;
            }
            Guard.save();
            PrideGuard.LOG.info("[PrideGuard] " + by.getName() + " set " + done);
            Guard.audit(by.getName(), done);
            return done;
        }

        private static String setOpt(EntityPlayerMP by, String scope, String key, String value) {
            Opt o = Opt.get(scope, key);
            if (o == null) return "no setting " + scope + "." + key;
            if (value.equals("*default")) value = o.def;
            String before = o.raw(), err = o.set(value);
            if (err != null) return o.label + ": " + err;
            Guard.save();
            String what = (scope.equals("global") ? "" : scope + " ") + o.label + ": " + before + " → " + o.raw();
            PrideGuard.LOG.info("[PrideGuard] " + by.getName() + " set " + what);
            Guard.audit(by.getName(), what);
            return what;
        }
    }
}
