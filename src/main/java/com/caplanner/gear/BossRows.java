/*
 * Copyright (c) 2026, GuimGod
 * All rights reserved. Licensed under the BSD 2-Clause License (see LICENSE).
 */
package com.caplanner.gear;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Which row of the monster table a boss of the Combat Achievements stands for. The table is shared with
 * the sums and goes by NPC id; the plan goes by the boss's name, so the choice of row is made here.
 */
final class BossRows
{
	/** Rows from this id on are written for another purpose, not monsters of the game (the "Yama" of 100001). */
	private static final int MADE_UP_IDS = 100000;
	/** Bosses whose row to use is not the one with the most hitpoints: Scurrius alone has 500, in a group 1,500. */
	private static final Map<String, Integer> FOUGHT_ALONE = new HashMap<>();

	static
	{
		FOUGHT_ALONE.put("scurrius", 7222);
	}

	private BossRows()
	{
	}

	/**
	 * The monster a boss's name stands for, with or without a leading "The". When the table has
	 * several versions of it (a quest fight, a later phase), the one with the most hitpoints: the
	 * fight a kill is counted on. Null when the table has no monster of that name.
	 */
	static StyleData.Monster named(StyleData data, String bossName)
	{
		if (bossName == null)
		{
			return null;
		}
		String wanted = bare(bossName);
		StyleData.Monster best = null;
		for (StyleData.Monster m : data.monsters())
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
		return alone != null && data.monster(alone) != null ? data.monster(alone) : best;
	}

	private static String bare(String name)
	{
		String n = name.toLowerCase(Locale.ROOT).trim();
		return n.startsWith("the ") ? n.substring(4) : n;
	}

	/**
	 * Whether a boss is fought in the Wilderness, where the revenant weapons deal more. The engine has this from the
	 * game; the planner, which works out a fight before it starts, goes by the boss's name.
	 */
	static boolean isInWilderness(String boss)
	{
		String b = boss == null ? "" : boss.toLowerCase(Locale.ROOT);
		for (String name : new String[]{"callisto", "artio", "vet'ion", "calvar'ion", "venenatis", "spindel",
			"chaos elemental", "chaos fanatic", "crazy archaeologist", "scorpia"})
		{
			if (b.equals(name))
			{
				return true;
			}
		}
		return false;
	}
}
