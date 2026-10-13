package com.caplanner.gear;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

/** The picker, with a handful of real items written out by hand (ids and bonuses as the game has them). */
public class GearTest
{
	private static final int HEAD = 0;
	private static final int WEAPON = 3;
	private static final int BODY = 4;
	private static final int SHIELD = 5;
	private static final int AMMO = 13;

	private static Gear.Stat item(int id, String name, int slot)
	{
		Gear.Stat s = new Gear.Stat();
		s.itemId = id;
		s.name = name;
		s.slot = slot;
		return s;
	}

	private static Gear.Stat whip()
	{
		Gear.Stat s = item(4151, "Abyssal whip", WEAPON);
		s.slash = 82;
		s.strength = 82;
		s.speed = 4;
		return s;
	}

	private static Gear.Stat scythe()
	{
		Gear.Stat s = item(22325, "Scythe of vitur", WEAPON);
		s.twoHanded = true;
		s.stab = 70;
		s.slash = 125;
		s.crush = 30;
		s.strength = 75;
		s.speed = 5;
		return s;
	}

	private static Gear.Stat defender()
	{
		Gear.Stat s = item(12954, "Dragon defender", SHIELD);
		s.stab = 25;
		s.slash = 24;
		s.crush = 23;
		s.strength = 6;
		return s;
	}

	private static Gear.Stat faceguard()
	{
		Gear.Stat s = item(24271, "Neitiznot faceguard", HEAD);
		s.strength = 6;
		return s;
	}

	private static Gear.Stat slayerHelm()
	{
		return item(11865, "Slayer helmet (i)", HEAD);
	}

	private static Gear.Stat chestplate()
	{
		Gear.Stat s = item(11832, "Bandos chestplate", BODY);
		s.strength = 4;
		return s;
	}

	private static Gear.Stat twistedBow()
	{
		Gear.Stat s = item(20997, "Twisted bow", WEAPON);
		s.twoHanded = true;
		s.ranged = 70;
		s.rangedStrength = 20;
		s.speed = 5;
		return s;
	}

	private static Gear.Stat dragonArrows()
	{
		Gear.Stat s = item(11212, "Dragon arrow", AMMO);
		s.rangedStrength = 60;
		return s;
	}

	private static Gear.Stat rubyBolts()
	{
		Gear.Stat s = item(9242, "Ruby bolts (e)", AMMO);
		s.rangedStrength = 103;
		return s;
	}

	private static Gear.Levels maxed()
	{
		Gear.Levels l = new Gear.Levels();
		l.attack = 99;
		l.strength = 99;
		l.ranged = 99;
		l.magic = 99;
		l.prayer = 99;
		l.hitpoints = 99;
		l.piety = true;
		l.rigour = true;
		l.augury = true;
		return l;
	}

	@Test
	public void aBossTheTableDoesNotHaveGetsNoSet() throws Exception
	{
		Gear gear = new Gear();
		assertTrue(gear.knows("Vardorvis"));
		assertTrue("a leading The does not matter", gear.knows("The Leviathan"));
		assertFalse(gear.knows("Chambers of Xeric"));
		assertFalse("the Gauntlet is fought in gear made inside it", gear.knows("Corrupted Hunllef"));
		assertNull(gear.best("Crystalline Hunllef", Arrays.asList(whip()), maxed(), false));
		assertNull(gear.best("Chambers of Xeric", Arrays.asList(whip()), maxed(), false));
		assertNull("nothing owned", gear.best("Vardorvis", new ArrayList<>(), maxed(), false));
		assertNull("nothing that hits", gear.best("Vardorvis", Arrays.asList(chestplate()), maxed(), false));
	}

	@Test
	public void theWholeSetIsBuiltAroundTheWeapon() throws Exception
	{
		Gear gear = new Gear();
		List<Gear.Stat> owned = Arrays.asList(whip(), scythe(), defender(), faceguard(), slayerHelm(), chestplate());
		Gear.Result r = gear.best("Vardorvis", owned, maxed(), false);
		assertNotNull(r);
		assertEquals("Melee", r.style);
		assertTrue(r.dps > 3);
		assertEquals(700, r.bossHitpoints);
		Gear.Stat weapon = r.items.get(WEAPON);
		assertNotNull(weapon);
		// a shield goes with a one-handed weapon and never with a two-handed one
		assertEquals(!weapon.twoHanded, r.items.containsKey(SHIELD));
		assertEquals("Bandos chestplate", r.items.get(BODY).name);
		assertEquals("off task the helmet with bonuses wins", "Neitiznot faceguard", r.items.get(HEAD).name);
		Gear.Result onTask = gear.best("Vardorvis", owned, maxed(), true);
		assertEquals("on task the Slayer helmet does", "Slayer helmet (i)", onTask.items.get(HEAD).name);
		assertTrue(onTask.dps > r.dps);
	}

	@Test
	public void aBowGetsItsArrowsNotTheBolts() throws Exception
	{
		Gear gear = new Gear();
		// Kree'arra flies: melee does not reach it
		Gear.Result r = gear.best("Kree'arra", Arrays.asList(whip(), defender(), twistedBow(), dragonArrows(), rubyBolts()), maxed(), false);
		assertNotNull(r);
		assertEquals("Ranged", r.style);
		assertEquals("Twisted bow", r.items.get(WEAPON).name);
		assertEquals("Dragon arrow", r.items.get(AMMO).name);
		assertFalse(r.items.containsKey(SHIELD));
		// a bow with no arrows does not shoot: no set, rather than a bow that would do nothing
		assertNull(gear.best("Kree'arra", Arrays.asList(twistedBow(), rubyBolts()), maxed(), false));
		// tar is fuel for a salamander: under darts it must not count as their strength
		Gear.Stat dart = item(11230, "Dragon dart", WEAPON);
		dart.rangedStrength = 35;
		dart.speed = 3;
		Gear.Stat tar = item(28837, "Irit tar", AMMO);
		tar.rangedStrength = 60;
		Gear.Result darts = gear.best("Kree'arra", Arrays.asList(dart, tar), maxed(), false);
		assertNotNull(darts);
		assertFalse(darts.items.containsKey(AMMO));
		Gear.Result both = gear.best("Kree'arra", Arrays.asList(dart, tar, twistedBow(), dragonArrows()), maxed(), false);
		assertEquals("the bow with its arrows beats darts thrown by hand", "Twisted bow", both.items.get(WEAPON).name);
	}

	@Test
	public void aSlotOnlyHoldsWhatBelongsThere() throws Exception
	{
		Gear gear = new Gear();
		Gear.Stat grapple = item(9419, "Mith grapple", AMMO);
		Gear.Stat blessing = item(22947, "Rada's blessing 4", AMMO);
		Gear.Stat plainBoots = item(1061, "Leather boots", 10);
		// a grapple fits the ammunition slot, and that is all it does there
		Gear.Result r = gear.best("The Nightmare", Arrays.asList(whip(), defender(), grapple, plainBoots), maxed(), false);
		assertNotNull(r);
		assertFalse("a grapple is not ammunition for a whip", r.items.containsKey(AMMO));
		assertFalse("boots that add nothing are left out", r.items.containsKey(10));
		Gear.Result blessed = gear.best("The Nightmare", Arrays.asList(whip(), grapple, blessing), maxed(), false);
		assertEquals("Rada's blessing 4", blessed.items.get(AMMO).name);
		// under a bow the slot is for its arrows, not for the blessing or the grapple
		Gear.Result bow = gear.best("The Nightmare", Arrays.asList(twistedBow(), dragonArrows(), grapple, blessing), maxed(), false);
		assertEquals("Dragon arrow", bow.items.get(AMMO).name);
	}

	@Test
	public void theSameInputGivesTheSameSet() throws Exception
	{
		Gear gear = new Gear();
		List<Gear.Stat> owned = Arrays.asList(whip(), scythe(), defender(), faceguard(), chestplate());
		Gear.Result a = gear.best("Vardorvis", owned, maxed(), false);
		List<Gear.Stat> shuffled = Arrays.asList(chestplate(), faceguard(), defender(), scythe(), whip());
		Gear.Result b = gear.best("Vardorvis", shuffled, maxed(), false);
		assertEquals(a.items.get(WEAPON).name, b.items.get(WEAPON).name);
		assertEquals(a.dps, b.dps, 1e-9);
	}
}
