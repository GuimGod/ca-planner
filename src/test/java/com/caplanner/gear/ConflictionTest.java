/*
 * Copyright (c) 2026, GuimGod
 * All rights reserved. Licensed under the BSD 2-Clause License (see LICENSE).
 */
package com.caplanner.gear;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;

/**
 * The Confliction gauntlets (confliction_sets.tsv, tools/gen_confliction.py): with a magic weapon of one hand the accuracy
 * is rolled the calculator's way for them; Tumeken's shadow, two-handed, gets their bonuses only. The Eye of Ayak, the
 * Sanguinesti staff, the Kodai wand and the Shadow with the gauntlets and with the Tormented bracelet, against the Nylocas
 * Hagios, Verzik's second and third phases, Vorkath, Kree'arra and an Abyssal demon.
 */
public class ConflictionTest
{
	private static final String FILE = "confliction_sets.tsv";

	@Test
	public void everyFigureIsTheCalculators() throws IOException
	{
		assertEquals(56, SetCalcTest.figuresOf(FILE).size());
	}

	@Test
	public void theEyeOnVerziksSecondPhase() throws IOException
	{
		Map<String, SetCalcTest.Case> by = new HashMap<>();
		for (SetCalcTest.Case k : SetCalcTest.figuresOf(FILE))
		{
			by.put(k.set + "@" + k.npc, k);
		}
		SetCalcTest.Case eye = by.get("eye-confliction@8372");
		SetCalcTest.Case bracelet = by.get("eye-tormented@8372");
		assertTrue(eye.loadout.conflictionGauntlets);
		assertFalse(eye.loadout.twoHanded);
		// the calculator: 4.4362 against 3.8017 per second, the gauntlets 16.7% more
		assertEquals(16.7, SetCalc.gain(bracelet.got, eye.got), 0.1);
		// without their accuracy roll, their bonuses alone would give much less
		eye.loadout.twoHanded = true;
		double bonusesOnly = SetCalc.perTick(eye.loadout, eye.player, eye.target, false);
		eye.loadout.twoHanded = false;
		assertTrue(SetCalc.gain(bracelet.got, bonusesOnly) < 10);
		// Tumeken's shadow takes both hands: the gauntlets' bonuses only (the calculator: 5.5% more)
		SetCalcTest.Case shadow = by.get("shadow-confliction@8372");
		assertTrue(shadow.loadout.twoHanded);
		assertEquals(5.6, SetCalc.gain(by.get("shadow-tormented@8372").got, shadow.got), 0.1);
	}

	@Test
	public void theRollIsTheCalculators()
	{
		// BaseCalc.getConflictionGauntletsAccuracyRoll: the double roll over one plus it less the single roll
		long attack = 30000;
		long defence = 20000;
		double single = StyleCalc.chance(attack, defence);
		double twice = StyleCalc.fangChance(attack, defence);
		assertEquals(twice / (1 + twice - single), StyleCalc.conflictionChance(attack, defence), 1e-12);
		assertTrue(StyleCalc.conflictionChance(attack, defence) > single);
		List<String> none = java.util.Collections.emptyList();
		SetCalc.Loadout l = new SetCalc.Loadout();
		SetEffects.wornSets(l, java.util.Arrays.asList("Eye of Ayak", "Confliction gauntlets"));
		assertTrue(l.conflictionGauntlets);
		SetEffects.wornSets(l, none);
		assertFalse(l.conflictionGauntlets);
	}
}
