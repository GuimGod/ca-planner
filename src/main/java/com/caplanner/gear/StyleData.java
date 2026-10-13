/*
 * Copyright (c) 2026, GuimGod
 * All rights reserved. Licensed under the BSD 2-Clause License (see LICENSE).
 */
package com.caplanner.gear;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The tables bundled with the plugin, read once: which category each weapon belongs to, the
 * attack styles of each category in Combat Options order, and the defence of each monster.
 * All three come from the OSRS Wiki (see tools/gen_data.py).
 */
final class StyleData
{
	/** One attack style of a weapon category. */
	static final class Style
	{
		final String name;
		/** stab, slash, crush, ranged, magic, or empty when the style does not attack. */
		final String type;
		/** Accurate, Aggressive, Controlled, Defensive..., or empty. */
		final String stance;

		Style(String name, String type, String stance)
		{
			this.name = name;
			this.type = type;
			this.stance = stance;
		}
	}

	/** A monster's Defence and Magic levels, its defence bonuses, its size and what the Twisted bow scales with. */
	static final class Monster
	{
		/** The NPC id of the table's row. */
		int id;
		final String name;
		final int level;
		final int stab;
		final int slash;
		final int crush;
		final int magicLevel;
		final int magic;
		final int light;
		final int standard;
		final int heavy;
		final int size;
		final int tbowMagic;
		final int flatArmour;
		final String immune;
		/** The Slayer categories it belongs to, as {@link SetEffects#taskKey} writes them. */
		final Set<String> slayer;
		final boolean undead;
		final boolean demon;
		final boolean dragon;
		final boolean kalphite;
		final boolean leafy;
		/** How much of a demonbane weapon's bonus it takes, in percent (Duke Sucellus 70, Yama 120). */
		final int demonbane;
		/** Hitpoints, or 0 when the table does not have them. */
		final int hitpoints;
		/** The element it is weak to ("air", "water", "earth", "fire") and by how much, in percent; null and 0 for none. */
		final String weakness;
		final int weaknessSeverity;

		Monster(String[] c)
		{
			name = c[1];
			level = Integer.parseInt(c[2]);
			stab = Integer.parseInt(c[3]);
			slash = Integer.parseInt(c[4]);
			crush = Integer.parseInt(c[5]);
			magicLevel = number(c, 6, 1);
			magic = number(c, 7, 0);
			light = number(c, 8, 0);
			standard = number(c, 9, 0);
			heavy = number(c, 10, 0);
			size = number(c, 11, 1);
			tbowMagic = number(c, 12, magicLevel);
			flatArmour = number(c, 13, 0);
			immune = c.length > 14 ? c[14] : "";
			Set<String> categories = new HashSet<>();
			if (c.length > 15)
			{
				for (String category : c[15].split(";"))
				{
					String key = SetEffects.taskKey(category);
					if (!key.isEmpty())
					{
						categories.add(key);
					}
				}
			}
			slayer = Collections.unmodifiableSet(categories);
			String attributes = c.length > 16 ? c[16] : "";
			undead = attributes.contains("u");
			demon = attributes.contains("d");
			dragon = attributes.contains("r");
			kalphite = attributes.contains("k");
			leafy = attributes.contains("l");
			demonbane = number(c, 17, 100);
			hitpoints = number(c, 18, 0);
			weakness = c.length > 19 && !c[19].isEmpty() ? c[19] : null;
			weaknessSeverity = number(c, 20, 0);
		}

		private static int number(String[] c, int i, int fallback)
		{
			return c.length > i && !c[i].isEmpty() ? Integer.parseInt(c[i]) : fallback;
		}

		SetCalc.Target target()
		{
			SetCalc.Target t = new SetCalc.Target();
			t.name = name;
			t.defenceLevel = level;
			t.stab = stab;
			t.slash = slash;
			t.crush = crush;
			t.magicLevel = magicLevel;
			t.magic = magic;
			t.light = light;
			t.standard = standard;
			t.heavy = heavy;
			t.size = size;
			t.tbowMagic = tbowMagic;
			t.flatArmour = flatArmour;
			t.immune = immune;
			t.cap = "Zulrah".equals(name) ? 50 : 0;
			t.undead = undead;
			t.demon = demon;
			t.dragon = dragon;
			t.kalphite = kalphite;
			t.leafy = leafy;
			t.demonbane = demonbane;
			t.hitpoints = hitpoints;
			t.weakness = weakness;
			t.weaknessSeverity = weaknessSeverity;
			return t;
		}
	}

	private final Map<Integer, String> weaponCategory = new HashMap<>();
	private final Map<String, List<Style>> categoryStyles = new HashMap<>();
	private final Map<Integer, Monster> monsters = new HashMap<>();
	/** The first monster of each name, in lower case: for the ids of the same monster that the table does not list. */
	private final Map<String, Monster> monstersByName = new HashMap<>();
	private final Set<String> slayerCategories = new HashSet<>();

	StyleData() throws IOException
	{
		for (String[] c : rows("weapons.tsv"))
		{
			weaponCategory.put(Integer.parseInt(c[0]), c[1]);
		}
		for (String[] c : rows("styles.tsv"))
		{
			List<Style> styles = new ArrayList<>();
			for (String s : c[1].split(";"))
			{
				String[] p = s.split(":", -1);
				styles.add(new Style(p[0], p.length > 1 ? p[1] : "", p.length > 2 ? p[2] : ""));
			}
			categoryStyles.put(c[0], Collections.unmodifiableList(styles));
		}
		for (String[] c : rows("monsters.tsv"))
		{
			Monster m = new Monster(c);
			m.id = Integer.parseInt(c[0]);
			monsters.put(m.id, m);
			if (m.name != null)
			{
				monstersByName.putIfAbsent(m.name.toLowerCase(java.util.Locale.ROOT), m);
			}
			slayerCategories.addAll(m.slayer);
		}
	}

	/** The weapon category of an item ("Scythe", "Staff", "Bow"...), or null when it is not a weapon in the table. */
	String category(int weaponItemId)
	{
		return weaponItemId <= 0 ? "Unarmed" : weaponCategory.get(weaponItemId);
	}

	/** The styles of this weapon in Combat Options order, or an empty list for a weapon the table does not have. */
	List<Style> styles(int weaponItemId)
	{
		String category = weaponItemId <= 0 ? "Unarmed" : weaponCategory.get(weaponItemId);
		List<Style> styles = category == null ? null : categoryStyles.get(category);
		return styles == null ? Collections.emptyList() : styles;
	}

	Monster monster(int npcId)
	{
		return monsters.get(npcId);
	}

	/**
	 * The monster by its NPC id or, when the table does not list that id, by its name: the game has several
	 * ids for the same monster in different places (the Hellhound of the Catacombs of Kourend), and the table
	 * holds one for each version the OSRS Wiki's calculator has.
	 */
	Monster monster(int npcId, String name)
	{
		Monster m = monsters.get(npcId);
		return m != null || name == null ? m : monstersByName.get(name.toLowerCase(java.util.Locale.ROOT));
	}

	/**
	 * The monster a boss's name stands for, with or without a leading "The". When the table has
	 * several versions of it (a quest fight, a later phase), the one with the most hitpoints: the
	 * fight a kill is counted on. Null when the table has no monster of that name.
	 */
	private static final int MADE_UP_IDS = 100000;
	/** Bosses whose row to use is not the one with the most hitpoints: Scurrius alone has 500, in a group 1,500. */
	private static final Map<String, Integer> FOUGHT_ALONE = new HashMap<>();

	static
	{
		FOUGHT_ALONE.put("scurrius", 7222);
	}

	Monster named(String name)
	{
		if (name == null)
		{
			return null;
		}
		String wanted = bare(name);
		Monster best = null;
		for (Monster m : monsters.values())
		{
			if (m.id >= MADE_UP_IDS)
			{
				// a row written for another purpose, not a monster of the game
				continue;
			}
			if (m.name != null && bare(m.name).equals(wanted)
				&& (best == null || m.hitpoints > best.hitpoints || (m.hitpoints == best.hitpoints && m.id < best.id)))
			{
				best = m;
			}
		}
		// where the version with the most hitpoints is not the one fought alone
		Integer alone = FOUGHT_ALONE.get(wanted);
		return alone != null && monsters.get(alone) != null ? monsters.get(alone) : best;
	}

	private static String bare(String name)
	{
		String n = name.toLowerCase(java.util.Locale.ROOT).trim();
		return n.startsWith("the ") ? n.substring(4) : n;
	}

	/** Every Slayer category the table knows, as {@link SetEffects#taskKey} writes them. */
	Set<String> slayerCategories()
	{
		return slayerCategories;
	}

	private static List<String[]> rows(String file) throws IOException
	{
		List<String[]> rows = new ArrayList<>();
		InputStream in = StyleData.class.getResourceAsStream(file);
		if (in == null)
		{
			throw new IOException("missing resource " + file);
		}
		try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8)))
		{
			String line;
			while ((line = r.readLine()) != null)
			{
				if (!line.isEmpty() && !line.startsWith("#"))
				{
					rows.add(line.split("\t", -1));
				}
			}
		}
		return rows;
	}
}
