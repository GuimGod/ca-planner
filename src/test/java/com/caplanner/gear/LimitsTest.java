package com.caplanner.gear;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.List;
import org.junit.Test;

/** The picker inside what a task allows. Items are written out by hand, as in {@link GearTest}. */
public class LimitsTest
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

	private static Gear.Stat melee(int id, String name, boolean twoHanded, int stab, int slash, int crush, int strength, int speed)
	{
		Gear.Stat s = item(id, name, WEAPON);
		s.twoHanded = twoHanded;
		s.stab = stab;
		s.slash = slash;
		s.crush = crush;
		s.strength = strength;
		s.speed = speed;
		return s;
	}

	private static Gear.Stat scythe()
	{
		return melee(22325, "Scythe of vitur", true, 70, 125, 30, 75, 5);
	}

	private static Gear.Stat whip()
	{
		return melee(4151, "Abyssal whip", false, 0, 82, 0, 82, 4);
	}

	private static Gear.Stat arclight()
	{
		return melee(19675, "Arclight", false, 10, 38, 0, 8, 4);
	}

	private static Gear.Stat mace()
	{
		return melee(24417, "Inquisitor's mace", false, 52, -4, 95, 89, 4);
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

	private static Gear.Stat arrows(int id, String name, int strength)
	{
		Gear.Stat s = item(id, name, AMMO);
		s.rangedStrength = strength;
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

	private static Gear.Levels maxed()
	{
		Gear.Levels l = new Gear.Levels();
		l.attack = l.strength = l.ranged = l.magic = l.prayer = l.hitpoints = 99;
		l.piety = l.rigour = l.augury = true;
		return l;
	}

	private static Gear.Limits limits()
	{
		return new Gear.Limits();
	}

	private static String weapon(Gear.Result r)
	{
		return r == null ? null : r.items.get(WEAPON).name;
	}

	@Test
	public void theWeaponTheTaskNamesIsTheOneInHand() throws Exception
	{
		Gear gear = new Gear();
		List<Gear.Stat> owned = Arrays.asList(scythe(), whip(), arclight(), defender());
		Gear.Limits demonbane = limits();
		demonbane.weapon.add("arclight");
		demonbane.weapon.add("emberlight");
		assertEquals("Arclight", weapon(gear.best("Skotizo", owned, maxed(), false, demonbane)));
		// and the other way: nothing demonbane
		Gear.Limits none = limits();
		none.exclude.add("arclight");
		assertFalse("Arclight".equals(weapon(gear.best("Skotizo", owned, maxed(), false, none))));
		// without the weapon the task names there is no set, rather than another weapon
		assertNull(gear.best("Skotizo", Arrays.asList(scythe(), whip()), maxed(), false, demonbane));
		// no limits at all is the plain search
		assertEquals(weapon(gear.best("Skotizo", owned, maxed(), false)), weapon(gear.best("Skotizo", owned, maxed(), false, null)));
	}

	@Test
	public void aStyleTheTaskForbidsIsNotPicked() throws Exception
	{
		Gear gear = new Gear();
		List<Gear.Stat> owned = Arrays.asList(whip(), defender(), twistedBow(), arrows(11212, "Dragon arrow", 60));
		Gear.Limits meleeOnly = limits();
		meleeOnly.styles.add("melee");
		assertEquals("Melee", gear.best("Vorkath", owned, maxed(), false, meleeOnly).style);
		Gear.Limits rangedOnly = limits();
		rangedOnly.styles.add("ranged");
		assertEquals("Ranged", gear.best("Vorkath", owned, maxed(), false, rangedOnly).style);
		Gear.Limits magicOnly = limits();
		magicOnly.styles.add("magic");
		assertNull("nothing owned casts", gear.best("Vorkath", owned, maxed(), false, magicOnly));
	}

	@Test
	public void oneHandAndShield() throws Exception
	{
		Gear gear = new Gear();
		List<Gear.Stat> owned = Arrays.asList(scythe(), whip(), defender());
		Gear.Limits oneHand = limits();
		oneHand.oneHanded = true;
		assertEquals("Abyssal whip", weapon(gear.best("Sarachnis", owned, maxed(), false, oneHand)));
		Gear.Limits shield = limits();
		shield.shield = true;
		Gear.Result r = gear.best("Sarachnis", owned, maxed(), false, shield);
		assertEquals("Abyssal whip", weapon(r));
		assertTrue(r.items.containsKey(SHIELD));
		// a shield that adds no damage is still worn when the task wants one
		Gear.Stat plain = item(1540, "Anti-dragon shield", SHIELD);
		Gear.Result worn = gear.best("Sarachnis", Arrays.asList(whip(), plain), maxed(), false, shield);
		assertEquals("Anti-dragon shield", worn.items.get(SHIELD).name);
		assertNull("no shield owned", gear.best("Sarachnis", Arrays.asList(scythe(), whip()), maxed(), false, shield));
	}

	@Test
	public void theKindOfHitTheTaskAsksFor() throws Exception
	{
		Gear gear = new Gear();
		List<Gear.Stat> owned = Arrays.asList(whip(), mace(), defender());
		Gear.Limits crush = limits();
		crush.meleeType = "crush";
		assertEquals("a whip only slashes", "Inquisitor's mace", weapon(gear.best("Sarachnis", owned, maxed(), false, crush)));
		assertNull(gear.best("Sarachnis", Arrays.asList(whip(), defender()), maxed(), false, crush));
		Gear.Limits stab = limits();
		stab.meleeType = "stab";
		Gear.Result r = gear.best("King Black Dragon", owned, maxed(), false, stab);
		assertNotNull("the mace has a stab style", r);
		assertEquals("Inquisitor's mace", weapon(r));
	}

	@Test
	public void theAmmunitionTheTaskAsksFor() throws Exception
	{
		Gear gear = new Gear();
		Gear.Limits mithril = limits();
		mithril.styles.add("ranged");
		mithril.ammoWord = "mithril";
		List<Gear.Stat> owned = Arrays.asList(twistedBow(), arrows(11212, "Dragon arrow", 60), arrows(888, "Mithril arrow", 22));
		Gear.Result r = gear.best("Leviathan", owned, maxed(), false, mithril);
		assertEquals("Mithril arrow", r.items.get(AMMO).name);
		assertEquals("Dragon arrow", gear.best("Leviathan", owned, maxed(), false).items.get(AMMO).name);
		assertNull(gear.best("Leviathan", Arrays.asList(twistedBow(), arrows(11212, "Dragon arrow", 60)), maxed(), false, mithril));
		// a bow that makes its own arrows uses no mithril at all: it is not a way round the rule
		Gear.Stat bowfa = item(25865, "Bow of faerdhinen (c)", WEAPON);
		bowfa.twoHanded = true;
		bowfa.ranged = 128;
		bowfa.rangedStrength = 106;
		bowfa.speed = 5;
		assertNotNull(gear.best("Leviathan", Arrays.asList(bowfa), maxed(), false));
		assertNull(gear.best("Leviathan", Arrays.asList(bowfa), maxed(), false, mithril));
		Gear.Stat mithrilDart = item(809, "Mithril dart", WEAPON);
		mithrilDart.rangedStrength = 7;
		mithrilDart.speed = 3;
		Gear.Stat dragonDart = item(11230, "Dragon dart", WEAPON);
		dragonDart.rangedStrength = 35;
		dragonDart.speed = 3;
		assertEquals("Mithril dart", gear.best("Leviathan", Arrays.asList(bowfa, mithrilDart, dragonDart), maxed(), false, mithril).items.get(WEAPON).name);
	}
}
