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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;

/**
 * Whole sets of gear against a few monsters, compared with the OSRS Wiki calculator
 * (tools/gen_sets.py). What matters to the plugin is the ratio between two sets, so the test
 * checks each figure and then the gain of every pair of sets on the same monster.
 */
public class SetCalcTest
{
	/** How far a figure may be from the calculator's, as a fraction of it. */
	private static final double FIGURE = 0.001;
	/** How far the gain of one set over another may be from the calculator's, in percentage points. */
	private static final double GAIN_POINTS = 6;

	static final class Case
	{
		String set;
		int npc;
		double expected;
		double got;
		/** In the files of the raids: the monster's health now, the raid level (-1 for none) and the party read. */
		int health;
		int toaLevel = -1;
		int party;
	}

	/**
	 * The cases of sets.tsv with the plugin's figure for each. A case of the Chambers of Xeric with a party
	 * (column 26) is worked out with the raid's scaling and then the special attacks of column 28, as the plugin
	 * does in the raid.
	 */
	static List<Case> cases() throws IOException
	{
		return cases("sets.tsv");
	}

	/**
	 * The cases of a file in the layout of sets.tsv. The files of the raids and of the fights with a rule of their own
	 * (toa_sets.tsv, tob_sets.tsv, rules_sets.tsv: tools/gen_toa.py, gen_tob.py, gen_rules.py) carry more columns: the
	 * Tombs of Amascut's raid level (-1 when none is read), the path level, the party read in the Theatre of Blood or the
	 * Tombs, the monster's health now, the player's boosted levels, the Inquisitor's pieces and the ruby or diamond
	 * bolts, and in the newer ones (table_sets.tsv: tools/gen_table.py) the ammunition worn and whether Efaritay's aid
	 * is, and in items_sets.tsv (tools/gen_items.py) the game's names of everything worn and the ammunition's strength. Their monster is scaled as
	 * the plugin scales it in game ({@link CombatCheckPlugin#scaled}, then Araxxor's enrage), with the Kandarin hard diary
	 * done, as in the calculator's runs.
	 */
	static List<Case> cases(String file) throws IOException
	{
		return cases(file, npc -> npc);
	}

	/**
	 * The same, each case run on the monster {@code asNpc} gives for the case's own (its {@code npc} becomes that one,
	 * the calculator's figure stays): the Ruptura araxyte on the Acidic's cases.
	 */
	static List<Case> cases(String file, java.util.function.IntUnaryOperator asNpc) throws IOException
	{
		StyleData data = new StyleData();
		List<Case> out = new ArrayList<>();
		try (BufferedReader r = new BufferedReader(new InputStreamReader(
			SetCalcTest.class.getResourceAsStream(file), StandardCharsets.UTF_8)))
		{
			String line;
			while ((line = r.readLine()) != null)
			{
				if (line.isEmpty() || line.startsWith("#"))
				{
					continue;
				}
				String[] c = line.split("\t", -1);
				SetCalc.Loadout l = new SetCalc.Loadout();
				int weaponId = Integer.parseInt(c[2]);
				l.weaponName = c[3];
				l.category = c[4];
				l.speed = Integer.parseInt(c[5]);
				l.styles = data.styles(weaponId);
				l.stab = Integer.parseInt(c[8]);
				l.slash = Integer.parseInt(c[9]);
				l.crush = Integer.parseInt(c[10]);
				l.magic = Integer.parseInt(c[11]);
				l.ranged = Integer.parseInt(c[12]);
				l.strength = Integer.parseInt(c[13]);
				l.rangedStrength = Integer.parseInt(c[14]);
				// the blowpipe cases carry the strength of the darts inside
				l.dartKnown = c[3].toLowerCase(java.util.Locale.ROOT).contains("blowpipe");
				l.magicDamage = Double.parseDouble(c[15]);
				Case k = new Case();
				k.set = c[0];
				k.npc = asNpc.applyAsInt(Integer.parseInt(c[6]));
				k.expected = Double.parseDouble(c[16]);
				StyleData.Monster monster = data.monster(k.npc);
				SetCalc.Target target = monster.target();
				if (c.length > 28 && !c[26].isEmpty() && Integer.parseInt(c[26]) > 0)
				{
					DefenceDrains.Levels levels = CoxScaling.levels(monster,
						new CoxScaling.Raid(Integer.parseInt(c[26]), "1".equals(c[27])));
					DefenceDrains drains = drains(c[28]);
					if (drains != null)
					{
						levels = drains.apply(levels, monster.demon, DefenceDrains.floor(k.npc, levels.defence));
					}
					target = monster.target(levels);
				}
				if (c.length > 19)
				{
					l.mask = Integer.parseInt(c[17]);
					l.salve = Integer.parseInt(c[18]);
					target.onTask = "1".equals(c[19]);
				}
				if (c.length > 23)
				{
					l.crystal = Integer.parseInt(c[20]);
					l.voidMelee = Integer.parseInt(c[21]);
					l.voidRanged = Integer.parseInt(c[22]);
					target.inWilderness = "1".equals(c[23]);
				}
				if (c.length > 25)
				{
					l.spell = Integer.parseInt(c[24]);
					l.tome = c[25].isEmpty() ? null : c[25];
				}
				SetCalc.Player player = player(c[1]);
				if (c.length > 35)
				{
					int toaLevel = Integer.parseInt(c[29]);
					int path = Integer.parseInt(c[30]);
					int party = Integer.parseInt(c[31]);
					int health = Integer.parseInt(c[32]);
					ToaScaling.Raid toa = toaLevel < 0 ? null : new ToaScaling.Raid(toaLevel, party, path, path, path, path);
					TobScaling.Raid tob = party > 0 ? new TobScaling.Raid(party) : null;
					DefenceDrains.Levels levels = MonsterLevels.scaled(monster, null, tob, toa);
					levels = MonsterLevels.enraged(monster, levels, health);
					// the Wardens of the third phase, enraged: the calculator's Enraged version of the same id
					levels = MonsterLevels.wardenEnraged(monster, levels, c.length > 36 && "Enraged".equals(c[36]));
					target = monster.target(levels);
					target.toaInvocation = ToaScaling.invocation(monster, toa);
					target.hitpointsNow = health;
					// the fight in the Wilderness (column 23) on the target made again
					target.inWilderness = "1".equals(c[23]);
					String[] lv = c[33].split(";");
					player.attack = Integer.parseInt(lv[0]);
					player.strength = Integer.parseInt(lv[1]);
					player.ranged = Integer.parseInt(lv[2]);
					player.magic = Integer.parseInt(lv[3]);
					player.kandarinDiary = true;
					l.inquisitor = Integer.parseInt(c[34]);
					l.bolt = Integer.parseInt(c[35]);
					l.voidMagic = c.length > 37 && !c[37].isEmpty() ? Integer.parseInt(c[37]) : 0;
					// the ammunition worn, which the weapon shoots in these cases (tools/raid_cases.py)
					if (c.length > 38 && !c[38].isEmpty())
					{
						l.ammo = c[38];
						l.ammoWorn = c[38];
					}
					// Efaritay's aid in the ring slot (table_sets.tsv)
					l.efaritay = c.length > 39 && "1".equals(c[39]);
					if (c.length > 41)
					{
						// the game's names of everything worn (items_sets.tsv): the plugin works out the sets from them, and
						// the ammunition's own strength
						SetEffects.wornSets(l, java.util.Arrays.asList(c[40].split(";")));
						l.ammoRangedStrength = Integer.parseInt(c[41]);
					}
					k.health = health;
					k.toaLevel = toaLevel;
					k.party = party;
				}
				k.got = SetCalc.perTick(l, player, target, false);
				out.add(k);
			}
		}
		return out;
	}

	/**
	 * The special attacks of a case, written "elderMaul=2;dwh=1" (the calculator's counts), added in the calculator's
	 * order ({@link DefenceDrainsTest#calculatorOrder}); null for none.
	 */
	static DefenceDrains drains(String text)
	{
		if (text.isEmpty())
		{
			return null;
		}
		java.util.Map<String, Integer> n = new java.util.HashMap<>();
		for (String part : text.split(";"))
		{
			String[] kv = part.split("=");
			switch (kv[0])
			{
				case "accursed":
				case "elderMaul":
				case "dwh":
				case "arclight":
				case "emberlight":
				case "tonalztic":
				case "seercull":
				case "bgs":
				case "ayak":
					n.put(kv[0], Integer.parseInt(kv[1]));
					break;
				default:
					throw new IllegalArgumentException(part);
			}
		}
		return DefenceDrainsTest.calculatorOrder(n.getOrDefault("accursed", 0) > 0, n.getOrDefault("elderMaul", 0),
			n.getOrDefault("dwh", 0), n.getOrDefault("arclight", 0), n.getOrDefault("emberlight", 0),
			n.getOrDefault("tonalztic", 0), n.getOrDefault("seercull", 0), n.getOrDefault("bgs", 0),
			n.getOrDefault("ayak", 0));
	}

	/** The presets the cases were generated with: Piety and a super combat, Rigour and a ranging potion, Augury and a heart. */
	private static SetCalc.Player player(String cls)
	{
		SetCalc.Player p = new SetCalc.Player();
		p.attack = 99;
		p.strength = 99;
		p.ranged = 99;
		p.magic = 99;
		if ("melee".equals(cls))
		{
			p.attack = 118;
			p.strength = 118;
			p.prayerAttack = 120;
			p.prayerStrength = 123;
		}
		else if ("ranged".equals(cls))
		{
			p.ranged = 112;
			p.prayerRanged = 120;
			p.prayerRangedStrength = 123;
		}
		else
		{
			p.magic = 112;
			p.prayerMagic = 125;
			p.prayerMagicDamage = 4;
		}
		return p;
	}

	/** Checks that every case of a file is within {@link #FIGURE} of the calculator's figure; returns the cases. */
	static List<Case> figuresOf(String file) throws IOException
	{
		List<Case> all = cases(file);
		List<String> off = new ArrayList<>();
		for (Case k : all)
		{
			if (Math.abs(k.got - k.expected) > FIGURE * k.expected)
			{
				off.add(String.format("%s on %d (raid level %d, party %d, health %d): %.5f, calculator %.5f", k.set, k.npc,
					k.toaLevel, k.party, k.health, k.got, k.expected));
			}
		}
		assertTrue(off.size() + " of " + all.size() + " off: " + off, off.isEmpty());
		return all;
	}

	@Test
	public void figuresAreCloseToTheCalculator() throws IOException
	{
		List<String> off = new ArrayList<>();
		for (Case k : cases())
		{
			if (Math.abs(k.got - k.expected) > FIGURE * k.expected)
			{
				off.add(String.format("%s on %d: %.3f, calculator %.3f", k.set, k.npc, k.got, k.expected));
			}
		}
		assertTrue(off.size() + " off: " + off, off.isEmpty());
	}

	@Test
	public void gainsBetweenSetsAreCloseToTheCalculator() throws IOException
	{
		Map<Integer, List<Case>> byNpc = new HashMap<>();
		for (Case k : cases())
		{
			byNpc.computeIfAbsent(k.npc, n -> new ArrayList<>()).add(k);
		}
		List<String> off = new ArrayList<>();
		for (List<Case> list : byNpc.values())
		{
			for (Case a : list)
			{
				for (Case b : list)
				{
					if (a == b || a.expected <= 0 || b.expected < a.expected)
					{
						continue;
					}
					double expected = (b.expected / a.expected - 1) * 100;
					double got = SetCalc.gain(a.got, b.got);
					// a big gain is judged in proportion: +300% against +320% is the same advice
					if (Math.abs(got - expected) > Math.max(GAIN_POINTS, expected * 0.08))
					{
						off.add(String.format("%s to %s on %d: %+.0f%%, calculator %+.0f%%", a.set, b.set, a.npc, got, expected));
					}
				}
			}
		}
		assertTrue(off.size() + " off: " + off, off.isEmpty());
	}

	/** Dharok's whole set against an Abyssal demon, with Piety and a super combat: the calculator's figures at 99, 50 and 10 hitpoints. */
	@Test
	public void dharoksSetGrowsWithTheHitpointsLost() throws IOException
	{
		StyleData data = new StyleData();
		double[][] cases = {{99, 3.12754}, {50, 4.58398}, {10, 5.82363}};
		for (double[] k : cases)
		{
			SetCalc.Loadout l = new SetCalc.Loadout();
			l.weaponName = "Dharok's greataxe";
			l.category = "Axe";
			l.speed = 7;
			l.styles = data.styles(4718);
			l.stab = 43;
			l.slash = 150;
			l.crush = 142;
			l.strength = 156;
			l.dharokSet = true;
			SetCalc.Player p = player("melee");
			p.hitpoints = (int) k[0];
			double got = SetCalc.perTick(l, p, data.monster(415).target(), false);
			assertTrue("at " + k[0] + " hitpoints: " + got + ", calculator " + k[1], Math.abs(got - k[1]) <= FIGURE * k[1]);
		}
		// without the whole set it is an ordinary axe
		SetCalc.Loadout plain = new SetCalc.Loadout();
		plain.weaponName = "Dharok's greataxe";
		plain.category = "Axe";
		assertTrue(SetCalc.plain(plain));
	}

	@Test
	public void anIdTheTableDoesNotListIsFoundByName() throws IOException
	{
		StyleData data = new StyleData();
		// Dagannoth Prime in Deadman Mode: the OSRS Wiki lists the id in a version the calculator's data does not have
		org.junit.Assert.assertNull(data.monster(12442));
		org.junit.Assert.assertNotNull(data.monster(12442, "Dagannoth Prime"));
		org.junit.Assert.assertEquals("Dagannoth Prime", data.monster(12442, "Dagannoth Prime").name);
		org.junit.Assert.assertNull(data.monster(12442, "No such monster"));
		org.junit.Assert.assertSame(data.monster(415), data.monster(415, "Something else"));
		// the Hellhound of the Catacombs of Kourend has a row of its own now, with the numbers of its version
		org.junit.Assert.assertEquals(data.monster(104).level, data.monster(7256).level);
		org.junit.Assert.assertSame(data.monster(7256), data.monster(7256, "Hellhound"));
	}

	@Test
	public void anotherClassIsComparedWithItsOwnBestPrayer()
	{
		// Rigour on, a bow in hand: the melee weapon in the inventory is counted with Piety
		SetCalc.Player rigour = player("ranged");
		SetCalc.Player melee = SetCalc.withPrayerOf(rigour, StyleCalc.MELEE);
		assertEquals(120, melee.prayerAttack);
		assertEquals(123, melee.prayerStrength);
		// the class already prayed for is left as it is
		assertSame(rigour, SetCalc.withPrayerOf(rigour, StyleCalc.RANGED));
		// no attack prayer on: none is added to the other class either
		SetCalc.Player none = new SetCalc.Player();
		assertSame(none, SetCalc.withPrayerOf(none, StyleCalc.MELEE));
		// without the scrolls and at a lower level, the best the player has
		SetCalc.Player low = player("melee");
		low.prayerLevel = 50;
		low.rigour = false;
		low.deadeye = false;
		assertEquals(115, SetCalc.withPrayerOf(low, StyleCalc.RANGED).prayerRanged);
		low.augury = false;
		low.vigour = false;
		assertEquals(115, SetCalc.withPrayerOf(low, StyleCalc.MAGIC).prayerMagic);
		// Prayer 99 without the Knight Waves: the two single melee prayers, not Piety
		SetCalc.Player noWaves = player("ranged");
		noWaves.piety = false;
		noWaves.chivalry = false;
		assertEquals(115, SetCalc.withPrayerOf(noWaves, StyleCalc.MELEE).prayerAttack);
		assertEquals(115, SetCalc.withPrayerOf(noWaves, StyleCalc.MELEE).prayerStrength);
		noWaves.chivalry = true;
		assertEquals(118, SetCalc.withPrayerOf(noWaves, StyleCalc.MELEE).prayerStrength);
	}

	/** The Ancient Magicks on autocast with a Kodai wand, against an Abyssal demon: the calculator's figures. */
	@Test
	public void ancientSpellsAreCloseToTheCalculator() throws IOException
	{
		StyleData data = new StyleData();
		double[][] cases = {{31, 1.79288}, {34, 2.18717}, {36, 2.48325}, {37, 2.97714}, {39, 3.17481}, {42, 3.66914},
			{43, 3.76804}, {44, 3.96584}, {45, 4.06475}, {46, 4.16366}};
		for (double[] k : cases)
		{
			SetCalc.Loadout l = new SetCalc.Loadout();
			l.weaponName = "Kodai wand";
			l.category = "Staff";
			l.magic = 157;
			l.magicDamage = 39;
			l.spell = (int) k[0];
			assertTrue("the spell has a figure", SetCalc.plain(l));
			double got = SetCalc.perTick(l, player("magic"), data.monster(415).target(), false);
			assertTrue("spell " + k[0] + ": " + got + ", calculator " + k[1], Math.abs(got - k[1]) <= FIGURE * k[1]);
		}
		assertTrue(SetCalc.harmlessBolts("Sapphire dragon bolts (e)"));
		assertFalse(SetCalc.harmlessBolts("Onyx bolts (e)"));
		assertFalse(SetCalc.harmlessBolts("Runite bolts"));
	}

	@Test
	public void weaponsWithTheirOwnRulesGetNoFigure()
	{
		SetCalc.Loadout l = new SetCalc.Loadout();
		l.category = "Polearm";
		l.weaponName = "Dragon hunter lance";
		assertTrue("the lance's rule is known", SetCalc.plain(l));
		l.category = "Axe";
		l.weaponName = "Soulreaper axe";
		assertFalse(SetCalc.plain(l));
		l.category = "Staff";
		l.weaponName = "Kodai wand";
		assertFalse(SetCalc.plain(l));
		l.category = "Crossbow";
		l.weaponName = "Zaryte crossbow";
		l.boltEffect = true;
		assertFalse(SetCalc.plain(l));
		l.boltEffect = false;
		assertTrue(SetCalc.plain(l));
		l.category = "Powered Staff";
		l.weaponName = "Tumeken's shadow";
		assertTrue(SetCalc.plain(l));
	}

	@Test
	public void gainIsUnknownWithoutBothFigures()
	{
		assertTrue(Double.isNaN(SetCalc.gain(-1, 5)));
		assertTrue(Double.isNaN(SetCalc.gain(5, -1)));
		assertEquals(50, SetCalc.gain(4, 6), 1e-9);
	}

	/** Ruby and diamond bolts (e) at several healths of a few monsters, against the calculator (bolts.tsv). */
	@Test
	public void rubyAndDiamondBoltsFollowTheMonstersHealth() throws IOException
	{
		StyleData data = new StyleData();
		List<String> off = new ArrayList<>();
		int n = 0;
		try (BufferedReader r = new BufferedReader(new InputStreamReader(
			SetCalcTest.class.getResourceAsStream("bolts.tsv"), StandardCharsets.UTF_8)))
		{
			String line;
			while ((line = r.readLine()) != null)
			{
				if (line.isEmpty() || line.startsWith("#"))
				{
					continue;
				}
				String[] c = line.split("\t", -1);
				SetCalc.Loadout l = new SetCalc.Loadout();
				l.weaponName = c[2];
				l.category = "Crossbow";
				l.bolt = SetCalc.boltKind(c[1]);
				l.ranged = Integer.parseInt(c[7]);
				l.rangedStrength = Integer.parseInt(c[8]);
				l.speed = Integer.parseInt(c[9]);
				SetCalc.Player p = player("ranged");
				p.kandarinDiary = "1".equals(c[3]);
				SetCalc.Target t = data.monster(Integer.parseInt(c[4])).target();
				t.hitpointsNow = Integer.parseInt(c[6]);
				double expected = Double.parseDouble(c[10]);
				double got = SetCalc.perTick(l, p, t, false);
				n++;
				if (Math.abs(got - expected) > FIGURE * expected)
				{
					off.add(String.format("%s, %s, %s at %s: %.3f, calculator %.3f", c[1], c[2], c[5], c[6], got, expected));
				}
			}
		}
		assertTrue(n > 500);
		assertTrue(off.size() + " of " + n + " off: " + off, off.isEmpty());
	}

	@Test
	public void theRubyEffectIsCappedLowerOnTheGemstoneCrab()
	{
		SetCalc.Player p = new SetCalc.Player();
		p.hitpoints = 99;
		SetCalc.Target t = new SetCalc.Target();
		t.hitpoints = 50000;
		assertEquals(0.06 * 100, SetCalc.withBolt(SetCalc.RUBY, false, p, t, 40, 0.0), 1e-9);
		t.name = "Gemstone Crab";
		assertEquals(0.06 * 60, SetCalc.withBolt(SetCalc.RUBY, false, p, t, 40, 0.0), 1e-9);
		assertEquals(0.06 * 66, SetCalc.withBolt(SetCalc.RUBY, true, p, t, 40, 0.0), 1e-9);
	}

	/** A melee weapon with its bonuses as in sets.tsv (the cox- sets), with its styles from the bundled table. */
	private static SetCalc.Loadout melee(StyleData data, int id, String name, String category, int speed,
		int stab, int slash, int crush, int strength)
	{
		SetCalc.Loadout l = new SetCalc.Loadout();
		l.weaponName = name;
		l.category = category;
		l.speed = speed;
		l.styles = data.styles(id);
		l.stab = stab;
		l.slash = slash;
		l.crush = crush;
		l.strength = strength;
		return l;
	}

	/**
	 * The Guardians of the Chambers of Xeric take damage from a pickaxe only: every one of their ids, any other
	 * weapon deals nothing, and so no other weapon is ever better than the pickaxe in hand (CombatCheckPlugin's
	 * betterWeapon weighs the two with SetCalc.gain, and a gain of -100% is never shown).
	 */
	@Test
	public void onlyAPickaxeHurtsTheGuardians() throws IOException
	{
		StyleData data = new StyleData();
		SetCalc.Loadout dpick = melee(data, 11920, "Dragon pickaxe", "Pickaxe", 5, 115, 112, 107, 113);
		SetCalc.Loadout rpick = melee(data, 1275, "Rune pickaxe", "Pickaxe", 5, 103, 112, 99, 100);
		SetCalc.Loadout scythe = melee(data, 22325, "Scythe of vitur", "Scythe", 5, 117, 210, 77, 138);
		SetCalc.Loadout tbow = new SetCalc.Loadout();
		tbow.weaponName = "Twisted bow";
		tbow.category = "Bow";
		tbow.speed = 6;
		tbow.ranged = 215;
		tbow.rangedStrength = 100;
		SetCalc.Loadout shadow = new SetCalc.Loadout();
		shadow.weaponName = "Tumeken's shadow";
		shadow.category = "Powered Staff";
		shadow.speed = 5;
		shadow.magic = 200;
		for (int id = 7569; id <= 7572; id++)
		{
			SetCalc.Target t = data.monster(id).target();
			double pick = SetCalc.perTick(dpick, player("melee"), t, false);
			assertTrue("the dragon pickaxe on " + id + ": " + pick, Math.abs(pick - 5.25851) <= FIGURE * 5.25851);
			double rune = SetCalc.perTick(rpick, player("melee"), t, false);
			assertTrue("the rune pickaxe on " + id + ": " + rune, Math.abs(rune - 4.37679) <= FIGURE * 4.37679);
			assertEquals(0, SetCalc.perTick(scythe, player("melee"), t, false), 0);
			assertEquals(0, SetCalc.perTick(tbow, player("ranged"), t, false), 0);
			assertEquals(0, SetCalc.perTick(shadow, player("magic"), t, false), 0);
			assertEquals(-100, SetCalc.gain(pick, SetCalc.perTick(scythe, player("melee"), t, false)), 1e-9);
			// with a pickaxe in the inventory and anything else in hand, the pickaxe is the one that can hit
			assertTrue(SetCalc.perTick(scythe, player("melee"), t, false) == 0 && rune > 0);
		}
		// the damage grows with the Mining level: (50 + Mining + the pickaxe's bonus) / 150 of each hit
		SetCalc.Player low = player("melee");
		low.mining = 60;
		SetCalc.Target guardian = data.monster(7569).target();
		assertTrue(SetCalc.perTick(dpick, low, guardian, false) < SetCalc.perTick(dpick, player("melee"), guardian, false));
		assertEquals(61, SetCalc.pickaxeBonus("dragon pickaxe"));
		assertEquals(61, SetCalc.pickaxeBonus("crystal pickaxe"));
		assertEquals(41, SetCalc.pickaxeBonus("gilded pickaxe"));
		assertEquals(1, SetCalc.pickaxeBonus("bronze pickaxe"));
		// a pickaxe is an ordinary weapon everywhere else
		assertTrue(SetCalc.perTick(dpick, player("melee"), data.monster(7584).target(), false) > 0);
	}

	/** Every id the game gives the Chambers of Xeric's monsters finds the row of the right form or part. */
	@Test
	public void everyChambersOfXericIdFindsItsRow() throws IOException
	{
		StyleData data = new StyleData();
		for (int id : new int[]{7540, 7541, 7542, 7545})
		{
			assertEquals("Tekton " + id, 155, data.monster(id).stab);
		}
		for (int id : new int[]{7543, 7544})
		{
			assertEquals("enraged Tekton " + id, 280, data.monster(id).stab);
		}
		for (int id = 7540; id <= 7545; id++)
		{
			assertTrue(data.monster(id).immune.contains("r"));
			assertEquals(5, data.monster(id).magicDivisor);
		}
		for (int id : new int[]{7551, 7554})
		{
			assertEquals("Great Olm", data.monster(id).name);
			assertEquals(3, data.monster(id).magicDivisor);
			assertEquals(1, data.monster(id).rangedDivisor);
		}
		for (int id : new int[]{7552, 7555})
		{
			assertEquals("Great Olm (Left claw)", data.monster(id).name);
			assertEquals(3, data.monster(id).magicDivisor);
			assertEquals(3, data.monster(id).rangedDivisor);
		}
		for (int id : new int[]{7550, 7553})
		{
			assertEquals("Great Olm (Right claw)", data.monster(id).name);
			assertEquals(1, data.monster(id).magicDivisor);
			assertEquals(3, data.monster(id).rangedDivisor);
		}
		// the claws dying: found by name, each its own part
		assertSame(data.monster(7550), data.monster(7556, "Great Olm (Right claw)"));
		assertSame(data.monster(7552), data.monster(7557, "Great Olm (Left claw)"));
		for (int id : new int[]{7573, 7574})
		{
			assertEquals("Lizardman shaman (Chambers of Xeric)", data.monster(id).name);
		}
		for (int id : new int[]{7584, 7585})
		{
			StyleData.Monster m = data.monster(id);
			assertEquals(3, m.meleeDivisor);
			assertTrue(m.divisionSparesFire && m.magicDefenceFromDefence && m.xerician);
		}
		assertEquals(220, data.monster(7563).level); // the large Muttadile
		assertEquals("f", data.monster(7530).immune); // Vespula: melee with a polearm only, as before
	}

	/** The Ice demon takes a third of every hit but a fire spell's and a demonbane weapon's (the calculator's figures). */
	@Test
	public void theIceDemonTakesAThirdButOfFireAndDemonbane() throws IOException
	{
		StyleData data = new StyleData();
		SetCalc.Target demon = data.monster(7585).target();
		SetCalc.Loadout kodai = new SetCalc.Loadout();
		kodai.weaponName = "Kodai wand";
		kodai.category = "Staff";
		kodai.magic = 157;
		kodai.magicDamage = 39;
		kodai.spell = 51; // Fire Surge
		double fire = SetCalc.perTick(kodai, player("magic"), demon, false);
		assertEquals(6.25518, fire, FIGURE * 6.25518);
		kodai.spell = 49; // Water Surge
		double water = SetCalc.perTick(kodai, player("magic"), demon, false);
		assertEquals(0.78337, water, FIGURE * 0.78337);
		SetCalc.Loadout ember = melee(data, 29589, "Emberlight", "Slash Sword", 4, 140, 184, 75, 84);
		assertEquals(6.64119, SetCalc.perTick(ember, player("melee"), demon, false), FIGURE * 6.64119);
		SetCalc.Loadout scythe = melee(data, 22325, "Scythe of vitur", "Scythe", 5, 117, 210, 77, 138);
		assertEquals(1.66495, SetCalc.perTick(scythe, player("melee"), demon, false), FIGURE * 1.66495);
	}

	/** A table published before the columns of the Chambers of Xeric's rules still reads, with no rule. */
	@Test
	public void aTableWithoutTheChambersOfXericColumnsStillReads() throws IOException
	{
		StyleData old = new StyleData("11920\tPickaxe\n", "Pickaxe\tSpike:stab:Accurate;Smash:crush:Aggressive\n",
			"7584\tIce demon\t160\t70\t70\t110\t390\t40\t140\t140\t140\t2\t390\t0\t\t\td\t115\t140\tfire\t150\n");
		StyleData.Monster m = old.monster(7584);
		assertEquals(1, m.meleeDivisor);
		assertEquals(1, m.rangedDivisor);
		assertEquals(1, m.magicDivisor);
		assertFalse(m.divisionSparesFire || m.divisionKeepsOne || m.magicDefenceFromDefence || m.xerician);
		SetCalc.Target t = m.target();
		assertTrue(SetCalc.perTick(melee(old, 11920, "Dragon pickaxe", "Pickaxe", 5, 115, 112, 107, 113),
			player("melee"), t, false) > 0);
	}

	/**
	 * The Deathly rangers of a Challenge Mode raid of 24 (2026-10-14): read as a normal raid, Tumeken's shadow comes
	 * out ahead of the Twisted bow, which is the switch the plugin called for while the Challenge Mode varbit read 0;
	 * with Challenge Mode the Twisted bow is ahead, as in the calculator.
	 */
	@Test
	public void theDeathlyRangersOfAChallengeModeRaidTakeTheTwistedBow() throws IOException
	{
		Map<String, Double> got = new HashMap<>();
		for (Case k : cases())
		{
			if (k.npc == 7559)
			{
				got.put(k.set, k.got);
			}
		}
		assertTrue(got.get("cox24-shadow") > got.get("cox24-tbow"));
		assertTrue(got.get("cox24cm-tbow") > got.get("cox24cm-shadow") * 1.15);
	}
}
