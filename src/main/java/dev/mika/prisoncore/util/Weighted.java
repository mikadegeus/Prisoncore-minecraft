package dev.mika.prisoncore.util;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * A simple weighted random picker. Entries are added with a positive weight and
 * {@link #pick(Random)} returns one proportionally to its share of the total
 * weight. Used for mine block palettes.
 *
 * @param <T> the element type
 */
public final class Weighted<T> {

    /** A single weighted choice. */
    private record Entry<T>(@NotNull T value, double weight) {
    }

    private final List<Entry<T>> entries = new ArrayList<>();
    private double totalWeight;

    /**
     * Add a choice. Non-positive weights are ignored so a mis-configured palette
     * entry cannot corrupt the distribution.
     */
    public void add(@NotNull T value, double weight) {
        if (weight <= 0) {
            return;
        }
        entries.add(new Entry<>(value, weight));
        totalWeight += weight;
    }

    /** @return {@code true} when no usable entries have been added. */
    public boolean isEmpty() {
        return entries.isEmpty();
    }

    /**
     * Pick an element proportional to its weight.
     *
     * @return a chosen element, or {@code null} when the picker is empty.
     */
    @Nullable
    public T pick(@NotNull Random random) {
        if (entries.isEmpty()) {
            return null;
        }
        double roll = random.nextDouble() * totalWeight;
        double cumulative = 0;
        for (Entry<T> entry : entries) {
            cumulative += entry.weight();
            if (roll < cumulative) {
                return entry.value();
            }
        }
        // Floating-point edge case: fall back to the last entry.
        return entries.get(entries.size() - 1).value();
    }
}
