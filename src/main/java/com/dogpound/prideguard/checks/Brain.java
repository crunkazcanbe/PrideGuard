package com.dogpound.prideguard.checks;

import com.dogpound.prideguard.BrainNet;
import com.dogpound.prideguard.Check;
import com.dogpound.prideguard.Opt;
import com.dogpound.prideguard.PlayerData;
import com.dogpound.prideguard.PrideGuard;
import net.minecraft.entity.Entity;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.CPacketAnimation;
import net.minecraft.network.play.client.CPacketPlayer;
import net.minecraft.network.play.client.CPacketUseEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraftforge.common.DimensionManager;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * AI Cheat Brain: learns what cheating LOOKS like in fights instead of following a written rule. Every hit becomes
 * a few numbers (aim error on the target, how suddenly the aim snapped, click timing, reach, swings, target
 * switching); each window of the last 20 hits becomes 12 features; {@link BrainNet} scores it 0 (human) to 1 (cheat).
 * It learns from labelled play: `/pg brain record <player> cheat|legit`, then `/pg brain train`.
 * Never punishes on its own (research: never ban on aim alone) — it only adds a Watch flag and a score in /pg info.
 */
public class Brain extends Check {
    public static final int FEATURES = 12, WINDOW = 20;
    public static BrainNet net;
    /** player → "cheat" / "legit" while recording training data */
    public static final Map<UUID, String> recording = new HashMap<>();
    public static final Map<UUID, Double> lastScore = new HashMap<>();

    private static final Map<UUID, ArrayDeque<double[]>> hits = new HashMap<>();   // per hit: angleOff, snap, avgTurn, intervalMs, dist, swung, switched
    private final Map<UUID, float[]> rot = new HashMap<>();                 // last yaw, pitch
    private final Map<UUID, ArrayDeque<Double>> turns = new HashMap<>();     // recent per-packet turn sizes
    private final Map<UUID, long[]> lastHit = new HashMap<>();              // time, entity id
    private final Map<UUID, Boolean> swung = new HashMap<>();
    private final Map<UUID, Integer> sinceWindow = new HashMap<>();

    public Brain() { super("brain", "AI Cheat Brain", "Combat", "a small neural network, trained on this server, that scores how cheat-like someone fights"); bufferNeeded = 2; decayPerMinute = 1; }
    private Opt[] threshold;
    @Override protected void options() { threshold = range("threshold", "Cheat score to flag", 0.97, 0.85, 0.5, 1, 0.01, "the brain's score (0-1) above which it adds a flag"); }

    @Override
    public boolean onPacket(PlayerData d, Packet<?> p) {
        UUID id = d.uuid;
        if (p instanceof CPacketAnimation) { swung.put(id, true); return false; }
        if (p instanceof CPacketPlayer && ((CPacketPlayer) p).getYaw(Float.NaN) == ((CPacketPlayer) p).getYaw(Float.NaN)) {   // has rotation
            CPacketPlayer m = (CPacketPlayer) p;
            float yaw = m.getYaw(0), pit = m.getPitch(0);
            float[] r = rot.get(id);
            if (r != null) {
                ArrayDeque<Double> q = turns.computeIfAbsent(id, k -> new ArrayDeque<>());
                q.add(Math.hypot(MathHelper.wrapDegrees(yaw - r[0]), pit - r[1]));
                if (q.size() > 6) q.poll();
            }
            rot.put(id, new float[]{yaw, pit});
            return false;
        }
        if (!(p instanceof CPacketUseEntity) || ((CPacketUseEntity) p).getAction() != CPacketUseEntity.Action.ATTACK) return false;
        Entity t = ((CPacketUseEntity) p).getEntityFromWorld(d.player.world);
        if (t == null) return false;
        long now = System.currentTimeMillis();
        Vec3d eye = new Vec3d(d.player.posX, d.player.posY + d.player.getEyeHeight(), d.player.posZ);
        Vec3d to = new Vec3d(t.posX, t.posY + t.height / 2, t.posZ).subtract(eye);
        double dist = to.lengthVector();
        double off = dist < 1e-3 ? 0 : Math.toDegrees(Math.acos(MathHelper.clamp(d.player.getLookVec().dotProduct(to.normalize()), -1, 1)));
        ArrayDeque<Double> q = turns.getOrDefault(id, new ArrayDeque<>());
        double last = q.isEmpty() ? 0 : q.peekLast(), avg = 0;
        for (double v : q) avg += v;
        avg = q.isEmpty() ? 0 : avg / q.size();
        long[] lh = lastHit.get(id);
        double interval = lh == null ? 1000 : Math.min(2000, now - lh[0]);
        boolean switched = lh != null && lh[1] != t.getEntityId();
        lastHit.put(id, new long[]{now, t.getEntityId()});
        ArrayDeque<double[]> h = hits.computeIfAbsent(id, k -> new ArrayDeque<>());
        h.add(new double[]{off, last, avg, interval, dist, swung.getOrDefault(id, false) ? 1 : 0, switched ? 1 : 0});
        swung.put(id, false);
        if (h.size() > WINDOW) h.poll();
        if (h.size() < WINDOW) return false;
        int n = sinceWindow.merge(id, 1, Integer::sum);
        if (n < 5) return false;                                    // a new window every 5 hits
        sinceWindow.put(id, 0);
        double[] f = features(h);
        String label = recording.get(id);
        if (label != null) save(f, label.equals("cheat") ? 1 : 0);
        if (net == null || net.trainedOn == 0) return false;
        double s = net.score(f);
        lastScore.put(id, s);
        if (s < lerp(threshold)) { pass(d); return false; }
        return flag(d, 0.5, String.format(Locale.ROOT, "AI brain: fights %.0f%% like a cheat (last %d hits)", s * 100, WINDOW));
    }

    /** start a player's hit history over (so a window never mixes two labels) */
    public static void resetHits(UUID id) { hits.remove(id); }

    /** 12 numbers, each roughly 0..1, describing a window of hits */
    static double[] features(ArrayDeque<double[]> h) {
        int n = h.size();
        double mOff = 0, mOff2 = 0, precise = 0, mSnap = 0, mTurn = 0, mInt = 0, mInt2 = 0, fast = 0, mDist = 0, sw = 0, swi = 0, maxTurn = 0;
        for (double[] e : h) {
            mOff += e[0]; mOff2 += e[0] * e[0]; if (e[0] < 3) precise++;
            mSnap += Math.log1p(e[1] / (e[2] + 0.5)); mTurn += e[1]; maxTurn = Math.max(maxTurn, e[1]);
            mInt += e[3]; mInt2 += e[3] * e[3]; if (e[3] < 80) fast++;
            mDist += e[4]; sw += e[5]; swi += e[6];
        }
        mOff /= n; mInt /= n;
        double sdOff = Math.sqrt(Math.max(0, mOff2 / n - mOff * mOff)), sdInt = Math.sqrt(Math.max(0, mInt2 / n - mInt * mInt));
        return new double[]{
                Math.min(1, mOff / 30), Math.min(1, sdOff / 30), precise / n, Math.min(1, mSnap / n / 3), Math.min(1, mTurn / n / 30),
                Math.min(1, mInt / 1000), Math.min(1, sdInt / Math.max(1, mInt)), fast / n, Math.min(1, mDist / n / 6),
                sw / n, swi / n, Math.min(1, maxTurn / 90)};
    }

    // ------------------------------------------------------------------ storage: <world>/prideguard/brain/
    static File dir() { File f = new File(DimensionManager.getCurrentSaveRootDirectory(), "prideguard/brain"); f.mkdirs(); return f; }

    static void save(double[] f, int label) {
        StringBuilder sb = new StringBuilder();
        for (double v : f) sb.append(String.format(Locale.ROOT, "%.5f,", v));
        sb.append(label).append('\n');
        try { Files.write(new File(dir(), "data.csv").toPath(), sb.toString().getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE, StandardOpenOption.APPEND); }
        catch (Exception e) { PrideGuard.LOG.warn("[PrideGuard] brain: can't save training data: " + e); }
    }

    public static List<double[]> data() throws Exception {
        List<double[]> out = new ArrayList<>();
        File f = new File(dir(), "data.csv");
        if (!f.isFile()) return out;
        for (String line : Files.readAllLines(f.toPath(), StandardCharsets.UTF_8)) {
            String[] p = line.trim().split(",");
            if (p.length != FEATURES + 1) continue;
            double[] row = new double[FEATURES + 1];
            for (int i = 0; i <= FEATURES; i++) row[i] = Double.parseDouble(p[i]);
            out.add(row);
        }
        return out;
    }

    /** train on everything recorded so far; returns a one-line report for chat */
    public static String train(int epochs) throws Exception {
        List<double[]> d = data();
        int cheat = 0;
        for (double[] r : d) if (r[FEATURES] >= 0.5) cheat++;
        if (cheat < 5 || d.size() - cheat < 5) return String.format("§cNeed at least 5 cheat and 5 legit windows (have %d cheat, %d legit). Record more with /pg brain record.", cheat, d.size() - cheat);
        BrainNet n = new BrainNet(FEATURES, 16, 42);
        double acc = n.train(d, epochs, 0.03, 7);
        net = n;
        Files.write(new File(dir(), "brain.txt").toPath(), n.save().getBytes(StandardCharsets.UTF_8));
        return String.format(Locale.ROOT, "§a✦ Brain trained on %d windows (%d cheat, %d legit): %.0f%% right on held-back examples", d.size(), cheat, d.size() - cheat, acc * 100);
    }

    /** load the saved brain when the world starts */
    public static void loadSaved() {
        net = null;
        try {
            File f = new File(dir(), "brain.txt");
            if (f.isFile()) net = BrainNet.load(new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8));
        } catch (Exception e) { PrideGuard.LOG.warn("[PrideGuard] brain: can't load brain.txt: " + e); }
    }
}
