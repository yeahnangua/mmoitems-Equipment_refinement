package com.example.enchantviewer;

import io.lumine.mythic.lib.api.item.ItemTag;
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

import java.util.*;
import java.util.logging.Logger;

public class MigrateGUI implements Listener {

    private final Map<UUID, BukkitRunnable> activeMigrations = new HashMap<>();
    private final Set<UUID> migratingPlayers = new HashSet<>();
    private final Map<UUID, ItemStack> itemsInProcess = new HashMap<>();
    private final Map<UUID, ItemStack> materialsConsumed = new HashMap<>();

    private final EnchantViewer plugin;
    private final Logger logger;
    private final LocaleManager locale;
    private static final int GUI_SIZE = 45;
    private int ITEM_SLOT;
    private int MATERIAL_SLOT;
    private int BUTTON_SLOT;
    private boolean animationEnabled;

//    private static final int ITEM_SLOT = 22; // Center slot of 3x3 grid
//    private static final int MATERIAL_SLOT = 24; // Right of the 3x3 grid
//    private static final int BUTTON_SLOT = 40;


    // Animation constants
    // Sequence: 8-9-6-3-2-1-4-7 around the center item
    private static final long ANIMATION_TICK_DELAY = 2L; // 2 ticks = 0.1 seconds


    public MigrateGUI(EnchantViewer plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.locale = plugin.getLocaleManager();

        FileConfiguration config = plugin.getConfig();
        this.ITEM_SLOT = config.getInt("gui.slots.item", 22);
        this.MATERIAL_SLOT = config.getInt("gui.slots.material", 24);
        this.BUTTON_SLOT = config.getInt("gui.slots.button", 40);
        this.animationEnabled = config.getBoolean("gui.animation.enabled", true);

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
        UUID playerUUID = player.getUniqueId();
        int clickedSlot = event.getRawSlot();
        Inventory inventory = event.getInventory();

        // Prevent any interaction while a migration is in progress for this player.
        if (migratingPlayers.contains(playerUUID)) {
            event.setCancelled(true);
            return;
        }

        // Allow placing/taking items only from item/material slots.
        if (clickedSlot < GUI_SIZE && clickedSlot != ITEM_SLOT && clickedSlot != MATERIAL_SLOT) {
            event.setCancelled(true);
        }

        // Handle button click
        if (clickedSlot == BUTTON_SLOT) {
            event.setCancelled(true); // Prevent taking the anvil

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
            int requiredAmount = 0;
            if (config.getBoolean("migration-cost.enabled", true)) {
                String requiredType = config.getString("migration-cost.material.type");
                String requiredId = config.getString("migration-cost.material.id");
                requiredAmount = config.getInt("migration-cost.material.amount", 1);

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
            }
            // --- End Material Cost Check ---

            migratingPlayers.add(playerUUID);
            itemsInProcess.put(playerUUID, itemToMigrate.clone());

            // Consume material
            if (requiredAmount > 0) {
                ItemStack consumed = materialItem.clone();
                consumed.setAmount(requiredAmount);
                materialsConsumed.put(playerUUID, consumed);

                int newAmount = materialItem.getAmount() - requiredAmount;
                if (newAmount > 0) {
                    materialItem.setAmount(newAmount);
                    inventory.setItem(MATERIAL_SLOT, materialItem);
                } else {
                    inventory.setItem(MATERIAL_SLOT, null);
                }
            }

            // 判断是否启用动画
            if (!animationEnabled) {
                ItemStack newItem = performMigration(player, itemToMigrate.clone());
                if (newItem != null) {
                    player.sendMessage(locale.getMessage("gui.success"));
                    player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 1.0f, 1.0f);
                    inventory.setItem(ITEM_SLOT, newItem);
                } else {
                    inventory.setItem(ITEM_SLOT, itemToMigrate);
                }
                cleanup(player, inventory);
                return;
            }

            // --- Start Animated Migration ---
            final ItemStack finalItemToMigrate = itemToMigrate.clone();
            final List<Integer> animationOrder = getAnimationOrder(ITEM_SLOT); // 动态生成动画轨迹

            // Replace button with placeholder
            ItemStack placeholder = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
            ItemMeta placeholderMeta = placeholder.getItemMeta();
            placeholderMeta.setDisplayName(" ");
            placeholder.setItemMeta(placeholderMeta);
            inventory.setItem(BUTTON_SLOT, placeholder);

            BukkitRunnable migrationTask = new BukkitRunnable() {
                private int step = 0;

                @Override
                public void run() {
                    if (!player.isOnline() || !player.getOpenInventory().getTitle().equals(locale.getRawMessage("gui.title"))) {
                        this.cancel();
                        return;
                    }

                    if (step < animationOrder.size()) {
                        ItemStack yellowPane = new ItemStack(Material.YELLOW_STAINED_GLASS_PANE);
                        ItemMeta meta = yellowPane.getItemMeta();
                        meta.setDisplayName(" ");
                        yellowPane.setItemMeta(meta);

                        inventory.setItem(animationOrder.get(step), yellowPane);
                        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 1.0f, 1.2f);
                        step++;
                    } else {
                        this.cancel();
                        ItemStack newItem = performMigration(player, finalItemToMigrate);

                        if (newItem != null) {
                            player.sendMessage(locale.getMessage("gui.success"));
                            ItemStack greenPane = new ItemStack(Material.LIME_STAINED_GLASS_PANE);
                            ItemMeta meta = greenPane.getItemMeta();
                            meta.setDisplayName(" ");
                            greenPane.setItemMeta(meta);
                            for (int slot : animationOrder) {
                                inventory.setItem(slot, greenPane);
                            }
                            player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 1.0f, 1.0f);
                            inventory.setItem(ITEM_SLOT, newItem);

                            new BukkitRunnable() {
                                @Override
                                public void run() {
                                    if (player.getOpenInventory().getTitle().equals(locale.getRawMessage("gui.title"))) {
                                        ItemStack grayPane = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
                                        ItemMeta meta = grayPane.getItemMeta();
                                        meta.setDisplayName(" ");
                                        grayPane.setItemMeta(meta);
                                        for (int slot : animationOrder) {
                                            inventory.setItem(slot, grayPane);
                                        }
                                    }
                                    cleanup(player, inventory);
                                }
                            }.runTaskLater(plugin, 20L); // 延迟1秒恢复
                        } else {
                            // Migration failed
                            inventory.setItem(ITEM_SLOT, finalItemToMigrate);
                            ItemStack grayPane = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
                            ItemMeta meta = grayPane.getItemMeta();
                            meta.setDisplayName(" ");
                            grayPane.setItemMeta(meta);
                            for (int slot : animationOrder) {
                                inventory.setItem(slot, grayPane);
                            }
                            cleanup(player, inventory);
                        }
                    }
                }
            };

            activeMigrations.put(playerUUID, migrationTask);
            migrationTask.runTaskTimer(plugin, 0L, ANIMATION_TICK_DELAY);
        }
    }

    private List<Integer> getAnimationOrder(int centerSlot) {
        int row = centerSlot / 9;
        int col = centerSlot % 9;
        List<Integer> path = new ArrayList<>();

        // 顺时针环绕路径
        if (row > 0 && col > 0) path.add((row - 1) * 9 + (col - 1)); // 左上
        if (row > 0) path.add((row - 1) * 9 + col);                   // 上
        if (row > 0 && col < 8) path.add((row - 1) * 9 + (col + 1)); // 右上
        if (col < 8) path.add(row * 9 + (col + 1));                   // 右
        if (row < 4 && col < 8) path.add((row + 1) * 9 + (col + 1)); // 右下
        if (row < 4) path.add((row + 1) * 9 + col);                   // 下
        if (row < 4 && col > 0) path.add((row + 1) * 9 + (col - 1)); // 左下
        if (col > 0) path.add(row * 9 + (col - 1));                   // 左

        return path;
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

        // --- NBT Data Transfer ---
        NBTItem newNbt = NBTItem.get(newItem);

        for (String key : nbtItemToMigrate.getTags()) {
            if (!key.equals("MMOITEMS_ITEM_STATS") && !key.equals("MMOITEMS_LORE")) {
                // As corrected by user, create a new ItemTag object
                newNbt.addTag(new ItemTag(key, nbtItemToMigrate.get(key)));
            }
        }
        logger.info("Transferred auxiliary NBT data from original item.");

        newItem = newNbt.toItem();
        // --- End NBT Data Transfer ---

        logger.info("Original enchantments found: " + originalEnchantments);
        logger.info("Original repair cost: " + repairCost);
        logger.info("Equippable component found: " + (equippable != null));

        ItemMeta newMeta = newItem.getItemMeta();
        if (newMeta != null) {
            // Re-apply vanilla enchantments and repair cost, as they are not part of the MMOItems NBT copy
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

    private void cleanup(Player player, Inventory inventory) {
        UUID playerUUID = player.getUniqueId();
        migratingPlayers.remove(playerUUID);
        activeMigrations.remove(playerUUID);
        itemsInProcess.remove(playerUUID);
        materialsConsumed.remove(playerUUID);

        // Restore the button only if the GUI is still open
        if (player.isOnline() && player.getOpenInventory().getTitle().equals(locale.getRawMessage("gui.title"))) {
            ItemStack migrateButton = new ItemStack(Material.ANVIL);
            ItemMeta buttonMeta = migrateButton.getItemMeta();
            buttonMeta.setDisplayName(locale.getRawMessage("gui.button-name"));
            buttonMeta.setLore(Collections.singletonList(locale.getRawMessage("gui.button-lore")));
            migrateButton.setItemMeta(buttonMeta);
            inventory.setItem(BUTTON_SLOT, migrateButton);
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!event.getView().getTitle().equals(locale.getRawMessage("gui.title"))) {
            return;
        }

        Player player = (Player) event.getPlayer();
        UUID playerUUID = player.getUniqueId();
        Inventory gui = event.getInventory();

        // Check if the player was in the middle of a migration
        if (migratingPlayers.contains(playerUUID)) {
            BukkitRunnable task = activeMigrations.get(playerUUID);
            if (task != null) {
                task.cancel();
            }

            // Return the original item that was being processed
            ItemStack originalItem = itemsInProcess.get(playerUUID);
            if (originalItem != null) {
                player.getInventory().addItem(originalItem);
            }

            // Return the materials that were consumed for the process
            ItemStack consumed = materialsConsumed.get(playerUUID);
            if (consumed != null) {
                player.getInventory().addItem(consumed);
            }

            // Also return any materials that were left over in the slot
            ItemStack materialLeft = gui.getItem(MATERIAL_SLOT);
            if (materialLeft != null && materialLeft.getType() != Material.AIR) {
                player.getInventory().addItem(materialLeft);
            }

            // Clean up all tracking for the player
            migratingPlayers.remove(playerUUID);
            activeMigrations.remove(playerUUID);
            itemsInProcess.remove(playerUUID);
            materialsConsumed.remove(playerUUID);

            logger.info("Migration interrupted for " + player.getName() + ". Returned items.");
        } else {
            // Standard GUI close, not during an active migration
            ItemStack itemToMigrate = gui.getItem(ITEM_SLOT);
            if (itemToMigrate != null && itemToMigrate.getType() != Material.AIR) {
                player.getInventory().addItem(itemToMigrate);
            }
            ItemStack materialItem = gui.getItem(MATERIAL_SLOT);
            if (materialItem != null && materialItem.getType() != Material.AIR) {
                player.getInventory().addItem(materialItem);
            }
            logger.info("Returned items to " + player.getName() + " after closing GUI.");
        }

        // Always clear the slots to prevent item duplication issues
        gui.clear(ITEM_SLOT);
        gui.clear(MATERIAL_SLOT);
    }
}
