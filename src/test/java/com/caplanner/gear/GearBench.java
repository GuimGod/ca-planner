package com.caplanner.gear;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Development only: picks gear for a list of bosses from a file of owned items, outside the
 * game, so the picks can be read and compared with a calculator.
 * Usage: gradlew bench -Powned=owned.tsv -Pbosses="Vardorvis;Zulrah"
 * The file has one item a line, tab-separated: id, slot index, two-handed (0/1), name, stab,
 * slash, crush, magic, ranged, strength, ranged strength, magic damage (percent), speed.
 */
public class GearBench
{
	private static final String[] SLOT = {"head", "cape", "neck", "weapon", "body", "shield", "", "legs", "", "hands", "feet", "", "ring", "ammo"};

	public static void main(String[] args) throws Exception
	{
		List<Gear.Stat> owned = new ArrayList<>();
		try (BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(args[0]), StandardCharsets.UTF_8)))
		{
			String line;
			while ((line = in.readLine()) != null)
			{
				String[] c = line.split("\t");
				if (c.length < 13)
				{
					continue;
				}
				Gear.Stat s = new Gear.Stat();
				s.itemId = Integer.parseInt(c[0]);
				s.slot = Integer.parseInt(c[1]);
				s.twoHanded = "1".equals(c[2]);
				s.name = c[3];
				s.stab = Integer.parseInt(c[4]);
				s.slash = Integer.parseInt(c[5]);
				s.crush = Integer.parseInt(c[6]);
				s.magic = Integer.parseInt(c[7]);
				s.ranged = Integer.parseInt(c[8]);
				s.strength = Integer.parseInt(c[9]);
				s.rangedStrength = Integer.parseInt(c[10]);
				s.magicDamage = Double.parseDouble(c[11]);
				s.speed = Integer.parseInt(c[12]);
				owned.add(s);
			}
		}
		Gear.Levels levels = new Gear.Levels();
		levels.attack = levels.strength = levels.ranged = levels.magic = levels.prayer = levels.hitpoints = 99;
		levels.piety = levels.rigour = levels.augury = true;
		Gear gear = new Gear();
		System.out.println(owned.size() + " wearable items");
		// a bank of "item id, name, quantity" lines, to see the provisional inventories
		Map<String, com.caplanner.core.Bag.Have> bank = new java.util.HashMap<>();
		com.caplanner.core.Bag bag = null;
		if (args.length > 2 && !args[2].isEmpty())
		{
			try (BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(args[2]), StandardCharsets.UTF_8)))
			{
				String line;
				while ((line = in.readLine()) != null)
				{
					String[] c = line.split("\t");
					if (c.length >= 3 && c[0].matches("\\d+") && c[2].matches("\\d+"))
					{
						bank.put(c[1].toLowerCase(), new com.caplanner.core.Bag.Have(Integer.parseInt(c[0]), Integer.parseInt(c[2]), c[1]));
					}
				}
			}
			try (java.io.InputStream in = GearBench.class.getResourceAsStream("/com/caplanner/bag.tsv"))
			{
				bag = com.caplanner.core.Bag.read(new InputStreamReader(in, StandardCharsets.UTF_8));
			}
			try (java.io.InputStream in = GearBench.class.getResourceAsStream("/com/caplanner/bag-wiki.tsv"))
			{
				bag.readWiki(new InputStreamReader(in, StandardCharsets.UTF_8));
			}
		}
		com.caplanner.core.Rules rules;
		try (java.io.InputStream in = GearBench.class.getResourceAsStream("/com/caplanner/rules.tsv"))
		{
			rules = com.caplanner.core.Rules.read(new InputStreamReader(in, StandardCharsets.UTF_8));
		}
		Fights fights;
		try (java.io.InputStream in = Gear.class.getResourceAsStream("fights.tsv"))
		{
			fights = Fights.read(new InputStreamReader(in, StandardCharsets.UTF_8));
		}
		for (String entry : args[1].split(";"))
		{
			// "Boss" or "Boss#taskId", to pick inside that task's own rule
			String boss = entry.contains("#") ? entry.substring(0, entry.indexOf('#')) : entry;
			Gear.Limits limits = null;
			if (entry.contains("#"))
			{
				com.caplanner.core.Rules.Rule rule = rules.of(Integer.parseInt(entry.substring(entry.indexOf('#') + 1).trim()));
				if (rule != null && rule.shapesTheSet())
				{
					limits = new Gear.Limits();
					limits.styles.addAll(rule.styles);
					limits.weapon.addAll(rule.weapon);
					limits.exclude.addAll(rule.exclude);
					limits.oneHanded = rule.oneHanded;
					limits.shield = rule.shield;
					limits.meleeType = rule.meleeType;
					limits.ammoWord = rule.ammoWord;
					limits.spellElement = rule.spellElement;
				}
			}
			boss = entry + (limits == null && entry.contains("#") ? " (no rule for the set)" : "");
			long start = System.nanoTime();
			String name = entry.contains("#") ? entry.substring(0, entry.indexOf('#')).trim() : entry.trim();
			Fights.Plan plan = fights.has(name) ? fights.plan(gear, name, owned, levels, false, limits) : null;
			Gear.Result r = fights.has(name) ? (plan == null ? null : plan.main) : gear.best(name, owned, levels, false, limits);
			long ms = (System.nanoTime() - start) / 1_000_000;
			if (r == null)
			{
				System.out.println(boss + ": no set (" + ms + " ms)");
				continue;
			}
			StringBuilder set = new StringBuilder();
			for (Map.Entry<Integer, Gear.Stat> e : r.items.entrySet())
			{
				set.append(set.length() == 0 ? "" : " | ").append(SLOT[e.getKey()]).append(": ").append(e.getValue().name);
			}
			System.out.println(String.format("%s: %s %.2f dps, hp %d (%d ms)%n   %s", boss, r.style, r.dps, r.bossHitpoints, ms, set));
			if (bag != null && plan == null)
			{
				java.util.Set<Integer> worn = new java.util.HashSet<>();
				for (Gear.Stat one : r.items.values())
				{
					worn.add(one.itemId);
				}
				com.caplanner.core.Bag.Provisional p = bag.provisional(name, r.style, bank, new java.util.HashSet<String>(), worn,
					com.caplanner.core.Bag.SLOTS);
				if (p != null)
				{
					StringBuilder inv = new StringBuilder();
					int slots = 0;
					for (com.caplanner.core.Bag.Line l : p.lines)
					{
						inv.append(inv.length() == 0 ? "" : " | ").append(l.quantity).append(" ").append(l.name).append(l.owned ? "" : " (NOT OWNED)");
						slots += l.slots();
					}
					System.out.println("   provisional (" + slots + " slots): " + inv);
					System.out.println("   lacking: " + p.lacking + " · switches: " + p.switches);
				}
			}
			if (plan != null)
			{
				System.out.println("   worn for: " + plan.mainLabel + (r.spell == null ? "" : " (casting " + r.spell + ")"));
				for (Fights.Role role : plan.roles)
				{
					StringBuilder carried = new StringBuilder();
					for (Gear.Stat c : role.carried)
					{
						carried.append(carried.length() == 0 ? "" : ", ").append(c.name);
					}
					System.out.println(String.format("   + %s: %s %.2f dps%s -> %s", role.label, role.nothing ? "nothing owned" : role.style, role.dps,
						role.spell == null ? "" : " (casting " + role.spell + ")", carried.length() == 0 ? "(the set worn does it)" : carried));
				}
			}
		}
		System.exit(0);
	}
}
