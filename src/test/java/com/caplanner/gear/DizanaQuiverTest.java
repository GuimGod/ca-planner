/*
 * Copyright (c) 2026, GuimGod
 * All rights reserved. Licensed under the BSD 2-Clause License (see LICENSE).
 */
package com.caplanner.gear;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;

/**
 * Dizana's quiver charged, the Blessed Dizana's quiver and Dizana's max cape: 10 more ranged accuracy and 1 more ranged
 * strength on the arrows and bolts shot (tools/gen_quiver.py, quiver_sets.tsv, the OSRS Wiki DPS calculator's own code;
 * OSRS Wiki, Blessed Dizana's quiver and Dizana's max cape). Before, the plugin gave Dizana's max cape its own bonuses
 * only: the Twisted bow came out 0.9% (Vorkath) to 3.5% (Graardor, Kree'arra) under the calculator's.
 */
public class DizanaQuiverTest
{
	/** Each case of quiver_sets.tsv within 0.1% of the calculator. */
	@Test
	public void figuresAreTheCalculators() throws IOException
	{
		List<SetCalcTest.Case> cases = SetCalcTest.cases("quiver_sets.tsv");
		assertEquals(100, cases.size());
		for (SetCalcTest.Case k : cases)
		{
			assertEquals(k.set + " on " + k.npc, k.expected, k.got, k.expected * 0.001);
		}
	}

	/** The quiver's bonus is there with the quiver and the max cape, not with the quiver uncharged, another cape or no ammunition. */
	@Test
	public void theBonusGoesWithTheQuiverAndArrowsOrBolts() throws IOException
	{
		Map<String, SetCalcTest.Case> vorkath = new HashMap<>();
		for (SetCalcTest.Case k : SetCalcTest.cases("quiver_sets.tsv"))
		{
			if (k.npc == 8059)
			{
				vorkath.put(k.set, k);
			}
		}
		for (String weapon : new String[]{"tbow", "zcb-ruby", "acb"})
		{
			double plain = vorkath.get(weapon + "-quiver-uncharged").got;
			for (String cape : new String[]{"dizana-max-cape", "blessed-quiver", "quiver-charged"})
			{
				SetCalcTest.Case k = vorkath.get(weapon + "-" + cape);
				assertTrue(weapon + " " + cape, k.loadout.dizanaQuiver && SetCalc.quiverApplies(k.loadout));
				assertTrue(weapon + " " + cape + " over the uncharged quiver", k.got > plain);
			}
			assertFalse(weapon + " uncharged", vorkath.get(weapon + "-quiver-uncharged").loadout.dizanaQuiver);
			assertFalse(weapon + " assembler", vorkath.get(weapon + "-assembler").loadout.dizanaQuiver);
		}
		// the Bow of Faerdhinen shoots none of the slot's ammunition, and the Heavy ballista's javelins cannot be stored in
		// the quiver (OSRS Wiki, Dizana's quiver; the calculator gives them the bonus): the cape is worn, the bonus does not
		// count
		for (String weapon : new String[]{"bowfa", "ballista"})
		{
			for (String cape : new String[]{"dizana-max-cape", "blessed-quiver", "quiver-charged"})
			{
				SetCalcTest.Case k = vorkath.get(weapon + "-" + cape);
				assertTrue(weapon + " " + cape, k.loadout.dizanaQuiver);
				assertFalse(weapon + " " + cape, SetCalc.quiverApplies(k.loadout));
				assertEquals(weapon + " " + cape, vorkath.get(weapon + "-quiver-uncharged").got, k.got, 1e-9);
			}
		}
	}

	/**
	 * What the plugin gave before (Dizana's max cape with its own bonuses alone) is the calculator's figure for the quiver
	 * uncharged, which has the same bonuses, on every case: the quiver's gain is the calculator's, from about 0.5% (the
	 * Zaryte crossbow on Vorkath) to about 3.5%; the Twisted bow 0.9% on Vorkath, 1.5% on the Leviathan and 3.5% on Graardor
	 * and Kree'arra.
	 */
	@Test
	public void beforeItWasTheUnchargedQuiver() throws IOException
	{
		Map<String, SetCalcTest.Case> uncharged = new HashMap<>();
		List<SetCalcTest.Case> cases = SetCalcTest.cases("quiver_sets.tsv");
		for (SetCalcTest.Case k : cases)
		{
			if (k.set.endsWith("-quiver-uncharged"))
			{
				uncharged.put(k.set.replace("-quiver-uncharged", "") + "@" + k.npc, k);
			}
		}
		int counted = 0;
		for (SetCalcTest.Case k : cases)
		{
			if (!k.set.endsWith("-dizana-max-cape") || k.set.startsWith("bowfa") || k.set.startsWith("ballista"))
			{
				continue;
			}
			SetCalcTest.Case plain = uncharged.get(k.set.replace("-dizana-max-cape", "") + "@" + k.npc);
			k.loadout.dizanaQuiver = false;
			double before = SetCalc.perTick(k.loadout, k.player, k.target, false);
			k.loadout.dizanaQuiver = true;
			assertEquals(k.set + " on " + k.npc + " before", plain.expected, before, plain.expected * 0.001);
			double gain = (k.got / before - 1) * 100;
			assertTrue(k.set + " on " + k.npc + " gain " + gain, gain > 0.4 && gain < 4);
			counted++;
		}
		assertEquals(12, counted);
	}

	/**
	 * The charges: the quiver without them is its own item and name (28947, "Dizana's quiver (uncharged)"), with no bonus;
	 * the charged one, the blessed one and the max cape have it. The arrows and bolts only: no javelins, darts or nothing.
	 */
	@Test
	public void chargedOrNotAndWhatIsShot()
	{
		SetCalc.Loadout l = new SetCalc.Loadout();
		SetEffects.wornSets(l, Arrays.asList("Twisted bow", "Dizana's quiver (uncharged)", "Dragon arrow"));
		l.ammo = "Dragon arrow";
		assertFalse(SetCalc.quiverApplies(l));
		SetEffects.wornSets(l, Arrays.asList("Twisted bow", "Dizana's quiver", "Dragon arrow"));
		assertTrue(SetCalc.quiverApplies(l));
		l.ammo = "Ruby dragon bolts (e)";
		assertTrue(SetCalc.quiverApplies(l));
		l.ammo = "Dragon javelin";
		assertFalse(SetCalc.quiverApplies(l));
		l.ammo = "Bolt rack";
		assertFalse(SetCalc.quiverApplies(l));
		l.ammo = null;
		assertFalse(SetCalc.quiverApplies(l));
		// the game's ids: the uncharged quiver is not read as the charged one
		assertEquals(28947, Variants.base(28947));
		assertEquals(28949, Variants.base(28949));
		assertEquals(28951, Variants.base(28953));
	}

	/** The names: locked too; not uncharged, not broken. */
	@Test
	public void theQuiversByName()
	{
		assertTrue(SetEffects.dizanaQuiver(Arrays.asList("Twisted bow", "Dizana's max cape")));
		assertTrue(SetEffects.dizanaQuiver(Arrays.asList("Dizana's max cape (l)")));
		assertTrue(SetEffects.dizanaQuiver(Arrays.asList("Blessed Dizana's quiver")));
		assertTrue(SetEffects.dizanaQuiver(Arrays.asList("Blessed Dizana's quiver (l)")));
		assertTrue(SetEffects.dizanaQuiver(Arrays.asList("Dizana's quiver")));
		assertFalse(SetEffects.dizanaQuiver(Arrays.asList("Dizana's quiver (uncharged)")));
		assertFalse(SetEffects.dizanaQuiver(Arrays.asList("Dizana's max cape (broken)")));
		assertFalse(SetEffects.dizanaQuiver(Arrays.asList("Dizana's quiver (l) (uncharged)")));
		assertFalse(SetEffects.dizanaQuiver(Arrays.asList("Blessed Dizana's quiver (l) (mangled)")));
		assertFalse(SetEffects.dizanaQuiver(Arrays.asList("Ava's assembler", "Max cape")));
		assertFalse(SetEffects.dizanaQuiver(null));
	}
}
