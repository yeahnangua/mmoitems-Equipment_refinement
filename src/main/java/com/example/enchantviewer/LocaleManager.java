package com.example.enchantviewer;

import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;

public class LocaleManager {

    private final EnchantViewer plugin;
    private FileConfiguration config;

    public LocaleManager(EnchantViewer plugin) {
        this.plugin = plugin;
    }

    public void load() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        this.config = plugin.getConfig();
    }

    public String getMessage(String path, String... replacements) {
        String message = config.getString("messages." + path, "&cMissing message: " + path);

        // Apply prefix if it's not a GUI title or lore
        if (!path.startsWith("gui.title") && !path.startsWith("gui.button-lore")) {
            message = config.getString("messages.prefix", "") + message;
        }

        // Apply placeholders
        for (int i = 0; i < replacements.length; i += 2) {
            if (i + 1 < replacements.length) {
                message = message.replace(replacements[i], replacements[i + 1]);
            }
        }

        return ChatColor.translateAlternateColorCodes('&', message);
    }

    // Gets a message without the global prefix, useful for GUI components.
    public String getRawMessage(String path, String... replacements) {
        String message = config.getString("messages." + path, "&cMissing message: " + path);
        for (int i = 0; i < replacements.length; i += 2) {
            if (i + 1 < replacements.length) {
                message = message.replace(replacements[i], replacements[i + 1]);
            }
        }
        return ChatColor.translateAlternateColorCodes('&', message);
    }
}
