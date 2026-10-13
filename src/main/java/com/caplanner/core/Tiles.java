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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Tiles the plugin can mark in the game for a boss, when the player asks for them: the way in, a
 * safe tile, where to stand for one attack. Only ones the plugin's authors placed and used.
 */
public final class Tiles
{
	/** One tile: where it is, its colour, what is written on it and what it is for. */
	public static final class Tile
	{
		public final int x;
		public final int y;
		public final int plane;
		/** Alpha, red, green, blue. */
		public final int colour;
		public final String label;
		/** What the tile is for, in a few words; may be empty. */
		public final String note;

		Tile(int x, int y, int plane, int colour, String label, String note)
		{
			this.x = x;
			this.y = y;
			this.plane = plane;
			this.colour = colour;
			this.label = label;
			this.note = note;
		}
	}

	private final Map<String, List<Tile>> byBoss = new HashMap<>();

	/** Reads "boss, x, y, plane, colour as AARRGGBB, label, note" lines, tab separated; "#" starts a comment. */
	public static Tiles read(Reader table) throws IOException
	{
		Tiles out = new Tiles();
		BufferedReader in = new BufferedReader(table);
		String line;
		while ((line = in.readLine()) != null)
		{
			if (line.trim().isEmpty() || line.startsWith("#"))
			{
				continue;
			}
			String[] c = line.split("\t", -1);
			if (c.length < 6)
			{
				continue;
			}
			try
			{
				Tile tile = new Tile(Integer.parseInt(c[1].trim()), Integer.parseInt(c[2].trim()), Integer.parseInt(c[3].trim()),
					(int) Long.parseLong(c[4].trim(), 16), c[5].trim(), c.length > 6 ? c[6].trim() : "");
				out.byBoss.computeIfAbsent(Requirements.key(c[0]), k -> new ArrayList<>()).add(tile);
			}
			catch (NumberFormatException e)
			{
				// a line that is not a tile is left out
			}
		}
		return out;
	}

	public static Tiles none()
	{
		return new Tiles();
	}

	/** The tiles of a boss; empty when it has none. */
	public List<Tile> of(String boss)
	{
		return Collections.unmodifiableList(byBoss.getOrDefault(Requirements.key(boss), Collections.<Tile>emptyList()));
	}

	/**
	 * What the tiles of a boss are: the label and the note of each different label, in the order they
	 * come. A tile with nothing written on it is not listed.
	 */
	public List<String[]> legend(String boss)
	{
		Map<String, String> seen = new LinkedHashMap<>();
		for (Tile t : of(boss))
		{
			if (!t.label.isEmpty())
			{
				seen.putIfAbsent(t.label, t.note);
			}
		}
		List<String[]> out = new ArrayList<>();
		for (Map.Entry<String, String> e : seen.entrySet())
		{
			out.add(new String[]{e.getKey(), e.getValue()});
		}
		return out;
	}
}
