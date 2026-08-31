package com.groupironmencompanion;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class GroupIronmenCompanionPluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(GroupIronmenCompanionPlugin.class);
		RuneLite.main(args);
	}
}
