package com.dogpound.prideguard;

import java.util.ArrayDeque;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.math.BlockPos;

/** Everything PrideGuard remembers about one online player. */
public final class PlayerData {
    public final UUID uuid;
    public EntityPlayerMP player;
    public final long joined = System.currentTimeMillis();

    // ---- latency: silent "transaction" pings every tick; every vanilla 1.12 client answers them in order ----
    final ArrayDeque<long[]> pendingTx = new ArrayDeque<>();   // {id, sentNanos}
    short nextTx = -1;
    public double pingMs = -1;                                 // smoothed round trip
    public long lastAck = System.nanoTime();

    // ---- per-check state (indexed like Guard.CHECKS) ----
    public final double[] vl = new double[Guard.CHECKS.size()];
    public final double[] buffer = new double[Guard.CHECKS.size()];
    public final int[] flags = new int[Guard.CHECKS.size()];
    public final long[] lastAlert = new long[Guard.CHECKS.size()];

    // ---- check scratch ----
    public long fakeClock;             // Timer: 50 ms per movement packet
    public boolean clockSet;
    public BlockPos digPos;            // FastBreak
    public double digProgress;
    public int digStartTick;
    public boolean digging;
    public int startsThisTick, instantThisSecond;
    public int packetsThisSecond, flyingThisSecond, clicksThisSecond;
    public int lastSlot = -1;
    public boolean sprintStarted;
    public int lastTeleportTick = -1000, groundSpoof;
    public int tpConfirmTick = -1000;                    // when their game confirmed our last teleport (CPacketConfirmTeleport)

    /** still settling after a teleport? Once their game confirms it, only a 3-tick settle (vanilla drops their moves until
     *  the confirm, so waiting longer just hands cheaters free seconds); unconfirmed teleports fall back to maxTicks. */
    public boolean inTeleportGrace(int maxTicks) {
        if (tpConfirmTick >= lastTeleportTick) return Guard.tick - tpConfirmTick < 3;
        return Guard.tick - lastTeleportTick < maxTicks;
    }
    public long exemptUntil;           // /pg exempt
    public int sentThisTick;
    public int swingsThisDig;                        // NoSwing: arm swings while digging
    public boolean hitAwaitingSwing;                 // KillAura: attacked, swing not seen yet
    public final java.util.Set<Integer> hitThisTick = new java.util.HashSet<>();
    public int payloadsThisSecond, tabsThisSecond;
    public java.util.Map<String, String> mods = java.util.Collections.emptyMap();   // FML handshake mod list

    // ---- movement (Phase 2) ----
    public double lastX, lastY, lastZ;               // last position the client reported
    public boolean hasLast, lastGround;
    public int airTicks, hoverTicks, liquidTopTicks;
    public double speedExcess;
    public int lastKnockTick = -1000, lastIceTick = -1000, lastBounceTick = -1000;
    public double knockSpeed;

    PlayerData(EntityPlayerMP p) { this.uuid = p.getUniqueID(); this.player = p; }
    PlayerData(UUID id) { this.uuid = id; this.player = null; }   // offline / self-test stand-in (no player object)

    public int pingTicks() { return pingMs < 0 ? 3 : (int) Math.ceil(pingMs / 50.0) + 1; }
    public double totalVl() { double t = 0; for (double v : vl) t += v; return t; }
    public boolean exempt() { return System.currentTimeMillis() < exemptUntil; }

    /** trust tier from play time and op status: scales how strict checks are, never exempts */
    public String trust() {
        if (Guard.opsAreTrusted && Perm.has(player, Perm.TRUSTED)) return "green";
        double hours = player.getStatFile().readStat(net.minecraft.stats.StatList.PLAY_ONE_MINUTE) / 20.0 / 3600.0;
        return hours >= Guard.trustGreenHours ? "green" : hours >= Guard.trustYellowHours ? "yellow" : hours >= Guard.trustOrangeHours ? "orange" : "red";
    }
}
