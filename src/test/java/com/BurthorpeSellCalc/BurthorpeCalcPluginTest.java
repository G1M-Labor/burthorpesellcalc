package com.BurthorpeSellCalc;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class BurthorpeCalcPluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(BurthorpeCalcPlugin.class);
		RuneLite.main(args);
	}
}