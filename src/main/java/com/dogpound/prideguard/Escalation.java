package com.dogpound.prideguard;


import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Per-player punishment ladder (2026-09-29, written by coder30, reviewed by Claude): kicks are remembered
 *  for windowHours; repeat offenders get a warning on join, and — only if an admin switches it on — a temp-ban. */
public final class Escalation {
    private static final Map<UUID, List<Long>> KICKS = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> BANNED_UNTIL = new ConcurrentHashMap<>();
    private static File file;
    
    private static Opt enabled;
    private static Opt windowHours;
    private static Opt warnAtKicks;
    private static Opt tempbanAtKicks;
    private static Opt tempbanMinutes;
    private static Opt tempbanEnabled;
    private static Opt tempbanMessage;
    
    public static void declare() {
        String g = "Punishment Ladder";
        enabled = Opt.bool("ladder", "enabled", g, "Escalate repeat offenders", true, "escalate by how often a player has been kicked");
        windowHours = Opt.integer("ladder", "windowHours", g, "Remember kicks for (hours)", 24, 1, 720, 1, "kicks older than this are forgiven");
        warnAtKicks = Opt.integer("ladder", "warnAtKicks", g, "Warn on join after (kicks)", 1, 0, 50, 1, "after this many kicks: a stern warning when they join (0 = never)");
        tempbanAtKicks = Opt.integer("ladder", "tempbanAtKicks", g, "Temp-ban after (kicks)", 3, 0, 50, 1, "after this many kicks in the window: temp-ban (0 = never)");
        tempbanMinutes = Opt.integer("ladder", "tempbanMinutes", g, "Temp-ban length (minutes)", 30, 1, 10080, 5, "how long a temp-ban lasts");
        tempbanEnabled = Opt.bool("ladder", "tempbanEnabled", g, "Allow automatic temp-bans", false, "OFF by default: bans stay a human decision unless you switch this on");
        tempbanMessage = Opt.text("ladder", "tempbanMessage", g, "Temp-ban message", "§cTemporarily banned by PrideGuard for {minutes} min ({kicks} kicks in {hours} h).", "what a temp-banned player sees. {minutes} {kicks} {hours} are filled in");
    }
    
    /** record a kick; returns the temp-ban message if this kick earned a temp-ban, else null (Guard.kick disconnects once) */
    public static String onKick(PlayerData d, Check c) {
        long now = System.currentTimeMillis();
        KICKS.computeIfAbsent(d.uuid, k -> Collections.synchronizedList(new ArrayList<>())).add(now);
        
        // Remove old kicks
        long windowMs = windowHours.i() * 3600000L;
        KICKS.computeIfPresent(d.uuid, (uuid, kicks) -> {
            kicks.removeIf(kickTime -> now - kickTime > windowMs);
            return kicks;
        });
        
        save();
        
        if (enabled.on() && tempbanEnabled.on() && tempbanAtKicks.i() > 0) {
            int recentCount = recentKicks(d.uuid);
            if (recentCount >= tempbanAtKicks.i()) {
                long until = now + tempbanMinutes.i() * 60000L;
                BANNED_UNTIL.put(d.uuid, until);
                save();
                PrideGuard.LOG.info("Tempbanned player {} until {} for {} kicks", d.uuid, until, recentCount);
                
                return tempbanMessage.raw()
                        .replace("{minutes}", String.valueOf(tempbanMinutes.i()))
                        .replace("{kicks}", String.valueOf(recentCount))
                        .replace("{hours}", String.valueOf(windowHours.i()));
            }
        }
        return null;
    }

    public static String joinCheck(UUID id) {
        Long bannedUntil = BANNED_UNTIL.get(id);
        if (bannedUntil != null) {
            long now = System.currentTimeMillis();
            if (now < bannedUntil) {
                long minutes = (bannedUntil - now + 59999) / 60000; // Round up
                return "§cYou are banned for " + minutes + " more minutes.";
            } else {
                BANNED_UNTIL.remove(id);
                save();
            }
        }
        return null;
    }
    
    public static String warning(UUID id) {
        if (enabled.on() && warnAtKicks.i() > 0) {
            int recentCount = recentKicks(id);
            if (recentCount >= warnAtKicks.i()) {
                return "§6✦ PrideGuard: you've been kicked " + recentCount + " time(s) in the last " + windowHours.i() + " hours. Play fair or a ban is next.";
            }
        }
        return null;
    }
    
    public static int recentKicks(UUID id) {
        List<Long> kicks = KICKS.get(id);
        if (kicks == null || kicks.isEmpty()) {
            return 0;
        }
        
        long now = System.currentTimeMillis();
        long windowMs = windowHours.i() * 3600000L;
        int count = 0;
        for (Long kickTime : kicks) {
            if (now - kickTime <= windowMs) {
                count++;
            }
        }
        return count;
    }
    
    public static long bannedUntil(UUID id) {
        Long until = BANNED_UNTIL.get(id);
        return until != null ? until : 0;
    }
    
    public static void pardon(UUID id) {
        KICKS.remove(id);
        BANNED_UNTIL.remove(id);
        save();
    }
    
    public static void load(File f) {
        file = f;
        KICKS.clear();
        BANNED_UNTIL.clear();
        
        if (!f.exists()) {
            return;
        }
        
        try (BufferedReader reader = Files.newBufferedReader(f.toPath(), StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(" ");
                if (parts.length < 3) continue;
                
                try {
                    UUID uuid = UUID.fromString(parts[0]);
                    String type = parts[1];
                    if ("kick".equals(type)) {
                        long time = Long.parseLong(parts[2]);
                        KICKS.computeIfAbsent(uuid, k -> Collections.synchronizedList(new ArrayList<>())).add(time);
                    } else if ("ban".equals(type)) {
                        long until = Long.parseLong(parts[2]);
                        BANNED_UNTIL.put(uuid, until);
                    }
                } catch (Exception e) {
                    PrideGuard.LOG.warn("Failed to parse line: {}", line);
                }
            }
        } catch (IOException e) {
            PrideGuard.LOG.warn("Failed to load escalation data", e);
        }
    }
    
    public static void save() {
        if (file == null) return;
        
        try {
            List<String> lines = new ArrayList<>();
            for (Map.Entry<UUID, List<Long>> entry : KICKS.entrySet()) {
                for (Long kickTime : entry.getValue()) {
                    lines.add(entry.getKey() + " kick " + kickTime);
                }
            }
            for (Map.Entry<UUID, Long> entry : BANNED_UNTIL.entrySet()) {
                lines.add(entry.getKey() + " ban " + entry.getValue());
            }
            
            Files.write(file.toPath(), lines, StandardCharsets.UTF_8);
        } catch (IOException e) {
            PrideGuard.LOG.warn("Failed to save escalation data", e);
        }
    }
}
