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
 */
public final class BossIcons
{
	/** Combat Achievements name to hiscores name, both as {@link #key} writes them. */
	private static final Map<String, String> OTHER_NAMES = new HashMap<>();

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
	}

	private BossIcons()
	{
	}

	private static void same(String achievements, String hiscores)
	{
		OTHER_NAMES.put(key(achievements), key(hiscores));
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
