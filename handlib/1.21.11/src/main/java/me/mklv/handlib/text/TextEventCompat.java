package me.mklv.handlib.text;

import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;

@SuppressWarnings("null")
public final class TextEventCompat {

    private TextEventCompat() {
    }

    public static ClickEvent runCommand(String command) {
        return new ClickEvent.RunCommand(command);
    }

    public static ClickEvent suggestCommand(String command) {
        return new ClickEvent.SuggestCommand(command);
    }

    public static HoverEvent showText(String text) {
        return new HoverEvent.ShowText(Component.literal(text));
    }

    public static HoverEvent showText(Component component) {
        return new HoverEvent.ShowText(component);
    }
}