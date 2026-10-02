package com.dogpound.prideguard.checks;

import com.dogpound.prideguard.Opt;
import com.dogpound.prideguard.PlayerData;
import net.minecraft.entity.player.EntityPlayerMP;

/** Walking on top of water or lava like it's solid (Jesus hacks): on the surface, not sinking, nothing to stand on. */
public class Jesus extends MoveCheck {
    public Jesus() { super("jesus", "Water Walk", "walking on water or lava like it's solid ground"); bufferNeeded = 2; }
    private Opt[] ticks;
    @Override protected void options() { ticks = range("surfaceTicks", "Ticks walking on liquid", 30, 10, 2, 200, 1, "ticks standing/walking on top of water or lava before it flags"); }

    @Override
    protected boolean move(PlayerData d, EntityPlayerMP pl, double fx, double fy, double fz, double x, double y, double z, boolean onGround) {
        // A swimmer at the surface always has their feet IN the liquid; a Jesus hack keeps the feet above it and bobs
        // ~0.08/tick to dodge "not moving vertically" rules (2026-09-29 CheatSim test: the old |dy|>0.05 reset let it
        // walk across a whole pool). So: liquid right under the feet, feet dry, nothing solid to stand on.
        boolean liquidBelow = liquid(pl, x, y - 0.1, z, 0.2), feetWet = liquid(pl, x, y + 0.05, z, 0.05);
        boolean surface = liquidBelow && !feetWet && !ground(pl, x, y, z, 0.3);
        if (!surface) { d.liquidTopTicks = Math.max(0, d.liquidTopTicks - 3); pass(d); return false; }   // one dip doesn't wipe it
        if (++d.liquidTopTicks < (int) lerp(ticks)) return false;
        d.liquidTopTicks = 0;
        return flag(d, 1, "walked on liquid" + gear(pl));
    }
}
