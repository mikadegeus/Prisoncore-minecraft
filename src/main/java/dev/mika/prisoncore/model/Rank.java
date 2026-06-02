package dev.mika.prisoncore.model;

import org.jetbrains.annotations.NotNull;

/**
 * An immutable rank in the A..Z progression.
 *
 * @param id          the rank identifier (e.g. {@code "A"})
 * @param displayName the name shown to players
 * @param index       the zero-based position in the rank ladder
 * @param cost        the money cost to advance <em>out</em> of this rank to the next
 */
public record Rank(@NotNull String id, @NotNull String displayName, int index, double cost) {
}
