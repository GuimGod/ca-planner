package com.caplanner.gear;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import org.junit.Test;

/** The picker for accounts that are not maxed: what they cannot wear yet, and what they cast. */
public class AccountsTest
{
	private static final int WEAPON = 3;
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

	private static Gear.Stat bow(int id, String name, int ranged, int strength, int speed)
	{
		Gear.Stat s = item(id, name, WEAPON);
		s.twoHanded = true;
		s.ranged = ranged;
		s.rangedStrength = strength;
		s.speed = speed;
		return s;
	}

	private static Gear.Stat staff(int id, String name, int magic, double damage)
	{
		Gear.Stat s = item(id, name, WEAPON);
		s.magic = magic;
		s.magicDamage = damage;
		s.speed = 5;
		return s;
	}

	private static Gear.Stat arrows(int id, String name, int strength)
	{
		Gear.Stat s = item(id, name, AMMO);
		s.rangedStrength = strength;
		return s;
	}

	private static Gear.Levels levels(int all)
	{
		Gear.Levels l = new Gear.Levels();
		l.attack = l.strength = l.ranged = l.magic = l.prayer = l.hitpoints = all;
		for (String skill : new String[]{"attack", "strength", "defence", "ranged", "magic", "prayer", "hitpoints", "slayer", "agility"})
		{
			l.all.put(skill, all);
		}
		return l;
	}

	private static String name(Gear.Result r, int slot)
	{
		return r == null || r.items.get(slot) == null ? null : r.items.get(slot).name;
	}

	@Test
	public void whatCannotBeWornYetIsNotPicked() throws Exception
	{
		Gear gear = new Gear();
		List<Gear.Stat> owned = Arrays.asList(bow(20997, "Twisted bow", 70, 20, 5), bow(843, "Oak shortbow", 14, 0, 4),
			arrows(11212, "Dragon arrow", 60), arrows(884, "Iron arrow", 10));
		// at 30 Ranged the Twisted bow (85) is out of reach; at 99 it is the one
		assertEquals("Oak shortbow", name(gear.best("Vorkath", owned, levels(30), false), WEAPON));
		assertEquals("Iron arrow", name(gear.best("Vorkath", owned, levels(30), false), AMMO));
		assertEquals("Twisted bow", name(gear.best("Vorkath", owned, levels(99), false), WEAPON));
		// levels that are not known stand in nobody's way
		Gear.Levels unknown = levels(30);
		unknown.all.clear();
		assertEquals("Twisted bow", name(gear.best("Vorkath", owned, unknown, false), WEAPON));
	}

	@Test
	public void theBundledTableKnowsWhatItemsAsk() throws Exception
	{
		WearLevels wear = WearLevels.read(new InputStreamReader(
			AccountsTest.class.getResourceAsStream("/com/caplanner/gear/wear-levels.tsv"), StandardCharsets.UTF_8));
		assertTrue(wear.size() > 1500);
		assertEquals(Integer.valueOf(70), wear.of("Abyssal whip").get("attack"));
		assertEquals(Integer.valueOf(40), wear.of("Rune scimitar").get("attack"));
		assertEquals(Integer.valueOf(40), wear.of("Rune platebody").get("defence"));
		assertFalse("the quest it needs is not a level", wear.of("Rune platebody").containsKey("slayer"));
		assertEquals(Integer.valueOf(85), wear.of("Tumeken's shadow").get("magic"));
		assertEquals(Integer.valueOf(77), wear.of("Emberlight").get("attack"));
		assertEquals(Integer.valueOf(55), wear.of("Leaf-bladed battleaxe").get("slayer"));
		// a variant the table does not name asks for what the plain item does
		assertEquals(wear.of("Abyssal whip"), wear.of("Abyssal whip (or)"));
		assertNull(wear.of("Amulet of glory(6)"));
		Map<String, Integer> low = new HashMap<>();
		low.put("attack", 60);
		assertFalse(wear.canWear("Abyssal whip", low));
		assertTrue(wear.canWear("Dragon scimitar", low));
		assertTrue("a skill that is not known is not held against the item", wear.canWear("Rune platebody", low));
	}

	@Test
	public void aStaffIsCountedWithTheBestSpellThePlayerCanCast() throws Exception
	{
		Gear gear = new Gear();
		List<Gear.Stat> owned = Arrays.asList(staff(1405, "Mystic air staff", 14, 0));
		// Vorkath is weak to fire
		Gear.Result maxed = gear.best("Vorkath", owned, levels(99), false);
		assertNotNull("a staff with runes is a set", maxed);
		assertEquals("Magic", maxed.style);
		assertEquals("Fire Surge", maxed.spell);
		// a lower level casts a lower spell, and deals less
		Gear.Result mid = gear.best("Vorkath", owned, levels(60), false);
		assertEquals("Fire Blast", mid.spell);
		assertTrue(mid.dps < maxed.dps);
		// only the runes owned
		Gear.Levels mind = levels(99);
		mind.runes = new HashSet<>(Arrays.asList("mind rune", "fire rune"));
		assertEquals("Fire Strike", gear.best("Vorkath", owned, mind, false).spell);
		Gear.Levels none = levels(99);
		none.runes = new HashSet<>();
		assertNull("no runes, no spell, no set", gear.best("Vorkath", owned, none, false));
		// a tome of the spell's element adds to it
		Gear.Stat tome = item(20714, "Tome of fire", SHIELD);
		tome.magic = 8;
		Gear.Result withTome = gear.best("Vorkath", Arrays.asList(owned.get(0), tome), levels(99), false);
		assertEquals("Tome of fire", name(withTome, SHIELD));
		assertTrue(withTome.dps > maxed.dps * 1.05);
		// a weapon with an attack of its own names no spell
		assertNull(gear.best("Vorkath", Arrays.asList(bow(20997, "Twisted bow", 70, 20, 5), arrows(11212, "Dragon arrow", 60)),
			levels(99), false).spell);
	}

	@Test
	public void spellsOfOneElementWhenTheTaskSaysSo() throws Exception
	{
		Gear gear = new Gear();
		Gear.Stat shadow = staff(27275, "Tumeken's shadow", 35, 0);
		shadow.twoHanded = true;
		List<Gear.Stat> owned = Arrays.asList(staff(1405, "Mystic air staff", 14, 0), shadow,
			bow(20997, "Twisted bow", 70, 20, 5), arrows(11212, "Dragon arrow", 60));
		Gear.Limits earth = new Gear.Limits();
		earth.spellElement = "earth";
		Gear.Result r = gear.best("The Hueycoatl", owned, levels(99), false, earth);
		assertEquals("Mystic air staff", name(r, WEAPON));
		assertEquals("Earth Surge", r.spell);
		assertNull("nothing owned casts", gear.best("The Hueycoatl", owned.subList(1, 4), levels(99), false, earth));
	}

	@Test
	public void whichSpellsAPlayerCanCast()
	{
		assertEquals(Arrays.asList(1), Spells.castable(1, null, false, null));
		// the twenty elemental spells, and the two that only one staff casts
		assertEquals(22, Spells.castable(99, null, false, null).size());
		assertEquals(38, Spells.castable(99, null, true, null).size());
		assertEquals("Magic Dart", Spells.name(Spells.MAGIC_DART));
		assertEquals("Iban Blast", Spells.name(Spells.IBAN_BLAST));
		assertEquals("only earth", 5, Spells.castable(99, null, true, "earth").size());
		assertEquals("Wind Strike", Spells.name(1));
		assertEquals("Fire Wave", Spells.name(16));
		assertEquals("Earth Surge", Spells.name(50));
		assertEquals("Smoke Rush", Spells.name(31));
		assertEquals("Ice Rush", Spells.name(34));
		assertEquals("Smoke Burst", Spells.name(35));
		assertEquals("Blood Blitz", Spells.name(41));
		assertEquals("Ice Barrage", Spells.name(46));
		// the name and the max hit go by the same number: an Ice Rush hits 16, an Ice Barrage 30
		assertEquals(16, SetCalc.spellBase(34, 99));
		assertEquals(30, SetCalc.spellBase(46, 99));
		assertEquals(Arrays.asList(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 60, 61, 31, 32, 33, 34), Spells.castable(58, null, true, null));
		assertNull(Spells.name(47));
		// blitz and barrage are cast with death and blood runes
		List<Integer> noBlood = Spells.castable(99, new HashSet<>(Arrays.asList("death rune", "chaos rune")), true, null);
		assertTrue("Ice Burst needs none", noBlood.contains(38));
		assertFalse("Ice Barrage needs blood runes", noBlood.contains(46));
		assertEquals("fire", Spells.tome("Tome of fire"));
		assertNull(Spells.tome("Dragon defender"));
	}

	@Test
	public void aStaffWithAnAttackOfItsOwnIsNotCountedAsCastingSpells() throws Exception
	{
		Gear gear = new Gear();
		// a staff the sums leave out stays out: it is not a Fire Surge caster
		assertNull(gear.best("Vorkath", Arrays.asList(staff(28796, "Bone staff", 20, 0)), levels(99), false));
		// which weapons cannot attack without their charges is the engine's, copied from Combat Check (ItemRulesTest)
		// a staff with no runes to cast is not "a weapon the plugin has no figure for"
		Gear.Limits staffOnly = new Gear.Limits();
		staffOnly.weapon.add("mystic air staff");
		Gear.Levels none = levels(99);
		none.runes = new HashSet<>();
		List<Gear.Stat> owned = Arrays.asList(staff(1405, "Mystic air staff", 14, 0));
		assertNull(gear.best("Vorkath", owned, none, false, staffOnly));
		assertTrue(gear.weaponsWithoutFigure(owned, staffOnly).isEmpty());
	}

	@Test
	public void stavesWithARuleOfTheirOwn() throws Exception
	{
		Gear gear = new Gear();
		Gear.Stat plain = staff(1405, "Mystic air staff", 14, 10);
		Gear.Stat twinflame = staff(30634, "Twinflame staff", 14, 10);
		// below the Surge spells the second spell of the twinflame staff tells, slower as it is
		double plainAt80 = gear.best("Vorkath", Arrays.asList(plain), levels(80), false).dps;
		Gear.Result twinAt80 = gear.best("Vorkath", Arrays.asList(twinflame), levels(80), false);
		assertEquals("Fire Wave", twinAt80.spell);
		// a second spell of 40%, a tenth more accuracy and damage, a tick slower
		assertTrue(twinAt80.dps > plainAt80 * 1.25 && twinAt80.dps < plainAt80 * 1.6);
		assertEquals("Twinflame staff", gear.best("Vorkath", Arrays.asList(plain, twinflame), levels(80), false).items.get(WEAPON).name);
		// at 99 it still does best with a Wave spell and its second hit, and stays ahead of a plain staff's Surge
		double plainAt99 = gear.best("Vorkath", Arrays.asList(plain), levels(99), false).dps;
		double twinAt99 = gear.best("Vorkath", Arrays.asList(twinflame), levels(99), false).dps;
		assertTrue(twinAt99 > plainAt99 && twinAt99 < plainAt99 * 1.3);
		// a smoke battlestaff adds a tenth to the standard spellbook
		double smoke = gear.best("Vorkath", Arrays.asList(staff(11998, "Smoke battlestaff", 14, 10)), levels(99), false).dps;
		double same = gear.best("Vorkath", Arrays.asList(plain), levels(99), false).dps;
		assertTrue(smoke > same * 1.1 && smoke < same * 1.3);
		// the dragon hunter wand does far more to a dragon, and nothing more to anything else
		Gear.Stat wand = staff(30070, "Dragon hunter wand", 14, 10);
		assertTrue(gear.best("Vorkath", Arrays.asList(wand), levels(99), false).dps > same * 1.5);
		assertEquals(gear.best("Sarachnis", Arrays.asList(plain), levels(99), false).dps,
			gear.best("Sarachnis", Arrays.asList(wand), levels(99), false).dps, 1e-9);
		// Magic Dart has no figure in the engine (the authors' choice in Combat Check's review): the Slayer's staff
		// casts what the level allows
		Gear.Levels mid = levels(50);
		mid.all.put("slayer", 55);
		Gear.Result dart = gear.best("Scurrius", Arrays.asList(staff(4170, "Slayer's staff", 12, 0)), mid, false);
		assertTrue(dart == null || !"Magic Dart".equals(dart.spell));
	}

	@Test
	public void virtusAddsToTheAncientMagicks() throws Exception
	{
		Gear gear = new Gear();
		Gear.Stat wand = staff(21006, "Kodai wand", 28, 15);
		Gear.Stat mask = item(26241, "Virtus mask", 0);
		mask.magic = 8;
		Gear.Stat top = item(26243, "Virtus robe top", 4);
		top.magic = 35;
		Gear.Stat legs = item(26245, "Virtus robe bottom", 7);
		legs.magic = 26;
		Gear.Levels ancients = levels(99);
		ancients.ancients = true;
		// the figures as they were before Virtus moved out of the sums: 3% more damage a piece with an Ancient spell
		Gear.Result virtus = gear.best("Commander Zilyana", Arrays.asList(wand, mask, top, legs), ancients, false);
		assertEquals("Ice Barrage", virtus.spell);
		assertEquals(1.196775311406365, virtus.dps, 1e-12);
		assertEquals("Virtus robe top", name(virtus, 4));
		Map<Integer, Gear.Stat> worn = new HashMap<>();
		worn.put(WEAPON, wand);
		worn.put(0, mask);
		worn.put(4, top);
		worn.put(7, legs);
		assertEquals(1.196775311406365, gear.dpsAt(2205, worn, ancients, false), 1e-12);
		// the same bonuses on pieces of another name add nothing to the spell
		Gear.Stat hat = item(4089, "Mystic hat", 0);
		hat.magic = 8;
		Gear.Stat robe = item(4091, "Mystic robe top", 4);
		robe.magic = 35;
		Gear.Stat bottom = item(4093, "Mystic robe bottom", 7);
		bottom.magic = 26;
		double plain = gear.best("Commander Zilyana", Arrays.asList(wand, hat, robe, bottom), ancients, false).dps;
		assertTrue(virtus.dps > plain);
		// nor does Virtus add to the standard spellbook
		Gear.Result standard = gear.best("Commander Zilyana", Arrays.asList(wand, mask, top, legs), levels(99), false);
		assertEquals(gear.best("Commander Zilyana", Arrays.asList(wand, hat, robe, bottom), levels(99), false).dps, standard.dps, 1e-9);
	}

	@Test
	public void aStaffOfTheDeadIsABladeAndAStaff() throws Exception
	{
		Gear gear = new Gear();
		Gear.Stat sotd = staff(11791, "Staff of the dead", 17, 15);
		sotd.slash = 55;
		sotd.strength = 72;
		sotd.speed = 4;
		Gear.Limits magic = new Gear.Limits();
		magic.styles.add("magic");
		Gear.Result cast = gear.best("Vorkath", Arrays.asList(sotd), levels(99), false, magic);
		assertNotNull(cast);
		assertEquals("Magic", cast.style);
		assertEquals("Fire Surge", cast.spell);
		Gear.Limits melee = new Gear.Limits();
		melee.styles.add("melee");
		// its blade has no figure in the engine (the authors' choice in Combat Check's review): no melee set from it
		assertNull(gear.best("Vorkath", Arrays.asList(sotd), levels(99), false, melee));
	}
}
