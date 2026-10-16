/*
 * Copyright (c) 2026, GuimGod
 * All rights reserved. Licensed under the BSD 2-Clause License (see LICENSE).
 */
package com.caplanner.gear;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Assume;
import org.junit.Test;

/**
 * The monster table's rules (tools/gen_data.py), against the OSRS Wiki DPS calculator's own code (tools/gen_table.py,
 * table_sets.tsv) and, where the OSRS Wiki decides what can hurt a monster, against the wiki: what each hit is divided
 * by on the Kraken, the Cave kraken, the Slagilith and the ogres; the leafy monsters; the vampyres of tiers 2 and 3;
 * Dusk and the Cyclopes of the Warriors' Guild; the Doom of Mokhaiotl's shield and burrow; Fareed and the Fire Warrior
 * of Lesarkus; Yama's magic defence; the Respiratory system, the Cave kraken's melee and Vespula's halberd; and a row
 * for every id the game gives a monster, with the numbers of its own version.
 */
public class MonsterTableTest
{
	private static final String FILE = "table_sets.tsv";

	/** The cases of table_sets.tsv on one row, by set. */
	private static Map<String, SetCalcTest.Case> on(int npc) throws IOException
	{
		Map<String, SetCalcTest.Case> cases = new HashMap<>();
		for (SetCalcTest.Case k : SetCalcTest.cases(FILE))
		{
			if (k.npc == npc)
			{
				cases.put(k.set, k);
			}
		}
		assertTrue("cases on " + npc, !cases.isEmpty());
		return cases;
	}

	/** {@code better} deals more than {@code worse} by the calculator and by the plugin. */
	private static void first(Map<String, SetCalcTest.Case> cases, String better, String worse)
	{
		SetCalcTest.Case a = cases.get(better);
		SetCalcTest.Case b = cases.get(worse);
		assertNotNull(better, a);
		assertNotNull(worse, b);
		assertTrue("calculator: " + better + " over " + worse + " on " + a.npc + " (" + a.expected + ", " + b.expected + ")",
			a.expected > b.expected);
		assertTrue("plugin: " + better + " over " + worse + " on " + a.npc + " (" + a.got + ", " + b.got + ")", a.got > b.got);
	}

	/** The set deals nothing by the calculator and by the plugin. */
	private static void nothing(Map<String, SetCalcTest.Case> cases, String set)
	{
		SetCalcTest.Case k = cases.get(set);
		assertNotNull(set, k);
		assertEquals("calculator: " + set + " on " + k.npc, 0, k.expected, 0);
		assertEquals("plugin: " + set + " on " + k.npc, 0, k.got, 0);
	}

	/** Every case within 0.1% of the calculator. */
	@Test
	public void figuresAreTheCalculators() throws IOException
	{
		assertTrue(SetCalcTest.figuresOf(FILE).size() > 250);
	}

	/**
	 * Flat armour takes its value off each melee and ranged hit that lands, down to 0 (the calculator's
	 * flatAddTransformer with a minimum of 0, the last of the monster's rules), the ruby and diamond bolts' effects
	 * too: the Drake (2), the Veiled kraken (15), an Elite Black Knight (7), an Armoured zombie (1), a Body Golem (2),
	 * a Shadow Wyrm (2), a Giant rockslug (2), a Runite Golem (4), the Eclipse Moon (6). And Zulrah's cap takes the
	 * bolts' effects like any hit.
	 */
	@Test
	public void flatArmourTakesAHitDownToNothing() throws IOException
	{
		List<String> off = new ArrayList<>();
		int n = 0;
		for (SetCalcTest.Case k : SetCalcTest.cases(FILE))
		{
			if (k.npc == 8612 || k.npc == 15576 || k.npc == 13463 || k.npc == 12730 || k.npc == 10691 || k.npc == 10399
				|| k.npc == 7392 || k.npc == 6600 || k.npc == 13012 || k.npc == 2042)
			{
				n++;
				if (Math.abs(k.got - k.expected) > 0.001 * k.expected)
				{
					off.add(String.format("%s on %d: %.5f, calculator %.5f", k.set, k.npc, k.got, k.expected));
				}
			}
		}
		assertTrue(n > 80);
		assertTrue(off.size() + " of " + n + " off: " + off, off.isEmpty());
		// hits of 0 to 3 that land are raised to 1, 1, 2, 3; flat armour 2 leaves 0, 0, 0, 1
		SetCalc.Target t = new SetCalc.Target();
		t.flatArmour = 2;
		assertEquals(0.25, SetCalc.landed(0, 3, t), 1e-9);
		// a negative flat armour adds to each hit
		t.flatArmour = -2;
		assertEquals((3 + 3 + 4 + 5) / 4.0, SetCalc.landed(0, 3, t), 1e-9);
	}

	/**
	 * The Fragment of Seren takes a hit above 22 as a random 22, 23 or 24, never more than the hit (the calculator's
	 * linearMinTransformer(2, 22); OSRS Wiki: "capped at 24 ... reduced to 22-24 damage"), on every id: the Scythe,
	 * whose hits are smaller, over Tumeken's shadow, which the plugin asked for before.
	 */
	@Test
	public void theFragmentOfSerenTakesAHitAbove22As22To24() throws IOException
	{
		for (int id : new int[]{8917, 8918, 8919, 8920})
		{
			Map<String, SetCalcTest.Case> seren = on(id);
			for (SetCalcTest.Case k : seren.values())
			{
				assertEquals(k.set + " on " + id, k.expected, k.got, 0.001 * k.expected);
			}
			first(seren, "scythe", "shadow");
			first(seren, "scythe", "sang");
		}
		SetCalc.Target t = new StyleData().monster(8917).target();
		assertEquals(22, t.damageCap);
		assertEquals(22, SetCalc.landed(22, 22, t), 1e-9);
		assertEquals((22 + 23 + 23) / 3.0, SetCalc.landed(23, 23, t), 1e-9);
		assertEquals(23, SetCalc.landed(60, 60, t), 1e-9);
		assertEquals(0, new StyleData().monster(415).target().damageCap);
	}

	/** The Kraken and the Cave kraken take a seventh of each ranged hit (at least 1): magic over the blowpipe. */
	@Test
	public void theKrakensTakeASeventhOfRanged() throws IOException
	{
		for (int id : new int[]{494, 492})
		{
			Map<String, SetCalcTest.Case> kraken = on(id);
			first(kraken, "shadow", "blowpipe");
			first(kraken, "trident", "blowpipe");
			first(kraken, "sang", "tbow");
		}
		StyleData data = new StyleData();
		for (int id : new int[]{494, 496, 492, 493})
		{
			StyleData.Monster m = data.monster(id);
			assertEquals(7, m.rangedDivisor);
			assertEquals(1, m.meleeDivisor);
			assertEquals(1, m.magicDivisor);
			assertTrue(m.divisionKeepsOne);
		}
	}

	/** The Slagilith takes a third of every hit but a pickaxe's: the dragon pickaxe over the blowpipe and the Shadow. */
	@Test
	public void theSlagilithTakesAThirdButOfAPickaxe() throws IOException
	{
		for (int id : new int[]{1362, 1364})
		{
			Map<String, SetCalcTest.Case> slagilith = on(id);
			first(slagilith, "dragon-pickaxe", "blowpipe");
			first(slagilith, "dragon-pickaxe", "shadow");
			first(slagilith, "dragon-pickaxe", "scythe");
		}
	}

	/**
	 * The ogres take a quarter of every hit; half of Crumble Undead's; all of a Comp ogre bow's with brutal arrows
	 * (the calculator's applyNpcTransforms).
	 */
	@Test
	public void theOgresTakeAQuarterButOfACompOgreBowWithBrutalArrows() throws IOException
	{
		StyleData data = new StyleData();
		SetCalc.Player p = new SetCalc.Player();
		for (int id : new int[]{866, 870, 872, 882})
		{
			SetCalc.Target t = data.monster(id).target();
			SetCalc.Loadout bow = new SetCalc.Loadout();
			bow.weaponName = "Comp ogre bow";
			bow.category = "Bow";
			bow.speed = 5;
			bow.ammoWorn = "Rune brutal";
			assertEquals("the Comp ogre bow with brutal arrows on " + id, 1, SetCalc.hitScale(bow, p, t, StyleCalc.RANGED).divisor);
			bow.ammoWorn = "Rune arrow";
			assertEquals(4, SetCalc.hitScale(bow, p, t, StyleCalc.RANGED).divisor);
			SetCalc.Loadout staff = new SetCalc.Loadout();
			staff.weaponName = "Ancient staff";
			staff.category = "Staff";
			staff.spell = 17; // Crumble Undead (OSRS Wiki, Varbit 276)
			assertEquals(2, SetCalc.hitScale(staff, p, t, StyleCalc.MAGIC).divisor);
			staff.spell = 51; // Fire Surge
			assertEquals(4, SetCalc.hitScale(staff, p, t, StyleCalc.MAGIC).divisor);
			SetCalc.Loadout scythe = new SetCalc.Loadout();
			scythe.weaponName = "Scythe of vitur";
			scythe.category = "Scythe";
			assertEquals(4, SetCalc.hitScale(scythe, p, t, StyleCalc.MELEE).divisor);
		}
		// and the scythe's figure is the calculator's, a quarter of each hit
		SetCalcTest.Case scythe = on(866).get("scythe");
		assertEquals(scythe.expected, scythe.got, 0.001 * scythe.expected);
	}

	/**
	 * The leafy monsters take damage from a leaf-bladed weapon, broad ammunition or Magic Dart only: with the
	 * Leaf-bladed battleaxe in hand no other weapon is ever better.
	 */
	@Test
	public void onlyLeafBladedWeaponsBroadAmmunitionOrMagicDartHurtTheLeafy() throws IOException
	{
		for (int id : new int[]{410, 411, 7405, 427, 430, 10397})
		{
			Map<String, SetCalcTest.Case> leafy = on(id);
			first(leafy, "leaf-bladed-battleaxe", "scythe");
			first(leafy, "acb-broad-bolts", "tbow");
			first(leafy, "tbow-broad-arrows", "blowpipe");
			for (String set : new String[]{"scythe", "rapier", "tbow", "blowpipe", "shadow", "sang"})
			{
				nothing(leafy, set);
			}
		}
		StyleData data = new StyleData();
		SetCalc.Target kurask = data.monster(410).target();
		SetCalc.Loadout dart = new SetCalc.Loadout();
		dart.weaponName = "Slayer's staff";
		dart.category = "Staff";
		dart.spell = 18; // Magic Dart: hurts it, and has no figure here
		assertEquals(-1, SetCalc.perTick(dart, magic(), kurask, false), 0);
		SetCalc.Loadout sword = melee(data, 11902, "Leaf-bladed sword", "Stab Sword", 4, 67, 62, 0, 50);
		assertTrue(SetCalc.perTick(sword, melee(), kurask, false) > 0);
	}

	/**
	 * No ordinary weapon hurts a vampyre of tier 3 (only the Ivandis and Blisterwood flails, the Blisterwood sickle
	 * and stake, the Hallowed flail and the Sunspear do) nor of tier 2 (silver weapons and Efaritay's aid do); tier 1
	 * takes everything.
	 */
	@Test
	public void ordinaryWeaponsDoNotHurtTheVampyresOfTiers2And3() throws IOException
	{
		for (int id : new int[]{9756, 3709, 9561, 15765, 3694})
		{
			Map<String, SetCalcTest.Case> vampyre = on(id);
			for (String set : new String[]{"scythe", "rapier", "emberlight", "tbow", "blowpipe", "shadow", "sang"})
			{
				nothing(vampyre, set);
			}
		}
		first(on(3237), "rapier", "emberlight");
		StyleData data = new StyleData();
		assertEquals(3, data.monster(9756).vampyre);
		assertEquals(2, data.monster(3694).vampyre);
		assertEquals(1, data.monster(3237).vampyre);
		assertEquals(0, data.monster(415).vampyre);
		SetCalc.Target sentinel = data.monster(9756).target();
		SetCalc.Target juvinate = data.monster(3694).target();
		SetCalc.Loadout sunspear = melee(data, 33722, "Sunspear", "Spear", 5, 100, 21, 60, 70);
		SetCalc.Loadout flail = melee(data, 33718, "Hallowed flail", "Flail", 4, 32, 68, 0, 46);
		SetCalc.Loadout silverlight = melee(data, 2402, "Silverlight", "Slash Sword", 5, 9, 14, -2, 12);
		SetCalc.Loadout rapier = melee(data, 22324, "Ghrazi rapier", "Stab Sword", 4, 100, 55, 0, 93);
		assertTrue(SetCalc.perTick(sunspear, melee(), sentinel, false) > 0);
		assertTrue(SetCalc.perTick(flail, melee(), sentinel, false) > 0);
		assertEquals(0, SetCalc.perTick(silverlight, melee(), sentinel, false), 0);
		assertTrue(SetCalc.perTick(silverlight, melee(), juvinate, false) > 0);
		assertEquals(0, SetCalc.perTick(rapier, melee(), juvinate, false), 0);
		rapier.efaritay = true;
		assertTrue("Efaritay's aid on a tier 2 vampyre", SetCalc.perTick(rapier, melee(), juvinate, false) > 0);
		assertEquals("not on a tier 3 one", 0, SetCalc.perTick(rapier, melee(), sentinel, false), 0);
		SetCalc.Loadout crossbow = new SetCalc.Loadout();
		crossbow.weaponName = "Rune crossbow";
		crossbow.category = "Crossbow";
		crossbow.speed = 6;
		crossbow.ranged = 90;
		crossbow.ammo = "Silver bolts";
		crossbow.ammoWorn = "Silver bolts";
		assertTrue(SetCalc.perTick(crossbow, ranged(), juvinate, false) > 0);
		assertEquals(0, SetCalc.perTick(crossbow, ranged(), sentinel, false), 0);
	}

	/**
	 * The Respiratory systems of the Abyssal Sire: every hit that lands is at least half the max hit, and a demonbane
	 * weapon's hit that lands kills one (the calculator's, by the name; OSRS Wiki: "always hit at least 50% of their
	 * maximum hit ... demonbane weapons (other than holy water) will instantly kill the systems on a successful hit"):
	 * the Scorching bow over the blowpipe and Tumeken's shadow, where the plugin asked for the other two before.
	 */
	@Test
	public void theRespiratorySystemTakesAtLeastHalfTheMaxAndDiesToDemonbane() throws IOException
	{
		Map<String, SetCalcTest.Case> lung = on(5914);
		assertEquals(12, lung.size());
		for (SetCalcTest.Case k : lung.values())
		{
			assertEquals(k.set, k.expected, k.got, 0.001 * k.expected);
		}
		first(lung, "scorching", "blowpipe");
		first(lung, "scorching", "shadow");
		first(lung, "blowpipe", "noxious-halberd");
	}

	/**
	 * The weapons that hurt a vampyre, as the calculator counts them on every tier (getAttackerDist, applyNpcTransforms):
	 * the Hallowed flail a quarter more accurate and a quarter more each hit, the Sunspear a quarter more accurate and a
	 * half more each hit (both vampyrebane); a silver weapon a tenth more each hit on tier 1 and at most 10 each hit on
	 * tier 2 (the calculator's limit); Efaritay's aid a tenth more each hit of those first, 15% more accuracy with a silver
	 * weapon, and on tier 2 half of each hit of any weapon that is not vampyrebane. The Sunspear over the Hallowed flail,
	 * which the plugin had a fifth ahead of it.
	 */
	@Test
	public void theVampyresOwnRulesForTheWeaponsThatHurtThem() throws IOException
	{
		for (int id : new int[]{9756, 3709, 9561, 15765, 3694, 5635, 3237, 3692})
		{
			for (SetCalcTest.Case k : on(id).values())
			{
				assertEquals(k.set + " on " + id, k.expected, k.got, 0.001 * k.expected);
			}
		}
		first(on(9756), "sunspear", "hallowed-flail");
		first(on(15765), "sunspear-efaritay", "hallowed-flail-efaritay");
		// tier 2: the flail, silver, at most 10 a hit; the Sunspear is not silver
		first(on(3694), "sunspear", "hallowed-flail");
		first(on(3694), "arclight", "silverlight");
		// Efaritay's aid on tier 2: half of each hit of the rapier, the Scythe, the Shadow
		first(on(3694), "rapier-efaritay", "silverlight-efaritay");
		first(on(5635), "shadow-efaritay", "tbow-efaritay");
		// tier 1: a tenth more with silver, and a tenth more again with Efaritay's aid
		first(on(3692), "sunspear-efaritay", "sunspear");
		first(on(3237), "sunspear", "hallowed-flail");
		// silver bolts are silver (the calculator's list has them, though its code never matches the names it gives them,
		// so they have no case): at most 10 each hit on tier 2, a tenth more each hit on tier 1, half with Efaritay's aid
		// on tier 2
		StyleData data = new StyleData();
		SetCalc.Loadout bolts = new SetCalc.Loadout();
		bolts.weaponName = "Rune crossbow";
		bolts.category = "Crossbow";
		bolts.ammo = "Silver bolts";
		SetCalc.Player p = ranged();
		SetCalc.Scale juvinate = SetCalc.hitScale(bolts, p, data.monster(3694).target(), StyleCalc.RANGED);
		assertEquals(10, juvinate.most);
		assertEquals(0, juvinate.before.length);
		SetCalc.Scale juvenile = SetCalc.hitScale(bolts, p, data.monster(3692).target(), StyleCalc.RANGED);
		assertEquals(0, juvenile.most);
		assertArrayEquals(new int[]{11, 10}, juvenile.before);
		bolts.efaritay = true;
		assertArrayEquals(new int[]{11, 10, 11, 10}, SetCalc.hitScale(bolts, p, data.monster(3692).target(), StyleCalc.RANGED).before);
		assertEquals(2, SetCalc.hitScale(bolts, p, data.monster(3694).target(), StyleCalc.RANGED).divisor);
		// a hit of 14 with the cap of 10 deals 10; 1 halved deals 0 (the calculator's division, no floor)
		assertEquals(10, SetCalc.landed(14, 14, data.monster(3694).target(), juvinate), 1e-9);
		assertEquals(0, SetCalc.landed(1, 1, data.monster(3694).target(),
			SetCalc.hitScale(bolts, p, data.monster(3694).target(), StyleCalc.RANGED)), 1e-9);
	}

	/**
	 * The awakened fights of Desert Treasure II's bosses: the game gives them the NPC ids of the fight after the quest
	 * (OSRS Wiki infoboxes), and the table has a row of their own under the negative of each id, with the numbers of
	 * the calculator's awakened version (tools/gen_data.py, tools/gen_table.py). The plugin takes it when the game's boss
	 * health display shows more hitpoints than the row after the quest has: the Leviathan and the Whisperer 2700
	 * against 900, Vardorvis 1400 against 700, Duke Sucellus 1697 against 485. On the awakened Leviathan, the Twisted
	 * bow over the blowpipe and Tumeken's shadow, which the plugin asked for before.
	 */
	@Test
	public void theAwakenedFightsAreTakenByTheBossHealthDisplay() throws IOException
	{
		StyleData data = new StyleData();
		// id, hitpoints and Defence after the quest, then awakened
		int[][] fights = {{12214, 900, 250, 2700, 287}, {12191, 485, 275, 1697, 316}, {12223, 700, 215, 1400, 268},
			{12426, 700, 215, 1400, 268}, {12204, 900, 250, 2700, 300}, {12205, 900, 250, 2700, 300}};
		for (int[] f : fights)
		{
			StyleData.Monster after = data.monster(f[0]);
			assertEquals(f[1], after.hitpoints);
			assertEquals(f[2], after.level);
			assertSame(after, data.inFight(after, 0));
			assertSame(after, data.inFight(after, f[1]));
			StyleData.Monster awake = data.inFight(after, f[3]);
			assertEquals(f[3], awake.hitpoints);
			assertEquals(f[4], awake.level);
			assertEquals(f[0], awake.id);
			assertSame(awake, data.monster(-f[0]));
			assertSame(awake, data.inFight(awake, f[3]));
			assertTrue(!data.monsters().contains(awake));
			for (SetCalcTest.Case k : on(-f[0]).values())
			{
				assertEquals(k.set + " on " + f[0] + " awakened", k.expected, k.got, 0.001 * k.expected);
			}
		}
		// any other monster is its own row whatever the display shows
		assertSame(data.monster(8061), data.inFight(data.monster(8061), 5000));
		assertNull(data.inFight(null, 2700));
		Map<String, SetCalcTest.Case> leviathan = on(-12214);
		first(leviathan, "tbow", "blowpipe");
		first(leviathan, "tbow", "shadow");
		first(on(-12191), "scythe", "emberlight");
	}

	/** The Respiratory system takes melee from a halberd only (OSRS Wiki: "magic, ranged or a halberd"). */
	@Test
	public void theRespiratorySystemTakesMeleeFromAHalberdOnly() throws IOException
	{
		StyleData data = new StyleData();
		SetCalc.Target lung = data.monster(5914).target();
		assertEquals("f", data.monster(5914).immune);
		SetCalc.Loadout halberd = melee(data, 29796, "Noxious halberd", "Polearm", 5, 80, 132, 0, 142);
		SetCalc.Loadout emberlight = melee(data, 29589, "Emberlight", "Slash Sword", 4, 63, 70, 0, 13);
		assertTrue(SetCalc.perTick(halberd, melee(), lung, false) > 0);
		assertEquals(0, SetCalc.perTick(emberlight, melee(), lung, false), 0);
		assertTrue(SetCalc.perTick(bow("Scorching bow", 124, 40), ranged(), lung, false) > 0);
	}

	/** Vorkath after the quest fights as 8061, with the numbers of the post-quest version (OSRS Wiki infobox). */
	@Test
	public void vorkathAfterTheQuestIsTheRowOf8061() throws IOException
	{
		StyleData data = new StyleData();
		StyleData.Monster vorkath = data.monster(8061);
		assertNotNull(vorkath);
		assertEquals(214, vorkath.level);
		assertEquals(240, vorkath.magic);
		assertEquals(750, vorkath.hitpoints);
		assertEquals(164, data.monster(8060).level); // in Dragon Slayer II
		Map<String, SetCalcTest.Case> cases = on(8061);
		first(cases, "blowpipe", "scythe");
		first(cases, "dhl", "scythe");
		first(cases, "dhcb", "scythe");
	}

	/**
	 * Every id the OSRS Wiki gives a version has a row with that version's numbers: the Lizardman shamans of the
	 * caves, the Dagannoths of the lighthouse and of Waterbirth, the Colosseum's Minotaur, the level 96 Araxyte.
	 */
	@Test
	public void everyIdOfAVersionHasTheRowOfThatVersion() throws IOException
	{
		StyleData data = new StyleData();
		for (int id : new int[]{6767, 7744, 7745})
		{
			assertEquals("Lizardman shaman " + id, 3, data.monster(id).size);
			first(on(id), "scythe", "rapier");
		}
		for (int id : new int[]{974, 975})
		{
			assertEquals(71, data.monster(id).level);
			assertEquals(2, data.monster(id).size);
			assertEquals(50, data.monster(id).magic);
			first(on(id), "scythe", "rapier");
		}
		assertEquals(75, data.monster(5942).level);
		assertEquals(90, data.monster(5942).crush);
		assertEquals(200, data.monster(5942).magic);
		assertEquals(65, data.monster(5943).level);
		first(on(5942), "shadow", "blowpipe");
		assertEquals(190, data.monster(12813).level);
		assertEquals(3, data.monster(12813).size);
		first(on(12813), "scythe", "blowpipe");
		assertEquals(1, data.monster(13666).size);
		first(on(13666), "rapier", "scythe");
	}

	/**
	 * Dusk and the Cyclopes of the Warriors' Guild take melee only, every id (the calculator's DUSK_IDS and
	 * WARRIORS_GUILD_CYCLOPES); Dusk's last form, 7888, has its own numbers.
	 */
	@Test
	public void duskAndTheCyclopesOfTheWarriorsGuildTakeMeleeOnly() throws IOException
	{
		StyleData data = new StyleData();
		for (int id : new int[]{7851, 7854, 7855, 7882, 7883, 7886, 7887, 7888, 7889, 2137, 2138, 2139, 2140, 2141, 2142,
			2463, 2465, 2467, 2464, 2466, 2468})
		{
			StyleData.Monster m = data.monster(id);
			assertNotNull(String.valueOf(id), m);
			assertTrue(id + ": " + m.immune, m.immune.contains("r") && m.immune.contains("g"));
		}
		StyleData.Monster last = data.monster(7888);
		assertEquals(150, last.level);
		assertEquals(6, last.size);
		assertEquals(0, last.flatArmour);
		for (int id : new int[]{7851, 7888, 2137, 2138, 2463, 2465, 2464, 2468})
		{
			Map<String, SetCalcTest.Case> cases = on(id);
			first(cases, "scythe", "rapier");
			for (String set : new String[]{"tbow", "blowpipe", "shadow", "sang"})
			{
				nothing(cases, set);
			}
		}
	}

	/**
	 * The Doom of Mokhaiotl's shield (14708) takes only demonbane, and every attack lands on it and on the Doom
	 * burrowed (14709), as the calculator counts its phases of 14707.
	 */
	@Test
	public void theDoomsShieldTakesDemonbaneOnlyAndEveryAttackLands() throws IOException
	{
		Map<String, SetCalcTest.Case> shield = on(14708);
		first(shield, "emberlight", "scythe");
		first(shield, "scorching", "tbow");
		for (String set : new String[]{"scythe", "rapier", "tbow", "blowpipe", "shadow"})
		{
			nothing(shield, set);
		}
		Map<String, SetCalcTest.Case> burrowed = on(14709);
		Map<String, SetCalcTest.Case> normal = on(14707);
		for (String set : new String[]{"scythe", "rapier", "tbow", "blowpipe", "shadow"})
		{
			assertTrue(set, burrowed.get(set).got > normal.get(set).got);
		}
		StyleData data = new StyleData();
		assertTrue(data.monster(14708).everyAttackLands && data.monster(14709).everyAttackLands);
		assertTrue(!data.monster(14707).everyAttackLands);
	}

	/** The Cave kraken takes no melee, a halberd's neither (OSRS Wiki: "Melee cannot be used, including halberds"). */
	@Test
	public void theCaveKrakenTakesNoMelee() throws IOException
	{
		StyleData data = new StyleData();
		SetCalc.Loadout tentacle = melee(data, 12006, "Abyssal tentacle", "Whip", 4, 0, 90, 0, 86);
		SetCalc.Loadout halberd = melee(data, 29796, "Noxious halberd", "Polearm", 5, 80, 132, 0, 142);
		for (int id : new int[]{492, 493})
		{
			assertEquals("M", data.monster(id).immune);
			SetCalc.Target t = data.monster(id).target();
			assertEquals(0, SetCalc.perTick(tentacle, melee(), t, false), 0);
			assertEquals(0, SetCalc.perTick(halberd, melee(), t, false), 0);
		}
		assertTrue(on(492).get("trident").got > 0);
	}

	/** The Eclipse Moon is its Regular version (flat armour 6), not its Clone special attack (4). */
	@Test
	public void theEclipseMoonIsItsRegularVersion() throws IOException
	{
		StyleData data = new StyleData();
		assertEquals(6, data.monster(13012).flatArmour);
		Map<String, SetCalcTest.Case> moon = on(13012);
		first(moon, "rapier", "scythe");
		first(moon, "inquisitors-mace", "scythe");
		// magic takes no flat armour: the figure is the calculator's
		assertEquals(moon.get("shadow").expected, moon.get("shadow").got, 0.001 * moon.get("shadow").expected);
	}

	/**
	 * Yama's magic defence is 60, as while the one it attacks uses magic (the calculator's "Tank using magic"), not
	 * the data's 0; the calculator's own id 100001 (the Contract of Glyphic Attenuation) is not a row: the game never
	 * gives it.
	 */
	@Test
	public void yamasMagicDefenceIsTheTanksUsingMagic() throws IOException
	{
		StyleData data = new StyleData();
		assertEquals(60, data.monster(14176).magic);
		assertNull(data.monster(100001));
		first(on(14176), "shadow", "sang");
	}

	/**
	 * Fareed takes magic only from a water spell (ranged from anything: OSRS Wiki, "any kind of Ranged weapon");
	 * the Fire Warrior of Lesarkus takes only ranged with Ice arrows on.
	 */
	@Test
	public void fareedTakesWaterSpellsAndTheFireWarriorIceArrows() throws IOException
	{
		Map<String, SetCalcTest.Case> fareed = on(3456);
		first(fareed, "kodai-water-surge", "kodai-fire-surge");
		first(fareed, "fang", "shadow");
		nothing(fareed, "shadow");
		nothing(fareed, "sang");
		nothing(fareed, "kodai-fire-surge");
		StyleData data = new StyleData();
		SetCalc.Loadout bow = bow("Twisted bow", 70, 20);
		bow.ammo = "Dragon arrow";
		bow.ammoWorn = "Dragon arrow";
		assertTrue(SetCalc.perTick(bow, ranged(), data.monster(3456).target(), false) > 0);
		Map<String, SetCalcTest.Case> warrior = on(3448);
		first(warrior, "msb-ice-arrows", "tbow");
		for (String set : new String[]{"scythe", "rapier", "tbow", "blowpipe", "shadow", "kodai-water-surge"})
		{
			nothing(warrior, set);
		}
	}

	/** A halberd hurts Vespula in flight (OSRS Wiki, Changes of 6 August 2025), which the calculator does not count. */
	@Test
	public void halberdsHurtVespulaInFlight() throws IOException
	{
		StyleData data = new StyleData();
		assertEquals("f", data.monster(7530).immune);
		SetCalc.Target vespula = data.monster(7530).target();
		SetCalc.Loadout halberd = melee(data, 29796, "Noxious halberd", "Polearm", 5, 80, 132, 0, 142);
		SetCalc.Loadout rapier = melee(data, 22324, "Ghrazi rapier", "Stab Sword", 4, 100, 55, 0, 93);
		assertTrue(SetCalc.perTick(halberd, melee(), vespula, false) > 0);
		assertEquals(0, SetCalc.perTick(rapier, melee(), vespula, false), 0);
	}

	/**
	 * The rules the OSRS Wiki decides and the hits divided by the monster's name, in the bundled table and in the one
	 * published (data/v1): a table made again from the calculator's lists alone fails here.
	 */
	@Test
	public void theWikisRulesAndTheDivisorsAreInBothTables() throws IOException
	{
		List<StyleData> tables = new ArrayList<>();
		tables.add(new StyleData());
		File dir = new File("data/v1");
		if (dir.isDirectory())
		{
			tables.add(new StyleData(read(dir, "weapons.tsv"), read(dir, "styles.tsv"), read(dir, "monsters.tsv")));
		}
		Assume.assumeTrue("run from the plugin's repository", tables.size() == 2);
		for (StyleData data : tables)
		{
			assertEquals("Respiratory system", "f", data.monster(5914).immune);
			assertEquals("Cave kraken", "M", data.monster(492).immune);
			assertEquals("its whirlpool", "M", data.monster(493).immune);
			assertEquals("Vespula", "f", data.monster(7530).immune);
			for (int id : new int[]{494, 496, 492, 493})
			{
				assertEquals(7, data.monster(id).rangedDivisor);
				assertTrue(data.monster(id).divisionKeepsOne);
			}
			for (int id : new int[]{1362, 1364})
			{
				StyleData.Monster m = data.monster(id);
				assertEquals(3, m.meleeDivisor);
				assertEquals(3, m.rangedDivisor);
				assertEquals(3, m.magicDivisor);
				assertTrue(m.divisionSparesPickaxe);
			}
			for (int id : new int[]{866, 867, 868, 869, 870, 871, 873, 874, 875, 876, 877, 872, 878, 879, 882})
			{
				StyleData.Monster m = data.monster(id);
				assertNotNull(String.valueOf(id), m);
				assertEquals(4, m.meleeDivisor);
				assertEquals(4, m.rangedDivisor);
				assertEquals(4, m.magicDivisor);
				assertTrue(m.ogreDivision);
			}
			assertTrue(data.monster(14708).immune.contains("b"));
			assertTrue(data.monster(3448).immune.contains("i"));
			assertTrue(data.monster(3456).immune.contains("w"));
		}
	}

	private static String read(File dir, String file) throws IOException
	{
		return new String(Files.readAllBytes(new File(dir, file).toPath()), StandardCharsets.UTF_8);
	}

	private static SetCalc.Loadout melee(StyleData data, int id, String name, String category, int speed, int stab,
		int slash, int crush, int strength)
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

	private static SetCalc.Loadout bow(String name, int ranged, int rangedStrength)
	{
		SetCalc.Loadout l = new SetCalc.Loadout();
		l.weaponName = name;
		l.category = "Bow";
		l.speed = 5;
		l.ranged = ranged;
		l.rangedStrength = rangedStrength;
		return l;
	}

	private static SetCalc.Player melee()
	{
		SetCalc.Player p = new SetCalc.Player();
		p.attack = 118;
		p.strength = 118;
		p.prayerAttack = 120;
		p.prayerStrength = 123;
		return p;
	}

	private static SetCalc.Player ranged()
	{
		SetCalc.Player p = new SetCalc.Player();
		p.ranged = 112;
		p.prayerRanged = 120;
		p.prayerRangedStrength = 123;
		return p;
	}

	private static SetCalc.Player magic()
	{
		SetCalc.Player p = new SetCalc.Player();
		p.magic = 112;
		p.prayerMagic = 125;
		p.prayerMagicDamage = 4;
		return p;
	}
}
