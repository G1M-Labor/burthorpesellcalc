package com.burthorpesellcalc;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.api.events.ScriptPreFired;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import java.text.NumberFormat;
import java.util.HashMap;
import java.util.Locale;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Slf4j
@PluginDescriptor(
        name = "Burthorpe Shop Calculator",
        description = "Calculates shop sale yields and appends values seamlessly to the bank title text natively",
        tags = {"bank", "shop", "money", "calc", "ironman"}
)
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

    // Stable cache register mirrors native Bank plugin value rendering flow [source: 2]
    private String cachedShopTextStr = "";

    public static final int SELL_AMOUNT_DEFAULT = 1;
    public static final int SELL_AMOUNT_LOW = 5;
    public static final int SELL_AMOUNT_MEDIUM = 10;
    public static final int SELL_AMOUNT_HIGH = 50;
    public static final int SELL_AMOUNT_ALL = -1;

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
        syncAllIncludedTags();
        log.info("Burthorpe Shop Calculator started!");
    }

    @Override
    protected void shutDown() throws Exception {
        overlayManager.remove(itemOverlay);
        eventBus.unregister(menuSwapper);
        forcedSellTiers.clear();
        cachedShopTextStr = "";

        Widget bankTitleWidget = client.getWidget(InterfaceID.Bankmain.TITLE);
        if (bankTitleWidget != null && bankTitleWidget.getText() != null) {
            String nativeText = bankTitleWidget.getText();
            nativeText = nativeText.replaceAll("\\s*<col=[0-9a-fA-F]+>\\(SHOP:[^)]+\\)\\s*\\(INV:[^)]+\\)</col>", "");
            bankTitleWidget.setText(nativeText);
        }
        log.info("Burthorpe Shop Calculator stopped!");
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

            if (configManager != null) {
                for (int id : includedItems) {
                    removeShopscapeTag(id);
                }
            }

            includedItems.clear();
            forcedSellTiers.clear();
            saveInclusions();
            recomputePricesCache();
            updateBankTitleValue();
            configManager.setConfiguration("burthorpesellcalc", "clearInclusionsToggle", false);
        }

        if (event.getKey().equals("valueFormat")) {
            recomputePricesCache();
            updateBankTitleValue();
        }
    }

    public boolean isItemIncluded(int itemId) {
        return includedItems.contains(itemManager.canonicalize(itemId));
    }

    public Integer getForcedBatchSize(int itemId) {
        return forcedSellTiers.get(itemManager.canonicalize(itemId));
    }

    private void saveInclusions() {
        StringBuilder sb = new StringBuilder();
        for (int id : includedItems) {
            int tierValue = forcedSellTiers.getOrDefault(id, SELL_AMOUNT_DEFAULT);
            sb.append(id).append(":").append(tierValue).append(",");
        }
        config.setIncludedItemIds(sb.toString());
    }

    private void loadInclusions() {
        includedItems.clear();
        forcedSellTiers.clear();
        String stored = config.includedItemIds();
        if (stored == null || stored.isEmpty()) return;

        for (String entry : stored.split(",")) {
            if (!entry.trim().isEmpty()) {
                try {
                    if (entry.contains(":")) {
                        String[] parts = entry.split(":");
                        int itemId = Integer.parseInt(parts[0].trim());
                        int tierValue = Integer.parseInt(parts[1].trim());

                        includedItems.add(itemId);
                        forcedSellTiers.put(itemId, tierValue);
                    } else {
                        int itemId = Integer.parseInt(entry.trim());
                        includedItems.add(itemId);
                        forcedSellTiers.put(itemId, SELL_AMOUNT_DEFAULT);
                    }
                } catch (NumberFormatException ignored) {}
            }
        }
    }

    private void syncAllIncludedTags() {
        if (configManager == null || includedItems.isEmpty()) return;
        for (int id : includedItems) {
            addShopscapeTag(id);
        }
    }
    @Subscribe(priority = 1)
    public void onScriptPreFired(ScriptPreFired event) {
        if (event.getScriptId() == ScriptID.BANKMAIN_FINISHBUILDING) {
            recomputePricesCache();
        }
    }

    @Subscribe
    public void onScriptPostFired(ScriptPostFired event) {
        if (event.getScriptId() == ScriptID.BANKMAIN_FINISHBUILDING) {
            updateBankTitleValue();
        }
    }

    private void recomputePricesCache() {
        ItemContainer bankContainer = client.getItemContainer(95);
        ItemContainer invContainer = client.getItemContainer(93);

        long totalBankValue = calculateContainerValue(bankContainer);
        long totalInvValue = calculateContainerValue(invContainer);

        String formattedShopStr = formatValue(totalBankValue);
        String formattedInvStr = formatValue(totalInvValue);

        cachedShopTextStr = " <col=ff981f>(SHOP: " + formattedShopStr + ") (INV: " + formattedInvStr + ")</col>";
    }

    public void updateBankTitleValue() {
        Widget bankTitleWidget = client.getWidget(InterfaceID.Bankmain.TITLE);
        if (bankTitleWidget == null || bankTitleWidget.isHidden() || cachedShopTextStr.isEmpty()) {
            return;
        }

        String nativeText = bankTitleWidget.getText();
        if (nativeText == null || nativeText.isEmpty()) {
            return;
        }

        String cleanText = nativeText.replaceAll("\\s*<col=[0-9a-fA-F]+>\\(SHOP:[^)]+\\)\\s*\\(INV:[^)]+\\)</col>", "");
        cleanText = cleanText.replaceAll("\\s*<col=[0-9a-fA-F]+>\\(SHOP:[^<]+\\)</col>", "");
        cleanText = cleanText.replaceAll("\\s*\\(SHOP:[^)]+\\)", "");

        bankTitleWidget.setText(cleanText + cachedShopTextStr);
    }

    private long calculateContainerValue(ItemContainer container) {
        if (container == null) return 0;

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

            if (batchSize == SELL_AMOUNT_ALL) {
                totalValue += calculateProjectedShopYield(itemId, item.getQuantity(), item.getQuantity());
            } else {
                totalValue += calculateProjectedShopYield(itemId, item.getQuantity(), batchSize);
            }
        }
        return totalValue;
    }

    public String formatValue(long value) {
        if (config.valueFormat() == burthorpecalcconfig.ValueFormatMode.PRECISE) {
            return NumberFormat.getNumberInstance(Locale.US).format(value);
        }

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

    public long calculateProjectedShopYield(int itemId, int totalQty, int sellBatchSize) {
        if (!isItemIncluded(itemId)) return 0;
        ItemComposition comp = itemManager.getItemComposition(itemId);
        int highAlchPrice = comp.getHaPrice();
        int baseStoreValue = comp.getPrice();
        if (highAlchPrice <= 0 || baseStoreValue <= 0) return 0;

        long totalCashYield = 0;
        int remainingItems = totalQty;

        int minimumFloorPrice = (int) Math.floor(baseStoreValue * 0.10);

        while (remainingItems > 0) {
            int currentBatch = Math.min(remainingItems, sellBatchSize);

            for (int i = 0; i < currentBatch; i++) {
                int decayedItemPrice = highAlchPrice - (int) Math.floor(i * 0.02 * baseStoreValue);

                if (decayedItemPrice < minimumFloorPrice) {
                    decayedItemPrice = minimumFloorPrice;
                }

                totalCashYield += decayedItemPrice;
            }

            remainingItems -= currentBatch;
        }
        return totalCashYield;
    }
    @Subscribe
    public void onMenuEntryAdded(MenuEntryAdded event) {
        if (!client.isKeyPressed(KeyCode.KC_SHIFT) || event.getItemId() <= 0) {
            return;
        }

        MenuEntry entry = event.getMenuEntry();
        if (entry == null) return;

        String option = event.getOption();
        if (option == null) return;

        int packedWidgetId = event.getActionParam1();
        int widgetGroupId = packedWidgetId >> 16;

        boolean isBankVaultItem = (widgetGroupId == 12) && option.equals("Examine");
        boolean isInventoryItem = (widgetGroupId != 12) && option.equals("Examine");

        // CONFIGURATION TOGGLE FIXED: Evaluates config rules before building entries [source: 2]
        boolean bankAllowed = isBankVaultItem && config.shiftBankMenu();
        boolean inventoryAllowed = isInventoryItem && config.shiftInventoryMenu();

        if (!bankAllowed && !inventoryAllowed) {
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

        int targetContainerId = isBankVaultItem ? 95 : 93;
        ItemContainer container = client.getItemContainer(targetContainerId);
        int totalStackQuantity = 0;
        if (container != null) {
            Item containerItem = container.getItem(event.getActionParam0());
            if (containerItem != null && itemManager.canonicalize(containerItem.getId()) == itemId) {
                totalStackQuantity = containerItem.getQuantity();
            }
        }

        boolean naturallyIncluded = isItemIncluded(itemId);
        if (!naturallyIncluded) {
            includedItems.add(itemId);
            forcedSellTiers.put(itemId, SELL_AMOUNT_DEFAULT);
        }

        String yieldDefaultStr = formatValue(calculateProjectedShopYield(itemId, totalStackQuantity, SELL_AMOUNT_DEFAULT));
        String yieldLowStr = formatValue(calculateProjectedShopYield(itemId, totalStackQuantity, SELL_AMOUNT_LOW));
        String yieldMediumStr = formatValue(calculateProjectedShopYield(itemId, totalStackQuantity, SELL_AMOUNT_MEDIUM));
        String yieldHighStr = formatValue(calculateProjectedShopYield(itemId, totalStackQuantity, SELL_AMOUNT_HIGH));
        String yieldAllStr = formatValue(calculateProjectedShopYield(itemId, totalStackQuantity, totalStackQuantity));

        if (!naturallyIncluded) {
            includedItems.remove(itemId);
            forcedSellTiers.remove(itemId);
        }

        if (naturallyIncluded) {
            client.getMenu().createMenuEntry(-1)
                    .setOption("<col=d8b4fe>Exclude from Shop</col>").setTarget(event.getTarget()).setType(MenuAction.RUNELITE)
                    .setIdentifier(event.getIdentifier()).setParam0(event.getActionParam0()).setParam1(event.getActionParam1()).setItemId(itemId);
        }

        client.getMenu().createMenuEntry(-1)
                .setOption("<col=ff0000>Set (Sell All)</col> <col=9e9e9e>[" + yieldAllStr + " GP]</col>").setTarget(event.getTarget()).setType(MenuAction.RUNELITE)
                .setIdentifier(event.getIdentifier()).setParam0(event.getActionParam0()).setParam1(event.getActionParam1()).setItemId(itemId)
                .onClick(e -> handleForceSelection(itemId, SELL_AMOUNT_ALL));

        client.getMenu().createMenuEntry(-1)
                .setOption("<col=ff9800>Set (Sell - " + SELL_AMOUNT_HIGH + ")</col> <col=9e9e9e>[" + yieldHighStr + " GP]</col>").setTarget(event.getTarget()).setType(MenuAction.RUNELITE)
                .setIdentifier(event.getIdentifier()).setParam0(event.getActionParam0()).setParam1(event.getActionParam1()).setItemId(itemId)
                .onClick(e -> handleForceSelection(itemId, SELL_AMOUNT_HIGH));

        client.getMenu().createMenuEntry(-1)
                .setOption("<col=ffff00>Set (Sell - " + SELL_AMOUNT_MEDIUM + ")</col> <col=9e9e9e>[" + yieldMediumStr + " GP]</col>").setTarget(event.getTarget()).setType(MenuAction.RUNELITE)
                .setIdentifier(event.getIdentifier()).setParam0(event.getActionParam0()).setParam1(event.getActionParam1()).setItemId(itemId)
                .onClick(e -> handleForceSelection(itemId, SELL_AMOUNT_MEDIUM));

        client.getMenu().createMenuEntry(-1)
                .setOption("<col=00ff00>Set (Sell - " + SELL_AMOUNT_LOW + ")</col> <col=9e9e9e>[" + yieldLowStr + " GP]</col>").setTarget(event.getTarget()).setType(MenuAction.RUNELITE)
                .setIdentifier(event.getIdentifier()).setParam0(event.getActionParam0()).setParam1(event.getActionParam1()).setItemId(itemId)
                .onClick(e -> handleForceSelection(itemId, SELL_AMOUNT_LOW));

        client.getMenu().createMenuEntry(-1)
                .setOption("<col=00ffff>Set (Sell - " + SELL_AMOUNT_DEFAULT + ")</col> <col=9e9e9e>[" + yieldDefaultStr + " GP]</col>").setTarget(event.getTarget()).setType(MenuAction.RUNELITE)
                .setIdentifier(event.getIdentifier()).setParam0(event.getActionParam0()).setParam1(event.getActionParam1()).setItemId(itemId)
                .onClick(e -> handleForceSelection(itemId, SELL_AMOUNT_DEFAULT));
    }

    private void handleForceSelection(int itemId, int quantity) {
        int canonicalId = itemManager.canonicalize(itemId);
        includedItems.add(canonicalId);
        forcedSellTiers.put(canonicalId, quantity);
        saveInclusions();

        addShopscapeTag(canonicalId);
        recomputePricesCache();
        updateBankTitleValue();

        ConfigChanged fluentEvent = new ConfigChanged();
        fluentEvent.setGroup("banktags");
        fluentEvent.setKey("item_" + canonicalId);
        fluentEvent.setNewValue("shopscape");
        eventBus.post(fluentEvent);
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

            removeShopscapeTag(itemId);
            recomputePricesCache();
            updateBankTitleValue();

            ConfigChanged fluentEvent = new ConfigChanged();
            fluentEvent.setGroup("banktags");
            fluentEvent.setKey("item_" + itemId);
            fluentEvent.setOldValue("shopscape");
            eventBus.post(fluentEvent);
            event.consume();
        }
    }

    private void addShopscapeTag(int itemId) {
        if (configManager == null) return;
        String currentTags = configManager.getConfiguration("banktags", "item_" + itemId);
        if (currentTags == null || currentTags.isEmpty()) {
            configManager.setConfiguration("banktags", "item_" + itemId, "shopscape");
        } else {
            String cleanTags = currentTags.toLowerCase();
            if (!cleanTags.contains("shopscape")) {
                configManager.setConfiguration("banktags", "item_" + itemId, currentTags + ",shopscape");
            }
        }
    }

    private void removeShopscapeTag(int itemId) {
        if (configManager == null) return;
        String currentTags = configManager.getConfiguration("banktags", "item_" + itemId);
        if (currentTags == null || currentTags.isEmpty()) return;

        String[] tags = currentTags.split(",");
        StringBuilder sb = new StringBuilder();
        for (String tag : tags) {
            if (!tag.trim().equalsIgnoreCase("shopscape")) {
                if (sb.length() > 0) sb.append(",");
                sb.append(tag.trim());
            }
        }

        if (sb.length() == 0) {
            configManager.unsetConfiguration("banktags", "item_" + itemId);
        } else {
            configManager.setConfiguration("banktags", "item_" + itemId, sb.toString());
        }
    }
}
