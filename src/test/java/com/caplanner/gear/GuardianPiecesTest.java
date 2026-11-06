/*
 * Copyright (c) 2026, GuimGod
 * All rights reserved. Licensed under the BSD 2-Clause License (see LICENSE).
 */
package com.caplanner.gear;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;

/**
 * A pickaxe on the Guardians of the Chambers of Xeric worn with the ranged or magic armour, against the same pickaxe
 * in the melee armour (guardian_sets.tsv, tools/gen_guardian.py): what the pieces' box says the melee pieces add, against
 * the OSRS Wiki calculator, alone and in a Challenge Mode raid of 23.
 */
public class GuardianPiecesTest
{
	private static final String FILE = "guardian_sets.tsv";
	/** How far the plugin's gain may be from the calculator's, in percentage points. */
	private static final double POINTS = 0.2;

	@Test
	public void meleeArmourOverRangedArmourWithAPickaxe() throws IOException
	{
		List<SetCalcTest.Case> all = SetCalcTest.figuresOf(FILE);
		assertEquals(12, all.size());
		Map<String, SetCalcTest.Case> by = new HashMap<>();
		for (SetCalcTest.Case k : all)
		{
			by.put(k.set + "@" + k.target.defenceLevel + "/" + k.target.hitpoints, k);
		}
		int checked = 0;
		for (SetCalcTest.Case k : all)
		{
			if (k.set.contains("-in-melee-"))
			{
				continue;
			}
			SetCalcTest.Case melee = by.get(k.set.replaceAll("-in-\\w+-armour","-in-melee-armour") + "@"
				+ k.target.defenceLevel + "/" + k.target.hitpoints);
			double want = (melee.expected / k.expected - 1) * 100;
			double got = SetCalc.gain(k.got, melee.got);
			assertEquals(k.set + " (defence " + k.target.defenceLevel + ")", want, got, POINTS);
			// the box writes it "+65%" alone and "+75%" in the Challenge Mode of 23
			assertTrue(got > 60);
			checked++;
		}
		assertEquals(8, checked);
	}
}
