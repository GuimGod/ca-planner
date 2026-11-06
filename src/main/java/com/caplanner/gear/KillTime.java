/*
 * Copyright (c) 2026, GuimGod
 * All rights reserved. Licensed under the BSD 2-Clause License (see LICENSE).
 */
package com.caplanner.gear;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * How long a weapon takes to kill a monster, as the OSRS Wiki DPS calculator works it out (PlayerVsNPCCalc.getHtk and
 * getTtk): from the chance of each damage one attack deals (all its hitsplats summed), the mean number of attacks until
 * the monster's hitpoints are gone, a hit beyond what is left wasted; times the ticks between attacks. Against a
 * monster of few hitpoints a fast weapon that kills in one hit beats a slow one that deals more per second.
 */
final class KillTime
{
	/** Above this many hitpoints the figure per tick is used: a hit's waste is nothing there, and the work is long. */
	static final int MOST_HITPOINTS = 10000;

	private KillTime()
	{
	}

	/** A damage of exactly {@code damage}, for sure. */
	static double[] certain(int damage)
	{
		double[] p = new double[Math.max(0, damage) + 1];
		p[Math.max(0, damage)] = 1;
		return p;
	}

	/** The damage of one hitsplat that lands with {@code chance}, its landed damage given, and is 0 otherwise. */
	static double[] withChance(double chance, double[] landed)
	{
		double[] p = landed.clone();
		for (int i = 0; i < p.length; i++)
		{
			p[i] *= chance;
		}
		p[0] += 1 - chance;
		return p;
	}

	/** The sum of two damages rolled on their own. */
	static double[] sum(double[] a, double[] b)
	{
		double[] s = new double[a.length + b.length - 1];
		for (int i = 0; i < a.length; i++)
		{
			if (a[i] == 0)
			{
				continue;
			}
			for (int j = 0; j < b.length; j++)
			{
				s[i + j] += a[i] * b[j];
			}
		}
		return s;
	}

	/** {@code a} with chance {@code share}, {@code b} otherwise. */
	static double[] mix(double share, double[] a, double[] b)
	{
		double[] m = new double[Math.max(a.length, b.length)];
		for (int i = 0; i < a.length; i++)
		{
			m[i] += share * a[i];
		}
		for (int i = 0; i < b.length; i++)
		{
			m[i] += (1 - share) * b[i];
		}
		return m;
	}

	/** The mean damage. */
	static double mean(double[] p)
	{
		double m = 0;
		for (int i = 1; i < p.length; i++)
		{
			m += i * p[i];
		}
		return m;
	}

	/** The last results: the same weapon on the same monster is worked out once, not on every game tick. */
	private static final Map<String, Double> SEEN = new LinkedHashMap<String, Double>(64, 0.75f, true)
	{
		@Override
		protected boolean removeEldestEntry(Map.Entry<String, Double> eldest)
		{
			return size() > 64;
		}
	};

	/**
	 * The mean number of attacks to take {@code hitpoints} down to nothing, each attack's damage as {@code damage} gives
	 * it (the calculator's getHtk): attacks of 0 are retried, a hit of more than is left kills. Infinite when no attack
	 * can deal anything; 0 for a monster with no hitpoints.
	 */
	static double hitsToKill(double[] damage, int hitpoints)
	{
		if (hitpoints <= 0)
		{
			return 0;
		}
		double nothing = damage.length == 0 ? 1 : damage[0];
		if (nothing >= 1)
		{
			return Double.POSITIVE_INFINITY;
		}
		String key = hitpoints + ":" + damage.length + ":" + Arrays.hashCode(damage);
		synchronized (SEEN)
		{
			Double known = SEEN.get(key);
			if (known != null)
			{
				return known;
			}
		}
		int top = Math.min(hitpoints, damage.length - 1);
		double[] htk = new double[hitpoints + 1];
		for (int hp = 1; hp <= hitpoints; hp++)
		{
			// at least this attack; the ones that leave some hitpoints add what killing those takes
			double val = 1;
			int most = Math.min(hp, top);
			for (int hit = 1; hit <= most; hit++)
			{
				val += damage[hit] * htk[hp - hit];
			}
			htk[hp] = val / (1 - nothing);
		}
		synchronized (SEEN)
		{
			SEEN.put(key, htk[hitpoints]);
		}
		return htk[hitpoints];
	}
}
