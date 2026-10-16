/*
 * Copyright (c) 2026, GuimGod
 * All rights reserved. Licensed under the BSD 2-Clause License (see LICENSE).
 */
package com.caplanner.core;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/**
 * The wiki's text on each task, as the plugin ships it: no markup left over and no sentence that lost the value a
 * wiki template carried (the audit of October 2026 found "worth under ." and "fairy ring code ." this way).
 */
public class HowToTextTest
{
	/** Markup the cleaning must not leave, and a word followed by nothing where a template stood. */
	private static final Pattern LEFT_OVER = Pattern.compile(
		"\\{\\{|\\}\\}|\\[\\[|\\]\\]|<ref|&[a-z]+;|&#\\d+;|\\b(?:under|every|code|costing|of|than|at) [.,;)]|\\( ?\\)");

	private static Map<Integer, String> rows(String file) throws Exception
	{
		Map<Integer, String> rows = new HashMap<>();
		try (BufferedReader in = new BufferedReader(new InputStreamReader(
			HowToTextTest.class.getResourceAsStream("/com/caplanner/" + file), StandardCharsets.UTF_8)))
		{
			for (String line; (line = in.readLine()) != null; )
			{
				if (!line.startsWith("#") && line.contains("\t"))
				{
					String[] c = line.split("\t", 2);
					rows.put(Integer.parseInt(c[0]), c[1]);
				}
			}
		}
		return rows;
	}

	@Test
	public void noMarkupNorEmptyValueIsLeft() throws Exception
	{
		List<String> bad = new ArrayList<>();
		for (Map.Entry<Integer, String> e : rows("howto-wiki.tsv").entrySet())
		{
			if (LEFT_OVER.matcher(e.getValue()).find())
			{
				bad.add(e.getKey() + ": " + e.getValue().substring(Math.max(0, findAt(e.getValue()) - 40),
					Math.min(e.getValue().length(), findAt(e.getValue()) + 40)));
			}
		}
		assertTrue(String.join("\n", bad), bad.isEmpty());
	}

	private static int findAt(String text)
	{
		java.util.regex.Matcher m = LEFT_OVER.matcher(text);
		return m.find() ? m.start() : 0;
	}

	@Test
	public void whatFailsATaskIsKeptPastTheCut() throws Exception
	{
		Map<Integer, String> how = rows("howto-wiki.tsv");
		// the wiki's warnings the audit found cut or wiped out, each a way to fail the task
		assertTrue(how.get(14).contains("thralls or recoil damage will automatically fail"));
		assertTrue(how.get(153).contains("Bug: The Keris partisan will fail this challenge."));
		assertTrue(how.get(350).contains("DO NOT equip a magic or ranged weapon"));
		assertTrue(how.get(41).contains("equip an axe"));
		assertTrue(how.get(511).contains("Rapid Heal"));
		assertTrue(how.get(398).contains("fairy ring code DJR"));
		assertTrue(how.get(493).contains("under 2,000,000 coins"));
		assertTrue(how.get(368).contains("every 4 ticks"));
		// a strategy that was wiped out whole by the cleaning, so the task showed none
		assertTrue(how.containsKey(587));
	}

	@Test
	public void theTextIsTheWikisWithItsLicence() throws Exception
	{
		StringBuilder head = new StringBuilder();
		try (BufferedReader in = new BufferedReader(new InputStreamReader(
			HowToTextTest.class.getResourceAsStream("/com/caplanner/howto-wiki.tsv"), StandardCharsets.UTF_8)))
		{
			for (String line; (line = in.readLine()) != null && line.startsWith("#"); )
			{
				head.append(line).append('\n');
			}
		}
		assertTrue(head.toString().contains("CC BY-NC-SA 3.0"));
		assertEquals("the authors' own notes are a file of their own", 21, rows("howto-own.tsv").size());
	}
}
