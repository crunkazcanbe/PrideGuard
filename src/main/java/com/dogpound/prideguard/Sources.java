package com.dogpound.prideguard;

import java.util.*;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

/**
 * Movement Sources: the modded things that legitimately move players in ways vanilla physics can't (jetpacks,
 * flight rings, speed boots, trains, ships). Every movement flag records what the player had on; the menu shows
 * which keep showing up across different players; she approves one with a click and from then on it counts as
 * a legit reason (research: "never exempt the player — widen the model for a known reason").
 */
public final class Sources {
    private Sources() {}

    public static final Set<String> APPROVED = new TreeSet<>();   // "item:ironjetpacks:jetpack_emerald", "ride:immersiverailroading:..."

    /** the non-vanilla things this player is wearing/holding/riding right now */
    public static List<String> of(EntityPlayerMP p) {
        List<String> out = new ArrayList<>();
        for (EntityEquipmentSlot slot : EntityEquipmentSlot.values()) add(out, p.getItemStackFromSlot(slot));
        try {                                                             // Baubles (rings, belts, amulets), if installed
            Class<?> api = Class.forName("baubles.api.BaublesApi");
            Object h = api.getMethod("getBaublesHandler", net.minecraft.entity.player.EntityPlayer.class).invoke(null, p);
            int n = (int) h.getClass().getMethod("getSlots").invoke(h);
            for (int i = 0; i < n; i++) add(out, (ItemStack) h.getClass().getMethod("getStackInSlot", int.class).invoke(h, i));
        } catch (Throwable ignored) {}
        Entity r = p.getRidingEntity();
        if (r != null) {
            ResourceLocation id = EntityList.getKey(r);
            if (id != null && !id.getResourceDomain().equals("minecraft")) out.add("ride:" + id);
        }
        return out;
    }

    private static void add(List<String> out, ItemStack s) {
        if (s == null || s.isEmpty() || s.getItem().getRegistryName() == null) return;
        ResourceLocation id = s.getItem().getRegistryName();
        if (!id.getResourceDomain().equals("minecraft")) out.add("item:" + id);
    }

    /** does the player have anything she approved as a movement source? */
    public static boolean approved(EntityPlayerMP p) {
        if (APPROVED.isEmpty()) return false;
        for (String k : of(p)) if (APPROVED.contains(k)) return true;
        return false;
    }
}
