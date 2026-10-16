/*
 * Copyright (c) 2026, GuimGod
 * All rights reserved. Licensed under the BSD 2-Clause License (see LICENSE).
 */
package com.caplanner.core;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Where to walk to for a boss: a tile on the way in, measured in the game. Only bosses with a
 * measured tile are in the bundled table; for the others the plugin offers no route. A boss can also
 * have other places worth a route, each under a label of its own: where the key to its lair drops, say.
 * When the route plugins cannot reach the boss, the tile is the last one they reach, with a note saying
 * so; the tile inside, where the boss is, is kept beside it for when they can.
 */
public final class Places
{
	private final Map<String, int[]> tiles = new HashMap<>();
	private final Map<String, int[]> inside = new HashMap<>();
	private final Map<String, String> notes = new HashMap<>();
	private final Map<String, List<Way>> ways = new HashMap<>();

	/** Another place worth a route for a boss: what the button says, what it is for, and the tile. */
	public static final class Way
	{
		public final String label;
		public final String note;
		private final int[] tile;

		Way(String label, String note, int[] tile)
		{
			this.label = label;
			this.note = note;
			this.tile = tile;
		}

		public int[] tile()
		{
			return tile.clone();
		}
	}

	/**
	 * Reads "boss, x, y, plane" lines, tab-separated; "#" starts a comment. A line that cannot be read
	 * is skipped. A line with a label after the plane (and a note after that) is another place for
	 * that boss, not the way to the boss itself. A line with a second tile after the plane (x, y, plane)
	 * and a note after that is the way to a boss the route stops short of: the first tile is where the
	 * route ends, the second where the boss is, and the note says the rest of the way.
	 */
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
				int[] tile = {
					Integer.parseInt(cols[1].trim()), Integer.parseInt(cols[2].trim()), Integer.parseInt(cols[3].trim())};
				String label = cols.length > 4 ? cols[4].trim() : "";
				if (cols.length >= 7 && label.matches("\\d+"))
				{
					// the route stops short: the boss's own tile and what to do from where the route ends
					String key = Requirements.key(cols[0]);
					out.inside.put(key, new int[]{
						Integer.parseInt(label), Integer.parseInt(cols[5].trim()), Integer.parseInt(cols[6].trim())});
					out.tiles.put(key, tile);
					if (cols.length > 7 && !cols[7].trim().isEmpty())
					{
						out.notes.put(key, cols[7].trim());
					}
				}
				else if (label.isEmpty())
				{
					out.tiles.put(Requirements.key(cols[0]), tile);
				}
				else
				{
					out.ways.computeIfAbsent(Requirements.key(cols[0]), k -> new ArrayList<>())
						.add(new Way(label, cols.length > 5 ? cols[5].trim() : "", tile));
				}
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

	/**
	 * The tile {x, y, plane} where the boss is, when the route plugins cannot reach it and {@link #of}
	 * is where they stop; null when the route goes all the way.
	 */
	public int[] inside(String boss)
	{
		int[] tile = inside.get(Requirements.key(boss));
		return tile == null ? null : tile.clone();
	}

	/** What to do from where the route ends, when it stops short of the boss; empty when it does not. */
	public String note(String boss)
	{
		return notes.getOrDefault(Requirements.key(boss), "");
	}

	/** The other places worth a route for the boss, in the order of the table; empty when there is none. */
	public List<Way> ways(String boss)
	{
		List<Way> list = ways.get(Requirements.key(boss));
		return list == null ? Collections.<Way>emptyList() : Collections.unmodifiableList(list);
	}
}
