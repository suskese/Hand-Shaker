package me.mklv.handlib.text;

import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;

@SuppressWarnings("null")
public final class TextEventCompat {

    private TextEventCompat() {
    }

    public static ClickEvent runCommand(String command) {
        return new ClickEvent(ClickEvent.Action.RUN_COMMAND, command);
    }

    public static ClickEvent suggestCommand(String command) {
        return new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, command);
    }

    public static HoverEvent showText(String text) {
        return new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(text));
    }

    public static HoverEvent showText(Component component) {
        return new HoverEvent(HoverEvent.Action.SHOW_TEXT, component);
    }
}