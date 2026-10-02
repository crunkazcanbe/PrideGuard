package com.dogpound.prideguard.checks;

import com.dogpound.prideguard.Opt;
import com.dogpound.prideguard.Check;
import com.dogpound.prideguard.PlayerData;

/** Starting to break several blocks in one tick, or instantly breaking far too many a second (nuker hacks). */
public class Nuker extends Check {
    public Nuker() { super("nuker", "Nuker", "World", "breaking many blocks at once"); bufferNeeded = 2; }
    private Opt[] rate;
    @Override protected void options() { rate = range("perSecond", "Instant breaks per second", 40, 14, 2, 200, 1, "instant-mined blocks a second before it flags"); }

    @Override
    public void onTick(PlayerData d, int tick) {
        if (d.player.isCreative()) return;
        int perSecond = (int) lerp(rate);
        if (d.startsThisTick > 2) flag(d, d.startsThisTick - 2, d.startsThisTick + " blocks started in one tick");
        else if (tick % 20 == 19 && d.instantThisSecond > perSecond) flag(d, (d.instantThisSecond - perSecond) / 5.0, d.instantThisSecond + " instant breaks in a second");
        else pass(d);
    }
}
