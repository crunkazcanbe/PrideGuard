package com.dogpound.prideguard;

import java.util.*;

/**
 * One setting of the labyrinth menu (her ask 2026-09-28: "every stat, every option, every setting you can have").
 * Checks and Guard declare their options once; config loading/saving, the network snapshot, the menu's option
 * pages, /pg and validation all come from this one list — so adding an option is one line.
 * scope = "global" or a check id. Values travel as strings and are clamped/validated here.
 */
public final class Opt {
    public enum Type { BOOL, INT, NUM, CHOICE, TEXT }

    public static final List<Opt> ALL = new ArrayList<>();
    private static final Map<String, Opt> BY_KEY = new HashMap<>();

    public final String scope, key, group, label, about, def;
    public final Type type;
    public final double min, max, step;
    public final String[] choices;
    private String value;
    private Runnable onChange;

    private Opt(String scope, String key, String group, String label, Type type, String def, double min, double max, double step, String[] choices, String about) {
        this.scope = scope; this.key = key; this.group = group; this.label = label; this.type = type;
        this.def = def; this.min = min; this.max = max; this.step = step; this.choices = choices; this.about = about;
        this.value = def;
        ALL.add(this);
        BY_KEY.put(scope + "." + key, this);
    }

    // ---- declaring ----
    public static Opt bool(String scope, String key, String group, String label, boolean def, String about) {
        return new Opt(scope, key, group, label, Type.BOOL, String.valueOf(def), 0, 1, 1, null, about);
    }
    public static Opt integer(String scope, String key, String group, String label, int def, int min, int max, int step, String about) {
        return new Opt(scope, key, group, label, Type.INT, String.valueOf(def), min, max, step, null, about);
    }
    public static Opt num(String scope, String key, String group, String label, double def, double min, double max, double step, String about) {
        return new Opt(scope, key, group, label, Type.NUM, fmt(def), min, max, step, null, about);
    }
    public static Opt choice(String scope, String key, String group, String label, String def, String[] choices, String about) {
        return new Opt(scope, key, group, label, Type.CHOICE, def, 0, choices.length - 1, 1, choices, about);
    }
    public static Opt text(String scope, String key, String group, String label, String def, String about) {
        return new Opt(scope, key, group, label, Type.TEXT, def, 0, 0, 0, null, about);
    }

    /** runs after every change (and after loading), e.g. to copy the value into a field a hot loop reads */
    public Opt onChange(Runnable r) { onChange = r; r.run(); return this; }

    public static Opt get(String scope, String key) { return BY_KEY.get(scope + "." + key); }

    // ---- reading ----
    public String raw() { return value; }
    public boolean on() { return Boolean.parseBoolean(value); }
    public int i() { return (int) Math.round(Double.parseDouble(value)); }
    public double d() { return Double.parseDouble(value); }
    public boolean changed() { return !value.equals(def); }

    // ---- writing: returns null when fine, or what's wrong ----
    public String set(String v) {
        String clean;
        try {
            switch (type) {
                case BOOL:
                    if (!v.equalsIgnoreCase("true") && !v.equalsIgnoreCase("false")) return "on/off only";
                    clean = String.valueOf(Boolean.parseBoolean(v));
                    break;
                case INT:
                    clean = String.valueOf((int) Math.round(clamp(Double.parseDouble(v))));
                    break;
                case NUM:
                    clean = fmt(clamp(Double.parseDouble(v)));
                    break;
                case CHOICE:
                    clean = null;
                    for (String c : choices) if (c.equalsIgnoreCase(v)) clean = c;
                    if (clean == null) return "one of " + String.join(", ", choices);
                    break;
                default:
                    clean = v.length() > 200 ? v.substring(0, 200) : v;
            }
        } catch (NumberFormatException e) {
            return "not a number: " + v;
        }
        value = clean;
        if (onChange != null) onChange.run();
        return null;
    }

    public void reset() { set(def); }

    private double clamp(double x) { return Math.max(min, Math.min(max, x)); }

    static String fmt(double x) {
        String s = String.format(java.util.Locale.ROOT, "%.6f", x);
        return s.contains(".") ? s.replaceAll("0+$", "").replaceAll("\\.$", "") : s;
    }

    /** menu row: scope,key,group,label,type,value,def,min,max,step,about,choices */
    String[] row() {
        return new String[]{scope, key, group, label, type.name(), value, def, fmt(min), fmt(max), fmt(step), about, choices == null ? "" : String.join("|", choices)};
    }

    /** the comma list inside a TEXT option, trimmed, empties dropped */
    public Set<String> list() {
        Set<String> out = new HashSet<>();
        for (String s : value.split(",")) if (!s.trim().isEmpty()) out.add(s.trim());
        return out;
    }

    /** self-check, run from SelfTest: clamping, choices, bad input */
    static String selfTest() {
        Opt n = new Opt("test", "n", "t", "n", Type.NUM, "1", 0, 5, 0.5, null, "");
        Opt c = new Opt("test", "c", "t", "c", Type.CHOICE, "A", 0, 1, 1, new String[]{"A", "B"}, "");
        Opt b = new Opt("test", "b", "t", "b", Type.BOOL, "false", 0, 1, 1, null, "");
        ALL.removeIf(o -> o.scope.equals("test"));
        BY_KEY.keySet().removeIf(k -> k.startsWith("test."));
        if (n.set("9") != null || n.d() != 5) return "num clamp high";
        if (n.set("-3") != null || n.d() != 0) return "num clamp low";
        if (n.set("x") == null) return "num accepted text";
        if (c.set("b") != null || !c.raw().equals("B")) return "choice case";
        if (c.set("Z") == null) return "choice accepted junk";
        if (b.set("yes") == null) return "bool accepted yes";
        if (b.set("TRUE") != null || !b.on()) return "bool parse";
        return null;
    }
}
