package com.wish.commandblockerbungee.utils;

public class NotificationAction {
    private final String label;
    private final String hover;
    private final String command;

    public NotificationAction(String label, String hover, String command) {
        this.label = label;
        this.hover = hover;
        this.command = command;
    }

    public String getLabel() { return label; }
    public String getHover() { return hover; }
    public String getCommand() { return command; }
}
