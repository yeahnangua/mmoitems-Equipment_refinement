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
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

public class MigrateGUI implements Listener {

    private final EnchantViewer plugin;
    private final Logger logger;
    private final LocaleManager locale;
    private static final int GUI_SIZE = 45;
    private static final int ITEM_SLOT = 22; // Center slot of 3x3 grid
    private static final int MATERIAL_SLOT = 24; // Right of the 3x3 grid
    private static final int BUTTON_SLOT = 40;

    // Animation constants
    // Sequence: 8-9-6-3-2-1-4-7 around the center item
    private static final List<Integer> ANIMATION_ORDER = Arrays.asList(31, 32, 23, 14, 13, 12, 21, 30);
    private static final List<Integer> BORDER_SLOTS = Arrays.asList(12, 13, 14, 21, 23, 30, 31, 32);
    private static final long ANIMATION_TICK_DELAY = 2L; // 2 ticks = 0.1 seconds

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
        Inventory inventory = event.getInventory();

        if (clickedSlot < GUI_SIZE && clickedSlot != ITEM_SLOT && clickedSlot != MATERIAL_SLOT) {
            event.setCancelled(true);
        }

        if (clickedSlot == BUTTON_SLOT) {
            ItemStack itemToMigrate = inventory.getItem(ITEM_SLOT);
            ItemStack materialItem = inventory.getItem(MATERIAL_SLOT);

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
                inventory.setItem(MATERIAL_SLOT, materialItem);
            }
            // --- End Material Cost Check ---

            // --- Start Animation & Migration ---
            final ItemStack finalItemToMigrate = itemToMigrate.clone();
            inventory.setItem(ITEM_SLOT, null); // Remove item during animation

            new BukkitRunnable() {
                private int step = 0;
                private final ItemStack yellowPane = new ItemStack(Material.YELLOW_STAINED_GLASS_PANE);
                private final ItemMeta paneMeta = yellowPane.getItemMeta();

                {
                    paneMeta.setDisplayName(" ");
                    yellowPane.setItemMeta(paneMeta);
                }

                @Override
                public void run() {
                    if (step < ANIMATION_ORDER.size()) {
                        inventory.setItem(ANIMATION_ORDER.get(step), yellowPane);
                        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 1.0f, 1.2f);
                        step++;
                    } else {
                        this.cancel();
                        ItemStack newItem = performMigration(player, finalItemToMigrate);

                        if (newItem != null) {
                            player.sendMessage(locale.getMessage("gui.success"));
                            ItemStack greenPane = new ItemStack(Material.LIME_STAINED_GLASS_PANE);
                            paneMeta.setDisplayName(" ");
                            greenPane.setItemMeta(paneMeta);
                            for (int slot : BORDER_SLOTS) {
                                inventory.setItem(slot, greenPane);
                            }
                            player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 1.0f, 1.0f);
                            inventory.setItem(ITEM_SLOT, newItem);

                            new BukkitRunnable() {
                                @Override
                                public void run() {
                                    ItemStack grayPane = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
                                    paneMeta.setDisplayName(" ");
                                    grayPane.setItemMeta(paneMeta);
                                    for (int slot : BORDER_SLOTS) {
                                        inventory.setItem(slot, grayPane);
                                    }
                                }
                            }.runTaskLater(plugin, 20L); // 1 second
                        } else {
                            // Migration failed, error message sent in performMigration
                            inventory.setItem(ITEM_SLOT, finalItemToMigrate);
                            ItemStack grayPane = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
                            paneMeta.setDisplayName(" ");
                            grayPane.setItemMeta(paneMeta);
                            for (int slot : BORDER_SLOTS) {
                                inventory.setItem(slot, grayPane);
                            }
                        }
                    }
                }
            }.runTaskTimer(plugin, 0L, ANIMATION_TICK_DELAY);
        }
    }

    private ItemStack performMigration(Player player, ItemStack itemToMigrate) {
        NBTItem nbtItemToMigrate = NBTItem.get(itemToMigrate);
        Map<Enchantment, Integer> originalEnchantments = itemToMigrate.getEnchantments();
        ItemMeta oldMeta = itemToMigrate.getItemMeta();

        int repairCost = 0;
        if (oldMeta instanceof Repairable) {
            repairCost = ((Repairable) oldMeta).getRepairCost();
        }

        EquippableComponent equippable = oldMeta.getEquippable();
        String itemType = nbtItemToMigrate.getString("MMOITEMS_ITEM_TYPE");
        String itemID = nbtItemToMigrate.getString("MMOITEMS_ITEM_ID");
        logger.info("Starting GUI migration for " + player.getName() + ". MMOItem Type: " + itemType + ", ID: " + itemID);

        ItemStack newItem = MMOItems.plugin.getItem(itemType, itemID);
        if (newItem == null) {
            logger.severe("Failed to generate new MMOItem via GUI. Type or ID might be invalid.");
            player.sendMessage(locale.getMessage("gui.error"));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return null;
        }

        logger.info("Original enchantments found: " + originalEnchantments);
        logger.info("Original repair cost: " + repairCost);
        logger.info("Equippable component found: " + (equippable != null));

        ItemMeta newMeta = newItem.getItemMeta();
        if (newMeta != null) {
            if (!originalEnchantments.isEmpty()) {
                for (Map.Entry<Enchantment, Integer> entry : originalEnchantments.entrySet()) {
                    newMeta.addEnchant(entry.getKey(), entry.getValue(), true);
                }
                logger.info("Applied enchantments to new item meta.");
            }

            if (repairCost > 0 && newMeta instanceof Repairable) {
                ((Repairable) newMeta).setRepairCost(repairCost);
                logger.info("Applied repair cost to new item meta.");
            }

            if (equippable != null) {
                newMeta.setEquippable(equippable);
                logger.info("Successfully transferred Equippable component.");
            }

            newItem.setItemMeta(newMeta);
            logger.info("Final item meta applied to new item.");
        } else {
            logger.warning("Could not get ItemMeta for the new item. Migration of enchants/cost/texture might fail.");
            if (!originalEnchantments.isEmpty()) {
                newItem.addUnsafeEnchantments(originalEnchantments);
                logger.info("Applied enchantments directly to item as a fallback.");
            }
        }
        return newItem;
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