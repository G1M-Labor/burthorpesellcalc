package net.runelite.client.plugins.burthorpesellcalc;

import java.util.regex.Pattern;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.ItemContainer;
import net.runelite.api.Menu;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.events.ClientTick;
import net.runelite.api.events.GameStateChanged;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;

public class burthorpemenuswapper {
    private final Client client;
    private final burthorpecalcplugin plugin;
    private final ItemManager itemManager;

    private int lastTrackedWorld = -1;

    @Inject
    public burthorpemenuswapper(Client client, burthorpecalcplugin plugin, ItemManager itemManager) {
        this.client = client;
        this.plugin = plugin;
        this.itemManager = itemManager;
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged event) {
        if (event.getGameState() == GameState.HOPPING || event.getGameState() == GameState.LOGGING_IN) {
            plugin.clearWorldSoldCache();
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
            plugin.clearWorldSoldCache();
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

        int targetAmount = 1;
        Integer forcedBatch = plugin.getForcedBatchSize(canonicalId);
        if (forcedBatch != null) {
            targetAmount = forcedBatch;
        }

        boolean isAllMode = (targetAmount == 999999);
        int itemInteractionQuantity = isAllMode ? 50 : targetAmount;

        if (!isAllMode && plugin.isItemSoldThisWorld(canonicalId)) {
            return;
        }

        // FIXED LAGLESS CHECK ROW:
        // Evaluates high-performance cache matches instantly on tick updates without scanning widgets [source: 7]
        if (!isAllMode && plugin.isCachedShopStockPresent(canonicalId)) {
            return;
        }

        ItemContainer inventory = client.getItemContainer(93);
        if (inventory == null) {
            return;
        }

        MenuEntry nativeTargetOption = null;
        String regexPattern = "^Sell " + itemInteractionQuantity + "\\b";
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
            if (isAllMode) {
                coloredOptionText = "<col=d8b4fe>Sell 50</col>";
            } else {
                switch (targetAmount) {
                    case 1: coloredOptionText = "<col=00ffff>Sell 1</col>"; break;
                    case 5: coloredOptionText = "<col=00ff00>Sell 5</col>"; break;
                    case 10: coloredOptionText = "<col=ffff00>Sell 10</col>"; break;
                    case 50: coloredOptionText = "<col=ff981f>Sell 50</col>"; break;
                    default: coloredOptionText = "Sell " + targetAmount; break;
                }
            }

            defaultLeftClickSlot.setOption(coloredOptionText);
            defaultLeftClickSlot.setIdentifier(nativeTargetOption.getIdentifier());
            defaultLeftClickSlot.setParam0(nativeTargetOption.getParam0());
            defaultLeftClickSlot.setParam1(nativeTargetOption.getParam1());
            defaultLeftClickSlot.setType(MenuAction.CC_OP);

            if (!isAllMode) {
                defaultLeftClickSlot.onClick(e -> plugin.markItemAsSoldThisWorld(canonicalId));
            }
            rootMenu.setMenuEntries(menuEntries);
        }
    }
}
