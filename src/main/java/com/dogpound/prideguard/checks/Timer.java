package com.dogpound.prideguard.checks;

import com.dogpound.prideguard.Opt;
import com.dogpound.prideguard.Check;
import com.dogpound.prideguard.Guard;
import com.dogpound.prideguard.PlayerData;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.CPacketPlayer;

/**
 * Speeding up the game clock (timer hacks make you walk, eat and fight faster). A 1.12 client sends exactly one
 * movement packet per tick, 50 ms apart. Each packet moves a "packet clock" forward 50 ms; if it runs ahead of
 * real time, the client is ticking too fast. Banked time is capped at 1 s (a frozen client may catch up, not
 * store up), and it resets after server lag spikes, teleports and respawns so those never flag.
 */
public class Timer extends Check {
    public Timer() { super("timer", "Timer", "Movement", "running the game faster than normal speed"); bufferNeeded = 3; decayPerMinute = 3; }
    private Opt tp;
    private Opt[] ahead;
    @Override protected void options() {
        tp = integer("teleportTicks", "Ignore after teleport (ticks)", 40, 0, 400, 5, "ticks after a teleport before game speed is judged");
        ahead = range("aheadMs", "Allowed to run ahead (ms)", 400, 90, 10, 3000, 10, "how far the player's game clock may run ahead of the server's");
    }

    @Override
    public boolean onPacket(PlayerData d, Packet<?> p) {
        if (!(p instanceof CPacketPlayer)) return false;
        long now = System.nanoTime();
        d.flyingThisSecond++;
        if (Guard.debug(this)) com.dogpound.prideguard.PrideGuardDebug.log(this, d.player, String.format("clockSet=%b lag=%b tpGrace=%b ahead=%.0fms", d.clockSet, Guard.tick < Guard.lagUntilTick, d.inTeleportGrace(tp.i()), (Math.max(d.fakeClock + 50_000_000L, now - 1_000_000_000L) - now) / 1e6));
        if (!d.clockSet || Guard.tick < Guard.lagUntilTick || d.inTeleportGrace(tp.i())) { d.fakeClock = now; d.clockSet = true; return false; }
        d.fakeClock = Math.max(d.fakeClock + 50_000_000L, now - 1_000_000_000L);
        long ahead = d.fakeClock - now, allow = (long) (lerp(this.ahead) * 1_000_000L);
        // NO pass(d) per packet (2026-09-29): a timer cheat sends ~15-30 honest-looking packets between flags, and
        // -0.25 each wiped every flag out, so the buffer never reached bufferNeeded — timer hacks were never caught.
        // The buffer cools down by decayPerMinute instead.
        if (ahead <= allow) return false;
        d.fakeClock = now;                                            // one flag per burst
        return flag(d, ahead / 50e6, String.format("%.0f ms ahead of real time", ahead / 1e6));
    }
}
