package com.burthorpesellcalc;

import com.google.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.MenuEntry;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;

public class BurthorpeMenuSwapper {
    private final Client client;
    private final BurthorpeCalcPlugin plugin;
    private final ItemManager itemManager;

    @Inject
    public BurthorpeMenuSwapper(Client client, BurthorpeCalcPlugin plugin, ItemManager itemManager) {
        this.client = client;
        this.plugin = plugin;
        this.itemManager = itemManager;
    }

    @Subscribe
    public void onMenuOptionClicked(MenuOptionClicked event) {
        String option = event.getMenuOption();
        if (option == null || !option.contains("Set (Sell -")) {
            return;
        }
        // Menu item customization event callbacks routed cleanly here
    }
}
