package com.dogpound.prideguard;

import java.io.IOException;
import java.util.*;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

/**
 * The PrideGuard labyrinth (her ask 2026-09-28: "very complex, tons of options, menus everything"). A stack of
 * pages you dive into — Hub → Dashboard / Live Feed → a flag / Players → a player → their flags / Checks → a
 * category → a check / Punishment Ladder / Movement Sources / Settings / Help — with breadcrumbs and Back.
 */
public class GuiGuard extends GuiScreen {
    static final int[] RAINBOW = {0xFFE40303, 0xFFFF8C00, 0xFFFFED00, 0xFF008026, 0xFF24408E, 0xFF732982, 0xFF5BCEFA, 0xFFF5A9B8};
    static final int PINK = 0xFFF5A9B8, BLUE = 0xFF5BCEFA, DIM = 0xFF8A8499;
    private static final Deque<Page> STACK = new ArrayDeque<>();   // remembered between openings

    int px, py, pw, ph, cx, cy, cw, ch;   // panel, and the content area inside it
    private int refresh;

    public GuiGuard() { if (STACK.isEmpty()) STACK.push(new Hub()); }

    Net.Snapshot s() { return ClientProxy.snapshot; }
    Page page() { return STACK.peek(); }
    void go(Page p) { STACK.push(p); initGui(); }
    void back() { if (STACK.size() > 1) { STACK.pop(); initGui(); } else mc.displayGuiScreen(null); }
    void send(String key, String value) { Net.CH.sendToServer(new Net.Set(key, value)); }
    net.minecraft.client.gui.FontRenderer font() { return fontRenderer; }

    @Override
    public void initGui() {
        Keyboard.enableRepeatEvents(true);
        pw = Math.max(Math.min(width - 16, 480), Math.min(width - 24, 1100)); ph = Math.max(Math.min(height - 16, 290), Math.min(height - 24, 620));
        px = (width - pw) / 2; py = (height - ph) / 2;
        cx = px + 10; cy = py + 40; cw = pw - 20; ch = ph - 50;
        buttonList.clear();
        buttonList.add(new Btn(-1, px + 8, py + 8, 44, 14, STACK.size() > 1 ? "◀ Back" : "✕ Close", 0xFF2A2238));
        if (s() == null) Net.CH.sendToServer(new Net.Ask());
        else page().init(this);
    }

    @Override
    public void updateScreen() {
        int every = s() == null ? 40 : Math.max(1, s().refreshSec) * 20;
        if (++refresh % every == 0) Net.CH.sendToServer(new Net.Ask());   // live (Settings › Menu › refresh)
        if (s() != null) page().tick(this);
        if (s() != null && buttonList.size() == 1) initGui();          // first data arrived
    }

    @Override public boolean doesGuiPauseGame() { return false; }
    @Override public void onGuiClosed() { Keyboard.enableRepeatEvents(false); }

    @Override
    protected void actionPerformed(GuiButton b) {
        if (b.id == -1) { back(); return; }
        if (b instanceof Btn && ((Btn) b).run != null) { ((Btn) b).run.run(); return; }
        page().button(this, b);
    }

    @Override
    protected void keyTyped(char c, int key) throws IOException {
        if (s() != null && page().key(this, c, key)) return;          // a search box or text field has the keyboard
        if (key == Keyboard.KEY_BACK) { back(); return; }
        super.keyTyped(c, key);
    }

    @Override
    protected void mouseClicked(int mx, int my, int button) throws IOException {
        super.mouseClicked(mx, my, button);
        if (button == 1) { back(); return; }
        if (s() != null) page().click(this, mx, my);
    }

    @Override
    protected void mouseClickMove(int mx, int my, int button, long held) { if (s() != null) page().drag(this, mx, my); }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int w = Mouse.getEventDWheel();
        if (w != 0 && s() != null) page().scroll(w > 0 ? -1 : 1);
    }

    @Override
    public void drawScreen(int mx, int my, float pt) {
        drawDefaultBackground();
        drawGradientRect(px, py, px + pw, py + ph, 0xF2140E22, 0xF2080510);
        int sw = pw / RAINBOW.length;
        for (int i = 0; i < RAINBOW.length; i++) drawRect(px + i * sw, py, i == RAINBOW.length - 1 ? px + pw : px + (i + 1) * sw, py + 3, RAINBOW[i]);
        // breadcrumbs
        List<String> crumbs = new ArrayList<>();
        for (Iterator<Page> it = STACK.descendingIterator(); it.hasNext(); ) crumbs.add(it.next().title());
        String badge = s() == null ? "" : s().safeMode ? "§a✚ SAFE MODE" : "§c⚔ ENFORCING";
        int room = pw - 58 - 10 - fontRenderer.getStringWidth(badge) - 12;             // never run into the badge
        // drop whole crumbs from the left ("… › ") until the path fits; the page you're on always shows
        int from = 0;
        String path;
        while (true) {
            path = "§l✦ PrideGuard §r§8" + (from > 0 ? "… › " : "") + String.join(" › ", crumbs.subList(from, crumbs.size() - 1))
                    + (crumbs.size() - 1 > from ? " › " : "") + "§f" + crumbs.get(crumbs.size() - 1);
            if (fontRenderer.getStringWidth(path) <= room || from >= crumbs.size() - 1) break;
            from++;
        }
        drawString(fontRenderer, fontRenderer.trimStringToWidth(path, room), px + 58, py + 11, 0xFFFFFF);
        if (!badge.isEmpty()) drawString(fontRenderer, badge, px + pw - 10 - fontRenderer.getStringWidth(badge), py + 11, 0xFFFFFF);
        drawRect(px + 6, py + 27, px + pw - 6, py + 28, 0x40FFFFFF);
        if (s() == null) drawCenteredString(fontRenderer, "§7asking the server… §8(only ops can open this)", px + pw / 2, py + ph / 2, 0xFFFFFF);
        else page().draw(this, mx, my);
        super.drawScreen(mx, my, pt);
        if (s() != null) { page().over(this, mx, my); page().tooltip(this, mx, my); }
    }

    // ================= helpers =================
    static final class Btn extends GuiButton {
        final int color;
        Runnable run;
        Btn(int id, int x, int y, int w, int h, String text, int color) { super(id, x, y, w, h, text); this.color = color; }
        Btn on(Runnable r) { run = r; return this; }
        @Override
        public void drawButton(net.minecraft.client.Minecraft mc, int mx, int my, float pt) {
            if (!visible) return;
            hovered = mx >= x && my >= y && mx < x + width && my < y + height;
            drawRect(x, y, x + width, y + height, enabled ? (hovered ? brighten(color) : color) : 0xFF222222);
            drawRect(x, y + height - 1, x + width, y + height, 0x60000000);
            if (hovered && enabled) drawRect(x, y, x + width, y + 1, 0x80FFFFFF);
            drawCenteredString(mc.fontRenderer, displayString, x + width / 2, y + (height - 8) / 2, enabled ? 0xFFFFFF : 0x777777);
        }
        static int brighten(int c) { int r = Math.min(255, ((c >> 16) & 255) + 40), g = Math.min(255, ((c >> 8) & 255) + 40), b = Math.min(255, (c & 255) + 40); return 0xFF000000 | (r << 16) | (g << 8) | b; }
    }

    Btn add(Btn b) { buttonList.add(b); return b; }

    void card(int x, int y, int w, int h, int edge) {
        drawRect(x, y, x + w, y + h, 0x70000000);
        drawRect(x, y, x + w, y + 1, edge);
    }

    void bar(int x, int y, int w, int h, double frac, int color) {
        drawRect(x, y, x + w, y + h, 0x40FFFFFF);
        drawRect(x, y, x + (int) Math.round(w * Math.max(0, Math.min(1, frac))), y + h, color);
    }

    static String ago(long t) {
        long s = (System.currentTimeMillis() - t) / 1000;
        return s < 60 ? s + "s" : s < 3600 ? s / 60 + "m" : s / 3600 + "h";
    }

    static int modeColor(String m) { try { return Mode.valueOf(m).color; } catch (Exception e) { return DIM; } }
    static String modeLabel(String m) { try { return Mode.valueOf(m).label; } catch (Exception e) { return m; } }
    static int catColor(String c) { return c.equals("Combat") ? 0xFFE40303 : c.equals("World") ? 0xFF008026 : c.equals("Movement") ? 0xFF5BCEFA : c.equals("Protocol") ? 0xFF732982 : DIM; }

    String[] checkRow(String id) { for (String[] c : s().checks) if (c[0].equals(id)) return c; return null; }
    String[] playerRow(String name) { for (String[] p : s().players) if (p[0].equals(name)) return p; return null; }
    int checkIndex(String name) { for (int i = 0; i < s().checks.size(); i++) if (s().checks.get(i)[1].equals(name)) return i; return -1; }

    // ================= pages =================
    abstract static class Page {
        int scroll;
        abstract String title();
        void init(GuiGuard g) {}
        abstract void draw(GuiGuard g, int mx, int my);
        void button(GuiGuard g, GuiButton b) {}
        void click(GuiGuard g, int mx, int my) {}
        void drag(GuiGuard g, int mx, int my) {}
        void scroll(int d) { scroll = Math.max(0, scroll + d); }
        void tooltip(GuiGuard g, int mx, int my) {}
        boolean key(GuiGuard g, char c, int k) { return false; }
        /** drawn after the buttons (labels that sit ON a button) */
        void over(GuiGuard g, int mx, int my) {}
        void tick(GuiGuard g) {}
    }

    /** a scrolling list of rows; click one to dive in */
    abstract static class ListPage extends Page {
        static final int ROW = 14;
        abstract List<String[]> rows(GuiGuard g);
        abstract void row(GuiGuard g, String[] r, int x, int y, int w, boolean hover);
        abstract void open(GuiGuard g, String[] r);
        int top(GuiGuard g) { return g.cy; }
        String empty() { return "nothing here yet"; }
        @Override void draw(GuiGuard g, int mx, int my) {
            List<String[]> rows = rows(g);
            int y0 = top(g), visible = (g.cy + g.ch - y0) / ROW;
            scroll = Math.min(scroll, Math.max(0, rows.size() - visible));
            g.drawRect(g.cx, y0, g.cx + g.cw, g.cy + g.ch, 0x50000000);
            for (int i = 0; i < visible && scroll + i < rows.size(); i++) {
                int y = y0 + i * ROW;
                boolean hover = mx >= g.cx && mx < g.cx + g.cw && my >= y && my < y + ROW;
                if (hover) g.drawRect(g.cx, y, g.cx + g.cw, y + ROW, 0x30FFFFFF);
                row(g, rows.get(scroll + i), g.cx + 4, y + 3, g.cw - 8, hover);
            }
            if (rows.isEmpty()) g.drawCenteredString(g.fontRenderer, "§8" + empty(), g.cx + g.cw / 2, y0 + 20, 0xFFFFFF);
            if (rows.size() > visible) g.drawString(g.fontRenderer, "§8" + (scroll + 1) + "–" + Math.min(rows.size(), scroll + visible) + " of " + rows.size() + " · scroll", g.cx + g.cw - 110, g.cy + g.ch + 2, 0xFFFFFF);
        }
        @Override void click(GuiGuard g, int mx, int my) {
            int y0 = top(g);
            if (mx < g.cx || mx >= g.cx + g.cw || my < y0 || my >= g.cy + g.ch) return;
            int i = scroll + (my - y0) / ROW;
            List<String[]> rows = rows(g);
            if (i < rows.size()) open(g, rows.get(i));
        }
    }

    // ---------- Hub ----------
    static final class Hub extends Page {
        static final String[][] TILES = {
            {"≡", "Dashboard", "the whole picture at a glance"},
            {"☰", "Live Feed", "every flag as it happens"},
            {"☺", "Players", "who's online, their ping, trust and violations"},
            {"⌕", "Checks", "every check, by category — tune each one"},
            {"⚖", "Punishment Ladder", "what each category does when it catches someone"},
            {"➤", "Movement Sources", "modded items & vehicles seen in flags (jetpacks, trains…)"},
            {"⚙", "Settings", "every global setting: enforcement, alerts, lag, trust, logging, menu"},
            {"⌕", "All Options", "search every setting of every check in one place"},
            {"⏱", "Performance", "how much server time each check costs"},
            {"☰", "Audit Log", "who changed which setting, and when"},
            {"▣", "Server", "TPS, tick time, memory, uptime, flags by hour"},
            {"❓", "Help", "how PrideGuard works"}};
        @Override String title() { return "Hub"; }
        @Override void init(GuiGuard g) {
            int cols = 4, rowsN = (TILES.length + cols - 1) / cols, tw = (g.cw - (cols - 1) * 8) / cols, th = (g.ch - 26 - (rowsN - 1) * 8) / rowsN;
            for (int i = 0; i < TILES.length; i++) {
                final int k = i;
                g.add(new Btn(100 + i, g.cx + (i % cols) * (tw + 8), g.cy + 26 + (i / cols) * (th + 8), tw, th, "", 0xFF1C1530).on(() -> g.go(page(k))));
            }
        }
        /** s if it fits in w pixels, else cut with a trailing "…" */
        static String fit(GuiGuard g, String s, int w) {
            if (g.fontRenderer.getStringWidth(s) <= w) return s;
            return g.fontRenderer.trimStringToWidth(s, w - g.fontRenderer.getStringWidth("…")).replaceAll("\\s+$", "") + "…";
        }
        static Page page(int k) {
            switch (k) {
                case 0: return new Dashboard();
                case 1: return new Feed(null, null);
                case 2: return new Players();
                case 3: return new Categories();
                case 4: return new Ladder();
                case 5: return new Sources();
                case 6: return new GuiGuardMore.OptionsPage("global", "Settings");
                case 7: return new GuiGuardMore.OptionsPage(null, "All Options");
                case 8: return new GuiGuardMore.Performance();
                case 9: return new GuiGuardMore.Audit();
                case 10: return new GuiGuardMore.Server();
                default: return new Help();
            }
        }
        @Override void draw(GuiGuard g, int mx, int my) {
            Net.Snapshot s = g.s();
            int flags10 = 0;
            for (String[] f : s.feed) if (System.currentTimeMillis() - Long.parseLong(f[0]) < 600_000) flags10++;
            g.drawString(g.fontRenderer, "§7" + s.players.size() + " online · §f" + flags10 + "§7 flags in the last 10 min · " + s.checks.size() + " checks watching", g.cx, g.cy + 6, 0xFFFFFF);
        }
        @Override void over(GuiGuard g, int mx, int my) {
            for (GuiButton b : g.buttonList) {
                if (b.id < 100 || b.id >= 100 + TILES.length) continue;
                String[] t = TILES[b.id - 100];
                int c = RAINBOW[(b.id - 100) % RAINBOW.length];
                g.drawRect(b.x, b.y, b.x + b.width, b.y + 2, c);
                int ty = b.y + Math.max(4, (b.height - 50) / 2);
                g.drawCenteredString(g.fontRenderer, t[0], b.x + b.width / 2, ty, 0xFFFFFF);
                // title: bold if it fits, plain if not, "…" only as a last resort (2026-09-29: long titles ran into the next tile)
                String title = g.fontRenderer.getStringWidth("§l" + t[1]) <= b.width - 6 ? "§l" + t[1] : fit(g, t[1], b.width - 6);
                g.drawCenteredString(g.fontRenderer, title, b.x + b.width / 2, ty + 12, 0xFFFFFF);
                List<String> lines = g.fontRenderer.listFormattedStringToWidth(t[2], b.width - 8);
                int room = 0;
                while (room < lines.size() && room < 3 && ty + 26 + room * 10 < b.y + b.height - 8) room++;
                for (int i = 0; i < room; i++) {
                    String l = lines.get(i);
                    if (i == room - 1 && room < lines.size()) l = fit(g, l + "…", b.width - 8);   // more text than room: say so
                    g.drawCenteredString(g.fontRenderer, "§7" + l, b.x + b.width / 2, ty + 26 + i * 10, 0xFFFFFF);
                }
            }
        }
    }

    // ---------- Dashboard ----------
    static final class Dashboard extends Page {
        @Override String title() { return "Dashboard"; }
        @Override void draw(GuiGuard g, int mx, int my) {
            Net.Snapshot s = g.s();
            long total = 0, cancels = 0;
            for (String[] c : s.checks) { total += Long.parseLong(c[9]); cancels += Long.parseLong(c[10]); }
            int hour = 0;
            Map<String, Integer> byPlayer = new HashMap<>();
            for (String[] f : s.feed) { if (System.currentTimeMillis() - Long.parseLong(f[0]) < 3_600_000) hour++; byPlayer.merge(f[1], 1, Integer::sum); }
            String worst = byPlayer.entrySet().stream().max(Map.Entry.comparingByValue()).map(e -> e.getKey() + " (" + e.getValue() + ")").orElse("nobody ♥");
            String[][] cards = {{"Online", s.players.size() + ""}, {"Flags since start", total + ""}, {"Last hour", hour + ""}, {"Stopped", cancels + ""}, {"Most flagged", worst}};
            int cw = (g.cw - 4 * 6) / 5;
            for (int i = 0; i < cards.length; i++) {
                int x = g.cx + i * (cw + 6);
                g.card(x, g.cy, cw, 34, RAINBOW[i]);
                g.drawString(g.fontRenderer, "§7" + cards[i][0], x + 4, g.cy + 5, 0xFFFFFF);
                g.drawString(g.fontRenderer, "§l" + g.fontRenderer.trimStringToWidth(cards[i][1], cw - 8), x + 4, g.cy + 19, 0xFFFFFF);
            }
            // flags per check
            g.drawString(g.fontRenderer, "§fFlags by check", g.cx, g.cy + 44, 0xFFFFFF);
            long max = 1;
            for (String[] c : s.checks) max = Math.max(max, Long.parseLong(c[9]));
            int y = g.cy + 56;
            for (String[] c : s.checks) {
                g.drawString(g.fontRenderer, "§7" + c[1], g.cx, y, 0xFFFFFF);
                g.bar(g.cx + 120, y + 1, g.cw - 200, 7, Long.parseLong(c[9]) / (double) max, catColor(c[2]));
                g.drawString(g.fontRenderer, "§f" + c[9] + " §8" + modeLabel(c[4]), g.cx + g.cw - 74, y, 0xFFFFFF);
                y += 11;
            }
            // flags per minute, last 30 min
            int by = y + 8, bh = g.cy + g.ch - by - 10;
            if (bh > 20) {
                int[] mins = new int[30];
                for (String[] f : s.feed) { int m = (int) ((System.currentTimeMillis() - Long.parseLong(f[0])) / 60000); if (m < 30) mins[29 - m]++; }
                int mm = 1;
                for (int v : mins) mm = Math.max(mm, v);
                g.drawString(g.fontRenderer, "§fFlags per minute §8(last 30 min)", g.cx, by - 2, 0xFFFFFF);
                int bw = (g.cw) / 30;
                for (int i = 0; i < 30; i++) {
                    int h = (int) ((bh - 10) * mins[i] / (double) mm);
                    g.drawRect(g.cx + i * bw, by + bh - h, g.cx + i * bw + bw - 1, by + bh, mins[i] == 0 ? 0x30FFFFFF : PINK);
                }
            }
        }
    }

    // ---------- Live Feed ----------
    static class Feed extends ListPage {
        final String player, category;
        Feed(String player, String category) { this.player = player; this.category = category; }
        @Override String title() { return player != null ? player + "'s flags" : category != null ? category + " flags" : "Live Feed"; }
        @Override void init(GuiGuard g) {
            if (player != null) return;
            String[] cats = {"All", "Combat", "World", "Movement", "Protocol"};
            for (int i = 0; i < cats.length; i++) {
                final String c = i == 0 ? null : cats[i];
                boolean on = Objects.equals(c, category);
                g.add(new Btn(200 + i, g.cx + i * 72, g.cy, 68, 13, cats[i], on ? 0xFF732982 : 0xFF2A2238).on(() -> { STACK.pop(); g.go(new Feed(null, c)); }));
            }
        }
        @Override int top(GuiGuard g) { return player != null ? g.cy : g.cy + 18; }
        @Override List<String[]> rows(GuiGuard g) {
            List<String[]> out = new ArrayList<>();
            for (String[] f : g.s().feed) if ((player == null || f[1].equals(player)) && (category == null || f[3].equals(category))) out.add(f);
            return out;
        }
        @Override String empty() { return "no flags — everyone's playing fair ♥"; }
        @Override void row(GuiGuard g, String[] f, int x, int y, int w, boolean hover) {
            g.drawRect(x - 4, y - 3, x - 2, y + 11, catColor(f[3]));
            String line = "§8" + ago(Long.parseLong(f[0])) + " §f" + f[1] + " §c" + f[2] + " §7" + f[4];
            g.drawString(g.fontRenderer, g.fontRenderer.trimStringToWidth(line, w - 90), x, y, 0xFFFFFF);
            g.drawString(g.fontRenderer, "§8VL §f" + f[5] + " §8" + f[6] + "ms", x + w - 84, y, 0xFFFFFF);
        }
        @Override void open(GuiGuard g, String[] r) { g.go(new FlagPage(r)); }
    }

    static final class FlagPage extends Page {
        final String[] f;
        FlagPage(String[] f) { this.f = f; }
        @Override String title() { return f[2]; }
        @Override void init(GuiGuard g) {
            int y = g.cy + g.ch - 18;
            g.add(new Btn(300, g.cx, y, 110, 16, "☺ " + f[1] + "'s page", 0xFF24408E).on(() -> g.go(new PlayerPage(f[1]))));
            g.add(new Btn(301, g.cx + 116, y, 110, 16, "⌕ The check", 0xFF732982).on(() -> { int i = g.checkIndex(f[2]); if (i >= 0) g.go(new CheckPage(g.s().checks.get(i)[0])); }));
            g.add(new Btn(302, g.cx + 232, y, 110, 16, "⚑ Teleport there", 0xFF008026).on(() -> { g.mc.player.sendChatMessage("/tp " + f[9] + " " + f[10] + " " + f[11]); g.mc.displayGuiScreen(null); }));
        }
        @Override void draw(GuiGuard g, int mx, int my) {
            String[][] lines = {
                {"Player", f[1] + " §8(trust " + f[14] + ")"}, {"Check", f[2] + " §8(" + f[3] + ")"}, {"What happened", f[4]},
                {"Violation level", f[5]}, {"Ping", f[6] + " ms"}, {"Server TPS", f[15]}, {"Action", f[7]},
                {"Where", "dim " + f[8] + " · " + f[9] + " " + f[10] + " " + f[11]}, {"Holding", f[12]}, {"Riding", f[13]},
                {"When", new java.text.SimpleDateFormat("MMM d HH:mm:ss").format(new Date(Long.parseLong(f[0]))) + " §8(" + ago(Long.parseLong(f[0])) + " ago)"}};
            g.card(g.cx, g.cy, g.cw, lines.length * 13 + 8, catColor(f[3]));
            for (int i = 0; i < lines.length; i++) {
                g.drawString(g.fontRenderer, "§7" + lines[i][0], g.cx + 6, g.cy + 6 + i * 13, 0xFFFFFF);
                g.drawString(g.fontRenderer, "§f" + lines[i][1], g.cx + 110, g.cy + 6 + i * 13, 0xFFFFFF);
            }
        }
    }

    // ---------- Players ----------
    static final class Players extends ListPage {
        @Override String title() { return "Players"; }
        @Override List<String[]> rows(GuiGuard g) {
            List<String[]> r = new ArrayList<>(g.s().players);
            r.sort((a, b) -> Double.compare(Double.parseDouble(b[3]), Double.parseDouble(a[3])));
            return r;
        }
        @Override void row(GuiGuard g, String[] p, int x, int y, int w, boolean hover) {
            int tc = p[4].equals("green") ? 0xFF3AD27F : p[4].equals("yellow") ? 0xFFFFD700 : p[4].equals("orange") ? 0xFFFF8C00 : 0xFFE40303;
            g.drawRect(x, y + 1, x + 6, y + 7, tc);
            g.drawString(g.fontRenderer, "§f" + p[0], x + 10, y, 0xFFFFFF);
            g.drawString(g.fontRenderer, "§8" + p[2] + " ms · trust " + p[4] + (Long.parseLong(p[8]) > 0 ? " · §bexempt" : ""), x + 120, y, 0xFFFFFF);
            g.drawString(g.fontRenderer, "§7VL §f" + p[3] + " §8" + p[5], x + w - 150, y, 0xFFFFFF);
        }
        @Override void open(GuiGuard g, String[] r) { g.go(new PlayerPage(r[0])); }
        @Override String empty() { return "nobody online"; }
    }

    static final class PlayerPage extends Page {
        final String name;
        boolean confirmKick;
        PlayerPage(String name) { this.name = name; }
        @Override String title() { return name; }
        @Override void init(GuiGuard g) {
            int y = g.cy + g.ch - 18, w = (g.cw - 5 * 6) / 6;
            g.add(new Btn(400, g.cx, y, w, 16, "↺ Reset VL", 0xFF24408E).on(() -> g.send("player." + name + ".reset", "")));
            g.add(new Btn(401, g.cx + (w + 6), y, w, 16, "Exempt 10m", 0xFF2A6A8E).on(() -> g.send("player." + name + ".exempt", "10")));
            g.add(new Btn(402, g.cx + 2 * (w + 6), y, w, 16, "Exempt 1h", 0xFF2A6A8E).on(() -> g.send("player." + name + ".exempt", "60")));
            g.add(new Btn(403, g.cx + 3 * (w + 6), y, w, 16, "☰ Flags", 0xFF732982).on(() -> g.go(new Feed(name, null))));
            g.add(new Btn(404, g.cx + 4 * (w + 6), y, w, 16, "⚑ Go to", 0xFF008026).on(() -> { g.mc.player.sendChatMessage("/tp " + name); g.mc.displayGuiScreen(null); }));
            g.add(new Btn(405, g.cx + 5 * (w + 6), y, w, 16, confirmKick ? "§lSure? Kick" : "Kick", 0xFFC0392B).on(() -> {
                if (!confirmKick) { confirmKick = true; g.initGui(); } else { g.send("player." + name + ".kick", ""); g.back(); }
            }));
        }
        @Override void draw(GuiGuard g, int mx, int my) {
            String[] p = g.playerRow(name);
            if (p == null) { g.drawCenteredString(g.fontRenderer, "§7" + name + " has left", g.cx + g.cw / 2, g.cy + 30, 0xFFFFFF); return; }
            g.drawString(g.fontRenderer, "§f§l" + name + " §r§7ping " + p[2] + " ms · trust §f" + p[4] + " §7· total VL §f" + p[3] + (Long.parseLong(p[8]) > 0 ? " §b· exempt " + p[8] + " s" : ""), g.cx, g.cy, 0xFFFFFF);
            if (p.length > 15) g.drawString(g.fontRenderer, "§8online " + p[9] + " min · " + p[13] + " h played · dim " + p[10] + " · " + p[11] + " · at " + p[12] + " · " + p[15] + " ❤ · " + p[14] + " clicks this second", g.cx, g.cy + 10, 0xFFFFFF);
            String[] vls = p[6].split(","), fl = p[7].split(",");
            double max = 5;
            for (String v : vls) max = Math.max(max, Double.parseDouble(v));
            int y = g.cy + 24;
            for (int i = 0; i < g.s().checks.size() && i < vls.length && y < g.cy + g.ch - 30; i++) {
                String[] c = g.s().checks.get(i);
                g.drawString(g.fontRenderer, "§7" + c[1], g.cx, y, 0xFFFFFF);
                g.bar(g.cx + 130, y + 1, g.cw - 230, 7, Double.parseDouble(vls[i]) / max, catColor(c[2]));
                g.drawString(g.fontRenderer, "§fVL " + vls[i] + " §8" + fl[i] + " flags", g.cx + g.cw - 94, y, 0xFFFFFF);
                y += 12;
            }
        }
    }

    // ---------- Checks ----------
    static final class Categories extends Page {
        static final String[][] CATS = {
            {"Combat", "reach, hit through walls, kill aura, auto clicker — next: aim analysis"},
            {"World", "fast break, nuker, far break/place, no-swing mining, air place — next: scaffold, xray"},
            {"Movement", "speed, fly, step, water walk, timer, no-fall — next: full movement prediction"},
            {"Protocol", "bad packets, floods, crash/dupe exploits, cheat-mod list — next: packet order"}};
        @Override String title() { return "Checks"; }
        @Override void init(GuiGuard g) {
            int th = (g.ch - 3 * 8) / 4;
            for (int i = 0; i < CATS.length; i++) { final String c = CATS[i][0]; g.add(new Btn(500 + i, g.cx, g.cy + i * (th + 8), g.cw, th, "", 0xFF1C1530).on(() -> g.go(new CheckList(c)))); }
        }
        @Override void draw(GuiGuard g, int mx, int my) {}
        @Override void over(GuiGuard g, int mx, int my) {
            for (GuiButton b : g.buttonList) {
                if (b.id < 500 || b.id > 503) continue;
                String[] c = CATS[b.id - 500];
                int n = 0; long f = 0;
                for (String[] k : g.s().checks) if (k[2].equals(c[0])) { n++; f += Long.parseLong(k[9]); }
                g.drawRect(b.x, b.y, b.x + 3, b.y + b.height, catColor(c[0]));
                g.drawString(g.fontRenderer, "§l" + c[0] + " §r§7" + n + " checks · " + f + " flags", b.x + 10, b.y + 6, 0xFFFFFF);
                g.drawString(g.fontRenderer, "§8" + c[1], b.x + 10, b.y + 18, 0xFFFFFF);
            }
        }
    }

    static final class CheckList extends ListPage {
        final String category;
        CheckList(String category) { this.category = category; }
        @Override String title() { return category; }
        @Override List<String[]> rows(GuiGuard g) {
            List<String[]> r = new ArrayList<>();
            for (String[] c : g.s().checks) if (c[2].equals(category)) r.add(c);
            return r;
        }
        @Override void row(GuiGuard g, String[] c, int x, int y, int w, boolean hover) {
            g.drawString(g.fontRenderer, "§f" + c[1], x, y, 0xFFFFFF);
            g.drawRect(x + 150, y - 1, x + 200, y + 9, modeColor(c[4]));
            g.drawCenteredString(g.fontRenderer, modeLabel(c[4]), x + 175, y, 0xFFFFFF);
            g.drawString(g.fontRenderer, "§7sens " + c[5] + " · " + c[9] + " flags", x + 210, y, 0xFFFFFF);
        }
        @Override void open(GuiGuard g, String[] r) { g.go(new CheckPage(r[0])); }
    }

    static final class CheckPage extends Page {
        final String id;
        boolean dragging;
        CheckPage(String id) { this.id = id; }
        @Override String title() { String[] c = null; return id; }
        @Override void init(GuiGuard g) {
            String[] c = g.checkRow(id);
            if (c == null) return;
            int y = g.cy + 34, w = (g.cw - 4 * 4) / 5;
            for (Mode m : Mode.values()) {
                boolean on = m.name().equals(c[4]);
                g.add(new Btn(600 + m.ordinal(), g.cx + m.ordinal() * (w + 4), y, w, 16, (on ? "● " : "") + m.label, on ? m.color : 0xFF2A2238).on(() -> g.send("check." + id + ".mode", m.name())));
            }
            int ry = g.cy + 100;
            addStepper(g, 610, g.cx, ry, "buffer", "In a row", Double.parseDouble(c[6]), 1);
            addStepper(g, 620, g.cx + g.cw / 3, ry, "decay", "Decay /min", Double.parseDouble(c[7]), 0.5);
            addStepper(g, 630, g.cx + 2 * g.cw / 3, ry, "kickAt", "Kick at VL", Double.parseDouble(c[8]), 5);
            g.add(new Btn(641, g.cx + 156, g.cy + g.ch - 18, 170, 16, "⚙ All " + (c.length > 16 ? c[16] : "") + " settings", 0xFF24408E).on(() -> g.go(new GuiGuardMore.OptionsPage(id, c[1] + " settings"))));
            g.add(new Btn(640, g.cx, g.cy + g.ch - 18, 150, 16, "☰ This check's flags", 0xFF732982).on(() -> g.go(new Feed(null, c[2]) {
                @Override String title() { return c[1] + " flags"; }
                @Override List<String[]> rows(GuiGuard gg) { List<String[]> o = new ArrayList<>(); for (String[] f : gg.s().feed) if (f[2].equals(c[1])) o.add(f); return o; }
                @Override void init(GuiGuard gg) {}
                @Override int top(GuiGuard gg) { return gg.cy; }
            })));
        }
        void addStepper(GuiGuard g, int base, int x, int y, String key, String label, double value, double step) {
            g.add(new Btn(base, x + 70, y, 16, 14, "−", 0xFF2A2238).on(() -> g.send("check." + id + "." + key, String.valueOf(Math.max(0, value - step)))));
            g.add(new Btn(base + 1, x + 124, y, 16, 14, "+", 0xFF2A2238).on(() -> g.send("check." + id + "." + key, String.valueOf(value + step))));
        }
        int sliderX(GuiGuard g) { return g.cx + 80; }
        int sliderW(GuiGuard g) { return g.cw - 130; }
        @Override void draw(GuiGuard g, int mx, int my) {
            String[] c = g.checkRow(id);
            if (c == null) return;
            if (g.buttonList.size() <= 1) g.initGui();
            g.drawString(g.fontRenderer, "§l" + c[1] + " §r§8" + c[2], g.cx, g.cy, 0xFFFFFF);
            g.drawString(g.fontRenderer, "§7" + c[3], g.cx, g.cy + 12, 0xFFFFFF);
            g.drawString(g.fontRenderer, "§8Mode — what happens when it catches someone:", g.cx, g.cy + 24, 0xFFFFFF);
            // sensitivity slider
            int sy = g.cy + 62, sx = sliderX(g), sw = sliderW(g), sens = Integer.parseInt(c[5]);
            g.drawString(g.fontRenderer, "§fSensitivity", g.cx, sy, 0xFFFFFF);
            g.drawRect(sx, sy + 3, sx + sw, sy + 5, 0x60FFFFFF);
            int knob = sx + (int) ((sens - 1) / 99.0 * sw);
            g.drawRect(sx, sy + 3, knob, sy + 5, PINK);
            g.drawRect(knob - 3, sy - 1, knob + 3, sy + 9, 0xFFFFFFFF);
            g.drawString(g.fontRenderer, "§f" + sens, sx + sw + 8, sy, 0xFFFFFF);
            g.drawString(g.fontRenderer, "§8forgiving", sx, sy + 11, 0xFFFFFF);
            g.drawString(g.fontRenderer, "§8strict", sx + sw - g.fontRenderer.getStringWidth("strict"), sy + 11, 0xFFFFFF);
            int ry = g.cy + 100;
            String[][] steppers = {{"In a row", c[6]}, {"Decay /min", c[7]}, {"Kick at VL", c[8]}};
            for (int i = 0; i < 3; i++) {
                int x = g.cx + i * g.cw / 3;
                g.drawString(g.fontRenderer, "§7" + steppers[i][0], x, ry + 3, 0xFFFFFF);
                g.drawCenteredString(g.fontRenderer, "§f" + steppers[i][1], x + 105, ry + 3, 0xFFFFFF);
            }
            g.card(g.cx, g.cy + 124, g.cw, 44, catColor(c[2]));
            g.drawString(g.fontRenderer, "§7Flags since start §f" + c[9] + "  §7· stopped §f" + c[10], g.cx + 6, g.cy + 130, 0xFFFFFF);
            try { g.drawString(g.fontRenderer, "§8" + Mode.valueOf(c[4]).label + ": " + Mode.valueOf(c[4]).about, g.cx + 6, g.cy + 142, 0xFFFFFF); } catch (Exception ignored) {}
            if (g.s().safeMode) g.drawString(g.fontRenderer, "§a✚ Safe Mode is on: Block and Kick act like Alert until you switch it off in Settings.", g.cx + 6, g.cy + 154, 0xFFFFFF);
            if (c.length > 15) {
                g.drawString(g.fontRenderer, "§7Players caught §f" + c[14] + "  §7· cost §f" + c[11] + " µs§7 a run, §f" + c[12] + "§7 runs, §f" + c[13] + " ms§7 total", g.cx + 6, g.cy + 176, 0xFFFFFF);
                String[] h = c[15].split(",");
                int[] hours = new int[24];
                for (int i = 0; i < 24 && i < h.length; i++) hours[i] = Integer.parseInt(h[i]);
                GuiGuardMore.chart(g, "Flags by hour of the day", hours, g.cx, g.cy + 192, g.cw, g.cy + g.ch - 22 - (g.cy + 192));
            }
        }
        @Override void click(GuiGuard g, int mx, int my) { dragging = my >= g.cy + 58 && my <= g.cy + 72 && mx >= sliderX(g) - 4 && mx <= sliderX(g) + sliderW(g) + 4; drag(g, mx, my); }
        @Override void drag(GuiGuard g, int mx, int my) {
            if (!dragging) return;
            int v = 1 + (int) Math.round(Math.max(0, Math.min(1, (mx - sliderX(g)) / (double) sliderW(g))) * 99);
            String[] c = g.checkRow(id);
            if (c != null && !c[5].equals(String.valueOf(v))) { c[5] = String.valueOf(v); g.send("check." + id + ".sens", c[5]); }
        }
        @Override void tooltip(GuiGuard g, int mx, int my) {
            for (GuiButton b : g.buttonList) if (b.id >= 600 && b.id < 605 && b.isMouseOver()) g.drawHoveringText(Collections.singletonList("§7" + Mode.values()[b.id - 600].about), mx, my);
        }
    }

    // ---------- Punishment Ladder ----------
    static final class Ladder extends Page {
        @Override String title() { return "Punishment Ladder"; }
        @Override void init(GuiGuard g) {
            int y = g.cy + 36;
            g.add(new Btn(799, g.cx + g.cw - 170, g.cy, 170, 16, "⚙ Repeat-offender ladder", 0xFF732982)
                    .on(() -> g.go(new GuiGuardMore.OptionsPage("ladder", "Repeat Offenders"))));
            for (int ci = 0; ci < Guard.CATEGORIES.size(); ci++) {
                String cat = Guard.CATEGORIES.get(ci);
                for (Mode m : Mode.values())
                    g.add(new Btn(700 + ci * 10 + m.ordinal(), g.cx + 100 + m.ordinal() * 68, y + ci * 26, 64, 16, m.label, m.color).on(() -> g.send("category." + cat + ".mode", m.name())));
            }
        }
        @Override void draw(GuiGuard g, int mx, int my) {
            g.drawString(g.fontRenderer, "§7Set a whole category at once. Each step includes the ones before it:", g.cx, g.cy, 0xFFFFFF);
            g.drawString(g.fontRenderer, "§5Watch §7→ §eAlert staff §7→ §6Block the cheat §7→ §cKick §8(bans stay a human decision)", g.cx, g.cy + 12, 0xFFFFFF);
            int y = g.cy + 36;
            for (int ci = 0; ci < Guard.CATEGORIES.size(); ci++) {
                String cat = Guard.CATEGORIES.get(ci);
                g.drawRect(g.cx, y + ci * 26, g.cx + 3, y + ci * 26 + 16, catColor(cat));
                g.drawString(g.fontRenderer, "§f" + cat, g.cx + 8, y + ci * 26 + 4, 0xFFFFFF);
                StringBuilder now = new StringBuilder();
                for (String[] c : g.s().checks) if (c[2].equals(cat)) now.append(c[1]).append(": ").append(modeLabel(c[4])).append("  ");
                g.drawString(g.fontRenderer, "§8" + g.fontRenderer.trimStringToWidth(now.toString(), g.cw - 100), g.cx + 100, y + ci * 26 + 18, 0xFFFFFF);
            }
        }
    }

    // ---------- Movement Sources (learned from flags, approved with a click) ----------
    static final class Sources extends ListPage {
        @Override String title() { return "Movement Sources"; }
        @Override int top(GuiGuard g) { return g.cy + 26; }
        boolean approved(GuiGuard g, String key) { for (String[] a : g.s().approved) if (a[0].equals(key)) return true; return false; }
        @Override List<String[]> rows(GuiGuard g) {
            Map<String, int[]> m = new HashMap<>();
            Map<String, Set<String>> who = new HashMap<>();
            for (String[] f : g.s().feed) {
                if (f.length < 17 || f[16].isEmpty() || !(f[3].equals("Movement") || f[3].equals("World"))) continue;
                for (String key : f[16].split(",")) {
                    m.computeIfAbsent(key, k -> new int[1])[0]++;
                    who.computeIfAbsent(key, k -> new HashSet<>()).add(f[1]);
                }
            }
            for (String[] a : g.s().approved) if (!m.containsKey(a[0])) { m.put(a[0], new int[1]); who.put(a[0], new HashSet<>()); }
            List<String[]> out = new ArrayList<>();
            m.forEach((k, v) -> out.add(new String[]{k, v[0] + "", who.get(k).size() + ""}));
            out.sort((a, b) -> Integer.parseInt(b[1]) - Integer.parseInt(a[1]));
            return out;
        }
        @Override void draw(GuiGuard g, int mx, int my) {
            g.drawString(g.fontRenderer, "§7Modded gear and vehicles players had when movement checks flagged them. §fClick one to approve it§7 —", g.cx, g.cy, 0xFFFFFF);
            g.drawString(g.fontRenderer, "§7approved = counts as a legit way to fly/run/move. §a● seen with 3+ players§7 = almost certainly the mod, not a cheat.", g.cx, g.cy + 11, 0xFFFFFF);
            super.draw(g, mx, my);
        }
        @Override void row(GuiGuard g, String[] r, int x, int y, int w, boolean hover) {
            boolean ok = approved(g, r[0]), likely = Integer.parseInt(r[2]) >= 3;
            g.drawString(g.fontRenderer, (ok ? "§a✔ " : likely ? "§e● " : "§8● ") + "§f" + r[0], x, y, 0xFFFFFF);
            g.drawString(g.fontRenderer, ok ? "§aapproved §8(click to undo)" : "§7" + r[1] + " flags · " + r[2] + " players §8· click to approve", x + w - 200, y, 0xFFFFFF);
        }
        @Override void open(GuiGuard g, String[] r) { g.send(approved(g, r[0]) ? "source.revoke" : "source.approve", r[0]); }
        @Override String empty() { return "no modded gear in movement flags yet"; }
    }

    // ---------- Help ----------
    static final class Help extends Page {
        static final String[] TEXT = {
            "§fPrideGuard§7 is the DogPound anti-cheat — the first one ever made for a Forge 1.12.2 modpack.",
            "",
            "§dHow a flag works: §7a check notices something a fair game can't do. A few in a row (the buffer) count",
            "§7as one flag, which raises that player's §fviolation level§7. Clean play lowers it again (decay).",
            "",
            "§dModes: §5Watch§7 logs quietly · §eAlert§7 tells staff · §6Block§7 stops the cheat · §cKick§7 at the kick line.",
            "§a✚ Safe Mode§7 keeps everything at Watch/Alert while we learn what normal looks like in this pack.",
            "",
            "§dFair to lag: §7every player gets a silent ping each tick, so reach and speed allow for their real delay.",
            "§dFair to mods: §7real modded block shapes and reach come from the server. Jetpacks, trains and gravity",
            "§7mods show up under Movement Sources — approve them with one click. Server-side mods (AOE tools, Buddy) never flag.",
            "",
            "§dCommands: §f/pg gui · alerts · safe on|off · info <p> · reset <p> · exempt <p> <min> · mode <check> <mode> · set <scope.key> <value>",
            "§dSettings: §7every check has ~20 settings (⚙ on its page); §fAll Options§7 searches all of them. Shift-click ± = 10 steps.",
            "§dComing next: §7full movement prediction, aim analysis, scaffold, ore hiding, xray analytics, replays."};
        @Override String title() { return "Help"; }
        @Override void draw(GuiGuard g, int mx, int my) {
            for (int i = 0; i < TEXT.length; i++) g.drawString(g.fontRenderer, g.fontRenderer.trimStringToWidth(TEXT[i], g.cw), g.cx, g.cy + i * 11, 0xFFFFFF);
        }
    }
}
