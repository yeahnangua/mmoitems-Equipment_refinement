# Gemini Project Analysis: EnchantViewer

## Project Overview

This project, `EnchantViewer`, has evolved into a powerful utility for server administrators dealing with `MMOItems` and `ItemsAdder` integration. Its primary purpose is to solve a specific compatibility issue: allowing `MMOItems` armor to correctly display `ItemsAdder` custom wearable textures.

The plugin achieves this by "refreshing" an `MMOItem`. It carefully extracts key data (enchantments, repair cost, and crucially, the `ItemsAdder`-provided `EquippableComponent`), generates a completely new `MMOItem` to re-roll its random stats, and then meticulously applies the saved data back onto the new item. This results in an item that has both fresh `MMOItems` stats and the correct `ItemsAdder` appearance.

## Dependencies

The project is built with Java 17 and managed by Maven. It depends on:
*   Spigot API (1.21.4)
*   MMOItems
*   MythicLib

**Note:** While `ItemsAdder` is not a direct code dependency, this plugin's core feature is only useful on servers where `ItemsAdder` is also installed and used for custom armor models.

### Key Features:

*   **MMOItems Refresh & Texture Fix:** The core workflow allows players to regenerate an `MMOItem` to get new random stats while preserving its `ItemsAdder` wearable texture.
*   **GUI Interface:** A primary, user-friendly workflow is provided through a graphical interface opened with the `/enchmigrate` command.
*   **Command-Based Workflow:** A secondary workflow for administrators is available via the `/getenchants` command.
*   **Configurable Cost:** The GUI-based migration has a configurable cost, defined by an `MMOItem` type and ID in the `config.yml`.
*   **NBT Inspector:** A debugging command, `/nbtinfo`, allows administrators to view the full NBT data of any held item in the server console.
*   **Localization:** All user-facing text is fully localizable through the `config.yml` file.

### Architecture:

*   **`EnchantViewer.java`**: The main plugin class that initializes all components, including commands and listeners.
*   **`LocaleManager.java`**: A helper class for loading and formatting all text from `config.yml`.
*   **`MigrateGUI.java`**: A `Listener` class that creates and manages the GUI for item migration.
*   **CommandExecutors**: `GetEnchantsCommand.java`, `OpenMigrateGUICommand.java`, and `NBTInfoCommand.java` handle the logic for their respective commands.

## Building and Running

### Prerequisites

*   Java 17 JDK
*   Apache Maven
*   A Spigot (or compatible) server for Minecraft 1.21.4.
*   The `MMOItems`, `MythicLib`, and `ItemsAdder` plugins must be installed on the server.

### Building

To build the plugin, run the following command from the project root directory:

```shell
mvn clean package
```

The compiled plugin, `enchantviewer-1.0-SNAPSHOT.jar`, will be located in the `target/` directory.

## Development Conventions

*   **Configuration:** All user-facing text is managed in `src/main/resources/config.yml`.
*   **Dependencies:** `MMOItems` and `MythicLib` are included as system-path dependencies from the `lib/` directory.
*   **Component API:** The plugin uses Bukkit's modern `ItemMeta` component API to read and write the `EquippableComponent`, which is essential for preserving the `ItemsAdder` texture.