/*
 * Copyright (c) 2026, GuimGod
 * All rights reserved. Licensed under the BSD 2-Clause License (see LICENSE).
 */
package com.caplanner.gear;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/**
 * Elemental magic in every figure: the tome of the spell's element, charged (the Tome of
 * Fire with searing pages), the Elemental amulet and never the Occult necklace, and the +2 of each elemental fragment of
 * the Fractured Archive the player broke. The simulator's figures are those of 2026-10-29 (built on the calculator
 * of 2026-10-26): on Dreadnoth the Harmonised nightmare staff on Fire Surge 13.04 at any Defence, the
 * Ascension crossbows 13.21 at 17 (1.3% more), 13.13 at 20, 13.01 at 25, 10.48 at 125. The gear is ArchiveReportTest's.
 */
public class ElementalMagicTest
{
	/** The neck slot, as in RuneLite's EquipmentInventorySlot. */
	private static final int NECK = 2;

	private static SetCalc.Player augury(String... fragments)
	{
		SetCalc.Player p = new SetCalc.Player();
		p.magic = 112;
		p.prayerMagic = 125;
		p.prayerMagicDamage = 4;
		p.fragments.addAll(Arrays.asList(fragments));
		return p;
	}

	private static SetCalc.Player rigour()
	{
		SetCalc.Player p = new SetCalc.Player();
		p.ranged = 112;
		p.prayerRanged = 120;
		p.prayerRangedStrength = 123;
		p.kandarinDiary = true;
		return p;
	}

	/** ArchiveReportTest's Harmonised nightmare staff on Fire Surge, Tome of fire and Elemental amulet; searing pages. */
	private static SetCalc.Loadout harmonised(StyleData data)
	{
		SetCalc.Loadout l = new SetCalc.Loadout();
		l.weaponName = "Harmonised nightmare staff";
		l.category = "Staff";
		l.styles = data.styles(24423);
		l.speed = 5;
		l.stab = 5;
		l.slash = 5;
		l.crush = 5;
		l.magic = 149;
		l.ranged = -6;
		l.strength = 18;
		l.rangedStrength = 3;
		l.magicDamage = 35;
		l.spell = 51; // Fire Surge
		l.tome = SetEffects.tomeElement("Tome of Fire", 51);
		l.searingPages = true;
		l.conflictionGauntlets = true;
		l.elementalAmulet = "all";
		return l;
	}

	/** ArchiveReportTest's Ascension crossbows with Diamond ascension bolts (e). */
	private static SetCalc.Loadout ascension(StyleData data)
	{
		SetCalc.Loadout l = new SetCalc.Loadout();
		l.weaponName = "Ascension crossbows";
		l.category = "Crossbow";
		l.styles = data.styles(ArchiveItems.ASCENSION_CROSSBOWS);
		l.speed = 3;
		l.twoHanded = true;
		l.stab = -3;
		l.slash = -3;
		l.crush = -3;
		l.magic = 4;
		l.ranged = 187;
		l.rangedStrength = 82;
		l.strength = 18;
		l.ammo = "Diamond ascension bolts (e)";
		l.ammoWorn = l.ammo;
		l.dizanaQuiver = true;
		l.bolt = SetCalc.boltKind(l.ammo);
		return l;
	}

	private static double perSecond(SetCalc.Loadout l, SetCalc.Player p, SetCalc.Target t)
	{
		return SetCalc.perTick(l, p, t, false) / 0.6;
	}

	private static SetCalc.Target dreadnoth(StyleData data, int defence)
	{
		SetCalc.Target t = data.monster(16665).target();
		t.defenceLevel = defence;
		return t;
	}

	private static void near(String what, double expected, double got, double tolerance)
	{
		assertTrue(what + ": " + got + " against the simulator's " + expected, Math.abs(got / expected - 1) <= tolerance);
	}

	/**
	 * The fragment: 2 more on the base of the spells of its element only (a fire fragment does nothing to a water spell),
	 * everywhere (OSRS Wiki, Fragment of burning fire: "permanently increase the base max hit of fire spells by 2"), known
	 * by the game's message or by the varbits recorded. With it and the searing pages the staff is the simulator's within
	 * 1%, and the Ascension crossbows come first by the minimum gain where the simulator puts them: about 17.
	 */
	@Test
	public void theFragmentAddsTwoToItsOwnElement() throws IOException
	{
		StyleData data = new StyleData();
		double withFire = perSecond(harmonised(data), augury("fire"), dreadnoth(data, 125));
		near("Harmonised, the fire fragment and the searing pages", 13.04, withFire, 0.01);
		double without = perSecond(harmonised(data), augury(), dreadnoth(data, 125));
		assertTrue(withFire + " over " + without, withFire > without * 1.05);
		assertEquals("a water fragment adds nothing to Fire Surge", without,
			perSecond(harmonised(data), augury("water", "earth", "air"), dreadnoth(data, 125)), 1e-9);
		// where the crossbows come first by the minimum gain, and where the two are even
		int first = -1;
		int even = -1;
		for (int defence = 125; defence >= 0; defence--)
		{
			double crossbows = perSecond(ascension(data), rigour(), dreadnoth(data, defence));
			if (even < 0 && crossbows >= withFire)
			{
				even = defence;
			}
			if (first < 0 && SetCalc.gain(withFire, crossbows) >= 1)
			{
				first = defence;
			}
		}
		System.out.println("Dreadnoth: Harmonised " + withFire + " (without the fragment " + without + "), even at "
			+ even + ", the crossbows 1% ahead at " + first);
		assertTrue("even at " + even + " (the simulator: between 20 and 25)", even >= 18 && even <= 27);
		assertTrue("1% ahead at " + first + " (the simulator: about 17)", first >= 13 && first <= 21);

		// the game's messages and the varbits seen in recorded fights
		assertEquals("fire", SetEffects.brokenFragment("You break open the fragment of burning fire and consume its power. "
			+ "Fire spells will have an increased max hit of +2."));
		assertEquals("earth", SetEffects.brokenFragment("You break open the fragment of living earth and consume its power. "
			+ "Earth spells will have an increased max hit of +2."));
		assertEquals("water", SetEffects.brokenFragment("You break open the fragment of deep water and consume its power. "
			+ "Water spells will have an increased max hit of +2."));
		assertEquals("air", SetEffects.brokenFragment("You break open the fragment of whispering wind and consume its "
			+ "power. Air spells will have an increased max hit of +2."));
		assertNull(SetEffects.brokenFragment("Untradeable drop: Fragment of burning fire"));
		assertEquals(16436, SetEffects.FRAGMENT_FIRE_VARBIT);
		assertEquals(16434, SetEffects.FRAGMENT_EARTH_VARBIT);
	}

	/**
	 * The tome of the spell's element counts only charged ("Tome of Fire (empty)" adds nothing; the item was renamed from
	 * "Tome of fire" on 2026-08-19, both read), and the searing pages only in a charged Tome of Fire on a fire spell: the
	 * least hit a tenth of the max before the tome's tenth (OSRS Wiki, Searing page).
	 */
	@Test
	public void theTomeCountsOnlyChargedAndTheSearingPagesOnlyInIt() throws IOException
	{
		assertEquals("fire", SetEffects.tomeElement("Tome of Fire", 51));
		assertEquals("fire", SetEffects.tomeElement("Tome of fire", 51));
		assertNull(SetEffects.tomeElement("Tome of Fire (empty)", 51));
		assertNull(SetEffects.tomeElement("Tome of fire (empty)", 51));
		assertNull("not the spell's element", SetEffects.tomeElement("Tome of water", 51));
		assertEquals("water", SetEffects.tomeElement("Tome of water", 49)); // Water Surge
		assertNull("no spell on autocast", SetEffects.tomeElement("Tome of Fire", 0));

		StyleData data = new StyleData();
		SetCalc.Target t = dreadnoth(data, 125);
		SetCalc.Loadout searing = harmonised(data);
		SetCalc.Loadout burnt = harmonised(data);
		burnt.searingPages = false;
		SetCalc.Loadout empty = harmonised(data);
		empty.tome = SetEffects.tomeElement("Tome of Fire (empty)", 51);
		empty.searingPages = true;
		double withSearing = perSecond(searing, augury("fire"), t);
		double withBurnt = perSecond(burnt, augury("fire"), t);
		double withEmpty = perSecond(empty, augury("fire"), t);
		// the least hit raises the mean: with a max of 54 the hits run 5 to 54 rather than 0 to 54 (+8.5% in the simulator)
		assertTrue(withSearing + " over " + withBurnt, withSearing > withBurnt * 1.05);
		assertTrue(withBurnt + " over " + withEmpty, withBurnt > withEmpty * 1.05);
		SetCalc.Loadout emptyBurnt = harmonised(data);
		emptyBurnt.tome = null;
		emptyBurnt.searingPages = false;
		assertEquals("pages in no tome count for nothing", perSecond(emptyBurnt, augury("fire"), t), withEmpty, 1e-9);

		// the game's messages on charging and checking the tome
		assertEquals(Boolean.TRUE, SetEffects.searingPages("Your tome has been charged with Searing Pages. It currently "
			+ "holds 7,147 charges."));
		assertEquals(Boolean.FALSE, SetEffects.searingPages("Your tome has been charged with Burnt Pages. It currently "
			+ "holds 1,000 charges."));
		assertNull(SetEffects.searingPages("You add a searing page to the pile."));
	}

	private static SwitchSet.Piece neck(int id, String name, int magic, int magicDamage, SetEffects.Context c)
	{
		SwitchSet.Piece p = new SwitchSet.Piece();
		p.itemId = id;
		p.slot = NECK;
		p.name = name;
		p.magic = magic;
		p.magicDamage = magicDamage;
		p.keep = SetEffects.keep(name, c);
		p.setBonus = SetEffects.scoreBonus(name, c);
		return p;
	}

	private static SetEffects.Context staff(int spell)
	{
		SetEffects.Context c = new SetEffects.Context();
		c.combatClass = StyleCalc.MAGIC;
		c.weaponName = "Harmonised nightmare staff";
		c.castingStaff = true;
		c.autocastElement = SetEffects.autocastElement(spell);
		c.ownedNames = new ArrayList<>();
		return c;
	}

	/**
	 * Never the Occult necklace on an elemental spell: its bonuses (magic 12, magic damage 5%) rank it above the Elemental
	 * amulet's (magic 10), so the switch asked for it. On an elemental spell from a staff the Elemental amulet, or the
	 * amulet of the spell's element, is asked for and kept on; with no elemental spell (a powered staff, a spell of the
	 * Ancient Magicks) the Occult as before.
	 */
	@Test
	public void theOccultNeverTakesTheElementalAmuletsPlace()
	{
		SetEffects.Context fireSurge = staff(51);
		SwitchSet.Piece[] worn = new SwitchSet.Piece[SwitchSet.SLOTS];
		worn[NECK] = neck(12002, "Occult necklace", 12, 5, fireSurge);
		SwitchSet.Piece elemental = neck(29983, "Elemental amulet", 10, 0, fireSurge);
		assertEquals("the Elemental amulet asked for over the Occult", Arrays.asList(29983),
			SwitchSet.missing(StyleCalc.MAGIC, null, worn, Arrays.asList(elemental)));

		worn[NECK] = neck(29983, "Elemental amulet", 10, 0, fireSurge);
		SwitchSet.Piece occult = neck(12002, "Occult necklace", 12, 5, fireSurge);
		assertTrue("the Occult never asked for over the Elemental amulet",
			SwitchSet.missing(StyleCalc.MAGIC, null, worn, Arrays.asList(occult)).isEmpty());
		assertTrue(SetEffects.keep("Elemental amulet", fireSurge));

		// the amulet of the spell's element works as the Elemental amulet; another element's does not
		worn[NECK] = neck(12002, "Occult necklace", 12, 5, fireSurge);
		assertEquals(Arrays.asList(1718), SwitchSet.missing(StyleCalc.MAGIC, null, worn,
			Arrays.asList(neck(1718, "Amulet of fire", 10, 0, fireSurge))));
		assertTrue(SwitchSet.missing(StyleCalc.MAGIC, null, worn,
			Arrays.asList(neck(1716, "Amulet of water", 10, 0, fireSurge))).isEmpty());

		// no elemental spell: the Occult as its bonuses say
		SetEffects.Context shadow = new SetEffects.Context();
		shadow.combatClass = StyleCalc.MAGIC;
		shadow.weaponName = "Tumeken's shadow";
		shadow.ownedNames = new ArrayList<>();
		worn[NECK] = neck(29983, "Elemental amulet", 10, 0, shadow);
		List<Integer> asked = SwitchSet.missing(StyleCalc.MAGIC, null, worn, Arrays.asList(neck(12002, "Occult necklace", 12, 5, shadow)));
		assertEquals(Arrays.asList(12002), asked);
		assertFalse(SetEffects.elementalAmuletInUse("Elemental amulet", staff(45))); // Ice Barrage
	}
}
