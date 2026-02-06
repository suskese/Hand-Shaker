package me.mklv.handshaker.fabric.server.configs;

import java.util.ArrayList;
import java.util.List;

public class ActionDefinition {
    private final String name;
    private final List<String> commands;
    private final boolean shouldLog;

    public ActionDefinition(String name) {
        this(name, new ArrayList<>(), false);
    }

    public ActionDefinition(String name, List<String> commands) {
        this(name, commands, false);
    }

    public ActionDefinition(String name, List<String> commands, boolean shouldLog) {
        this.name = name;
        this.commands = commands != null ? new ArrayList<>(commands) : new ArrayList<>();
        this.shouldLog = shouldLog;
    }

    public String getName() {
        return name;
    }

    public List<String> getCommands() {
        return commands;
    }

    public boolean shouldLog() {
        return shouldLog;
    }

    public void addCommand(String command) {
        this.commands.add(command);
    }

    public boolean isEmpty() {
        return commands.isEmpty();
    }

    public int getCommandCount() {
        return commands.size();
    }
}
