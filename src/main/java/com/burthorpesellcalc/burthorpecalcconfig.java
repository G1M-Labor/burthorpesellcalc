package com.burthorpesellcalc;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("burthorpesellcalc")
public interface burthorpecalcconfig extends Config {

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
            description = "Decide how gp values show within shift rightclick and your bank header",
            position = 1
    )
    default ValueFormatMode valueFormat() {
        return ValueFormatMode.ROUNDED;
    }

    @ConfigItem(keyName = "shiftBankMenu", name = "Shift Bank Menu", description = "", position = 2)
    default boolean shiftBankMenu() { return true; }

    @ConfigItem(keyName = "shiftInventoryMenu", name = "Shift Inventory Menu", description = "", position = 3)
    default boolean shiftInventoryMenu() { return true; }

    @ConfigItem(keyName = "enableBankHighlight", name = "Enable Bank Highlight", description = "", position = 4)
    default boolean enableBankHighlight() { return true; }

    @ConfigItem(keyName = "enableInventoryHighlight", name = "Enable Inventory Highlight", description = "", position = 5)
    default boolean enableInventoryHighlight() { return true; }

    @ConfigItem(keyName = "clearInclusionsToggle", name = "Clear Database", description = "", position = 6)
    default boolean clearInclusionsToggle() { return false; }

    @ConfigItem(keyName = "includedItemIds", name = "", description = "", hidden = true)
    default String includedItemIds() { return ""; }

    @ConfigItem(keyName = "includedItemIds", name = "", description = "")
    void setIncludedItemIds(String val);
}
