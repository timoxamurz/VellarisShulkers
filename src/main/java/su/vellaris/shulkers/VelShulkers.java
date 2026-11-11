package su.vellaris.shulkers;

import org.bukkit.plugin.java.JavaPlugin;

import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class VelShulkers extends JavaPlugin {

    private ShulkerDataManager dataManager;
    private ShulkerGUI gui;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        dataManager = new ShulkerDataManager(this);
        dataManager.loadAll();
        gui = new ShulkerGUI(this, dataManager);

        PluginCommand cmd = getCommand("velshulkers");
        if (cmd != null) {
            ShulkerCommand executor = new ShulkerCommand(this, dataManager, gui);
            cmd.setExecutor(executor);
            cmd.setTabCompleter(executor);
        }

        getServer().getPluginManager().registerEvents(new ShulkerListener(this, dataManager, gui), this);
        getServer().getPluginManager().registerEvents(gui, this);

        getLogger().info("Vellaris Shulkers has been enabled");
    }

    @Override
    public void onDisable() {
        dataManager.saveAll();
        getLogger().info("Vellaris Shulkers has been disabled. Goodbye!");
    }

    public ShulkerDataManager getDataManager() {
        return dataManager;
    }

    public ShulkerGUI getGui() {
        return gui;
    }
}
