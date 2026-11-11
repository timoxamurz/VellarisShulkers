package su.vellaris.shulkers;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Collectors;

public class ShulkerCommand implements CommandExecutor, TabCompleter {

    private final VelShulkers plugin;
    private final ShulkerDataManager dataManager;
    private final ShulkerGUI gui;

    public ShulkerCommand(VelShulkers plugin, ShulkerDataManager dataManager, ShulkerGUI gui) {
        this.plugin = plugin;
        this.dataManager = dataManager;
        this.gui = gui;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd,
                             @NotNull String label, @NotNull String[] args) {

        if (args.length < 1) {
            sendUsage(sender);
            return true;
        }

        String sub = args[0].toLowerCase();

        switch (sub) {
            case "open" -> handleOpen(sender, args);
            case "giverandom" -> handleGiveRandom(sender, args);
            case "reload" -> {
                if (!sender.hasPermission("velshulkers.admin")) {
                    sender.sendMessage(MiniMessage.miniMessage()
                            .deserialize("<red>Нет прав!</red>"));
                    return true;
                }
                dataManager.loadAll();
                sender.sendMessage(MiniMessage.miniMessage()
                        .deserialize("<green>Данные перезагружены!</green>"));
            }
            default -> sendUsage(sender);
        }
        return true;
    }

    private void handleOpen(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Только игрок!");
            return;
        }
        if (!player.hasPermission("velshulkers.admin")) {
            player.sendMessage(MiniMessage.miniMessage()
                    .deserialize("<red>Нет прав!</red>"));
            return;
        }
        if (args.length < 2) {
            player.sendMessage(MiniMessage.miniMessage()
                    .deserialize("<red>Использование: /velshulkers open <basic|premium|premiumplus></red>"));
            return;
        }

        String type = args[1].toLowerCase();
        if (!type.equals("basic") && !type.equals("premium")
                && !type.equals("premiumplus") && !type.equals("default")) {
            player.sendMessage(MiniMessage.miniMessage()
                    .deserialize("<red>Неизвестный тип! Используйте: basic, premium, premiumplus, default</red>"));
            return;
        }

        gui.openMenu(player, type);
    }

    private void handleGiveRandom(CommandSender sender, String[] args) {
        if (!sender.hasPermission("velshulkers.giverandom")) {
            sender.sendMessage(MiniMessage.miniMessage()
                    .deserialize("<red>Нет прав!</red>"));
            return;
        }

        int amount = 1;
        if (args.length >= 2) {
            try {
                amount = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                sender.sendMessage(MiniMessage.miniMessage()
                        .deserialize("<red>Неверное число!</red>"));
                return;
            }
        }

        List<ShulkerEntry> basicPool = dataManager.getBasicShulkers();
        if (basicPool.isEmpty()) {
            sender.sendMessage(MiniMessage.miniMessage()
                    .deserialize("<red>Нет шалкеров в пуле!</red>"));
            return;
        }

        int givenTotal = 0;
        for (Player online : Bukkit.getOnlinePlayers()) {
            int given = giveRandomShulkers(online, basicPool, amount);
            givenTotal += given;
        }

        sender.sendMessage(MiniMessage.miniMessage()
                .deserialize("<green>Раздано " + givenTotal + " шалкеров всем игрокам!</green>"));
    }

    /**
     * Выдаёт игроку случайные неповторяющиеся шалкеры.
     * Каждый шалкер имеет 50% шанс выпасть.
     * Если у игрока есть права premium / premiumplus - гарантированно выдаётся кит.
     */
    private int giveRandomShulkers(Player player, List<ShulkerEntry> pool, int amount) {
        int guaranteed = 0;
        int randomGiven = 0;

        // === 1. Базовый кит — гарантированно ВСЕМ (100%), даже премиумщикам ===
        // === 1. Базовые киты — гарантированно ВСЕМ (100%), даже премиумщикам ===
        List<ShulkerEntry> defaultPool = dataManager.getDefaultShulkers();
        if (!defaultPool.isEmpty()) {
            for (ShulkerEntry defaultKit : defaultPool) {
                giveShulker(player, defaultKit);
                guaranteed++;
            }
            player.sendMessage(MiniMessage.miniMessage()
                    .deserialize("<blue>Вам выданы базовые киты (" + defaultPool.size() + " шт.)!</blue>"));
        }

        // === 2. Гарантированные премиум-киты ===
        if (player.hasPermission("velshulkers.premiumplus") || player.hasPermission("velshulkers.premium")) {
            if (player.hasPermission("velshulkers.premiumplus")) {
                List<ShulkerEntry> ppPool = dataManager.getPremiumPlusShulkers();
                if (!ppPool.isEmpty()) {
                    ShulkerEntry ppKit = ppPool.get(new Random().nextInt(ppPool.size()));
                    giveShulker(player, ppKit);
                    guaranteed++;
//                    player.sendMessage(MiniMessage.miniMessage()
//                            .deserialize("<gold>Вам выдан Premium+ кит!</gold>"));
                }
            }
            else if (player.hasPermission("velshulkers.premium")) {
                List<ShulkerEntry> premPool = dataManager.getPremiumShulkers();
                if (!premPool.isEmpty()) {
                    ShulkerEntry premKit = premPool.get(new Random().nextInt(premPool.size()));
                    giveShulker(player, premKit);
                    guaranteed++;
//                    player.sendMessage(MiniMessage.miniMessage()
//                            .deserialize("<gold>Вам выдан Premium кит!</gold>"));
                }
            }
        }

        // === 3. Случайные шалкеры: amount влияет ТОЛЬКО на них ===
        List<ShulkerEntry> shuffled = new ArrayList<>(pool);
        Collections.shuffle(shuffled);

        int toRoll = Math.min(amount, shuffled.size());
        for (int i = 0; i < toRoll; i++) {
            if (Math.random() < 0.5) {
                giveShulker(player, shuffled.get(i));
                randomGiven++;
            }
        }

        // === Итоговое сообщение ===
        int total = guaranteed + randomGiven;
        if (total > 0) {
            player.sendMessage(MiniMessage.miniMessage()
                    .deserialize("<green>Вы получили " + total + " шалкер(ов): "
                            + guaranteed + " гарантированных + "
                            + randomGiven + " случайных!</green>"));
        }

        return total;
    }

    private void giveShulker(Player player, ShulkerEntry entry) {
        ItemStack shulker = dataManager.createShulkerItem(entry);
        Map<Integer, ItemStack> leftover = player.getInventory().addItem(shulker);
        if (!leftover.isEmpty()) {
            for (ItemStack item : leftover.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), item);
            }
        }
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage(MiniMessage.miniMessage().deserialize("""
                <gold>=== Velllaris Shulkers ===</gold>
                <white>/velshulkers open <basic|premium|premiumplus> - открыть меню</white>
                <white>/velshulkers giverandom [amount] - раздать случайные шалкеры</white>
                <white>/velshulkers reload - перезагрузить данные</white>
                """));
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command cmd,
                                                @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) {
            return filterStartsWith(List.of("open", "giverandom", "reload"), args[0]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("open")) {
            return filterStartsWith(List.of("default", "basic", "premium", "premiumplus"), args[1]);
        }
        return Collections.emptyList();
    }

    private List<String> filterStartsWith(List<String> options, String input) {
        return options.stream()
                .filter(s -> s.startsWith(input.toLowerCase()))
                .collect(Collectors.toList());
    }
}
