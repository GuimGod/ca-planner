/*
 * Copyright (c) 2026, GuimGod
 * All rights reserved. Licensed under the BSD 2-Clause License (see LICENSE).
 */
package com.caplanner.gear;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import net.runelite.client.plugins.specialcounter.SpecialWeapon;
import org.junit.Test;

/**
 * The stats a monster wins back with time after special attacks lowered them (OSRS Wiki, Temporary skill drain: "Stats
 * are restored at a rate of 1 per 60 seconds", "Most monsters follow the same rules as players"; Stat restoration:
 * "Every minute, equivalent to 100 ticks"), the monsters with a rate of their own, and the changes of form that give the
 * Defence back.
 */
public class StatRestoreTest
{
	private static final int GRAARDOR = 2215;
	private static final int TEKTON = 7540;

	private static DefenceDrains.Levels levels(int attack, int strength, int defence, int magic, int ranged, int mdef)
	{
		DefenceDrains.Levels l = new DefenceDrains.Levels();
		l.attack = attack;
		l.strength = strength;
		l.defence = defence;
		l.magic = magic;
		l.ranged = ranged;
		l.magicDefence = mdef;
		return l;
	}

	/** General Graardor's levels (Attack 280, Strength 350, Defence 250, Magic 80, Ranged 350, magic defence 298). */
	private static DefenceDrains.Levels graardor()
	{
		return levels(280, 350, 250, 80, 350, 298);
	}

	/** The standard: one level of each lowered stat every 100 ticks after the special attack, up to the full level. */
	@Test
	public void oneLevelEachMinute()
	{
		DefenceDrains d = new DefenceDrains();
		d.add(SpecialWeapon.DRAGON_WARHAMMER, 1, false, 1000);
		assertEquals(175, d.apply(graardor(), false, 0, GRAARDOR, 0).defence);
		assertEquals(175, d.apply(graardor(), false, 0, GRAARDOR, 99).defence);
		assertEquals(176, d.apply(graardor(), false, 0, GRAARDOR, 100).defence);
		assertEquals(185, d.apply(graardor(), false, 0, GRAARDOR, 1000).defence);
		assertEquals(250, d.apply(graardor(), false, 0, GRAARDOR, 100_000).defence);
		// with no monster given (the calculator's way), nothing comes back
		assertEquals(175, d.apply(graardor(), false, 0).defence);
	}

	/**
	 * Every stat lowered comes back ("any boosted or drained stat"), but not the magic defence bonus the Eye of Ayak
	 * lowers (Eye of Ayak: "As Magic Defence is not a stat, NPCs cannot restore it").
	 */
	@Test
	public void everyStatButTheMagicDefenceBonus()
	{
		DefenceDrains d = new DefenceDrains();
		// 250 Defence, 350 Strength, 280 Attack and 20 of Magic: Magic 80 -> 60
		d.add(SpecialWeapon.BANDOS_GODSWORD, 900, false, 0);
		d.add(SpecialWeapon.EYE_OF_AYAK, 50, false, 0);
		DefenceDrains.Levels now = d.apply(graardor(), false, 0, GRAARDOR, 0);
		assertEquals(0, now.defence);
		assertEquals(60, now.magic);
		assertEquals(248, now.magicDefence);
		DefenceDrains.Levels later = d.apply(graardor(), false, 0, GRAARDOR, 300);
		assertEquals(3, later.defence);
		assertEquals(3, later.strength);
		assertEquals(3, later.attack);
		assertEquals(63, later.magic);
		assertEquals(350, later.ranged);
		assertEquals(248, later.magicDefence);
	}

	/**
	 * The levels that came back count for the special attacks landed after them: an Elder maul at tick 0 (250 -> 163), 3
	 * levels back by tick 300 (166), then a Dragon warhammer takes 30% of 166 (117, not 115), and one more by tick 400.
	 */
	@Test
	public void theLevelsBackCountForTheNextSpecialAttack()
	{
		DefenceDrains d = new DefenceDrains();
		d.add(SpecialWeapon.ELDER_MAUL, 1, false, 0);
		d.add(SpecialWeapon.DRAGON_WARHAMMER, 1, false, 300);
		assertEquals(117, d.apply(graardor(), false, 0, GRAARDOR, 0).defence);
		assertEquals(118, d.apply(graardor(), false, 0, GRAARDOR, 100).defence);
		assertEquals(115, d.apply(graardor(), false, 0).defence);
	}

	/** Tekton wins a level back every 15 ticks (OSRS Wiki, Tekton): a Dragon warhammer's 61 levels in 915 ticks. */
	@Test
	public void tekton()
	{
		DefenceDrains d = new DefenceDrains();
		d.add(SpecialWeapon.DRAGON_WARHAMMER, 1, true, 0);
		DefenceDrains.Levels full = levels(390, 390, 205, 205, 1, 0);
		assertEquals(144, d.apply(full, false, 0, TEKTON, 0).defence);
		assertEquals(154, d.apply(full, false, 0, TEKTON, 150).defence);
		assertEquals(205, d.apply(full, false, 0, TEKTON, 915).defence);
	}

	/** Each monster's rate as its OSRS Wiki page gives it, in ticks a level, and the standard for the rest. */
	@Test
	public void theRatesOfTheirOwn()
	{
		int[][] rates = {
			{15, 7540}, {15, 7543}, {15, 7545}, // Tekton, every form
			{10, 7566}, {10, 7567}, // Vasa Nistirio
			{10, 7530}, {10, 7532}, // Vespula
			{16, 7538}, {100, 7539}, // the Vespine soldier flying; walking the standard (the wiki gives flying only)
			{9, 7568}, // the Glowing crystal
			{25, 7604}, {25, 7606}, // the Skeletal Mystics
			{20, 7573}, {20, 7574}, // the Lizardman shamans of the Chambers
			{8, 7569}, {8, 7572}, // the Guardians of the Chambers
			{45, 7533}, // the Abyssal portal
			{10000, 7534}, // the Lux grubs
			{1, 7576}, // the Jewelled Crabs
			{5, 9416}, {5, 377}, // Phosani's Nightmare
			{100, 7584}, // the Ice demon: "the standard rate of 1 per 100 ticks"
			{100, GRAARDOR}, {100, 8340}, {100, 11278}, {100, 319}, // Graardor, Xarpus, Nex, the Corporeal Beast
		};
		for (int[] r : rates)
		{
			assertEquals("npc " + r[1], 0, DefenceDrains.recovered(r[1], r[0] - 1));
			assertEquals("npc " + r[1], 1, DefenceDrains.recovered(r[1], r[0]));
			assertEquals("npc " + r[1], 3, DefenceDrains.recovered(r[1], 3 * r[0] + r[0] - 1));
		}
		// the Nightmare: 20 levels a minute
		assertEquals(0, DefenceDrains.recovered(9425, 99));
		assertEquals(20, DefenceDrains.recovered(9425, 100));
	}

	/** A floor holds the Defence up; the levels come back from there. */
	@Test
	public void fromTheFloor()
	{
		DefenceDrains d = new DefenceDrains();
		d.add(SpecialWeapon.BANDOS_GODSWORD, 300, false, 0);
		// Nex: 260, floor 250
		DefenceDrains.Levels nex = levels(315, 200, 260, 230, 350, 300);
		assertEquals(250, d.apply(nex, false, DefenceDrains.floor(11278, 260), 11278, 0).defence);
		assertEquals(255, d.apply(nex, false, DefenceDrains.floor(11278, 260), 11278, 500).defence);
		assertEquals(260, d.apply(nex, false, DefenceDrains.floor(11278, 260), 11278, 5000).defence);
	}

	/**
	 * Vet'ion and Calvar'ion give their Defence back going into their second form (OSRS Wiki: "any drained Defence levels
	 * will be restored"), not the other way.
	 */
	@Test
	public void theSecondFormOfVetionAndCalvarion()
	{
		assertTrue(DefenceDrains.resets(6611, 6612));
		assertTrue(DefenceDrains.resets(11993, 11994));
		assertFalse(DefenceDrains.resets(6612, 6611));
		assertFalse(DefenceDrains.resets(11994, 11993));
		// the Kalphite Queen keeps them (Kalphite Queen: "when the defence is lowered on KQ's first form it stays lowered
		// on the second form")
		assertFalse(DefenceDrains.resets(963, 965));
	}
}
