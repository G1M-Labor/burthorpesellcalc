package com.burthorpesellcalc;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

@ConfigGroup("burthorpesellcalc")
public interface burthorpecalcconfig extends Config {

    @ConfigSection(
            name = "Bank Tag Integration Info",
            description = "Information regarding Bank Tag integration. Right-click, reset this 'Bank Tag Guide' to reset infobox.",
            position = 0,
            closedByDefault = false
    )
    String integrationSection = "integrationSection";

    @ConfigItem(
            keyName = "tagNoticeText",
            name = "Bank Tag Guide",
            description = "",
            position = 1,
            section = "integrationSection"
    )
    default String tagNoticeBox() {
        return "Selected items are automatically imported to the 'shopscape' bank tag. Please create this tag in-game to ensure easy item access. Warning: excluding items in layout mode requires the layout to be enabled and disabled to apply.";
    }

    enum ValueFormatMode {
        ROUNDED("Rounded"),
        PRECISE("Precise");

        private final String name;

        ValueFormatMode(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    @ConfigItem(
            keyName = "valueFormat",
            name = "Value Format",
            description = "Decide how gp values show within shift right-click and your bank header",
            position = 2
    )
    default ValueFormatMode valueFormat() {
        return ValueFormatMode.ROUNDED;
    }

    @ConfigItem(keyName = "shiftBankMenu", name = "Shift Right-click : Bank Menu", description = "Toggles Shift right-click options within the Bank", position = 3)
    default boolean shiftBankMenu() { return true; }

    @ConfigItem(keyName = "shiftInventoryMenu", name = "Shift Right-click : Inventory Menu", description = "Toggles Shift right-click options within the inventory", position = 4)
    default boolean shiftInventoryMenu() { return true; }

    @ConfigItem(keyName = "enableBankHighlight", name = "Enable Bank Highlight", description = "Toggles highlighting included items within the bank", position = 5)
    default boolean enableBankHighlight() { return true; }

    @ConfigItem(keyName = "enableInventoryHighlight", name = "Enable Shop Highlight", description = "Toggles highlighting included items within the shop", position = 6)
    default boolean enableInventoryHighlight() { return true; }

    @ConfigItem(keyName = "clearInclusionsToggle", name = "Clear Database", description = "Clear all included items and their sell quantities", position = 7)
    default boolean clearInclusionsToggle() { return false; }

    @ConfigItem(keyName = "includedItemIds", name = "", description = "", hidden = true)
    default String includedItemIds() { return ""; }

    @ConfigItem(keyName = "includedItemIds", name = "", description = "")
    void setIncludedItemIds(String val);
}
