/*
 * Copyright (c) 2026, GuimGod
 * All rights reserved. Licensed under the BSD 2-Clause License (see LICENSE).
 */
package com.caplanner.gear;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;

/**
 * The Theatre of Blood, against the OSRS Wiki calculator's own code (tools/gen_tob.py): the hitpoints of every monster
 * of the raid for a party of 1 to 5 (tob.tsv), and whole sets of gear against the raid's bosses by the ids the game gives
 * them in combat (tob_sets.tsv): Sotetseg in normal and Hard Mode no longer reads the Entry Mode row.
 */
public class TobScalingTest
{
	@Test
	public void sameHitpointsAsTheCalculator() throws IOException
	{
		StyleData data = new StyleData();
		int rows = 0;
		List<String> off = new ArrayList<>();
		try (BufferedReader in = new BufferedReader(new InputStreamReader(
			TobScalingTest.class.getResourceAsStream("tob.tsv"), StandardCharsets.UTF_8)))
		{
			String line;
			while ((line = in.readLine()) != null)
			{
				if (line.startsWith("#") || line.trim().isEmpty())
				{
					continue;
				}
				String[] c = line.split("\t", -1);
				StyleData.Monster m = data.monster(Integer.parseInt(c[0]));
				assertNotNull("the table has " + c[0], m);
				int got = TobScaling.hitpoints(m, m.hitpoints, new TobScaling.Raid(Integer.parseInt(c[2])));
				if (got != Integer.parseInt(c[4]))
				{
					off.add(c[0] + " " + c[1] + ", " + c[2] + " players: " + got + ", calculator " + c[4]);
				}
				rows++;
			}
		}
		assertTrue(off.size() + " off: " + off, off.isEmpty());
		assertTrue("the file has its cases", rows > 300);
	}

	@Test
	public void figuresAreTheCalculators() throws IOException
	{
		assertTrue(SetCalcTest.figuresOf("tob_sets.tsv").size() > 500);
	}

	/**
	 * On Xarpus and Verzik's second phase, in every mode, the Scythe of vitur attacks once every 16/3 ticks, not every 5
	 * (OSRS Wiki, Theatre of Blood/Strategies, Xarpus: "This uses 15/16 available ticks to damage the boss, or
	 * effectively a 5.33 tick attack speed"): each of its variants deals 15/16 of what it dealt there
	 * (the calculator's figure times 5 / (16/3) in tob_sets.tsv, tools/raid_cases.py), and the same as before everywhere
	 * else. Another 5-tick weapon is not touched.
	 */
	@Test
	public void theScytheIsSlowerOnXarpusAndVerziksSecondPhase() throws IOException
	{
		StyleData data = new StyleData();
		SetCalc.Player p = new SetCalc.Player();
		p.attack = 118;
		p.strength = 118;
		p.prayerAttack = 120;
		p.prayerStrength = 123;
		int[] walked = {10766, 10767, 10768, 10769, 8338, 8339, 8340, 8341, 10770, 10771, 10772, 10773,
			10833, 10834, 8372, 8373, 10850, 10851};
		for (String weapon : new String[]{"Scythe of vitur", "Holy scythe of vitur", "Sanguine scythe of vitur"})
		{
			SetCalc.Loadout l = scythe(data, weapon);
			for (int id : walked)
			{
				// the ids the table does not list find Xarpus by his name, as in game
				SetCalc.Target t = data.monster(id, id < 10833 && id != 8372 && id != 8373 ? "Xarpus" : "Verzik Vitur").target();
				assertEquals(id + "", 16.0 / 3, t.scytheSpeed, 0);
				double slower = SetCalc.perTick(l, p, t, false);
				t.scytheSpeed = 0;
				double before = SetCalc.perTick(l, p, t, false);
				assertTrue(weapon + " on " + id, before > 0);
				assertEquals(weapon + " on " + id, before * 15 / 16, slower, 1e-12);
			}
		}
		// Verzik's first and third phases, Sotetseg, the Maiden, the Nylocas Vasilias, a boss outside the raid: the
		// weapon's own speed (and every case of the calculator's files stays its figure: figuresAreTheCalculators)
		for (int id : new int[]{8369, 8370, 10830, 10847, 8374, 8375, 10835, 10852, 8388, 10868, 8360, 8355, 2215})
		{
			assertEquals(id + "", 0, data.monster(id).target().scytheSpeed, 0);
			assertEquals(id + "", 0, Weapons.scytheSpeed(id), 0);
		}
		// another weapon of 5 ticks keeps its speed on Xarpus
		SetCalc.Loadout axe = scythe(data, "Soulreaper axe");
		SetCalc.Target xarpus = data.monster(8340).target();
		double axeNow = SetCalc.perTick(axe, p, xarpus, false);
		xarpus.scytheSpeed = 0;
		assertEquals(SetCalc.perTick(axe, p, xarpus, false), axeNow, 0);
		assertTrue(axeNow > 0);
		// the calculator's case of the Scythe on Xarpus (tob_sets.tsv) is the plugin's, at 15/16 of the calculator's own
		int cases = 0;
		for (SetCalcTest.Case k : SetCalcTest.cases("tob_sets.tsv"))
		{
			if ("tob-scythe".equals(k.set) && Weapons.scytheSpeed(k.npc) > 0)
			{
				assertEquals(k.npc + "", k.expected, k.got, 0.001 * k.expected);
				cases++;
			}
		}
		assertTrue("cases", cases >= 18);
	}

	/** The Scythe of vitur's bonuses, as in tob_sets.tsv (the set tob-scythe), under the name given. */
	private static SetCalc.Loadout scythe(StyleData data, String name)
	{
		SetCalc.Loadout l = new SetCalc.Loadout();
		l.weaponName = name;
		l.category = "Scythe";
		l.speed = 5;
		l.styles = data.styles(22325);
		l.stab = 117;
		l.slash = 210;
		l.crush = 77;
		l.magic = -61;
		l.ranged = -63;
		l.strength = 138;
		return l;
	}

	/**
	 * Every id the game gives a boss of the Theatre in combat finds the row of its own mode: Sotetseg in combat (8388,
	 * Hard Mode 10868) is no longer the Entry Mode one found by his name.
	 */
	@Test
	public void theCombatIdsFindTheirOwnMode() throws IOException
	{
		StyleData data = new StyleData();
		assertEquals(200, data.monster(8388).level);
		assertEquals(200, data.monster(10868).level);
		assertEquals(150, data.monster(10865).level);
		assertEquals('b', data.monster(8388).tobScaling);
		assertEquals('e', data.monster(10865).tobScaling);
		// the Maiden at 70% in Entry Mode and Hard Mode
		assertEquals(80, data.monster(10815).level);
		assertEquals(200, data.monster(10823).level);
		// the Nylocas Vasilias in each form, and Verzik's first phase
		for (int id : new int[]{8355, 8356, 8357, 10808, 10809, 10810})
		{
			assertEquals("Nylocas Vasilias", data.monster(id).name);
			assertEquals('b', data.monster(id).tobScaling);
		}
		for (int id : new int[]{10787, 10788, 10789})
		{
			assertEquals('e', data.monster(id).tobScaling);
		}
		for (int id : new int[]{8370, 8371, 10831, 10832, 10848, 10849})
		{
			assertTrue(id + " is Verzik's first phase", data.monster(id).verzikP1);
		}
		// the wave Nylocas once they fight: the big ones are not the small ones any more
		assertEquals(20, data.monster(8351).level);
		assertEquals(1, data.monster(8348).level);
		// the id itself comes before the name
		assertSame(data.monster(8388), data.monster(8388, "Sotetseg"));
	}

	/**
	 * Sotetseg in a normal raid (the review of 2026-10-15): read as the Entry Mode row, the plugin called the Scythe over
	 * Tumeken's shadow; with the row of his own mode the Shadow comes first, as in the calculator.
	 */
	@Test
	public void sotetsegInANormalRaidTakesTheShadow() throws IOException
	{
		Map<String, SetCalcTest.Case> normal = new HashMap<>();
		Map<String, SetCalcTest.Case> entry = new HashMap<>();
		for (SetCalcTest.Case k : SetCalcTest.cases("tob_sets.tsv"))
		{
			if (k.party == 5 && k.npc == 8388)
			{
				normal.put(k.set, k);
			}
			else if (k.party == 5 && k.npc == 10865)
			{
				entry.put(k.set, k);
			}
		}
		SetCalcTest.Case shadow = normal.get("tob-shadow");
		SetCalcTest.Case scythe = normal.get("tob-scythe");
		assertTrue(shadow.expected > scythe.expected);
		assertTrue(shadow.got > scythe.got);
		// the Entry Mode row ranks them the other way: what the plugin did before
		assertTrue(entry.get("tob-scythe").got > entry.get("tob-shadow").got);
	}

	/** Verzik's first phase limits each melee hit to a random 0 to 10 and every other one to 0 to 3. */
	@Test
	public void verziksFirstPhaseLimitsEachHit() throws IOException
	{
		Map<String, SetCalcTest.Case> p1 = new HashMap<>();
		for (SetCalcTest.Case k : SetCalcTest.cases("tob_sets.tsv"))
		{
			if (k.npc == 8370 && k.party == 5)
			{
				p1.put(k.set, k);
			}
		}
		// the Scythe's three hits each up to 10 over the Elder maul's one
		assertTrue(p1.get("tob-scythe").got > 2 * p1.get("tob-elder-maul").got);
		assertTrue(p1.get("tob-tbow").got < 3.0 / 5);
		assertEquals(1.5, SetCalc.limitedMean(3, 3, false), 1e-9);
		assertEquals(5, SetCalc.limitedMean(100, 10, false), 1e-9);
		assertEquals(0.5, SetCalc.limitedMean(1, 1, false), 1e-9);
	}

	/** Only the hitpoints scale: a party of 3 or fewer gets 75%, 4 gets 87.5%; Entry Mode its own table. */
	@Test
	public void namedCases() throws IOException
	{
		StyleData data = new StyleData();
		StyleData.Monster sotetseg = data.monster(8388);
		assertEquals(3000, TobScaling.hitpoints(sotetseg, 4000, new TobScaling.Raid(1)));
		assertEquals(3000, TobScaling.hitpoints(sotetseg, 4000, new TobScaling.Raid(3)));
		assertEquals(3500, TobScaling.hitpoints(sotetseg, 4000, new TobScaling.Raid(4)));
		assertEquals(4000, TobScaling.hitpoints(sotetseg, 4000, new TobScaling.Raid(5)));
		assertEquals(4000, TobScaling.hitpoints(sotetseg, 4000, null));
		StyleData.Monster entry = data.monster(10865);
		assertEquals(560, TobScaling.hitpoints(entry, 2240, new TobScaling.Raid(1)));
		assertEquals(1064, TobScaling.hitpoints(entry, 2240, new TobScaling.Raid(2)));
		// a monster that is not of the Theatre is never scaled
		assertEquals(255, TobScaling.hitpoints(data.monster(2215), 255, new TobScaling.Raid(1)));
		// the levels themselves never change in the Theatre
		DefenceDrains.Levels l = MonsterLevels.scaled(sotetseg, null, new TobScaling.Raid(2), null);
		assertEquals(200, l.defence);
		assertEquals(3000, l.hitpoints);
		assertEquals(new TobScaling.Raid(4), new TobScaling.Raid(4));
	}

	/**
	 * Verzik's transitions and death take the phase the OSRS Wiki lists them in, and the Hard Mode wave Nylocas the
	 * normal Small and Big rows (the wiki lists their ids in those versions).
	 */
	@Test
	public void transitionsAndHardModeWavesFindTheirRows() throws IOException
	{
		StyleData data = new StyleData();
		assertEquals(200, data.monster(8373).level);
		assertEquals(150, data.monster(8375).level);
		assertEquals(200, data.monster(10851).level);
		assertEquals(150, data.monster(10853).level);
		assertEquals(120, data.monster(10834).level);
		assertEquals(120, data.monster(10836).level);
		for (int id : new int[]{10791, 10797, 10792, 10798, 10793, 10799})
		{
			assertEquals(1, data.monster(id).level);
			assertEquals(11, data.monster(id).hitpoints);
			assertEquals('b', data.monster(id).tobScaling);
		}
		for (int id : new int[]{10794, 10800, 10795, 10801, 10796, 10802})
		{
			assertEquals(20, data.monster(id).level);
			assertEquals(22, data.monster(id).hitpoints);
		}
	}
}
