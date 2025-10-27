package com.example.enchantviewer;

import io.lumine.mythic.lib.api.item.NBTItem;
import net.Indyuce.mmoitems.MMOItems;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.Repairable;
import org.bukkit.inventory.meta.components.EquippableComponent;

import java.util.Collections;
import java.util.Map;
import java.util.logging.Logger;

public class MigrateGUI implements Listener {

    private final EnchantViewer plugin;
    private final Logger logger;
    private final LocaleManager locale;
    private static final int GUI_SIZE = 45;
    private static final int ITEM_SLOT = 21; // Slot 4
    private static final int MATERIAL_SLOT = 23; // Slot 6
    private static final int BUTTON_SLOT = 40;

    public MigrateGUI(EnchantViewer plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.locale = plugin.getLocaleManager();
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void open(Player player) {
        String guiTitle = locale.getRawMessage("gui.title");
        Inventory gui = Bukkit.createInventory(null, GUI_SIZE, guiTitle);

        ItemStack placeholder = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta placeholderMeta = placeholder.getItemMeta();
        placeholderMeta.setDisplayName(" ");
        placeholder.setItemMeta(placeholderMeta);

        ItemStack migrateButton = new ItemStack(Material.ANVIL);
        ItemMeta buttonMeta = migrateButton.getItemMeta();
        buttonMeta.setDisplayName(locale.getRawMessage("gui.button-name"));
        buttonMeta.setLore(Collections.singletonList(locale.getRawMessage("gui.button-lore")));
        migrateButton.setItemMeta(buttonMeta);

        for (int i = 0; i < GUI_SIZE; i++) {
            gui.setItem(i, placeholder);
        }

        gui.setItem(ITEM_SLOT, null);
        gui.setItem(MATERIAL_SLOT, null);
        gui.setItem(BUTTON_SLOT, migrateButton);

        player.openInventory(gui);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!event.getView().getTitle().equals(locale.getRawMessage("gui.title"))) {
            return;
        }

        Player player = (Player) event.getWhoClicked();
        int clickedSlot = event.getRawSlot();

        if (clickedSlot < GUI_SIZE && clickedSlot != ITEM_SLOT && clickedSlot != MATERIAL_SLOT) {
            event.setCancelled(true);
        }

        if (clickedSlot == BUTTON_SLOT) {
            ItemStack itemToMigrate = event.getInventory().getItem(ITEM_SLOT);
            ItemStack materialItem = event.getInventory().getItem(MATERIAL_SLOT);

            if (itemToMigrate == null || itemToMigrate.getType() == Material.AIR) {
                player.sendMessage(locale.getMessage("gui.item-required"));
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                return;
            }

            NBTItem nbtItemToMigrate = NBTItem.get(itemToMigrate);
            if (!nbtItemToMigrate.hasTag("MMOITEMS_ITEM_TYPE") || !nbtItemToMigrate.hasTag("MMOITEMS_ITEM_ID")) {
                player.sendMessage(locale.getMessage("gui.not-mmoitem"));
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                return;
            }

            // --- Material Cost Check ---
            FileConfiguration config = plugin.getConfig();
            if (config.getBoolean("migration-cost.enabled", true)) {
                String requiredType = config.getString("migration-cost.material.type");
                String requiredId = config.getString("migration-cost.material.id");
                int requiredAmount = config.getInt("migration-cost.material.amount", 1);

                if (materialItem == null || materialItem.getType() == Material.AIR) {
                    player.sendMessage(locale.getMessage("gui.material-required"));
                    player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                    return;
                }

                NBTItem nbtMaterial = NBTItem.get(materialItem);
                String materialType = nbtMaterial.getString("MMOITEMS_ITEM_TYPE");
                String materialId = nbtMaterial.getString("MMOITEMS_ITEM_ID");

                if (!requiredType.equals(materialType) || !requiredId.equals(materialId)) {
                    player.sendMessage(locale.getMessage("gui.invalid-material"));
                    player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                    return;
                }

                if (materialItem.getAmount() < requiredAmount) {
                    player.sendMessage(locale.getMessage("gui.insufficient-material", "%amount%", String.valueOf(requiredAmount)));
                    player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                    return;
                }

                // Consume material
                materialItem.setAmount(materialItem.getAmount() - requiredAmount);
                event.getInventory().setItem(MATERIAL_SLOT, materialItem);
            }
            // --- End Material Cost Check ---


            // --- Start Migration Logic (User's Plan) ---

            // 1. Save data to be preserved from the old item
            Map<Enchantment, Integer> originalEnchantments = itemToMigrate.getEnchantments();
            ItemMeta oldMeta = itemToMigrate.getItemMeta();

            int repairCost = 0;
            if (oldMeta instanceof Repairable) {
                repairCost = ((Repairable) oldMeta).getRepairCost();
            }

            //EquippableComponent equippable = oldMeta.getComponent(EquippableComponent.class);
            EquippableComponent equippable = oldMeta.getEquippable();

            // 2. Get MMOItems identifiers
            String itemType = nbtItemToMigrate.getString("MMOITEMS_ITEM_TYPE");
            String itemID = nbtItemToMigrate.getString("MMOITEMS_ITEM_ID");
            logger.info("Starting GUI migration for " + player.getName() + ". MMOItem Type: " + itemType + ", ID: " + itemID);

            // 3. Generate a new item using MMOItems to get fresh random stats
            ItemStack newItem = MMOItems.plugin.getItem(itemType, itemID);
            if (newItem == null) {
                logger.severe("Failed to generate new MMOItem via GUI. Type or ID might be invalid.");
                player.sendMessage(locale.getMessage("gui.error"));
                return;
            }

            // 4. Apply the preserved data to the new item
            logger.info("Original enchantments found: " + originalEnchantments);
            logger.info("Original repair cost: " + repairCost);
            logger.info("Equippable component found: " + (equippable != null));

            ItemMeta newMeta = newItem.getItemMeta();

            // Apply enchantments
            if (newMeta != null) {
                if (!originalEnchantments.isEmpty()) {
                    for (Map.Entry<Enchantment, Integer> entry : originalEnchantments.entrySet()) {
                        newMeta.addEnchant(entry.getKey(), entry.getValue(), true);
                    }
                    logger.info("Applied enchantments to new item meta.");
                }

                // Apply repair cost
                if (repairCost > 0 && newMeta instanceof Repairable) {
                    ((Repairable) newMeta).setRepairCost(repairCost);
                    logger.info("Applied repair cost to new item meta.");
                }

                // Apply the Equippable component for the ItemsAdder texture
                if (equippable != null) {
                    newMeta.setEquippable(equippable);
                    logger.info("Successfully transferred Equippable component.");
                }

                newItem.setItemMeta(newMeta);
                logger.info("Final item meta applied to new item.");
            } else {
                logger.warning("Could not get ItemMeta for the new item. Migration of enchants/cost/texture might fail.");
                // Fallback for enchantments if meta is null for some reason
                if (!originalEnchantments.isEmpty()) {
                    newItem.addUnsafeEnchantments(originalEnchantments);
                    logger.info("Applied enchantments directly to item as a fallback.");
                }
            }

            // logger.info("Generated new item (GUI): " + newItem.toString());

            // 5. Update the GUI
            final ItemStack finalNewItem = newItem;
            Bukkit.getScheduler().runTask(plugin, () -> event.getInventory().setItem(ITEM_SLOT, finalNewItem));

            player.sendMessage(locale.getMessage("gui.success"));
            player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 1.0f, 1.0f);
            // --- End Migration Logic ---
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!event.getView().getTitle().equals(locale.getRawMessage("gui.title"))) {
            return;
        }

        Inventory gui = event.getInventory();
        Player player = (Player) event.getPlayer();

        ItemStack itemToMigrate = gui.getItem(ITEM_SLOT);
        ItemStack materialItem = gui.getItem(MATERIAL_SLOT);

        if (itemToMigrate != null && itemToMigrate.getType() != Material.AIR) {
            player.getInventory().addItem(itemToMigrate);
        }
        if (materialItem != null && materialItem.getType() != Material.AIR) {
            player.getInventory().addItem(materialItem);
        }

        gui.clear(ITEM_SLOT);
        gui.clear(MATERIAL_SLOT);

        logger.info("Returned items to " + player.getName() + " after closing GUI.");
    }
}
