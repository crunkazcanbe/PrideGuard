package com.dogpound.prideguard.checks;

import com.dogpound.prideguard.Opt;
import com.dogpound.prideguard.Check;
import com.dogpound.prideguard.PlayerData;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.CPacketUseEntity;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;

/** Hitting something a solid block stands in front of — killaura and hitbox hacks do; a real mouse can't. */
public class HitThroughWalls extends Check {
    public HitThroughWalls() { super("walls", "Hit Through Walls", "Combat", "hitting something with a solid block in the way"); bufferNeeded = 3; }
    private Opt close, tol;
    @Override protected void options() {
        close = num("closeRange", "Ignore hits closer than (blocks)", 1.2, 0, 4, 0.1, "point-blank hits are never judged (hitboxes overlap walls)");
        tol = num("wallTolerance", "Wall tolerance (blocks)", 0.25, 0, 2, 0.05, "a wall this close to the eye doesn't count as in the way");
    }

    @Override
    public boolean onPacket(PlayerData d, Packet<?> p) {
        if (!(p instanceof CPacketUseEntity) || ((CPacketUseEntity) p).getAction() != CPacketUseEntity.Action.ATTACK) return false;
        EntityPlayerMP pl = d.player;
        Entity t = ((CPacketUseEntity) p).getEntityFromWorld(pl.world);
        if (t == null || pl.isRiding()) return false;
        AxisAlignedBB b = t.getEntityBoundingBox();
        Vec3d eye = new Vec3d(pl.posX, pl.posY + pl.getEyeHeight(), pl.posZ);
        if (eye.distanceTo(new Vec3d((b.minX + b.maxX) / 2, (b.minY + b.maxY) / 2, (b.minZ + b.maxZ) / 2)) < close.d()) return false;
        // any clear line to the centre, the top, the closest point, or the four upper corners = fine
        Vec3d[] aims = {
            new Vec3d((b.minX + b.maxX) / 2, (b.minY + b.maxY) / 2, (b.minZ + b.maxZ) / 2),
            new Vec3d((b.minX + b.maxX) / 2, b.maxY - 0.05, (b.minZ + b.maxZ) / 2),
            new Vec3d(Math.max(b.minX, Math.min(eye.x, b.maxX)), Math.max(b.minY, Math.min(eye.y, b.maxY)), Math.max(b.minZ, Math.min(eye.z, b.maxZ))),
            new Vec3d(b.minX, b.maxY - 0.05, b.minZ), new Vec3d(b.maxX, b.maxY - 0.05, b.minZ), new Vec3d(b.minX, b.maxY - 0.05, b.maxZ), new Vec3d(b.maxX, b.maxY - 0.05, b.maxZ)};
        for (Vec3d a : aims) {
            RayTraceResult r = pl.world.rayTraceBlocks(eye, a, false, true, false);
            if (r == null || r.typeOfHit != RayTraceResult.Type.BLOCK || r.hitVec.distanceTo(a) < tol.d()) { pass(d); return false; }
        }
        return flag(d, 1, "hit " + t.getName() + " through a wall");
    }
}
