package com.dogpound.prideguard;

import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.server.permission.DefaultPermissionHandler;
import net.minecraftforge.server.permission.DefaultPermissionLevel;
import net.minecraftforge.server.permission.PermissionAPI;

/** PrideGuard's staff powers as named permissions. No permissions mod installed = the old op-level-2 check, exactly. */
public final class Perm {
    public static final String ADMIN = "prideguard.admin", ALERTS = "prideguard.alerts", BYPASS = "prideguard.bypass", TRUSTED = "prideguard.trusted";

    private Perm() {}

    static void register() {
        PermissionAPI.registerNode(ADMIN, DefaultPermissionLevel.OP, "Open PrideGuard's menu and use its admin tools");
        PermissionAPI.registerNode(ALERTS, DefaultPermissionLevel.OP, "Receive cheat alerts and kick messages");
        PermissionAPI.registerNode(BYPASS, DefaultPermissionLevel.OP, "Skipped by checks set to 'ignore ops'");
        PermissionAPI.registerNode(TRUSTED, DefaultPermissionLevel.OP, "Counts as trusted (green) when 'ops count as green' is on");
    }

    public static boolean has(ICommandSender who, String node) {
        if (who instanceof EntityPlayerMP && !(who instanceof FakePlayer) && !((EntityPlayer) who).world.isRemote
                && !(PermissionAPI.getPermissionHandler() instanceof DefaultPermissionHandler)) {
            try { return PermissionAPI.hasPermission((EntityPlayer) who, node); } catch (Throwable ignored) {}
        }
        return who != null && who.canUseCommand(2, "prideguard");
    }
}
