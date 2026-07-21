package com.BurthorpeSellCalc;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Slf4j
@PluginDescriptor(
        name = "Burthorpe Shop Calculator",
        description = "Calculates shop sale yields and appends values seamlessly to the bank title text natively",
        tags = {"bank", "shop", "money", "calc", "ironman"}
)
public class BurthorpeCalcPlugin extends Plugin {

    @Inject private Client client;
    @Inject private BurthorpeCalcConfig config;
    @Inject private ItemManager itemManager;
    @Inject private OverlayManager overlayManager;
    @Inject private BurthorpeItemOverlay itemOverlay;
    @Inject private EventBus eventBus;
    @Inject private BurthorpeMenuSwapper menuSwapper;

    private final Set<Integer> includedItems = new HashSet<>();
    private final Map<Integer, Integer> forcedSellTiers = new HashMap<>();

    private static final int SELL_AMOUNT_DEFAULT = 1;
    private static final int SELL_AMOUNT_LOW = 5;
    private static final int SELL_AMOUNT_MEDIUM = 10;
    private static final int SELL_AMOUNT_HIGH = 50;

    @Provides
    @SuppressWarnings("unused")
    BurthorpeCalcConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(BurthorpeCalcConfig.class);
    }

    @Override
    protected void startUp() throws Exception {
        loadInclusions();
        overlayManager.add(itemOverlay);
        eventBus.register(menuSwapper);
        log.info("Burthorpe Shop Calculator started!");
    }

    @Override
    protected void shutDown() throws Exception {
        overlayManager.remove(itemOverlay);
        eventBus.unregister(menuSwapper);
        forcedSellTiers.clear();
        log.info("Burthorpe Shop Calculator stopped!");
    }

    public boolean isItemIncluded(int itemId) {
        return includedItems.contains(itemId);
    }

    public Integer getForcedBatchSize(int itemId) {
        return forcedSellTiers.get(itemId);
    }

    @Subscribe(priority = -2)
    public void onScriptPostFired(ScriptPostFired event) {
        if (event.getScriptId() == ScriptID.BANKMAIN_FINISHBUILDING) {
            updateBankTitleValue();
        }
    }

    private void updateBankTitleValue() {
        Widget bankTitleWidget = client.getWidget(InterfaceID.Bankmain.TITLE);
        if (bankTitleWidget == null || bankTitleWidget.isHidden()) {
            return;
        }

        ItemContainer bankContainer = client.getItemContainer(95);
        if (bankContainer == null) {
            return;
        }

        long totalTabValue = 0;
        Item[] items = bankContainer.getItems();

        // Track the highest tier encountered across items in the tab to color the title context uniformly
        int highestTierEncountered = 1;

        for (Item item : items) {
            if (item == null || item.getId() <= 0 || item.getQuantity() <= 0) {
                continue;
            }

            int itemId = item.getId();
            if (!isItemIncluded(itemId)) {
                continue;
            }

            int qty = item.getQuantity();
            int batchSize = SELL_AMOUNT_DEFAULT;

            Integer forcedBatch = getForcedBatchSize(itemId);
            if (forcedBatch != null) {
                batchSize = forcedBatch;
            } else if (qty >= config.highThreshold()) {
                batchSize = SELL_AMOUNT_HIGH;
            } else if (qty >= config.mediumThreshold()) {
                batchSize = SELL_AMOUNT_MEDIUM;
            } else if (qty >= config.lowThreshold()) {
                batchSize = SELL_AMOUNT_LOW;
            }

            if (batchSize > highestTierEncountered) {
                highestTierEncountered = batchSize;
            }

            totalTabValue += calculateProjectedShopYield(itemId, qty, batchSize);
        }

        String nativeText = bankTitleWidget.getText();
        if (nativeText == null || nativeText.isEmpty()) {
            return;
        }

        nativeText = nativeText.replaceAll("\\s*<col=[0-9a-fA-F]+>\\(SHOP:[^<]+\\)</col>", "");
        nativeText = nativeText.replaceAll("\\s*\\(SHOP:[^)]+\\)", "");

        String formattedShopStr = formatValue(totalTabValue);

        // Dynamically assign hex colors to your bank value text using your exact specifications
        String tierColorHex;
        switch (highestTierEncountered) {
            case 5:
                tierColorHex = "00ff00"; // Green
                break;
            case 10:
                tierColorHex = "ffff00"; // Yellow
                break;
            case 50:
                tierColorHex = "ff0000"; // Red
                break;
            case 1:
            default:
                tierColorHex = "00ffff"; // Cyan
                break;
        }

        bankTitleWidget.setText(nativeText + " <col=" + tierColorHex + ">(SHOP: " + formattedShopStr + ")</col>");
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

    @Subscribe
    public void onMenuEntryAdded(MenuEntryAdded event) {
        Widget shopWidget = client.getWidget(301, 16);
        if (shopWidget != null && !shopWidget.isHidden()) {
            return;
        }

        Widget bankWidget = client.getWidget(12, 13);
        if (bankWidget == null || bankWidget.isHidden()) {
            return;
        }

        if (client.isKeyPressed(KeyCode.KC_SHIFT) && event.getOption().equals("Examine")) {
            int itemId = event.getItemId();

            if (isItemIncluded(itemId)) {
                // FIXED: Changed exclusion layout options text to match Light Purple hex codes (#d8b4fe)
                client.getMenu().createMenuEntry(-1)
                        .setOption("<col=d8b4fe>Exclude from Shop</col>").setTarget(event.getTarget()).setType(MenuAction.RUNELITE)
                        .setIdentifier(event.getIdentifier()).setParam0(event.getActionParam0()).setParam1(event.getActionParam1()).setItemId(itemId);
            }

            // Keep configuration choices synchronized with your primary layout hex sets
            ItemContainer bank = client.getItemContainer(95);
            int qty = bank != null ? bank.count(itemId) : 0;

            String sell1Value = formatValue(calculateProjectedShopYield(itemId, qty, SELL_AMOUNT_DEFAULT));
            String sell5Value = formatValue(calculateProjectedShopYield(itemId, qty, SELL_AMOUNT_LOW));
            String sell10Value = formatValue(calculateProjectedShopYield(itemId, qty, SELL_AMOUNT_MEDIUM));
            String sell50Value = formatValue(calculateProjectedShopYield(itemId, qty, SELL_AMOUNT_HIGH));

            client.getMenu().createMenuEntry(-1)
                    .setOption("<col=00ffff>Set (Sell " + SELL_AMOUNT_DEFAULT + ")</col> <col=ffffff>(" + sell1Value + ")</col>")
                    .setTarget(event.getTarget())
                    .setType(MenuAction.RUNELITE)
                    .setIdentifier(event.getIdentifier())
                    .setParam0(event.getActionParam0())
                    .setParam1(event.getActionParam1())
                    .setItemId(itemId)
                    .onClick(e -> handleForceSelection(itemId, SELL_AMOUNT_DEFAULT));

            client.getMenu().createMenuEntry(-1)
                    .setOption("<col=00ff00>Set (Sell " + SELL_AMOUNT_LOW + ")</col> <col=ffffff>(" + sell5Value + ")</col>")
                    .setTarget(event.getTarget())
                    .setType(MenuAction.RUNELITE)
                    .setIdentifier(event.getIdentifier())
                    .setParam0(event.getActionParam0())
                    .setParam1(event.getActionParam1())
                    .setItemId(itemId)
                    .onClick(e -> handleForceSelection(itemId, SELL_AMOUNT_LOW));

            client.getMenu().createMenuEntry(-1)
                    .setOption("<col=ffff00>Set (Sell " + SELL_AMOUNT_MEDIUM + ")</col> <col=ffffff>(" + sell10Value + ")</col>")
                    .setTarget(event.getTarget())
                    .setType(MenuAction.RUNELITE)
                    .setIdentifier(event.getIdentifier())
                    .setParam0(event.getActionParam0())
                    .setParam1(event.getActionParam1())
                    .setItemId(itemId)
                    .onClick(e -> handleForceSelection(itemId, SELL_AMOUNT_MEDIUM));

            client.getMenu().createMenuEntry(-1)
                    .setOption("<col=ff0000>Set (Sell " + SELL_AMOUNT_HIGH + ")</col> <col=ffffff>(" + sell50Value + ")</col>")
                    .setTarget(event.getTarget())
                    .setType(MenuAction.RUNELITE)
                    .setIdentifier(event.getIdentifier())
                    .setParam0(event.getActionParam0())
                    .setParam1(event.getActionParam1())
                    .setItemId(itemId)
                    .onClick(e -> handleForceSelection(itemId, SELL_AMOUNT_HIGH));
        }
    }

    private void handleForceSelection(int itemId, int quantity) {
        if (!includedItems.contains(itemId)) {
            includedItems.add(itemId);
            saveInclusions();
        }

        forcedSellTiers.put(itemId, quantity);
        updateBankTitleValue();
    }

    @Subscribe
    public void onMenuOptionClicked(MenuOptionClicked event) {
        if (event.getMenuAction() != MenuAction.RUNELITE) {
            return;
        }

        if (event.getMenuOption().contains("Exclude from Shop")) {
            int itemId = event.getItemId();
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
        }config.setIncludedItemIds(sb.toString());}private void loadInclusions() {includedItems.clear();String stored = config.includedItemIds();if (stored == null || stored.isEmpty()) return;for (String part : stored.split(",")) {if (!part.trim().isEmpty()) {try {includedItems.add(Integer.parseInt(part.trim()));} catch (NumberFormatException ignored) {}}}}public long calculateProjectedShopYield(int itemId, int totalQty, int sellBatchSize) {if (!isItemIncluded(itemId)) return 0;int highAlchPrice = itemManager.getItemComposition(itemId).getHaPrice();if (highAlchPrice <= 0) return 0;long totalCashYield = 0;int remainingItems = totalQty;while (remainingItems > 0) {int currentBatch = Math.min(remainingItems, sellBatchSize);for (int i = 0; i < currentBatch; i++) {double decayFactor = Math.max(0.0, 1.0 - (0.02 * i));totalCashYield += (long) Math.floor(highAlchPrice * decayFactor);}remainingItems -= currentBatch;}return totalCashYield;}}