package com.dogpound.prideguard.checks;

import com.dogpound.prideguard.Opt;
import com.dogpound.prideguard.Check;
import com.dogpound.prideguard.Guard;
import com.dogpound.prideguard.PlayerData;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.CPacketPlayerDigging;

/** Mining a block for a while without the arm ever moving — the real game swings every tick while you dig. */
public class NoSwingBreak extends Check {
    public NoSwingBreak() { super("noswing", "No-Swing Breaking", "World", "breaking blocks without ever swinging the arm (hacked mining)"); bufferNeeded = 2; }
    private Opt[] ticks;
    @Override protected void options() { ticks = range("ticks", "Digging ticks without a swing", 12, 5, 1, 100, 1, "ticks of digging without any arm swing before it flags"); }

    @Override
    public boolean onPacket(PlayerData d, Packet<?> p) {
        if (!(p instanceof CPacketPlayerDigging) || d.player.isCreative()) return false;
        CPacketPlayerDigging dig = (CPacketPlayerDigging) p;
        if (dig.getAction() == CPacketPlayerDigging.Action.START_DESTROY_BLOCK) { d.swingsThisDig = 0; return false; }
        if (dig.getAction() != CPacketPlayerDigging.Action.STOP_DESTROY_BLOCK) return false;
        int ticks = Guard.tick - d.digStartTick;
        if (ticks < (int) lerp(this.ticks) || d.swingsThisDig > 0) { pass(d); return false; }
        return flag(d, 1, "dug for " + ticks + " ticks without a single swing");
    }
}
