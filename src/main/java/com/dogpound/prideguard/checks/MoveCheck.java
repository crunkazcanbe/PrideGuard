package com.dogpound.prideguard.checks;

import com.dogpound.prideguard.Check;
import com.dogpound.prideguard.Guard;
import com.dogpound.prideguard.PlayerData;
import com.dogpound.prideguard.Sources;
import net.minecraft.block.material.Material;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.MobEffects;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.CPacketPlayer;
import net.minecraft.util.math.AxisAlignedBB;

/**
 * Shared base for movement checks. Each client position packet becomes one "move" (from, to, ground), but only
 * when nothing legitimately changes physics: riding, creative/spectator flight, elytra, a server teleport not yet
 * landed, server lag, levitation, cobwebs, or an approved Movement Source. Knockback and explosions are handled
 * by each check as extra allowance, not exemption.
 */
public abstract class MoveCheck extends Check {
    protected MoveCheck(String id, String name, String about) { super(id, name, "Movement", about); }

    @Override
    public final boolean onPacket(PlayerData d, Packet<?> p) {
        if (!(p instanceof CPacketPlayer)) return false;
        CPacketPlayer m = (CPacketPlayer) p;
        EntityPlayerMP pl = d.player;
        double x = m.getX(pl.posX), y = m.getY(pl.posY), z = m.getZ(pl.posZ);
        if (!d.hasLast) return false;                       // Guard records positions after all checks (see Guard.moved)
        if (exempt(d, pl)) { if (Guard.debug(this)) com.dogpound.prideguard.PrideGuardDebug.log(this, pl, "exempt"); return false; }
        if (Guard.debug(this)) com.dogpound.prideguard.PrideGuardDebug.log(this, pl, String.format("from %.2f,%.2f,%.2f to %.2f,%.2f,%.2f ground=%b lastGround=%b", d.lastX, d.lastY, d.lastZ, x, y, z, m.isOnGround(), d.lastGround));
        return move(d, pl, d.lastX, d.lastY, d.lastZ, x, y, z, m.isOnGround());
    }

    static boolean exempt(PlayerData d, EntityPlayerMP pl) {
        return pl.isRiding() || pl.capabilities.allowFlying || pl.capabilities.isFlying || pl.isElytraFlying() || pl.isSpectator()
            || pl.isDead || pl.isPlayerSleeping() || d.inTeleportGrace(30) || Guard.tick < Guard.lagUntilTick
            || pl.isPotionActive(MobEffects.LEVITATION) || inWeb(pl) || Sources.approved(pl);
    }

    static boolean inWeb(EntityPlayerMP pl) {
        AxisAlignedBB bb = pl.getEntityBoundingBox();
        return pl.world.isMaterialInBB(bb, Material.WEB);
    }

    /** solid ground (any block or entity collision) within `below` blocks under the feet at x,y,z */
    static boolean ground(EntityPlayerMP pl, double x, double y, double z, double below) {
        double w = pl.width / 2 + 0.05;
        AxisAlignedBB feet = new AxisAlignedBB(x - w, y - below, z - w, x + w, y + 0.01, z + w);
        return !pl.world.getCollisionBoxes(pl, feet).isEmpty();
    }

    static boolean liquid(EntityPlayerMP pl, double x, double y, double z, double below) {
        double w = pl.width / 2;
        return pl.world.containsAnyLiquid(new AxisAlignedBB(x - w, y - below, z - w, x + w, y + 0.2, z + w));
    }

    /** knockback / explosion still carrying the player: how much extra speed to allow */
    static double knock(PlayerData d) {
        int since = Guard.tick - d.lastKnockTick;
        return since > 40 ? 0 : d.knockSpeed * Math.pow(0.91, since) + 0.1;
    }

    protected abstract boolean move(PlayerData d, EntityPlayerMP pl, double fx, double fy, double fz, double x, double y, double z, boolean onGround);

    /** what the player had on, added to every movement flag (feeds the Movement Sources page) */
    protected String gear(EntityPlayerMP pl) {
        java.util.List<String> s = Sources.of(pl);
        return s.isEmpty() ? "" : " · has " + String.join(", ", s);
    }
}
