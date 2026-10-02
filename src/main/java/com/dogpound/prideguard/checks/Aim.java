package com.dogpound.prideguard.checks;

import com.dogpound.prideguard.Opt;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.dogpound.prideguard.Check;
import com.dogpound.prideguard.PlayerData;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.CPacketPlayer;
import net.minecraft.network.play.client.CPacketUseEntity;

/**
 * Aimbots and kill-aura rotations, judged ONLY in fights (a hit within the last 2 s), so Buddy/Baritone turning
 * the camera while walking or mining never counts. (1) Snap: 60°+ onto the target in the very tick of the hit.
 * (2) Non-mouse aim: a mouse turns the camera in steps of one fixed size (from the sensitivity) — the step
 * shared by all recent pitch changes (their "GCD"). Aimbots turn by smooth maths, so that shared step collapses
 * to nothing. Research: never punish on aim alone — this one stays at Watch/Alert.
 */
public class Aim extends Check {
    private final Map<UUID, ArrayDeque<Double>> pitch = new HashMap<>();
    private final Map<UUID, Long> lastHit = new HashMap<>();
    private final Map<UUID, float[]> lastRot = new HashMap<>();

    public Aim() { super("aim", "Aim Analysis", "Combat", "aimbots and kill-aura: snapping onto targets, camera moves no mouse can make (fights only)"); bufferNeeded = 3; decayPerMinute = 1; }
    private Opt fight, window, need, maxTurn;
    private Opt[] gcd;
    @Override protected void options() {
        fight = integer("fightMs", "Fight window (ms)", 2000, 200, 20000, 100, "only aim movement this soon after a hit is analysed");
        window = integer("window", "Samples kept", 60, 20, 400, 5, "recent aim steps remembered");
        need = integer("minSamples", "Samples before judging", 40, 10, 400, 5, "aim steps needed before a verdict");
        maxTurn = num("maxTurn", "Ignore turns bigger than (degrees)", 40, 5, 180, 1, "big flicks are ignored (not mouse-sized steps)");
        gcd = range("gcd", "Smallest real mouse step (degrees)", 0.0015, 0.004, 0.0001, 0.05, 0.0001, "aim moving in steps smaller than a real mouse can make");
    }

    @Override
    public boolean onPacket(PlayerData d, Packet<?> p) {
        long now = System.currentTimeMillis();
        if (p instanceof CPacketUseEntity && ((CPacketUseEntity) p).getAction() == CPacketUseEntity.Action.ATTACK) {
            lastHit.put(d.uuid, now);
            float[] r = lastRot.get(d.uuid);
            if (r != null && r[2] > 60 && now - (long) r[3] < 60) return flag(d, r[2] / 60, String.format("snapped %.0f° onto the target in the tick it hit", r[2]));
            return false;
        }
        if (!(p instanceof CPacketPlayer) || d.player.isRiding()) return false;
        CPacketPlayer m = (CPacketPlayer) p;
        float yaw = m.getYaw(d.player.rotationYaw), pit = m.getPitch(d.player.rotationPitch);
        float[] prev = lastRot.get(d.uuid);
        float dy = prev == null ? 0 : Math.abs(net.minecraft.util.math.MathHelper.wrapDegrees(yaw - prev[0])), dp = prev == null ? 0 : Math.abs(pit - prev[1]);
        lastRot.put(d.uuid, new float[]{yaw, pit, (float) Math.hypot(dy, dp), now});
        Long hit = lastHit.get(d.uuid);
        if (hit == null || now - hit > fight.i() || dp < 0.01 || dp > maxTurn.d()) return false;         // fights only; ignore still / huge turns
        ArrayDeque<Double> q = pitch.computeIfAbsent(d.uuid, k -> new ArrayDeque<>());
        q.add((double) dp);
        if (q.size() > window.i()) q.poll();
        if (q.size() < need.i()) return false;
        double g = 0;
        for (double v : q) g = gcd(g, v);
        if (g >= lerp(gcd)) { pass(d); return false; }
        q.clear();
        return flag(d, 1, String.format("camera steps share no mouse step (%.5f°) during a fight", g));
    }

    private static double gcd(double a, double b) {
        for (int i = 0; i < 40 && b > 1e-3; i++) { double t = a % b; a = b; b = t; }   // 0.001° tolerance: mouse rounding noise never counts
        return a;
    }
}
