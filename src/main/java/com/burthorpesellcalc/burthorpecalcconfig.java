package com.github.g1mlabor.burthorpesellcalc;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("burthorpesellcalc")
public interface burthorpecalcconfig extends Config {
    @ConfigItem(
        keyName = "includedItemIds",
        name = "Included Items",
        description = "Saved item IDs tracking inclusions list",
        hidden = true
    )
    default String includedItemIds() { return ""; }
    void setIncludedItemIds(String ids);

    @ConfigItem(
        position = 1,
        keyName = "enableBankHighlight",
        name = "Enable Bank Outline",
        description = "Toggle the clean colored outline strokes on matching items inside your banking grid views"
    )
    default boolean enableBankHighlight() { return true; }

    @ConfigItem(
        position = 2,
        keyName = "enableInventoryHighlight",
        name = "Enable Inventory Outline",
        description = "Toggle the clean colored outline strokes on matching items inside your player inventory slots"
    )
    default boolean enableInventoryHighlight() { return true; }

    @ConfigItem(
        position = 3,
        keyName = "shiftBankMenu",
        name = "Shift Right Click Menu - Bank",
        description = "Allow the custom configuration set choices when shift right-clicking items inside the bank vault"
    )
    default boolean shiftBankMenu() { return true; }

    @ConfigItem(
        position = 4,
        keyName = "shiftInventoryMenu",
        name = "Shift Right Click - Shop",
        description = "Allow the custom configuration set choices when shift right-clicking items inside your player inventory / shop layouts"
    )
    default boolean shiftInventoryMenu() { return true; }

    @ConfigItem(
        position = 5,
        keyName = "menuValueDisplayMode",
        name = "Value format",
        description = "Choose how numbers represent inside your right-click choices text rows"
    )
    default MenuValueFormat menuValueDisplayMode() { return MenuValueFormat.PRECISE; }

    @ConfigItem(
        position = 6,
        keyName = "clearInclusionsToggle",
        name = "Delete All Inclusions",
        description = "Check this box to immediately clear all saved item listings from your tracking database"
    )
    default boolean clearInclusionsToggle() { return false; }

    enum MenuValueFormat {
        PRECISE("Precise"),
        ROUNDED("Rounded");

        private final String name;
        MenuValueFormat(String name) { this.name = name; }
        @Override public String toString() { return name; }
    }
}
