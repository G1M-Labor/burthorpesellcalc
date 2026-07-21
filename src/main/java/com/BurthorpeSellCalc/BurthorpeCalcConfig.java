package com.BurthorpeSellCalc;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("burthorpesellcalc")
public interface BurthorpeCalcConfig extends Config {
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
            keyName = "defaultThreshold",
            name = "Default Threshold (Sell 1)",
            description = "Quantity required to trigger Cyan Sell 1 left-click priority"
    )
    default int defaultThreshold() { return 1; }

    @ConfigItem(
            position = 2,
            keyName = "lowThreshold",
            name = "Low Threshold (Sell 5)",
            description = "Quantity required to trigger Green Sell 5 left-click priority"
    )
    default int lowThreshold() { return 5; }

    @ConfigItem(
            position = 3,
            keyName = "mediumThreshold",
            name = "Medium Threshold (Sell 10)",
            description = "Quantity required to trigger Yellow Sell 10 left-click priority"
    )
    default int mediumThreshold() { return 10; }

    @ConfigItem(
            position = 4,
            keyName = "highThreshold",
            name = "High Threshold (Sell 50)",
            description = "Quantity required to trigger Red Sell 50 left-click priority"
    )
    default int highThreshold() { return 50; }
}
