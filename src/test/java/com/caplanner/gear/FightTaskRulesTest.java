package com.caplanner.gear;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.caplanner.core.GearCard;
import com.caplanner.core.Rules;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.junit.Test;

/**
 * The parts carried by name against every task of the Combat Achievements: none takes a weapon the task's rule does not
 * allow or an item it excludes, and no special attack that lowers Defence is listed for a boss that takes none.
 */
public class FightTaskRulesTest
{
	/** Special attacks that lower the target's Defence. */
	private static final List<String> DRAINS = Arrays.asList("elder maul", "dragon warhammer", "bandos godsword", "arclight",
		"emberlight", "bone dagger", "soulflame horn", "barrelchest anchor", "seercull", "accursed sceptre", "eye of ayak",
		"tonalztics of ralos", "dorgeshuun crossbow");
	/** Bosses whose Defence cannot be lowered, in one of their fights at least (DefenceDrains, the awakened fights, the Moons). */
	private static final List<String> UNDRAINED = Arrays.asList("Moons of Peril", "Brutus", "Vardorvis", "Duke Sucellus", "Leviathan",
		"Whisperer");

	private static BufferedReader reader(String resource)
	{
		return new BufferedReader(new InputStreamReader(FightTaskRulesTest.class.getResourceAsStream(resource), StandardCharsets.UTF_8));
	}

	/** Every weapon the table names, owned: a melee, ranged and magic bonus each, so that any part finds its first one. */
	private static List<Gear.Stat> everythingNamed() throws Exception
	{
		Set<String> names = new LinkedHashSet<>();
		try (BufferedReader in = reader("/com/caplanner/gear/fights.tsv"))
		{
			String line;
			while ((line = in.readLine()) != null)
			{
				String[] c = line.split("\t", -1);
				if (line.startsWith("#") || c.length < 8)
				{
					continue;
				}
				for (String n : c[7].split(";"))
				{
					if (!n.trim().isEmpty())
					{
						names.add(n.trim());
					}
				}
			}
		}
		List<Gear.Stat> out = new ArrayList<>();
		int id = 900000;
		for (String n : names)
		{
			Gear.Stat s = new Gear.Stat();
			s.itemId = id++;
			s.name = n;
			s.slot = 3;
			s.stab = s.slash = s.crush = s.ranged = s.magic = 20;
			s.strength = 20;
			s.speed = 5;
			out.add(s);
		}
		// and a set of each style the sums know, so that the set worn is found
		int[][] real = {{4151, 3, 82, 82, 0, 0, 0}, {12954, 5, 24, 6, 0, 0, 0}, {20997, 3, 0, 0, 70, 0, 20}, {11212, 13, 0, 0, 0, 0, 60},
			{1405, 3, 0, 0, 0, 14, 0}};
		String[] realNames = {"Abyssal whip", "Dragon defender", "Twisted bow", "Dragon arrow", "Mystic air staff"};
		for (int i = 0; i < real.length; i++)
		{
			Gear.Stat s = new Gear.Stat();
			s.itemId = real[i][0];
			s.slot = real[i][1];
			s.name = realNames[i];
			s.slash = real[i][2];
			s.strength = real[i][3];
			s.ranged = real[i][4];
			s.magic = real[i][5];
			s.rangedStrength = real[i][6];
			s.twoHanded = s.itemId == 20997;
			s.speed = s.slot == 3 ? 4 : 0;
			out.add(s);
		}
		return out;
	}

	@Test
	public void noPartBreaksTheRuleOfATask() throws Exception
	{
		Gear gear = new Gear();
		Fights fights;
		try (BufferedReader in = reader("/com/caplanner/gear/fights.tsv"))
		{
			fights = Fights.read(in);
		}
		Rules rules;
		try (BufferedReader in = reader("/com/caplanner/rules.tsv"))
		{
			rules = Rules.read(in);
		}
		Gear.Levels levels = new Gear.Levels();
		levels.attack = levels.strength = levels.ranged = levels.magic = levels.prayer = levels.hitpoints = 99;
		levels.piety = levels.rigour = levels.augury = true;
		List<Gear.Stat> owned = everythingNamed();
		int tasks = 0;
		int checked = 0;
		try (BufferedReader in = reader("/com/caplanner/tasks.tsv"))
		{
			String line;
			while ((line = in.readLine()) != null)
			{
				String[] c = line.split("\t");
				if (c.length < 2 || !c[0].matches("\\d+"))
				{
					continue;
				}
				tasks++;
				Gear.Limits limits = GearCard.limitsOf(rules, Integer.parseInt(c[0]));
				if (limits == null || !fights.has(c[1]))
				{
					continue;
				}
				Fights.Plan plan = fights.plan(gear, c[1], owned, levels, false, limits);
				if (plan == null)
				{
					continue;
				}
				checked++;
				for (Fights.Role role : plan.roles)
				{
					for (Gear.Stat s : role.carried)
					{
						String n = s.name.toLowerCase(Locale.ROOT);
						String where = "task " + c[0] + " (" + c[1] + "), part " + role.label + ": " + s.name;
						assertTrue(where + " is excluded", limits.exclude.stream().noneMatch(n::contains));
						if (s.slot == 3 && !limits.weapon.isEmpty())
						{
							assertTrue(where + " is not a weapon the task allows", limits.weapon.stream().anyMatch(n::contains));
						}
					}
				}
			}
		}
		assertTrue(tasks >= 600);
		assertTrue("tasks with a rule in a fight of parts: " + checked, checked >= 8);
	}

	@Test
	public void noSpecialAttackThatLowersDefenceWhereNoneLands() throws Exception
	{
		try (BufferedReader in = reader("/com/caplanner/gear/fights.tsv"))
		{
			String line;
			while ((line = in.readLine()) != null)
			{
				String[] c = line.split("\t", -1);
				if (line.startsWith("#") || c.length < 8 || !UNDRAINED.contains(c[0]))
				{
					continue;
				}
				for (String n : c[7].toLowerCase(Locale.ROOT).split(";"))
				{
					assertFalse(c[0] + ": " + n, DRAINS.contains(n.trim()));
				}
			}
		}
	}
}
