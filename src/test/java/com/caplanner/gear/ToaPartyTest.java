/*
 * Copyright (c) 2026, GuimGod
 * All rights reserved. Licensed under the BSD 2-Clause License (see LICENSE).
 */
package com.caplanner.gear;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

/**
 * The Tombs of Amascut against the OSRS Wiki DPS calculator with a party and path levels (toa_party_sets.tsv,
 * tools/gen_toa_party.py; toa_sets.tsv has them alone on path 0): raid levels 300 and 500, alone and in a trio, path
 * levels 0, 2 and 4, on the bosses, the Obelisk and the Wardens of the second phase. The party and the path change the
 * hitpoints only, which the ruby bolts, the Keris partisan of the sun and the time to kill go by.
 */
public class ToaPartyTest
{
	private static final String FILE = "toa_party_sets.tsv";

	@Test
	public void everyFigureIsTheCalculators() throws IOException
	{
		assertEquals(840, SetCalcTest.figuresOf(FILE).size());
	}

	@Test
	public void everyTimeToKillIsTheCalculators() throws IOException
	{
		List<String> off = new ArrayList<>();
		int checked = 0;
		for (SetCalcTest.Case k : SetCalcTest.cases(FILE))
		{
			double got = SetCalc.ticksToKill(k.loadout, k.player, k.target);
			if (Double.isNaN(got) || Double.isNaN(k.htk))
			{
				continue; // a set whose damage is not put as chances (the ruby bolts), the Wardens' core
			}
			double want = k.htk * k.calculatorSpeed;
			checked++;
			if (Math.abs(got - want) > 0.005 * want)
			{
				off.add(String.format("%s on %s, raid level %d, path, party %d: %.3f ticks, calculator %.3f", k.set,
					k.target.name, k.toaLevel, k.party, got, want));
			}
		}
		assertTrue(checked > 600);
		assertTrue(off.size() + " off: " + off, off.isEmpty());
	}
}
