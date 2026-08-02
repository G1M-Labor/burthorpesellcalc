// FIXED PACKAGE DIRECTORY: Shifted package definitions to align exactly with your online com.burthorpesellcalc folder pathway
package com.burthorpesellcalc;

import com.google.inject.Provides;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.api.events.GameTick;
import net.runelite.api.widgets.Widget;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.game.ItemManager;

@Slf4j
@PluginDescriptor(
    name = "Burthorpe Shop Calculator",
    description = "Calculates shop sale yields and appends values seamlessly to the bank title text natively",
    tags = {"bank", "shop", "money", "calc", "ironman"}
)
@SuppressWarnings("deprecation")
public class burthorpecalcplugin extends Plugin {

    @Inject private Client client;
    @Inject private burthorpecalcconfig config;
    @Inject private ItemManager itemManager;
    @Inject private OverlayManager overlayManager;
    @Inject private burthorpeitemoverlay itemOverlay;
    @Inject private EventBus eventBus;
    @Inject private burthorpemenuswapper menuSwapper;
    @Inject private ConfigManager configManager;

    private final Set<Integer> includedItems = new HashSet<>();
    private final Map<Integer, Integer> forcedSellTiers = new HashMap<>();
    private final Set<Integer> worldSoldItemIds = new HashSet<>();
    private final Set<Integer> shopItemsWithStockCache = new HashSet<>();

    private static final int SELL_AMOUNT_DEFAULT = 1;
    private static final int SELL_AMOUNT_LOW = 5;
    private static final int SELL_AMOUNT_MEDIUM = 10;
    private static final int SELL_AMOUNT_HIGH = 50;
    private static final int SELL_AMOUNT_ALL_MARKER = 999999;

    @Provides
    @SuppressWarnings("unused")
    burthorpecalcconfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(burthorpecalcconfig.class);
    }
    @Override
    protected void startUp() throws Exception {
        loadInclusions();
        overlayManager.add(itemOverlay);
        eventBus.register(menuSwapper);
    }

    @Override
    protected void shutDown() throws Exception {
        overlayManager.remove(itemOverlay);
        eventBus.unregister(menuSwapper);
        forcedSellTiers.clear();
        worldSoldItemIds.clear();
        shopItemsWithStockCache.clear();
    }

    public void markItemAsSoldThisWorld(int canonicalId) {
        worldSoldItemIds.add(canonicalId);
    }

    public void clearWorldSoldCache() {
        worldSoldItemIds.clear();
        shopItemsWithStockCache.clear();
    }

    public boolean isItemSoldThisWorld(int canonicalId) {
        return worldSoldItemIds.contains(canonicalId);
    }

    @Subscribe
    public void onGameTick(GameTick event) {
        if (client.getGameState() != GameState.LOGGED_IN) {
            return;
        }

        Widget shopGrid = client.getWidget(301, 16);
        if (shopGrid == null || shopGrid.isHidden()) {
            shopGrid = client.getWidget(300, 16);
        }
        boolean isShopOpen = (shopGrid != null && !shopGrid.isHidden());

        if (!isShopOpen) {
            if (!shopItemsWithStockCache.isEmpty()) {
                shopItemsWithStockCache.clear();
            }
            return;
        }

        Widget[] items = shopGrid.getDynamicChildren();
        if (items == null) {
            return;
        }

        Set<Integer> currentTickStock = new HashSet<>();
        for (Widget itemWidget : items) {
            if (itemWidget != null && itemWidget.getItemId() > 0 && itemWidget.getItemQuantity() > 0) {
                int canonicalId = itemManager.canonicalize(itemWidget.getItemId());
                currentTickStock.add(canonicalId);
            }
        }

        if (!currentTickStock.equals(shopItemsWithStockCache)) {
            for (int id : shopItemsWithStockCache) {
                if (!currentTickStock.contains(id)) {
                    worldSoldItemIds.remove(id);
                }
            }
            
            shopItemsWithStockCache.clear();
            shopItemsWithStockCache.addAll(currentTickStock);
            updateBankTitleValue();
        }
    }

    public boolean isCachedShopStockPresent(int canonicalId) {
        return shopItemsWithStockCache.contains(canonicalId);
    }
    @Subscribe
    public void onConfigChanged(ConfigChanged event) {
        if (!event.getGroup().equals("burthorpesellcalc")) {
            return;
        }

        if (event.getKey().equals("clearInclusionsToggle")) {
            if (!config.clearInclusionsToggle()) {
                return;
            }

            includedItems.clear();
            forcedSellTiers.clear();
            saveInclusions();
            updateBankTitleValue();

            configManager.setConfiguration("burthorpesellcalc", "clearInclusionsToggle", false);
        }
    }

    public boolean isItemIncluded(int itemId) {
        return includedItems.contains(itemManager.canonicalize(itemId));
    }

    public Integer getForcedBatchSize(int itemId) {
        return forcedSellTiers.get(itemManager.canonicalize(itemId));
    }

    public void updateBankTitleValue() {
        Widget bankTitleWidget = client.getWidget(12, 15);
        if (bankTitleWidget == null || bankTitleWidget.isHidden()) {
            return;
        }

        ItemContainer bankContainer = client.getItemContainer(95);
        ItemContainer invContainer = client.getItemContainer(93);

        long totalBankValue = calculateContainerValue(bankContainer);
        long totalInvValue = calculateContainerValue(invContainer);

        String nativeText = bankTitleWidget.getText();
        if (nativeText == null || nativeText.isEmpty()) {
            return;
        }

        nativeText = nativeText.replaceAll("\\s*<col=[0-9a-fA-F]+>\\(SHOP:\\s*[^)]+\\)\\s*\\(INV:\\s*[^)]+\\)</col>", "");
        nativeText = nativeText.replaceAll("\\s*<col=[0-9a-fA-F]+>\\(SHOP:\\s*[^<]+\\)</col>", "");
        nativeText = nativeText.replaceAll("\\s*\\(SHOP:\\s*[^)]+\\)", "");

        String formattedShopStr = formatValue(totalBankValue);
        String formattedInvStr = formatValue(totalInvValue);

        String replacementString = " <col=ff981f>(SHOP: " + formattedShopStr + ") (INV: " + formattedInvStr + ")</col>";
        bankTitleWidget.setText(nativeText + replacementString);
    }

    private long calculateContainerValue(ItemContainer container) {
        if (container == null) {
            return 0;
        }

        long totalValue = 0;
        for (Item item : container.getItems()) {
            if (item == null || item.getId() <= 0 || item.getQuantity() <= 0) {
                continue;
            }

            int itemId = item.getId();
            if (!isItemIncluded(itemId)) {
                continue;
            }

            int batchSize = SELL_AMOUNT_DEFAULT;
            Integer forcedBatch = getForcedBatchSize(itemId);
            if (forcedBatch != null) {
                batchSize = forcedBatch;
            }

            if (batchSize == SELL_AMOUNT_ALL_MARKER) {
                totalValue += calculateContinuousAllYield(itemId, item.getQuantity());
            } else {
                totalValue += calculateProjectedShopYield(itemId, item.getQuantity(), batchSize);
            }
        }
        return totalValue;
    }

    private String formatValue(long value) {
        if (value >= 1_000_000_000) {
            long truncated = value / 100_000_000;
            return (truncated / 10.0) + "B";
        }
        if (value >= 1_000_000) {
            long truncated = value / 100_000;
            return (truncated / 10.0) + "M";
        }
        if (value >= 1_000) {
            long truncated = value / 100;
            return (truncated / 10.0) + "K";
        }
        return String.valueOf(value);
    }

    private String resolveDynamicMenuText(long yieldValue) {
        if (config.menuValueDisplayMode() == burthorpecalcconfig.MenuValueFormat.ROUNDED) {
            return formatValue(yieldValue);
        }
        return String.format("%,d gp", yieldValue);
    }

    @Subscribe(priority = -2)
    public void onScriptPostFired(ScriptPostFired event) {
        if (event.getScriptId() == ScriptID.BANKMAIN_FINISHBUILDING) {
            updateBankTitleValue();
        }
    }
    @Subscribe
    public void onMenuEntryAdded(MenuEntryAdded event) {
        if (!client.isKeyPressed(KeyCode.KC_SHIFT) || event.getItemId() <= 0) {
            return;
        }

        MenuEntry entry = event.getMenuEntry();
        if (entry == null) {
            return;
        }

        int packedWidgetId = entry.getParam1();
        int widgetGroupId = packedWidgetId >> 16;

        boolean isBankVaultItem = (widgetGroupId == 12);
        boolean isInventoryItem = (widgetGroupId == 15 || widgetGroupId == 300 || widgetGroupId == 301);

        if (!isBankVaultItem && !isInventoryItem) {
            return;
        }

        if (isBankVaultItem && !config.shiftBankMenu()) {
            return;
        }
        if (isInventoryItem && !config.shiftInventoryMenu()) {
            return;
        }

        MenuEntry[] existingEntries = client.getMenu().getMenuEntries();
        if (existingEntries != null) {
            for (MenuEntry existing : existingEntries) {
                String existingOpt = existing.getOption();
                if (existingOpt != null && (existingOpt.contains("Set (Sell -") || existingOpt.contains("Exclude from Shop"))) {
                    return;
                }
            }
        }

        int itemId = itemManager.canonicalize(event.getItemId());
        
        int containerId = isBankVaultItem ? 95 : 93;
        ItemContainer targetContainer = client.getItemContainer(containerId);
        int itemStackQuantity = 1;
        if (targetContainer != null) {
            itemStackQuantity = targetContainer.count(event.getItemId());
        }

        if (isItemIncluded(itemId)) {
            client.getMenu().createMenuEntry(-1)
                .setOption("<col=ff0000>Exclude from Shop</col>").setTarget(event.getTarget()).setType(MenuAction.RUNELITE)
                .setIdentifier(event.getIdentifier()).setParam0(event.getActionParam0()).setParam1(event.getActionParam1()).setItemId(itemId);
        }

        long baselineVolumeCount = itemStackQuantity;
        String yieldAll = resolveDynamicMenuText(calculateContinuousAllYield(itemId, baselineVolumeCount));
        String yield50 = resolveDynamicMenuText(calculateMenuDisplayYield(itemId, baselineVolumeCount, SELL_AMOUNT_HIGH));
        String yield10 = resolveDynamicMenuText(calculateMenuDisplayYield(itemId, baselineVolumeCount, SELL_AMOUNT_MEDIUM));
        String yield5 = resolveDynamicMenuText(calculateMenuDisplayYield(itemId, baselineVolumeCount, SELL_AMOUNT_LOW));
        String yield1 = resolveDynamicMenuText(calculateMenuDisplayYield(itemId, baselineVolumeCount, SELL_AMOUNT_DEFAULT));

        client.getMenu().createMenuEntry(-1)
            .setOption("<col=d8b4fe>Set (Sell - All)</col> <col=ff981f>[" + yieldAll + "]</col>").setTarget(event.getTarget()).setType(MenuAction.RUNELITE)
            .setIdentifier(event.getIdentifier()).setParam0(event.getActionParam0()).setParam1(event.getActionParam1()).setItemId(itemId)
            .onClick(e -> handleForceSelection(itemId, SELL_AMOUNT_ALL_MARKER));

        client.getMenu().createMenuEntry(-1)
            .setOption("<col=ff981f>Set (Sell - 50)</col> <col=ff981f>[" + yield50 + "]</col>").setTarget(event.getTarget()).setType(MenuAction.RUNELITE)
            .setIdentifier(event.getIdentifier()).setParam0(event.getActionParam0()).setParam1(event.getActionParam1()).setItemId(itemId)
            .onClick(e -> handleForceSelection(itemId, SELL_AMOUNT_HIGH));

        client.getMenu().createMenuEntry(-1)
            .setOption("<col=ffff00>Set (Sell - 10)</col> <col=ff981f>[" + yield10 + "]</col>").setTarget(event.getTarget()).setType(MenuAction.RUNELITE)
            .setIdentifier(event.getIdentifier()).setParam0(event.getActionParam0()).setParam1(event.getActionParam1()).setItemId(itemId)
            .onClick(e -> handleForceSelection(itemId, SELL_AMOUNT_MEDIUM));

        client.getMenu().createMenuEntry(-1)
            .setOption("<col=00ff00>Set (Sell - 5)</col> <col=ff981f>[" + yield5 + "]</col>").setTarget(event.getTarget()).setType(MenuAction.RUNELITE)
            .setIdentifier(event.getIdentifier()).setParam0(event.getActionParam0()).setParam1(event.getActionParam1()).setItemId(itemId)
            .onClick(e -> handleForceSelection(itemId, SELL_AMOUNT_LOW));

        client.getMenu().createMenuEntry(-1)
            .setOption("<col=00ffff>Set (Sell - 1)</col> <col=ff981f>[" + yield1 + "]</col>").setTarget(event.getTarget()).setType(MenuAction.RUNELITE)
            .setIdentifier(event.getIdentifier()).setParam0(event.getActionParam0()).setParam1(event.getActionParam1()).setItemId(itemId)
            .onClick(e -> handleForceSelection(itemId, SELL_AMOUNT_DEFAULT));
    }

    private void handleForceSelection(int itemId, int quantity) {
        int canonicalId = itemManager.canonicalize(itemId);
        includedItems.add(canonicalId);
        saveInclusions();
        
        forcedSellTiers.put(canonicalId, quantity);
        updateBankTitleValue();
    }

    @Subscribe
    public void onMenuOptionClicked(MenuOptionClicked event) {
        if (event.getMenuAction() != MenuAction.RUNELITE) {
            return;
        }

        if (event.getMenuOption().contains("Exclude from Shop")) {
            int itemId = itemManager.canonicalize(event.getItemId());
            includedItems.remove(itemId);
            forcedSellTiers.remove(itemId);
            saveInclusions();
            updateBankTitleValue();
            event.consume();
        }
    }

    private void saveInclusions() {
        StringBuilder sb = new StringBuilder();
        for (int id : includedItems) {
            sb.append(id).append(",");
        }
        config.setIncludedItemIds(sb.toString());
    }

    private void loadInclusions() {
        includedItems.clear();
        String stored = config.includedItemIds();
        if (stored == null || stored.isEmpty()) return;
        for (String part : stored.split(",")) {
            if (!part.trim().isEmpty()) {
                try {
                    includedItems.add(Integer.parseInt(part.trim()));
                } catch (NumberFormatException ignored) {}
            }
        }
    }

    private long calculateContinuousAllYield(int itemId, long totalQty) {
        int highAlchPrice = itemManager.getItemComposition(itemId).getHaPrice();
        if (highAlchPrice <= 0) return 0;

        long totalCashYield = 0;
        long priceFloorLimit = (long) Math.floor(highAlchPrice * 0.10);

        for (int i = 0; i < totalQty; i++) {
            double decayFactor = 1.0 - (0.02 * i);
            long singleItemValue = (long) Math.floor(highAlchPrice * decayFactor);
            
            if (singleItemValue < priceFloorLimit) {
                singleItemValue = priceFloorLimit;
            }
            totalCashYield += singleItemValue;
        }
        return totalCashYield;
    }

    private long calculateMenuDisplayYield(int itemId, long totalQty, int sellBatchSize) {
        int highAlchPrice = itemManager.getItemComposition(itemId).getHaPrice();
        if (highAlchPrice <= 0) return 0;

        long totalCashYield = 0;
        long remainingItems = totalQty;
        long priceFloorLimit = (long) Math.floor(highAlchPrice * 0.10);

        while (remainingItems > 0) {
            long currentBatch = Math.min(remainingItems, sellBatchSize);
            for (int i = 0; i < currentBatch; i++) {
                double decayFactor = 1.0 - (0.02 * i);
                long singleItemValue = (long) Math.floor(highAlchPrice * decayFactor);
                
                if (singleItemValue < priceFloorLimit) {
                    singleItemValue = priceFloorLimit;
                }
                totalCashYield += singleItemValue;
            }
            remainingItems -= currentBatch;
        }
        return totalCashYield;
    }

    public long calculateProjectedShopYield(int itemId, int totalQty, int sellBatchSize) {
        if (!isItemIncluded(itemId)) return 0;
        return calculateMenuDisplayYield(itemId, totalQty, sellBatchSize);
    }
}
