/*
 * Copyright (c) 2026, GuimGod
 * All rights reserved. Licensed under the BSD 2-Clause License (see LICENSE).
 */
package com.caplanner.gear;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/** The conditional effects of armour, applied the way the plugin applies them. Bonuses are round figures. */
public class SetEffectsTest
{
	private static final int HEAD = 0;
	private static final int NECK = 2;
	private static final int BODY = 4;
	private static final int LEGS = 7;
	private static final int HANDS = 9;

	private int nextId = 1;

	private static SetEffects.Context context(String combatClass, String meleeType, String weapon, String... owned)
	{
		SetEffects.Context c = new SetEffects.Context();
		c.combatClass = combatClass;
		c.meleeType = meleeType;
		c.weaponName = weapon;
		c.ownedNames = Arrays.asList(owned);
		return c;
	}

	/** A piece as the plugin builds it: the item's own bonuses, then what its name earns in the context. */
	private SwitchSet.Piece piece(String name, int slot, SetEffects.Context c, int stab, int slash, int crush, int str,
		int ranged, int rangedStr, int magic, double magicDamage)
	{
		SwitchSet.Piece p = new SwitchSet.Piece();
		p.itemId = nextId++;
		p.slot = slot;
		p.stab = stab;
		p.slash = slash;
		p.crush = crush;
		p.strength = str;
		p.ranged = ranged;
		p.rangedStrength = rangedStr;
		p.magic = magic;
		p.magicDamage = magicDamage + SetEffects.magicDamageBonus(name, c);
		p.setBonus = SetEffects.scoreBonus(name, c);
		p.keep = SetEffects.keep(name);
		p.carriedForUse = SetEffects.carriedForUse(name);
		return p;
	}

	private static SwitchSet.Piece[] worn(SwitchSet.Piece... pieces)
	{
		SwitchSet.Piece[] w = new SwitchSet.Piece[SwitchSet.SLOTS];
		for (SwitchSet.Piece p : pieces)
		{
			w[p.slot] = p;
		}
		return w;
	}

	private static List<Integer> ids(SwitchSet.Piece... pieces)
	{
		List<Integer> ids = new ArrayList<>();
		for (SwitchSet.Piece p : pieces)
		{
			ids.add(p.itemId);
		}
		return ids;
	}

	@Test
	public void crystalArmourBeatsMasoriOnlyWithACrystalBow()
	{
		SetEffects.Context bowfa = context(StyleCalc.RANGED, null, "Bow of faerdhinen (c)");
		SwitchSet.Piece masori = piece("Masori body (f)", BODY, bowfa, 0, 0, 0, 0, 43, 4, -4, 0);
		SwitchSet.Piece crystal = piece("Crystal body", BODY, bowfa, 0, 0, 0, 0, 31, 0, -18, 0);
		assertEquals(ids(crystal), SwitchSet.missing(StyleCalc.RANGED, null, worn(masori), Arrays.asList(crystal)));

		SetEffects.Context tbow = context(StyleCalc.RANGED, null, "Twisted bow");
		masori = piece("Masori body (f)", BODY, tbow, 0, 0, 0, 0, 43, 4, -4, 0);
		crystal = piece("Crystal body", BODY, tbow, 0, 0, 0, 0, 31, 0, -18, 0);
		assertTrue(SwitchSet.missing(StyleCalc.RANGED, null, worn(masori), Arrays.asList(crystal)).isEmpty());
		// and the other way round: with a Twisted bow, Masori in the bag is the better body
		assertEquals(ids(masori), SwitchSet.missing(StyleCalc.RANGED, null, worn(crystal), Arrays.asList(masori)));
	}

	@Test
	public void theThreeCrystalPiecesWeighOneTwoThree()
	{
		SetEffects.Context bowfa = context(StyleCalc.RANGED, null, "Bow of faerdhinen");
		assertEquals(SetEffects.CRYSTAL_UNIT, SetEffects.scoreBonus("Crystal helm", bowfa), 0);
		assertEquals(2 * SetEffects.CRYSTAL_UNIT, SetEffects.scoreBonus("Crystal legs", bowfa), 0);
		assertEquals(3 * SetEffects.CRYSTAL_UNIT, SetEffects.scoreBonus("Crystal body", bowfa), 0);
		assertEquals(0, SetEffects.scoreBonus("Crystal shield", bowfa), 0);
		assertTrue(SetEffects.isCrystalBow("Crystal bow"));
		assertFalse(SetEffects.isCrystalBow("Magic shortbow"));
	}

	@Test
	public void aSlayerHelmetAndASalveAmuletStayOn()
	{
		SetEffects.Context magic = context(StyleCalc.MAGIC, null, "Tumeken's shadow");
		SwitchSet.Piece helmet = piece("Slayer helmet (i)", HEAD, magic, 0, 0, 0, 0, 3, 0, 3, 0);
		SwitchSet.Piece salve = piece("Salve amulet(ei)", NECK, magic, 0, 0, 0, 0, 0, 0, 0, 0);
		SwitchSet.Piece hat = piece("Ancestral hat", HEAD, magic, 0, 0, 0, 0, -2, 0, 8, 3);
		SwitchSet.Piece occult = piece("Occult necklace", NECK, magic, 0, 0, 0, 0, 0, 0, 12, 5);
		assertTrue(SwitchSet.missing(StyleCalc.MAGIC, null, worn(helmet, salve), Arrays.asList(hat, occult)).isEmpty());
	}

	@Test
	public void theVoidSetInUseIsAskedForAsAWhole()
	{
		// a ranged void switch carried while the mage set is on, bow in hand
		String[] owned = {"Void ranger helm", "Void mage helm", "Void knight top", "Void knight robe", "Void knight gloves"};
		SetEffects.Context ranged = context(StyleCalc.RANGED, null, "Twisted bow", owned);
		SwitchSet.Piece mageHelm = piece("Void mage helm", HEAD, ranged, 0, 0, 0, 0, 0, 0, 0, 0);
		SwitchSet.Piece top = piece("Void knight top", BODY, ranged, 0, 0, 0, 0, 0, 0, 0, 0);
		SwitchSet.Piece robe = piece("Void knight robe", LEGS, ranged, 0, 0, 0, 0, 0, 0, 0, 0);
		SwitchSet.Piece gloves = piece("Void knight gloves", HANDS, ranged, 0, 0, 0, 0, 0, 0, 0, 0);
		SwitchSet.Piece rangerHelm = piece("Void ranger helm", HEAD, ranged, 0, 0, 0, 0, 0, 0, 0, 0);
		SwitchSet.Piece masoriBody = piece("Masori body (f)", BODY, ranged, 0, 0, 0, 0, 43, 4, -4, 0);
		// only the helm changes between void sets; the robes are already on and are not traded for Masori
		assertEquals(ids(rangerHelm),
			SwitchSet.missing(StyleCalc.RANGED, null, worn(mageHelm, top, robe, gloves), Arrays.asList(rangerHelm, masoriBody)));
	}

	@Test
	public void voidRobesWithoutTheHelmOfTheStyleAreJustRobes()
	{
		SetEffects.Context melee = context(StyleCalc.MELEE, StyleCalc.SLASH, "Scythe of vitur", "Void mage helm", "Void knight top");
		assertEquals(0, SetEffects.scoreBonus("Void knight top", melee), 0);
		assertEquals(0, SetEffects.scoreBonus("Void mage helm", melee), 0);
	}

	@Test
	public void inquisitorsCountsMoreOnCrush()
	{
		SetEffects.Context crush = context(StyleCalc.MELEE, StyleCalc.CRUSH, "Inquisitor's mace");
		SetEffects.Context slash = context(StyleCalc.MELEE, StyleCalc.SLASH, "Scythe of vitur");
		assertEquals(2 * SetEffects.INQUISITOR_UNIT, SetEffects.scoreBonus("Inquisitor's hauberk", crush), 0);
		assertEquals(0, SetEffects.scoreBonus("Inquisitor's hauberk", slash), 0);
	}

	@Test
	public void aMeleePieceIsJudgedByTheDamageTypeInUse()
	{
		// a crush body and a slash body, same strength; the style in use decides
		SetEffects.Context slash = context(StyleCalc.MELEE, StyleCalc.SLASH, "Scythe of vitur");
		SwitchSet.Piece crushBody = piece("Crush body", BODY, slash, -11, -11, 12, 4, 0, 0, 0, 0);
		SwitchSet.Piece slashBody = piece("Slash body", BODY, slash, 0, 10, 0, 4, 0, 0, 0, 0);
		assertEquals(ids(slashBody), SwitchSet.missing(StyleCalc.MELEE, StyleCalc.SLASH, worn(crushBody), Arrays.asList(slashBody)));
		assertTrue(SwitchSet.missing(StyleCalc.MELEE, StyleCalc.CRUSH, worn(crushBody), Arrays.asList(slashBody)).isEmpty());
	}

	@Test
	public void virtusOnlyOnAncientsFromAStaff()
	{
		SetEffects.Context c = context(StyleCalc.MAGIC, null, "Kodai wand");
		c.castingStaff = true;
		c.ancientSpellbook = true;
		assertEquals(SetEffects.VIRTUS_ANCIENT_BONUS, SetEffects.magicDamageBonus("Virtus robe top", c), 0);
		c.ancientSpellbook = false;
		assertEquals(0, SetEffects.magicDamageBonus("Virtus robe top", c), 0);
		c.ancientSpellbook = true;
		c.castingStaff = false; // Tumeken's shadow
		assertEquals(0, SetEffects.magicDamageBonus("Virtus robe top", c), 0);
		assertEquals(0, SetEffects.magicDamageBonus("Ancestral robe top", c), 0);
	}

	@Test
	public void aTeleportRingIsNeverAskedForAndALightbearerStaysOn()
	{
		SetEffects.Context melee = context(StyleCalc.MELEE, StyleCalc.SLASH, "Scythe of vitur");
		SwitchSet.Piece lightbearer = piece("Lightbearer", 12, melee, 0, 0, 0, 0, 0, 0, 0, 0);
		SwitchSet.Piece shadows = piece("Ring of shadows", 12, melee, 4, 4, 4, 2, 4, 2, 4, 2);
		SwitchSet.Piece bellator = piece("Bellator ring", 12, melee, 0, 20, 0, 6, 0, 0, 0, 0);
		// the Ring of shadows is a teleport: not asked for over a Lightbearer, nor over an empty finger
		assertTrue(SwitchSet.missing(StyleCalc.MELEE, StyleCalc.SLASH, worn(lightbearer), Arrays.asList(shadows)).isEmpty());
		assertTrue(SwitchSet.missing(StyleCalc.MELEE, StyleCalc.SLASH, worn(), Arrays.asList(shadows)).isEmpty());
		// and a Lightbearer is worn for its effect: a damage ring in the bag does not replace it
		assertTrue(SwitchSet.missing(StyleCalc.MELEE, StyleCalc.SLASH, worn(lightbearer), Arrays.asList(bellator)).isEmpty());
	}

	@Test
	public void whatIsKept()
	{
		for (String name : new String[]{"Slayer helmet (i)", "Black mask (10) (i)", "Salve amulet (e)", "Void knight top",
			"Elite void robe", "Blood moon tassets", "Berserker necklace", "Obsidian platelegs"})
		{
			assertTrue(name, SetEffects.keep(name));
		}
		for (String name : new String[]{"Ancestral hat", "Masori body (f)", "Occult necklace", "Crystal body", "Torva platebody"})
		{
			assertFalse(name, SetEffects.keep(name));
		}
	}

	@Test
	public void slayerBraceletStaysAndDiaryGlovesAreNeverAskedFor()
	{
		// reported in game: the plugin asked for Karamja gloves 4 (+1 all round) over an Expeditious bracelet (no bonuses)
		assertTrue(SetEffects.keep("Expeditious bracelet"));
		assertTrue(SetEffects.keep("Bracelet of slaughter"));
		assertTrue(SetEffects.carriedForUse("Karamja gloves 4"));
		assertTrue(SetEffects.carriedForUse("Explorer's ring 4"));
		assertFalse(SetEffects.carriedForUse("Ferocious gloves"));
	}

	@org.junit.Test
	public void theTomeOfTheElementOnAutocastCounts()
	{
		org.junit.Assert.assertEquals("fire", SetEffects.autocastElement(51));
		org.junit.Assert.assertEquals("water", SetEffects.autocastElement(2));
		org.junit.Assert.assertEquals("earth", SetEffects.autocastElement(15));
		org.junit.Assert.assertEquals("air", SetEffects.autocastElement(48));
		org.junit.Assert.assertNull(SetEffects.autocastElement(46));
		org.junit.Assert.assertNull(SetEffects.autocastElement(0));
		SetEffects.Context c = new SetEffects.Context();
		c.castingStaff = true;
		c.autocastElement = "fire";
		org.junit.Assert.assertTrue(SetEffects.tomeInUse("Tome of fire", c));
		org.junit.Assert.assertFalse(SetEffects.tomeInUse("Tome of fire (empty)", c));
		org.junit.Assert.assertFalse(SetEffects.tomeInUse("Tome of water", c));
		org.junit.Assert.assertEquals(10, SetEffects.magicDamageBonus("Tome of fire", c), 0);
		org.junit.Assert.assertTrue(SetEffects.keep("Tome of fire", c));
		c.autocastElement = null;
		org.junit.Assert.assertFalse(SetEffects.tomeInUse("Tome of fire", c));
	}

	@Test
	public void aWeaponHeldForItsSpecialAttackIsKnown()
	{
		for (String name : new String[]{"Dragon claws", "Burning claws", "Voidwaker", "Dragon dagger(p++)", "Dragon warhammer",
			"Elder maul (or)", "Saradomin godsword", "Armadyl godsword (or)", "Dark bow", "Crimson kisten"})
		{
			org.junit.Assert.assertTrue(name, SetEffects.isSpecialAttackWeapon(name));
		}
		for (String name : new String[]{"Scythe of vitur", "Abyssal whip", "Osmumten's fang", "Twisted bow", "Toxic blowpipe",
			"Emberlight", "Soulreaper axe", "Zaryte crossbow", "Tumeken's shadow", null})
		{
			org.junit.Assert.assertFalse(String.valueOf(name), SetEffects.isSpecialAttackWeapon(name));
		}
	}

	@Test
	public void aWeaponChosenForHittingSeveralMonstersIsKnown()
	{
		org.junit.Assert.assertTrue(SetEffects.isManyTargetWeapon("Venator bow"));
		org.junit.Assert.assertTrue(SetEffects.isManyTargetWeapon("Venator bow (uncharged)"));
		org.junit.Assert.assertFalse(SetEffects.isManyTargetWeapon("Toxic blowpipe"));
		org.junit.Assert.assertFalse(SetEffects.isManyTargetWeapon("Twisted bow"));
		org.junit.Assert.assertFalse(SetEffects.isManyTargetWeapon(null));
	}
}
