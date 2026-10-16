/*
 * Copyright (c) 2026, GuimGod
 * All rights reserved. Licensed under the BSD 2-Clause License (see LICENSE).
 */
package com.caplanner.core;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * Finds, for a boss as the Combat Achievements name it, the entry of the hiscores that has its
 * icon. The two lists do not always spell a boss the same way, so names are compared without
 * case, spaces, punctuation or a leading "The", and the few that still differ are listed here.
 * A boss the hiscores do not have (a slayer monster, a quest boss) is drawn with an item of its own
 * instead: its pet, or without one a drop or item it is known by.
 */
public final class BossIcons
{
	/** Combat Achievements name to hiscores name, both as {@link #key} writes them. */
	private static final Map<String, String> OTHER_NAMES = new HashMap<>();
	/** Combat Achievements name, as {@link #key} writes it, to the item drawn for it. */
	private static final Map<String, Integer> ITEMS = new HashMap<>();

	static
	{
		// one fight, several difficulties: the icon is the fight's
		same("Chambers of Xeric: Challenge Mode", "Chambers of Xeric Challenge Mode");
		same("Theatre of Blood: Hard Mode", "Theatre of Blood Hard Mode");
		same("Theatre of Blood: Entry Mode", "Theatre of Blood");
		same("Tombs of Amascut: Expert Mode", "Tombs of Amascut Expert Mode");
		same("Tombs of Amascut: Entry Mode", "Tombs of Amascut");
		same("Corrupted Hunllef", "The Corrupted Gauntlet");
		same("Crystalline Hunllef", "The Gauntlet");
		same("Fortis Colosseum", "Sol Heredit");
		same("Moons of Peril", "Lunar Chests");
		same("Barrows", "Barrows Chests");
		same("Royal Titans", "The Royal Titans");
		same("TzHaar-Ket-Rak's Challenges", "TzTok-Jad");

		// not on the hiscores, and none of these has a pet: an item it drops or is known by, as its page on the
		// OSRS Wiki has it (checked 2026-10-16)
		item("Aberrant Spectre", 4168); // Nose peg, worn against them: https://oldschool.runescape.wiki/w/Aberrant_spectre
		item("Basilisk Knight", 24268); // Basilisk jaw: https://oldschool.runescape.wiki/w/Basilisk_Knight
		item("Black Dragon", 1747); // Black dragonhide: https://oldschool.runescape.wiki/w/Black_dragon
		item("Bloodveld", 13496); // Ensouled bloodveld head: https://oldschool.runescape.wiki/w/Bloodveld
		item("Brutal Black Dragon", 13511); // Ensouled dragon head: https://oldschool.runescape.wiki/w/Brutal_black_dragon
		item("Demonic Gorilla", 19529); // Zenyte shard: https://oldschool.runescape.wiki/w/Demonic_gorilla
		item("Fire Giant", 554); // Fire rune: https://oldschool.runescape.wiki/w/Fire_giant
		item("Fragment of Seren", 23962); // Crystal shard, of Prifddinas, which Song of the Elves opens: https://oldschool.runescape.wiki/w/Crystal_shard
		item("Galvek", 22114); // Mythical cape, from Dragon Slayer II: https://oldschool.runescape.wiki/w/Mythical_cape
		item("Gargoyle", 4153); // Granite maul: https://oldschool.runescape.wiki/w/Gargoyle
		item("Giants", 13475); // Ensouled giant head, of every giant of the Giants' Den: https://oldschool.runescape.wiki/w/Fire_giant
		item("Glough", 19564); // Royal seed pod, from Monkey Madness II: https://oldschool.runescape.wiki/w/Royal_seed_pod
		item("Greater Demon", 13502); // Ensouled demon head: https://oldschool.runescape.wiki/w/Greater_demon
		item("Hellhound", 13233); // Smouldering stone: https://oldschool.runescape.wiki/w/Hellhound
		item("Kurask", 20727); // Leaf-bladed battleaxe: https://oldschool.runescape.wiki/w/Kurask
		item("Lizardman Shaman", 13576); // Dragon warhammer: https://oldschool.runescape.wiki/w/Lizardman_shaman
		item("None", 25926); // Ghommal's hilt 1, the Combat Achievements' own reward: https://oldschool.runescape.wiki/w/Ghommal%27s_hilt_1
		item("Skeletal Wyvern", 6809); // Granite legs: https://oldschool.runescape.wiki/w/Skeletal_Wyvern
		item("Tormented Demon", 29580); // Tormented synapse: https://oldschool.runescape.wiki/w/Tormented_Demon
		item("Wyrm", 21009); // Dragon sword: https://oldschool.runescape.wiki/w/Wyrm
	}

	private BossIcons()
	{
	}

	private static void same(String achievements, String hiscores)
	{
		OTHER_NAMES.put(key(achievements), key(hiscores));
	}

	private static void item(String achievements, int itemId)
	{
		ITEMS.put(key(achievements), itemId);
	}

	/**
	 * @return the item to draw for a boss the hiscores do not have, or -1 when there is none
	 */
	public static int item(String boss)
	{
		Integer id = ITEMS.get(key(boss));
		return id == null ? -1 : id;
	}

	/** The bosses drawn with an item, as {@link #key} writes them. */
	static java.util.Set<String> withItems()
	{
		return java.util.Collections.unmodifiableSet(ITEMS.keySet());
	}

	/** Lower case, letters and digits only, without a leading "the". */
	public static String key(String name)
	{
		String k = name == null ? "" : name.toLowerCase().replaceAll("[^a-z0-9]", "");
		return k.startsWith("the") && k.length() > 5 ? k.substring(3) : k;
	}

	/**
	 * @param boss          a boss as the Combat Achievements name it
	 * @param hiscoresNames every name the hiscores have
	 * @return the hiscores name whose icon to use, or null when the boss is not on the hiscores
	 */
	public static String match(String boss, Collection<String> hiscoresNames)
	{
		String wanted = key(boss);
		String other = OTHER_NAMES.get(wanted);
		String found = null;
		for (String name : hiscoresNames)
		{
			String k = key(name);
			if (k.equals(wanted))
			{
				return name;
			}
			if (k.equals(other))
			{
				found = name;
			}
		}
		return found;
	}
}
