/*
 * Copyright (c) 2026, GuimGod
 * All rights reserved. Licensed under the BSD 2-Clause License (see LICENSE).
 */
package com.caplanner.gear;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import net.runelite.client.plugins.specialcounter.SpecialWeapon;
import org.junit.Test;

/**
 * The levels special attacks leave a monster with, compared with the OSRS Wiki calculator (tools/gen_drains.py),
 * how each special attack the Special Attack Counter reports is counted, and the OSRS Wiki's rules the calculator does
 * not have (the order they landed in, the special attacks it has no field for, the floors, the fights immune to drains).
 */
public class DefenceDrainsTest
{
	/**
	 * The special attacks of the calculator's counts, added in its order (DefenceReduction.ts: the Accursed sceptre, the
	 * Elder maul, the Dragon warhammer, Arclight, Emberlight, the Tonalztics of Ralos' hits, the Seercull, the godsword,
	 * the Eye of Ayak), which is an order they can land in: then the levels are the calculator's.
	 */
	static DefenceDrains calculatorOrder(boolean accursed, int elderMaul, int dwh, int arclight, int emberlight,
		int tonalztic, int seercull, int bgs, int ayak)
	{
		DefenceDrains d = new DefenceDrains();
		if (accursed)
		{
			d.add(SpecialWeapon.ACCURSED_SCEPTRE, 1, false);
		}
		for (int i = 0; i < elderMaul; i++)
		{
			d.add(SpecialWeapon.ELDER_MAUL, 1, false);
		}
		for (int i = 0; i < dwh; i++)
		{
			d.add(SpecialWeapon.DRAGON_WARHAMMER, 1, false);
		}
		for (int i = 0; i < arclight; i++)
		{
			d.add(SpecialWeapon.ARCLIGHT, 1, false);
		}
		for (int i = 0; i < emberlight; i++)
		{
			d.add(SpecialWeapon.EMBERLIGHT, 1, false);
		}
		// two hits at most for each special attack
		for (int left = tonalztic; left > 0; left -= 2)
		{
			d.add(SpecialWeapon.TONALZTICS_OF_RALOS, Math.min(2, left), false);
		}
		if (seercull > 0)
		{
			d.add(SpecialWeapon.SEERCULL, seercull, false);
		}
		if (bgs > 0)
		{
			d.add(SpecialWeapon.BANDOS_GODSWORD, bgs, false);
		}
		if (ayak > 0)
		{
			d.add(SpecialWeapon.EYE_OF_AYAK, ayak, false);
		}
		return d;
	}

	/** Special attacks that landed, in that order: "maul" an Elder maul that hit, "bgs" a Bandos godsword's damage. */
	private static DefenceDrains inOrder(Object... specials)
	{
		DefenceDrains d = new DefenceDrains();
		for (int i = 0; i < specials.length; i++)
		{
			if ("maul".equals(specials[i]))
			{
				d.add(SpecialWeapon.ELDER_MAUL, 1, false);
			}
			else
			{
				d.add(SpecialWeapon.BANDOS_GODSWORD, (Integer) specials[++i], false);
			}
		}
		return d;
	}

	/** The Defence the plugin leaves the monster of this id with (its one place for it, DefenceDrains.left). */
	private static DefenceDrains.Levels leftOn(StyleData data, int npc, TobScaling.Raid tob, DefenceDrains d)
	{
		StyleData.Monster m = data.monster(npc);
		return DefenceDrains.left(m, npc, MonsterLevels.scaled(m, null, tob, null), d, 0);
	}

	/**
	 * The answer key of a simulator (the
	 * calculator's own function for each step, in the order given), its 10 checks in the same order and with the same
	 * figures: Xarpus in normal mode (Defence 250, no floor), Sotetseg (200, floor 100), and the case of the calculator on
	 * the web (tools.runescape.wiki/osrs-dps, 2026-10-15: Xarpus, a party of three, two Elder mauls and a Bandos godsword
	 * of 43: Defence 63, slash defence roll 4608). The defence roll is (Defence + 9) * (bonus + 64), Xarpus's slash bonus
	 * being 0.
	 */
	@Test
	public void theSimulatorsOrderCases() throws IOException
	{
		StyleData data = new StyleData();
		int xarpus = 8340;
		int sotetseg = 8387;
		// 1. maul then BGS 50: 250 - 87 = 163, then 113
		DefenceDrains.Levels mb = leftOn(data, xarpus, null, inOrder("maul", "bgs", 50));
		assertEquals(113, mb.defence);
		// 2. BGS 50 then maul: 200, then 200 - 70 = 130
		assertEquals(130, leftOn(data, xarpus, null, inOrder("bgs", 50, "maul")).defence);
		// 3. the order [maul, BGS] is the calculator's counts {elderMaul: 1, bgs: 50}, in its fixed order (maul first):
		// the same Defence, defence roll and figure
		DefenceDrains.Levels counts = leftOn(data, xarpus, null, calculatorOrder(false, 1, 0, 0, 0, 0, 0, 50, 0));
		assertEquals(counts.defence, mb.defence);
		int slash = data.monster(xarpus).slash;
		assertEquals((counts.defence + 9) * (slash + 64), (mb.defence + 9) * (slash + 64));
		SetCalc.Loadout scythe = new SetCalc.Loadout();
		scythe.weaponName = "Scythe of vitur";
		scythe.category = "Scythe";
		scythe.speed = 5;
		scythe.styles = data.styles(22325);
		scythe.slash = 210;
		scythe.strength = 138;
		SetCalc.Player p = new SetCalc.Player();
		p.attack = 118;
		p.strength = 118;
		p.prayerAttack = 120;
		p.prayerStrength = 123;
		assertEquals(SetCalc.perTick(scythe, p, data.monster(xarpus).target(counts), false),
			SetCalc.perTick(scythe, p, data.monster(xarpus).target(mb), false), 0);
		// 4. Sotetseg 200: three mauls, or BGS 30 then two mauls, stop at the floor of 100
		assertEquals(100, leftOn(data, sotetseg, null, inOrder("maul", "maul", "maul")).defence);
		assertEquals(100, leftOn(data, sotetseg, null, inOrder("bgs", 30, "maul", "maul")).defence);
		// 5. no drain: 250
		assertEquals(250, leftOn(data, xarpus, null, null).defence);
		// 6. the calculator on the web: Xarpus, a party of three, maul, maul, BGS 43: Defence 63, defence roll 4608
		DefenceDrains.Levels web = leftOn(data, xarpus, new TobScaling.Raid(3), inOrder("maul", "maul", "bgs", 43));
		assertEquals(63, web.defence);
		assertEquals(4608, (web.defence + 9) * (slash + 64));
	}

	/** The calculator's levels for each case of drains.tsv: label to {defence left, magic left (-1: not checked)}. */
	private static Map<String, int[]> cases() throws IOException
	{
		Map<String, int[]> out = new HashMap<>();
		try (BufferedReader in = new BufferedReader(new InputStreamReader(
			DefenceDrainsTest.class.getResourceAsStream("drains.tsv"), StandardCharsets.UTF_8)))
		{
			String line;
			while ((line = in.readLine()) != null)
			{
				if (line.startsWith("#") || line.trim().isEmpty())
				{
					continue;
				}
				String[] c = line.split("\t", -1);
				out.put(c[0], new int[]{Integer.parseInt(c[16]), "-".equals(c[17]) ? -1 : Integer.parseInt(c[17])});
			}
		}
		return out;
	}

	@Test
	public void sameLevelsAsTheCalculator() throws IOException
	{
		StyleData data = new StyleData();
		int rows = 0;
		try (BufferedReader in = new BufferedReader(new InputStreamReader(
			DefenceDrainsTest.class.getResourceAsStream("drains.tsv"), StandardCharsets.UTF_8)))
		{
			String line;
			while ((line = in.readLine()) != null)
			{
				if (line.startsWith("#") || line.trim().isEmpty())
				{
					continue;
				}
				String[] c = line.split("\t", -1);
				int npc = Integer.parseInt(c[1]);
				boolean demon = "1".equals(c[3]);
				int defence = Integer.parseInt(c[4]);
				int magic = Integer.parseInt(c[5]);
				int magicDefence = Integer.parseInt(c[6]);
				DefenceDrains d = calculatorOrder("1".equals(c[7]), Integer.parseInt(c[8]), Integer.parseInt(c[9]),
					Integer.parseInt(c[10]), Integer.parseInt(c[11]), Integer.parseInt(c[12]), Integer.parseInt(c[13]),
					Integer.parseInt(c[14]), Integer.parseInt(c[15]));
				// the levels the drains start from: the table's, scaled for the raid as the calculator scaled them
				StyleData.Monster monster = data.monster(npc);
				int party = c.length > 19 ? Integer.parseInt(c[19]) : 0;
				DefenceDrains.Levels full = CoxScaling.levels(monster,
					party > 0 ? new CoxScaling.Raid(party, "1".equals(c[20])) : null);
				assertEquals(c[0] + " full defence", defence, full.defence);
				assertEquals(c[0] + " full magic", magic, full.magic);
				assertEquals(c[0] + " magic defence", magicDefence, full.magicDefence);
				assertEquals(c[0] + " demon", demon, monster.demon);
				DefenceDrains.Levels l = d.apply(full, demon, DefenceDrains.floor(npc, defence));
				assertEquals(c[0] + " defence", Integer.parseInt(c[16]), l.defence);
				if (!"-".equals(c[17]))
				{
					assertEquals(c[0] + " magic", Integer.parseInt(c[17]), l.magic);
				}
				if (!"-".equals(c[18]))
				{
					assertEquals(c[0] + " magic defence", Integer.parseInt(c[18]), l.magicDefence);
				}
				// what the plugin works out for the monster fought: the same, but for a fight immune to drains
				assertEquals(c[0] + " fought", l.defence, DefenceDrains.left(monster, npc, full, d, 0).defence);
				rows++;
			}
		}
		assertTrue("the file has its cases", rows >= 62);
	}

	/** Two Elder maul hits and a Bandos godsword hit of 60 on Tekton, as they arrive: 205 to 134, 88, then 28. */
	@Test
	public void tektonSequence()
	{
		DefenceDrains d = new DefenceDrains();
		d.add(SpecialWeapon.ELDER_MAUL, 1, true);
		assertEquals(134, d.apply(205, 205, 0, false, 0).defence);
		d.add(SpecialWeapon.ELDER_MAUL, 1, true);
		assertEquals(88, d.apply(205, 205, 0, false, 0).defence);
		d.add(SpecialWeapon.BANDOS_GODSWORD, 60, true);
		assertEquals(28, d.apply(205, 205, 0, false, 0).defence);
	}

	/** On Tekton a miss still lowers: 5% for the Elder maul and the Dragon warhammer, 10 levels for the godsword. */
	@Test
	public void tektonMisses()
	{
		DefenceDrains d = new DefenceDrains();
		d.add(SpecialWeapon.DRAGON_WARHAMMER, 0, true);
		assertEquals(205 - 205 * 5 / 100, d.apply(205, 205, 0, false, 0).defence);
		d.add(SpecialWeapon.BANDOS_GODSWORD, 0, true);
		assertEquals(195 - 10, d.apply(205, 205, 0, false, 0).defence);
		d.add(SpecialWeapon.ELDER_MAUL, 0, true);
		assertEquals(185 - 185 * 5 / 100, d.apply(205, 205, 0, false, 0).defence);
	}

	/** Anywhere else a special attack that deals no damage lowers nothing. */
	@Test
	public void missesElsewhereLowerNothing()
	{
		DefenceDrains d = new DefenceDrains();
		for (SpecialWeapon w : SpecialWeapon.values())
		{
			d.add(w, 0, false);
		}
		assertTrue(d.none());
		assertEquals(250, d.apply(250, 80, 298, false, 0).defence);
	}

	/** Each special attack as the Special Attack Counter reports it is the calculator's count of it. */
	@Test
	public void eachWeaponIsCountedAsTheCalculatorCountsIt()
	{
		Object[][] cases = {
			{SpecialWeapon.DRAGON_WARHAMMER, 40, calculatorOrder(false, 0, 1, 0, 0, 0, 0, 0, 0)},
			{SpecialWeapon.ELDER_MAUL, 1, calculatorOrder(false, 1, 0, 0, 0, 0, 0, 0, 0)},
			{SpecialWeapon.ARCLIGHT, 12, calculatorOrder(false, 0, 0, 1, 0, 0, 0, 0, 0)},
			{SpecialWeapon.DARKLIGHT, 9, calculatorOrder(false, 0, 0, 1, 0, 0, 0, 0, 0)},
			{SpecialWeapon.EMBERLIGHT, 20, calculatorOrder(false, 0, 0, 0, 1, 0, 0, 0, 0)},
			{SpecialWeapon.TONALZTICS_OF_RALOS, 2, calculatorOrder(false, 0, 0, 0, 0, 2, 0, 0, 0)},
			{SpecialWeapon.TONALZTICS_OF_RALOS, 1, calculatorOrder(false, 0, 0, 0, 0, 1, 0, 0, 0)},
			{SpecialWeapon.SEERCULL, 18, calculatorOrder(false, 0, 0, 0, 0, 0, 18, 0, 0)},
			{SpecialWeapon.BANDOS_GODSWORD, 33, calculatorOrder(false, 0, 0, 0, 0, 0, 0, 33, 0)},
			{SpecialWeapon.EYE_OF_AYAK, 25, calculatorOrder(false, 0, 0, 0, 0, 0, 0, 0, 25)},
			{SpecialWeapon.ACCURSED_SCEPTRE, 30, calculatorOrder(true, 0, 0, 0, 0, 0, 0, 0, 0)},
		};
		for (Object[] c : cases)
		{
			DefenceDrains one = new DefenceDrains();
			one.add((SpecialWeapon) c[0], (Integer) c[1], false);
			DefenceDrains.Levels got = one.apply(levels(), true, 0);
			DefenceDrains.Levels want = ((DefenceDrains) c[2]).apply(levels(), true, 0);
			assertEquals(c[0] + " defence", want.defence, got.defence);
			assertEquals(c[0] + " magic", want.magic, got.magic);
			assertEquals(c[0] + " magic defence", want.magicDefence, got.magicDefence);
			assertEquals(c[0] + " attack", want.attack, got.attack);
		}
		// two godsword hits are their damage added up, as long as Defence lasts
		DefenceDrains two = new DefenceDrains();
		two.add(SpecialWeapon.BANDOS_GODSWORD, 33, false);
		two.add(SpecialWeapon.BANDOS_GODSWORD, 7, false);
		assertEquals(calculatorOrder(false, 0, 0, 0, 0, 0, 0, 40, 0).apply(levels(), false, 0).defence,
			two.apply(levels(), false, 0).defence);
		// Dinh's bulwark lowers nothing
		DefenceDrains bulwark = new DefenceDrains();
		bulwark.add(SpecialWeapon.BULWARK, 30, false);
		assertTrue(bulwark.none());
	}

	/** A monster's full levels for the cases above. */
	private static DefenceDrains.Levels levels()
	{
		DefenceDrains.Levels l = new DefenceDrains.Levels();
		l.attack = 250;
		l.strength = 250;
		l.defence = 250;
		l.magic = 160;
		l.ranged = 200;
		l.magicDefence = 120;
		return l;
	}

	/** The Accursed sceptre's drain does not stack: a second one changes nothing. */
	@Test
	public void accursedOnce()
	{
		DefenceDrains d = new DefenceDrains();
		d.add(SpecialWeapon.ACCURSED_SCEPTRE, 10, false);
		int once = d.apply(250, 80, 0, false, 0).defence;
		d.add(SpecialWeapon.ACCURSED_SCEPTRE, 10, false);
		assertEquals(once, d.apply(250, 80, 0, false, 0).defence);
	}

	/**
	 * CC-35: each special attack lowers the level the one before left, in the order they landed (OSRS Wiki, Dragon warhammer
	 * and Elder maul, Special attack: "each reduction is calculated from the current temporary level"; Bandos godsword:
	 * "drains the opponent's combat levels equal to the damage dealt"). The calculator applies its counts in one order of
	 * its own, the percentages first: its numbers stand when they landed in that order (drains.tsv), and the other order
	 * is the wiki's rule on the level the first one left, as the calculator rounds (fractions down).
	 */
	@Test
	public void theOrderTheyLandedIn() throws IOException
	{
		Map<String, int[]> calc = cases();
		// the Kalphite Queen (300): a Dragon warhammer, then a godsword hit of 50, as the calculator has it
		DefenceDrains dwhFirst = new DefenceDrains();
		dwhFirst.add(SpecialWeapon.DRAGON_WARHAMMER, 40, false);
		dwhFirst.add(SpecialWeapon.BANDOS_GODSWORD, 50, false);
		assertEquals(calc.get("kq-dwh-1-bgs-50")[0], dwhFirst.apply(300, 150, 100, false, 0).defence);
		// the godsword first: 300 to 250 (the calculator's kq-bgs-50), then the hammer takes 30% of 250
		DefenceDrains bgsFirst = new DefenceDrains();
		bgsFirst.add(SpecialWeapon.BANDOS_GODSWORD, 50, false);
		int afterBgs = calc.get("kq-bgs-50")[0];
		assertEquals(afterBgs, bgsFirst.apply(300, 150, 100, false, 0).defence);
		bgsFirst.add(SpecialWeapon.DRAGON_WARHAMMER, 40, false);
		assertEquals(afterBgs - afterBgs * 3 / 10, bgsFirst.apply(300, 150, 100, false, 0).defence);
		assertEquals(175, bgsFirst.apply(300, 150, 100, false, 0).defence);
		// Tekton (205): the godsword's 60 before the two Elder mauls leaves 145, 95, then 62; after them, the
		// calculator's 28
		DefenceDrains tekton = new DefenceDrains();
		tekton.add(SpecialWeapon.BANDOS_GODSWORD, 60, true);
		tekton.add(SpecialWeapon.ELDER_MAUL, 1, true);
		tekton.add(SpecialWeapon.ELDER_MAUL, 1, true);
		int level = 205 - 60;
		level -= level * 35 / 100;
		level -= level * 35 / 100;
		assertEquals(62, level);
		assertEquals(level, tekton.apply(205, 205, 0, false, 0).defence);
		DefenceDrains maulsFirst = new DefenceDrains();
		maulsFirst.add(SpecialWeapon.ELDER_MAUL, 1, true);
		maulsFirst.add(SpecialWeapon.ELDER_MAUL, 1, true);
		maulsFirst.add(SpecialWeapon.BANDOS_GODSWORD, 60, true);
		assertEquals(calc.get("tekton-elder-maul-2-bgs-60")[0], maulsFirst.apply(205, 205, 0, false, 0).defence);
	}

	/**
	 * CC-36: the bone dagger and the Dorgeshuun crossbow lower Defence by their damage, but only while it is full (OSRS
	 * Wiki, Bone dagger and Dorgeshuun crossbow, Special attack: "lowers the target's Defence by the amount of damage
	 * dealt"; "they'll only try to reduce it if it's on (or above) its base level at the time", Mod Ash), which is a
	 * godsword's hit that stops at Defence (the calculator's kq-bgs-30); the Barrelchest anchor lowers Defence by a tenth of
	 * its damage (Barrelchest anchor, Special attack: "drains the opponent's combat stats equivalent by 10% of the damage
	 * dealt in the following order: Defence, Attack, Ranged, then Magic"; "Drains one stat", Mod Ash), rounded down.
	 */
	@Test
	public void boneDaggerDorgeshuunCrossbowAndAnchor() throws IOException
	{
		Map<String, int[]> calc = cases();
		for (SpecialWeapon w : new SpecialWeapon[]{SpecialWeapon.BONE_DAGGER, SpecialWeapon.DORGESHUUN_CROSSBOW})
		{
			DefenceDrains d = new DefenceDrains();
			d.add(w, 30, false);
			assertEquals(w + " on the full Defence", calc.get("kq-bgs-30")[0], d.apply(300, 150, 100, false, 0).defence);
			// a second one finds Defence lowered: nothing more
			d.add(w, 30, false);
			assertEquals(w + " twice", calc.get("kq-bgs-30")[0], d.apply(300, 150, 100, false, 0).defence);
			// after an Elder maul (the calculator's kq-elder-maul-1): nothing
			DefenceDrains after = new DefenceDrains();
			after.add(SpecialWeapon.ELDER_MAUL, 1, false);
			after.add(w, 30, false);
			assertEquals(w + " after a maul", calc.get("kq-elder-maul-1")[0], after.apply(300, 150, 100, false, 0).defence);
			// before it: the maul takes 35% of what the dagger left
			DefenceDrains before = new DefenceDrains();
			before.add(w, 30, false);
			before.add(SpecialWeapon.ELDER_MAUL, 1, false);
			assertEquals(w + " before a maul", 270 - 270 * 35 / 100, before.apply(300, 150, 100, false, 0).defence);
			// down to the floor, no further
			DefenceDrains floored = new DefenceDrains();
			floored.add(w, 200, false);
			assertEquals(w + " at the floor", 250, floored.apply(300, 150, 100, false, 250).defence);
		}
		DefenceDrains anchor = new DefenceDrains();
		anchor.add(SpecialWeapon.BARRELCHEST_ANCHOR, 64, false);
		DefenceDrains.Levels l = anchor.apply(300, 150, 100, false, 0);
		assertEquals(300 - 6, l.defence);
		assertEquals(150, l.magic);
		anchor.add(SpecialWeapon.BARRELCHEST_ANCHOR, 64, false);
		assertEquals(300 - 12, anchor.apply(300, 150, 100, false, 0).defence);
		// with Defence gone, the next level in its order: Attack, which no figure reads
		DefenceDrains gone = new DefenceDrains();
		gone.add(SpecialWeapon.BANDOS_GODSWORD, 300, false);
		gone.add(SpecialWeapon.BARRELCHEST_ANCHOR, 50, false);
		DefenceDrains.Levels full = levels();
		full.defence = 300;
		DefenceDrains.Levels g = gone.apply(full, false, 0);
		assertEquals(0, g.defence);
		assertEquals(full.attack - 5, g.attack);
		assertEquals(full.magic, g.magic);
	}

	/**
	 * CC-48: the Accursed sceptre takes a level already lowered no further than 15% below the full one (OSRS Wiki, Accursed
	 * sceptre, Special attack: "If the target's levels have already been reduced, Condemn will only drain them to a maximum
	 * of 15%. As a result, multiple special attacks do not stack."), and the Seercull lowers Magic only while it is full
	 * (Seercull, Special attack: "will only drain the opponent's Magic level if it is not already reduced; consecutive use
	 * of Soulshot or accursed sceptre special attacks will have no effect on their Magic level"). First, they are the
	 * calculator's (kq-accursed-elder-maul-1, kq-seercull-30).
	 */
	@Test
	public void accursedAndSeercullDoNotAddUp() throws IOException
	{
		Map<String, int[]> calc = cases();
		// the sceptre, then a maul: the calculator's 166, Magic 127
		DefenceDrains sceptreFirst = new DefenceDrains();
		sceptreFirst.add(SpecialWeapon.ACCURSED_SCEPTRE, 30, false);
		sceptreFirst.add(SpecialWeapon.ELDER_MAUL, 1, false);
		DefenceDrains.Levels l = sceptreFirst.apply(300, 150, 100, false, 0);
		assertEquals(calc.get("kq-accursed-elder-maul-1")[0], l.defence);
		assertEquals(calc.get("kq-accursed-elder-maul-1")[1], l.magic);
		// a maul, then the sceptre: Defence is already 35% down and stays at the maul's 195; Magic, full, goes to 85%
		DefenceDrains maulFirst = new DefenceDrains();
		maulFirst.add(SpecialWeapon.ELDER_MAUL, 1, false);
		maulFirst.add(SpecialWeapon.ACCURSED_SCEPTRE, 30, false);
		l = maulFirst.apply(300, 150, 100, false, 0);
		assertEquals(calc.get("kq-elder-maul-1")[0], l.defence);
		assertEquals(150 * 17 / 20, l.magic);
		// the wiki's example: 80 Defence and Magic cannot be drained lower than 68
		DefenceDrains wiki = new DefenceDrains();
		wiki.add(SpecialWeapon.ACCURSED_SCEPTRE, 1, false);
		assertEquals(68, wiki.apply(80, 80, 0, false, 0).defence);
		assertEquals(68, wiki.apply(80, 80, 0, false, 0).magic);
		// two Seerculls of 30: the first, as the calculator's kq-seercull-30; the second finds Magic lowered
		DefenceDrains seercull = new DefenceDrains();
		seercull.add(SpecialWeapon.SEERCULL, 30, false);
		seercull.add(SpecialWeapon.SEERCULL, 30, false);
		assertEquals(calc.get("kq-seercull-30")[1], seercull.apply(300, 150, 100, false, 0).magic);
		// after the sceptre, the Seercull does nothing to Magic
		DefenceDrains afterSceptre = new DefenceDrains();
		afterSceptre.add(SpecialWeapon.ACCURSED_SCEPTRE, 30, false);
		afterSceptre.add(SpecialWeapon.SEERCULL, 30, false);
		assertEquals(150 * 17 / 20, afterSceptre.apply(300, 150, 100, false, 0).magic);
		// before it: the Seercull's 120 is already more than 15% down; the sceptre still takes Defence to 255
		DefenceDrains beforeSceptre = new DefenceDrains();
		beforeSceptre.add(SpecialWeapon.SEERCULL, 30, false);
		beforeSceptre.add(SpecialWeapon.ACCURSED_SCEPTRE, 30, false);
		l = beforeSceptre.apply(300, 150, 100, false, 0);
		assertEquals(120, l.magic);
		assertEquals(300 * 17 / 20, l.defence);
	}

	@Test
	public void floors()
	{
		assertEquals(215, DefenceDrains.floor(12223, 215)); // Vardorvis cannot be drained
		assertEquals(250, DefenceDrains.floor(11278, 260)); // Nex
		assertEquals(145, DefenceDrains.floor(14176, 225)); // Yama
		assertEquals(0, DefenceDrains.floor(7540, 205)); // Tekton
		assertTrue(DefenceDrains.tekton(7545));
		assertFalse(DefenceDrains.tekton(2215));
	}

	/**
	 * CC-33 and CC-49: the floors the OSRS Wiki gives that the calculator does not have (above them, its numbers stand:
	 * drains.tsv): the Phantom Muspah 125 (Phantom Muspah/Strategies: "Muspah's defence can be reduced down to 125"), the
	 * Doom of Mokhaiotl 60 (Doom of Mokhaiotl/Strategies, Combat: "Doom has a defence drain cap of 30, meaning that its
	 * Defence can only be lowered to a minimum of 60"), the Fragment of Seren 60 (Fragment of Seren Speed-Trialist: "her
	 * defence cannot be lowered below 60") and the Menaphite Akh 100 (Menaphite Akh: "her defence cannot be lowered below
	 * 100", Mod Ash: "it won't go below 100"). Every id the wiki gives each.
	 */
	@Test
	public void floorsOfTheWiki() throws IOException
	{
		for (int id : new int[]{12077, 12078, 12082, 12079, 12080})
		{
			assertEquals("Phantom Muspah " + id, 125, DefenceDrains.floor(id, 200));
		}
		for (int id : new int[]{14707, 14708, 14709})
		{
			assertEquals("Doom " + id, 60, DefenceDrains.floor(id, 90));
		}
		for (int id : new int[]{8917, 8918, 8919, 8920})
		{
			assertEquals("Fragment of Seren " + id, 60, DefenceDrains.floor(id, 102));
		}
		assertEquals(100, DefenceDrains.floor(11492, 140));
		Map<String, int[]> calc = cases();
		// the Muspah (200): an Elder maul as the calculator's 130; a second one would take it to 85, the floor holds 125
		DefenceDrains mauls = new DefenceDrains();
		mauls.add(SpecialWeapon.ELDER_MAUL, 1, false);
		assertEquals(calc.get("muspah-elder-maul-1")[0], mauls.apply(200, 150, 437, false, DefenceDrains.floor(12077, 200)).defence);
		mauls.add(SpecialWeapon.ELDER_MAUL, 1, false);
		assertEquals(125, mauls.apply(200, 150, 437, false, DefenceDrains.floor(12077, 200)).defence);
		// the Doom (90): three Elder mauls stop at 60
		DefenceDrains doom = new DefenceDrains();
		for (int i = 0; i < 3; i++)
		{
			doom.add(SpecialWeapon.ELDER_MAUL, 1, false);
		}
		assertEquals(60, doom.apply(90, 275, 160, true, DefenceDrains.floor(14707, 90)).defence);
		// the Fragment (102): a godsword's 30 is the calculator's 72; 60 stops at 60
		DefenceDrains fragment = new DefenceDrains();
		fragment.add(SpecialWeapon.BANDOS_GODSWORD, 30, false);
		assertEquals(calc.get("fragment-of-seren-bgs-30")[0], fragment.apply(102, 102, 10, false, DefenceDrains.floor(8917, 102)).defence);
		fragment.add(SpecialWeapon.BANDOS_GODSWORD, 30, false);
		assertEquals(60, fragment.apply(102, 102, 10, false, DefenceDrains.floor(8917, 102)).defence);
		// the Menaphite Akh (140): two Elder mauls stop at 100
		DefenceDrains akh = new DefenceDrains();
		akh.add(SpecialWeapon.ELDER_MAUL, 1, false);
		akh.add(SpecialWeapon.ELDER_MAUL, 1, false);
		assertEquals(100, akh.apply(140, 150, 280, false, DefenceDrains.floor(11492, 140)).defence);
	}

	/**
	 * CC-33 and CC-49: monsters whose Defence comes back on a change of form, though the table's Defence is the same in
	 * both (OSRS Wiki): the Phantom Muspah entering its shielded phase (Phantom Muspah/Strategies: "The defence reduction
	 * will preserve between phases until the shielded phase, where Muspah's defence resets to 200") and Galvek going on to
	 * another phase (Galvek, Mechanics: "he will reset his Defence to its original level after every phase"; ids by phase
	 * from its infobox: Fire 8094, 8095, 8177; Air 8097; Water 8096; Earth 8098, 8178, 8179).
	 */
	@Test
	public void defenceBackOnAChangeOfForm()
	{
		assertTrue(DefenceDrains.resets(12077, 12079));
		assertTrue(DefenceDrains.resets(12078, 12079));
		assertTrue(DefenceDrains.resets(12082, 12079));
		assertFalse("between its other forms the drains stay", DefenceDrains.resets(12077, 12078));
		assertFalse(DefenceDrains.resets(12082, 12077));
		assertFalse("out of the shield, what is drained there stays", DefenceDrains.resets(12079, 12080));
		assertTrue(DefenceDrains.resets(8094, 8097));
		assertTrue(DefenceDrains.resets(8097, 8096));
		assertTrue(DefenceDrains.resets(8096, 8098));
		assertFalse("the same phase", DefenceDrains.resets(8094, 8095));
		assertFalse("an id the wiki puts in no phase", DefenceDrains.resets(8096, 8099));
		assertFalse(DefenceDrains.resets(963, 965));
	}

	/**
	 * CC-34: the fights that take no drain at all, whatever lands (OSRS Wiki, Temporary skill drain: "Some monsters are
	 * immune to stat drains. These include Verzik Vitur, the Awakened variants of the Forgotten Four, contracted Yama
	 * fights, and Demonic Brutus"; Hard mode, The Forgotten Four: "They also become immune to stat drains, absorbing them";
	 * Moons of Peril/Strategies, Weapon choice: "Defence-reducing special attacks and Death Charge have no effect on the
	 * Moons of Peril"). The awakened fights are the table's rows under the negative of the id; the fights after the quest,
	 * with the same NPC id, are drained. Yama is not taken as immune: the wiki's own page on him says the drains act in the
	 * contracts (Yama, Contract variants: "stat draining effects which reference Yama's maximum stats only consider his
	 * normal form. For example, the accursed sceptre special attack can drain stats twice"), and the table has no
	 * contracted fight.
	 */
	@Test
	public void fightsImmuneToDrains() throws IOException
	{
		StyleData data = new StyleData();
		DefenceDrains d = new DefenceDrains();
		d.add(SpecialWeapon.ELDER_MAUL, 1, false);
		d.add(SpecialWeapon.BANDOS_GODSWORD, 50, false);
		d.add(SpecialWeapon.ACCURSED_SCEPTRE, 30, false);
		d.add(SpecialWeapon.EYE_OF_AYAK, 30, false);
		for (int id : new int[]{12191, 12214, 12204, 12223})
		{
			StyleData.Monster awakened = data.monster(-id);
			assertTrue(awakened.name, awakened.awakened);
			assertTrue(awakened.name, DefenceDrains.immune(awakened));
			DefenceDrains.Levels full = CoxScaling.levels(awakened, null);
			assertSame(awakened.name + " awakened", full, DefenceDrains.left(awakened, id, full, d, 0));
			StyleData.Monster afterQuest = data.monster(id);
			assertFalse(afterQuest.name, afterQuest.awakened);
			assertFalse(afterQuest.name, DefenceDrains.immune(afterQuest));
		}
		// Duke Sucellus after the quest (275) is drained as before: the maul leaves 179
		StyleData.Monster duke = data.monster(12191);
		DefenceDrains maul = new DefenceDrains();
		maul.add(SpecialWeapon.ELDER_MAUL, 1, false);
		assertEquals(275 - 275 * 35 / 100, DefenceDrains.left(duke, 12191, CoxScaling.levels(duke, null), maul, 0).defence);
		for (int id : new int[]{15628, 15629, 13011, 13012, 13013})
		{
			StyleData.Monster m = data.monster(id);
			assertTrue(m.name, DefenceDrains.immune(m));
			assertTrue(m.name, DefenceDrains.immune(id));
			DefenceDrains.Levels full = CoxScaling.levels(m, null);
			DefenceDrains.Levels left = DefenceDrains.left(m, id, full, d, 0);
			assertEquals(m.name, full.defence, left.defence);
			assertEquals(m.name, full.magic, left.magic);
			assertEquals(m.name, full.magicDefence, left.magicDefence);
		}
		StyleData.Monster yama = data.monster(14176);
		assertFalse(DefenceDrains.immune(yama));
		assertEquals(225 - 225 * 35 / 100, DefenceDrains.left(yama, 14176, CoxScaling.levels(yama, null), maul, 0).defence);
		assertFalse(DefenceDrains.immune(data.monster(15626))); // Brutus
	}

	/**
	 * What the plugin works out for the monster fought: the floor by the table's row and by the NPC's own id, nothing
	 * counted, and the Nightmares winning their Defence back with time (see below).
	 */
	@Test
	public void theLevelsOfTheMonsterFought() throws IOException
	{
		StyleData data = new StyleData();
		StyleData.Monster kq = data.monster(963);
		DefenceDrains.Levels full = CoxScaling.levels(kq, null);
		assertSame(full, DefenceDrains.left(kq, 963, full, null, 0));
		assertSame(full, DefenceDrains.left(kq, 963, full, new DefenceDrains(), 0));
		// the Phantom Muspah: two Elder mauls stop at its floor of 125
		DefenceDrains mauls = new DefenceDrains();
		mauls.add(SpecialWeapon.ELDER_MAUL, 1, false);
		mauls.add(SpecialWeapon.ELDER_MAUL, 1, false);
		StyleData.Monster muspah = data.monster(12077);
		assertEquals(125, DefenceDrains.left(muspah, 12077, CoxScaling.levels(muspah, null), mauls, 0).defence);
		// Phosani's Nightmare: a Dragon warhammer to the floor of 120, and a level back every 5 ticks after it
		StyleData.Monster phosani = data.monster(9416);
		DefenceDrains dwh = new DefenceDrains();
		dwh.add(SpecialWeapon.DRAGON_WARHAMMER, 40, false);
		DefenceDrains.Levels phosaniFull = CoxScaling.levels(phosani, null);
		assertEquals(120, DefenceDrains.left(phosani, 9416, phosaniFull, dwh, 0).defence);
		assertEquals(130, DefenceDrains.left(phosani, 9416, phosaniFull, dwh, 50).defence);
		assertEquals(150, DefenceDrains.left(phosani, 9416, phosaniFull, dwh, 1000).defence);
	}

	/**
	 * The Nightmare wins back 20 Defence a minute and Phosani's Nightmare one level every 5 ticks (OSRS Wiki; the
	 * calculator has no time): a Dragon warhammer takes Phosani from 150 to the floor of 120, and she is back to full
	 * 150 ticks later.
	 */
	@Test
	public void theNightmaresWinTheirDefenceBack()
	{
		assertEquals(0, DefenceDrains.recovered(9416, 0));
		assertEquals(10, DefenceDrains.recovered(9416, 50));
		assertEquals(30, DefenceDrains.recovered(9416, 150));
		assertEquals(0, DefenceDrains.recovered(9425, 99));
		assertEquals(20, DefenceDrains.recovered(9425, 100));
		assertEquals(40, DefenceDrains.recovered(9425, 250));
		assertEquals(0, DefenceDrains.recovered(415, 1000));
		DefenceDrains d = new DefenceDrains();
		d.add(SpecialWeapon.DRAGON_WARHAMMER, 40, false);
		DefenceDrains.Levels drained = d.apply(150, 150, 40, false, DefenceDrains.floor(9416, 150));
		assertEquals(120, drained.defence);
		assertEquals(130, Math.min(150, drained.defence + DefenceDrains.recovered(9416, 50)));
	}
}
