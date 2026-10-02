package com.dogpound.prideguard.checks;

import com.dogpound.prideguard.Opt;
import com.dogpound.prideguard.Guard;
import com.dogpound.prideguard.PlayerData;
import net.minecraft.block.Block;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.BlockPos;

/**
 * Moving faster than the player's own speed allows. The limit comes from the SERVER's movement-speed attribute —
 * which already includes sprinting, speed potions and every mod's speed gear — times what the ground allows
 * (ice, jumping boost), plus knockback. Small overshoots build an "excess" that drains, so one odd tick never flags.
 */
public class Speed extends MoveCheck {
    public Speed() { super("speed", "Speed", "moving faster than your own speed (sprint, potions, gear) allows"); bufferNeeded = 2; }
    private Opt ground, air, iceTicks, iceMult, water, carry;
    private Opt[] margin, excess;
    @Override protected void options() {
        ground = num("groundSpeed", "Ground limit (blocks/tick)", 0.29, 0.1, 2, 0.01, "fastest walking/sprinting move on the ground, x the player's speed attribute");
        air = num("airSpeed", "Air limit (blocks/tick)", 0.63, 0.1, 3, 0.01, "fastest move in the air (sprint-jump), x the speed attribute");
        iceTicks = integer("iceTicks", "Ice memory (ticks)", 40, 0, 200, 5, "how long after touching ice the ice allowance lasts");
        iceMult = num("iceMultiplier", "Ice multiplier", 2.6, 1, 6, 0.1, "how much faster ice lets you go");
        water = num("waterSpeed", "Water/lava minimum (blocks/tick)", 0.4, 0.05, 2, 0.01, "limit while swimming, x the speed attribute");
        carry = num("excessCarry", "Overshoot memory", 0.85, 0, 0.99, 0.01, "how much of a small overshoot is remembered each tick (higher = catches slow speed hacks)");
        margin = range("margin", "Extra allowance (blocks/tick)", 0.12, 0.02, 0, 1, 0.01, "added to every limit");
        excess = range("excess", "Overshoot before a flag", 1.2, 0.35, 0.05, 5, 0.05, "total remembered overshoot that counts as speeding");
    }

    @Override
    protected boolean move(PlayerData d, EntityPlayerMP pl, double fx, double fy, double fz, double x, double y, double z, boolean onGround) {
        double dist = Math.hypot(x - fx, z - fz);
        Block under = pl.world.getBlockState(new BlockPos(x, y - 0.5, z)).getBlock();
        if (under == Blocks.ICE || under == Blocks.PACKED_ICE || under == Blocks.FROSTED_ICE || under.slipperiness > 0.7f) d.lastIceTick = Guard.tick;
        double attr = pl.getEntityAttribute(SharedMonsterAttributes.MOVEMENT_SPEED).getAttributeValue();
        double base = Math.max(0.1, attr) / 0.1;                     // 1.0 = plain walking; sprint/potions/gear push it up
        double limit = (onGround && d.lastGround ? ground.d() : air.d()) * base;                    // sprint-jump's first air tick is the fastest vanilla move
        if (Guard.tick - d.lastIceTick < iceTicks.i()) limit *= iceMult.d();
        if (pl.isInWater() || pl.isInLava()) limit = Math.max(limit, water.d() * base);
        limit += knock(d) + lerp(margin);
        double over = dist - limit;
        d.speedExcess = Math.max(0, d.speedExcess * carry.d() + over);
        if (d.speedExcess < lerp(excess)) { pass(d); return false; }
        double ex = d.speedExcess;
        d.speedExcess = 0;
        return flag(d, ex, String.format("%.2f blocks/tick, limit %.2f%s", dist, limit, gear(pl)));
    }
}
