package com.example.enchantviewer;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class OpenMigrateGUICommand implements CommandExecutor {

    private final MigrateGUI migrateGUI;
    private final LocaleManager locale;

    public OpenMigrateGUICommand(MigrateGUI migrateGUI) {
        this.migrateGUI = migrateGUI;
        this.locale = EnchantViewer.getInstance().getLocaleManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(locale.getMessage("player-only"));
            return true;
        }

        if (!sender.hasPermission("enchantviewer.gui")) {
            sender.sendMessage(locale.getMessage("no-permission"));
            return true;
        }

        Player player = (Player) sender;
        migrateGUI.open(player);
        return true;
    }
}
