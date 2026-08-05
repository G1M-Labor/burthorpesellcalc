package com.burthorpesellcalc;

import net.runelite.api.Client;
import net.runelite.api.ItemContainer;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.overlay.WidgetItemOverlay;

import javax.inject.Inject;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;

public class burthorpeitemoverlay extends WidgetItemOverlay {
    private final Client client;
    private final burthorpecalcplugin plugin;
    private final burthorpecalcconfig config;
    private final ItemManager itemManager;

    // Per-frame cached data to eliminate duplicate lookups
    private Widget activeShopGrid = null;
    private final Map<Integer, Integer> shopStockCache = new HashMap<>();

    @Inject
    public burthorpeitemoverlay(Client client, burthorpecalcplugin plugin, burthorpecalcconfig config, ItemManager itemManager) {
        this.client = client;
        this.plugin = plugin;
        this.config = config;
        this.itemManager = itemManager;
        showOnBank();
        showOnInventory();
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        // Look up active shop grid exactly once at the beginning of the frame
        activeShopGrid = client.getWidget(301, 16);
        if (activeShopGrid == null || activeShopGrid.isHidden()) {
            activeShopGrid = client.getWidget(300, 16); // Fallback layout variant
        }

        // Populate a fast lookup cache for current shop stock if a shop is open
        shopStockCache.clear();
        if (activeShopGrid != null && !activeShopGrid.isHidden()) {
            Widget[] shopItems = activeShopGrid.getDynamicChildren();
            if (shopItems != null) {
                for (Widget shopItemWidget : shopItems) {
                    if (shopItemWidget != null && shopItemWidget.getItemQuantity() > 0) {
                        int shopItemCanonicalId = itemManager.canonicalize(shopItemWidget.getItemId());
                        shopStockCache.put(shopItemCanonicalId, shopItemWidget.getItemQuantity());
                    }
                }
            }
        }

        // Delegate execution back to the base engine to process individual item slots
        return super.render(graphics);
    }

    @Override
    public void renderItemOverlay(Graphics2D graphics, int itemId, net.runelite.api.widgets.WidgetItem widgetItem) {
        int canonicalId = itemManager.canonicalize(itemId);
        if (!plugin.isItemIncluded(canonicalId)) {
            return;
        }

        int packedWidgetId = widgetItem.getWidget().getId();
        int widgetGroupId = packedWidgetId >> 16;

        // Structural check evaluating target context matching your plugin scripts
        boolean isBankWidget = (widgetGroupId == InterfaceID.Bankmain.TITLE >> 16);

        // Bank Highlight Guard
        if (isBankWidget && !config.enableBankHighlight()) {
            return;
        }

        // Inventory/Shop Highlight Guard
        if (!isBankWidget) {
            if (!config.enableInventoryHighlight()) {
                return;
            }

            // Early exit if no shop GUI is open using our pre-cached frame widget
            if (activeShopGrid == null || activeShopGrid.isHidden()) {
                return;
            }
        }

        int batchSize = 1;
        Integer forcedBatch = plugin.getForcedBatchSize(canonicalId);
        if (forcedBatch != null) {
            batchSize = forcedBatch;
        }

        // Overstock Check Optimization: Replaced O(N) loop with O(1) map lookup
        if (!isBankWidget && batchSize != burthorpecalcplugin.SELL_AMOUNT_ALL) {
            if (shopStockCache.containsKey(canonicalId)) {
                return;
            }
        }

        // FIX: Replaced deprecated InventoryID enum lookup with raw stable container integer allocations
        // 95 matches Bank item storage containers, 93 matches Inventory storage containers
        int containerId = isBankWidget ? 95 : 93;
        ItemContainer container = client.getItemContainer(containerId);
        if (container == null) {
            return;
        }

        Color strokeColor;
        if (batchSize == burthorpecalcplugin.SELL_AMOUNT_ALL) {
            strokeColor = Color.RED;
        } else {
            switch (batchSize) {
                case 1: strokeColor = Color.CYAN; break;
                case 5: strokeColor = Color.GREEN; break;
                case 10: strokeColor = Color.YELLOW; break;
                case 50: strokeColor = Color.ORANGE; break;
                default: strokeColor = Color.MAGENTA; break;
            }
        }

        BufferedImage itemOutlineImage = itemManager.getItemOutline(itemId, widgetItem.getQuantity(), strokeColor);
        if (itemOutlineImage != null) {
            graphics.drawImage(itemOutlineImage, widgetItem.getCanvasLocation().getX(), widgetItem.getCanvasLocation().getY(), null);
        }
    }
}
