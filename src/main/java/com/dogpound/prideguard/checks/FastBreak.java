package com.dogpound.prideguard.checks;

import com.dogpound.prideguard.Opt;
import com.dogpound.prideguard.Check;
import com.dogpound.prideguard.PlayerData;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.CPacketPlayerDigging;
import net.minecraft.network.play.server.SPacketBlockChange;
import net.minecraft.util.math.BlockPos;

/**
 * Breaking blocks faster than your tool allows. The server adds up the real break progress every tick — tool,
 * enchantments, haste, water, and every mod's break-speed changes (Forge BreakSpeed) — and the block must be
 * (nearly) done when the client says it's broken. Vanilla lets it through at 70 %: FastBreak hacks live there.
 */
public class FastBreak extends Check {
    public FastBreak() { super("fastbreak", "Fast Break", "World", "breaking blocks faster than the tool in hand can"); bufferNeeded = 2; }
    private Opt jitterCap;
    private Opt[] slack;
    @Override protected void options() {
        slack = range("slack", "Extra break progress", 0.25, 0.02, 0, 1, 0.01, "how much sooner than possible a block may break (1 = a whole block)");
        jitterCap = integer("pingJitterCap", "Lag allowance cap (ticks)", 10, 0, 40, 1, "the most ticks of lag jitter forgiven");
    }

    @Override
    public boolean onPacket(PlayerData d, Packet<?> p) {
        if (!(p instanceof CPacketPlayerDigging)) return false;
        CPacketPlayerDigging dig = (CPacketPlayerDigging) p;
        EntityPlayerMP pl = d.player;
        if (pl.isCreative()) return false;
        BlockPos pos = dig.getPosition();
        switch (dig.getAction()) {
            case START_DESTROY_BLOCK: {
                IBlockState s = pl.world.getBlockState(pos);
                float rate = s.getPlayerRelativeBlockHardness(pl, pl.world, pos);
                d.startsThisTick++;
                if (rate >= 1) { d.instantThisSecond++; d.digging = false; return false; }
                d.digging = true; d.digPos = pos; d.digProgress = rate; d.digStartTick = com.dogpound.prideguard.Guard.tick;
                return false;
            }
            case ABORT_DESTROY_BLOCK: d.digging = false; return false;
            case STOP_DESTROY_BLOCK: {
                if (!d.digging || !pos.equals(d.digPos)) return false;
                d.digging = false;
                IBlockState s = pl.world.getBlockState(pos);
                float rate = s.getPlayerRelativeBlockHardness(pl, pl.world, pos);
                double slack = rate * (2 + Math.min(jitterCap.i(), d.pingTicks() / 2)) + lerp(this.slack);   // jitter + forgiveness
                if (d.digProgress + slack >= 1.0) { pass(d); return false; }
                boolean stop = flag(d, 1.0 - d.digProgress, String.format("broke %s at %.0f%%", s.getBlock().getLocalizedName(), d.digProgress * 100));
                if (stop) pl.connection.sendPacket(new SPacketBlockChange(pl.world, pos));   // put the block back on their screen
                return stop;
            }
            default: return false;
        }
    }

    @Override
    public void onTick(PlayerData d, int tick) {
        if (!d.digging || d.digPos == null) return;
        EntityPlayerMP pl = d.player;
        d.digProgress += pl.world.getBlockState(d.digPos).getPlayerRelativeBlockHardness(pl, pl.world, d.digPos);
    }
}
