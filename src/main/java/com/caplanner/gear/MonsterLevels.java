/*
 * Copyright (c) 2026, GuimGod
 * All rights reserved. Licensed under the BSD 2-Clause License (see LICENSE).
 */
package com.caplanner.gear;

/**
 * A monster's levels before the special attacks' drains, in the order of the OSRS Wiki DPS calculator's
 * {@code MonsterScaling.ts}: the Chambers of Xeric's scaling, then the Theatre of Blood's, then the Tombs of Amascut's,
 * then the phases ({@code lib/scaling/Phases.ts}: Araxxor's enrage). The drains come last ({@link DefenceDrains#apply}).
 */
final class MonsterLevels
{
	private MonsterLevels()
	{
	}

	/**
	 * The monster's levels scaled for the raid it is in. Each raid only touches the monsters of its own (the last two
	 * change only the hitpoints); a null raid leaves them as the table has them.
	 */
	static DefenceDrains.Levels scaled(StyleData.Monster monster, CoxScaling.Raid cox, TobScaling.Raid tob, ToaScaling.Raid toa)
	{
		DefenceDrains.Levels l = CoxScaling.levels(monster, cox);
		TobScaling.apply(monster, tob, l);
		ToaScaling.apply(monster, toa, l);
		return l;
	}

	/**
	 * Araxxor from a quarter of his hitpoints on: enraged, with 35 more Defence, 28 more Magic and 31 more Ranged (the
	 * calculator's phase "Enraged"; OSRS Wiki, Araxxor: "When Araxxor reaches 25% health, he enters an enrage phase").
	 * His health must be known ({@code health} above 0). Any other monster is left as it is.
	 */
	static DefenceDrains.Levels enraged(StyleData.Monster monster, DefenceDrains.Levels l, int health)
	{
		if (!monster.araxxorEnrage || health <= 0 || (long) health * 4 > l.hitpoints)
		{
			return l;
		}
		DefenceDrains.Levels e = l.copy();
		e.defence += 35;
		e.magic += 28;
		e.ranged += 31;
		return e;
	}

	/**
	 * The Wardens of the Tombs of Amascut's third phase once enraged: Defence 180 instead of 150 (the calculator's Enraged
	 * version; OSRS Wiki: below 5% of its hitpoints for the first time, the Warden heals 20% and its Defence is set to
	 * 180, which the drains can take down to 120 again). Any other monster, or a Warden not enraged, is left as it is.
	 */
	static DefenceDrains.Levels wardenEnraged(StyleData.Monster monster, DefenceDrains.Levels l, boolean enraged)
	{
		if (!enraged || monster.enragedDefence <= 0)
		{
			return l;
		}
		DefenceDrains.Levels e = l.copy();
		e.defence = monster.enragedDefence;
		return e;
	}
}
