package com.dogpound.prideguard.checks;

import com.dogpound.prideguard.Opt;
import com.dogpound.prideguard.PlayerData;
import net.minecraft.entity.player.EntityPlayerMP;

/** Walking straight up a tall step with no jump — step hacks; uses the server's own step height (mod boots raise it). */
public class Step extends MoveCheck {
    public Step() { super("step", "Step", "walking straight up blocks taller than your step height"); bufferNeeded = 1; }   // a 1.5+ block step with no jump is impossible legit; staircases put ~5 normal moves between steps, which drained a buffer of 2
    private Opt[] margin;
    @Override protected void options() { margin = range("margin", "Extra step height (blocks)", 0.25, 0.05, 0, 2, 0.01, "added to the player's own step height"); }

    @Override
    protected boolean move(PlayerData d, EntityPlayerMP pl, double fx, double fy, double fz, double x, double y, double z, boolean onGround) {
        double dy = y - fy;
        if (!(onGround && d.lastGround) || dy <= 0) { pass(d); return false; }
        double limit = Math.max(0.6, pl.stepHeight) + lerp(margin);
        if (com.dogpound.prideguard.Guard.debug(this)) com.dogpound.prideguard.PrideGuardDebug.log(this, pl, String.format("step dy=%.2f limit=%.2f (server stepHeight %.2f)", dy, limit, pl.stepHeight));
        if (dy <= limit) { pass(d); return false; }
        return flag(d, dy - limit, String.format("stepped up %.2f blocks (step height %.1f)%s", dy, pl.stepHeight, gear(pl)));
    }
}
