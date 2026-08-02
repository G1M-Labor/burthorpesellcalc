package com.burthorpesellcalc;

import com.google.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;

public class burthorpemenuswapper {
    private final Client client;
    private final burthorpecalcplugin plugin;
    private final ItemManager itemManager;

    @Inject
    public burthorpemenuswapper(Client client, burthorpecalcplugin plugin, ItemManager itemManager) {
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
    }
}
