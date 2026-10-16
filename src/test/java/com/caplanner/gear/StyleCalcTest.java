/*
 * Copyright (c) 2026, GuimGod
 * All rights reserved. Licensed under the BSD 2-Clause License (see LICENSE).
 */
package com.caplanner.gear;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/**
 * Checks the reduced formula against the OSRS Wiki DPS calculator: cases.tsv holds the
 * calculator's DPS for every melee style of a weapon against a monster (99 Attack and
 * Strength, super combat, Piety). For each weapon and monster the style this plugin would
 * recommend must be one the calculator puts within 1% of its best. The Tormented Demon's are
 * the mean of two of the calculator's phases, as the plugin counts half of the attacks on it
 * without defence.
 */
public class StyleCalcTest
{
	private static final class Row
	{
		String type;
		String stance;
		double calculator;
		double score;
	}

	@Test
	public void bestStyleAgreesWithTheCalculator() throws Exception
	{
		Map<String, List<Row>> groups = new LinkedHashMap<>();
		try (BufferedReader r = new BufferedReader(new InputStreamReader(
			StyleCalcTest.class.getResourceAsStream("cases.tsv"), StandardCharsets.UTF_8)))
		{
			String line;
			while ((line = r.readLine()) != null)
			{
				if (line.isEmpty() || line.startsWith("#"))
				{
					continue;
				}
				String[] c = line.split("\t");
				String weapon = c[2];
				StyleCalc.Input in = new StyleCalc.Input();
				in.attackLevel = 99 + 19;
				in.strengthLevel = 99 + 19;
				in.prayerAttack = 120;
				in.prayerStrength = 123;
				in.stab = Integer.parseInt(c[9]);
				in.slash = Integer.parseInt(c[10]);
				in.crush = Integer.parseInt(c[11]);
				in.strength = Integer.parseInt(c[12]);
				in.inquisitor = Integer.parseInt(c[13]);
				in.fang = Weapons.isFang(weapon);
				in.corpbane = Weapons.isCorpbane(weapon);
				in.corporealBeast = "Corporeal Beast".equals(c[4]);
				// the Tormented Demon: half of the attacks without defence, every one of them landing, as the plugin
				// counts it until it reads the demon's state; its cases are the mean of the calculator's phases
				// "Shielded" and "Shielded (Defenceless)" (tools/gen_data.py, PHASE_MEAN)
				in.defencelessShare = Weapons.defencelessShare(c[4]);
				String[] m = monsters().get(Integer.parseInt(c[3]));
				in.defenceLevel = Integer.parseInt(m[2]);
				in.defenceStab = Integer.parseInt(m[3]);
				in.defenceSlash = Integer.parseInt(m[4]);
				in.defenceCrush = Integer.parseInt(m[5]);
				Row row = new Row();
				row.type = c[7];
				row.stance = c[8];
				row.calculator = Double.parseDouble(c[14]);
				row.score = StyleCalc.expectedHit(in, row.type, row.stance);
				groups.computeIfAbsent(c[0] + " | " + weapon + " | " + c[3] + " " + c[4], k -> new ArrayList<>()).add(row);
			}
		}
		int compared = 0;
		List<String> wrong = new ArrayList<>();
		for (Map.Entry<String, List<Row>> e : groups.entrySet())
		{
			double bestCalculator = 0;
			Row mine = null;
			for (Row row : e.getValue())
			{
				bestCalculator = Math.max(bestCalculator, row.calculator);
				if (mine == null || row.score > mine.score)
				{
					mine = row;
				}
			}
			if (bestCalculator <= 0)
			{
				continue; // the calculator says no style damages this monster
			}
			compared++;
			if (mine.calculator < bestCalculator * 0.99)
			{
				wrong.add(String.format("%s: picked %s/%s (%.3f), calculator best %.3f", e.getKey(), mine.type, mine.stance,
					mine.calculator, bestCalculator));
			}
		}
		System.out.println("compared " + compared + " weapon and monster pairs, " + wrong.size() + " disagree");
		for (String w : wrong)
		{
			System.out.println("  " + w);
		}
		assertTrue("no pairs compared", compared > 1000);
		assertTrue(wrong.size() + " of " + compared + " pairs disagree with the calculator", wrong.isEmpty());
	}

	private static Map<Integer, String[]> monsters;

	private static Map<Integer, String[]> monsters() throws Exception
	{
		if (monsters == null)
		{
			monsters = new LinkedHashMap<>();
			try (BufferedReader r = new BufferedReader(new InputStreamReader(
				StyleData.class.getResourceAsStream("monsters.tsv"), StandardCharsets.UTF_8)))
			{
				String line;
				while ((line = r.readLine()) != null)
				{
					if (!line.isEmpty() && !line.startsWith("#"))
					{
						String[] c = line.split("\t");
						monsters.put(Integer.parseInt(c[0]), c);
					}
				}
			}
		}
		return monsters;
	}
}
