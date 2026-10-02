package com.dogpound.prideguard;

import net.minecraft.entity.player.EntityPlayerMP;

/** /pg debug <check>: one server-log line per decision, for tuning a check against a real cheat */
public final class PrideGuardDebug {
    private PrideGuardDebug() {}
    public static void log(Check c, EntityPlayerMP p, String what) { PrideGuard.LOG.info("[PrideGuard debug] " + c.id + " " + p.getName() + " t" + Guard.tick + ": " + what); }
}
