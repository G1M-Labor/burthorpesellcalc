package com.BurthorpeSellCalc;

import net.runelite.api.*;
import net.runelite.api.events.ClientTick;
import net.runelite.client.eventbus.Subscribe;

import javax.inject.Inject;

public class BurthorpeMenuSwapper {
    private final Client client;
    private final BurthorpeCalcPlugin plugin;
    private final BurthorpeCalcConfig config;

    @Inject
    public BurthorpeMenuSwapper(Client client, BurthorpeCalcPlugin plugin, BurthorpeCalcConfig config) {
        this.client = client;
        this.plugin = plugin;
        this.config = config;
    }

    @Subscribe
    public void onClientTick(ClientTick event) {
        if (client.isMenuOpen()) {
            return;
        }

        Menu rootMenu = client.getMenu();
        MenuEntry[] menuEntries = rootMenu.getMenuEntries();
        if (menuEntries == null || menuEntries.length == 0) {
            return;
        }

        MenuEntry valueEntry = null;
        for (int i = menuEntries.length - 1; i >= 0; i--) {
            MenuEntry entry = menuEntries[i];
            String option = entry.getOption();
            if (option != null && option.contains("Value")) {
                valueEntry = entry;
                break;
            }
        }

        if (valueEntry == null) {
            return;
        }

        int itemId = valueEntry.getItemId();

        if (!plugin.isItemIncluded(itemId)) {
            return;
        }

        // FIXED: Replaced deprecated InventoryID call with the reliable container index ID 93
        ItemContainer inventory = client.getItemContainer(93);
        if (inventory == null) {
            return;
        }

        int qtyOwned = inventory.count(itemId);
        int targetAmount; // FIXED: Removed redundant initializer warning

        Integer forcedBatch = plugin.getForcedBatchSize(itemId);
        if (forcedBatch != null) {
            targetAmount = forcedBatch;
        } else {
            if (qtyOwned >= config.highThreshold()) {
                targetAmount = 50;
            } else if (qtyOwned >= config.mediumThreshold()) {
                targetAmount = 10;
            } else if (qtyOwned >= config.lowThreshold()) {
                targetAmount = 5;
            } else if (qtyOwned >= config.defaultThreshold()) {
                targetAmount = 1;
            } else {
                targetAmount = 1;
            }
        }

        String coloredOptionText;
        switch (targetAmount) {
            case 1:
                coloredOptionText = "<col=00ffff>Sell 1</col>"; // Cyan
                break;
            case 5:
                coloredOptionText = "<col=00ff00>Sell 5</col>"; // Green
                break;
            case 10:
                coloredOptionText = "<col=ffff00>Sell 10</col>"; // Yellow
                break;
            case 50:
                coloredOptionText = "<col=ff0000>Sell 50</col>"; // Red
                break;
            default:
                coloredOptionText = "Sell " + targetAmount;
                break;
        }

        // FIXED: Replaced the manual array copy warnings with an optimized structural clone
        MenuEntry[] rebuiltEntries = menuEntries.clone();
        MenuEntry leftClickSlot = rebuiltEntries[rebuiltEntries.length - 1];

        leftClickSlot.setOption(coloredOptionText);
        leftClickSlot.setIdentifier(valueEntry.getIdentifier());
        leftClickSlot.setParam1(valueEntry.getParam1());
        leftClickSlot.setItemId(itemId);
        leftClickSlot.setType(MenuAction.CC_OP);

        switch (targetAmount) {
            case 1:
                leftClickSlot.setParam0(1); // Mapped parameters for single sale actions
                break;
            case 5:
                leftClickSlot.setParam0(2); // Mapped parameters for Sell 5
                break;
            case 10:
                leftClickSlot.setParam0(3); // Mapped parameters for Sell 10
                break;
            case 50:
                leftClickSlot.setParam0(4); // Mapped parameters for Sell 50
                break;
            default:
                leftClickSlot.setParam0(valueEntry.getParam0());
                break;
        }

        rootMenu.setMenuEntries(rebuiltEntries);
    }
}
