/*
 * Copyright (c) 2026, GuimGod
 * All rights reserved. Licensed under the BSD 2-Clause License (see LICENSE).
 */
package com.caplanner.gear;

/**
 * The melee damage formula, reduced to what differs between the attack styles of one weapon:
 * the attack bonus and the monster's defence for the style's damage type, the stance, and the
 * few effects that only apply to one damage type. Everything that multiplies all the styles
 * alike (Slayer helmet, Salve amulet, a weapon hitting several times) is left out, so the
 * result is a score to compare styles with, not a DPS figure.
 *
 * <p>The formulas follow the OSRS Wiki DPS calculator; the tests compare the best style with
 * the calculator's over a few thousand weapon and monster pairs.
 */
final class StyleCalc
{
	static final String STAB = "stab";
	static final String SLASH = "slash";
	static final String CRUSH = "crush";

	/** The player and the monster, as far as the comparison needs them. */
	static final class Input
	{
		/** Boosted Attack and Strength levels. */
		int attackLevel;
		int strengthLevel;
		/** Prayer multipliers as percentages: 100 for none, 120 and 123 for Piety. */
		int prayerAttack = 100;
		int prayerStrength = 100;
		/** Worn attack bonuses and melee strength bonus. */
		int stab;
		int slash;
		int crush;
		int strength;
		/** Inquisitor's pieces worn, weighted as the game does: helm 1, hauberk 2, plateskirt 2. */
		int inquisitor;
		/** Osmumten's fang: rolls accuracy twice and narrows its damage range, on stab only. */
		boolean fang;
		/** A weapon that deals full damage to the Corporeal Beast on stab (fang, halberd, spear). */
		boolean corpbane;

		int defenceLevel;
		int defenceStab;
		int defenceSlash;
		int defenceCrush;
		/** The Corporeal Beast halves melee damage from anything but a corpbane weapon on stab. */
		boolean corporealBeast;
		/** A monster every attack lands on, whatever the style (the calculator's rule for the Tormented Demon). */
		boolean alwaysHit;
		/** Slayer helmet on task or Salve amulet against the undead: multiplies the attack roll and the max hit. */
		int bonusNumerator = 1;
		int bonusDenominator = 1;
		/** What a weapon with a rule of its own does to the attack roll and to the max hit (a demonbane weapon on a demon). */
		double weaponAccuracy = 1;
		double weaponDamage = 1;
		/** Added to the max hit after everything else (the Colossal blade, by the monster's size). */
		int maxAdd;
		/** The whole melee Void set: a tenth more effective Attack and Strength, rounded down, before the gear's bonus. */
		boolean voidMelee;
	}

	private StyleCalc()
	{
	}

	static final String MELEE = "melee";
	static final String RANGED = "ranged";
	static final String MAGIC = "magic";

	static boolean melee(String type)
	{
		return STAB.equals(type) || SLASH.equals(type) || CRUSH.equals(type);
	}

	/** melee, ranged or magic for a style's damage type; null for a style that does not attack. */
	static String combatClass(String type)
	{
		return melee(type) ? MELEE : RANGED.equals(type) ? RANGED : MAGIC.equals(type) ? MAGIC : null;
	}

	/** Expected damage of one attack with this damage type and stance; 0 when the type is not melee. */
	static double expectedHit(Input in, String type, String stance)
	{
		if (!melee(type))
		{
			return 0;
		}
		double[] cm = chanceAndMax(in, type, stance);
		long max = (long) cm[1];
		boolean fangStab = in.fang && STAB.equals(type);
		double average = fangStab ? averageBetween(max * 3 / 20, max - max * 3 / 20) : averageBetween(0, max);
		if (in.corporealBeast && !(in.corpbane && STAB.equals(type)))
		{
			average /= 2;
		}
		return cm[0] * average;
	}

	/** The chance to hit and the max hit of one attack with this damage type and stance. */
	static double[] chanceAndMax(Input in, String type, String stance)
	{
		int attackBonus = STAB.equals(type) ? in.stab : SLASH.equals(type) ? in.slash : in.crush;
		int defenceBonus = STAB.equals(type) ? in.defenceStab : SLASH.equals(type) ? in.defenceSlash : in.defenceCrush;

		long attack = (long) in.attackLevel * in.prayerAttack / 100 + 8;
		long strength = (long) in.strengthLevel * in.prayerStrength / 100 + 8;
		if ("Accurate".equals(stance))
		{
			attack += 3;
		}
		else if ("Aggressive".equals(stance))
		{
			strength += 3;
		}
		else if ("Controlled".equals(stance))
		{
			attack += 1;
			strength += 1;
		}
		if (in.voidMelee)
		{
			attack = attack * 11 / 10;
			strength = strength * 11 / 10;
		}

		long attackRoll = attack * (attackBonus + 64);
		long max = (strength * (in.strength + 64) + 320) / 640;
		attackRoll = attackRoll * in.bonusNumerator / in.bonusDenominator;
		max = max * in.bonusNumerator / in.bonusDenominator;
		attackRoll = (long) (attackRoll * in.weaponAccuracy);
		max = (long) (max * in.weaponDamage) + in.maxAdd;
		if (CRUSH.equals(type) && in.inquisitor > 0)
		{
			attackRoll = attackRoll * (200 + in.inquisitor) / 200;
			max = max * (200 + in.inquisitor) / 200;
		}
		long defenceRoll = (long) (in.defenceLevel + 9) * (defenceBonus + 64);

		boolean fangStab = in.fang && STAB.equals(type);
		double chance = in.alwaysHit ? 1 : fangStab ? fangChance(attackRoll, defenceRoll) : chance(attackRoll, defenceRoll);
		return new double[]{chance, max};
	}

	/** Mean of a hit rolled evenly from min to max, where a successful hit of 0 counts as 1. */
	static double averageBetween(long min, long max)
	{
		if (max < min)
		{
			return 0;
		}
		double sum = 0;
		for (long h = min; h <= max; h++)
		{
			sum += Math.max(1, h);
		}
		return sum / (max - min + 1);
	}

	static double chance(long attack, long defence)
	{
		if (attack < 0)
		{
			attack = Math.min(0, attack + 2);
		}
		if (defence < 0)
		{
			defence = Math.min(0, defence + 2);
		}
		if (attack >= 0 && defence >= 0)
		{
			return standard(attack, defence);
		}
		if (attack >= 0)
		{
			return 1 - 1.0 / (-defence + 1) / (attack + 1);
		}
		if (defence >= 0)
		{
			return 0;
		}
		return standard(-defence, -attack);
	}

	private static double standard(long attack, long defence)
	{
		return attack > defence
			? 1 - (defence + 2) / (2.0 * (attack + 1))
			: attack / (2.0 * (defence + 1));
	}

	/** Osmumten's fang outside the Tombs of Amascut: the better of two accuracy rolls. */
	static double fangChance(long attack, long defence)
	{
		if (attack < 0 || defence < 0)
		{
			return chance(attack, defence);
		}
		double a = attack;
		double d = defence;
		return attack > defence
			? 1 - (d + 2) * (2 * d + 3) / (a + 1) / (a + 1) / 6
			: a * (4 * a + 5) / 6 / (a + 1) / (d + 1);
	}
}
