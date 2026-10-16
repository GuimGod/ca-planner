/*
 * Copyright (c) 2026, GuimGod
 * All rights reserved. Licensed under the BSD 2-Clause License (see LICENSE).
 */
package com.caplanner.gear;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/**
 * A two-style inventory as carried to the Phantom Muspah: Twisted bow and Masori worn, the magic
 * switch in the inventory. The bonuses are round figures close to the items', enough to tell a
 * magic piece from a ranged one; the plugin reads the real ones from RuneLite.
 */
public class SwitchSetTest
{
	private static final int HEAD = 0;
	private static final int CAPE = 1;
	private static final int NECK = 2;
	private static final int BODY = 4;
	private static final int LEGS = 7;
	private static final int HANDS = 9;
	private static final int FEET = 10;
	private static final int RING = 12;

	private static SwitchSet.Piece piece(int id, int slot, int magic, double magicDamage, int ranged, int rangedStrength)
	{
		SwitchSet.Piece p = new SwitchSet.Piece();
		p.itemId = id;
		p.slot = slot;
		p.magic = magic;
		p.magicDamage = magicDamage;
		p.ranged = ranged;
		p.rangedStrength = rangedStrength;
		return p;
	}

	// ranged set
	private static final SwitchSet.Piece TBOW = twoHanded(piece(20997, SwitchSet.WEAPON, 0, 0, 70, 20));
	private static final SwitchSet.Piece MASORI_MASK = piece(27235, HEAD, -1, 0, 12, 2);
	private static final SwitchSet.Piece MASORI_BODY = piece(27238, BODY, -4, 0, 43, 4);
	private static final SwitchSet.Piece MASORI_CHAPS = piece(27241, LEGS, -2, 0, 27, 2);
	private static final SwitchSet.Piece RUPTURE = piece(33639, NECK, 0, 0, 15, 5);
	private static final SwitchSet.Piece ZARYTE_VAMBS = piece(26235, HANDS, 0, 0, 18, 2);
	private static final SwitchSet.Piece QUIVER = piece(28902, CAPE, 0, 0, 18, 3);
	private static final SwitchSet.Piece TREADS = piece(31097, FEET, 6, 0, 15, 2);
	private static final SwitchSet.Piece LIGHTBEARER = piece(25975, RING, 0, 0, 0, 0);
	private static final SwitchSet.Piece ZCB = piece(26374, SwitchSet.WEAPON, 0, 0, 110, 0);
	private static final SwitchSet.Piece BUCKLER = piece(21000, SwitchSet.SHIELD, 0, 0, 18, 0);
	private static final SwitchSet.Piece BOLTS = piece(21940, SwitchSet.AMMO, 0, 0, 0, 122);
	// magic set
	private static final SwitchSet.Piece SHADOW = twoHanded(piece(27275, SwitchSet.WEAPON, 35, 0, 0, 0));
	private static final SwitchSet.Piece KODAI = piece(21006, SwitchSet.WEAPON, 28, 15, 0, 0);
	private static final SwitchSet.Piece ANCESTRAL_HAT = piece(21018, HEAD, 8, 3, -2, 0);
	private static final SwitchSet.Piece ANCESTRAL_TOP = piece(21021, BODY, 35, 3, -8, 0);
	private static final SwitchSet.Piece ANCESTRAL_BOTTOM = piece(21024, LEGS, 26, 3, -7, 0);
	private static final SwitchSet.Piece OCCULT = piece(19720, NECK, 12, 5, 0, 0);
	private static final SwitchSet.Piece CONFLICTION = piece(31106, HANDS, 20, 7, 0, 0);
	private static final SwitchSet.Piece ZAMORAK_CAPE = piece(21780, CAPE, 15, 2, 0, 0);
	// not gear
	private static final SwitchSet.Piece MAX_CAPE = piece(13280, CAPE, 0, 0, 0, 0);

	private static SwitchSet.Piece twoHanded(SwitchSet.Piece p)
	{
		p.twoHanded = true;
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

	@Test
	public void theWholeMagicSwitchIsListedInInventoryOrder()
	{
		// the player just put the Shadow on; everything else is still the ranged set
		SwitchSet.Piece[] worn = worn(SHADOW, MASORI_MASK, MASORI_BODY, MASORI_CHAPS, RUPTURE, ZARYTE_VAMBS, QUIVER, TREADS, LIGHTBEARER, BOLTS);
		List<SwitchSet.Piece> inventory = Arrays.asList(TBOW, ANCESTRAL_TOP, OCCULT, KODAI, ANCESTRAL_HAT, ANCESTRAL_BOTTOM,
			CONFLICTION, ZAMORAK_CAPE, ZCB, MAX_CAPE, BUCKLER);
		assertEquals(Arrays.asList(21021, 19720, 21018, 21024, 31106, 21780), SwitchSet.missing(StyleCalc.MAGIC, null, worn, inventory));
	}

	@Test
	public void aPieceDisappearsWhenItIsPutOn()
	{
		// five on, the gloves and the cape forgotten
		SwitchSet.Piece[] worn = worn(SHADOW, ANCESTRAL_HAT, ANCESTRAL_TOP, ANCESTRAL_BOTTOM, OCCULT, ZARYTE_VAMBS, QUIVER, TREADS, LIGHTBEARER, BOLTS);
		List<SwitchSet.Piece> inventory = Arrays.asList(TBOW, MASORI_BODY, RUPTURE, KODAI, MASORI_MASK, MASORI_CHAPS,
			CONFLICTION, ZAMORAK_CAPE, ZCB, MAX_CAPE, BUCKLER);
		assertEquals(Arrays.asList(31106, 21780), SwitchSet.missing(StyleCalc.MAGIC, null, worn, inventory));
	}

	@Test
	public void aCompleteSwitchLeavesNothing()
	{
		SwitchSet.Piece[] worn = worn(SHADOW, ANCESTRAL_HAT, ANCESTRAL_TOP, ANCESTRAL_BOTTOM, OCCULT, CONFLICTION, ZAMORAK_CAPE, TREADS, LIGHTBEARER, BOLTS);
		List<SwitchSet.Piece> inventory = Arrays.asList(TBOW, MASORI_BODY, RUPTURE, KODAI, MASORI_MASK, MASORI_CHAPS,
			ZARYTE_VAMBS, QUIVER, ZCB, MAX_CAPE, BUCKLER);
		assertTrue(SwitchSet.missing(StyleCalc.MAGIC, null, worn, inventory).isEmpty());
	}

	@Test
	public void backToTheBowTheRangedPiecesAreTheOnesMissing()
	{
		SwitchSet.Piece[] worn = worn(TBOW, ANCESTRAL_HAT, ANCESTRAL_TOP, ANCESTRAL_BOTTOM, OCCULT, CONFLICTION, ZAMORAK_CAPE, TREADS, LIGHTBEARER, BOLTS);
		List<SwitchSet.Piece> inventory = Arrays.asList(SHADOW, MASORI_BODY, RUPTURE, KODAI, MASORI_MASK, MASORI_CHAPS,
			ZARYTE_VAMBS, QUIVER, ZCB, MAX_CAPE, BUCKLER);
		// the buckler is not asked for: the bow takes both hands. Neither are the other weapons.
		assertEquals(Arrays.asList(27238, 33639, 27235, 27241, 26235, 28902), SwitchSet.missing(StyleCalc.RANGED, null, worn, inventory));
	}

	@Test
	public void aShieldIsAskedForWithAOneHandedWeapon()
	{
		SwitchSet.Piece[] worn = worn(ZCB, MASORI_MASK, MASORI_BODY, MASORI_CHAPS, RUPTURE, ZARYTE_VAMBS, QUIVER, TREADS, LIGHTBEARER, BOLTS);
		List<SwitchSet.Piece> inventory = Arrays.asList(SHADOW, ANCESTRAL_TOP, BUCKLER, TBOW);
		assertEquals(Arrays.asList(21000), SwitchSet.missing(StyleCalc.RANGED, null, worn, inventory));
	}

	@Test
	public void afterTheSwitchTheSetHasThePiecesPutOnAndTheWornArrayIsUntouched()
	{
		// ranged armour on, the Shadow in hand, the mage pieces still in the inventory
		SwitchSet.Piece[] worn = worn(SHADOW, MASORI_MASK, MASORI_BODY, MASORI_CHAPS, RUPTURE, ZARYTE_VAMBS, QUIVER, TREADS, LIGHTBEARER, BOLTS);
		List<SwitchSet.Piece> inventory = Arrays.asList(ANCESTRAL_HAT, ANCESTRAL_TOP, ANCESTRAL_BOTTOM, OCCULT);
		SwitchSet.Piece[] done = SwitchSet.after(StyleCalc.MAGIC, null, worn, inventory);
		assertEquals(ANCESTRAL_HAT, done[HEAD]);
		assertEquals(ANCESTRAL_TOP, done[BODY]);
		assertEquals(ANCESTRAL_BOTTOM, done[LEGS]);
		assertEquals(OCCULT, done[NECK]);
		assertEquals(SHADOW, done[SwitchSet.WEAPON]);
		assertEquals(MASORI_MASK, worn[HEAD]);
		// the same pieces the reminder lists
		assertEquals(4, SwitchSet.missing(StyleCalc.MAGIC, null, worn, inventory).size());
	}

	@Test
	public void oneClassDoesNotAskForAnothersPieces()
	{
		// holding the bow with the ranged set on: the magic pieces in the inventory are left alone
		SwitchSet.Piece[] worn = worn(TBOW, MASORI_MASK, MASORI_BODY, MASORI_CHAPS, RUPTURE, ZARYTE_VAMBS, QUIVER, TREADS, LIGHTBEARER, BOLTS);
		List<SwitchSet.Piece> inventory = new ArrayList<>(Arrays.asList(SHADOW, ANCESTRAL_TOP, OCCULT, ANCESTRAL_HAT,
			ANCESTRAL_BOTTOM, CONFLICTION, ZAMORAK_CAPE, MAX_CAPE));
		assertTrue(SwitchSet.missing(StyleCalc.RANGED, null, worn, inventory).isEmpty());
	}

	@Test
	public void virtusBeatsAncestralOnlyWithItsAncientBonus()
	{
		SwitchSet.Piece[] worn = worn(KODAI, ANCESTRAL_HAT, ANCESTRAL_TOP, ANCESTRAL_BOTTOM, OCCULT, ZAMORAK_CAPE, TREADS, LIGHTBEARER);
		// same magic attack as Ancestral, 2% magic damage: left alone
		SwitchSet.Piece plain = piece(26243, BODY, 35, 2, 0, 0);
		assertTrue(SwitchSet.missing(StyleCalc.MAGIC, null, worn, Arrays.asList(plain)).isEmpty());
		// on Ancient Magicks the plugin adds 3% to each Virtus piece: now it is the better top
		SwitchSet.Piece onAncients = piece(26243, BODY, 35, 2 + SetEffects.VIRTUS_ANCIENT_BONUS, 0, 0);
		assertEquals(Arrays.asList(26243), SwitchSet.missing(StyleCalc.MAGIC, null, worn, Arrays.asList(onAncients)));
	}

	@Test
	public void aPieceWorseThanAnEmptySlotIsTakenOff()
	{
		// melee gloves with a staff: -16 magic attack, and no other gloves carried
		SwitchSet.Piece ferocious = piece(22981, HANDS, -16, 0, -16, 0);
		SwitchSet.Piece[] worn = worn(SHADOW, ANCESTRAL_HAT, ferocious, BOLTS);
		assertEquals(Arrays.asList(22981), SwitchSet.takeOff(StyleCalc.MAGIC, null, worn, Arrays.asList(TBOW, MASORI_BODY)));
		// gloves that add nothing either way stay on (Zaryte vambraces with a staff)
		assertTrue(SwitchSet.takeOff(StyleCalc.MAGIC, null, worn(SHADOW, ZARYTE_VAMBS), Arrays.asList(TBOW)).isEmpty());
		// with better gloves carried, those are asked for instead
		assertTrue(SwitchSet.takeOff(StyleCalc.MAGIC, null, worn, Arrays.asList(CONFLICTION)).isEmpty());
		assertEquals(Arrays.asList(31106), SwitchSet.missing(StyleCalc.MAGIC, null, worn, Arrays.asList(CONFLICTION)));
		// a piece kept for its effect is never taken off, and neither is the weapon
		ferocious.keep = true;
		assertTrue(SwitchSet.takeOff(StyleCalc.MAGIC, null, worn, Arrays.asList(TBOW)).isEmpty());
		assertTrue(SwitchSet.takeOff(StyleCalc.MAGIC, null, worn(TBOW), Arrays.asList(SHADOW)).isEmpty());
	}

	@Test
	public void withoutAClassNothingIsAsked()
	{
		assertTrue(SwitchSet.missing(null, null, worn(TBOW), Arrays.asList(ANCESTRAL_TOP)).isEmpty());
	}
}
