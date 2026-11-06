/*
 * Copyright (c) 2026, GuimGod
 * All rights reserved. Licensed under the BSD 2-Clause License (see LICENSE).
 */
package com.caplanner.gear;

import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;

/**
 * Every monster of the calculator's data with a row in the table, and every weapon of the table, against the OSRS Wiki
 * DPS calculator (sweep_sets.tsv, tools/gen_sweep.py): each figure the plugin shows must be within 0.1% of the
 * calculator's. A weapon the plugin gives no figure ({@link SetCalc#plain}, or a figure of -1: the Royal Titans'
 * ranged) is left out: it is never shown or pointed at.
 */
public class SweepTest
{
	private static final double FIGURE = 0.001;
	/**
	 * Where the OSRS Wiki decides against the calculator and the plugin follows the wiki (README, "Other fights with a
	 * rule of their own"; MonsterTableTest checks each in the table): the monster, and the class whose figures differ
	 * ("any" for all).
	 */
	private static final Map<Integer, String> WIKI_DECIDES = new HashMap<>();

	static
	{
		// the Cave kraken and its whirlpool: "Melee cannot be used, including halberds"
		WIKI_DECIDES.put(492, StyleCalc.MELEE);
		WIKI_DECIDES.put(493, StyleCalc.MELEE);
		// the Respiratory systems take melee from a halberd only
		WIKI_DECIDES.put(5914, StyleCalc.MELEE);
		// a halberd hurts Vespula in flight (the game's change of 6 August 2025)
		WIKI_DECIDES.put(7530, StyleCalc.MELEE);
		// Fareed takes "any kind of Ranged weapon", not arrows only
		WIKI_DECIDES.put(3456, StyleCalc.RANGED);
		// the Ruptura araxyte takes the max hit as the Acidic and Mirrorback ones do (the same sentence on the three pages)
		WIKI_DECIDES.put(13673, "any");
	}

	@Test
	public void everyMonsterAndWeaponIsCloseToTheCalculator() throws IOException
	{
		List<SetCalcTest.Case> all = SetCalcTest.cases("sweep_sets.tsv");
		List<String> off = new ArrayList<>();
		int checked = 0;
		for (SetCalcTest.Case k : all)
		{
			String decided = WIKI_DECIDES.get(k.npc);
			if (!SetCalc.plain(k.loadout) || k.got < 0
				|| decided != null && (decided.equals("any") || decided.equals(SetCalc.combatClass(k.loadout))))
			{
				continue;
			}
			checked++;
			if (Math.abs(k.got - k.expected) > FIGURE * k.expected)
			{
				off.add(String.format("%s on %d: %.5f, calculator %.5f", k.set, k.npc, k.got, k.expected));
			}
		}
		assertTrue("too few cases checked: " + checked, checked > all.size() / 2);
		assertTrue(off.size() + " of " + checked + " off: " + off, off.isEmpty());
	}

	/**
	 * The weapons the sweep found counted where the game or the calculator has a rule the plugin does not model: no
	 * figure for them, so they are never shown or pointed at (SetCalc.NOT_PLAIN, with the OSRS Wiki's pages).
	 */
	@Test
	public void weaponsWithARuleNotModelledHaveNoFigure()
	{
		for (String name : new String[]{"Bone mace", "Bone shortbow", "Dark bow (bh)", "Drygore blowpipe"})
		{
			SetCalc.Loadout l = new SetCalc.Loadout();
			l.weaponName = name;
			l.category = "Spiked";
			assertTrue(name, !SetCalc.plain(l));
		}
		// Dinh's bulwark has its figure (BulwarkTest), and so has the Dark bow itself, with its two arrows
		SetCalc.Loadout bulwark = new SetCalc.Loadout();
		bulwark.weaponName = "Dinh's bulwark";
		bulwark.category = "Bulwark";
		assertTrue(SetCalc.plain(bulwark));
		SetCalc.Loadout dark = new SetCalc.Loadout();
		dark.weaponName = "Dark bow";
		dark.category = "Bow";
		assertTrue(SetCalc.plain(dark));
	}
}
