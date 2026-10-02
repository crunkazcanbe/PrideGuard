package com.dogpound.prideguard.checks;

import com.dogpound.prideguard.Opt;
import com.dogpound.prideguard.Check;
import com.dogpound.prideguard.Guard;
import com.dogpound.prideguard.PlayerData;
import com.dogpound.prideguard.PrideGuardDebug;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.CPacketPlayer;
import net.minecraft.util.math.AxisAlignedBB;

/**
 * Claiming to stand on the ground while floating (NoFall hacks do it to skip fall damage). Checks the ground
 * FLAG against the server's real collision — including modded blocks and boats — and never the damage, because
 * lots of modded boots legitimately cancel fall damage.
 */
public class GroundSpoof extends Check {
    public GroundSpoof() { super("groundspoof", "No Fall (Ground Spoof)", "Movement", "pretending to stand on the ground in mid-air, to take no fall damage"); bufferNeeded = 2; }   // 4 + a 3-packet streak let every short NoFall drop through (2026-09-29 test)
    private Opt tp;
    private Opt[] reach;
    @Override protected void options() {
        tp = integer("teleportTicks", "Ignore after teleport (ticks)", 60, 0, 400, 5, "ticks after a teleport before on-ground claims are checked");
        reach = range("groundReach", "Ground search depth (blocks)", 0.6, 0.12, 0.01, 3, 0.01, "how far below the feet still counts as standing on something");
    }

    @Override
    public boolean onPacket(PlayerData d, Packet<?> p) {
        if (!(p instanceof CPacketPlayer)) return false;
        CPacketPlayer m = (CPacketPlayer) p;
        EntityPlayerMP pl = d.player;
        if (Guard.debug(this)) PrideGuardDebug.log(this, pl, String.format("onGround=%b y=%.2f tpAgo=%d lag=%b inWater=%b streak=%d", m.isOnGround(), m.getY(pl.posY), Guard.tick - d.lastTeleportTick, Guard.tick < Guard.lagUntilTick, pl.isInWater(), d.groundSpoof));
        if (!m.isOnGround() || pl.isRiding() || pl.capabilities.allowFlying || pl.isElytraFlying() || pl.isOnLadder() || pl.isInWater() || pl.isInLava()
            || d.inTeleportGrace(tp.i()) || Guard.tick < Guard.lagUntilTick) { d.groundSpoof = 0; return false; }
        double x = m.getX(pl.posX), y = m.getY(pl.posY), z = m.getZ(pl.posZ);
        double w = pl.width / 2 + 0.2;
        AxisAlignedBB feet = new AxisAlignedBB(x - w, y - lerp(reach), z - w, x + w, y + 0.1, z + w);
        if (!pl.world.getCollisionBoxes(pl, feet).isEmpty() || pl.world.containsAnyLiquid(feet)) { d.groundSpoof = 0; pass(d); return false; }
        if (++d.groundSpoof < 2) return false;
        return flag(d, 1, String.format("'on the ground' %.1f blocks up in the air", y - Math.floor(y)));
    }
}
