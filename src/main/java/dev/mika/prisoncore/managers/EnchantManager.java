package dev.mika.prisoncore.managers;

import dev.mika.prisoncore.PrisonCore;
import dev.mika.prisoncore.model.CustomEnchant;
import dev.mika.prisoncore.model.PlayerData;
import dev.mika.prisoncore.pickaxe.PickaxeKeys;
import dev.mika.prisoncore.pickaxe.PickaxeLore;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Owns custom-enchant definitions and the logic to read, write and upgrade enchant
 * levels stored in the pickaxe's persistent data. Passive enchants are applied as
 * held-item effects here; greed and proc enchants are read by the block-break
 * pipeline (M6).
 */
public final class EnchantManager {

    /** Outcome of an upgrade attempt, used to drive user messaging. */
    public enum UpgradeResult {
        SUCCESS,
        MAX_LEVEL,
        NOT_ENOUGH_TOKENS,
        DISABLED
    }

    /** Tuning loaded from {@code enchants.yml} for one enchant. */
    public record EnchantSettings(@NotNull String displayName, int maxLevel, double baseCost,
                                  double costMultiplier, double bonusPerLevel, double procChancePerLevel) {
    }

    private static final int PASSIVE_EFFECT_TICKS = 80;

    private final PrisonCore plugin;
    private final PickaxeKeys keys;
    private final Map<CustomEnchant, EnchantSettings> settings = new EnumMap<>(CustomEnchant.class);

    public EnchantManager(@NotNull PrisonCore plugin, @NotNull PickaxeKeys keys) {
        this.plugin = plugin;
        this.keys = keys;
    }

    /** (Re)load enchant tuning from {@code enchants.yml}. */
    public void load() {
        settings.clear();
        File file = new File(plugin.getDataFolder(), "enchants.yml");
        if (!file.exists()) {
            plugin.getLogger().warning("enchants.yml not found; no enchants available.");
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = yaml.getConfigurationSection("enchants");
        if (root == null) {
            return;
        }
        for (CustomEnchant enchant : CustomEnchant.values()) {
            ConfigurationSection section = root.getConfigurationSection(enchant.configKey());
            if (section == null) {
                continue;
            }
            int maxLevel = section.getInt("max-level", 0);
            if (maxLevel <= 0) {
                continue;
            }
            settings.put(enchant, new EnchantSettings(
                    section.getString("display-name", enchant.defaultDisplayName()),
                    maxLevel,
                    section.getDouble("base-cost", 100),
                    section.getDouble("cost-multiplier", 1.2),
                    section.getDouble("bonus-per-level", 0),
                    section.getDouble("proc-chance-per-level", 0)));
        }
        plugin.getLogger().info("Loaded " + settings.size() + " custom enchants.");
    }

    // ------------------------------------------------------------------
    //  Definitions
    // ------------------------------------------------------------------

    @NotNull
    public List<CustomEnchant> enabledEnchants() {
        List<CustomEnchant> list = new ArrayList<>();
        for (CustomEnchant enchant : CustomEnchant.values()) {
            if (settings.containsKey(enchant)) {
                list.add(enchant);
            }
        }
        return list;
    }

    @Nullable
    public EnchantSettings getSettings(@NotNull CustomEnchant enchant) {
        return settings.get(enchant);
    }

    @NotNull
    public String displayName(@NotNull CustomEnchant enchant) {
        EnchantSettings setting = settings.get(enchant);
        return setting != null ? setting.displayName() : enchant.defaultDisplayName();
    }

    public int maxLevel(@NotNull CustomEnchant enchant) {
        EnchantSettings setting = settings.get(enchant);
        return setting != null ? setting.maxLevel() : 0;
    }

    public double bonusPerLevel(@NotNull CustomEnchant enchant) {
        EnchantSettings setting = settings.get(enchant);
        return setting != null ? setting.bonusPerLevel() : 0;
    }

    public double procChancePerLevel(@NotNull CustomEnchant enchant) {
        EnchantSettings setting = settings.get(enchant);
        return setting != null ? setting.procChancePerLevel() : 0;
    }

    /** Token cost to go from {@code currentLevel} to the next. */
    public double upgradeCost(@NotNull CustomEnchant enchant, int currentLevel) {
        EnchantSettings setting = settings.get(enchant);
        if (setting == null) {
            return 0;
        }
        return setting.baseCost() * Math.pow(setting.costMultiplier(), currentLevel);
    }

    // ------------------------------------------------------------------
    //  Pickaxe state
    // ------------------------------------------------------------------

    public boolean isPrisonPickaxe(@Nullable ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.getPersistentDataContainer()
                .getOrDefault(keys.identity(), PersistentDataType.INTEGER, 0) == 1;
    }

    /** Read an enchant level straight from a container (used on the hot path). */
    public int level(@NotNull PersistentDataContainer container, @NotNull CustomEnchant enchant) {
        return container.getOrDefault(keys.enchant(enchant), PersistentDataType.INTEGER, 0);
    }

    public int getLevel(@NotNull ItemStack item, @NotNull CustomEnchant enchant) {
        ItemMeta meta = item.getItemMeta();
        return meta != null ? level(meta.getPersistentDataContainer(), enchant) : 0;
    }

    /** Set an enchant's level on the pickaxe and rebuild its lore. */
    public void setLevel(@NotNull ItemStack item, @NotNull CustomEnchant enchant, int level) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return;
        }
        meta.getPersistentDataContainer().set(keys.enchant(enchant), PersistentDataType.INTEGER, level);
        if (enchant == CustomEnchant.EFFICIENCY) {
            if (level > 0) {
                meta.addEnchant(Enchantment.EFFICIENCY, Math.min(level, 255), true);
            } else {
                meta.removeEnchant(Enchantment.EFFICIENCY);
            }
        }
        meta.lore(PickaxeLore.build(this, meta.getPersistentDataContainer()));
        item.setItemMeta(meta);
    }

    /**
     * Attempt to upgrade an enchant one level, spending the player's tokens.
     */
    @NotNull
    public UpgradeResult attemptUpgrade(@NotNull PlayerData data, @NotNull ItemStack item,
                                        @NotNull CustomEnchant enchant) {
        EnchantSettings setting = settings.get(enchant);
        if (setting == null) {
            return UpgradeResult.DISABLED;
        }
        int currentLevel = getLevel(item, enchant);
        if (currentLevel >= setting.maxLevel()) {
            return UpgradeResult.MAX_LEVEL;
        }
        long cost = (long) Math.ceil(upgradeCost(enchant, currentLevel));
        if (data.tokens() < cost) {
            return UpgradeResult.NOT_ENOUGH_TOKENS;
        }
        data.addTokens(-cost);
        setLevel(item, enchant, currentLevel + 1);
        return UpgradeResult.SUCCESS;
    }

    // ------------------------------------------------------------------
    //  Passive effects
    // ------------------------------------------------------------------

    /** Apply held-pickaxe movement/haste effects for the player's current levels. */
    public void applyPassiveEffects(@NotNull Player player) {
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (!isPrisonPickaxe(hand)) {
            return;
        }
        applyEffect(player, CustomEnchant.HASTE, PotionEffectType.HASTE, hand);
        applyEffect(player, CustomEnchant.SPEED, PotionEffectType.SPEED, hand);
        applyEffect(player, CustomEnchant.JUMP, PotionEffectType.JUMP_BOOST, hand);
    }

    private void applyEffect(@NotNull Player player, @NotNull CustomEnchant enchant,
                             @NotNull PotionEffectType type, @NotNull ItemStack hand) {
        int level = getLevel(hand, enchant);
        if (level <= 0) {
            return;
        }
        player.addPotionEffect(new PotionEffect(type, PASSIVE_EFFECT_TICKS, level - 1, true, false, false));
    }
}
