package su.vellaris.shulkers;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.ShulkerBox;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

public class ShulkerGUI implements Listener {

    private final VelShulkers plugin;
    private final ShulkerDataManager dataManager;

    private final Map<UUID, String> openMenus = new HashMap<>();
    private final Map<UUID, Integer> pages = new HashMap<>();

    private static final int ITEMS_PER_PAGE = 36;

    public ShulkerGUI(VelShulkers plugin, ShulkerDataManager dataManager) {
        this.plugin = plugin;
        this.dataManager = dataManager;
    }

    // ========== ОТКРЫТИЕ / ОТРИСОВКА ==========

    public void openMenu(Player player, String type) {
        String menuType = type.toLowerCase();

        ShulkerMenuHolder holder = new ShulkerMenuHolder(menuType);
        Inventory inv = Bukkit.createInventory(holder, 54,
                MiniMessage.miniMessage().deserialize(titleFor(menuType)));
        holder.setInventory(inv);

        // Если у игрока было открыто другое меню — оно закроется здесь,
        // и CloseEvent вычистит СТАРЫЕ данные до того, как мы запишем новые
        player.openInventory(inv);

        openMenus.put(player.getUniqueId(), menuType);
        pages.put(player.getUniqueId(), 0);

        renderPage(player);
    }

    /**
     * Рисует страницу ПРЯМО в уже открытом инвентаре.
     * Никакого openInventory() — значит, никакого ложного CloseEvent.
     */
    public void renderPage(Player player) {
        String type = openMenus.get(player.getUniqueId());
        if (type == null) return;

        Inventory inv = player.getOpenInventory().getTopInventory();
        if (!(inv.getHolder() instanceof ShulkerMenuHolder holder)
                || !type.equals(holder.getMenuType())) {
            return; // меню не открыто — ничего не рисуем
        }

        int page = pages.getOrDefault(player.getUniqueId(), 0);
        List<ShulkerEntry> entries = dataManager.getListByType(type);

        int totalPages = Math.max(1, (int) Math.ceil((double) entries.size() / ITEMS_PER_PAGE));
        page = Math.min(Math.max(page, 0), totalPages - 1);
        pages.put(player.getUniqueId(), page);

        inv.clear();

        int start = page * ITEMS_PER_PAGE;
        int end = Math.min(start + ITEMS_PER_PAGE, entries.size());

        for (int i = start; i < end; i++) {
            ShulkerEntry entry = entries.get(i);

            ItemStack display = dataManager.createMenuItem(entry);
            ItemMeta meta = display.getItemMeta();
            if (meta != null) {
                List<Component> lore = new ArrayList<>();
                lore.add(Component.text("ID: " + entry.getId(), NamedTextColor.GRAY));
                lore.add(Component.empty());
                lore.add(Component.text("ЛКМ - получить шалкер", NamedTextColor.GREEN));
                lore.add(Component.text("Shift+ЛКМ - удалить из файла", NamedTextColor.RED));

                meta.lore(ShulkerDataManager.withoutItalic(lore)); // ← обернули
                display.setItemMeta(meta);
            }

            inv.setItem(i - start, display);
        }

        // Навигация и кнопки
        // Навигация и кнопки
        if (page > 0) {
            inv.setItem(45, createButton(Material.ARROW, "<yellow>← Предыдущая страница"));
        }
        if (page < totalPages - 1) {
            inv.setItem(53, createButton(Material.ARROW, "<yellow>Следующая страница →"));
        }

        inv.setItem(47, createButton(Material.BOOK,
                "<white>Страница: <yellow>" + (page + 1) + "<white>/<yellow>" + totalPages +
                        "\n<white>Всего шалкеров: <yellow>" + entries.size()));
        inv.setItem(49, createButton(Material.LIME_STAINED_GLASS, "<green>+ Добавить шалкер из курсора/руки"));
        inv.setItem(50, createButton(Material.BARRIER, "<red>Закрыть"));
    }

    private String titleFor(String type) {
        return switch (type) {
            case "premium" -> "<gold>Premium <white>Шалкеры";
            case "premiumplus", "premium+" -> "<aqua>Premium+ <white>Шалкеры";
            case "default" -> "<blue>Стандартные <white>киты";
            default -> "<green>Базовые <white>Шалкеры";
        };
    }

    private ItemStack createButton(Material mat, String name) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(ShulkerDataManager.withoutItalic(
                    MiniMessage.miniMessage().deserialize(name)));
            item.setItemMeta(meta);
        }
        return item;
    }

    // ========== КЛИКИ ==========

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        // Опознаём меню по holder'у, а не по карте — надёжнее
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof ShulkerMenuHolder holder)) return;

        // Клик "вне" инвентаря
        if (event.getClickedInventory() == null) {
            event.setCancelled(true);
            return;
        }

        int rawSlot = event.getRawSlot();

        // Клики в инвентаре игрока: разрешаем брать в курсор
        if (rawSlot < 0 || rawSlot >= top.getSize()) {
            if (event.isShiftClick() || event.getClick() == ClickType.DOUBLE_CLICK) {
                event.setCancelled(true);
            }
            return;
        }

        // Всё в меню отменяем ВСЕГДА, пока оно открыто
        event.setCancelled(true);

        String type = holder.getMenuType();
        int page = pages.getOrDefault(player.getUniqueId(), 0);
        List<ShulkerEntry> entries = dataManager.getListByType(type);
        int slot = rawSlot;

        // Предыдущая страница
        if (slot == 45) {
            if (page > 0) {
                pages.put(player.getUniqueId(), page - 1);
                renderPage(player);
            }
            return;
        }

        // Следующая страница
        if (slot == 53) {
            int totalPages = Math.max(1, (int) Math.ceil((double) entries.size() / ITEMS_PER_PAGE));
            if (page < totalPages - 1) {
                pages.put(player.getUniqueId(), page + 1);
                renderPage(player);
            }
            return;
        }

        // Закрыть
        if (slot == 50) {
            player.closeInventory();
            return;
        }

        // Добавить шалкер
        if (slot == 49) {
            ItemStack source = event.getCursor();
            boolean fromCursor = true;

            if (isEmpty(source) || !isShulkerBox(source.getType())) {
                source = player.getInventory().getItemInMainHand();
                fromCursor = false;
            }

            if (isEmpty(source) || !isShulkerBox(source.getType())) {
                player.sendMessage(MiniMessage.miniMessage()
                        .deserialize("<red>Возьмите шалкер в курсор или в руку и нажмите эту кнопку!</red>"));
                return;
            }

            String id = "shulker_" + System.currentTimeMillis();
            String name = "<light_purple>Шалкер #" + (entries.size() + 1) + "</light_purple>";

            ItemMeta sourceMeta = source.getItemMeta();
            if (sourceMeta != null && sourceMeta.hasDisplayName() && sourceMeta.displayName() != null) {
                name = MiniMessage.miniMessage().serialize(sourceMeta.displayName());
            }

            List<Map<String, Object>> items = extractShulkerContents(source);

            ShulkerEntry entry = new ShulkerEntry(id, name, items);
            entry.setMaterial(source.getType().name());

            dataManager.addShulker(type, entry);

            if (fromCursor) {
                if (source.getAmount() > 1) {
                    source.setAmount(source.getAmount() - 1);
                    event.setCursor(source);
                } else {
                    event.setCursor(new ItemStack(Material.AIR));
                }
            } else {
                if (source.getAmount() > 1) {
                    source.setAmount(source.getAmount() - 1);
                } else {
                    player.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
                }
            }

            player.updateInventory();
            player.sendMessage(MiniMessage.miniMessage()
                    .deserialize("<green>Шалкер добавлен! ID: " + id + "</green>"));
            renderPage(player); // просто перерисовка, без переоткрытия
            return;
        }

        // Клик по сохранённому шалкеру
        if (slot >= 0 && slot < ITEMS_PER_PAGE) {
            int index = page * ITEMS_PER_PAGE + slot;
            if (index >= entries.size()) return;

            ShulkerEntry entry = entries.get(index);

            if (event.isShiftClick()) {
                dataManager.removeShulker(type, entry.getId());
                player.sendMessage(MiniMessage.miniMessage()
                        .deserialize("<red>Шалкер '" + entry.getId() + "' удалён!</red>"));
                renderPage(player);
                return;
            }

            ItemStack shulker = dataManager.createShulkerItem(entry);
            Map<Integer, ItemStack> leftover = player.getInventory().addItem(shulker);
            if (!leftover.isEmpty()) {
                for (ItemStack item : leftover.values()) {
                    player.getWorld().dropItemNaturally(player.getLocation(), item);
                }
            }
            player.sendMessage(MiniMessage.miniMessage()
                    .deserialize("<green>Вы получили шалкер: " + entry.getName() + "</green>"));
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!(event.getView().getTopInventory().getHolder() instanceof ShulkerMenuHolder)) return;

        int topSize = event.getView().getTopInventory().getSize();
        for (int rawSlot : event.getRawSlots()) {
            if (rawSlot >= 0 && rawSlot < topSize) {
                event.setCancelled(true);
                return;
            }
        }
    }

    // ========== СЛУЖЕБНОЕ ==========

    public void handleMenuClose(Player player) {
        openMenus.remove(player.getUniqueId());
        pages.remove(player.getUniqueId());
    }

    public String getOpenMenuType(UUID uuid) {
        return openMenus.get(uuid);
    }

    private boolean isEmpty(ItemStack item) {
        return item == null || item.getType() == Material.AIR;
    }

    private boolean isShulkerBox(Material material) {
        return material != null && material.name().endsWith("SHULKER_BOX");
    }

    private List<Map<String, Object>> extractShulkerContents(ItemStack shulkerItem) {
        List<Map<String, Object>> result = new ArrayList<>();

        if (!(shulkerItem.getItemMeta() instanceof BlockStateMeta bsm)) {
            return result;
        }

        if (!(bsm.getBlockState() instanceof ShulkerBox shulkerBox)) {
            return result;
        }

        // Получаем содержимое шалкера с позициями
        ItemStack[] contents = shulkerBox.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack item = contents[i];
            if (item == null || item.getType() == Material.AIR) continue;

            // Сохраняем слот и сам предмет
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("slot", i);
            entry.put("item", dataManager.serializeItem(item));
            result.add(entry);
        }

        return result;
    }
}