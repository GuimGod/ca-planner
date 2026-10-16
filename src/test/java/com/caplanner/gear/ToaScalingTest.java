/*
 * Copyright (c) 2026, GuimGod
 * All rights reserved. Licensed under the BSD 2-Clause License (see LICENSE).
 */
package com.caplanner.gear;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
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
 * The Tombs of Amascut, against the OSRS Wiki calculator's own code (tools/gen_toa.py): the hitpoints of every monster
 * of the raid for every raid level, path level and party (toa.tsv), and whole sets of gear against the raid's bosses
 * at several raid levels (toa_sets.tsv), with the raid's rules: Tumeken's shadow four times, Osmumten's fang's double
 * roll the raid's way, the Keris partisan of the sun below a quarter of the hitpoints, the defence roll by the raid level
 * but for Kephri's overlords.
 */
public class ToaScalingTest
{
	@Test
	public void sameHitpointsAsTheCalculator() throws IOException
	{
		StyleData data = new StyleData();
		int rows = 0;
		List<String> off = new ArrayList<>();
		try (BufferedReader in = new BufferedReader(new InputStreamReader(
			ToaScalingTest.class.getResourceAsStream("toa.tsv"), StandardCharsets.UTF_8)))
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
				int path = Integer.parseInt(c[3]);
				ToaScaling.Raid raid = new ToaScaling.Raid(Integer.parseInt(c[2]), Integer.parseInt(c[4]), path, path, path, path);
				int got = ToaScaling.hitpoints(m, m.hitpoints, raid);
				if (got != Integer.parseInt(c[6]))
				{
					off.add(c[0] + " " + raid + ": " + got + ", calculator " + c[6]);
				}
				rows++;
			}
		}
		assertTrue(off.size() + " off: " + off, off.isEmpty());
		assertTrue("the file has its cases", rows > 10000);
	}

	@Test
	public void figuresAreTheCalculators() throws IOException
	{
		assertTrue(SetCalcTest.figuresOf("toa_sets.tsv").size() > 1000);
	}

	/** The cases of one monster at one raid level, by set: the plugin's figure (got) and the calculator's (expected). */
	private static Map<String, SetCalcTest.Case> at(int npc, int raidLevel) throws IOException
	{
		Map<String, SetCalcTest.Case> cases = new HashMap<>();
		for (SetCalcTest.Case k : SetCalcTest.cases("toa_sets.tsv"))
		{
			if (k.npc == npc && k.toaLevel == raidLevel && k.party <= 1)
			{
				cases.put(k.set, k);
			}
		}
		return cases;
	}

	/** {@code better} deals more than {@code worse} by the calculator and by the plugin. */
	private static void first(Map<String, SetCalcTest.Case> cases, String better, String worse)
	{
		SetCalcTest.Case a = cases.get(better);
		SetCalcTest.Case b = cases.get(worse);
		assertNotNull(better, a);
		assertNotNull(worse, b);
		assertTrue("calculator: " + better + " over " + worse + " (" + a.expected + ", " + b.expected + ")", a.expected > b.expected);
		assertTrue("plugin: " + better + " over " + worse + " (" + a.got + ", " + b.got + ")", a.got > b.got);
	}

	/**
	 * The release's review (2026-10-15) with the plugin's own code: Ba-Ba at raid level 300 took the Scythe over the Fang
	 * (+19.7%) and the Scythe over the Shadow; the Obelisk and Akkha's Shadow took the Scythe over Tumeken's shadow. With
	 * the raid's rules the calculator's order comes back: Fang 8.86 over Scythe 7.59 on Ba-Ba, the Shadow first on the
	 * Obelisk at every raid level and on Akkha's Shadow from 150 (smelling salts, the review's gear).
	 */
	@Test
	public void theReviewCasesComeOutAsTheCalculator() throws IOException
	{
		Map<String, SetCalcTest.Case> baba = at(11778, 300);
		first(baba, "rev-fang", "rev-scythe");
		assertEquals(8.861, baba.get("rev-fang").got / 0.6, 0.001);
		assertEquals(7.588, baba.get("rev-scythe").got / 0.6, 0.001);
		for (int level : new int[]{0, 150, 300, 400, 500})
		{
			Map<String, SetCalcTest.Case> obelisk = at(11751, level);
			first(obelisk, "rev-shadow", "rev-scythe");
			first(obelisk, "rev-shadow", "rev-fang");
		}
		for (int level : new int[]{150, 300, 400, 500})
		{
			Map<String, SetCalcTest.Case> shadow = at(11797, level);
			first(shadow, "rev-shadow", "rev-scythe");
			first(shadow, "rev-shadow", "rev-fang");
		}
		// Akkha at 300: Shadow 13.20 over Fang 9.20 over Scythe 8.95 (the plugin took the Scythe and asked Fang to Scythe)
		Map<String, SetCalcTest.Case> akkha = at(11789, 300);
		first(akkha, "rev-shadow", "rev-fang");
		first(akkha, "rev-fang", "rev-scythe");
	}

	/** Tumeken's shadow multiplies its bonuses by four in the raid: on Zebak at 400 it deals about a quarter more than with three. */
	@Test
	public void theShadowIsFourTimesInTheRaid() throws IOException
	{
		StyleData data = new StyleData();
		SetCalc.Loadout shadow = new SetCalc.Loadout();
		shadow.weaponName = "Tumeken's shadow";
		shadow.category = "Powered Staff";
		shadow.speed = 5;
		shadow.magic = 35 + 103;
		shadow.magicDamage = 23;
		SetCalc.Player p = new SetCalc.Player();
		p.magic = 125;
		p.prayerMagic = 125;
		p.prayerMagicDamage = 4;
		ToaScaling.Raid raid = new ToaScaling.Raid(400, 1, 0, 0, 0, 0);
		StyleData.Monster zebak = data.monster(11730);
		SetCalc.Target inRaid = zebak.target(MonsterLevels.scaled(zebak, null, null, raid));
		inRaid.toaInvocation = ToaScaling.invocation(zebak, raid);
		SetCalc.Target asOutside = zebak.target(MonsterLevels.scaled(zebak, null, null, raid));
		asOutside.toaInvocation = inRaid.toaInvocation;
		asOutside.toa = false;
		double four = SetCalc.perTick(shadow, p, inRaid, false);
		double three = SetCalc.perTick(shadow, p, asOutside, false);
		assertTrue(four + " over " + three, four > three * 1.15);
		// every boss of the raid is the raid's
		for (int id : new int[]{11789, 11797, 11778, 11719, 11721, 11724, 11730, 11751, 11753, 11755, 11756, 11758, 11761, 11762})
		{
			assertTrue(id + " is of the Tombs", data.monster(id).toa);
		}
		assertFalse(data.monster(415).toa);
	}

	/** The raid level scales the defence roll, after the drains, for every monster of the raid but Kephri's overlords. */
	@Test
	public void theDefenceRollGrowsWithTheRaidLevelButNotTheOverlords() throws IOException
	{
		StyleData data = new StyleData();
		ToaScaling.Raid raid = new ToaScaling.Raid(300, 4, 3, 3, 3, 3);
		assertEquals(300, ToaScaling.invocation(data.monster(11789), raid));
		assertEquals(300, ToaScaling.invocation(data.monster(11762), raid));
		for (int overlord = 11724; overlord <= 11726; overlord++)
		{
			assertEquals(0, ToaScaling.invocation(data.monster(overlord), raid));
		}
		assertEquals(0, ToaScaling.invocation(data.monster(11789), null));
		assertEquals(0, ToaScaling.invocation(data.monster(415), raid));
		// (250 + 300) / 250 of the roll, rounded down
		assertEquals(22000, ToaScaling.defenceRoll(10000, 300));
		assertEquals(10000, ToaScaling.defenceRoll(10000, 0));
		assertEquals(10038, ToaScaling.defenceRoll(9999, 1));
	}

	/** Osmumten's fang in the raid: 1 - (1 - p)^2 of the ordinary chance, not the double roll used elsewhere. */
	@Test
	public void theFangRollsTwiceTheRaidsWay()
	{
		double p = StyleCalc.chance(20000, 30000);
		assertEquals(1 - (1 - p) * (1 - p), StyleCalc.fangChanceInTheTombs(20000, 30000), 1e-12);
		assertTrue(StyleCalc.fangChanceInTheTombs(20000, 30000) > StyleCalc.fangChance(20000, 30000));
	}

	/**
	 * The Keris partisan of the sun is a quarter more accurate in the raid below a quarter of the monster's hitpoints:
	 * the cases at full health, at a quarter and just below it (toa_sets.tsv) agree with the calculator, and below it
	 * the keris deals more.
	 */
	@Test
	public void theKerisOfTheSunBelowAQuarter() throws IOException
	{
		List<SetCalcTest.Case> keris = new ArrayList<>();
		for (SetCalcTest.Case k : SetCalcTest.cases("toa_sets.tsv"))
		{
			if (k.set.equals("toa-keris-sun") && k.npc == 11762 && k.toaLevel == 400 && k.party == 3)
			{
				keris.add(k);
			}
		}
		assertEquals(4, keris.size());
		// full, a quarter (not below), a quarter less one, a fifth
		assertEquals(keris.get(0).got, keris.get(1).got, 1e-9);
		assertTrue(keris.get(2).got > keris.get(1).got * 1.05);
		assertTrue(keris.get(2).expected > keris.get(1).expected * 1.05);
		SetCalc.Target t = new SetCalc.Target();
		t.toa = true;
		t.hitpoints = 2000;
		t.hitpointsNow = 499;
		assertTrue(SetCalc.sunKerisRule(t));
		t.hitpointsNow = 500;
		assertFalse(SetCalc.sunKerisRule(t));
		t.hitpointsNow = 0;
		assertFalse(SetCalc.sunKerisRule(t));
		t.hitpointsNow = 10;
		t.toa = false;
		assertFalse(SetCalc.sunKerisRule(t));
	}

	/** The raid read from the game is kept while it does not change. */
	@Test
	public void sameRaidIsEqual()
	{
		assertEquals(new ToaScaling.Raid(300, 4, 1, 2, 3, 4), new ToaScaling.Raid(300, 4, 1, 2, 3, 4));
		assertFalse(new ToaScaling.Raid(300, 4, 1, 2, 3, 4).equals(new ToaScaling.Raid(305, 4, 1, 2, 3, 4)));
		assertEquals(3, new ToaScaling.Raid(0, 1, 1, 2, 3, 4).pathLevel('H'));
		assertEquals(4, new ToaScaling.Raid(0, 1, 1, 2, 3, 4).pathLevel('A'));
		assertEquals(0, new ToaScaling.Raid(0, 1, 1, 2, 3, 4).pathLevel((char) 0));
	}

	/**
	 * The Wardens of the second phase: every attack lands, ranged and magic deal 15% to 60% of the max by what is left
	 * of the attack roll over a third of the defence roll (cases against the calculator in toa_sets.tsv, the ids that
	 * pray against ranged too); the cores count as the Warden "Core-ejected"; the third phase enraged has Defence 180.
	 */
	@Test
	public void theWardensSecondPhaseCoresAndEnrage() throws IOException
	{
		StyleData data = new StyleData();
		for (int id : new int[]{11753, 11754, 11756, 11757})
		{
			assertTrue(id + " is of the second phase", data.monster(id).wardenP2);
		}
		assertFalse(data.monster(11762).wardenP2);
		assertTrue(data.monster(11770).toaCore && data.monster(11770).alwaysMaxMelee && data.monster(11770).toa);
		assertTrue(data.monster(11771).toaCore && data.monster(11771).alwaysMaxMelee);
		assertEquals(4500, ToaScaling.hitpoints(data.monster(11770), data.monster(11770).hitpoints, new ToaScaling.Raid(0, 1, 0, 0, 0, 0)));
		// 15% to 35% with nothing left over; 40% to 60% from 42,000 on; in between by a straight line, rounded down
		assertEquals(15, SetCalc.wardenP2Range(100, 0, 30000)[0]);
		assertEquals(35, SetCalc.wardenP2Range(100, 0, 30000)[1]);
		assertEquals(40, SetCalc.wardenP2Range(100, 100000, 30)[0]);
		assertEquals(60, SetCalc.wardenP2Range(100, 100000, 30)[1]);
		assertEquals(27, SetCalc.wardenP2Range(100, 31000, 30000)[0]);
		// the third phase enraged: Defence 180 instead of 150, for its two ids only
		for (int id : new int[]{11761, 11762})
		{
			StyleData.Monster warden = data.monster(id);
			assertEquals(180, warden.enragedDefence);
			assertEquals(180, MonsterLevels.wardenEnraged(warden, CoxScaling.unscaled(warden), true).defence);
			assertEquals(150, MonsterLevels.wardenEnraged(warden, CoxScaling.unscaled(warden), false).defence);
		}
		assertEquals(0, data.monster(11763).enragedDefence);
		assertEquals(data.monster(415).level, MonsterLevels.wardenEnraged(data.monster(415), CoxScaling.unscaled(data.monster(415)), true).defence);
		// enraged, the Warden takes less from the Shadow, by the calculator and by the plugin
		SetCalcTest.Case calm = null;
		SetCalcTest.Case enraged = null;
		for (SetCalcTest.Case k : SetCalcTest.cases("toa_sets.tsv"))
		{
			if (k.set.equals("toa-scythe") && k.npc == 11762 && k.toaLevel == 300 && k.party == 1)
			{
				if (calm == null)
				{
					calm = k;
				}
				else
				{
					enraged = k;
				}
			}
		}
		assertNotNull(enraged);
		assertTrue(calm.expected > enraged.expected);
		assertTrue(calm.got > enraged.got);
	}
}
