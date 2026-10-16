/*
 * Copyright (c) 2026, GuimGod
 * All rights reserved. Licensed under the BSD 2-Clause License (see LICENSE).
 */
package com.caplanner.gear;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;

/**
 * The Chambers of Xeric's scaling, compared with the OSRS Wiki calculator's own (tools/gen_cox.py), and what it
 * changes in the advice: the gear the calculator ranks first in a big Challenge Mode raid is not the one it ranks
 * first alone.
 */
public class CoxScalingTest
{
	@Test
	public void sameLevelsAsTheCalculator() throws IOException
	{
		StyleData data = new StyleData();
		int rows = 0;
		List<String> off = new ArrayList<>();
		try (BufferedReader in = new BufferedReader(new InputStreamReader(
			CoxScalingTest.class.getResourceAsStream("cox.tsv"), StandardCharsets.UTF_8)))
		{
			String line;
			while ((line = in.readLine()) != null)
			{
				if (line.startsWith("#") || line.trim().isEmpty())
				{
					continue;
				}
				String[] c = line.split("\t", -1);
				StyleData.Monster m = data.monster(Integer.parseInt(c[0]));
				assertNotNull("the table has " + c[0], m);
				CoxScaling.Raid raid = new CoxScaling.Raid(Integer.parseInt(c[3]), "1".equals(c[4]), Integer.parseInt(c[5]),
					Integer.parseInt(c[6]), Integer.parseInt(c[7]));
				DefenceDrains.Levels l = CoxScaling.levels(m, raid);
				int[] got = {l.attack, l.strength, l.defence, l.magic, l.ranged, l.hitpoints};
				for (int i = 0; i < got.length; i++)
				{
					if (got[i] != Integer.parseInt(c[8 + i]))
					{
						off.add(c[0] + " " + c[2] + " " + raid + " skill " + i + ": " + got[i] + ", calculator " + c[8 + i]);
					}
				}
				rows++;
			}
		}
		assertTrue(off.size() + " off: " + off, off.isEmpty());
		assertTrue("the file has its cases", rows > 2000);
	}

	@Test
	public void namedCases() throws IOException
	{
		StyleData data = new StyleData();
		// Tekton in Challenge Mode: his Defence grows 20% below 4 players and 35% from 4 on, not 50%
		DefenceDrains.Levels t3 = CoxScaling.levels(data.monster(7540), new CoxScaling.Raid(3, true));
		assertEquals(250, t3.defence);
		assertEquals(250, t3.magic);
		assertEquals(637, t3.attack);
		assertEquals(900, t3.hitpoints);
		DefenceDrains.Levels t24 = CoxScaling.levels(data.monster(7545), new CoxScaling.Raid(24, true));
		assertEquals(882, t24.attack);
		assertEquals(332, t24.defence);
		assertEquals(5850, t24.hitpoints);
		// the Olm's mage hand: Magic scales with the defensive levels, then is halved
		DefenceDrains.Levels hand = CoxScaling.levels(data.monster(7553), new CoxScaling.Raid(24, true));
		assertEquals(315, hand.defence);
		assertEquals(157, hand.magic);
		assertEquals(4800, hand.hitpoints);
		// the Glowing crystal: no Challenge Mode growth of its Defence or hitpoints
		DefenceDrains.Levels crystal = CoxScaling.levels(data.monster(7568), new CoxScaling.Raid(24, true));
		DefenceDrains.Levels crystalNormal = CoxScaling.levels(data.monster(7568), new CoxScaling.Raid(24, false));
		assertEquals(crystalNormal.defence, crystal.defence);
		assertEquals(crystalNormal.hitpoints, crystal.hitpoints);
		assertTrue(crystal.magic > crystalNormal.magic);
		// Vespula: Magic counts with Defence, not with the offensive levels
		DefenceDrains.Levels vespula = CoxScaling.levels(data.monster(7530), new CoxScaling.Raid(12, true));
		assertEquals(vespula.defence, vespula.magic);
		assertTrue(vespula.attack > vespula.defence);
		// outside a raid, and alone in a normal raid, the table's levels stay as they are
		StyleData.Monster mystic = data.monster(7604);
		DefenceDrains.Levels alone = CoxScaling.levels(mystic, new CoxScaling.Raid(1, false));
		assertEquals(mystic.level, alone.defence);
		assertEquals(mystic.magicLevel, alone.magic);
		assertEquals(mystic.hitpoints, alone.hitpoints);
		assertEquals(mystic.level, CoxScaling.levels(mystic, null).defence);
		// a monster that is not of the raid is never scaled
		StyleData.Monster graardor = data.monster(2215);
		assertEquals(graardor.level, CoxScaling.levels(graardor, new CoxScaling.Raid(24, true)).defence);
	}

	/**
	 * Skeletal Mystics, with the Salve amulet(ei) on both sides: alone in Challenge Mode Tumeken's shadow deals more
	 * than the Twisted bow; in a Challenge Mode raid of 24 the Mystics' Magic level has grown with the party and the
	 * Twisted bow comes first. Without the amulet the bow is first from about 32. Both by the calculator and by the
	 * plugin's figures (the cases of tools/gen_sets.py).
	 */
	@Test
	public void twistedBowOvertakesTheShadowOnMysticsInABigChallengeMode() throws IOException
	{
		Map<String, SetCalcTest.Case> cases = new HashMap<>();
		for (SetCalcTest.Case k : SetCalcTest.cases())
		{
			if (k.npc == 7604)
			{
				cases.put(k.set, k);
			}
		}
		first(cases, "cox1cm-shadow-salve", "cox1cm-tbow-salve");
		first(cases, "cox24cm-tbow-salve", "cox24cm-shadow-salve");
		first(cases, "cox32cm-tbow", "cox32cm-shadow");
		first(cases, "cox1cm-shadow", "cox1cm-tbow");
	}

	private static void first(Map<String, SetCalcTest.Case> cases, String better, String worse)
	{
		SetCalcTest.Case a = cases.get(better);
		SetCalcTest.Case b = cases.get(worse);
		assertNotNull(better, a);
		assertNotNull(worse, b);
		assertTrue("calculator: " + better + " over " + worse, a.expected > b.expected);
		assertTrue("plugin: " + better + " over " + worse + " (" + a.got + ", " + b.got + ")", a.got > b.got);
	}

	/** The raid read from the game is kept while it does not change. */
	@Test
	public void sameRaidIsEqual()
	{
		assertEquals(new CoxScaling.Raid(5, true), new CoxScaling.Raid(5, true, 126, 99, 495));
		assertTrue(!new CoxScaling.Raid(5, true).equals(new CoxScaling.Raid(5, false)));
		CoxScaling.Raid r = new CoxScaling.Raid(0, false);
		assertEquals(1, r.partySize);
		assertSame(r, r);
	}
}
