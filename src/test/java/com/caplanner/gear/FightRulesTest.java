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
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;

/**
 * The fights with a rule of their own in the OSRS Wiki calculator, against its own code (tools/gen_rules.py,
 * rules_sets.tsv): the Nightmare's totems, the Hueycoatl's tail, the monsters every attack of a class lands at the max
 * hit on (the Royal Titans' elementals among them), the Corporeal Beast, Araxxor's enrage and the Twinflame staff; and
 * no ranged figure on the Royal Titans.
 */
public class FightRulesTest
{
	@Test
	public void figuresAreTheCalculators() throws IOException
	{
		assertTrue(SetCalcTest.figuresOf("rules_sets.tsv").size() > 200);
	}

	/** The cases of one monster at one health (0 for any), by set. */
	private static Map<String, SetCalcTest.Case> on(int npc, int health) throws IOException
	{
		Map<String, SetCalcTest.Case> cases = new HashMap<>();
		for (SetCalcTest.Case k : SetCalcTest.cases("rules_sets.tsv"))
		{
			if (k.npc == npc && (health == 0 || k.health == health))
			{
				cases.put(k.set, k);
			}
		}
		return cases;
	}

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
	 * The Nightmare's totems take magic twice, on every id of the four totems: Tumeken's shadow over the Scythe, which
	 * the plugin asked for before (it had only the 9435 row and no rule).
	 */
	@Test
	public void theTotemsTakeMagicTwice() throws IOException
	{
		StyleData data = new StyleData();
		for (int id : new int[]{9434, 9435, 9437, 9438, 9440, 9441, 9443, 9444})
		{
			assertEquals(2, data.monster(id).magicMultiplier);
			assertEquals(1, data.monster(id).meleeMultiplier);
			Map<String, SetCalcTest.Case> totem = on(id, 0);
			first(totem, "shadow", "scythe");
			first(totem, "sang", "scythe");
		}
	}

	/**
	 * The Hueycoatl's tail limits each hit to a random 0 to 4 (9 for crush with crush the highest bonus, where a miss
	 * deals 1): the Glacial temotli over the Dragon hunter lance, which the plugin asked for before; the broken tail
	 * (14015) has no limit.
	 */
	@Test
	public void theHueycoatlsTailLimitsEachHit() throws IOException
	{
		Map<String, SetCalcTest.Case> tail = on(14014, 0);
		first(tail, "temotli", "lance");
		first(tail, "inquisitors-mace", "scythe");
		Map<String, SetCalcTest.Case> broken = on(14015, 0);
		first(broken, "lance", "temotli");
		assertTrue(broken.get("scythe").got > 2 * tail.get("scythe").got);
		// with crush the highest bonus a miss deals 1; the limits
		assertEquals(1, SetCalc.limitedMean(0, 9, true), 1e-9);
		assertEquals(1, SetCalc.limitedMean(1, 9, true), 1e-9);
		assertEquals(0.9, SetCalc.limitedMean(1, 9, false), 1e-9);
		assertEquals((1 + 1 + 2 + 2 + 2) / 5.0, SetCalc.limitedMean(2, 4, true), 1e-9);
		assertEquals(2, SetCalc.limitedMean(50, 4, false), 1e-9);
	}

	/**
	 * Every attack of a class lands at the max hit on some monsters: magic on the Royal Titans' elementals (the
	 * Sanguinesti staff's 8 more included: the staff over Tumeken's shadow, the other way before).
	 */
	@Test
	public void theElementalsTakeTheMaxHitOfMagic() throws IOException
	{
		StyleData data = new StyleData();
		assertTrue(data.monster(14150).alwaysMaxMagic && data.monster(14151).alwaysMaxMagic);
		assertFalse(data.monster(14150).alwaysMaxMelee || data.monster(14150).alwaysMaxRanged);
		for (int id : new int[]{14150, 14151})
		{
			Map<String, SetCalcTest.Case> elemental = on(id, 0);
			first(elemental, "sang", "shadow");
			first(elemental, "shadow", "scythe");
		}
		// with a low magic attack bonus a cast lands less often: bonus / 100 + 0.3, 45% more in the Void mage set
		Map<String, SetCalcTest.Case> fire = on(14150, 0);
		assertTrue(data.monster(14150).titanElemental);
		assertTrue(fire.get("twinflame-fire-wave-in-melee-armour").got < fire.get("twinflame-fire-wave").got / 2);
		assertTrue(fire.get("twinflame-fire-wave-in-melee-armour").expected < fire.get("twinflame-fire-wave").expected / 2);
		assertTrue(data.monster(14180).alwaysMaxMelee && data.monster(14180).alwaysMaxRanged && data.monster(14180).alwaysMaxMagic);
		assertTrue(data.monster(11710).alwaysMaxMelee);
		assertTrue(data.monster(11711).alwaysMaxRanged);
		assertTrue(data.monster(11709).alwaysMaxMagic);
		assertTrue(data.monster(11758).alwaysMaxMelee);
	}

	/**
	 * The Corporeal Beast halves each hit of ranged too, and of melee but a corpbane weapon's stab; magic is whole. A ruby
	 * bolt's effect is not halved, a diamond bolt's is (the calculator's order).
	 */
	@Test
	public void theCorporealBeastHalvesRangedToo() throws IOException
	{
		Map<String, SetCalcTest.Case> corp = on(319, 0);
		first(corp, "fang", "scythe");
		first(corp, "fang", "tbow");
		// the Zamorakian hasta is a spear: its stab is whole, and over its own slash and crush
		first(corp, "fang", "hasta");
		SetCalc.Target t = new SetCalc.Target();
		t.name = "Corporeal Beast";
		assertTrue(SetCalc.corpHalves(t, StyleCalc.RANGED, false));
		assertTrue(SetCalc.corpHalves(t, StyleCalc.MELEE, false));
		assertFalse(SetCalc.corpHalves(t, StyleCalc.MELEE, true));
		assertFalse(SetCalc.corpHalves(t, StyleCalc.MAGIC, false));
		t.name = "General Graardor";
		assertFalse(SetCalc.corpHalves(t, StyleCalc.RANGED, false));
	}

	/** Araxxor from a quarter of his hitpoints: 35 more Defence, 28 more Magic, 31 more Ranged (255 of 1020 and below). */
	@Test
	public void araxxorEnragesAtAQuarter() throws IOException
	{
		StyleData data = new StyleData();
		StyleData.Monster araxxor = data.monster(13668);
		DefenceDrains.Levels full = CoxScaling.unscaled(araxxor);
		assertEquals(full.defence, MonsterLevels.enraged(araxxor, full, 256).defence);
		assertEquals(full.defence + 35, MonsterLevels.enraged(araxxor, full, 255).defence);
		assertEquals(full.magic + 28, MonsterLevels.enraged(araxxor, full, 255).magic);
		assertEquals(full.defence, MonsterLevels.enraged(araxxor, full, 0).defence);
		StyleData.Monster graardor = data.monster(2215);
		assertEquals(graardor.level, MonsterLevels.enraged(graardor, CoxScaling.unscaled(graardor), 1).defence);
		// enraged he takes less from melee, by the calculator and by the plugin
		Map<String, SetCalcTest.Case> calm = on(13668, 256);
		Map<String, SetCalcTest.Case> enraged = on(13668, 255);
		assertTrue(calm.get("scythe").expected > enraged.get("scythe").expected);
		assertTrue(calm.get("scythe").got > enraged.get("scythe").got);
	}

	/** The Twinflame staff's bolts, blasts and waves hit twice (four tenths more), every 6 ticks. */
	@Test
	public void theTwinflameStaffHitsTwice() throws IOException
	{
		Map<String, SetCalcTest.Case> giant = on(7261, 0);
		assertNotNull(giant.get("twinflame-fire-wave"));
		// a wave hits twice, a surge once: the wave deals more than its max alone would say
		assertTrue(giant.get("twinflame-fire-wave").got > giant.get("twinflame-fire-surge").got);
	}

	/**
	 * The Royal Titans: their ranged accuracy is six times while they stand out of melee distance, which the plugin
	 * cannot tell, so ranged gets no figure on them (and no weapon is pointed at with a bow in hand, nor a bow).
	 */
	@Test
	public void theRoyalTitansGetNoRangedFigure() throws IOException
	{
		StyleData data = new StyleData();
		SetCalc.Loadout tbow = new SetCalc.Loadout();
		tbow.weaponName = "Twisted bow";
		tbow.category = "Bow";
		tbow.speed = 6;
		tbow.ranged = 215;
		tbow.rangedStrength = 100;
		SetCalc.Loadout fang = new SetCalc.Loadout();
		fang.weaponName = "Osmumten's fang";
		fang.category = "Stab Sword";
		fang.speed = 5;
		fang.styles = data.styles(26219);
		fang.stab = 105 + 150;
		fang.strength = 103 + 80;
		SetCalc.Player p = new SetCalc.Player();
		p.attack = 118;
		p.strength = 118;
		p.ranged = 112;
		for (int id : new int[]{12596, 14147})
		{
			SetCalc.Target t = data.monster(id).target();
			assertEquals(-1, SetCalc.perTick(tbow, p, t, false), 0);
			assertTrue(SetCalc.perTick(fang, p, t, false) > 0);
			assertTrue(Double.isNaN(SetCalc.gain(SetCalc.perTick(tbow, p, t, false), SetCalc.perTick(fang, p, t, false))));
		}
	}

	/**
	 * The Tormented Demon, every id: its fire shield takes a fifth off each hit (at least 1) of anything but a
	 * demonbane weapon or an abyssal weapon in melee, and half of the attacks are counted as meeting it without defence,
	 * every one of them landing, in every class (the plugin does not read yet which it is,
	 * until it does). The calculator has no such phase: each case is the mean, style by style, of its phases
	 * "Shielded" and "Shielded (Defenceless)" (tools/gen_rules.py). The Emberlight over the Scythe and the Scorching bow
	 * over the Twisted bow, where the plugin asked for the other two before; the abyssal weapons take no shield.
	 */
	@Test
	public void theTormentedDemonsShieldAndHalfItsAttacksWithoutDefence() throws IOException
	{
		for (int id = 13599; id <= 13602; id++)
		{
			Map<String, SetCalcTest.Case> demon = on(id, 0);
			assertEquals(17, demon.size());
			for (SetCalcTest.Case k : demon.values())
			{
				assertEquals(k.set + " on " + id, k.expected, k.got, 0.001 * k.expected);
			}
			first(demon, "emberlight", "scythe");
			first(demon, "scorching", "tbow");
			first(demon, "tentacle", "rapier");
			first(demon, "abyssal-dagger", "scythe");
		}
		StyleData data = new StyleData();
		SetCalc.Target demon = data.monster(13599).target();
		assertTrue(demon.fireShield);
		assertEquals(0.5, demon.defencelessShare, 0);
		assertEquals(0.5, Weapons.defencelessShare("Tormented Demon"), 0);
		assertEquals(0, data.monster(415).target().defencelessShare, 0);
		// half of the attacks land without a roll: (chance + 1) / 2
		assertEquals(0.7, SetCalc.withDefenceless(demon, 0.4), 1e-12);
		SetCalc.Player p = new SetCalc.Player();
		SetCalc.Loadout scythe = new SetCalc.Loadout();
		scythe.weaponName = "Scythe of vitur";
		scythe.category = "Scythe";
		assertEquals(4, SetCalc.hitScale(scythe, p, demon, StyleCalc.MELEE).numerator);
		assertEquals(5, SetCalc.hitScale(scythe, p, demon, StyleCalc.MELEE).divisor);
		SetCalc.Loadout ember = new SetCalc.Loadout();
		ember.weaponName = "Emberlight";
		ember.category = "Slash Sword";
		assertEquals(1, SetCalc.hitScale(ember, p, demon, StyleCalc.MELEE).divisor);
		SetCalc.Loadout whip = new SetCalc.Loadout();
		whip.weaponName = "Abyssal whip";
		whip.category = "Whip";
		assertEquals(1, SetCalc.hitScale(whip, p, demon, StyleCalc.MELEE).divisor);
		// a hit of 1 stays 1 under the shield, 3 becomes 2
		assertEquals(1, SetCalc.landed(1, 1, demon, SetCalc.FIRE_SHIELD), 1e-12);
		assertEquals(2, SetCalc.landed(3, 3, demon, SetCalc.FIRE_SHIELD), 1e-12);
	}

	/** Every case of a monster within 0.1% of the calculator; the cases, by set. */
	private static Map<String, SetCalcTest.Case> calculators(int npc) throws IOException
	{
		Map<String, SetCalcTest.Case> cases = on(npc, 0);
		assertTrue("cases on " + npc, cases.size() >= 10);
		for (SetCalcTest.Case k : cases.values())
		{
			assertEquals(k.set + " on " + npc, k.expected, k.got, 0.001 * k.expected);
		}
		return cases;
	}

	/**
	 * Araxxor's Acidic and Mirrorback araxytes (by the name, as the calculator has it): every attack lands at the max
	 * hit with the Noxious halberd, with a crush style while crush is the highest attack bonus worn, and with a crossbow
	 * while ranged is (OSRS Wiki: "always max hit ... crush or heavy Ranged weapon ... or the noxious halberd"). The
	 * Inquisitor's mace and the halberd over the blowpipe, which the plugin asked for before; a crossbow in melee armour,
	 * where ranged is not the highest bonus, is counted as usual.
	 */
	@Test
	public void theAraxytesTakeTheMaxHitFromCrushCrossbowsAndTheHalberd() throws IOException
	{
		for (int id : new int[]{13675, 13671})
		{
			Map<String, SetCalcTest.Case> araxyte = calculators(id);
			first(araxyte, "inquisitors-mace", "blowpipe");
			first(araxyte, "noxious-halberd", "blowpipe");
			first(araxyte, "noxious-halberd", "scythe");
			first(araxyte, "zcb-runite", "tbow");
			first(araxyte, "zcb-runite", "zcb-in-melee-armour");
		}
		assertEquals("crush", SetCalc.highestBonus(129, 72, 215, -48, -55));
		assertEquals("ranged", SetCalc.highestBonus(-15, -16, -15, 273, -29));
		// a tie is the earlier of slash, stab, crush, ranged, magic (the calculator's order)
		assertEquals("slash", SetCalc.highestBonus(50, 50, 50, 50, 50));
		assertEquals("slash", SetCalc.highestBonus(40, 77, 40, 65, -65));
		// the attack style: with crush the highest bonus, every crush attack is a max hit, and Pound is the style
		StyleData data = new StyleData();
		StyleData.Monster acidic = data.monster(13675);
		StyleCalc.Input in = new StyleCalc.Input();
		in.attackLevel = 118;
		in.strengthLevel = 118;
		in.prayerAttack = 120;
		in.prayerStrength = 123;
		in.stab = 129;
		in.slash = 72;
		in.crush = 215;
		in.ranged = -48;
		in.magic = -55;
		in.strength = 166;
		in.defenceLevel = acidic.level;
		in.defenceStab = acidic.stab;
		in.defenceSlash = acidic.slash;
		in.defenceCrush = acidic.crush;
		in.araxyteMax = Weapons.araxyteMax(acidic.name);
		double crush = StyleCalc.expectedHit(in, StyleCalc.CRUSH, "Aggressive");
		assertEquals(StyleCalc.chanceAndMax(in, StyleCalc.CRUSH, "Aggressive")[1], crush, 0);
		assertTrue(crush > StyleCalc.expectedHit(in, StyleCalc.STAB, "Aggressive"));
		in.araxyteMax = false;
		assertTrue(StyleCalc.expectedHit(in, StyleCalc.CRUSH, "Aggressive") < crush);
	}

	/**
	 * The Ruptura araxyte takes the max hit as the Acidic and the Mirrorback do: the OSRS Wiki has the same sentence on the
	 * three pages ("Players will always max hit when attacking them with a crush or heavy Ranged weapon ... or the noxious
	 * halberd"), while the calculator has the rule for the two others only (PlayerVsNPCCalc.ts, by the name). The wiki
	 * decides the mechanics: with the Acidic's levels, bonuses and size, every one of the Acidic's cases
	 * on the Ruptura is the calculator's figure for the Acidic, within 0.1%.
	 */
	@Test
	public void theRupturaAraxyteTakesTheMaxHitToo() throws IOException
	{
		assertTrue(Weapons.araxyteMax("Ruptura Araxyte"));
		assertTrue(new StyleData().monster(13673).target().araxyteMax);
		int n = 0;
		for (SetCalcTest.Case k : SetCalcTest.cases("rules_sets.tsv", npc -> npc == 13675 ? 13673 : npc))
		{
			if (k.npc == 13673)
			{
				assertEquals(k.set + " on the Ruptura", k.expected, k.got, 0.001 * k.expected);
				n++;
			}
		}
		assertTrue("cases", n >= 10);
	}

	/**
	 * Every attack lands on the Spawn of the Abyssal Sire, in every class (the calculator's GUARANTEED_ACCURACY_MONSTERS),
	 * on both of the ids the OSRS Wiki gives it (the calculator lists the first).
	 */
	@Test
	public void everyAttackLandsOnTheSpawn() throws IOException
	{
		StyleData data = new StyleData();
		for (int id : new int[]{5916, 5917})
		{
			calculators(id);
			assertTrue(data.monster(id).everyAttackLands);
		}
	}

	/**
	 * Scurrius's Giant rats and the Flowers of Hespori and of A Night at the Theatre die in one hit from any attack (the
	 * calculator's ONE_HIT_MONSTERS; OSRS Wiki: "always die in one hit"): every attack deals their hitpoints, so the
	 * faster weapon is the better one: the rapier over the Scythe, the blowpipe over both.
	 */
	@Test
	public void theRatsAndTheFlowersDieInOneHit() throws IOException
	{
		StyleData data = new StyleData();
		for (int id : new int[]{7223, 8584, 11193})
		{
			Map<String, SetCalcTest.Case> one = calculators(id);
			first(one, "rapier", "scythe");
			first(one, "blowpipe", "rapier");
			assertTrue(data.monster(id).oneHit);
		}
		assertTrue(!data.monster(415).oneHit);
		// every attack style ties there: no style is ever pointed at
		StyleCalc.Input in = new StyleCalc.Input();
		in.attackLevel = 118;
		in.strengthLevel = 118;
		in.stab = 100;
		in.slash = 50;
		in.strength = 90;
		in.oneHit = true;
		assertEquals(StyleCalc.expectedHit(in, StyleCalc.STAB, "Accurate"), StyleCalc.expectedHit(in, StyleCalc.SLASH, "Aggressive"), 0);
		assertTrue(StyleCalc.expectedHit(in, StyleCalc.STAB, "Accurate") > 0);
	}

	/** The Hueycoatl's head in each of its ids (14009, 14010, 14013) is the head, not the body found by the name. */
	@Test
	public void theHueycoatlsHeadInEachId() throws IOException
	{
		StyleData data = new StyleData();
		for (int id : new int[]{14009, 14010, 14013})
		{
			assertEquals(7, data.monster(id).size);
			assertEquals(2500, data.monster(id).hitpoints);
		}
		assertEquals(1, data.monster(14017).size);
		assertTrue(on(14013, 0).get("scythe").got > 0);
	}
}
