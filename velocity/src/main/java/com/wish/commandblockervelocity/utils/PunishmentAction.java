package com.wish.commandblockervelocity.utils;

public class PunishmentAction {
    private final int threshold;
    private final String command;

    public PunishmentAction(int threshold, String command) {
        this.threshold = threshold;
        this.command = command;
    }

    public int getThreshold() { return threshold; }
    public String getCommand() { return command; }
}
