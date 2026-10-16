package com.caplanner.gear;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.caplanner.core.Rules;
import com.caplanner.core.SpeedLimit;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;

/** What the second review of the picker found. Items are written out by hand, as in {@link GearTest}. */
public class SecondReviewTest
{
	private static final int AMULET = 2;
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

	private static Gear.Stat shooter(int id, String name, boolean twoHanded, int ranged, int strength, int speed)
	{
		Gear.Stat s = item(id, name, WEAPON);
		s.twoHanded = twoHanded;
		s.ranged = ranged;
		s.rangedStrength = strength;
		s.speed = speed;
		return s;
	}

	private static Gear.Stat ammo(int id, String name, int strength)
	{
		Gear.Stat s = item(id, name, AMMO);
		s.rangedStrength = strength;
		return s;
	}

	private static Gear.Stat whip(int id)
	{
		Gear.Stat s = item(id, "Abyssal whip", WEAPON);
		s.slash = 82;
		s.strength = 82;
		s.speed = 4;
		return s;
	}

	private static Gear.Levels maxed()
	{
		Gear.Levels l = new Gear.Levels();
		l.attack = l.strength = l.ranged = l.magic = l.prayer = l.hitpoints = 99;
		l.piety = l.rigour = l.augury = true;
		return l;
	}

	@Test
	public void aCrossbowIsNotDroppedForItsBestBoltsHavingNoFigure() throws Exception
	{
		Gear gear = new Gear();
		Gear.Stat crossbow = shooter(11785, "Armadyl crossbow", false, 100, 0, 6);
		Gear.Stat dart = shooter(809, "Mithril dart", false, 0, 7, 3);
		// onyx bolts have the highest bonus, and an effect the sums do not know
		List<Gear.Stat> owned = Arrays.asList(dart, crossbow, ammo(9245, "Onyx bolts (e)", 120), ammo(9242, "Ruby bolts (e)", 103));
		Gear.Result r = gear.best("Vorkath", owned, maxed(), false);
		assertNotNull(r);
		assertEquals("Armadyl crossbow", r.items.get(WEAPON).name);
		assertEquals("Ruby bolts (e)", r.items.get(AMMO).name);
	}

	@Test
	public void ammunitionTooGoodForTheWeaponIsNotShot() throws Exception
	{
		// which ammunition each weapon fires is the engine's, copied from Combat Check (WeaponsTest, ItemRulesTest)
		Gear gear = new Gear();
		List<Gear.Stat> owned = Arrays.asList(shooter(861, "Magic shortbow", true, 69, 0, 4),
			ammo(11212, "Dragon arrow", 60), ammo(892, "Rune arrow", 49));
		assertEquals("Rune arrow", gear.best("Vorkath", owned, maxed(), false).items.get(AMMO).name);
		assertNull("only arrows it cannot fire", gear.best("Vorkath", owned.subList(0, 2), maxed(), false));
	}

	@Test
	public void theSameItemsGiveTheSameSetWhateverTheirOrder() throws Exception
	{
		Gear gear = new Gear();
		// two whips that differ in nothing but their id (a recoloured one)
		Gear.Stat plain = whip(4151);
		Gear.Stat frozen = whip(12774);
		assertEquals(4151, gear.best("Sarachnis", Arrays.asList(plain, frozen), maxed(), false).items.get(WEAPON).itemId);
		assertEquals(4151, gear.best("Sarachnis", Arrays.asList(frozen, plain), maxed(), false).items.get(WEAPON).itemId);
	}

	@Test
	public void aPieceThatAddsNothingComesOff() throws Exception
	{
		Gear gear = new Gear();
		// a Salve amulet does nothing against the living
		Gear.Result r = gear.best("Sarachnis", Arrays.asList(whip(4151), item(12018, "Salve amulet(ei)", AMULET)), maxed(), false);
		assertFalse(r.items.containsKey(AMULET));
	}

	@Test
	public void theShieldATaskForcesIsTheOneThatDealsTheMost() throws Exception
	{
		Gear gear = new Gear();
		Gear.Limits shield = new Gear.Limits();
		shield.shield = true;
		Gear.Stat plain = item(1540, "Anti-dragon shield", SHIELD);
		// better by its plain bonuses (it stabs well), worse for a whip, which slashes
		Gear.Stat worse = item(3122, "Granite shield", SHIELD);
		worse.stab = 50;
		worse.slash = -20;
		Gear.Result r = gear.best("Sarachnis", Arrays.asList(whip(4151), worse, plain), maxed(), false, shield);
		assertEquals("Anti-dragon shield", r.items.get(SHIELD).name);
	}

	/**
	 * A weapon a task names, when owned, gives either a set or the line that says the plugin has no
	 * figure for it; "nothing you own fits" is for when it truly cannot be used.
	 */
	@Test
	public void aWeaponATaskNamesIsNeverCalledMissingWhenOwned() throws Exception
	{
		Gear gear = new Gear();
		Rules rules;
		try (BufferedReader in = reader("/com/caplanner/rules.tsv"))
		{
			rules = Rules.read(in);
		}
		// the weapons of the bundled table that the sums leave out, each as a task names it
		int without = 0;
		for (int id : rules.taskIds())
		{
			for (String name : rules.of(id).weapon)
			{
				Gear.Limits limits = new Gear.Limits();
				limits.weapon.addAll(rules.of(id).weapon);
				String worn = name.substring(0, 1).toUpperCase() + name.substring(1);
				Gear.Stat weapon = item(900000 + without, worn, WEAPON);
				weapon.speed = 4;
				List<Gear.Stat> one = Collections.singletonList(weapon);
				if (gear.best("Skotizo", one, maxed(), false, limits) == null)
				{
					assertEquals("task " + id, Collections.singletonList(worn), gear.weaponsWithoutFigure(one, limits));
					without++;
				}
			}
		}
		assertTrue(without >= 4);
		// a weapon with a figure that cannot shoot is not "no figure": it is nothing that fits
		Gear.Limits dragonbane = new Gear.Limits();
		dragonbane.weapon.add("dragon hunter crossbow");
		List<Gear.Stat> noBolts = Collections.singletonList(shooter(21012, "Dragon hunter crossbow", false, 95, 0, 5));
		assertNull(gear.best("Vorkath", noBolts, maxed(), false, dragonbane));
		assertTrue(gear.weaponsWithoutFigure(noBolts, dragonbane).isEmpty());
		Gear.Limits flail = new Gear.Limits();
		flail.weapon.add("verac's flail");
		Gear.Stat veracs = item(4755, "Verac's flail", WEAPON);
		veracs.twoHanded = true;
		veracs.crush = 82;
		veracs.strength = 72;
		veracs.speed = 5;
		List<Gear.Stat> owned = Arrays.asList(veracs, whip(4151));
		assertNull(gear.best("Kalphite Queen", owned, maxed(), false, flail));
		assertEquals(Collections.singletonList("Verac's flail"), gear.weaponsWithoutFigure(owned, flail));
		assertTrue(gear.weaponsWithoutFigure(Collections.singletonList(whip(4151)), flail).isEmpty());
		assertTrue(gear.weaponsWithoutFigure(owned, null).isEmpty());
	}

	@Test
	public void rubyBoltsAreCountedAtTheirAverageOverAKill() throws Exception
	{
		Gear gear = new Gear();
		Gear.Stat crossbow = shooter(11785, "Armadyl crossbow", false, 100, 0, 6);
		List<Gear.Stat> ruby = Arrays.asList(crossbow, ammo(9242, "Ruby bolts (e)", 103));
		List<Gear.Stat> plain = Arrays.asList(crossbow, ammo(9144, "Runite bolts", 115));
		// Vorkath has 750 hitpoints: a ruby shot that lands its effect deals 66 on average, not 100
		double gain = (gear.best("Vorkath", ruby, maxed(), false).dps - gear.best("Vorkath", plain, maxed(), false).dps) * 3.6;
		assertTrue("ruby bolts add about 0.06 x 66 a shot at most: " + gain, gain < 0.06 * 67 && gain > 0);
	}

	/** Of the game's Speed tasks, the limit is held against one kill only where it is the limit of one kill. */
	@Test
	public void onlyALimitOfOneKillIsHeldAgainstASet() throws Exception
	{
		int oneKill = 0;
		try (BufferedReader in = reader("/com/caplanner/speed.tsv"))
		{
			String line;
			while ((line = in.readLine()) != null)
			{
				String objective = line.split("\t")[1];
				if (!SpeedLimit.isOneKill(objective))
				{
					continue;
				}
				oneKill++;
				String o = objective.toLowerCase();
				assertFalse(objective, o.startsWith("complete") || o.contains(" times") || o.contains("average")
					|| o.contains("all three") || o.contains("levels") || o.contains("kills"));
			}
		}
		assertTrue(oneKill > 30);
		assertTrue(SpeedLimit.isOneKill("Kill the Hespori in less than 36 seconds."));
		assertTrue(SpeedLimit.isOneKill("Kill Zulrah in less than 54 seconds, without a slayer task."));
		assertTrue(SpeedLimit.isOneKill("Defeat the Doom of Mokhaiotl level 1 in less than 30 seconds."));
		assertFalse(SpeedLimit.isOneKill("Kill the Shellbane Gryphon 10 times within 12:30 minutes of entering the cave."));
		assertFalse(SpeedLimit.isOneKill("Complete the first 5 kills of the Maggot King within 9 minutes after entering the arena."));
	}

	private static BufferedReader reader(String resource)
	{
		return new BufferedReader(new InputStreamReader(
			SecondReviewTest.class.getResourceAsStream(resource), StandardCharsets.UTF_8));
	}
}
