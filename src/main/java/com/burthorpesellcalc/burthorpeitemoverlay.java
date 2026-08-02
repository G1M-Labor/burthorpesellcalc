package com.burthorpesellcalc;

import com.google.inject.Inject;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import net.runelite.api.Client;
import net.runelite.api.widgets.Widget;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.overlay.WidgetItemOverlay;

public class BurthorpeItemOverlay extends WidgetItemOverlay {
    private final Client client;
    private final BurthorpeCalcPlugin plugin;
    private final BurthorpeCalcConfig config;
    private final ItemManager itemManager;

    @Inject
    public BurthorpeItemOverlay(Client client, BurthorpeCalcPlugin plugin, BurthorpeCalcConfig config, ItemManager itemManager) {
        this.client = client;
        this.plugin = plugin;
        this.config = config;
        this.itemManager = itemManager;
        showOnBank();
        showOnInventory();
    }

    @Override
    public void renderItemOverlay(Graphics2D graphics, int itemId, Widget itemWidget) {
        int canonicalId = itemManager.canonicalize(itemId);
        if (!plugin.isItemIncluded(canonicalId)) {
            return;
        }

        boolean isBank = itemWidget.getParent().getId() == 786445; // Group ID for Bank main grid
        if (isBank && !config.enableBankHighlight()) {
            return;
        }
        if (!isBank && !config.enableInventoryHighlight()) {
            return;
        }

        Rectangle bounds = itemWidget.getBounds();
        if (bounds == null) {
            return;
        }

        graphics.setColor(new Color(255, 152, 31, 180)); // Soft orange highlight stroke
        graphics.drawRect(bounds.x, bounds.y, bounds.width - 1, bounds.height - 1);
    }
}
