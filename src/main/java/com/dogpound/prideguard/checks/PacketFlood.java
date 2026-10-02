package com.dogpound.prideguard.checks;

import com.dogpound.prideguard.Opt;
import com.dogpound.prideguard.Check;
import com.dogpound.prideguard.PlayerData;

/** Hundreds of packets a second — lag machines, crash tools, spam macros. */
public class PacketFlood extends Check {
    public PacketFlood() { super("flood", "Packet Flood", "Protocol", "sending far more packets than a game ever needs (lag / crash tools)"); }
    private Opt[] rate;
    @Override protected void options() { rate = range("perSecond", "Packets per second", 1500, 350, 50, 20000, 50, "client packets a second before it flags"); }

    @Override
    public void onTick(PlayerData d, int tick) {
        if (tick % 20 != 19) return;
        int limit = (int) lerp(rate);
        if (d.packetsThisSecond > limit) flag(d, d.packetsThisSecond / (double) limit, d.packetsThisSecond + " packets in one second");
        else pass(d);
    }
}
