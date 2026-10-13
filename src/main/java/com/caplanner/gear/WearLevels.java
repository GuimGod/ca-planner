/*
 * Copyright (c) 2026, GuimGod
 * All rights reserved. Licensed under the BSD 2-Clause License (see LICENSE).
 */
package com.caplanner.gear;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * The levels an item asks for before it can be worn, read from the bundled table: the game's
 * own data does not say. An item the table does not have asks for nothing, as far as this knows;
 * quests an item needs are not here.
 */
final class WearLevels
{
	private final Map<String, Map<String, Integer>> byName = new HashMap<>();

	/** Reads "name, tab, skill N; skill N" lines; "#" starts a comment. A part that is not understood is left out. */
	static WearLevels read(Reader table) throws IOException
	{
		WearLevels out = new WearLevels();
		BufferedReader in = new BufferedReader(table);
		String line;
		while ((line = in.readLine()) != null)
		{
			if (line.trim().isEmpty() || line.startsWith("#"))
			{
				continue;
			}
			String[] c = line.split("\t");
			if (c.length < 2)
			{
				continue;
			}
			Map<String, Integer> levels = new HashMap<>();
			for (String part : c[1].split(";"))
			{
				String p = part.trim();
				int space = p.lastIndexOf(' ');
				if (space <= 0)
				{
					continue;
				}
				try
				{
					levels.put(p.substring(0, space).trim().toLowerCase(Locale.ROOT), Integer.parseInt(p.substring(space + 1).trim()));
				}
				catch (NumberFormatException e)
				{
					// not a level
				}
			}
			if (!levels.isEmpty())
			{
				out.byName.put(c[0].trim().toLowerCase(Locale.ROOT), levels);
			}
		}
		return out;
	}

	/**
	 * What the item asks for, or null for nothing. A variant the table does not name (an ornament
	 * kit, a charge count, a colour in brackets) asks for what the plain item does.
	 */
	Map<String, Integer> of(String itemName)
	{
		String name = itemName == null ? "" : itemName.toLowerCase(Locale.ROOT).trim();
		while (!name.isEmpty())
		{
			Map<String, Integer> levels = byName.get(name);
			if (levels != null)
			{
				return levels;
			}
			int bracket = name.lastIndexOf('(');
			if (bracket <= 0 || !name.endsWith(")"))
			{
				return null;
			}
			name = name.substring(0, bracket).trim();
		}
		return null;
	}

	/**
	 * Whether a player of these levels can wear the item. A skill whose level is not known does
	 * not stand in the way.
	 *
	 * @param levels real levels by skill name in lower case
	 */
	boolean canWear(String itemName, Map<String, Integer> levels)
	{
		Map<String, Integer> needs = of(itemName);
		if (needs == null || levels == null)
		{
			return true;
		}
		for (Map.Entry<String, Integer> need : needs.entrySet())
		{
			Integer have = levels.get(need.getKey());
			if (have != null && have < need.getValue())
			{
				return false;
			}
		}
		return true;
	}

	int size()
	{
		return byName.size();
	}
}
