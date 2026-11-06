/*
 * Copyright (c) 2026, GuimGod
 * All rights reserved. Licensed under the BSD 2-Clause License (see LICENSE).
 */
package com.caplanner.gear;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import org.junit.Test;

/**
 * The other monsters inside the Tombs of Amascut (the baboons of Apmeken, the crocodile of Crondis, Kephri's swarms...):
 * the calculator's lists have only the bosses and count these as outside the raid; the OSRS Wiki counts where the fight
 * is. Tumeken's shadow's passive is "four within the Tombs of Amascut", the Keris partisan of amascut has its bonuses
 * "Inside ToA", and the raid level raises the defence of "all enemies within the raid", but not of Kephri's scarabs; the
 * baboons' hitpoints "are now static". The figures of rules_sets.tsv are the calculator's, outside (SetCalcTest).
 */
public class ToaRoomsTest
{
	private static SetCalcTest.Case of(String set, int npc) throws IOException
	{
		for (SetCalcTest.Case k : SetCalcTest.cases("rules_sets.tsv"))
		{
			if (k.set.equals(set) && k.npc == npc)
			{
				return k;
			}
		}
		return null;
	}

	@Test
	public void theRoomsAreInsideTheTombs() throws IOException
	{
		StyleData data = new StyleData();
		for (int id : new int[]{11697, 11705, 11709, 11713, 11715, 11723, 11727, 11729})
		{
			StyleData.Monster m = data.monster(id);
			assertTrue(id + " inside", m.toa && m.toaRoom);
			assertEquals(id + " no path", 0, m.toaPath);
		}
		// the bosses are not rooms
		assertFalse(data.monster(11789).toaRoom);
		assertFalse(data.monster(11719).toaRoom);
	}

	@Test
	public void tumekensShadowFourTimesOnABaboon() throws IOException
	{
		SetCalcTest.Case shadow = of("shadow", 11709);
		assertNotNull(shadow);
		double outside = shadow.got;
		shadow.target.toa = true;
		double inside = SetCalc.perTick(shadow.loadout, shadow.player, shadow.target, false);
		assertTrue("inside " + inside + ", outside " + outside, inside > outside * 1.05);
	}

	@Test
	public void theRaidLevelRaisesTheirDefenceButNotKephrisScarabs() throws IOException
	{
		StyleData data = new StyleData();
		ToaScaling.Raid raid = new ToaScaling.Raid(300, 3, 0, 0, 0, 0);
		assertEquals(300, ToaScaling.invocation(data.monster(11709), raid));
		assertEquals(300, ToaScaling.invocation(data.monster(11705), raid));
		assertEquals(0, ToaScaling.invocation(data.monster(11723), raid));
		assertEquals(0, ToaScaling.invocation(data.monster(11727), raid));
		// a Scythe on a Baboon Brawler: less at raid level 300 than at none
		SetCalcTest.Case scythe = of("scythe", 11709);
		assertNotNull(scythe);
		scythe.target.toa = true;
		double none = SetCalc.perTick(scythe.loadout, scythe.player, scythe.target, false);
		scythe.target.toaInvocation = 300;
		double at300 = SetCalc.perTick(scythe.loadout, scythe.player, scythe.target, false);
		assertTrue(at300 < none);
	}

	@Test
	public void theirHitpointsAreNotScaled() throws IOException
	{
		StyleData data = new StyleData();
		ToaScaling.Raid raid = new ToaScaling.Raid(500, 3, 4, 4, 4, 4);
		StyleData.Monster brawler = data.monster(11712);
		assertEquals(brawler.hitpoints, ToaScaling.hitpoints(brawler, brawler.hitpoints, raid));
		StyleData.Monster swarm = data.monster(11723);
		assertEquals(swarm.hitpoints, ToaScaling.hitpoints(swarm, swarm.hitpoints, raid));
		// a boss is scaled
		StyleData.Monster akkha = data.monster(11789);
		assertTrue(ToaScaling.hitpoints(akkha, akkha.hitpoints, raid) > akkha.hitpoints);
	}
}
