package su.vellaris.shulkers;


import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.block.ShulkerBox;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.inventory.meta.ArmorMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.trim.ArmorTrim;
import org.bukkit.inventory.meta.trim.TrimMaterial;
import org.bukkit.inventory.meta.trim.TrimPattern;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class ShulkerDataManager {

    private final VelShulkers plugin;
    private final Gson gson;

    private List<ShulkerEntry> defaultShulkers = new ArrayList<>();
    private List<ShulkerEntry> basicShulkers = new ArrayList<>();
    private List<ShulkerEntry> premiumShulkers = new ArrayList<>();
    private List<ShulkerEntry> premiumPlusShulkers = new ArrayList<>();

    private File defaultFile;
    private File basicFile;
    private File premiumFile;
    private File premiumPlusFile;

    private Material resolveShulkerMaterial(ShulkerEntry entry) {
        if (entry.getMaterial() != null) {
            Material material = Material.matchMaterial(entry.getMaterial());

            if (material != null && material.name().endsWith("SHULKER_BOX")) {
                return material;
            }
        }

        return Material.SHULKER_BOX;
    }

    public ShulkerDataManager(VelShulkers plugin) {
        this.plugin = plugin;
        this.gson = new GsonBuilder().setPrettyPrinting().create();
    }

    public static Component withoutItalic(Component component) {
        return component.decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    public static List<Component> withoutItalic(List<Component> components) {
        List<Component> result = new ArrayList<>(components.size());
        for (Component component : components) {
            result.add(withoutItalic(component));
        }
        return result;
    }

    public void loadAll() {
        File dataFolder = plugin.getDataFolder();
        if (!dataFolder.exists()) dataFolder.mkdirs();

        defaultFile = new File(dataFolder, "default_shulker_data.json");
        basicFile = new File(dataFolder, "shulkers_data.json");
        premiumFile = new File(dataFolder, "premium_shulkers_data.json");
        premiumPlusFile = new File(dataFolder, "premiumplus_shulkers_data.json");

        defaultShulkers = loadFromFile(defaultFile);
        basicShulkers = loadFromFile(basicFile);
        premiumShulkers = loadFromFile(premiumFile);
        premiumPlusShulkers = loadFromFile(premiumPlusFile);
    }

    public void saveAll() {
        saveToFile(defaultFile, defaultShulkers);
        saveToFile(basicFile, basicShulkers);
        saveToFile(premiumFile, premiumShulkers);
        saveToFile(premiumPlusFile, premiumPlusShulkers);
    }

    private List<ShulkerEntry> loadFromFile(File file) {
        if (!file.exists()) {
            try {
                file.createNewFile();
                saveToFile(file, new ArrayList<>());
            } catch (IOException e) {
                plugin.getLogger().severe("Не удалось создать файл: " + file.getName());
            }
            return new ArrayList<>();
        }
        try (Reader reader = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) {
            Type type = new TypeToken<List<ShulkerEntry>>() {}.getType();
            List<ShulkerEntry> result = gson.fromJson(reader, type);
            return result != null ? result : new ArrayList<>();
        } catch (Exception e) {
            plugin.getLogger().severe("Ошибка чтения файла: " + file.getName());
            return new ArrayList<>();
        }
    }

    private void saveToFile(File file, List<ShulkerEntry> data) {
        try (Writer writer = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
            gson.toJson(data, writer);
        } catch (IOException e) {
            plugin.getLogger().severe("Ошибка записи файла: " + file.getName());
        }
    }

    // === Доступ к спискам ===
    public List<ShulkerEntry> getDefaultShulkers() { return defaultShulkers; }
    public List<ShulkerEntry> getBasicShulkers() { return basicShulkers; }
    public List<ShulkerEntry> getPremiumShulkers() { return premiumShulkers; }
    public List<ShulkerEntry> getPremiumPlusShulkers() { return premiumPlusShulkers; }

    public List<ShulkerEntry> getListByType(String type) {
        return switch (type.toLowerCase()) {
            case "premium" -> premiumShulkers;
            case "premiumplus", "premium+" -> premiumPlusShulkers;
            case "default" -> defaultShulkers;
            default -> basicShulkers;
        };
    }

    // === Добавление / удаление ===
    public void addShulker(String type, ShulkerEntry entry) {
        getListByType(type).add(entry);
        saveAll();
    }

    public void removeShulker(String type, String id) {
        getListByType(type).removeIf(e -> e.getId().equalsIgnoreCase(id));
        saveAll();
    }

    public ItemStack createMenuItem(ShulkerEntry entry) {
        ItemStack display = new ItemStack(resolveShulkerMaterial(entry));

        ItemMeta meta = display.getItemMeta();
        if (meta != null) {
            if (entry.getName() != null) {
                meta.displayName(withoutItalic(MiniMessage.miniMessage().deserialize(entry.getName())));
            } else {
                meta.displayName(withoutItalic(Component.text("Шалкер")));
            }

            display.setItemMeta(meta);
        }

        return display;
    }

    // === Создание ItemStack шалкера из entry ===
    public ItemStack createShulkerItem(ShulkerEntry entry) {
        Material boxMaterial = resolveShulkerMaterial(entry);
        ItemStack shulker = new ItemStack(boxMaterial);

        if (!(shulker.getItemMeta() instanceof BlockStateMeta meta)) {
            return shulker;
        }

        String rawName = entry.getName() != null ? entry.getName() : "Шалкер";
        meta.displayName(withoutItalic(MiniMessage.miniMessage().deserialize(rawName)));

        if (meta.getBlockState() instanceof ShulkerBox shulkerBox) {
            Inventory shulkerInv = shulkerBox.getInventory();
            List<Map<String, Object>> items = entry.getItems();

            if (items != null) {
                for (Map<String, Object> itemEntry : items) {
                    Object slotObj = itemEntry.get("slot");
                    Object itemMapObj = itemEntry.get("item");

                    if (slotObj instanceof Number && itemMapObj instanceof Map) {
                        int slot = ((Number) slotObj).intValue();
                        @SuppressWarnings("unchecked")
                        Map<String, Object> itemMap = (Map<String, Object>) itemMapObj;

                        ItemStack itemStack = parseItemFromMap(itemMap);
                        if (itemStack != null && slot >= 0 && slot < shulkerInv.getSize()) {
                            // Расставляем предметы по их исходным позициям
                            shulkerInv.setItem(slot, itemStack);
                        }
                    }
                }
            }

            meta.setBlockState(shulkerBox);
        }

        shulker.setItemMeta(meta);
        return shulker;
    }

    public Map<String, Object> serializeItem(ItemStack item) {
        return item.serialize();
    }

    public static Map<String, Object> serializeTrim(@NotNull ArmorTrim trim) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("material", trim.getMaterial().getKey().toString()); // minecraft:quartz
        result.put("pattern", trim.getPattern().getKey().toString());   // minecraft:ward
        return result;
    }

    private ItemStack parseItemFromMap(Map<String, Object> map) {
        try {
            return ItemStack.deserialize(map);
        } catch (Exception e) {
            plugin.getLogger().warning("Ошибка десериализации предмета: " + e.getMessage());
            return null;
        }
    }
}
