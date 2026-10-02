package com.dogpound.prideguard;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * The cheat brain's neural network: a tiny multilayer perceptron (inputs → one tanh hidden layer → sigmoid)
 * written from scratch, so PrideGuard needs no AI library or outside program. It learns from labelled windows
 * of combat numbers (1 = cheating, 0 = human) and answers "how cheat-like is this?" from 0 to 1.
 * Pure Java on purpose: `java BrainNet.java` runs {@link #main} as its own self-check.
 */
public final class BrainNet {
    public final int in, hidden;
    final double[][] w1;   // [hidden][in]
    final double[] b1, w2; // [hidden]
    double b2;
    public int trainedOn;  // examples in the last training run (0 = untrained)

    public BrainNet(int in, int hidden, long seed) {
        this.in = in; this.hidden = hidden;
        w1 = new double[hidden][in]; b1 = new double[hidden]; w2 = new double[hidden];
        Random r = new Random(seed);
        double s1 = 1 / Math.sqrt(in), s2 = 1 / Math.sqrt(hidden);
        for (int h = 0; h < hidden; h++) { for (int i = 0; i < in; i++) w1[h][i] = r.nextGaussian() * s1; w2[h] = r.nextGaussian() * s2; }
    }

    /** 0 = looks human, 1 = looks like a cheat */
    public double score(double[] x) {
        double z = b2;
        for (int h = 0; h < hidden; h++) z += w2[h] * Math.tanh(pre(h, x));
        return 1 / (1 + Math.exp(-z));
    }

    private double pre(int h, double[] x) {
        double s = b1[h];
        for (int i = 0; i < in; i++) s += w1[h][i] * x[i];
        return s;
    }

    /** plain SGD on log-loss; examples are {features..., label}. Returns accuracy on a held-back 20%. */
    public double train(List<double[]> examples, int epochs, double rate, long seed) {
        List<double[]> all = new ArrayList<>(examples);
        Collections.shuffle(all, new Random(seed));
        int cut = Math.max(1, (int) (all.size() * 0.8));
        List<double[]> tr = all.subList(0, cut), te = all.size() > cut ? all.subList(cut, all.size()) : all.subList(0, cut);
        Random r = new Random(seed + 1);
        double[] a = new double[hidden];
        for (int e = 0; e < epochs; e++) {
            List<double[]> order = new ArrayList<>(tr);
            Collections.shuffle(order, r);
            for (double[] ex : order) {
                double z = b2;
                for (int h = 0; h < hidden; h++) { a[h] = Math.tanh(pre(h, ex)); z += w2[h] * a[h]; }
                double g = 1 / (1 + Math.exp(-z)) - ex[in];          // d loss / d z
                for (int h = 0; h < hidden; h++) {
                    double gh = g * w2[h] * (1 - a[h] * a[h]);
                    w2[h] -= rate * g * a[h];
                    b1[h] -= rate * gh;
                    for (int i = 0; i < in; i++) w1[h][i] -= rate * gh * ex[i];
                }
                b2 -= rate * g;
            }
        }
        trainedOn = tr.size();
        int ok = 0;
        for (double[] ex : te) if ((score(ex) >= 0.5) == (ex[in] >= 0.5)) ok++;
        return (double) ok / te.size();
    }

    // ------------------------------------------------------------------ save / load (one line of numbers per row)
    public String save() {
        StringBuilder sb = new StringBuilder();
        sb.append(in).append(' ').append(hidden).append(' ').append(trainedOn).append('\n');
        for (double[] row : w1) sb.append(join(row)).append('\n');
        sb.append(join(b1)).append('\n').append(join(w2)).append('\n').append(b2).append('\n');
        return sb.toString();
    }

    public static BrainNet load(String text) {
        String[] l = text.trim().split("\n");
        String[] head = l[0].trim().split(" ");
        BrainNet n = new BrainNet(Integer.parseInt(head[0]), Integer.parseInt(head[1]), 0);
        n.trainedOn = Integer.parseInt(head[2]);
        for (int h = 0; h < n.hidden; h++) n.w1[h] = nums(l[1 + h], n.in);
        double[] b1 = nums(l[1 + n.hidden], n.hidden), w2 = nums(l[2 + n.hidden], n.hidden);
        System.arraycopy(b1, 0, n.b1, 0, n.hidden);
        System.arraycopy(w2, 0, n.w2, 0, n.hidden);
        n.b2 = Double.parseDouble(l[3 + n.hidden].trim());
        return n;
    }

    private static String join(double[] v) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < v.length; i++) sb.append(i == 0 ? "" : " ").append(String.format(Locale.ROOT, "%.6g", v[i]));
        return sb.toString();
    }

    private static double[] nums(String line, int n) {
        String[] p = line.trim().split(" ");
        if (p.length != n) throw new IllegalArgumentException("expected " + n + " numbers, got " + p.length);
        double[] v = new double[n];
        for (int i = 0; i < n; i++) v[i] = Double.parseDouble(p[i]);
        return v;
    }

    /** self-check: learns a made-up "cheaters aim too perfectly" pattern, survives save/load */
    public static void main(String[] args) {
        Random r = new Random(1);
        List<double[]> ex = new ArrayList<>();
        for (int k = 0; k < 600; k++) {
            boolean cheat = k % 2 == 0;
            double[] x = new double[4];
            x[0] = cheat ? r.nextDouble() * 0.2 : 0.3 + r.nextDouble() * 0.7;   // aim error: tiny for cheats
            x[1] = cheat ? r.nextDouble() * 0.15 : 0.2 + r.nextDouble() * 0.6;  // click-timing wobble: tiny for cheats
            x[2] = r.nextDouble(); x[3] = r.nextDouble();                        // noise
            double[] row = new double[5];
            System.arraycopy(x, 0, row, 0, 4);
            row[4] = cheat ? 1 : 0;
            ex.add(row);
        }
        BrainNet n = new BrainNet(4, 6, 42);
        double acc = n.train(ex, 60, 0.05, 7);
        BrainNet m = load(n.save());
        double[] probe = {0.05, 0.05, 0.5, 0.5};
        if (acc < 0.95) throw new AssertionError("accuracy " + acc);
        if (Math.abs(m.score(probe) - n.score(probe)) > 1e-4) throw new AssertionError("save/load changed the answer");
        if (m.score(probe) < 0.8 || m.score(new double[]{0.8, 0.6, 0.5, 0.5}) > 0.2) throw new AssertionError("wrong answers");
        System.out.printf(Locale.ROOT, "BrainNet OK: accuracy %.3f, cheat-like probe %.2f%n", acc, m.score(probe));
    }
}
