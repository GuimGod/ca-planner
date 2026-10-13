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
}
