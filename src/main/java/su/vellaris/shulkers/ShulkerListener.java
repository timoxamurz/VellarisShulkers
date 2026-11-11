package su.vellaris.shulkers;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class ShulkerListener implements Listener {

    private final ShulkerGUI gui;

    public ShulkerListener(VelShulkers plugin, ShulkerDataManager dataManager, ShulkerGUI gui) {
        this.gui = gui;
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        // Реагируем ТОЛЬКО на закрытие нашего меню
        if (event.getInventory().getHolder() instanceof ShulkerMenuHolder
                && event.getPlayer() instanceof Player player) {
            gui.handleMenuClose(player);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        gui.handleMenuClose(event.getPlayer());
    }
}