/*
 * Copyright (c) 2026, GuimGod
 * All rights reserved. Licensed under the BSD 2-Clause License (see LICENSE).
 */
package com.caplanner.gear;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import org.junit.Test;

/**
 * The sets the planner adds up carry Dizana's quiver and the ammunition shot, so the engine counts the quiver's 10
 * ranged accuracy and 1 ranged strength on the arrows and bolts (SetCalc.quiverApplies; DizanaQuiverTest has the
 * calculator's figures).
 */
public class LoadoutsQuiverTest
{
	private static final int CAPE = 1;

	private static SwitchSet.Piece piece(int id, int slot, String name, int ranged, int rangedStrength)
	{
		SwitchSet.Piece p = new SwitchSet.Piece();
		p.itemId = id;
		p.slot = slot;
		p.name = name;
		p.ranged = ranged;
		p.rangedStrength = rangedStrength;
		return p;
	}

	private static SwitchSet.Piece[] set(SwitchSet.Piece weapon, SwitchSet.Piece cape, SwitchSet.Piece ammo)
	{
		SwitchSet.Piece[] pieces = new SwitchSet.Piece[SwitchSet.SLOTS];
		pieces[SwitchSet.WEAPON] = weapon;
		pieces[CAPE] = cape;
		pieces[SwitchSet.AMMO] = ammo;
		return pieces;
	}

	@Test
	public void theQuiverCountsOnTheArrowsShot() throws IOException
	{
		StyleData data = new StyleData();
		SwitchSet.Piece tbow = piece(20997, SwitchSet.WEAPON, "Twisted bow", 70, 20);
		tbow.speed = 6;
		SwitchSet.Piece cape = piece(28902, CAPE, "Dizana's max cape", 18, 3);
		SwitchSet.Piece arrows = piece(11212, SwitchSet.AMMO, "Dragon arrow", 0, 60);

		SetCalc.Loadout l = Loadouts.of(set(tbow, cape, arrows), data);
		assertTrue(l.dizanaQuiver);
		assertEquals("Dragon arrow", l.ammo);
		assertTrue(SetCalc.quiverApplies(l));

		// bolts under a bow are not shot: no ammunition, no bonus
		SwitchSet.Piece bolts = piece(21905, SwitchSet.AMMO, "Dragon bolts", 0, 122);
		SetCalc.Loadout boltsUnderBow = Loadouts.of(set(tbow, cape, bolts), data);
		assertNull(boltsUnderBow.ammo);
		assertFalse(SetCalc.quiverApplies(boltsUnderBow));

		// another cape
		SetCalc.Loadout assembler = Loadouts.of(set(tbow, piece(22109, CAPE, "Ava's assembler", 8, 2), arrows), data);
		assertFalse(assembler.dizanaQuiver);
		assertFalse(SetCalc.quiverApplies(assembler));
	}
}
