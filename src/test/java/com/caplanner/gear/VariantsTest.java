/*
 * Copyright (c) 2026, GuimGod
 * All rights reserved. Licensed under the BSD 2-Clause License (see LICENSE).
 */
package com.caplanner.gear;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;

/**
 * Every item in another dress is the item: the whole table (variants.tsv, tools/gen_variants.py) and one family of
 * dresses at a time.
 */
public class VariantsTest
{
	/** What every rule kept by name says of an item: the same for a dress and its item. */
	private static String rules(String name)
	{
		StringBuilder s = new StringBuilder();
		s.append(SetEffects.keep(name)).append(SetEffects.carriedForUse(name)).append(SetEffects.maskKind(name))
			.append(SetEffects.salveKind(name)).append(SetEffects.crystalWeight(name)).append(SetEffects.gauntletItem(name))
			.append(SetEffects.isBlessing(name)).append(Weapons.inquisitorWeight(name)).append(Weapons.isFang(name))
			.append(Weapons.isTzhaarWeapon(name)).append(Weapons.cannotAttackUncharged(name));
		for (String cls : new String[]{StyleCalc.MELEE, StyleCalc.RANGED, StyleCalc.MAGIC})
		{
			s.append(SetEffects.voidKind(Collections.singletonList(name), cls));
		}
		return s.toString();
	}

	@Test
	public void theWholeTable() throws IOException
	{
		Map<Integer, Integer> all = Variants.all();
		assertTrue("the table is read: " + all.size(), all.size() > 1500);
		StyleData data = new StyleData();
		List<String> wrong = new ArrayList<>();
		for (Map.Entry<Integer, Integer> e : all.entrySet())
		{
			int dress = e.getKey();
			int item = e.getValue();
			// the item is itself, and the dress is it
			assertEquals(item, Variants.base(item));
			assertEquals(item, Variants.base(dress));
			String dressName = Variants.name(dress);
			String itemName = Variants.name(item);
			assertNotNull(dress + " named", dressName);
			assertNotNull(item + " named", itemName);
			if (!rules(dressName).equals(rules(itemName)))
			{
				wrong.add(dress + " " + dressName + " -> " + item + " " + itemName + ": a rule tells them apart");
			}
			// a weapon the table knows in this dress: the item is in it too, with the same styles
			String category = data.category(dress);
			if (category != null && !category.equals(data.category(item)))
			{
				wrong.add(dress + " " + dressName + " is " + category + ", " + item + " " + itemName + " is "
					+ data.category(item));
			}
			// the ammunition a ranged weapon fires
			if (Weapons.listed(dress) && !Weapons.listed(item))
			{
				wrong.add(dress + " " + dressName + " fires its list, " + item + " has none");
			}
		}
		assertTrue(String.join("\n", wrong), wrong.isEmpty());
	}

	/**
	 * Each weapon in another dress deals what its item does, in a case of each class of the calculator's (sets.tsv):
	 * the rules SetCalc keeps by the weapon's name (the Scythe's three hits, the Keris, the salamanders...) read a dress as
	 * the item.
	 */
	@Test
	public void everyWeaponDealsWhatItsItemDoes() throws IOException
	{
		StyleData data = new StyleData();
		Map<String, SetCalcTest.Case> byClass = new HashMap<>();
		for (SetCalcTest.Case k : SetCalcTest.cases())
		{
			String cls = SetCalc.combatClass(k.loadout);
			if (cls != null && k.target.size >= 3 && !byClass.containsKey(cls))
			{
				byClass.put(cls, k);
			}
		}
		assertEquals(3, byClass.size());
		int compared = 0;
		for (Map.Entry<Integer, Integer> e : Variants.all().entrySet())
		{
			String category = data.category(e.getValue());
			if (category == null)
			{
				continue;
			}
			SetCalc.Loadout bare = new SetCalc.Loadout();
			bare.category = category;
			SetCalcTest.Case k = byClass.get(SetCalc.combatClass(bare));
			if (k == null)
			{
				continue;
			}
			String before = k.loadout.weaponName;
			String beforeCategory = k.loadout.category;
			try
			{
				k.loadout.category = category;
				k.loadout.weaponName = Variants.name(e.getValue());
				double item = SetCalc.perTick(k.loadout, k.player, k.target, false);
				k.loadout.weaponName = Variants.name(e.getKey());
				double dress = SetCalc.perTick(k.loadout, k.player, k.target, false);
				assertEquals(k.loadout.weaponName + " as " + Variants.name(e.getValue()), item, dress, 1e-12);
				compared++;
			}
			finally
			{
				k.loadout.weaponName = before;
				k.loadout.category = beforeCategory;
			}
		}
		assertTrue("weapons compared: " + compared, compared > 300);
	}

	private static void family(int item, int... dresses)
	{
		for (int dress : dresses)
		{
			assertEquals(dress + " " + Variants.name(dress), item, Variants.base(dress));
		}
	}

	@Test
	public void theOathplateRadiant()
	{
		family(30750, 30777, 33462); // Oathplate helm
		family(30753, 30779); // chest
		family(30756, 30781); // legs
	}

	@Test
	public void theTorvaSanguine()
	{
		family(26382, 28254); // Torva full helm
		family(26384, 28256); // platebody
		family(26386, 28258, 33194); // platelegs
	}

	@Test
	public void theScytheHolyAndSanguine()
	{
		family(22325, 25736, 25739); // charged
		family(22486, 25738, 25741); // uncharged: the charged and the uncharged stay apart
	}

	@Test
	public void theTwistedAncestral()
	{
		family(21018, 24664);
		family(21021, 24666);
		family(21024, 24668);
	}

	@Test
	public void theBowOfFaerdhinenInEveryColour()
	{
		family(25865, 25867, 25884, 25886, 25888, 25890, 25892, 25894, 25896, 27187, 33021);
	}

	@Test
	public void theBladeOfSaeldorInEveryColour()
	{
		family(23995, 24551, 25870, 25872, 25874, 25876, 25878, 25880, 25882);
	}

	@Test
	public void theCrystalArmourInEveryColour()
	{
		family(23971, 27705, 27717, 27729, 27741, 27753, 27765, 27777, 33031); // helm
		family(23975, 27697, 27709, 27721, 27733, 27745, 27757, 27769, 33023); // body
		family(23979, 27701, 27713, 27725, 27737, 27749, 27761, 27773, 33027); // legs
	}

	@Test
	public void lockedCapesAndDefenders()
	{
		family(21285, 24133); // Infernal max cape (l)
		family(21898, 24135); // Assembler max cape (l)
		family(27363, 27365); // Masori assembler max cape (l)
		family(28902, 28906); // Dizana's max cape (l)
		family(28951, 28953); // Dizana's quiver (l)
		family(22322, 24186); // Avernic defender (l)
	}

	@Test
	public void theRancourAndOtherOrnamentKits()
	{
		family(29801, 29804); // Amulet of rancour (s)
		family(6585, 12436); // Amulet of fury (or)
		family(13652, 26708, 28039); // Dragon claws (or), (cr)
		family(21003, 27100); // Elder maul (or)
		family(11804, 20370); // Bandos godsword (or)
		family(11832, 26718); // Bandos chestplate (or)
		family(11828, 26715); // Armadyl chestplate (or)
		family(26219, 33174); // Osmumten's fang
		family(12926, 28688); // Toxic blowpipe: the Blazing blowpipe
		family(13072, 26469); // Elite void top (or)
		family(27275, 28547); // Tumeken's shadow: the corrupted one
	}

	@Test
	public void theSlayerHelmetInEveryColour()
	{
		family(11864, 19639, 19643, 19647, 21264, 21888, 23073, 24370, 25898, 25904, 25910, 29816, 33066, 33338, 33340);
		family(11865, 19641, 19645, 19649, 21266, 21890, 23075, 24444, 25900, 25906, 25912, 29818, 33068, 33439, 33445);
	}

	/** Not dresses: another item, with its own bonuses or its own effect. */
	@Test
	public void notDresses()
	{
		for (int item : new int[]{11864, 11865, 12017, 12018, 10588, 31088, 31091, 31097, 27235, 25862, 24225, 24227,
			27277, 27275})
		{
			assertEquals(item, Variants.base(item));
		}
		// imbued, enchanted, the Avernic treads with other boots in them, the Masori (f), the inactive Bow of Faerdhinen,
		// the Granite maul with the ornate handle (its own special attack): none of them is another dress of an item
		for (int[] apart : new int[][]{{11865, 11864}, {12018, 12017}, {31091, 31088}, {31097, 31088}, {27235, 27226},
			{25862, 25865}, {24225, 4153}, {24227, 4153}})
		{
			assertTrue(Arrays.toString(apart), Variants.base(apart[0]) != Variants.base(apart[1]));
		}
	}
}
