/*
 * Copyright (c) 2026, GuimGod
 * All rights reserved. Licensed under the BSD 2-Clause License (see LICENSE).
 */
package com.caplanner.core;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * What players call a boss besides the name the game gives it ("cg", "dks", "tob"), so that the
 * search finds it by either. Read once from the bundled table.
 */
public final class Nicknames
{
	private static volatile Map<String, List<String>> bundled;

	private Nicknames()
	{
	}

	/** Reads "boss, tab, names separated by ;" lines; "#" starts a comment. Keys and names are as {@link Planner#key} makes them. */
	static Map<String, List<String>> read(Reader table) throws IOException
	{
		Map<String, List<String>> out = new HashMap<>();
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
			List<String> names = new ArrayList<>();
			for (String name : c[1].split(";"))
			{
				String key = Planner.key(name);
				if (!key.isEmpty())
				{
					names.add(key);
				}
			}
			out.put(Planner.key(c[0]), Collections.unmodifiableList(names));
		}
		return out;
	}

	/** The other names of a boss, each as {@link Planner#key} makes it; empty when it has none. */
	public static List<String> of(String boss)
	{
		Map<String, List<String>> all = bundled;
		if (all == null)
		{
			all = new HashMap<>();
			try (InputStream in = Nicknames.class.getResourceAsStream("/com/caplanner/nicknames.tsv"))
			{
				if (in != null)
				{
					all = read(new InputStreamReader(in, StandardCharsets.UTF_8));
				}
			}
			catch (IOException e)
			{
				// the search goes by the game's names alone
			}
			bundled = all;
		}
		return all.getOrDefault(Planner.key(boss), Collections.emptyList());
	}
}
