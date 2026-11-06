/*
 * Copyright (c) 2026, GuimGod
 * All rights reserved. Licensed under the BSD 2-Clause License (see LICENSE).
 */
package com.caplanner.gear;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import org.junit.Test;

/**
 * The sets the planner adds up give the engine what the Combat Check gives it since the Fractured Archive: the
 * ammunition by the item (the Ascension bolts with the Ascension crossbows only), the elemental amulet, Zeal and the
 * elemental fragments broken.
 */
public class LoadoutsArchiveTest
{
	private static final int NECK = 2;

	private static SwitchSet.Piece piece(int id, int slot, String name)
	{
		SwitchSet.Piece p = new SwitchSet.Piece();
		p.itemId = id;
		p.slot = slot;
		p.name = name;
		return p;
	}

	private static SwitchSet.Piece[] set(SwitchSet.Piece... pieces)
	{
		SwitchSet.Piece[] out = new SwitchSet.Piece[SwitchSet.SLOTS];
		for (SwitchSet.Piece p : pieces)
		{
			out[p.slot] = p;
		}
		return out;
	}

	@Test
	public void theAscensionBoltsGoWithTheAscensionCrossbowsOnly() throws IOException
	{
		StyleData data = new StyleData();
		SwitchSet.Piece ascension = piece(ArchiveItems.ASCENSION_CROSSBOWS, SwitchSet.WEAPON, "Ascension crossbows");
		SwitchSet.Piece rune = piece(9185, SwitchSet.WEAPON, "Rune crossbow");
		SwitchSet.Piece bolts = piece(ArchiveItems.DIAMOND_ASCENSION_BOLTS_E, SwitchSet.AMMO, "Diamond ascension bolts (e)");
		SwitchSet.Piece rubies = piece(21944, SwitchSet.AMMO, "Ruby dragon bolts (e)");

		SetCalc.Loadout shot = Loadouts.of(set(ascension, bolts), data);
		assertEquals("Diamond ascension bolts (e)", shot.ammo);
		assertEquals(SetCalc.DIAMOND, shot.bolt);

		// another crossbow does not fire them, and the Ascension crossbows fire nothing else
		SetCalc.Loadout notShot = Loadouts.of(set(rune, bolts), data);
		assertNull(notShot.ammo);
		assertEquals("Diamond ascension bolts (e)", notShot.ammoWorn);
		SetCalc.Loadout rubiesUnder = Loadouts.of(set(ascension, rubies), data);
		assertNull(rubiesUnder.ammo);
		assertEquals(0, rubiesUnder.bolt);
	}

	@Test
	public void theElementalAmuletIsWorn() throws IOException
	{
		StyleData data = new StyleData();
		SwitchSet.Piece staff = piece(12899, SwitchSet.WEAPON, "Trident of the swamp");
		assertEquals("all", Loadouts.of(set(staff, piece(1, NECK, "Elemental amulet")), data).elementalAmulet);
		assertEquals("fire", Loadouts.of(set(staff, piece(2, NECK, "Amulet of fire")), data).elementalAmulet);
		assertNull(Loadouts.of(set(staff, piece(3, NECK, "Occult necklace")), data).elementalAmulet);
	}

	@Test
	public void zealAndTheFragmentsStayWithThePlayer()
	{
		Gear.Levels v = new Gear.Levels();
		v.attack = 99;
		v.strength = 99;
		v.ranged = 99;
		v.magic = 99;
		v.prayer = 99;
		v.hitpoints = 99;
		v.piety = true;
		v.augury = true;
		v.zeal = true;
		v.fragments.add("fire");

		SetCalc.Player melee = Gear.player(v, StyleCalc.MELEE);
		assertEquals(ArchiveItems.ZEAL_ATTACK, melee.prayerAttack);
		assertEquals(ArchiveItems.ZEAL_STRENGTH, melee.prayerStrength);
		SetCalc.Player magic = Gear.player(v, StyleCalc.MAGIC);
		assertTrue(magic.fragments.contains("fire"));
		assertTrue(melee.fragments.contains("fire"));
	}
}
