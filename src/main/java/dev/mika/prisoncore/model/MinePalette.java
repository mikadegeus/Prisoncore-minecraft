package dev.mika.prisoncore.model;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.mika.prisoncore.util.Weighted;
import org.bukkit.Material;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * A mine's weighted block composition. Builds a {@link Weighted} picker lazily and
 * serialises to/from the compact JSON stored in the database
 * ({@code [{"material":"STONE","weight":70.0}, ...]}).
 */
public final class MinePalette {

    private static final Material FALLBACK = Material.STONE;

    private final List<PaletteEntry> entries;
    private Weighted<Material> weighted;

    public MinePalette(@NotNull List<PaletteEntry> entries) {
        this.entries = List.copyOf(entries);
    }

    @NotNull
    public List<PaletteEntry> entries() {
        return entries;
    }

    /** Pick a material according to the palette weights, never null. */
    @NotNull
    public Material randomMaterial(@NotNull Random random) {
        if (weighted == null) {
            weighted = buildWeighted();
        }
        Material picked = weighted.pick(random);
        return picked != null ? picked : FALLBACK;
    }

    @NotNull
    private Weighted<Material> buildWeighted() {
        Weighted<Material> result = new Weighted<>();
        for (PaletteEntry entry : entries) {
            result.add(entry.material(), entry.weight());
        }
        return result;
    }

    @NotNull
    public String toJson() {
        JsonArray array = new JsonArray();
        for (PaletteEntry entry : entries) {
            JsonObject object = new JsonObject();
            object.addProperty("material", entry.material().name());
            object.addProperty("weight", entry.weight());
            array.add(object);
        }
        return array.toString();
    }

    @NotNull
    public static MinePalette fromJson(@NotNull String json) {
        List<PaletteEntry> list = new ArrayList<>();
        try {
            JsonArray array = JsonParser.parseString(json).getAsJsonArray();
            for (JsonElement element : array) {
                JsonObject object = element.getAsJsonObject();
                Material material = Material.matchMaterial(object.get("material").getAsString());
                if (material != null) {
                    list.add(new PaletteEntry(material, object.get("weight").getAsDouble()));
                }
            }
        } catch (RuntimeException ignored) {
            // Malformed palette JSON falls back to an empty palette (-> STONE).
        }
        return new MinePalette(list);
    }
}
