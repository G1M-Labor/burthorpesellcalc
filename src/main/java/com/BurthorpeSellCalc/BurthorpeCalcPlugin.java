package net.runelite.client.plugins.burthorpesellcalc;

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
import net.runelite.api.gameval.InterfaceID;
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
public class BurthorpeCalcPlugin extends Plugin {

    @Inject private Client client;
    @Inject private BurthorpeCalcConfig config;
    @Inject private ItemManager itemManager;
    @Inject private OverlayManager overlayManager;
    @Inject private BurthorpeItemOverlay itemOverlay;
    @Inject private EventBus eventBus;
    @Inject private BurthorpeMenuSwapper menuSwapper;
    @Inject private ConfigManager configManager;

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

    @Subscribe
    public void onConfigChanged(ConfigChanged event) {
        if (!event.getGroup().equals("burthorpesellcalc")) {
            return;
        }

        if (event.getKey().equals("clearInclusionsToggle")) {
            // FIXED EVALUATION LAYER: Checked the typed configuration interface method directly to clear out line 78 object casting errors
            if (!config.clearInclusionsToggle()) {
                return;
            }

            includedItems.clear();
            forcedSellTiers.clear();
            saveInclusions();
            updateBankTitleValue();
            log.info("Burthorpe Calculator database immediately wiped via settings configuration toggle.");

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
        Widget bankTitleWidget = client.getWidget(InterfaceID.Bankmain.TITLE);
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

        nativeText = nativeText.replaceAll("\\s*<col=[0-9a-fA-F]+>\\(SHOP:[^)]+\\)\\s*\\(INV:[^)]+\\)</col>", "");
        nativeText = nativeText.replaceAll("\\s*<col=[0-9a-fA-F]+>\\(SHOP:[^<]+\\)</col>", "");
        nativeText = nativeText.replaceAll("\\s*\\(SHOP:[^)]+\\)", "");

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

            totalValue += calculateProjectedShopYield(itemId, item.getQuantity(), batchSize);
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

        String option = event.getOption();
        if (option == null) {
            return;
        }

        boolean isBankVaultItem = entry.getType() == MenuAction.CC_OP_LOW_PRIORITY && option.contains("Value");
        boolean isInventoryItem = option.equals("Examine");

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

        if (isItemIncluded(itemId)) {
            client.getMenu().createMenuEntry(-1)
                    .setOption("<col=d8b4fe>Exclude from Shop</col>").setTarget(event.getTarget()).setType(MenuAction.RUNELITE)
                    .setIdentifier(event.getIdentifier()).setParam0(event.getActionParam0()).setParam1(event.getActionParam1()).setItemId(itemId);
        }

        client.getMenu().createMenuEntry(-1)
                .setOption("<col=00ffff>Set (Sell - " + SELL_AMOUNT_DEFAULT + ")</col>").setTarget(event.getTarget()).setType(MenuAction.RUNELITE)
                .setIdentifier(event.getIdentifier()).setParam0(event.getActionParam0()).setParam1(event.getActionParam1()).setItemId(itemId)
                .onClick(e -> handleForceSelection(itemId, SELL_AMOUNT_DEFAULT));

        client.getMenu().createMenuEntry(-1)
                .setOption("<col=00ff00>Set (Sell - " + SELL_AMOUNT_LOW + ")</col>").setTarget(event.getTarget()).setType(MenuAction.RUNELITE)
                .setIdentifier(event.getIdentifier()).setParam0(event.getActionParam0()).setParam1(event.getActionParam1()).setItemId(itemId)
                .onClick(e -> handleForceSelection(itemId, SELL_AMOUNT_LOW));

        client.getMenu().createMenuEntry(-1)
                .setOption("<col=ffff00>Set (Sell - " + SELL_AMOUNT_MEDIUM + ")</col>").setTarget(event.getTarget()).setType(MenuAction.RUNELITE)
                .setIdentifier(event.getIdentifier()).setParam0(event.getActionParam0()).setParam1(event.getActionParam1()).setItemId(itemId)
                .onClick(e -> handleForceSelection(itemId, SELL_AMOUNT_MEDIUM));

        client.getMenu().createMenuEntry(-1)
                .setOption("<col=ff0000>Set (Sell - " + SELL_AMOUNT_HIGH + ")</col>").setTarget(event.getTarget()).setType(MenuAction.RUNELITE)
                .setIdentifier(event.getIdentifier()).setParam0(event.getActionParam0()).setParam1(event.getActionParam1()).setItemId(itemId)
                .onClick(e -> handleForceSelection(itemId, SELL_AMOUNT_HIGH));
    }

    private void handleForceSelection(int itemId, int quantity) {
        int canonicalId = itemManager.canonicalize(itemId);
        if (!includedItems.contains(canonicalId)) {
            includedItems.add(canonicalId);
            saveInclusions();
        }

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

    public long calculateProjectedShopYield(int itemId, int totalQty, int sellBatchSize) {
        if (!isItemIncluded(itemId)) return 0;
        int highAlchPrice = itemManager.getItemComposition(itemId).getHaPrice();
        if (highAlchPrice <= 0) return 0;

        long totalCashYield = 0;
        int remainingItems = totalQty;

        while (remainingItems > 0) {
            int currentBatch = Math.min(remainingItems, sellBatchSize);
            for (int i = 0; i < currentBatch; i++) {
                double decayFactor = Math.max(0.0, 1.0 - (0.02 * i));
                totalCashYield += (long) Math.floor(highAlchPrice * decayFactor);
            }
            remainingItems -= currentBatch;
        }
        return totalCashYield;
    }
}
