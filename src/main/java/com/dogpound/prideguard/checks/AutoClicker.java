package com.dogpound.prideguard.checks;

import com.dogpound.prideguard.Opt;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.dogpound.prideguard.Check;
import com.dogpound.prideguard.PlayerData;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.CPacketUseEntity;

/**
 * Autoclickers and macros, from the gaps between hits (never mining swings — those tick like a metronome on their
 * own). Two signs: more clicks a second than a hand manages, or fast clicking whose rhythm barely varies —
 * people are messy, macros aren't. Butterfly/jitter clickers are fast but messy, so they pass the second test.
 */
public class AutoClicker extends Check {
    private final Map<UUID, ArrayDeque<Long>> hits = new HashMap<>();

    public AutoClicker() { super("autoclicker", "Auto Clicker", "Combat", "clicking faster or more perfectly evenly than a human hand can"); bufferNeeded = 2; }
    private Opt window, minClicks, humanCps;
    private Opt[] maxCps, rhythm;
    @Override protected void options() {
        window = integer("window", "Clicks remembered", 20, 5, 100, 1, "how many recent hits are looked at");
        minClicks = integer("minClicks", "Clicks before judging", 12, 3, 100, 1, "hits in a burst before the rhythm is judged");
        maxCps = range("maxCps", "Fastest clicks per second", 25, 14, 5, 60, 1, "clicking faster than this flags");
        humanCps = num("rhythmFromCps", "Judge rhythm above (clicks/s)", 8, 1, 30, 0.5, "the evenness test only runs when clicking at least this fast");
        rhythm = range("rhythm", "Least human variation (ms)", 6, 14, 0.5, 60, 0.5, "click gaps varying less than this are too perfect for a hand");
    }

    @Override
    public boolean onPacket(PlayerData d, Packet<?> p) {
        if (!(p instanceof CPacketUseEntity) || ((CPacketUseEntity) p).getAction() != CPacketUseEntity.Action.ATTACK) return false;
        ArrayDeque<Long> q = hits.computeIfAbsent(d.uuid, k -> new ArrayDeque<>());
        long now = System.nanoTime();
        if (!q.isEmpty() && now - q.peekLast() > 1_000_000_000L) q.clear();     // a pause: new burst
        q.add(now);
        if (q.size() > window.i()) q.poll();
        if (q.size() < minClicks.i()) return false;
        Long[] t = q.toArray(new Long[0]);
        double[] gaps = new double[t.length - 1];
        double mean = 0;
        for (int i = 1; i < t.length; i++) { gaps[i - 1] = (t[i] - t[i - 1]) / 1e6; mean += gaps[i - 1]; }
        mean /= gaps.length;
        double var = 0;
        for (double g : gaps) var += (g - mean) * (g - mean);
        double sd = Math.sqrt(var / gaps.length), cps = 1000 / Math.max(1, mean);
        if (cps > lerp(maxCps)) { q.clear(); return flag(d, cps / 10, String.format("%.1f clicks a second", cps)); }
        if (cps > humanCps.d() && sd < lerp(rhythm)) { q.clear(); return flag(d, 1, String.format("%.1f clicks/s, rhythm varies only %.1f ms", cps, sd)); }
        pass(d);
        return false;
    }
}
