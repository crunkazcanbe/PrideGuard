package com.dogpound.prideguard.checks;

import com.dogpound.prideguard.Opt;
import com.dogpound.prideguard.Guard;
import com.dogpound.prideguard.PlayerData;
import net.minecraft.entity.player.EntityPlayerMP;

/**
 * Staying in the air without falling. A vanilla jump rises for ~6 ticks and then gravity wins; hovering or climbing
 * with no ground, ladder, liquid or knockback under you means flight. Jetpacks and flight rings show up here with
 * their item name — approve them under Movement Sources and they stop flagging.
 */
public class Fly extends MoveCheck {
    public Fly() { super("fly", "Fly / Hover", "staying up in the air without falling (flying without a reason)"); bufferNeeded = 2; }
    private Opt knockBonus, jumpBonus, bounce;
    private Opt[] hover;
    @Override protected void options() {
        hover = range("hoverTicks", "Hover/climb ticks allowed", 20, 9, 2, 200, 1, "ticks in the air without falling before it flags");
        knockBonus = integer("knockBonus", "Extra ticks after knockback", 25, 0, 200, 5, "more air time allowed while knockback is carrying the player");
        jumpBonus = integer("jumpBoostBonus", "Extra ticks with Jump Boost", 10, 0, 200, 5, "more air time with a Jump Boost potion");
        bounce = integer("bounceTicks", "Slime bounce grace (ticks)", 40, 0, 400, 5, "ignore air time this long after bouncing on slime");
    }

    @Override
    protected boolean move(PlayerData d, EntityPlayerMP pl, double fx, double fy, double fz, double x, double y, double z, boolean onGround) {
        if (ground(pl, x, y, z, 0.6) || liquid(pl, x, y, z, 0.4) || pl.isOnLadder() || Guard.tick - d.lastBounceTick < bounce.i()) {
            d.airTicks = 0; d.hoverTicks = 0; pass(d); return false;
        }
        d.airTicks++;
        double dy = y - fy;
        if (dy >= -0.01) d.hoverTicks++; else d.hoverTicks = Math.max(0, d.hoverTicks - 2);
        int allow = (int) lerp(hover) + (knock(d) > 0.15 ? knockBonus.i() : 0) + (pl.getActivePotionEffect(net.minecraft.init.MobEffects.JUMP_BOOST) == null ? 0 : jumpBonus.i());
        if (d.hoverTicks <= allow) return false;
        d.hoverTicks = 0;
        return flag(d, 1, String.format("%d ticks in the air without falling%s", d.airTicks, gear(pl)));
    }
}
