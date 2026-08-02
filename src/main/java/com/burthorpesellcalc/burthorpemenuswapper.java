package com.burthorpesellcalc;

import net.runelite.api.*;
import net.runelite.api.events.ClientTick;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.widgets.Widget;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;

import javax.inject.Inject;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

public class burthorpemenuswapper {
    private final Client client;
    private final burthorpecalcplugin plugin;
    private final ItemManager itemManager;

    private int lastTrackedWorld = -1;
    private final Set<Integer> worldSoldItemIds = new HashSet<>();

    @Inject
    public burthorpemenuswapper(Client client, burthorpecalcplugin plugin, ItemManager itemManager) {
        this.client = client;
        this.plugin = plugin;
        this.itemManager = itemManager;
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged event) {
        if (event.getGameState() == GameState.HOPPING || event.getGameState() == GameState.LOGGING_IN) {
            worldSoldItemIds.clear();
            lastTrackedWorld = client.getWorld();
        }
    }

    @Subscribe
    public void onClientTick(ClientTick event) {
        if (client.isMenuOpen()) {
            return;
        }

        int currentWorld = client.getWorld();
        if (currentWorld != lastTrackedWorld && currentWorld != 0) {
            worldSoldItemIds.clear();
            lastTrackedWorld = currentWorld;
        }

        Menu rootMenu = client.getMenu();
        MenuEntry[] menuEntries = rootMenu.getMenuEntries();
        if (menuEntries == null || menuEntries.length == 0) {
            return;
        }

        MenuEntry defaultLeftClickSlot = menuEntries[menuEntries.length - 1];
        if (defaultLeftClickSlot == null) {
            return;
        }

        int rawItemId = defaultLeftClickSlot.getItemId();
        int canonicalId = itemManager.canonicalize(rawItemId);

        if (!plugin.isItemIncluded(canonicalId)) {
            return;
        }

        boolean itemHasShopStock = false;
        Widget shopGrid = client.getWidget(301, 16);

        if (shopGrid == null || shopGrid.isHidden()) {
            shopGrid = client.getWidget(300, 16);
        }

        if (shopGrid != null && !shopGrid.isHidden()) {
            Widget[] items = shopGrid.getDynamicChildren();
            if (items != null && items.length > 0) {
                for (Widget itemWidget : items) {
                    if (itemWidget != null && itemManager.canonicalize(itemWidget.getItemId()) == canonicalId) {
                        if (itemWidget.getItemQuantity() > 0) {
                            itemHasShopStock = true;
                            break;
                        }
                    }
                }
            }
        }

        int targetAmount = 1;
        Integer forcedBatch = plugin.getForcedBatchSize(canonicalId);
        if (forcedBatch != null) {
            targetAmount = forcedBatch;
        }

        boolean isSellAllMode = (targetAmount == burthorpecalcplugin.SELL_AMOUNT_ALL);
        int nativeLookupAmount = isSellAllMode ? 50 : targetAmount;

        // Bypasses overstock logic completely for Sell All mode
        if (!isSellAllMode) {
            if (!itemHasShopStock) {
                worldSoldItemIds.remove(canonicalId);
            }
            if (worldSoldItemIds.contains(canonicalId) || itemHasShopStock) {
                return;
            }
        }

        ItemContainer inventory = client.getItemContainer(93);
        if (inventory == null) {
            return;
        }

        MenuEntry nativeTargetOption = null;
        String regexPattern = "^Sell " + nativeLookupAmount + "\\b";
        Pattern pattern = Pattern.compile(regexPattern);

        for (MenuEntry entry : menuEntries) {
            String option = entry.getOption();
            if (option != null && pattern.matcher(option).find() && entry.getItemId() == rawItemId) {
                nativeTargetOption = entry;
                break;
            }
        }

        if (nativeTargetOption != null) {
            String coloredOptionText;
            if (isSellAllMode) {
                coloredOptionText = "<col=ff0000>Sell All (50s)</col>";
            } else {
                switch (targetAmount) {
                    case 1: coloredOptionText = "<col=00ffff>Sell 1</col>"; break;
                    case 5: coloredOptionText = "<col=00ff00>Sell 5</col>"; break;
                    case 10: coloredOptionText = "<col=ffff00>Sell 10</col>"; break;
                    case 50: coloredOptionText = "<col=ff9800>Sell 50</col>"; break;
                    default: coloredOptionText = "Sell " + targetAmount; break;
                }
            }

            defaultLeftClickSlot.setOption(coloredOptionText);
            defaultLeftClickSlot.setIdentifier(nativeTargetOption.getIdentifier());
            defaultLeftClickSlot.setParam0(nativeTargetOption.getParam0());
            defaultLeftClickSlot.setParam1(nativeTargetOption.getParam1());
            defaultLeftClickSlot.setType(nativeTargetOption.getType()); // FIX: Retains target interaction packets directly

            if (!isSellAllMode) {
                defaultLeftClickSlot.onClick(e -> worldSoldItemIds.add(canonicalId));
            }
            rootMenu.setMenuEntries(menuEntries);
        }
    }
}
