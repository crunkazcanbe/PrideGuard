package com.dogpound.prideguard.checks;

import com.dogpound.prideguard.Check;
import com.dogpound.prideguard.PlayerData;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.CPacketAnimation;
import net.minecraft.network.play.client.CPacketPlayer;
import net.minecraft.network.play.client.CPacketUseEntity;

/**
 * Two things a real 1.12 client never does: (1) hit something without swinging its arm in the same tick — the
 * game always sends the swing right after the hit; (2) hit two DIFFERENT targets in one tick (multi-aura).
 */
public class KillAura extends Check {
    public KillAura() { super("killaura", "Kill Aura", "Combat", "auto-attack hacks: hitting without swinging, or several targets in one tick"); bufferNeeded = 3; }

    @Override
    public boolean onPacket(PlayerData d, Packet<?> p) {
        if (p instanceof CPacketUseEntity && ((CPacketUseEntity) p).getAction() == CPacketUseEntity.Action.ATTACK) {
            net.minecraft.entity.Entity t = ((CPacketUseEntity) p).getEntityFromWorld(d.player.world);
            if (t == null) return false;
            d.hitAwaitingSwing = true;
            d.hitThisTick.add(t.getEntityId());
            if (d.hitThisTick.size() > 1) return flag(d, d.hitThisTick.size() - 1, "hit " + d.hitThisTick.size() + " different targets in one tick");
        } else if (p instanceof CPacketAnimation) {
            d.hitAwaitingSwing = false;
        } else if (p instanceof CPacketPlayer) {
            boolean noSwing = d.hitAwaitingSwing;
            d.hitAwaitingSwing = false;
            d.hitThisTick.clear();
            if (noSwing) return flag(d, 1, "hit without swinging");
            pass(d);
        }
        return false;
    }
}
