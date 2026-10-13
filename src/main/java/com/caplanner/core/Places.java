/*
 * Copyright (c) 2026, GuimGod
 * All rights reserved. Licensed under the BSD 2-Clause License (see LICENSE).
 */
package com.caplanner.core;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.HashMap;
import java.util.Map;

/**
 * Where to walk to for a boss: a tile on the way in, measured in the game. Only bosses with a
 * measured tile are in the bundled table; for the others the plugin offers no route.
 */
public final class Places
{
	private final Map<String, int[]> tiles = new HashMap<>();

	/** Reads "boss, x, y, plane" lines, tab-separated; "#" starts a comment. A line that cannot be read is skipped. */
	public static Places read(Reader table) throws IOException
	{
		Places out = new Places();
		BufferedReader in = new BufferedReader(table);
		String line;
		while ((line = in.readLine()) != null)
		{
			if (line.trim().isEmpty() || line.startsWith("#"))
			{
				continue;
			}
			String[] cols = line.split("\t");
			try
			{
				out.tiles.put(Requirements.key(cols[0]), new int[]{
					Integer.parseInt(cols[1].trim()), Integer.parseInt(cols[2].trim()), Integer.parseInt(cols[3].trim())});
			}
			catch (RuntimeException e)
			{
				// not a line of the table
			}
		}
		return out;
	}

	/** The tile {x, y, plane} for the boss, or null when none was measured. */
	public int[] of(String boss)
	{
		int[] tile = tiles.get(Requirements.key(boss));
		return tile == null ? null : tile.clone();
	}
}
