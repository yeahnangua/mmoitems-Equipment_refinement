package com.example.enchantviewer;

import org.bukkit.plugin.java.JavaPlugin;

public class EnchantViewer extends JavaPlugin {

    private static EnchantViewer instance;
    private LocaleManager localeManager;

    @Override
    public void onEnable() {
        instance = this;

        // Setup LocaleManager
        this.localeManager = new LocaleManager(this);
        this.localeManager.load();

        // Register the command executors
        this.getCommand("getenchants").setExecutor(new GetEnchantsCommand(this));

        // Initialize GUI and register its command
        MigrateGUI migrateGUI = new MigrateGUI(this);
        this.getCommand("enchmigrate").setExecutor(new OpenMigrateGUICommand(migrateGUI));
        this.getCommand("nbtinfo").setExecutor(new NBTInfoCommand(this));

        getLogger().info("EnchantViewer has been enabled with GUI and localization!");
    }

    @Override
    public void onDisable() {
        getLogger().info("EnchantViewer has been disabled!");
    }

    public static EnchantViewer getInstance() {
        return instance;
    }

    public LocaleManager getLocaleManager() {
        return localeManager;
    }
}
