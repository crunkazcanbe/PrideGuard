package com.dogpound.prideguard.checks;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import com.dogpound.prideguard.Check;
import com.dogpound.prideguard.PlayerData;

/**
 * The mod list the player's game reported when joining (Forge handshake). Cheat clients can hide, so this is a
 * deterrent and a record, not proof — but anything on the watch list is flagged the moment they join.
 */
public class ClientMods extends Check {
    public static final List<String> WATCH = Arrays.asList("wurst", "forgehax", "impact", "aristois", "liquidbounce", "meteor", "wolfram",
        "sigma", "xray", "advancedxray", "xraymod", "freecam", "killaura", "autoclicker", "baritone-cheat", "nodus", "huzuni", "future", "rusherhack", "inertia", "kami");

    public ClientMods() { super("clientmods", "Client Mods", "Protocol", "joining with known cheat mods installed (from the game's own mod list)"); }

    public void joined(PlayerData d) {
        StringBuilder hits = new StringBuilder();
        for (String id : d.mods.keySet()) {
            String low = id.toLowerCase(Locale.ROOT);
            for (String w : WATCH) if (low.equals(w) || low.startsWith(w)) { hits.append(hits.length() == 0 ? "" : ", ").append(id); break; }
        }
        if (hits.length() > 0) flag(d, 5, "joined with " + hits);
    }
}
