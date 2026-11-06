/*
 * Copyright (c) 2026, GuimGod
 * All rights reserved. Licensed under the BSD 2-Clause License (see LICENSE).
 */
package com.caplanner.gear;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;

/**
 * The Virtus robes on the Ancient Magicks, the echo Virtus too (tools/gen_virtus.py, virtus_sets.tsv, the OSRS Wiki DPS
 * calculator's own code): 3% more magic damage a piece with a combat spell of the Ancients, which the plugin adds to each
 * piece by its name (SetEffects.magicDamageBonus), as here.
 */
public class VirtusTest
{
	/** The game's names of everything worn, by set (column 40). */
	private static Map<String, List<String>> names() throws IOException
	{
		Map<String, List<String>> out = new HashMap<>();
		try (BufferedReader r = new BufferedReader(new InputStreamReader(
			VirtusTest.class.getResourceAsStream("virtus_sets.tsv"), StandardCharsets.UTF_8)))
		{
			String line;
			while ((line = r.readLine()) != null)
			{
				if (!line.isEmpty() && !line.startsWith("#"))
				{
					String[] c = line.split("\t", -1);
					out.put(c[0], Arrays.asList(c[40].split(";")));
				}
			}
		}
		return out;
	}

	/** Each case within 0.1% of the calculator, the Ancients' 3% added from the names worn. */
	@Test
	public void figuresAreTheCalculators() throws IOException
	{
		Map<String, List<String>> names = names();
		List<SetCalcTest.Case> cases = SetCalcTest.cases("virtus_sets.tsv");
		assertEquals(72, cases.size());
		for (SetCalcTest.Case k : cases)
		{
			assertEquals(k.set + " on " + k.npc, k.expected, withAncients(k, names.get(k.set)), k.expected * 0.001);
		}
	}

	/** The plugin's figure for the case: each piece's magic damage on the spell cast, as SwitchSet.Piece has it. */
	private static double withAncients(SetCalcTest.Case k, List<String> worn)
	{
		SetEffects.Context c = new SetEffects.Context();
		c.castingStaff = true;
		c.ancientSpellbook = k.loadout.spell >= 31 && k.loadout.spell <= 46;
		double before = k.loadout.magicDamage;
		for (String name : worn)
		{
			k.loadout.magicDamage += SetEffects.magicDamageBonus(name, c);
		}
		double got = SetCalc.perTick(k.loadout, k.player, k.target, false);
		k.loadout.magicDamage = before;
		return got;
	}

	/**
	 * The echo Virtus is the Virtus (OSRS Wiki, Echo virtus robe top: the kit "gives no additional bonuses"): the same 3%
	 * on the Ancients, by its own name too, and on Ice Barrage the whole set over Ancestral, as the Virtus.
	 */
	@Test
	public void theEchoVirtusIsTheVirtus() throws IOException
	{
		SetEffects.Context ancient = new SetEffects.Context();
		ancient.castingStaff = true;
		ancient.ancientSpellbook = true;
		for (String piece : new String[]{"Echo virtus mask", "Echo virtus robe top", "Echo virtus robe bottom"})
		{
			assertEquals(piece, SetEffects.VIRTUS_ANCIENT_BONUS, SetEffects.magicDamageBonus(piece, ancient), 0);
		}
		SetEffects.Context standard = new SetEffects.Context();
		standard.castingStaff = true;
		assertEquals(0, SetEffects.magicDamageBonus("Echo virtus robe top", standard), 0);
		assertEquals(0, SetEffects.magicDamageBonus("Virtus robe top", standard), 0);
		// the game's echo pieces read as the Virtus ones (Variants)
		assertEquals(26241, Variants.base(30437));
		assertEquals(26243, Variants.base(30439));
		assertEquals(26245, Variants.base(30441));

		Map<String, List<String>> names = names();
		Map<String, Double> vorkath = new HashMap<>();
		Map<String, Double> calculator = new HashMap<>();
		for (SetCalcTest.Case k : SetCalcTest.cases("virtus_sets.tsv"))
		{
			if (k.npc == 8059)
			{
				vorkath.put(k.set, withAncients(k, names.get(k.set)));
				calculator.put(k.set, k.expected);
			}
		}
		assertEquals(vorkath.get("virtus-ice-barrage"), vorkath.get("echo-ice-barrage"), 1e-9);
		assertEquals(vorkath.get("virtus-top-ice-barrage"), vorkath.get("echo-top-ice-barrage"), 1e-9);
		// the calculator reads its own echo pieces as Virtus (getCanonicalEquipment): the same figure
		assertEquals(calculator.get("virtus-ice-barrage"), calculator.get("calc-echo-ice-barrage"), 1e-9);
		assertTrue(vorkath.get("echo-ice-barrage") > vorkath.get("ancestral-ice-barrage"));
		// Fire Surge is not of the Ancients: Ancestral is better there
		assertTrue(vorkath.get("ancestral-fire-surge") > vorkath.get("echo-fire-surge"));
	}
}
