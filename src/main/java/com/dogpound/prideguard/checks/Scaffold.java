package com.dogpound.prideguard.checks;

import com.dogpound.prideguard.Opt;
import com.dogpound.prideguard.Check;
import com.dogpound.prideguard.PlayerData;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.CPacketPlayerTryUseItemOnBlock;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

/**
 * Scaffold / bridging hacks place blocks against faces the player can't see: clicking a face from BEHIND it (e.g.
 * the underside of the block you stand on), or reporting a click point that isn't on that face at all. A real
 * mouse can only click faces that point toward the eye, at a spot inside the face (AntiCheatAddition's idea).
 */
public class Scaffold extends Check {
    public Scaffold() { super("scaffold", "Scaffold / Impossible Place", "World", "placing blocks against faces you can't see (scaffold / bridge hacks)"); bufferNeeded = 3; }
    private Opt[] behind, lookOff;
    @Override protected void options() {
        behind = range("behind", "Place from behind a face (blocks)", 0.4, 0.1, 0, 2, 0.05, "how far behind the clicked face the eye may be");
        lookOff = range("lookAngle", "Place without looking (degrees)", 75, 35, 10, 180, 1, "how far the clicked spot may be from where the player is looking (scaffold hacks place under you while you look ahead)");
    }

    @Override
    public boolean onPacket(PlayerData d, Packet<?> p) {
        if (!(p instanceof CPacketPlayerTryUseItemOnBlock)) return false;
        CPacketPlayerTryUseItemOnBlock u = (CPacketPlayerTryUseItemOnBlock) p;
        float fx = u.getFacingX(), fy = u.getFacingY(), fz = u.getFacingZ();
        if (fx < -0.01f || fx > 1.01f || fy < -0.01f || fy > 1.01f || fz < -0.01f || fz > 1.01f)
            return flag(d, 2, String.format("clicked point (%.2f, %.2f, %.2f) isn't on the block", fx, fy, fz));
        EntityPlayerMP pl = d.player;
        if (pl.isRiding()) return false;
        BlockPos b = u.getPos();
        EnumFacing f = u.getDirection();
        Vec3d n = new Vec3d(f.getFrontOffsetX(), f.getFrontOffsetY(), f.getFrontOffsetZ());
        Vec3d face = new Vec3d(b.getX() + 0.5, b.getY() + 0.5, b.getZ() + 0.5).add(n.scale(0.5));
        double behind = new Vec3d(pl.posX, pl.posY + pl.getEyeHeight(), pl.posZ).subtract(face).dotProduct(n);
        if (behind <= -lerp(this.behind)) return flag(d, 1, String.format("clicked the %s face from %.2f blocks behind it", f.getName(), -behind));
        // a real click has the crosshair on the block: compare the look direction with the direction to the clicked spot
        Vec3d eye = new Vec3d(pl.posX, pl.posY + pl.getEyeHeight(), pl.posZ);
        Vec3d hit = new Vec3d(b.getX() + fx, b.getY() + fy, b.getZ() + fz), to = hit.subtract(eye);
        if (to.lengthVector() > 0.3) {
            double deg = Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, pl.getLookVec().dotProduct(to.normalize())))));
            if (com.dogpound.prideguard.Guard.debug(this)) com.dogpound.prideguard.PrideGuardDebug.log(this, pl, String.format("place %s face=%s look-off %.0f°", b, f.getName(), deg));
            if (deg > lerp(lookOff)) return flag(d, deg / 45, String.format("placed a block %.0f° away from where they were looking", deg));
        }
        pass(d);
        return false;
    }
}
