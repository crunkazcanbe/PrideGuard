package com.dogpound.prideguard.checks;

import com.dogpound.prideguard.Check;
import com.dogpound.prideguard.PlayerData;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.CPacketEntityAction;
import net.minecraft.network.play.client.CPacketHeldItemChange;
import net.minecraft.network.play.client.CPacketPlayer;
import net.minecraft.network.play.client.CPacketUseEntity;

/** Packets a real, unmodified game can never send: looking past straight up/down, hitting yourself, and so on. */
public class BadPackets extends Check {
    public BadPackets() { super("badpackets", "Bad Packets", "Protocol", "sending things a normal game can't — a sure sign of a hacked client"); weight = 2; }

    @Override
    public boolean onPacket(PlayerData d, Packet<?> p) {
        EntityPlayerMP pl = d.player;
        if (p instanceof CPacketPlayer) {
            float pitch = ((CPacketPlayer) p).getPitch(pl.rotationPitch);
            if (Math.abs(pitch) > 90.01f) return flag(d, 3, String.format("looked %.0f° up/down (max 90)", pitch));
        } else if (p instanceof CPacketUseEntity) {
            CPacketUseEntity u = (CPacketUseEntity) p;
            if (u.getAction() == CPacketUseEntity.Action.ATTACK) {
                if (u.getEntityFromWorld(pl.world) == pl) return flag(d, 3, "attacked themselves");
                if (pl.isDead || pl.getHealth() <= 0) return flag(d, 2, "attacked while dead");
                if (pl.isPlayerSleeping()) return flag(d, 2, "attacked while asleep");
            }
        } else if (p instanceof CPacketHeldItemChange) {
            int slot = ((CPacketHeldItemChange) p).getSlotId();
            boolean same = slot == d.lastSlot;
            d.lastSlot = slot;
            if (same) return flag(d, 0.5, "switched to the slot already held (" + slot + ")");
        } else if (p instanceof CPacketEntityAction) {
            CPacketEntityAction a = (CPacketEntityAction) p;
            if (a.getAction() == CPacketEntityAction.Action.START_SPRINTING) {
                boolean twice = d.sprintStarted;
                d.sprintStarted = true;
                if (twice) return flag(d, 0.5, "started sprinting twice");
            } else if (a.getAction() == CPacketEntityAction.Action.STOP_SPRINTING) d.sprintStarted = false;
        }
        return false;
    }
}
