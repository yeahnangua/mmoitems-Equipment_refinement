package com.example.enchantviewer;

import io.lumine.mythic.lib.api.item.NBTItem;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.logging.Logger;

public class NBTInfoCommand implements CommandExecutor {

    private final Logger logger;
    private final LocaleManager locale;

    public NBTInfoCommand(EnchantViewer plugin) {
        this.logger = plugin.getLogger();
        this.locale = plugin.getLocaleManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(locale.getMessage("player-only"));
            return true;
        }

        Player player = (Player) sender;
        ItemStack item = player.getInventory().getItemInMainHand();

        if (item == null || item.getType().isAir()) {
            player.sendMessage(locale.getMessage("prefix") + "§c请手持一个物品再执行此命令。");
            return true;
        }

        NBTItem nbtItem = NBTItem.get(item);
        String nbtString = nbtItem.getItem().toString();

        logger.info("--- NBT Info for item: " + item.getType() + " ---");
        logger.info(nbtString);
        logger.info("--- End of NBT Info ---");

        player.sendMessage(locale.getMessage("prefix") + "§a物品的NBT信息已成功输出到服务器控制台。");

        return true;
    }
}
