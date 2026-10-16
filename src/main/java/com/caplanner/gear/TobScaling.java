/*
 * Copyright (c) 2026, GuimGod
 * All rights reserved. Licensed under the BSD 2-Clause License (see LICENSE).
 */
package com.caplanner.gear;

/**
 * The Theatre of Blood's scaling: only the hitpoints grow or shrink with the party. A port of the OSRS Wiki DPS
 * calculator's {@code lib/scaling/TheatreOfBlood.ts}, with the same rounding (down). In normal and Hard Mode a party of
 * 3 or fewer gets 75% of the hitpoints, 4 gets 87.5% and 5 the full ones (OSRS Wiki, Theatre of Blood); Entry Mode has
 * its own table, 10, 19, 27, 34 and 40 fortieths for 1 to 5 players (the calculator's; the wiki's boss pages say "down to
 * 20%" for one player). Hard Mode and Entry Mode monsters have NPC ids and levels of their own: no level scales.
 *
 * <p>Which ids scale and how is a column of the monster table (tools/gen_data.py, from the calculator's
 * {@code TOB_MONSTER_IDS} and {@code TOB_EM_MONSTER_IDS}). It comes after the Chambers of Xeric's scaling and before the
 * Tombs of Amascut's, as in the calculator ({@code MonsterScaling.ts}).
 */
final class TobScaling
{
	/** Entry Mode's share of the hitpoints, in fortieths, for 1 to 5 players. */
	private static final int[] ENTRY_FORTIETHS = {10, 19, 27, 34, 40};

	/** The party the raid was scaled for, as read from the game. */
	static final class Raid
	{
		final int partySize;

		Raid(int partySize)
		{
			this.partySize = partySize;
		}

		@Override
		public boolean equals(Object o)
		{
			return o instanceof Raid && ((Raid) o).partySize == partySize;
		}

		@Override
		public int hashCode()
		{
			return partySize;
		}

		@Override
		public String toString()
		{
			return partySize + " players";
		}
	}

	private TobScaling()
	{
	}

	/** The monster's hitpoints for this party: its own when it is not a monster of the Theatre or there is no raid. */
	static int hitpoints(StyleData.Monster m, int hitpoints, Raid raid)
	{
		if (raid == null || m.tobScaling == 0)
		{
			return hitpoints;
		}
		if (m.tobScaling == 'b')
		{
			int n = Math.min(5, Math.max(3, raid.partySize));
			return hitpoints * (n + 3) / 8;
		}
		int n = Math.min(5, Math.max(1, raid.partySize));
		return hitpoints * ENTRY_FORTIETHS[n - 1] / 40;
	}

	/** Scales the hitpoints of {@code l} (the monster's levels so far) for the party. */
	static void apply(StyleData.Monster m, Raid raid, DefenceDrains.Levels l)
	{
		l.hitpoints = hitpoints(m, l.hitpoints, raid);
	}
}
