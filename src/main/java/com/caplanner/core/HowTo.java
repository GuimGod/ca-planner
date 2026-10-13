/*
 * Copyright (c) 2026, GuimGod
 * All rights reserved. Licensed under the BSD 2-Clause License (see LICENSE).
 */
package com.caplanner.core;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * What is written about how to go about a task, to read before going: the text of the task's page
 * on the OSRS Wiki (kept in a file of its own, under the wiki's licence) and, apart from it, notes
 * of this plugin's own. It is text to read beforehand; nothing here watches the fight.
 */
public final class HowTo
{
	private final Map<Integer, List<String>> byTask = new HashMap<>();

	/** Reads "task id, tab, text" lines, the text's line breaks written as "\n"; "#" starts a comment. */
	public static HowTo read(Reader table) throws IOException
	{
		HowTo out = new HowTo();
		BufferedReader in = new BufferedReader(table);
		String line;
		while ((line = in.readLine()) != null)
		{
			if (line.trim().isEmpty() || line.startsWith("#"))
			{
				continue;
			}
			int tab = line.indexOf('\t');
			if (tab <= 0)
			{
				continue;
			}
			try
			{
				List<String> paragraphs = new ArrayList<>();
				for (String p : Arrays.asList(line.substring(tab + 1).split("\\\\n")))
				{
					if (!p.trim().isEmpty())
					{
						paragraphs.add(p.trim());
					}
				}
				if (!paragraphs.isEmpty())
				{
					out.byTask.put(Integer.parseInt(line.substring(0, tab).trim()), Collections.unmodifiableList(paragraphs));
				}
			}
			catch (NumberFormatException e)
			{
				// not a task line
			}
		}
		return out;
	}

	public static HowTo none()
	{
		return new HowTo();
	}

	/** The paragraphs written about the task; empty when there are none. */
	public List<String> of(int taskId)
	{
		return byTask.getOrDefault(taskId, Collections.emptyList());
	}

	public int size()
	{
		return byTask.size();
	}
}
