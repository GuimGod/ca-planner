/*
 * Copyright (c) 2026, GuimGod
 * All rights reserved. Licensed under the BSD 2-Clause License (see LICENSE).
 */
package com.caplanner;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup(CaPlannerPlugin.CONFIG_GROUP)
public interface CaPlannerConfig extends Config
{
	enum Language
	{
		ENGLISH("English"),
		PORTUGUESE("Português (BR)");

		private final String label;

		Language(String label)
		{
			this.label = label;
		}

		@Override
		public String toString()
		{
			return label;
		}
	}

	@ConfigItem(
		keyName = "language",
		name = "Language",
		description = "The language the panel is written in. Names the game gives (items, bosses, spells, tasks) stay in English.<br>"
			+ "In another language the objective of each task is the plugin's own translation, which may be wrong:<br>"
			+ "the game's own words are shown under it.",
		position = 1
	)
	default Language language()
	{
		return Language.ENGLISH;
	}

	@ConfigItem(
		keyName = "updateData",
		name = "Update the monster table",
		description = "When the plugin starts, ask the GitHub repository of Combat Check (the same authors' plugin, whose<br>"
			+ "gear engine this one uses) whether a newer table of monsters and weapons has been published, and use it:<br>"
			+ "a monster the game added is known without waiting for a new version of the plugin. One small request at<br>"
			+ "startup; only numbers and names are read. Off: the plugin uses the table it came with and makes no request.",
		position = 3
	)
	default boolean updateData()
	{
		return true;
	}

	@ConfigItem(
		keyName = CaPlannerPlugin.FORGET_CHANGES,
		name = "Forget my gear changes",
		description = "Tick it to forget every change you made to the recommended gear of the account you are logged in with:<br>"
			+ "every boss shows the recommendation again. It unticks itself. The changes are kept only in these settings,<br>"
			+ "for each account; the plugin sends nothing about them anywhere.",
		position = 4
	)
	default boolean forgetGearChanges()
	{
		return false;
	}
}
