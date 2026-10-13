package com.caplanner.gear;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.caplanner.core.Bag;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.Test;

/** What a fight makes the player wear is worn, whatever it costs in damage. */
public class MustWearTest
{
	private static final int HEAD = 0;
	private static final int WEAPON = 3;
	private static final int SHIELD = 5;
	private static final int FEET = 10;

	private static Gear.Stat item(int id, String name, int slot)
	{
		Gear.Stat s = new Gear.Stat();
		s.itemId = id;
		s.name = name;
		s.slot = slot;
		return s;
	}

	private static Gear.Stat melee(int id, String name, boolean twoHanded, int slash, int strength, int speed)
	{
		Gear.Stat s = item(id, name, WEAPON);
		s.twoHanded = twoHanded;
		s.slash = slash;
		s.strength = strength;
		s.speed = speed;
		return s;
	}

	private static Gear.Levels maxed()
	{
		Gear.Levels l = new Gear.Levels();
		l.attack = l.strength = l.ranged = l.magic = l.prayer = l.hitpoints = 99;
		l.piety = l.rigour = l.augury = true;
		return l;
	}

	private static Gear.Limits wearing(String... names)
	{
		Gear.Limits l = new Gear.Limits();
		l.mustWear.add(Arrays.asList(names));
		return l;
	}

	@Test
	public void aShieldTheFightNeedsBeatsABetterShieldAndATwoHandedWeapon() throws Exception
	{
		Gear gear = new Gear();
		Gear.Stat scythe = melee(22325, "Scythe of vitur", true, 125, 75, 5);
		Gear.Stat whip = melee(4151, "Abyssal whip", false, 82, 82, 4);
		Gear.Stat defender = item(12954, "Dragon defender", SHIELD);
		defender.slash = 24;
		defender.strength = 6;
		Gear.Stat mirror = item(4156, "Mirror shield", SHIELD);
		List<Gear.Stat> owned = Arrays.asList(scythe, whip, defender, mirror);
		// left alone the picker takes neither the mirror shield nor, perhaps, a one-handed weapon
		assertFalse("Mirror shield".equals(name(gear.best("Sarachnis", owned, maxed(), false), SHIELD)));
		Gear.Result r = gear.best("Sarachnis", owned, maxed(), false, wearing("mirror shield", "v's shield"));
		assertEquals("Mirror shield", name(r, SHIELD));
		assertEquals("Abyssal whip", name(r, WEAPON));
		assertTrue(r.mustWearMissing.isEmpty());
		// only a two-handed weapon owned: no set can wear the shield
		assertEquals(null, gear.best("Sarachnis", Arrays.asList(scythe, mirror), maxed(), false, wearing("mirror shield")));
	}

	@Test
	public void ofSeveralThatWouldDoTheOneThatCostsLeastIsWorn() throws Exception
	{
		Gear gear = new Gear();
		Gear.Stat whip = melee(4151, "Abyssal whip", false, 82, 82, 4);
		Gear.Stat stone = item(23037, "Boots of stone", FEET);
		Gear.Stat brimstone = item(22951, "Boots of brimstone", FEET);
		brimstone.slash = 3;
		brimstone.strength = 3;
		Gear.Stat primordial = item(13239, "Primordial boots", FEET);
		primordial.slash = 12;
		primordial.strength = 12;
		Gear.Result r = gear.best("Sarachnis", Arrays.asList(whip, stone, primordial, brimstone), maxed(), false,
			wearing("boots of stone", "boots of brimstone", "granite boots"));
		assertEquals("Boots of brimstone", name(r, FEET));
		// without the need the better boots are worn
		assertEquals("Primordial boots", name(gear.best("Sarachnis", Arrays.asList(whip, stone, primordial, brimstone), maxed(), false), FEET));
	}

	@Test
	public void whatIsNotOwnedIsReportedAndTheSetIsStillPicked() throws Exception
	{
		Gear gear = new Gear();
		Gear.Stat whip = melee(4151, "Abyssal whip", false, 82, 82, 4);
		Gear.Limits limits = wearing("nose peg", "slayer helmet");
		limits.mustWear.add(Arrays.asList("mirror shield"));
		Gear.Stat helmet = item(11864, "Slayer helmet", HEAD);
		Gear.Result r = gear.best("Sarachnis", Arrays.asList(whip, helmet), maxed(), false, limits);
		assertNotNull(r);
		// a helmet that adds no damage off task is worn all the same, and stays on
		assertEquals("Slayer helmet", name(r, HEAD));
		assertEquals(Arrays.asList(1), r.mustWearMissing);
	}

	@Test
	public void theBundledTableSaysWhatEachFightMakesThePlayerWear() throws Exception
	{
		Bag bag = Bag.read(new InputStreamReader(
			MustWearTest.class.getResourceAsStream("/com/caplanner/bag.tsv"), StandardCharsets.UTF_8));
		Set<String> none = new HashSet<>();
		assertEquals("Mirror shield", bag.wear("Basilisk Knight", none).get(0).name);
		assertEquals(4156, bag.wear("Basilisk Knight", none).get(0).itemId);
		assertTrue(bag.wear("Basilisk Knight", none).get(0).names.contains("v's shield"));
		assertEquals(1, bag.wear("Alchemical Hydra", none).size());
		Set<String> diary = new HashSet<>(Arrays.asList("kourend-elite"));
		assertTrue("the diary makes the boots needless", bag.wear("Alchemical Hydra", diary).isEmpty());
		assertTrue(bag.wear("Sarachnis", none).isEmpty());
		assertEquals(3, bag.wield("Kurask").size());
		assertEquals(1, bag.notes("Kurask").size());
		assertTrue(bag.wield("Vorkath").isEmpty());
		// none of this is part of the inventory
		assertTrue(bag.of("Kurask", "melee", new java.util.HashMap<>()).isEmpty());
		assertTrue(bag.of("Basilisk Knight", "melee", new java.util.HashMap<>()).isEmpty());
	}

	private static String name(Gear.Result r, int slot)
	{
		return r == null || r.items.get(slot) == null ? null : r.items.get(slot).name;
	}
}
