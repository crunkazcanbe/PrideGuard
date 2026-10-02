package com.dogpound.prideguard;

import net.minecraft.network.Packet;

/**
 * One anti-cheat check. Flags go through a BUFFER (a few in a row before one counts), then add to the player's
 * violation level for this check, which DECAYS over time. What happens next is the check's Mode, capped by
 * Safe Mode (the global "never punish" switch that is ON until she decides the checks are tuned).
 */
public abstract class Check {
    public final String id, name, category, about;
    public int index;

    // settings (config/prideguard.cfg, editable in the menu)
    public Mode mode = Mode.WATCH;
    public int sensitivity = 50;       // 1 = very forgiving … 100 = very strict
    public double bufferNeeded = 1;    // flags in a row before one counts
    public double decayPerMinute = 2;  // violation level lost per minute while clean
    public double kickAt = 20;         // violation level that kicks (when the mode is Kick)
    public double weight = 1;
    public boolean policy;             // a rule (not a cheat guess): Safe Mode doesn't soften it

    // who / when it runs (every check has these; menu → a check → All options)
    public boolean ignoreCreative, ignoreSpectator = true, ignoreOps, ignoreTrusted;   // creative still checked by default (kill aura, nuker work there too)
    public int joinGraceSec, maxPingMs, teleportGraceTicks, alertCooldownMs = -1;
    public double minTps, alertMinVl;
    public java.util.Set<String> offDims = new java.util.HashSet<>();

    // stats since start
    public long flagsTotal, cancelsTotal;
    public long nanos, runs;                 // time spent in this check (Performance page)
    public final java.util.Set<java.util.UUID> flaggedPlayers = new java.util.HashSet<>();
    public final int[] flagsByHour = new int[24];   // flags per hour of the day

    public final java.util.List<Opt> opts = new java.util.ArrayList<>();

    protected Check(String id, String name, String category, String about) {
        this.id = id; this.name = name; this.category = category; this.about = about;
    }

    // ---------------- options ----------------
    private static String[] modeNames() { String[] n = new String[Mode.values().length]; for (Mode m : Mode.values()) n[m.ordinal()] = m.name(); return n; }

    /** the settings every check has; called once after construction (so a check's own defaults are in place) */
    final void declare() {
        String s = id;
        add(Opt.choice(s, "mode", "Enforcement", "Mode", mode.name(), modeNames(), "what happens when it catches someone: Off, Watch (log only), Alert (tell staff), Block (stop it), Kick"), o -> mode = Mode.valueOf(o.raw()));
        add(Opt.integer(s, "sensitivity", "Enforcement", "Sensitivity", sensitivity, 1, 100, 5, "1 = very forgiving … 100 = very strict. Moves every limit inside the check"), o -> sensitivity = o.i());
        add(Opt.num(s, "buffer", "Enforcement", "In a row", bufferNeeded, 1, 20, 0.5, "how many suspicious actions in a row before one counts as a flag"), o -> bufferNeeded = o.d());
        add(Opt.num(s, "decayPerMinute", "Enforcement", "Decay per minute", decayPerMinute, 0, 100, 0.5, "violation level a player loses for every clean minute"), o -> decayPerMinute = o.d());
        add(Opt.num(s, "kickAt", "Enforcement", "Kick at VL", kickAt, 1, 1000, 5, "violation level that kicks, when the mode is Kick and Safe Mode is off"), o -> kickAt = o.d());
        add(Opt.num(s, "weight", "Enforcement", "Weight", weight, 0.1, 10, 0.1, "how much each flag adds to the violation level (x the size of the cheat)"), o -> weight = o.d());
        add(Opt.bool(s, "ignoreCreative", "Who it ignores", "Ignore creative mode", ignoreCreative, "don't check players in creative"), o -> ignoreCreative = o.on());
        add(Opt.bool(s, "ignoreSpectator", "Who it ignores", "Ignore spectators", ignoreSpectator, "don't check players in spectator mode"), o -> ignoreSpectator = o.on());
        add(Opt.bool(s, "ignoreOps", "Who it ignores", "Ignore ops", ignoreOps, "don't check server operators"), o -> ignoreOps = o.on());
        add(Opt.bool(s, "ignoreTrusted", "Who it ignores", "Ignore trusted players", ignoreTrusted, "don't check players with green trust (lots of play time)"), o -> ignoreTrusted = o.on());
        add(Opt.integer(s, "joinGraceSec", "Who it ignores", "Grace after joining (s)", joinGraceSec, 0, 600, 5, "seconds after logging in before this check starts watching (chunks still loading)"), o -> joinGraceSec = o.i());
        add(Opt.integer(s, "maxPingMs", "Lag", "Skip above ping (ms)", maxPingMs, 0, 5000, 50, "don't check players whose ping is higher than this. 0 = always check"), o -> maxPingMs = o.i());
        add(Opt.num(s, "minTps", "Lag", "Skip below server TPS", minTps, 0, 20, 0.5, "pause this check while the server runs slower than this. 0 = always check"), o -> minTps = o.d());
        add(Opt.integer(s, "teleportGraceTicks", "Lag", "Grace after teleport (ticks)", teleportGraceTicks, 0, 200, 5, "ticks after a teleport, respawn or dimension change to ignore (20 ticks = 1 s)"), o -> teleportGraceTicks = o.i());
        add(Opt.text(s, "offDims", "Worlds", "Off in dimensions", "", "dimension ids where this check doesn't run, comma separated (0 overworld, -1 nether, 1 end, others from mods)"), o -> offDims = o.list());
        add(Opt.num(s, "alertMinVl", "Alerts", "Alert from VL", alertMinVl, 0, 1000, 1, "only tell staff once the violation level is at least this"), o -> alertMinVl = o.d());
        add(Opt.integer(s, "alertCooldownMs", "Alerts", "Alert cooldown (ms)", alertCooldownMs, -1, 600000, 500, "at most one alert per player this often for this check. -1 = use the global setting"), o -> alertCooldownMs = o.i());
        options();
    }

    /** a check's own settings (its limits); override and use num()/integer()/bool() */
    protected void options() {}

    protected final Opt add(Opt o, java.util.function.Consumer<Opt> apply) { opts.add(o); o.onChange(() -> apply.accept(o)); return o; }
    protected final Opt num(String key, String label, double def, double min, double max, double step, String about) { Opt o = Opt.num(id, key, "Limits", label, def, min, max, step, about); opts.add(o); return o; }
    protected final Opt integer(String key, String label, int def, int min, int max, int step, String about) { Opt o = Opt.integer(id, key, "Limits", label, def, min, max, step, about); opts.add(o); return o; }
    protected final Opt bool(String key, String label, boolean def, String about) { Opt o = Opt.bool(id, key, "Limits", label, def, about); opts.add(o); return o; }

    /** a limit the Sensitivity slider moves between: both ends are settings of their own */
    protected final Opt[] range(String key, String label, double forgiving, double strict, double min, double max, double step, String about) {
        Opt a = Opt.num(id, key + "Forgiving", "Limits", label + " (forgiving end)", forgiving, min, max, step, about + " — used at sensitivity 1");
        Opt b = Opt.num(id, key + "Strict", "Limits", label + " (strict end)", strict, min, max, step, about + " — used at sensitivity 100");
        opts.add(a); opts.add(b);
        return new Opt[]{a, b};
    }
    protected double lerp(Opt[] r) { return lerp(r[0].d(), r[1].d()); }

    /** should this check look at this player right now? (the "Who it ignores", "Lag" and "Worlds" options) */
    public boolean active(PlayerData d) {
        net.minecraft.entity.player.EntityPlayerMP p = d.player;
        if (ignoreCreative && p.isCreative()) return false;
        if (ignoreSpectator && p.isSpectator()) return false;
        if (ignoreOps && Perm.has(p, Perm.BYPASS)) return false;
        if (ignoreTrusted && "green".equals(d.trust())) return false;
        if (joinGraceSec > 0 && System.currentTimeMillis() - d.joined < joinGraceSec * 1000L) return false;
        if (maxPingMs > 0 && d.pingMs > maxPingMs) return false;
        if (minTps > 0 && Guard.tpsNow < minTps) return false;
        if (teleportGraceTicks > 0 && d.inTeleportGrace(teleportGraceTicks)) return false;
        return offDims.isEmpty() || !offDims.contains(String.valueOf(p.dimension));
    }

    /** a client packet, on the server thread, before vanilla handles it. true = cancel it */
    public boolean onPacket(PlayerData d, Packet<?> p) { return false; }

    /** once per server tick per player */
    public void onTick(PlayerData d, int tick) {}

    /** 0 (sensitivity 1) … 1 (sensitivity 100) */
    protected double strict() { return (sensitivity - 1) / 99.0; }
    /** from a forgiving value to a strict one, by sensitivity */
    protected double lerp(double forgiving, double strict) { return forgiving + (strict - forgiving) * strict(); }

    /** clean behaviour: the buffer drains */
    protected void pass(PlayerData d) { d.buffer[index] = Math.max(0, d.buffer[index] - 0.25); }

    /** caught something. Returns true when the action should be stopped. */
    protected boolean flag(PlayerData d, double amount, String detail) {
        if (Guard.debug(this)) PrideGuardDebug.log(this, d.player, String.format("FLAG? amount=%.2f buffer=%.2f/%.1f exempt=%b active=%b: %s", amount, d.buffer[index], bufferNeeded, d.exempt(), active(d), detail));
        if (d.exempt()) return false;
        d.buffer[index] += 1;
        if (d.buffer[index] < bufferNeeded) return false;
        d.vl[index] += weight * Math.max(0.1, amount);
        d.flags[index]++;
        flagsTotal++;
        flaggedPlayers.add(d.uuid);
        flagsByHour[java.time.LocalTime.now().getHour()]++;
        Mode m = Guard.effective(this);
        Guard.record(this, d, detail, m);
        if (m.ordinal() >= Mode.KICK.ordinal() && d.vl[index] >= kickAt) Guard.kick(d, this);
        boolean stop = m.ordinal() >= Mode.CANCEL.ordinal();
        if (stop) cancelsTotal++;
        return stop;
    }
}
