/*
 * Copyright (c) 2026, GuimGod
 * All rights reserved. Licensed under the BSD 2-Clause License (see LICENSE).
 */
package com.caplanner.gear;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.Test;

/**
 * The weapons' and items' own rules, against the OSRS Wiki calculator's own code (tools/gen_items.py, items_sets.tsv,
 * with the game's names of everything worn) and, where the OSRS Wiki decides the mechanics against the calculator, the
 * wiki: the obsidian armour and the Berserker necklace, the Tonalztics of Ralos uncharged, the weapons without charges,
 * the Dark bow, Ahrim's set, the crystal armour and the Gauntlet, the ammunition each bow and crossbow fires, the Void
 * mage set, the Keris partisan of amascut, the Arclight without charges, the Comp ogre bow, the Dragon hunter wand, the
 * bursts and barrages, and the weapons held for their special attack.
 */
public class ItemRulesTest
{
	@Test
	public void figuresAreTheCalculators() throws IOException
	{
		assertTrue(SetCalcTest.figuresOf("items_sets.tsv").size() > 250);
	}

	/** The cases of one monster, by set. */
	private static Map<String, SetCalcTest.Case> on(int npc) throws IOException
	{
		Map<String, SetCalcTest.Case> cases = new HashMap<>();
		for (SetCalcTest.Case k : SetCalcTest.cases("items_sets.tsv"))
		{
			if (k.npc == npc)
			{
				cases.put(k.set, k);
			}
		}
		return cases;
	}

	private static void first(Map<String, SetCalcTest.Case> cases, String better, String worse)
	{
		SetCalcTest.Case a = cases.get(better);
		SetCalcTest.Case b = cases.get(worse);
		assertNotNull(better, a);
		assertNotNull(worse, b);
		assertTrue("calculator: " + better + " over " + worse + " (" + a.expected + ", " + b.expected + ")", a.expected > b.expected);
		assertTrue("plugin: " + better + " over " + worse + " (" + a.got + ", " + b.got + ")", a.got > b.got);
	}

	/**
	 * CC-14: with a TzHaar weapon the obsidian armour adds a tenth of the accuracy and max hit and the Berserker necklace a
	 * fifth to each hit: the Toktz-xil-ak over the whip and the dragon scimitar in that armour, which the plugin asked for.
	 */
	@Test
	public void obsidianCountsWithATzhaarWeapon() throws IOException
	{
		Map<String, SetCalcTest.Case> brute = on(6918);
		first(brute, "obsidian-toktz-xil-ak", "obsidian-whip");
		first(brute, "obsidian-toktz-xil-ak", "obsidian-dragon-scimitar");
		Map<String, SetCalcTest.Case> giant = on(7261);
		first(giant, "obsidian-tzhaar-ket-om", "obsidian-dwh");
		List<String> set = java.util.Arrays.asList("Toktz-xil-ak", "Obsidian helmet", "Obsidian platebody", "Obsidian platelegs",
			"Berserker necklace (or)");
		SetCalc.Loadout l = new SetCalc.Loadout();
		SetEffects.wornSets(l, set);
		assertTrue(l.obsidianSet && l.berserkerNecklace);
		SetEffects.wornSets(l, set.subList(0, 3));
		assertFalse(l.obsidianSet || l.berserkerNecklace);
		assertTrue(Weapons.isTzhaarWeapon("Tzhaar-ket-om (t)"));
		assertFalse(Weapons.isTzhaarWeapon("Abyssal whip"));
	}

	/** CC-15: the Tonalztics of Ralos hits twice only charged: uncharged it is under the Twisted bow on Graardor. */
	@Test
	public void theTonalzticsHitsOnceUncharged() throws IOException
	{
		Map<String, SetCalcTest.Case> graardor = on(2215);
		first(graardor, "tbow", "tonalztics-uncharged");
		first(graardor, "tonalztics-charged", "tonalztics-uncharged");
		assertEquals(2, graardor.get("tonalztics-charged").got / graardor.get("tonalztics-uncharged").got, 0.01);
	}

	/**
	 * CC-18: a revenant weapon without ether has no extra 50% in the Wilderness (the calculator's, by its version): the
	 * whip over the Ursine chainmace (u) on Callisto. The weapons that cannot attack without charges (OSRS Wiki) get no
	 * figure, so they are never pointed at; the Scythe, the Venator bow and the Tonalztics still attack uncharged.
	 */
	@Test
	public void aWeaponWithoutChargesIsNotCountedCharged() throws IOException
	{
		Map<String, SetCalcTest.Case> callisto = on(6609);
		first(callisto, "whip-wild", "ursine-uncharged-wild");
		first(callisto, "ursine-charged-wild", "whip-wild");
		for (String name : new String[]{"Tumeken's shadow (uncharged)", "Sanguinesti staff (uncharged)",
			"Holy sanguinesti staff (uncharged)", "Eye of Ayak (uncharged)", "Warped sceptre (uncharged)", "Craw's bow (u)",
			"Webweaver bow (u)"})
		{
			SetCalc.Loadout l = new SetCalc.Loadout();
			l.weaponName = name;
			l.category = name.contains("bow") ? "Bow" : "Powered Staff";
			assertFalse(name, SetCalc.plain(l));
			assertEquals(name, -1, SetCalc.perTick(l, new SetCalc.Player(), new SetCalc.Target(), true), 0);
		}
		for (String name : new String[]{"Scythe of Vitur (uncharged)", "Venator bow (uncharged)", "Tonalztics of Ralos (uncharged)",
			"Tumeken's shadow", "Craw's bow"})
		{
			assertFalse(name, Weapons.cannotAttackUncharged(name));
		}
	}

	/**
	 * CC-18: the Bow of Faerdhinen and the Crystal bow "(inactive)", their crystal charges used up, do not attack either
	 * (OSRS Wiki, Crystal bow and Bow of Faerdhinen): no figure, never pointed at, like the other weapons without charges.
	 * Charged, in any colour, they keep theirs.
	 */
	@Test
	public void anInactiveCrystalBowDoesNotAttack()
	{
		for (String name : new String[]{"Bow of faerdhinen (inactive)", "Crystal bow (inactive)"})
		{
			SetCalc.Loadout l = new SetCalc.Loadout();
			l.weaponName = name;
			l.category = "Bow";
			l.speed = 4;
			assertTrue(name, Weapons.cannotAttackUncharged(name));
			assertFalse(name, SetCalc.plain(l));
			assertEquals(name, -1, SetCalc.perTick(l, new SetCalc.Player(), new SetCalc.Target(), true), 0);
		}
		for (String name : new String[]{"Bow of faerdhinen", "Bow of faerdhinen (c)", "Crystal bow", "Arclight (inactive)",
			"Crystal body (inactive)"})
		{
			assertFalse(name, Weapons.cannotAttackUncharged(name));
		}
	}

	/** CC-19: the Dark bow fires two arrows, each with its own roll: over the Magic longbow with amethyst arrows. */
	@Test
	public void theDarkBowFiresTwoArrows() throws IOException
	{
		first(on(7261), "dark-bow", "magic-longbow-amethyst");
		first(on(8059), "dark-bow", "magic-longbow-amethyst");
	}

	/** CC-20: Ahrim's set with the Amulet of the damned: a quarter of the spells 30% more, over the same set with a fury. */
	@Test
	public void ahrimsSetWithTheDamned() throws IOException
	{
		Map<String, SetCalcTest.Case> demon = on(415);
		first(demon, "ahrims-damned-fire-wave", "ahrims-fury-fire-wave");
		SetCalc.Loadout l = new SetCalc.Loadout();
		SetEffects.wornSets(l, java.util.Arrays.asList("Ahrim's staff 100", "Ahrim's hood", "Ahrim's robetop 75",
			"Ahrim's robeskirt", "Amulet of the damned (full)"));
		assertTrue(l.ahrimsSet);
		SetEffects.wornSets(l, java.util.Arrays.asList("Ahrim's staff", "Ahrim's hood", "Ahrim's robetop 0", "Ahrim's robeskirt",
			"Amulet of the damned"));
		assertFalse("a broken piece", l.ahrimsSet);
	}

	/**
	 * CC-28: the Gauntlet's bow and armour have no set effect (the calculator's): the halberd over the bow on the
	 * Crystalline Bear, the other way before. A crystal piece without charges has none either (OSRS Wiki). The Gauntlet's
	 * staves have a figure.
	 */
	@Test
	public void theCrystalSetIsPrifddinassOnly() throws IOException
	{
		Map<String, SetCalcTest.Case> bear = on(9032);
		first(bear, "gauntlet-halberd", "gauntlet-bow");
		assertTrue(bear.get("gauntlet-staff").got > 0);
		first(bear, "gauntlet-staff", "gauntlet-staff-attuned");
		first(bear, "gauntlet-staff-attuned", "gauntlet-staff-basic");
		assertEquals(0, SetEffects.crystalWeight("Crystal body (perfected)"));
		assertEquals(0, SetEffects.crystalWeight("Crystal helm (inactive)"));
		assertEquals(3, SetEffects.crystalWeight("Crystal body"));
		assertFalse(SetEffects.isCrystalBow("Crystal bow (perfected)"));
		assertFalse(SetEffects.isCrystalBow("Corrupted bow (attuned)"));
		assertTrue(SetEffects.isCrystalBow("Crystal bow"));
		assertTrue(SetEffects.isCrystalBow("Bow of Faerdhinen (c)"));
		// without charges the pieces count nothing: the Bow of Faerdhinen is weaker than with the set active
		Map<String, SetCalcTest.Case> graardor = on(2215);
		first(graardor, "bowfa-crystal", "bowfa-crystal-inactive");
	}

	/** The cases of ammo.tsv (tools/gen_items.py): each weapon of the calculator's list and the ammunition it fires. */
	private static Map<Integer, Set<Integer>> ammoLists(List<Integer> every) throws IOException
	{
		Map<Integer, Set<Integer>> fires = new HashMap<>();
		try (BufferedReader r = new BufferedReader(new InputStreamReader(
			ItemRulesTest.class.getResourceAsStream("ammo.tsv"), StandardCharsets.UTF_8)))
		{
			String line;
			while ((line = r.readLine()) != null)
			{
				if (line.startsWith("# every ammunition\t"))
				{
					for (String id : line.split("\t")[1].split(","))
					{
						every.add(Integer.parseInt(id));
					}
				}
				if (line.isEmpty() || line.startsWith("#"))
				{
					continue;
				}
				String[] c = line.split("\t", -1);
				Set<Integer> ids = new HashSet<>();
				for (String id : c[1].isEmpty() ? new String[0] : c[1].split(","))
				{
					ids.add(Integer.parseInt(id));
				}
				fires.put(Integer.parseInt(c[0]), ids);
			}
		}
		return fires;
	}

	/**
	 * CC-29 and CC-47: a weapon fires only the ammunition of the calculator's list for it (ammoApplicability), every
	 * pair of its lists: no dragon arrows in a Magic shortbow (i), no dragon bolts in a Rune crossbow, brutal arrows in
	 * the Comp ogre bow. A weapon or an ammunition the list does not have goes by the word in the name, as before.
	 */
	@Test
	public void aWeaponFiresTheCalculatorsAmmunition() throws IOException
	{
		List<Integer> every = new ArrayList<>();
		Map<Integer, Set<Integer>> fires = ammoLists(every);
		assertEquals(69, fires.size());
		List<String> off = new ArrayList<>();
		for (Map.Entry<Integer, Set<Integer>> w : fires.entrySet())
		{
			assertTrue(String.valueOf(w.getKey()), Weapons.listed(w.getKey()));
			for (int a : every)
			{
				if (Weapons.canFire(w.getKey(), "Bow", "", a, "") != w.getValue().contains(a))
				{
					off.add(w.getKey() + " " + a);
				}
			}
		}
		assertTrue(off.size() + " off: " + off, off.isEmpty());
		assertFalse(Weapons.canFire(12788, "Bow", "Magic shortbow (i)", 11212, "Dragon arrow"));
		assertTrue(Weapons.canFire(12788, "Bow", "Magic shortbow (i)", 21326, "Amethyst arrow"));
		assertFalse(Weapons.canFire(9185, "Crossbow", "Rune crossbow", 21905, "Dragon bolts"));
		assertTrue(Weapons.canFire(4827, "Bow", "Comp ogre bow", 4803, "Rune brutal"));
		assertFalse("the name alone, as before", Weapons.has("Rune brutal", Weapons.ammoKind("Bow", "Comp ogre bow")));
		// not on the lists: by the word in the name
		assertTrue(Weapons.canFire(12765, "Bow", "Dark bow", 11212, "Dragon arrow"));
		assertFalse(Weapons.canFire(12765, "Bow", "Dark bow", 9144, "Runite bolts"));
	}

	/**
	 * CC-30: the Void mage set raises the effective Magic level by 45%, the Elite set adds 5% damage: the Sanguinesti
	 * staff in it as the calculator counts it (it was 30% under). The Elite set over the ordinary one.
	 */
	@Test
	public void theVoidMageSetCounts() throws IOException
	{
		Map<String, SetCalcTest.Case> graardor = on(2215);
		first(graardor, "void-mage-elite-sang", "void-mage-sang");
		SetCalc.Loadout l = new SetCalc.Loadout();
		SetEffects.wornSets(l, java.util.Arrays.asList("Sanguinesti staff", "Void mage helm", "Elite void top", "Elite void robe",
			"Void knight gloves"));
		assertEquals(2, l.voidMagic);
	}

	/**
	 * CC-31: the Keris partisan of amascut with its stab and strength from the OSRS Wiki and the calculator (the RuneLite
	 * data gives none), 50 and 22 less outside the Tombs of Amascut, and 115/100 of the max hit on a kalphite in whole
	 * numbers, as the calculator rounds it: a max hit of 100 is 115, where 100 * 1.15 in floating point is 114.99999...
	 * (the plugin gave 114; for the max hits the keris reaches outside the raid both ways agree).
	 */
	@Test
	public void theKerisPartisanOfAmascut() throws IOException
	{
		assertEquals(108, Weapons.stab(Weapons.KERIS_PARTISAN_OF_AMASCUT, 0));
		assertEquals(67, Weapons.strength(Weapons.KERIS_PARTISAN_OF_AMASCUT, 0));
		assertEquals(57, Weapons.stab(1, 57));
		// (99 + 8) * (532 + 64): a max hit of 100, which the keris makes 115 on a kalphite
		StyleCalc.Input in = new StyleCalc.Input();
		in.attackLevel = 99;
		in.strengthLevel = 99;
		in.strength = 532;
		in.damageNumerator = 115;
		in.damageDenominator = 100;
		assertEquals(115, (long) StyleCalc.chanceAndMax(in, StyleCalc.STAB, "Accurate")[1]);
		in.weaponDamage = 1.15;
		in.damageNumerator = 1;
		in.damageDenominator = 1;
		assertEquals("the floating point way", 114, (long) StyleCalc.chanceAndMax(in, StyleCalc.STAB, "Accurate")[1]);
		// the cases of the file: in and out of the raid, every strength boost from 0 to 19, the calculator's
		assertTrue(on(957).get("keris-amascut").got > 0 && on(11721).get("keris-amascut").got > 0);
	}

	/** CC-40: the zombie axe, the bludgeon, the Brine sabre, the Granite maul, the Leaf-bladed sword and spear have figures. */
	@Test
	public void weaponsWithNoRuleOfTheirOwnHaveFigures() throws IOException
	{
		for (String name : new String[]{"Zombie axe", "Abyssal bludgeon", "Brine sabre", "Granite maul", "Granite maul (or)",
			"Leaf-bladed sword", "Leaf-bladed spear"})
		{
			SetCalc.Loadout l = new SetCalc.Loadout();
			l.weaponName = name;
			l.category = "Slash Sword";
			assertTrue(name, SetCalc.plain(l));
		}
		// the leafy monsters take the leaf-bladed sword's hits
		assertTrue(on(410).get("leaf-bladed-sword").got > 0);
		assertEquals(0, on(410).get("abyssal-bludgeon").got, 0);
	}

	/** CC-46: the Arclight without charges is the Darklight (OSRS Wiki): 60% on a demon, not 70%. */
	@Test
	public void theInactiveArclightIsTheDarklight() throws IOException
	{
		Map<String, SetCalcTest.Case> demon = on(415);
		first(demon, "arclight", "arclight-inactive");
	}

	/**
	 * CC-47: the Comp ogre bow with brutal arrows takes the ammunition's strength alone, the ranged level and 10 (the
	 * calculator's); on the ogres all of its hit.
	 */
	@Test
	public void theCompOgreBowWithBrutals() throws IOException
	{
		Map<String, SetCalcTest.Case> zogre = on(866);
		first(zogre, "comp-ogre-bow-rune-brutal", "comp-ogre-bow-ogre-arrow");
	}

	/** The Dragon hunter wand: on a dragon 75% more accuracy and 40% more damage, over the Kodai wand; not elsewhere. */
	@Test
	public void theDragonHunterWandOnADragon() throws IOException
	{
		first(on(8059), "dragon-hunter-wand-fire-surge", "kodai-fire-surge");
		first(on(7261), "kodai-fire-surge", "dragon-hunter-wand-fire-surge");
		SetCalc.Loadout l = new SetCalc.Loadout();
		l.weaponName = "Dragon hunter wand";
		l.category = "Staff";
		l.spell = 51;
		assertTrue(SetCalc.plain(l));
	}

	/**
	 * CC-07: with a burst or a barrage on autocast no other weapon is pointed at, as with the Venator bow: they hit up to
	 * nine monsters (OSRS Wiki, Varbit 276 for the numbers: Ice Burst 38, Ice Barrage 46). A rush and a blitz hit one.
	 */
	@Test
	public void aBurstOrABarrageKeepsQuiet()
	{
		assertTrue(SetEffects.pointsAtNoOtherWeapon("Kodai wand", 38));
		assertTrue(SetEffects.pointsAtNoOtherWeapon("Kodai wand", 46));
		assertTrue(SetEffects.pointsAtNoOtherWeapon("Ancient sceptre", 35));
		assertTrue(SetEffects.pointsAtNoOtherWeapon("Venator bow", 0));
		assertFalse(SetEffects.pointsAtNoOtherWeapon("Kodai wand", 42));
		assertFalse(SetEffects.pointsAtNoOtherWeapon("Kodai wand", 34));
		assertFalse(SetEffects.pointsAtNoOtherWeapon("Kodai wand", 51));
		assertFalse(SetEffects.pointsAtNoOtherWeapon("Sanguinesti staff", 0));
	}

	/**
	 * CC-43: the Tonalztics of Ralos is held for its special attack, which lowers the defence (DefenceDrains): no weapon
	 * is pointed at while it is in hand, as with the Dragon warhammer.
	 */
	@Test
	public void theTonalzticsIsASpecialAttackWeapon()
	{
		assertTrue(SetEffects.isSpecialAttackWeapon("Tonalztics of Ralos"));
		assertTrue(SetEffects.isSpecialAttackWeapon("Tonalztics of Ralos (uncharged)"));
	}
}
