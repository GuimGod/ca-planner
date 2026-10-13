/*
 * Copyright (c) 2026, GuimGod
 * All rights reserved. Licensed under the BSD 2-Clause License (see LICENSE).
 */
package com.caplanner.core;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The words the plugin writes, in another language. The plugin is written in English; a table
 * gives each line in the other language, with "{}" where a name or a number goes. A line the
 * table does not have stays as it is, which is what keeps the game's own names (items, bosses,
 * spells, tasks) in English.
 */
public final class Lang
{
	private static final int DEEPEST = 4;
	private static final int REMEMBERED = 3000;
	private static final String DOT = " · ";

	private final Map<String, String> whole = new HashMap<>();
	private final List<Pattern> shapes = new ArrayList<>();
	private final List<String> answers = new ArrayList<>();
	private final Map<String, String> remembered = new LinkedHashMap<String, String>(256, 0.75f, true)
	{
		@Override
		protected boolean removeEldestEntry(Map.Entry<String, String> eldest)
		{
			return size() > REMEMBERED;
		}
	};

	/** English, as written: nothing is changed. */
	public static Lang none()
	{
		return new Lang();
	}

	/**
	 * Reads "English, tab, the other language" lines; "#" starts a comment. "{}" in the English
	 * stands for anything, and "{1}", "{2}" in the answer for what was found there, in order.
	 */
	public static Lang read(Reader table) throws IOException
	{
		Lang out = new Lang();
		BufferedReader in = new BufferedReader(table);
		String line;
		while ((line = in.readLine()) != null)
		{
			if (line.trim().isEmpty() || line.startsWith("#"))
			{
				continue;
			}
			int tab = line.indexOf('\t');
			if (tab <= 0 || tab == line.length() - 1)
			{
				continue;
			}
			String english = line.substring(0, tab);
			String other = line.substring(tab + 1);
			if (english.contains("{}"))
			{
				StringBuilder shape = new StringBuilder();
				String[] parts = english.split("\\{\\}", -1);
				for (int i = 0; i < parts.length; i++)
				{
					shape.append(Pattern.quote(parts[i])).append(i < parts.length - 1 ? "(.+?)" : "");
				}
				out.shapes.add(Pattern.compile(shape.toString(), Pattern.DOTALL));
				out.answers.add(other);
			}
			else
			{
				out.whole.put(english, other);
			}
		}
		return out;
	}

	/** Whether this changes anything at all. */
	public boolean isNone()
	{
		return whole.isEmpty() && shapes.isEmpty();
	}

	/** The text in the other language, or as it is when the table does not have it. */
	public String of(String text)
	{
		if (text == null || text.isEmpty() || isNone())
		{
			return text;
		}
		synchronized (remembered)
		{
			String known = remembered.get(text);
			if (known != null)
			{
				return known;
			}
		}
		String out = of(text, 0);
		synchronized (remembered)
		{
			remembered.put(text, out);
		}
		return out;
	}

	private String of(String text, int depth)
	{
		String exact = whole.get(text);
		if (exact != null || depth > DEEPEST)
		{
			return exact != null ? exact : text;
		}
		for (int i = 0; i < shapes.size(); i++)
		{
			Matcher m = shapes.get(i).matcher(text);
			if (m.matches())
			{
				String answer = answers.get(i);
				for (int g = m.groupCount(); g >= 1; g--)
				{
					// what was found may itself be a line of the table ("Finish X" inside a list of reasons)
					answer = answer.replace("{" + g + "}", of(m.group(g), depth + 1));
				}
				return answer;
			}
		}
		// several things said in one line, each on its own
		if (text.contains(DOT))
		{
			StringBuilder out = new StringBuilder();
			for (String part : text.split(Pattern.quote(DOT), -1))
			{
				out.append(out.length() == 0 ? "" : DOT).append(of(part, depth + 1));
			}
			return out.toString();
		}
		// a name and what it is for ("Rock hammer: to finish it off"): the name stays
		int colon = text.indexOf(": ");
		if (colon > 0 && whole.containsKey(text.substring(colon + 2)))
		{
			return text.substring(0, colon + 2) + whole.get(text.substring(colon + 2));
		}
		return text;
	}
}
