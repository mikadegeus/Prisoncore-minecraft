package dev.mika.prisoncore.util;

import org.jetbrains.annotations.NotNull;

import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/**
 * Number formatting helpers. {@link #money(double)} renders a plain value
 * without trailing zeros; {@link #compact(double)} renders a short,
 * human-readable form such as {@code 1.2K}, {@code 3.4M} or {@code 5.6B}.
 */
public final class NumberFormat {

    private static final DecimalFormat PLAIN =
            new DecimalFormat("#,##0.##", new DecimalFormatSymbols(Locale.US));
    private static final DecimalFormat SHORT =
            new DecimalFormat("0.##", new DecimalFormatSymbols(Locale.US));

    private static final String[] SUFFIXES = {"", "K", "M", "B", "T", "Q"};
    private static final double THOUSAND = 1000.0;

    static {
        PLAIN.setRoundingMode(RoundingMode.DOWN);
        SHORT.setRoundingMode(RoundingMode.DOWN);
    }

    private NumberFormat() {
        // Utility class, never instantiated.
    }

    /** Format a monetary value with thousands separators and no trailing zeros. */
    @NotNull
    public static String money(double value) {
        return PLAIN.format(value);
    }

    /** Format a long count with thousands separators. */
    @NotNull
    public static String money(long value) {
        return PLAIN.format(value);
    }

    /**
     * Format a value compactly, scaling by thousands and appending a suffix.
     * Values below 1000 are shown as-is.
     */
    @NotNull
    public static String compact(double value) {
        if (value < THOUSAND) {
            return SHORT.format(value);
        }
        double scaled = value;
        int index = 0;
        while (scaled >= THOUSAND && index < SUFFIXES.length - 1) {
            scaled /= THOUSAND;
            index++;
        }
        return SHORT.format(scaled) + SUFFIXES[index];
    }
}
