package com.burthorpesellcalc;

import net.runelite.api.Client;
import net.runelite.api.ItemContainer;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.overlay.WidgetItemOverlay;

import javax.inject.Inject;
import java.awt.*;
import java.awt.image.BufferedImage;

public class burthorpeitemoverlay extends WidgetItemOverlay {
    private final Client client;
    private final burthorpecalcplugin plugin; // FIXED: Updated typo to match exact lowercase naming conventions
    private final burthorpecalcconfig config;
    private final ItemManager itemManager;

    @Inject
    public burthorpeitemoverlay(Client client, burthorpecalcplugin plugin, burthorpecalcconfig config, ItemManager itemManager) { // FIXED: Updated typo to match exact lowercase naming conventions
        this.client = client;
        this.plugin = plugin;
        this.config = config;
        this.itemManager = itemManager;
        showOnBank();
        showOnInventory();
    }

    @Override
    public void renderItemOverlay(Graphics2D graphics, int itemId, net.runelite.api.widgets.WidgetItem widgetItem) {
        int canonicalId = itemManager.canonicalize(itemId);
        if (!plugin.isItemIncluded(canonicalId)) {
            return;
        }

        int packedWidgetId = widgetItem.getWidget().getId();
        int widgetGroupId = packedWidgetId >> 16;
        boolean isBankWidget = (widgetGroupId == 12);

        if (isBankWidget && !config.enableBankHighlight()) {
            return;
        }
        if (!isBankWidget && !config.enableInventoryHighlight()) {
            return;
        }

        int containerId = isBankWidget ? 95 : 93;
        ItemContainer container = client.getItemContainer(containerId);
        if (container == null) {
            return;
        }

        int batchSize = 1;
        Integer forcedBatch = plugin.getForcedBatchSize(canonicalId);
        if (forcedBatch != null) {
            batchSize = forcedBatch;
        }

        Color strokeColor;
        switch (batchSize) {
            case 1: strokeColor = Color.CYAN; break;
            case 10: strokeColor = Color.YELLOW; break;
            case 50: strokeColor = Color.RED; break;
            case 5:
            default: strokeColor = Color.GREEN; break;
        }

        BufferedImage itemOutlineImage = itemManager.getItemOutline(itemId, widgetItem.getQuantity(), strokeColor);
        if (itemOutlineImage != null) {
            graphics.drawImage(itemOutlineImage, widgetItem.getCanvasLocation().getX(), widgetItem.getCanvasLocation().getY(), null);
        }
    }
}
