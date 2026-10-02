package com.dogpound.prideguard;

/** What a check does when it catches something. Each step includes the ones before it. */
public enum Mode {
    OFF("Off", 0xFF555555, "doesn't run"),
    WATCH("Watch", 0xFF5BCEFA, "only logs — nobody is told, nothing is stopped"),
    ALERT("Alert", 0xFFFFD700, "logs and tells staff in chat"),
    CANCEL("Block", 0xFFFF8C00, "also stops the cheating action (setback / cancelled hit / block put back)"),
    KICK("Kick", 0xFFE40303, "also kicks once the violation level reaches the kick line");

    public final String label, about;
    public final int color;
    Mode(String label, int color, String about) { this.label = label; this.color = color; this.about = about; }

    public Mode next() { return values()[(ordinal() + 1) % values().length]; }
}
