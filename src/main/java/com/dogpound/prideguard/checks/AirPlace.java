package com.dogpound.prideguard.checks;

import com.dogpound.prideguard.Check;
import com.dogpound.prideguard.PlayerData;
import net.minecraft.block.state.IBlockState;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.CPacketPlayerTryUseItemOnBlock;

/** Clicking a block face that isn't there (air or a liquid) — how "air place" and some scaffold hacks build. */
public class AirPlace extends Check {
    public AirPlace() { super("airplace", "Air Place", "World", "placing blocks against thin air or water"); bufferNeeded = 3; }

    @Override
    public boolean onPacket(PlayerData d, Packet<?> p) {
        if (!(p instanceof CPacketPlayerTryUseItemOnBlock)) return false;
        CPacketPlayerTryUseItemOnBlock u = (CPacketPlayerTryUseItemOnBlock) p;
        IBlockState s = d.player.world.getBlockState(u.getPos());
        boolean nothing = s.getBlock().isAir(s, d.player.world, u.getPos()) || s.getMaterial().isLiquid();
        if (!nothing) { pass(d); return false; }
        return flag(d, 1, "clicked on " + (s.getMaterial().isLiquid() ? "liquid" : "air") + " at " + u.getPos().getX() + " " + u.getPos().getY() + " " + u.getPos().getZ());
    }
}
