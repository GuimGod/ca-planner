/*
 * Copyright (c) 2026, GuimGod
 * All rights reserved. Licensed under the BSD 2-Clause License (see LICENSE).
 */
package com.caplanner.gear;

/**
 * The Tombs of Amascut's scaling. The raid level does not change any level: it multiplies the monster's defence roll by
 * (250 + raid level) / 250, except for Kephri's scarab overlords (the calculator's {@code getNPCDefenceRoll}), and with
 * the path level and the party it raises the hitpoints. The hitpoints are a port of the OSRS Wiki DPS calculator's
 * {@code lib/scaling/TombsOfAmascut.ts}, with the same rounding: 0.4% for each raid level (0.1% for the Wardens' core,
 * which starts at 4500), then 8% plus 5% for each path level for the four paths' monsters, then 90% for the second
 * player, 90% more for the third and 60% for each one from the fourth, and last a round to 5 above 100 and to 10 above
 * 300.
 *
 * <p>Which monsters are the raid's, their path and the ones the calculator treats apart are a column of the monster
 * table (tools/gen_data.py). It comes after the Theatre of Blood's scaling and before the special attacks' drains, as in
 * the calculator ({@code MonsterScaling.ts}); the defence roll is scaled after the drains, when it is rolled.
 */
final class ToaScaling
{
	/** What the scaling reads from the game. */
	static final class Raid
	{
		/** The raid level (the invocations' sum), 0 or more. */
		final int invocation;
		/** The party, 1 to 8; 0 when it is not known (then the hitpoints are not scaled for a party). */
		final int partySize;
		/** The level of each path: Crondis (Zebak), Scabaras (Kephri), Het (Akkha), Apmeken (Ba-Ba). */
		final int crondis;
		final int scabaras;
		final int het;
		final int apmeken;

		Raid(int invocation, int partySize, int crondis, int scabaras, int het, int apmeken)
		{
			this.invocation = Math.max(0, invocation);
			this.partySize = Math.max(0, partySize);
			this.crondis = crondis;
			this.scabaras = scabaras;
			this.het = het;
			this.apmeken = apmeken;
		}

		/** The level of the path a monster ends; 0 for a monster of no path. */
		int pathLevel(char path)
		{
			switch (path)
			{
				case 'C':
					return crondis;
				case 'S':
					return scabaras;
				case 'H':
					return het;
				case 'A':
					return apmeken;
				default:
					return 0;
			}
		}

		@Override
		public boolean equals(Object o)
		{
			if (!(o instanceof Raid))
			{
				return false;
			}
			Raid r = (Raid) o;
			return invocation == r.invocation && partySize == r.partySize && crondis == r.crondis
				&& scabaras == r.scabaras && het == r.het && apmeken == r.apmeken;
		}

		@Override
		public int hashCode()
		{
			return ((((invocation * 31 + partySize) * 31 + crondis) * 31 + scabaras) * 31 + het) * 31 + apmeken;
		}

		@Override
		public String toString()
		{
			return "raid level " + invocation + ", " + partySize + " players, paths " + crondis + "/" + scabaras + "/" + het
				+ "/" + apmeken;
		}
	}

	/** The Wardens' core starts at this many hitpoints, whatever the table says. */
	static final int CORE_HITPOINTS = 4500;

	private ToaScaling()
	{
	}

	/** The raid level the monster's defence roll is scaled with: 0 outside the raid, with no raid and for the overlords. */
	static int invocation(StyleData.Monster m, Raid raid)
	{
		return raid == null || !m.toa || m.toaRollUnscaled ? 0 : raid.invocation;
	}

	/** A defence roll scaled for the raid level: (250 + raid level) / 250 of it, rounded down. */
	static long defenceRoll(long roll, int invocation)
	{
		return invocation <= 0 ? roll : roll * (250 + invocation) / 250;
	}

	/** The monster's hitpoints in this raid: its own when it is not a monster of the raid or there is no raid. */
	static int hitpoints(StyleData.Monster m, int hitpoints, Raid raid)
	{
		if (raid == null || !m.toa)
		{
			return hitpoints;
		}
		int hp = m.toaCore ? CORE_HITPOINTS : hitpoints;
		int invocationFactor = (m.toaCore ? 1 : 4) * raid.invocation / 10;
		hp += hp * invocationFactor / 100;
		int path = Math.min(6, Math.max(0, raid.pathLevel(m.toaPath)));
		if (m.toaPath != 0 && path >= 1)
		{
			hp = hp * (100 + 3 + 5 * path) / 100;
		}
		int party = Math.min(8, raid.partySize);
		if (party >= 2)
		{
			int partyFactor = 9 * (party >= 3 ? 2 : 1);
			if (party >= 4)
			{
				partyFactor += 6 * (party - 3);
			}
			hp = hp * (10 + partyFactor) / 10;
		}
		if (hp > 100)
		{
			int roundTo = hp > 300 ? 10 : 5;
			hp = (hp + roundTo / 2) / roundTo * roundTo;
		}
		return hp;
	}

	/** Scales the hitpoints of {@code l} (the monster's levels so far) for the raid. */
	static void apply(StyleData.Monster m, Raid raid, DefenceDrains.Levels l)
	{
		l.hitpoints = hitpoints(m, l.hitpoints, raid);
	}
}
