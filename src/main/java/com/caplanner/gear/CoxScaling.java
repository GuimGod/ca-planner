/*
 * Copyright (c) 2026, GuimGod
 * All rights reserved. Licensed under the BSD 2-Clause License (see LICENSE).
 */
package com.caplanner.gear;

/**
 * The Chambers of Xeric's scaling: the levels and hitpoints a monster of the raid has for the size of the party,
 * Challenge Mode and the party's highest combat and Hitpoints levels. A port of the OSRS Wiki DPS calculator's
 * {@code lib/scaling/ChambersOfXeric.ts} (getSkillMeta, applySinglesCoxScaling, applyMultiCoxScaling, applyOlmScaling),
 * with the same rounding: every fraction is rounded down. It comes before the special attacks' drains, as in the
 * calculator ({@code MonsterScaling.ts}: the raid's scaling first, the defence reductions last).
 *
 * <p>Which formula a monster takes and the ids the calculator treats apart (Tekton, the Glowing crystal, the
 * Guardians, the monsters whose Magic level counts as a defensive one) are columns of the monster table
 * (tools/gen_data.py).
 */
final class CoxScaling
{
	/** Challenge Mode adds half. */
	private static final int CM_SCALE_PERCENT = 50;

	/** What the raid's scaling reads: the party and the mode. */
	static final class Raid
	{
		/** The party the raid was scaled for, 1 to 100. */
		final int partySize;
		final boolean challengeMode;
		/** The highest combat level in the party. */
		final int maxCombat;
		/**
		 * The highest Hitpoints level in the party. The game gives no varbit for it: the plugin takes 99, the
		 * calculator's default and what nearly every raid party has.
		 */
		final int maxHitpoints;
		/** The sum of the party's Mining levels: the Guardians' hitpoints. The plugin takes 99 for each member. */
		final int sumMining;

		Raid(int partySize, boolean challengeMode, int maxCombat, int maxHitpoints, int sumMining)
		{
			this.partySize = Math.max(1, partySize);
			this.challengeMode = challengeMode;
			this.maxCombat = maxCombat;
			this.maxHitpoints = maxHitpoints;
			this.sumMining = sumMining;
		}

		/** A party of {@code partySize} at combat level 126 and 99 Hitpoints and Mining. */
		Raid(int partySize, boolean challengeMode)
		{
			this(partySize, challengeMode, 126, 99, 99 * Math.max(1, partySize));
		}

		@Override
		public boolean equals(Object o)
		{
			if (!(o instanceof Raid))
			{
				return false;
			}
			Raid r = (Raid) o;
			return partySize == r.partySize && challengeMode == r.challengeMode && maxCombat == r.maxCombat
				&& maxHitpoints == r.maxHitpoints && sumMining == r.sumMining;
		}

		@Override
		public int hashCode()
		{
			return ((partySize * 31 + (challengeMode ? 1 : 0)) * 31 + maxCombat) * 31 + maxHitpoints;
		}

		@Override
		public String toString()
		{
			return (challengeMode ? "Challenge Mode, " : "") + partySize + " players, highest combat " + maxCombat;
		}
	}

	private CoxScaling()
	{
	}

	/**
	 * The monster's levels in this raid: its own ones ({@link #unscaled}) when it is not a monster of the raid's
	 * scaling or there is no raid.
	 */
	static DefenceDrains.Levels levels(StyleData.Monster m, Raid raid)
	{
		DefenceDrains.Levels l = unscaled(m);
		if (raid == null || m.coxScaling == 0 || !m.xerician)
		{
			return l;
		}
		switch (m.coxScaling)
		{
			case 's':
				singles(m, raid, l);
				break;
			case 'H':
			case 'L':
			case 'R':
				olm(m, raid, l);
				break;
			default:
				multi(m, raid, l);
				break;
		}
		return l;
	}

	/** The table's levels, as they are. */
	static DefenceDrains.Levels unscaled(StyleData.Monster m)
	{
		DefenceDrains.Levels l = new DefenceDrains.Levels();
		l.attack = m.attack;
		l.strength = m.strength;
		l.defence = m.level;
		l.magic = m.magicLevel;
		l.ranged = m.ranged;
		l.magicDefence = m.magic;
		l.hitpoints = m.hitpoints;
		return l;
	}

	/** {@code getSkillMeta}: the highest of the offensive levels and of the defensive ones, a level of 1 left out. */
	private static int baseOffensive(StyleData.Monster m)
	{
		int best = -1;
		for (int level : offensives(m))
		{
			if (level != 1)
			{
				best = Math.max(best, level);
			}
		}
		return best < 0 ? 1 : best;
	}

	private static int baseDefensive(StyleData.Monster m)
	{
		int best = m.level != 1 ? m.level : -1;
		if (m.coxMagicDefensive && m.magicLevel != 1)
		{
			best = Math.max(best, m.magicLevel);
		}
		return best < 0 ? 1 : best;
	}

	private static int[] offensives(StyleData.Monster m)
	{
		return m.coxMagicDefensive ? new int[]{m.attack, m.strength, m.ranged}
			: new int[]{m.attack, m.strength, m.ranged, m.magicLevel};
	}

	/** Gives every offensive level that is not 1 the value {@code offensive}, and every defensive one {@code defensive}. */
	private static void set(StyleData.Monster m, DefenceDrains.Levels l, int offensive, int defensive)
	{
		if (m.attack != 1)
		{
			l.attack = offensive;
		}
		if (m.strength != 1)
		{
			l.strength = offensive;
		}
		if (m.ranged != 1)
		{
			l.ranged = offensive;
		}
		if (m.level != 1)
		{
			l.defence = defensive;
		}
		if (m.magicLevel != 1)
		{
			l.magic = m.coxMagicDefensive ? defensive : offensive;
		}
	}

	private static int baseHitpoints(StyleData.Monster m, Raid raid)
	{
		return m.coxGuardian ? 151 + raid.sumMining / raid.partySize : m.hitpoints;
	}

	private static int addPercent(int x, int percent)
	{
		return x + x * percent / 100;
	}

	private static int clamp(int x, int low, int high)
	{
		return Math.max(Math.min(x, high), low);
	}

	/** {@code applySinglesCoxScaling}: the Scavenger beasts and the Vespine soldiers. */
	private static void singles(StyleData.Monster m, Raid raid, DefenceDrains.Levels l)
	{
		int hpScaler = clamp(raid.maxCombat, 60, 126);
		int statScaler = clamp(raid.maxHitpoints, 55, 99);
		if (raid.challengeMode)
		{
			statScaler = addPercent(statScaler, CM_SCALE_PERCENT);
			hpScaler = addPercent(hpScaler, CM_SCALE_PERCENT);
		}
		l.hitpoints = Math.max(baseHitpoints(m, raid) * hpScaler / 126, 5);
		set(m, l, Math.max(baseOffensive(m) * statScaler / 99, 1), Math.max(baseDefensive(m) * statScaler / 99, 1));
	}

	/** {@code applyMultiCoxScaling}: every other monster of the raid. */
	private static void multi(StyleData.Monster m, Raid raid, DefenceDrains.Levels l)
	{
		int partySize = clamp(raid.partySize, 1, 100);
		int m1 = partySize - 1;
		int highestCombat = clamp(raid.maxCombat, 60, 126);
		int highestHp = clamp(55 + 44 * raid.maxHitpoints / 99, 55, 99);
		int offensive = baseOffensive(m) * highestHp / 99;
		int defensive = baseDefensive(m) * highestHp / 99;
		int hp = baseHitpoints(m, raid) * highestCombat / 126;
		offensive = offensive * (100 + iSqrt(m1) * 7 + m1) / 100;
		defensive = defensive * (100 + iSqrt(m1) + m1 * 7 / 10) / 100;
		hp += hp * (partySize * 50 / 100);
		if (raid.challengeMode)
		{
			offensive = addPercent(offensive, CM_SCALE_PERCENT);
			// the Glowing crystal's hitpoints and Defence do not grow with the mode; Tekton's Defence grows less
			if (!m.coxGlowingCrystal)
			{
				hp = addPercent(hp, CM_SCALE_PERCENT);
				defensive = addPercent(defensive, !m.coxTekton ? CM_SCALE_PERCENT : partySize < 4 ? 20 : 35);
			}
		}
		l.hitpoints = clamp(hp, 50, 30000);
		set(m, l, clamp(offensive, 50, 5000), clamp(defensive, 50, 20000));
	}

	/** {@code applyOlmScaling}: the Great Olm's head and hands, scaled as the rooms are, then their own hitpoints. */
	private static void olm(StyleData.Monster m, Raid raid, DefenceDrains.Levels l)
	{
		int factor = Math.min(raid.partySize - 1, 50) - 3 * (Math.min(raid.partySize, 50) / 8);
		multi(m, raid, l);
		if (m.coxScaling == 'R')
		{
			l.magic = l.magic / 2;
		}
		l.hitpoints = m.coxScaling == 'H' ? 800 + 400 * factor : 600 + 300 * factor;
	}

	/** The square root rounded down, as the calculator's {@code iSqrt}. */
	static int iSqrt(int x)
	{
		return (int) Math.sqrt(x);
	}
}
