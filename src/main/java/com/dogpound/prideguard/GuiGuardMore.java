package com.dogpound.prideguard;

import java.util.*;

import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import org.lwjgl.input.Keyboard;

/**
 * More of the labyrinth (her ask 2026-09-28: "the most complex menu you can possibly make, every stat, every option").
 * OptionsPage draws ANY set of options from the server's Opt list — one check, the globals, or all ~400 with search.
 */
final class GuiGuardMore {
    private GuiGuardMore() {}

    // row indexes of Opt.row(): scope,key,group,label,type,value,def,min,max,step,about,choices
    static final int SCOPE = 0, KEY = 1, GROUP = 2, LABEL = 3, TYPE = 4, VALUE = 5, DEF = 6, MIN = 7, MAX = 8, STEP = 9, ABOUT = 10, CHOICES = 11;

    static String num(double v) {
        String s = String.format(Locale.ROOT, "%.6f", v);
        return s.replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    // ================= every option of a scope (or all of them) =================
    static final class OptionsPage extends GuiGuard.Page {
        final String scope, title;           // scope null = every option everywhere
        GuiTextField search, editor;
        String[] editing;                    // the TEXT option being typed into
        boolean onlyChanged;
        final List<Object[]> lines = new ArrayList<>();   // {"group", name} or {"opt", row}
        static final int ROW = 16;

        OptionsPage(String scope, String title) { this.scope = scope; this.title = title; }
        @Override String title() { return title; }

        @Override void init(GuiGuard g) {
            if (!g.s().hasOpts) Net.CH.sendToServer(new Net.Ask(true));
            String keep = search == null ? "" : search.getText();
            search = new GuiTextField(9000, g.mc.fontRenderer, g.cx + 1, g.cy + 1, Math.min(220, g.cw / 3), 12);
            search.setText(keep);
            search.setFocused(scope == null && editing == null);
            search.setMaxStringLength(60);
            int bx = g.cx + g.cw;
            g.add(new GuiGuard.Btn(9001, bx - 96, g.cy, 96, 14, "↺ Reset page", 0xFF8E2424).on(() -> {
                if (scope != null) g.send("opt." + scope + ".*reset", "");
                else for (String sc : scopes(g)) g.send("opt." + sc + ".*reset", "");
            }));
            g.add(new GuiGuard.Btn(9002, bx - 200, g.cy, 100, 14, onlyChanged ? "● Changed only" : "○ Changed only", onlyChanged ? 0xFF732982 : 0xFF2A2238).on(() -> { onlyChanged = !onlyChanged; scroll = 0; g.initGui(); }));
        }

        Set<String> scopes(GuiGuard g) { Set<String> s = new LinkedHashSet<>(); for (String[] o : g.s().opts) s.add(o[SCOPE]); return s; }

        String scopeName(GuiGuard g, String sc) {
            if (sc.equals("global")) return "Global";
            String[] c = g.checkRow(sc);
            return c == null ? sc : c[1];
        }

        void build(GuiGuard g) {
            lines.clear();
            String q = search == null ? "" : search.getText().trim().toLowerCase(Locale.ROOT);
            String lastHead = null;
            for (String[] o : g.s().opts) {
                if (scope != null && !o[SCOPE].equals(scope)) continue;
                if (onlyChanged && o[VALUE].equals(o[DEF])) continue;
                if (!q.isEmpty() && !(o[LABEL] + " " + o[ABOUT] + " " + o[KEY] + " " + o[GROUP] + " " + scopeName(g, o[SCOPE])).toLowerCase(Locale.ROOT).contains(q)) continue;
                String head = (scope == null ? scopeName(g, o[SCOPE]) + " › " : "") + o[GROUP];
                if (!head.equals(lastHead)) { lines.add(new Object[]{"group", head}); lastHead = head; }
                lines.add(new Object[]{"opt", o});
            }
        }

        int top(GuiGuard g) { return g.cy + 18; }
        int visible(GuiGuard g) { return (g.cy + g.ch - top(g)) / ROW; }

        @Override void draw(GuiGuard g, int mx, int my) {
            if (g.s().opts.isEmpty()) { g.drawCenteredString(g.font(), "§7loading every setting…", g.cx + g.cw / 2, g.cy + 40, 0xFFFFFF); return; }
            build(g);
            search.drawTextBox();
            if (search.getText().isEmpty() && !search.isFocused()) g.drawString(g.font(), "§8⌕ search settings…", g.cx + 5, g.cy + 3, 0xFFFFFF);
            int changed = 0, total = 0;
            for (String[] o : g.s().opts) if (scope == null || o[SCOPE].equals(scope)) { total++; if (!o[VALUE].equals(o[DEF])) changed++; }
            g.drawString(g.font(), "§7" + total + " settings · §d" + changed + " changed", search.x + search.width + 8, g.cy + 3, 0xFFFFFF);

            int vis = visible(g);
            scroll = Math.max(0, Math.min(scroll, Math.max(0, lines.size() - vis)));
            int y0 = top(g);
            g.drawRect(g.cx, y0, g.cx + g.cw, g.cy + g.ch, 0x50000000);
            for (int i = 0; i < vis && scroll + i < lines.size(); i++) {
                Object[] l = lines.get(scroll + i);
                int y = y0 + i * ROW;
                if (l[0].equals("group")) {
                    g.drawRect(g.cx, y + ROW - 2, g.cx + g.cw, y + ROW - 1, 0x40F5A9B8);
                    g.drawString(g.font(), "§d§l" + l[1], g.cx + 4, y + 5, 0xFFFFFF);
                    continue;
                }
                String[] o = (String[]) l[1];
                boolean hover = mx >= g.cx && mx < g.cx + g.cw && my >= y && my < y + ROW;
                if (hover) g.drawRect(g.cx, y, g.cx + g.cw, y + ROW, 0x20FFFFFF);
                boolean ch = !o[VALUE].equals(o[DEF]);
                g.drawString(g.font(), (ch ? "§d● " : "§8○ ") + "§f" + g.font().trimStringToWidth(o[LABEL], g.cw - 250), g.cx + 8, y + 4, 0xFFFFFF);
                drawControl(g, o, y, mx, my);
            }
            if (lines.size() > vis) {
                int track = g.cy + g.ch - y0, knob = Math.max(10, track * vis / lines.size());
                int ky = y0 + (track - knob) * scroll / Math.max(1, lines.size() - vis);
                g.drawRect(g.cx + g.cw - 3, y0, g.cx + g.cw, g.cy + g.ch, 0x30FFFFFF);
                g.drawRect(g.cx + g.cw - 3, ky, g.cx + g.cw, ky + knob, GuiGuard.PINK);
            }
            if (lines.isEmpty()) g.drawCenteredString(g.font(), "§8no setting matches", g.cx + g.cw / 2, y0 + 20, 0xFFFFFF);
            if (editing != null) editor.drawTextBox();
        }

        // the control column, right side: [↺] [ − ] value [ + ]  /  [ ON ]  /  [ choice ▸ ]  /  [ text… ]
        int ctrlX(GuiGuard g) { return g.cx + g.cw - 226; }

        void drawControl(GuiGuard g, String[] o, int y, int mx, int my) {
            int x = ctrlX(g), w = 200;
            boolean ch = !o[VALUE].equals(o[DEF]);
            button(g, x + w + 4, y + 2, 14, 12, "↺", ch ? 0xFF8E2424 : 0xFF222222, mx, my);
            switch (o[TYPE]) {
                case "BOOL": {
                    boolean on = Boolean.parseBoolean(o[VALUE]);
                    button(g, x + w - 60, y + 2, 60, 12, on ? "ON" : "OFF", on ? 0xFF008026 : 0xFF444444, mx, my);
                    break;
                }
                case "INT": case "NUM": {
                    double v = Double.parseDouble(o[VALUE]), mn = Double.parseDouble(o[MIN]), mxv = Double.parseDouble(o[MAX]);
                    button(g, x, y + 2, 14, 12, "−", 0xFF2A2238, mx, my);
                    button(g, x + w - 14, y + 2, 14, 12, "+", 0xFF2A2238, mx, my);
                    int bx = x + 18, bw = w - 36;
                    g.drawRect(bx, y + 11, bx + bw, y + 13, 0x40FFFFFF);
                    double frac = mxv > mn ? (v - mn) / (mxv - mn) : 0;
                    g.drawRect(bx, y + 11, bx + (int) (bw * Math.max(0, Math.min(1, frac))), y + 13, GuiGuard.PINK);
                    g.drawCenteredString(g.font(), "§f" + num(v), bx + bw / 2, y + 2, 0xFFFFFF);
                    break;
                }
                case "CHOICE":
                    button(g, x, y + 2, w, 12, o[VALUE] + "  ▸", 0xFF24408E, mx, my);
                    break;
                default:
                    if (editing != null && editing[SCOPE].equals(o[SCOPE]) && editing[KEY].equals(o[KEY])) {
                        editor.x = x; editor.y = y + 2; editor.width = w;
                    } else button(g, x, y + 2, w, 12, g.font().trimStringToWidth(o[VALUE].isEmpty() ? "§8(empty) click to type" : o[VALUE], w - 8), 0xFF2A2238, mx, my);
            }
        }

        void button(GuiGuard g, int x, int y, int w, int h, String text, int color, int mx, int my) {
            boolean over = mx >= x && mx < x + w && my >= y && my < y + h;
            g.drawRect(x, y, x + w, y + h, over ? GuiGuard.Btn.brighten(color) : color);
            g.drawCenteredString(g.font(), text, x + w / 2, y + (h - 8) / 2, 0xFFFFFF);
        }

        @Override void click(GuiGuard g, int mx, int my) {
            search.mouseClicked(mx, my, 0);
            if (editing != null) {
                if (mx >= editor.x && mx < editor.x + editor.width && my >= editor.y && my < editor.y + editor.height) { editor.mouseClicked(mx, my, 0); return; }
                commit(g);
            }
            int y0 = top(g);
            if (my < y0 || my >= g.cy + g.ch) return;
            int i = scroll + (my - y0) / ROW;
            if (i >= lines.size() || !lines.get(i)[0].equals("opt")) return;
            String[] o = (String[]) lines.get(i)[1];
            int y = y0 + (i - scroll) * ROW, x = ctrlX(g), w = 200;
            if (my < y + 2 || my >= y + 14) return;
            String key = "opt." + o[SCOPE] + "." + o[KEY];
            if (mx >= x + w + 4 && mx < x + w + 18) { g.send(key, "*default"); click(g); return; }
            boolean shift = Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT);
            switch (o[TYPE]) {
                case "BOOL":
                    if (mx >= x + w - 60 && mx < x + w) { flip(o, String.valueOf(!Boolean.parseBoolean(o[VALUE]))); g.send(key, o[VALUE]); click(g); }
                    break;
                case "INT": case "NUM": {
                    double v = Double.parseDouble(o[VALUE]), st = Double.parseDouble(o[STEP]) * (shift ? 10 : 1);
                    double mn = Double.parseDouble(o[MIN]), mxv = Double.parseDouble(o[MAX]);
                    Double nv = null;
                    if (mx >= x && mx < x + 14) nv = v - st;
                    else if (mx >= x + w - 14 && mx < x + w) nv = v + st;
                    else if (mx >= x + 18 && mx < x + w - 18) {          // click on the bar: jump there, snapped to the step
                        double frac = (mx - (x + 18)) / (double) (w - 36);
                        nv = mn + Math.round(frac * (mxv - mn) / Double.parseDouble(o[STEP])) * Double.parseDouble(o[STEP]);
                    }
                    if (nv != null) {
                        nv = Math.max(mn, Math.min(mxv, nv));
                        String out = o[TYPE].equals("INT") ? String.valueOf(Math.round(nv)) : num(nv);
                        flip(o, out); g.send(key, out); click(g);
                    }
                    break;
                }
                case "CHOICE":
                    if (mx >= x && mx < x + w) {
                        String[] cs = o[CHOICES].split("\\|");
                        int k = Arrays.asList(cs).indexOf(o[VALUE]);
                        String nv = cs[(k + (shift ? cs.length - 1 : 1)) % cs.length];
                        flip(o, nv); g.send(key, nv); click(g);
                    }
                    break;
                default:
                    if (mx >= x && mx < x + w) {
                        editing = o;
                        editor = new GuiTextField(9003, g.mc.fontRenderer, x, y + 2, w, 12);
                        editor.setMaxStringLength(200);
                        editor.setText(o[VALUE]);
                        editor.setFocused(true);
                        search.setFocused(false);
                    }
            }
        }

        /** show the change at once; the server's reply confirms (or corrects) it */
        static void flip(String[] o, String v) { o[VALUE] = v; }

        static void click(GuiGuard g) {
            g.mc.getSoundHandler().playSound(net.minecraft.client.audio.PositionedSoundRecord.getMasterRecord(net.minecraft.init.SoundEvents.UI_BUTTON_CLICK, 1f));
        }

        void commit(GuiGuard g) {
            if (editing == null) return;
            if (!editor.getText().equals(editing[VALUE])) { flip(editing, editor.getText()); g.send("opt." + editing[SCOPE] + "." + editing[KEY], editor.getText()); }
            editing = null;
        }

        @Override boolean key(GuiGuard g, char c, int k) {
            if (editing != null) {
                if (k == Keyboard.KEY_RETURN || k == Keyboard.KEY_NUMPADENTER) { commit(g); return true; }
                if (k == Keyboard.KEY_ESCAPE) { editing = null; return true; }
                editor.textboxKeyTyped(c, k);
                return true;
            }
            if (search.isFocused()) {
                if (k == Keyboard.KEY_ESCAPE) { search.setFocused(false); return true; }
                search.textboxKeyTyped(c, k);
                scroll = 0;
                return true;
            }
            return false;
        }

        @Override void tick(GuiGuard g) { search.updateCursorCounter(); if (editing != null) editor.updateCursorCounter(); }

        @Override void tooltip(GuiGuard g, int mx, int my) {
            int y0 = top(g);
            if (my < y0 || my >= g.cy + g.ch || mx < g.cx || mx >= ctrlX(g) - 4) return;
            int i = scroll + (my - y0) / ROW;
            if (i >= lines.size() || !lines.get(i)[0].equals("opt")) return;
            String[] o = (String[]) lines.get(i)[1];
            List<String> tip = new ArrayList<>();
            tip.add("§f" + o[LABEL] + (scope == null ? " §8(" + scopeName(g, o[SCOPE]) + ")" : ""));
            tip.addAll(g.font().listFormattedStringToWidth("§7" + o[ABOUT], 260));
            String range = o[TYPE].equals("INT") || o[TYPE].equals("NUM") ? " §8· range " + o[MIN] + " – " + o[MAX] + " · step " + o[STEP] + " (shift ×10)" : o[TYPE].equals("CHOICE") ? " §8· " + o[CHOICES].replace("|", ", ") + " (shift = back)" : "";
            tip.add("§8default §f" + (o[DEF].isEmpty() ? "(empty)" : o[DEF]) + range);
            tip.add("§8/pg set " + o[SCOPE] + "." + o[KEY] + " <value>");
            g.drawHoveringText(tip, mx, my);
        }
    }

    // ================= where the server's time goes =================
    static final class Performance extends GuiGuard.ListPage {
        @Override String title() { return "Performance"; }
        @Override int top(GuiGuard g) { return g.cy + 24; }
        @Override List<String[]> rows(GuiGuard g) {
            List<String[]> r = new ArrayList<>(g.s().checks);
            r.sort((a, b) -> Double.compare(Double.parseDouble(b[13]), Double.parseDouble(a[13])));
            return r;
        }
        @Override void draw(GuiGuard g, int mx, int my) {
            double total = 0;
            for (String[] c : g.s().checks) total += Double.parseDouble(c[13]);
            String[] sv = g.s().server.isEmpty() ? null : g.s().server.get(0);
            g.drawString(g.font(), "§7Time each check has used since the server started §8(all players) §7· total §f" + num(total) + " ms"
                + (sv == null ? "" : " §7· server tick §f" + sv[1] + " ms §7· TPS §f" + sv[0]), g.cx, g.cy, 0xFFFFFF);
            g.drawString(g.font(), "§8check                         µs per run        runs          total ms", g.cx + 4, g.cy + 12, 0xFFFFFF);
            super.draw(g, mx, my);
        }
        @Override void row(GuiGuard g, String[] c, int x, int y, int w, boolean hover) {
            double max = 0.001;
            for (String[] k : g.s().checks) max = Math.max(max, Double.parseDouble(k[13]));
            g.drawString(g.font(), "§f" + c[1], x, y, 0xFFFFFF);
            g.drawString(g.font(), "§7" + c[11], x + 150, y, 0xFFFFFF);
            g.drawString(g.font(), "§8" + c[12], x + 240, y, 0xFFFFFF);
            g.drawString(g.font(), "§f" + c[13], x + 330, y, 0xFFFFFF);
            g.bar(x + 400, y + 1, Math.max(10, w - 400), 7, Double.parseDouble(c[13]) / max, GuiGuard.catColor(c[2]));
        }
        @Override void open(GuiGuard g, String[] r) { g.go(new GuiGuard.CheckPage(r[0])); }
    }

    // ================= who changed what =================
    static final class Audit extends GuiGuard.ListPage {
        @Override String title() { return "Audit Log"; }
        @Override List<String[]> rows(GuiGuard g) { return g.s().audit; }
        @Override String empty() { return "no settings changed since the server started"; }
        @Override void row(GuiGuard g, String[] a, int x, int y, int w, boolean hover) {
            String when = new java.text.SimpleDateFormat("MMM d HH:mm:ss").format(new Date(Long.parseLong(a[0])));
            g.drawString(g.font(), "§8" + when + " §d" + a[1] + " §7" + g.font().trimStringToWidth(a[2], w - 200), x, y, 0xFFFFFF);
        }
        @Override void open(GuiGuard g, String[] r) {}
    }

    // ================= the server itself =================
    static final class Server extends GuiGuard.Page {
        @Override String title() { return "Server"; }
        @Override void draw(GuiGuard g, int mx, int my) {
            if (g.s().server.isEmpty()) return;
            String[] s = g.s().server.get(0);
            double tps = Double.parseDouble(s[0]), mem = Double.parseDouble(s[6]), max = Math.max(1, Double.parseDouble(s[5]));
            String[][] cards = {{"TPS", s[0]}, {"Tick", s[1] + " ms"}, {"Online", s[2]}, {"Uptime", Long.parseLong(s[4]) / 60 + "h " + Long.parseLong(s[4]) % 60 + "m"}, {"Flags in memory", s[3]}};
            int cw = (g.cw - 4 * 6) / 5;
            for (int i = 0; i < cards.length; i++) {
                int x = g.cx + i * (cw + 6);
                g.card(x, g.cy, cw, 34, GuiGuard.RAINBOW[i]);
                g.drawString(g.font(), "§7" + cards[i][0], x + 4, g.cy + 5, 0xFFFFFF);
                g.drawString(g.font(), "§l" + cards[i][1], x + 4, g.cy + 19, 0xFFFFFF);
            }
            int y = g.cy + 48;
            g.drawString(g.font(), "§fTicks per second §8(20 = perfect)", g.cx, y, 0xFFFFFF);
            g.bar(g.cx + 160, y + 1, g.cw - 160, 8, tps / 20, tps >= 18 ? 0xFF3AD27F : tps >= 14 ? 0xFFFFD700 : 0xFFE40303);
            y += 16;
            g.drawString(g.font(), "§fMemory §8" + (int) mem + " / " + (int) max + " MB", g.cx, y, 0xFFFFFF);
            g.bar(g.cx + 160, y + 1, g.cw - 160, 8, mem / max, mem / max < 0.75 ? GuiGuard.BLUE : 0xFFFF8C00);
            y += 16;
            g.drawString(g.font(), "§fSafe Mode " + (g.s().safeMode ? "§aON §8(nothing is blocked or kicked)" : "§cOFF §8(checks enforce their modes)"), g.cx, y, 0xFFFFFF);
            y += 14;
            int on = 0, block = 0;
            for (String[] c : g.s().checks) { if (!c[4].equals("OFF")) on++; if (c[4].equals("CANCEL") || c[4].equals("KICK")) block++; }
            g.drawString(g.font(), "§7" + on + " of " + g.s().checks.size() + " checks running · " + block + " set to block or kick · " + g.s().approved.size() + " approved movement sources", g.cx, y, 0xFFFFFF);
            // flags by hour of day, every check together
            int[] hours = new int[24];
            for (String[] c : g.s().checks) { String[] h = c[15].split(","); for (int i = 0; i < 24 && i < h.length; i++) hours[i] += Integer.parseInt(h[i]); }
            chart(g, "Flags by hour of the day §8(since start, all checks)", hours, g.cx, y + 16, g.cw, g.cy + g.ch - y - 18);
        }
    }

    /** a 24-bar chart with hour labels */
    static void chart(GuiGuard g, String title, int[] v, int x, int y, int w, int h) {
        if (h < 30) return;
        g.drawString(g.font(), "§f" + title, x, y, 0xFFFFFF);
        int mm = 1;
        for (int n : v) mm = Math.max(mm, n);
        int bw = w / v.length, base = y + h - 10;
        int now = java.time.LocalTime.now().getHour();
        for (int i = 0; i < v.length; i++) {
            int bh = (int) ((h - 24) * v[i] / (double) mm);
            g.drawRect(x + i * bw, base - bh, x + i * bw + bw - 2, base, v[i] == 0 ? 0x30FFFFFF : i == now ? GuiGuard.BLUE : GuiGuard.PINK);
            if (i % 3 == 0) g.drawString(g.font(), "§8" + i, x + i * bw, base + 2, 0xFFFFFF);
        }
    }
}
