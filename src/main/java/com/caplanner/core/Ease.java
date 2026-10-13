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

/** How many players have done each task, from the bundled table: the plugin's measure of "easy". */
public final class Ease
{
	private Ease()
	{
	}

	/** Reads "task id, tab, percent" lines; "#" starts a comment. A line that cannot be read is skipped. */
	public static Map<Integer, Double> read(Reader table) throws IOException
	{
		Map<Integer, Double> out = new HashMap<>();
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
				out.put(Integer.parseInt(cols[0].trim()), Double.parseDouble(cols[1].trim()));
			}
			catch (RuntimeException e)
			{
				// not a line of the table
			}
		}
		return out;
	}
}
