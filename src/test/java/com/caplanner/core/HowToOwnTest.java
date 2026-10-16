/*
 * Copyright (c) 2026, GuimGod
 * All rights reserved. Licensed under the BSD 2-Clause License (see LICENSE).
 */
package com.caplanner.core;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/** The authors' own notes, as the plugin ships them: every line is read, none is skipped. */
public class HowToOwnTest
{
	@Test
	public void everyLineIsATaskAndBudgetCutterKeepsBothParagraphs() throws Exception
	{
		try (BufferedReader in = reader())
		{
			for (String line; (line = in.readLine()) != null; )
			{
				if (!line.trim().isEmpty() && !line.startsWith("#"))
				{
					// a line with no tab is skipped by the reader: a paragraph lost from the panel
					assertTrue(line, line.indexOf('\t') > 0 && line.substring(0, line.indexOf('\t')).trim().matches("\\d+"));
				}
			}
		}
		HowTo own;
		try (BufferedReader in = reader())
		{
			own = HowTo.read(in);
		}
		assertEquals(2, own.of(493).size());
		assertTrue(own.of(493).get(1).startsWith("To see the figure the game uses"));
	}

	private static BufferedReader reader()
	{
		return new BufferedReader(new InputStreamReader(
			HowToOwnTest.class.getResourceAsStream("/com/caplanner/howto-own.tsv"), StandardCharsets.UTF_8));
	}
}
