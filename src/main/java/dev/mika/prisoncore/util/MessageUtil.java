package dev.mika.prisoncore.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Central text helper. Converts legacy {@code &} colour codes (and MiniMessage
 * tags) into Adventure {@link Component}s and strips the default italic styling
 * that Minecraft applies to custom item names and lore.
 */
public final class MessageUtil {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();
    private static final MiniMessage MINI = MiniMessage.miniMessage();

    private MessageUtil() {
        // Utility class, never instantiated.
    }

    /**
     * Parse a string containing legacy {@code &} colour codes into a component.
     * Non-italic by default so item names render cleanly.
     */
    @NotNull
    public static Component color(@NotNull String input) {
        return LEGACY.deserialize(input).decoration(TextDecoration.ITALIC, false);
    }

    /**
     * Parse a string using the MiniMessage format (e.g. {@code <gold>text</gold>}).
     */
    @NotNull
    public static Component mini(@NotNull String input) {
        return MINI.deserialize(input).decoration(TextDecoration.ITALIC, false);
    }

    /**
     * Convert a list of legacy-coded strings into a list of components.
     */
    @NotNull
    public static List<Component> colorList(@NotNull List<String> input) {
        List<Component> result = new ArrayList<>(input.size());
        for (String line : input) {
            result.add(color(line));
        }
        return result;
    }

    /**
     * Replace a placeholder token with a value across the given message.
     *
     * @param message     the raw message
     * @param placeholder the token without braces, e.g. {@code amount}
     * @param value       the replacement value
     * @return a new string with every occurrence replaced
     */
    @NotNull
    public static String replace(@NotNull String message, @NotNull String placeholder, @NotNull String value) {
        return message.replace("{" + placeholder + "}", value);
    }
}
