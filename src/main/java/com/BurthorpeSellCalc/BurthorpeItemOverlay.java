package com.BurthorpeSellCalc;

import net.runelite.api.Client;
import net.runelite.api.ItemContainer;
import net.runelite.api.widgets.Widget;
import net.runelite.client.ui.overlay.WidgetItemOverlay;

import javax.inject.Inject;
import java.awt.*;

public class BurthorpeItemOverlay extends WidgetItemOverlay {
    private final Client client;
    private final BurthorpeCalcPlugin plugin;
    private final BurthorpeCalcConfig config;

    private static final Color COLOR_DEFAULT = new Color(0, 255, 0, 45);    // Green (Default, < Low)
    private static final Color COLOR_LOW = new Color(255, 127, 0, 55);      // Orange (Low, Sell 5)
    private static final Color COLOR_MEDIUM = new Color(255, 0, 0, 55);     // Red (Medium, Sell 10)
    private static final Color COLOR_HIGH = new Color(128, 0, 128, 65);     // Purple (High, Sell 50)

    @Inject
    public BurthorpeItemOverlay(Client client, BurthorpeCalcPlugin plugin, BurthorpeCalcConfig config) {
        this.client = client;
        this.plugin = plugin;
        this.config = config;
        showOnBank();
    }

    @Override
    public void renderItemOverlay(Graphics2D graphics, int itemId, net.runelite.api.widgets.WidgetItem itemWidget) {
        Widget bankWidget = client.getWidget(12, 0);
        if (bankWidget == null || bankWidget.isHidden()) {
            return;
        }

        if (!plugin.isItemIncluded(itemId)) {
            return;
        }

        ItemContainer bankContainer = client.getItemContainer(95);
        if (bankContainer == null) {
            return;
        }

        int totalQty = bankContainer.count(itemId);
        if (totalQty <= 0) return;

        Color paintColor = COLOR_DEFAULT;
        Integer forcedBatch = plugin.getForcedBatchSize(itemId);

        if (forcedBatch != null) {
            if (forcedBatch == 50) {
                paintColor = COLOR_HIGH;
            } else if (forcedBatch == 10) {
                paintColor = COLOR_MEDIUM;
            } else if (forcedBatch == 5) {
                paintColor = COLOR_LOW;
            }
        } else {
            // Fixed the configuration lookups to match our strictly hardcoded numeric threshold tiers
            if (totalQty >= config.highThreshold()) {
                paintColor = COLOR_HIGH;
            } else if (totalQty >= config.mediumThreshold()) {
                paintColor = COLOR_MEDIUM;
            } else if (totalQty >= config.lowThreshold()) {
                paintColor = COLOR_LOW;
            }
        }

        Rectangle bounds = itemWidget.getCanvasBounds();
        graphics.setColor(paintColor);
        graphics.fillRect(bounds.x, bounds.y, bounds.width, bounds.height);
    }
}
