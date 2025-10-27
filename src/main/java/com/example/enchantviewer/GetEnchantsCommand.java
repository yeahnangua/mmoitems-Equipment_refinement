package com.example.enchantviewer;

import io.lumine.mythic.lib.api.item.NBTItem;
import net.Indyuce.mmoitems.MMOItems;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.Repairable;
import org.bukkit.inventory.meta.components.EquippableComponent;

import java.util.Map;
import java.util.logging.Logger;

public class GetEnchantsCommand implements CommandExecutor {

    private final Logger logger;
    private final LocaleManager locale;

    public GetEnchantsCommand(EnchantViewer plugin) {
        this.logger = plugin.getLogger();
        this.locale = plugin.getLocaleManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("enchantviewer.use")) {
            sender.sendMessage(locale.getMessage("no-permission"));
            return true;
        }

        if (args.length != 2) {
            sender.sendMessage(locale.getMessage("command.usage").replace("<command>", label));
            return false;
        }

        Player target = Bukkit.getPlayer(args[0]);
        if (target == null) {
            sender.sendMessage(locale.getMessage("command.player-not-found", "%player%", args[0]));
            return true;
        }

        String hand = args[1].toLowerCase();
        ItemStack item;

        if (hand.equals("mainhand")) {
            item = target.getInventory().getItemInMainHand();
        } else if (hand.equals("offhand")) {
            item = target.getInventory().getItemInOffHand();
        } else {
            sender.sendMessage(locale.getMessage("command.invalid-hand"));
            return false;
        }

        logger.info("----------------------------------------");
        logger.info("Processing item for " + target.getName() + "'s " + hand + " via command...");

        if (item == null || item.getType().isAir()) {
            logger.info(target.getName() + " is not holding any item in their " + hand + ".");
            sender.sendMessage(locale.getMessage("prefix") + "§e" + target.getName() + " is not holding any item in their " + hand + ".");
            logger.info("----------------------------------------");
            return true;
        }

        NBTItem nbtItem = NBTItem.get(item);

        if (nbtItem.hasTag("MMOITEMS_ITEM_TYPE") && nbtItem.hasTag("MMOITEMS_ITEM_ID")) {

            // 1. Save data to be preserved from the old item
            Map<Enchantment, Integer> originalEnchantments = item.getEnchantments();
            ItemMeta oldMeta = item.getItemMeta();

            int repairCost = 0;
            if (oldMeta instanceof Repairable) {
                repairCost = ((Repairable) oldMeta).getRepairCost();
            }

            //EquippableComponent equippable = oldMeta.getComponent(EquippableComponent.class);
            EquippableComponent equippable = oldMeta.getEquippable();

            // 2. Get MMOItems identifiers
            String itemType = nbtItem.getString("MMOITEMS_ITEM_TYPE");
            String itemID = nbtItem.getString("MMOITEMS_ITEM_ID");
            logger.info("MMOItem detected. Type: " + itemType + ", ID: " + itemID);

            // 3. Generate a new item using MMOItems to get fresh random stats
            ItemStack newItem = MMOItems.plugin.getItem(itemType, itemID);

            if (newItem == null) {
                logger.severe("Failed to generate new MMOItem. Type or ID might be invalid.");
                sender.sendMessage(locale.getMessage("gui.error")); // Reusing gui error message
                logger.info("----------------------------------------");
                return true;
            }

            // 4. Apply the preserved data to the new item
            ItemMeta newMeta = newItem.getItemMeta();

            // Apply enchantments
            if (!originalEnchantments.isEmpty()) {
                newItem.addUnsafeEnchantments(originalEnchantments);
            }

            // Apply repair cost
            if (repairCost > 0 && newMeta instanceof Repairable) {
                ((Repairable) newMeta).setRepairCost(repairCost);
            }

            // Apply the Equippable component for the ItemsAdder texture
            if (equippable != null) {
                // newMeta.setComponent(EquippableComponent.class, equippable);
                newMeta.setEquippable(equippable);
                logger.info("Successfully transferred Equippable component.");
            }

            newItem.setItemMeta(newMeta);

            logger.info("Generated new item (Command): " + newItem.toString());

            // 5. Update player's hand
            if (hand.equals("mainhand")) {
                target.getInventory().setItemInMainHand(newItem);
            } else {
                target.getInventory().setItemInOffHand(newItem);
            }

            logger.info("Successfully replaced item in " + target.getName() + "'s " + hand + ".");

            // Inform both the command sender and the target player
            sender.sendMessage(locale.getMessage("command.item-given", "%player%", target.getName()));
            if (sender != target) {
                target.sendMessage(locale.getMessage("command.refreshed"));
            }

        } else {
            logger.warning("The held item is not an MMOItem. Cannot perform operation.");
            sender.sendMessage(locale.getMessage("command.not-mmoitem"));
        }

        logger.info("----------------------------------------");
        return true;
    }
}