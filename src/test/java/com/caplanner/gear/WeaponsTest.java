/*
 * Copyright (c) 2026, GuimGod
 * All rights reserved. Licensed under the BSD 2-Clause License (see LICENSE).
 */
package com.caplanner.gear;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/** What the damage sums read from a weapon's or a monster's name alone. */
public class WeaponsTest
{
	@Test
	public void setAndWeaponNames()
	{
		assertEquals(1, Weapons.inquisitorWeight("Inquisitor's great helm"));
		assertEquals(2, Weapons.inquisitorWeight("Inquisitor's hauberk"));
		assertEquals(0, Weapons.inquisitorWeight("Inquisitor's mace"));
		assertTrue(Weapons.isCorpbane("Zamorakian spear"));
		assertTrue(Weapons.isCorpbane("Osmumten's fang (or)"));
		assertFalse(Weapons.isCorpbane("Blue moon spear"));
		assertFalse(Weapons.isCorpbane("Scythe of vitur"));
	}

	@Test
	public void ammunitionCountsOnlyWithAWeaponThatShootsIt()
	{
		assertEquals("bolts", Weapons.ammoKind("Crossbow", "Zaryte crossbow"));
		assertEquals("bolt rack", Weapons.ammoKind("Crossbow", "Karil's crossbow 100"));
		assertTrue(Weapons.isShotAmmo("Bolt rack"));
		assertEquals("arrow", Weapons.ammoKind("Bow", "Twisted bow"));
		assertNull(Weapons.ammoKind("Bow", "Webweaver bow"));
		assertNull(Weapons.ammoKind("Bow", "Bow of faerdhinen (c)"));
		assertTrue(Weapons.shootsNoAmmo("Bow", "Webweaver bow"));
		assertFalse(Weapons.shootsNoAmmo("Bow", "Venator bow"));
		assertEquals("arrow", Weapons.ammoKind("Bow", "Venator bow"));
		assertFalse(Weapons.shootsNoAmmo("Bow", "Twisted bow"));
		assertFalse(Weapons.shootsNoAmmo("Crossbow", "Zaryte crossbow"));
		// dragon arrows left in the slot give nothing to a blowpipe
		assertTrue(Weapons.throwsItself("Thrown") && Weapons.isShotAmmo("Dragon arrow"));
		assertFalse(Weapons.throwsItself("Bow"));
		assertFalse(Weapons.isShotAmmo("Rada's blessing 4"));
	}
}
