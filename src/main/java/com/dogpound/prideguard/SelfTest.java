package com.dogpound.prideguard;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.entity.passive.EntityPig;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.play.client.CPacketAnimation;
import net.minecraft.network.play.client.CPacketPlayer;
import net.minecraft.network.play.client.CPacketUseEntity;
import net.minecraft.util.EnumHand;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/**
 * Dev check (only with a file "prideguard-selftest" in the game folder = the throwaway test world): feeds the
 * checks the packets a cheat client would send — as the real test player — and requires each one to be caught,
 * after first requiring that ordinary play produced NO flags. Logs PRIDEGUARD PASS / FAIL.
 */
public final class SelfTest {
    private static EntityPlayerMP who;
    private static int at;

    static void register() { MinecraftForge.EVENT_BUS.register(new SelfTest()); }

    @SubscribeEvent
    public void joined(PlayerEvent.PlayerLoggedInEvent e) {
        if (who == null && e.player instanceof EntityPlayerMP && new File("prideguard-selftest").isFile()) { who = (EntityPlayerMP) e.player; at = Guard.tick + 100; }
    }

    @SubscribeEvent
    public void tick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || who == null || Guard.tick < at) return;
        EntityPlayerMP p = who;
        who = null;
        List<String> fails = new ArrayList<>();
        String optErr = Opt.selfTest();
        if (optErr != null) fails.add("options engine: " + optErr);
        String ladderErr = ladderTest(p);
        if (ladderErr != null) fails.add("punishment ladder: " + ladderErr);
        PlayerData d = Guard.data(p);
        // 1. ordinary play (the other tests walking around, opening inventories) must not have flagged anything
        int legit = 0;
        StringBuilder which = new StringBuilder();
        for (Check c : Guard.CHECKS) if (d.flags[c.index] > 0) { legit += d.flags[c.index]; which.append(c.name).append(' '); }
        if (legit > 0) fails.add("normal play flagged " + legit + "x: " + which.toString().trim());

        boolean safe = Guard.safeMode;
        Mode[] modes = new Mode[Guard.CHECKS.size()];
        for (Check c : Guard.CHECKS) { modes[c.index] = c.mode; c.mode = Mode.WATCH; }
        // the test player is usually a creative OP who just joined: switch off every "who it ignores" rule for the
        // test (2026-09-29: reach/timer/speed/fly "missed" only because the test world was creative), restore after
        Object[][] saved = new Object[Guard.CHECKS.size()][];
        for (Check c : Guard.CHECKS) {
            saved[c.index] = new Object[]{c.ignoreCreative, c.ignoreSpectator, c.ignoreOps, c.ignoreTrusted, c.joinGraceSec,
                    c.maxPingMs, c.minTps, new java.util.HashSet<>(c.offDims)};
            c.ignoreCreative = c.ignoreSpectator = c.ignoreOps = c.ignoreTrusted = false;
            c.joinGraceSec = 0; c.maxPingMs = 0; c.minTps = 0; c.offDims.clear();
        }
        Guard.safeMode = true;
        double x = p.posX, y = p.posY, z = p.posZ;
        List<EntityPig> pigs = new ArrayList<>();
        try {
            // Reach: a pig 5.5 blocks away, hit 3 times
            EntityPig far = pig(p, x + 5.5, y, z, pigs);
            expect(fails, d, "reach", () -> { for (int i = 0; i < 3; i++) { Guard.in(p, attack(far)); Guard.in(p, new CPacketAnimation(EnumHand.MAIN_HAND)); } });
            // Bad Packets: looking 120° down
            expect(fails, d, "badpackets", () -> Guard.in(p, new CPacketPlayer.Rotation(p.rotationYaw, 120f, true)));
            // Kill Aura: two pigs in one tick, and hits without a swing
            EntityPig a = pig(p, x + 1.5, y, z, pigs), b = pig(p, x - 1.5, y, z, pigs);
            expect(fails, d, "killaura", () -> {
                for (int i = 0; i < 4; i++) {
                    Guard.lagUntilTick = 0;
                    Guard.in(p, attack(a)); Guard.in(p, attack(b)); Guard.in(p, new CPacketPlayer(true));
                }
            });
            // Timer: three bursts of 30 movement packets at once (1.5 s of game time in no time at all)
            expect(fails, d, "timer", () -> {
                for (int burst = 0; burst < 4; burst++) { Guard.lagUntilTick = 0; d.lastTeleportTick = -1000; for (int i = 0; i < 30; i++) Guard.in(p, new CPacketPlayer(true)); }
            });
            // Speed: 2 blocks a tick along the ground
            expect(fails, d, "speed", () -> {
                d.lastTeleportTick = -1000; d.hasLast = false;
                Guard.in(p, new CPacketPlayer.Position(x, y, z, true));
                for (int i = 1; i <= 12; i++) { Guard.lagUntilTick = 0; d.lastTeleportTick = -1000; Guard.in(p, new CPacketPlayer.Position(x + i * 2.0, y, z, true)); }
            });
            // Fly: hovering 6 blocks up for 3 seconds
            expect(fails, d, "fly", () -> {
                d.lastTeleportTick = -1000; d.hasLast = false;
                for (int i = 0; i < 60; i++) { Guard.lagUntilTick = 0; d.lastTeleportTick = -1000; Guard.in(p, new CPacketPlayer.Position(x, y + 6, z + i * 0.01, false)); }
            });
            // Step: a staircase of 2-block steps with no jump, a few normal moves between them (step hacks)
            expect(fails, d, "step", () -> {
                Guard.debugCheck = "step";
                d.lastTeleportTick = -1000; d.hasLast = false;
                Guard.in(p, new CPacketPlayer.Position(x, y, z, true));
                for (int i = 1; i <= 3; i++) {
                    Guard.lagUntilTick = 0;
                    Guard.in(p, new CPacketPlayer.Position(x + i * 1.0, y + i * 2.0, z, true));
                    for (int k = 1; k <= 4; k++) Guard.in(p, new CPacketPlayer.Position(x + i * 1.0 + k * 0.2, y + i * 2.0, z, true));
                }
            });
            Guard.debugCheck = "";
            // No Fall: claiming to stand on the ground while dropping 6 blocks up in the air
            expect(fails, d, "groundspoof", () -> {
                d.lastTeleportTick = -1000; d.hasLast = false;
                for (int i = 0; i < 30; i++) { Guard.lagUntilTick = 0; Guard.in(p, new CPacketPlayer.Position(x, y + 8 - i * 0.1, z + 3, true)); }
            });
        } catch (Throwable t) {
            fails.add("exception " + t);
            t.printStackTrace();
        } finally {
            for (EntityPig pg : pigs) pg.setDead();
            for (Check c : Guard.CHECKS) {
                c.mode = modes[c.index];
                Object[] o = saved[c.index];
                c.ignoreCreative = (Boolean) o[0]; c.ignoreSpectator = (Boolean) o[1]; c.ignoreOps = (Boolean) o[2];
                c.ignoreTrusted = (Boolean) o[3]; c.joinGraceSec = (Integer) o[4]; c.maxPingMs = (Integer) o[5];
                c.minTps = (Double) o[6]; c.offDims.clear(); @SuppressWarnings("unchecked") java.util.Set<String> dims = (java.util.Set<String>) o[7]; c.offDims.addAll(dims);
            }
            Guard.safeMode = safe;
            java.util.Arrays.fill(d.vl, 0); java.util.Arrays.fill(d.buffer, 0);
            Guard.teleported(p);
            p.connection.setPlayerLocation(x, y, z, p.rotationYaw, 0);
        }
        System.out.println(fails.isEmpty() ? "[PrideGuard] PRIDEGUARD PASS: normal play clean; caught reach, bad packets, kill aura, timer, speed, fly, step, no fall"
            : "[PrideGuard] PRIDEGUARD FAIL: " + fails);
    }

    private static CPacketUseEntity attack(net.minecraft.entity.Entity t) { return new CPacketUseEntity(t); }

    private static EntityPig pig(EntityPlayerMP p, double x, double y, double z, List<EntityPig> keep) {
        EntityPig g = new EntityPig(p.world);
        g.setPosition(x, y, z);
        g.setNoAI(true);
        p.world.spawnEntity(g);
        keep.add(g);
        return g;
    }

    /** the repeat-offender ladder on a pretend player (never the real one): 3 kicks -> temp-ban -> pardon */
    private static String ladderTest(EntityPlayerMP real) {
        Opt on = Opt.get("ladder", "tempbanEnabled"), at = Opt.get("ladder", "tempbanAtKicks"), en = Opt.get("ladder", "enabled");
        String was = on.raw(), wasAt = at.raw(), wasEn = en.raw();
        java.util.UUID fake = java.util.UUID.nameUUIDFromBytes("prideguard-selftest-ladder".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        PlayerData d = new PlayerData(fake);
        try {
            on.set("true"); at.set("3"); en.set("true");
            Check c = Guard.CHECKS.get(0);
            String r1 = Escalation.onKick(d, c), r2 = Escalation.onKick(d, c);
            if (r1 != null || r2 != null) return "banned before the 3rd kick";
            if (Escalation.warning(fake) == null) return "no warning after 2 kicks";
            if (Escalation.onKick(d, c) == null) return "3rd kick didn't temp-ban";
            if (Escalation.joinCheck(fake) == null) return "temp-banned player could still join";
            Escalation.pardon(fake);
            if (Escalation.joinCheck(fake) != null || Escalation.recentKicks(fake) != 0) return "pardon didn't clear it";
            return null;
        } catch (Throwable t) {
            return "exception " + t;
        } finally {
            Escalation.pardon(fake);
            on.set(was); at.set(wasAt); en.set(wasEn);
        }
    }

    private static void expect(List<String> fails, PlayerData d, String checkId, Runnable cheat) {
        Check c = Guard.check(checkId);
        int before = d.flags[c.index];
        cheat.run();
        if (d.flags[c.index] <= before) fails.add(c.name + " missed it");
    }
}
