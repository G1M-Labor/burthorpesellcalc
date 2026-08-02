package net.runelite.client.plugins.burthorpesellcalc;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.ItemContainer;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.overlay.WidgetItemOverlay;

public class burthorpeitemoverlay extends WidgetItemOverlay {
    private final Client client;
    private final burthorpecalcplugin plugin;
    private final burthorpecalcconfig config;
    private final ItemManager itemManager;

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

        int batchSize = 1;
        Integer forcedBatch = plugin.getForcedBatchSize(canonicalId);
        if (forcedBatch != null) {
            batchSize = forcedBatch;
        }

        // FIXED LAGLESS RENDERING:
        // Evaluates the event-driven hash registry cache in an instant O(1) step, completely removing UI scanning lag [source: 7]
        if (batchSize != 999999) {
            if (plugin.isItemSoldThisWorld(canonicalId) || plugin.isCachedShopStockPresent(canonicalId)) {
                return;
            }
        }

        Color strokeColor;
        if (batchSize == 999999) {
            strokeColor = new Color(216, 180, 254);
        } else {
            switch (batchSize) {
                case 1: strokeColor = Color.CYAN; break;
                case 5: strokeColor = Color.GREEN; break;
                case 10: strokeColor = Color.YELLOW; break;
                case 50: strokeColor = new Color(255, 152, 31); break;
                default: strokeColor = Color.WHITE; break;
            }
        }

        BufferedImage itemOutlineImage = itemManager.getItemOutline(itemId, widgetItem.getQuantity(), strokeColor);
        if (itemOutlineImage != null) {
            graphics.drawImage(itemOutlineImage, widgetItem.getCanvasLocation().getX(), widgetItem.getCanvasLocation().getY(), null);
        }
    }
}
