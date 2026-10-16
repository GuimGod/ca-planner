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
		/** Osmumten's fang: rolls accuracy twice on stab, and narrows its damage range with every style. */
		boolean fang;
		/** A weapon that hits twice, up to half the max each (Torag's hammers, the Glacial temotli...). */
		boolean twoHits;
		/** A weapon that deals full damage to the Corporeal Beast on stab (fang, halberd, spear). */
		boolean corpbane;

		int defenceLevel;
		int defenceStab;
		int defenceSlash;
		int defenceCrush;
		/** The Corporeal Beast halves melee damage from anything but a corpbane weapon on stab. */
		boolean corporealBeast;
		/** A monster every attack lands on, whatever the style (the Wardens of the second phase, the Doom's shield and burrow). */
		boolean alwaysHit;
		/**
		 * The share of the attacks that meet the monster without defence and land, the others rolling against it: the
		 * Tormented Demon's (SetCalc.Target#defencelessShare), 0 for any other.
		 */
		double defencelessShare;
		/** Slayer helmet on task or Salve amulet against the undead: multiplies the attack roll and the max hit. */
		int bonusNumerator = 1;
		int bonusDenominator = 1;
		/** What a weapon with a rule of its own does to the attack roll and to the max hit (a demonbane weapon on a demon). */
		double weaponAccuracy = 1;
		double weaponDamage = 1;
		/** The same in whole numbers, each rounded down as the calculator does (the keris partisans: 115/100, 133/100). */
		int accuracyNumerator = 1;
		int accuracyDenominator = 1;
		int damageNumerator = 1;
		int damageDenominator = 1;
		/** The whole obsidian armour with a TzHaar weapon: a tenth of the accuracy and of the max hit before the bonuses is added. */
		boolean obsidian;
		/** Added to the max hit after everything else (the Colossal blade, by the monster's size). */
		int maxAdd;
		/** The whole melee Void set: a tenth more effective Attack and Strength, rounded down, before the gear's bonus. */
		boolean voidMelee;
		/** A monster every melee attack lands on at the max hit (the calculator's ALWAYS_MAX_HIT_MONSTERS). */
		boolean alwaysMax;
		/** The ranged and magic attack bonuses worn: with the three of melee, which one is the highest. */
		int ranged;
		int magic;
		/**
		 * Araxxor's araxytes ({@link Weapons#araxyteMax}): every attack of the Noxious halberd ({@link #noxiousHalberd}),
		 * and of a crush style while crush is the highest attack bonus worn, lands at the max hit.
		 */
		boolean araxyteMax;
		boolean noxiousHalberd;
		/** A hit kills the monster (Scurrius's rats, the Flowers): every style deals the same, its hitpoints. */
		boolean oneHit;
		/** A monster of the Tombs of Amascut: Osmumten's fang rolls its accuracy twice the raid's way, 1 - (1 - p)^2. */
		boolean toa;
		/** The raid level the monster's defence roll is multiplied by, as (250 + it) / 250; 0 for none. */
		int defenceRollInvocation;
		/**
		 * The Hueycoatl's tail: each hit that lands is limited to a random 0 to 4, or 0 to 9 with a crush style while crush
		 * is the highest of the stab, slash and crush bonuses (then a hit left at nothing, a miss too, deals 1).
		 */
		boolean hueycoatlTail;
		/** Verzik's first phase: each melee hit that lands is limited to a random 0 to 10. */
		boolean verzikP1;
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
		// Osmumten's fang deals 15% to 85% of its max hit with every style; its double roll is for stab only
		long min = in.fang ? max * 3 / 20 : 0;
		if (in.oneHit)
		{
			// every style that can deal anything deals the monster's hitpoints: a tie
			return max - min > 0 ? 1 : 0;
		}
		boolean araxyte = in.araxyteMax && (in.noxiousHalberd
			|| CRUSH.equals(type) && "crush".equals(SetCalc.highestBonus(in.stab, in.slash, in.crush, in.ranged, in.magic)));
		if (in.alwaysMax || araxyte)
		{
			return Math.max(1, max - min);
		}
		// each hit limited to a random 0 to 10 on Verzik's first phase; on the Hueycoatl's tail 0 to 4, or 0 to 9 for
		// crush while crush is the highest bonus (a miss then deals 1)
		boolean crushRule = in.hueycoatlTail && CRUSH.equals(type) && in.crush > in.slash && in.crush > in.stab;
		int limit = in.verzikP1 ? 10 : in.hueycoatlTail ? (crushRule ? 9 : 4) : 0;
		double average = in.twoHits ? between(0, max / 2, limit, crushRule) + between(0, max - max / 2, limit, crushRule)
			: between(min, max - min, limit, crushRule);
		if (in.corporealBeast && !(in.corpbane && STAB.equals(type)))
		{
			average /= 2;
		}
		return cm[0] * average + (crushRule ? (in.twoHits ? 2 : 1) * (1 - cm[0]) : 0);
	}

	/**
	 * Mean of a hit rolled evenly from min to max that landed, limited as on Verzik's first phase or the Hueycoatl's tail
	 * when {@code limit} is set.
	 */
	private static double between(long min, long max, int limit, boolean zeroToOne)
	{
		if (limit <= 0)
		{
			return averageBetween(min, max);
		}
		if (max < min)
		{
			return 0;
		}
		double sum = 0;
		for (long h = min; h <= max; h++)
		{
			sum += SetCalc.limitedMean(Math.max(1, h), limit, zeroToOne);
		}
		return sum / (max - min + 1);
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
		long baseRoll = attackRoll;
		long baseMax = max;
		attackRoll = attackRoll * in.bonusNumerator / in.bonusDenominator;
		max = max * in.bonusNumerator / in.bonusDenominator;
		if (in.obsidian)
		{
			// a tenth of the roll and of the max hit before the Salve amulet or the Slayer helmet (the calculator's)
			attackRoll += baseRoll / 10;
			max += baseMax / 10;
		}
		attackRoll = (long) (attackRoll * in.weaponAccuracy) * in.accuracyNumerator / in.accuracyDenominator;
		max = (long) (max * in.weaponDamage) * in.damageNumerator / in.damageDenominator + in.maxAdd;
		if (CRUSH.equals(type) && in.inquisitor > 0)
		{
			attackRoll = attackRoll * (200 + in.inquisitor) / 200;
			max = max * (200 + in.inquisitor) / 200;
		}
		// in the Tombs of Amascut the roll grows with the raid level, after the drains (the calculator's order)
		long defenceRoll = ToaScaling.defenceRoll((long) (in.defenceLevel + 9) * (defenceBonus + 64), in.defenceRollInvocation);

		boolean fangStab = in.fang && STAB.equals(type);
		double chance = in.alwaysHit ? 1 : fangStab ? (in.toa ? fangChanceInTheTombs(attackRoll, defenceRoll)
			: fangChance(attackRoll, defenceRoll)) : chance(attackRoll, defenceRoll);
		if (!in.alwaysHit && in.defencelessShare > 0)
		{
			chance = in.defencelessShare + (1 - in.defencelessShare) * chance;
		}
		return new double[]{chance, max};
	}

	/** Osmumten's fang inside the Tombs of Amascut: two rolls of the ordinary chance, 1 - (1 - p)^2 (the calculator's). */
	static double fangChanceInTheTombs(long attack, long defence)
	{
		double p = chance(attack, defence);
		return 1 - (1 - p) * (1 - p);
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
