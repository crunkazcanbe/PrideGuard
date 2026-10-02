package com.dogpound.prideguard.checks;

import com.dogpound.prideguard.Opt;
import com.dogpound.prideguard.Check;
import com.dogpound.prideguard.PlayerData;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.CPacketPlayerDigging;
import net.minecraft.network.play.client.CPacketPlayerTryUseItemOnBlock;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

/** Breaking or clicking blocks beyond your reach — uses the real reach attribute, so modded reach items count. */
public class FarInteract extends Check {
    public FarInteract() { super("farinteract", "Far Break / Place", "World", "breaking or using blocks beyond arm's reach"); bufferNeeded = 2; }
    private Opt[] margin;
    @Override protected void options() { margin = range("margin", "Extra allowance (blocks)", 2.0, 0.9, 0, 6, 0.05, "added to the block reach before it flags"); }

    @Override
    public boolean onPacket(PlayerData d, Packet<?> p) {
        BlockPos pos;
        if (p instanceof CPacketPlayerDigging && ((CPacketPlayerDigging) p).getAction() == CPacketPlayerDigging.Action.START_DESTROY_BLOCK) pos = ((CPacketPlayerDigging) p).getPosition();
        else if (p instanceof CPacketPlayerTryUseItemOnBlock) pos = ((CPacketPlayerTryUseItemOnBlock) p).getPos();
        else return false;
        EntityPlayerMP pl = d.player;
        if (pl.isRiding()) return false;
        double reach = pl.getEntityAttribute(EntityPlayer.REACH_DISTANCE).getAttributeValue();
        double dist = new Vec3d(pl.posX, pl.posY + pl.getEyeHeight(), pl.posZ).distanceTo(new Vec3d(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5));
        double limit = reach + lerp(margin);                     // block centre vs the nearest face: up to ~0.87 extra
        if (dist <= limit) { pass(d); return false; }
        return flag(d, dist - limit, String.format("%.1f blocks away (reach %.1f)", dist, reach));
    }
}
