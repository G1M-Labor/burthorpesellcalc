package com.burthorpesellcalc;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class burthorpecalcplugintest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(burthorpecalcplugin.class);
		RuneLite.main(args);
	}
}