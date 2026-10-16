/*
 * Copyright (c) 2026, GuimGod
 * All rights reserved. Licensed under the BSD 2-Clause License (see LICENSE).
 */
package com.caplanner.gear;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The tables the plugin works from, read once: which category each weapon belongs to, the attack
 * styles of each category in Combat Options order, and the defence of each monster. All three come
 * from the OSRS Wiki (see tools/gen_data.py). They are bundled with the plugin; a newer copy of the
 * same three files may replace them at startup (see {@link RemoteData}).
 */
final class StyleData
{
	/** A level the table does not have: high enough that a Bandos godsword's damage never goes past it. */
	static final int UNKNOWN_LEVEL = 100000;

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

	/**
	 * A monster's Defence and Magic levels, its defence bonuses, its size, what the Twisted bow scales with, and the rules
	 * of the raids and fights the DPS calculator has for it.
	 */
	static final class Monster
	{
		/** The NPC id of the table's row. */
		int id;
		/**
		 * A row of the awakened fight of one of Desert Treasure II's bosses (under the negative of the id in the table):
		 * the game gives it the same NPC id as the fight after the quest ({@link StyleData#inFight}).
		 */
		boolean awakened;
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
		/** A monster of the Chambers of Xeric: the Twisted bow scales with up to 350 magic instead of 250. */
		final boolean xerician;
		/**
		 * A vampyre's tier (1, 2 or 3), 0 for none: tier 2 takes damage only from silver weapons or with Efaritay's aid,
		 * tier 3 only from a few silver ones.
		 */
		final int vampyre;
		/** What each hit that lands is divided by, for melee, ranged and magic (1 for no division). */
		final int meleeDivisor;
		final int rangedDivisor;
		final int magicDivisor;
		/** A hit of 1 or more that is divided stays at least 1 (Tekton). */
		final boolean divisionKeepsOne;
		/** No division for a fire spell or a demonbane weapon (the Ice demon). */
		final boolean divisionSparesFire;
		/** No division with a pickaxe in hand (the Slagilith). */
		final boolean divisionSparesPickaxe;
		/** The ogres' rule: half for a Crumble Undead spell instead, no division for a Comp ogre bow with brutal arrows. */
		final boolean ogreDivision;
		/** Its magic defence is rolled with its Defence level, not its Magic level. */
		final boolean magicDefenceFromDefence;
		/** How much of a demonbane weapon's bonus it takes, in percent (Duke Sucellus 70, Yama 120). */
		final int demonbane;
		/** Hitpoints, or 0 when the table does not have them. */
		final int hitpoints;
		/** The element it is weak to ("air", "water", "earth", "fire") and by how much, in percent; null and 0 for none. */
		final String weakness;
		final int weaknessSeverity;
		/** Attack, Strength and Ranged levels; {@link #UNKNOWN_LEVEL} in a table older than these columns. */
		final int attack;
		final int strength;
		final int ranged;
		/** The magic attack bonus, -1 when the table does not have it. */
		final int magicAttack;
		/**
		 * The Chambers of Xeric's scaling it takes ({@link CoxScaling}): 'm' the rooms', 's' the singles formula
		 * (Scavenger beasts, Vespine soldiers), 'H', 'L', 'R' the Great Olm's head, melee hand and mage hand; 0 for none.
		 */
		final char coxScaling;
		/** Its Magic level scales with the defensive levels (Tekton, Vespula, the Olm's hands...), not the offensive ones. */
		final boolean coxMagicDefensive;
		final boolean coxTekton;
		final boolean coxGlowingCrystal;
		/** A Guardian: its hitpoints come from the party's Mining levels. */
		final boolean coxGuardian;
		/**
		 * The Theatre of Blood's scaling it takes ({@link TobScaling}): 'b' normal or Hard Mode, 'e' Entry Mode; 0 for
		 * none.
		 */
		final char tobScaling;
		/**
		 * A monster of the Tombs of Amascut: Tumeken's shadow multiplies its bonuses by four, Osmumten's fang rolls its
		 * accuracy twice the raid's way, the Keris partisan of the sun is more accurate below a quarter of its
		 * hitpoints, and the raid level scales its defence roll and hitpoints ({@link ToaScaling}).
		 */
		final boolean toa;
		/** The path it ends ('H' Het, 'A' Apmeken, 'S' Scabaras, 'C' Crondis): its level adds to the hitpoints; 0 for none. */
		final char toaPath;
		/** Its defence roll does not grow with the raid level (Kephri's scarab overlords). */
		final boolean toaRollUnscaled;
		/** The Wardens' ejected core: 4500 hitpoints, a tenth of the raid level's growth. */
		final boolean toaCore;
		/** What each hit that lands is multiplied by, for melee, ranged and magic (the Nightmare's totems take magic twice). */
		final int meleeMultiplier;
		final int rangedMultiplier;
		final int magicMultiplier;
		/** Every attack of the class lands at the max hit (the calculator's ALWAYS_MAX_HIT_MONSTERS). */
		final boolean alwaysMaxMelee;
		final boolean alwaysMaxRanged;
		final boolean alwaysMaxMagic;
		/** The Hueycoatl's tail: each hit is limited to a random 0 to 4 (9 with crush or an earth spell). */
		final boolean hueycoatlTail;
		/**
		 * No ranged figure (the Royal Titans): their ranged accuracy is six times while they stand out of melee distance,
		 * which the plugin cannot tell.
		 */
		final boolean noRangedFigure;
		/** Araxxor: enraged from a quarter of his hitpoints, with more Defence, Magic and Ranged. */
		final boolean araxxorEnrage;
		/** Verzik's first phase: each hit is limited to a random 0 to 10 for melee, 0 to 3 for ranged and magic. */
		final boolean verzikP1;
		/** The Royal Titans' elementals: magic lands with the magic attack bonus / 100 + 0.3, at the max hit. */
		final boolean titanElemental;
		/**
		 * The Wardens of the Tombs of Amascut's second phase: every attack lands, and ranged and magic deal 15% to 60% of
		 * their max hit by what is left of the attack roll (the calculator's own rule for them).
		 */
		final boolean wardenP2;
		/** Its Defence level once enraged (the Wardens of the third phase: 180); 0 for none. */
		final int enragedDefence;
		/**
		 * Every attack lands on it (the Doom of Mokhaiotl shielded and burrowed: the calculator's accuracy outside its Normal
		 * phase; the Spawn of the Abyssal Sire and the Judge of Yama: its GUARANTEED_ACCURACY_MONSTERS).
		 */
		final boolean everyAttackLands;
		/** A hit kills it, whatever the hit (the calculator's ONE_HIT_MONSTERS: Scurrius's Giant rats, the Flowers). */
		final boolean oneHit;

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
			xerician = attributes.contains("x");
			vampyre = attributes.contains("3") ? 3 : attributes.contains("2") ? 2 : attributes.contains("1") ? 1 : 0;
			demonbane = number(c, 17, 100);
			hitpoints = number(c, 18, 0);
			weakness = c.length > 19 && !c[19].isEmpty() ? c[19] : null;
			weaknessSeverity = number(c, 20, 0);
			// the two columns of the Chambers of Xeric's rules; an older table has neither
			String[] divisors = c.length > 21 && !c[21].isEmpty() ? c[21].split(";", -1) : new String[0];
			meleeDivisor = number(divisors, 0, 1);
			rangedDivisor = number(divisors, 1, 1);
			magicDivisor = number(divisors, 2, 1);
			String rules = c.length > 22 ? c[22] : "";
			magicDefenceFromDefence = rules.contains("D");
			divisionSparesFire = rules.contains("F");
			divisionKeepsOne = rules.contains("1");
			divisionSparesPickaxe = rules.contains("P");
			ogreDivision = rules.contains("Z");
			// the levels a Bandos godsword's damage goes on to and the raid's scaling reads; an older table has none
			// of them: then the godsword stops at Defence, as it did, and nothing is scaled
			attack = number(c, 23, UNKNOWN_LEVEL);
			strength = number(c, 24, UNKNOWN_LEVEL);
			ranged = number(c, 25, UNKNOWN_LEVEL);
			magicAttack = number(c, 26, -1);
			coxScaling = c.length > 27 && !c[27].isEmpty() ? c[27].charAt(0) : 0;
			String cox = c.length > 28 ? c[28] : "";
			coxMagicDefensive = cox.contains("d");
			coxTekton = cox.contains("t");
			coxGlowingCrystal = cox.contains("c");
			coxGuardian = cox.contains("g");
			// the Theatre of Blood's and the Tombs of Amascut's columns, and the other rules; an older table has none
			String raid = c.length > 29 ? c[29] : "";
			tobScaling = raid.contains("b") ? 'b' : raid.contains("e") ? 'e' : 0;
			toa = raid.contains("t");
			toaPath = raid.contains("H") ? 'H' : raid.contains("A") ? 'A' : raid.contains("S") ? 'S' : raid.contains("C") ? 'C' : 0;
			toaRollUnscaled = toa && raid.contains("o");
			toaCore = toa && raid.contains("w");
			String[] multipliers = c.length > 30 && !c[30].isEmpty() ? c[30].split(";", -1) : new String[0];
			meleeMultiplier = number(multipliers, 0, 1);
			rangedMultiplier = number(multipliers, 1, 1);
			magicMultiplier = number(multipliers, 2, 1);
			String max = c.length > 31 ? c[31] : "";
			alwaysMaxMelee = max.contains("m");
			alwaysMaxRanged = max.contains("r");
			alwaysMaxMagic = max.contains("g");
			String more = c.length > 32 ? c[32] : "";
			hueycoatlTail = more.contains("T");
			noRangedFigure = more.contains("O");
			araxxorEnrage = more.contains("X");
			verzikP1 = more.contains("V");
			titanElemental = more.contains("E");
			wardenP2 = more.contains("W");
			everyAttackLands = more.contains("A");
			oneHit = more.contains("K");
			enragedDefence = number(c, 33, 0);
		}

		/**
		 * The monster's levels as the fight has them: {@code levels} from {@link CoxScaling#levels} and
		 * {@link DefenceDrains#apply}, or null for the table's own.
		 */
		SetCalc.Target target(DefenceDrains.Levels levels)
		{
			SetCalc.Target t = target();
			if (levels == null)
			{
				return t;
			}
			t.defenceLevel = levels.defence;
			t.magicLevel = levels.magic;
			t.magic = levels.magicDefence;
			t.hitpoints = levels.hitpoints;
			// the Twisted bow scales with the higher of the Magic level and the magic attack bonus, which no drain or
			// scaling touches. An older table has only the higher of the two: above the Magic level it is the bonus
			t.tbowMagic = magicAttack >= 0 ? Math.max(levels.magic, magicAttack)
				: tbowMagic > magicLevel ? tbowMagic : levels.magic;
			return t;
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
			// the Fragment of Seren: a hit above 22 is a random 22 to 24 (the calculator's rule by the name)
			t.damageCap = "Fragment of Seren".equals(name) ? 22 : 0;
			// the Tormented Demon's fire shield, and the share of the attacks that meet it without defence
			t.fireShield = Weapons.fireShield(name);
			t.defencelessShare = Weapons.defencelessShare(name);
			// Araxxor's araxytes that take the max hit from crush, crossbows and the Noxious halberd (by the name)
			t.araxyteMax = Weapons.araxyteMax(name);
			// Xarpus and Verzik's second phase: the Scythe of vitur attacks once every 16/3 ticks (by the row's id)
			t.scytheSpeed = Weapons.scytheSpeed(id);
			// the Respiratory systems: at least half the max hit, and a demonbane hit kills one (by the name)
			t.leastHalfMax = "Respiratory system".equals(name);
			t.demonbaneKills = t.leastHalfMax;
			t.undead = undead;
			t.demon = demon;
			t.dragon = dragon;
			t.kalphite = kalphite;
			t.leafy = leafy;
			t.xerician = xerician;
			t.vampyre = vampyre;
			t.meleeDivisor = meleeDivisor;
			t.rangedDivisor = rangedDivisor;
			t.magicDivisor = magicDivisor;
			t.divisionKeepsOne = divisionKeepsOne;
			t.divisionSparesFire = divisionSparesFire;
			t.divisionSparesPickaxe = divisionSparesPickaxe;
			t.ogreDivision = ogreDivision;
			t.everyAttackLands = everyAttackLands;
			t.oneHit = oneHit;
			t.magicDefenceFromDefence = magicDefenceFromDefence;
			t.demonbane = demonbane;
			t.hitpoints = hitpoints;
			t.weakness = weakness;
			t.weaknessSeverity = weaknessSeverity;
			t.toa = toa;
			t.meleeMultiplier = meleeMultiplier;
			t.rangedMultiplier = rangedMultiplier;
			t.magicMultiplier = magicMultiplier;
			t.alwaysMaxMelee = alwaysMaxMelee;
			t.alwaysMaxRanged = alwaysMaxRanged;
			t.alwaysMaxMagic = alwaysMaxMagic;
			t.hueycoatlTail = hueycoatlTail;
			t.verzikP1 = verzikP1;
			t.titanElemental = titanElemental;
			t.wardenP2 = wardenP2;
			t.noRangedFigure = noRangedFigure;
			return t;
		}
	}

	private final Map<Integer, String> weaponCategory = new HashMap<>();
	private final Map<String, List<Style>> categoryStyles = new HashMap<>();
	private final Map<Integer, Monster> monsters = new HashMap<>();
	/**
	 * The awakened fights of Desert Treasure II's bosses, by the NPC id they share with the fight after the quest: the
	 * table's rows under the negative of the id (tools/gen_data.py).
	 */
	private final Map<Integer, Monster> awakened = new HashMap<>();
	/** The first monster of each name, in lower case: for the ids of the same monster that the table does not list. */
	private final Map<String, Monster> monstersByName = new HashMap<>();
	private final Set<String> slayerCategories = new HashSet<>();

	/** The tables bundled with the plugin. */
	StyleData() throws IOException
	{
		this(resource("weapons.tsv"), resource("styles.tsv"), resource("monsters.tsv"));
	}

	/**
	 * The tables from the text of the three files. A row that does not read (a number that is not one,
	 * a column missing) throws: the caller keeps the tables it had.
	 */
	StyleData(String weaponTable, String styleTable, String monsterTable) throws IOException
	{
		for (String[] c : rows(weaponTable))
		{
			weaponCategory.put(Integer.parseInt(c[0]), c[1]);
		}
		for (String[] c : rows(styleTable))
		{
			List<Style> styles = new ArrayList<>();
			for (String s : c[1].split(";"))
			{
				String[] p = s.split(":", -1);
				styles.add(new Style(p[0], p.length > 1 ? p[1] : "", p.length > 2 ? p[2] : ""));
			}
			categoryStyles.put(c[0], Collections.unmodifiableList(styles));
		}
		for (String[] c : rows(monsterTable))
		{
			Monster m = new Monster(c);
			int key = Integer.parseInt(c[0]);
			m.id = Math.abs(key);
			slayerCategories.addAll(m.slayer);
			if (key < 0)
			{
				// an awakened fight: the game gives it the same NPC id as the other one
				m.awakened = true;
				awakened.put(m.id, m);
				continue;
			}
			monsters.put(m.id, m);
			if (m.name != null)
			{
				monstersByName.putIfAbsent(m.name.toLowerCase(java.util.Locale.ROOT), m);
			}
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

	/** The row of an NPC id; for the negative of an id, the awakened fight of that id ({@link #awakened}). */
	Monster monster(int npcId)
	{
		return npcId < 0 ? awakened.get(-npcId) : monsters.get(npcId);
	}

	/**
	 * The fight the game is in with this row's monster, by the most hitpoints the game's boss health display shows for
	 * it (0 when it shows none): the awakened fight of Desert Treasure II's bosses when the display has more than the
	 * row has (the Leviathan, the Whisperer: 2700 against 900 after the quest), and the row itself otherwise.
	 */
	Monster inFight(Monster monster, int displayedHitpoints)
	{
		if (monster == null || displayedHitpoints <= monster.hitpoints)
		{
			return monster;
		}
		Monster other = awakened.get(monster.id);
		return other != null && other != monster ? other : monster;
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

	/** Every monster of the table, one per row but the awakened fights' ({@link #inFight}), read only. */
	Collection<Monster> monsters()
	{
		return Collections.unmodifiableCollection(monsters.values());
	}

	/** Every Slayer category the table knows, as {@link SetEffects#taskKey} writes them. */
	Set<String> slayerCategories()
	{
		return slayerCategories;
	}

	/** How many monsters, weapons and weapon categories the tables hold: a table read from elsewhere is weighed against these. */
	int monsterCount()
	{
		return monsters.size();
	}

	int weaponCount()
	{
		return weaponCategory.size();
	}

	int categoryCount()
	{
		return categoryStyles.size();
	}

	/** The text of a file bundled with the plugin. */
	static String resource(String file) throws IOException
	{
		InputStream in = StyleData.class.getResourceAsStream(file);
		if (in == null)
		{
			throw new IOException("missing resource " + file);
		}
		StringBuilder text = new StringBuilder();
		try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8)))
		{
			char[] buffer = new char[8192];
			int n;
			while ((n = r.read(buffer)) > 0)
			{
				text.append(buffer, 0, n);
			}
		}
		return text.toString();
	}

	/** The rows of a table's text, tab separated; empty lines and lines starting with "#" are left out. */
	private static List<String[]> rows(String text) throws IOException
	{
		List<String[]> rows = new ArrayList<>();
		try (BufferedReader r = new BufferedReader(new StringReader(text)))
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
