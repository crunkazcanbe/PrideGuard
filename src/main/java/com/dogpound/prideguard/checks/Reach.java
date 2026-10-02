package com.dogpound.prideguard.checks;

import com.dogpound.prideguard.Opt;
import com.dogpound.prideguard.Check;
import com.dogpound.prideguard.Guard;
import com.dogpound.prideguard.PlayerData;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.CPacketUseEntity;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.Vec3d;

/**
 * Hitting further than the game allows (3 blocks, 6 in creative). Judged against every place the target was
 * during the attacker's ping (what their screen showed), from standing and sneaking eye height, so lag never flags.
 * Vanilla 1.12 accepts hits up to 6 blocks — that gap is exactly what reach hacks use.
 */
public class Reach extends Check {
    public Reach() { super("reach", "Reach", "Combat", "hitting mobs or players from further away than the game allows"); bufferNeeded = 2; }
    private Opt survival, creative, extraTicks, sneakEye;
    private Opt[] margin;
    @Override protected void options() {
        survival = num("survivalReach", "Survival reach (blocks)", 3.0, 1, 10, 0.1, "how far a survival player may hit (vanilla: 3)");
        creative = num("creativeReach", "Creative reach (blocks)", 6.0, 1, 12, 0.1, "how far a creative player may hit (vanilla: about 5-6)");
        margin = range("margin", "Extra allowance (blocks)", 0.35, 0.03, 0, 3, 0.01, "added on top of the reach before it flags");
        extraTicks = integer("extraTicks", "Extra history (ticks)", 3, 0, 20, 1, "ticks of the target's movement allowed beyond the attacker's ping");
        sneakEye = bool("sneakEye", "Allow sneaking eye height", true, "also measure from sneaking eye height (1.54) — a bit more forgiving");
    }

    @Override
    public boolean onPacket(PlayerData d, Packet<?> p) {
        if (!(p instanceof CPacketUseEntity) || ((CPacketUseEntity) p).getAction() != CPacketUseEntity.Action.ATTACK) return false;
        EntityPlayerMP pl = d.player;
        Entity t = ((CPacketUseEntity) p).getEntityFromWorld(pl.world);
        if (t == null || pl.isRiding() || t == pl) return false;
        double limit = pl.isCreative() ? creative.d() : survival.d();
        // /reach (Pride Realms) and reach items raise the reach attribute above its normal 5 — allow the same extra here
        limit += Math.max(0, pl.getEntityAttribute(net.minecraft.entity.player.EntityPlayer.REACH_DISTANCE).getAttributeValue() - 5.0);
        double margin = lerp(this.margin);
        double best = Double.MAX_VALUE;
        for (AxisAlignedBB bb : Guard.History.boxes(t, d.pingTicks() + extraTicks.i())) {
            AxisAlignedBB b = bb.grow(t.getCollisionBorderSize());
            for (double eye : sneakEye.on() ? new double[]{1.62, 1.54} : new double[]{1.62}) best = Math.min(best, dist(new Vec3d(pl.posX, pl.posY + eye, pl.posZ), b));
        }
        if (best <= limit + margin) { pass(d); return false; }
        return flag(d, best - limit, String.format("%.2f blocks to %s", best, t.getName()));
    }

    static double dist(Vec3d e, AxisAlignedBB b) {
        double x = Math.max(b.minX, Math.min(e.x, b.maxX)), y = Math.max(b.minY, Math.min(e.y, b.maxY)), z = Math.max(b.minZ, Math.min(e.z, b.maxZ));
        return e.distanceTo(new Vec3d(x, y, z));
    }
}
