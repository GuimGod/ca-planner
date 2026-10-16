/*
 * Copyright (c) 2026, GuimGod
 * All rights reserved. Licensed under the BSD 2-Clause License (see LICENSE).
 */
package com.caplanner.gear;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.runelite.client.plugins.specialcounter.SpecialWeapon;

/**
 * The special attacks that lowered one monster's Defence (and Magic), and the levels it is left with.
 *
 * <p>Each special attack is taken off the levels the one before left, in the order they landed: the game lowers the
 * level the monster has at the time (OSRS Wiki, Dragon warhammer and Elder maul: "each reduction is calculated from the
 * current temporary level"; Bandos godsword: "drains the opponent's combat levels equal to the damage dealt"). Each one
 * is worked out as the OSRS Wiki DPS calculator works it out ({@code applyDefenceReductions}: each fraction rounded
 * down, no Defence below the monster's floor, a Bandos godsword's damage beyond the Defence left going on to Strength,
 * Attack, Magic and Ranged); the calculator only has counts and applies them in one order of its own (Accursed sceptre,
 * Elder maul, Dragon warhammer, Arclight, Emberlight, Tonalztics of Ralos, Seercull, Bandos godsword, Eye of Ayak), which
 * is the order they landed in when they landed so.
 *
 * <p>Rules the calculator does not have come from the game (OSRS Wiki): an Elder maul or Dragon warhammer that fails to
 * hit Tekton still takes 5% of his Defence, and a Bandos godsword that fails to hit him takes 10 levels; the Accursed
 * sceptre takes a level already lowered no further than 15% below the full one, and the Seercull lowers Magic only while
 * it is full; the bone dagger and the Dorgeshuun crossbow lower Defence by their damage only while it is full, and the
 * Barrelchest anchor lowers one level by a tenth of its damage. The Vulnerability spell, which the calculator also has,
 * is not a special attack: nothing reports it.
 */
final class DefenceDrains
{
	/** What a special attack that landed does to the levels. */
	private enum Kind
	{
		/** The Accursed sceptre: Defence and Magic to 85% of the full levels at most. */
		ACCURSED,
		/** The Elder maul: 35% of the Defence left. */
		ELDER_MAUL,
		/** The Dragon warhammer: 30% of the Defence left. */
		DWH,
		/** An Elder maul or Dragon warhammer that failed to hit Tekton: 5% of the Defence left. */
		TEKTON_MISS,
		/** Arclight and Darklight, which share the same special attack. */
		ARCLIGHT,
		EMBERLIGHT,
		/** One hit of the Tonalztics of Ralos' special attack that landed: an eighth of the Magic left off Defence. */
		TONALZTIC,
		/** The Seercull: its damage off Magic while Magic is full. */
		SEERCULL,
		/** The Bandos godsword: its damage off Defence, then Strength, Attack, Magic and Ranged. */
		BGS,
		/** The Eye of Ayak: its damage off the magic defence bonus. */
		AYAK,
		/** The bone dagger and the Dorgeshuun crossbow: their damage off Defence while Defence is full. */
		BONE,
		/** The Barrelchest anchor: a tenth of its damage off Defence, or Attack, Ranged or Magic past it. */
		ANCHOR
	}

	/** One special attack that landed: what it does, and its damage where that counts. */
	private static final class Drain
	{
		final Kind kind;
		final int amount;

		Drain(Kind kind, int amount)
		{
			this.kind = kind;
			this.amount = amount;
		}
	}

	/** The special attacks that lowered something, in the order they landed. */
	private final List<Drain> landed = new ArrayList<>();

	/** A monster's levels: what the damage formulas read, and what a Bandos godsword's damage goes on to. */
	static final class Levels
	{
		int attack;
		int strength;
		int defence;
		int magic;
		int ranged;
		/** The magic defence bonus (the Eye of Ayak lowers it). */
		int magicDefence;
		/** Its hitpoints when full (the raid's scaling changes them; no drain does). */
		int hitpoints;

		Levels copy()
		{
			Levels l = new Levels();
			l.attack = attack;
			l.strength = strength;
			l.defence = defence;
			l.magic = magic;
			l.ranged = ranged;
			l.magicDefence = magicDefence;
			l.hitpoints = hitpoints;
			return l;
		}
	}

	/**
	 * Counts one special attack as the Special Attack Counter reports it, after the ones counted before: {@code hit} is
	 * the damage of the hit, 1 or 0 for an Elder maul (whether it dealt damage), and the number of hits that landed for
	 * the Tonalztics of Ralos. A special attack that deals no damage lowers nothing, except on Tekton.
	 */
	void add(SpecialWeapon weapon, int hit, boolean tekton)
	{
		if (weapon == null)
		{
			return;
		}
		switch (weapon)
		{
			case DRAGON_WARHAMMER:
			case ELDER_MAUL:
				if (hit > 0)
				{
					landed(weapon == SpecialWeapon.ELDER_MAUL ? Kind.ELDER_MAUL : Kind.DWH, 0);
				}
				else if (tekton)
				{
					landed(Kind.TEKTON_MISS, 0);
				}
				break;
			case ARCLIGHT:
			case DARKLIGHT:
				if (hit > 0)
				{
					landed(Kind.ARCLIGHT, 0);
				}
				break;
			case EMBERLIGHT:
				if (hit > 0)
				{
					landed(Kind.EMBERLIGHT, 0);
				}
				break;
			case BANDOS_GODSWORD:
				if (hit > 0 || tekton)
				{
					// on Tekton a miss drains 10 (OSRS Wiki, Bandos godsword: "following the stat drain order")
					landed(Kind.BGS, hit > 0 ? hit : 10);
				}
				break;
			case TONALZTICS_OF_RALOS:
				for (int i = 0; i < Math.min(hit, 2); i++)
				{
					landed(Kind.TONALZTIC, 0);
				}
				break;
			case SEERCULL:
				if (hit > 0)
				{
					landed(Kind.SEERCULL, hit);
				}
				break;
			case EYE_OF_AYAK:
				if (hit > 0)
				{
					landed(Kind.AYAK, hit);
				}
				break;
			case ACCURSED_SCEPTRE:
				if (hit > 0)
				{
					landed(Kind.ACCURSED, 0);
				}
				break;
			case BONE_DAGGER:
			case DORGESHUUN_CROSSBOW:
				if (hit > 0)
				{
					landed(Kind.BONE, hit);
				}
				break;
			case BARRELCHEST_ANCHOR:
				if (hit > 0)
				{
					landed(Kind.ANCHOR, hit);
				}
				break;
			default:
				// Dinh's bulwark lowers nothing
				break;
		}
	}

	private void landed(Kind kind, int amount)
	{
		landed.add(new Drain(kind, amount));
	}

	/** True when nothing has been counted. */
	boolean none()
	{
		return landed.isEmpty();
	}

	/**
	 * The levels left after these special attacks, from the monster's full ones ({@code defence}, {@code magic},
	 * {@code magicDefence}) when its other levels are not known: a Bandos godsword's damage then stops at Defence.
	 */
	Levels apply(int defence, int magic, int magicDefence, boolean demon, int floor)
	{
		Levels full = new Levels();
		full.attack = StyleData.UNKNOWN_LEVEL;
		full.strength = StyleData.UNKNOWN_LEVEL;
		full.ranged = StyleData.UNKNOWN_LEVEL;
		full.defence = defence;
		full.magic = magic;
		full.magicDefence = magicDefence;
		return apply(full, demon, floor);
	}

	/**
	 * The levels left after these special attacks, from the monster's full ones (after the raid's scaling, which
	 * comes first: {@link CoxScaling}); {@code floor} is the lowest Defence it can be taken to ({@link #floor}).
	 * No other level goes below 0.
	 */
	Levels apply(Levels full, boolean demon, int floor)
	{
		Levels l = full.copy();
		for (Drain d : landed)
		{
			switch (d.kind)
			{
				case ACCURSED:
					// "If the target's levels have already been reduced, Condemn will only drain them to a maximum of
					// 15%" (OSRS Wiki, Accursed sceptre): never below 85% of the full level by itself, nothing on a level
					// already lower
					l.defence = Math.max(floor, Math.min(l.defence, full.defence * 17 / 20));
					l.magic = Math.max(0, Math.min(l.magic, full.magic * 17 / 20));
					break;
				case ELDER_MAUL:
					l.defence = Math.max(floor, l.defence - l.defence * 35 / 100);
					break;
				case DWH:
					l.defence = Math.max(floor, l.defence - l.defence * 3 / 10);
					break;
				case TEKTON_MISS:
					l.defence = Math.max(floor, l.defence - l.defence * 5 / 100);
					break;
				case ARCLIGHT:
					// Arclight and Emberlight take a part of the full levels (before any drain) from Attack, Strength
					// and Defence
					arclight(l, full, demon ? 2 : 1, floor);
					break;
				case EMBERLIGHT:
					arclight(l, full, demon ? 3 : 1, floor);
					break;
				case TONALZTIC:
					l.defence = Math.max(floor, l.defence - l.magic / 8);
					break;
				case SEERCULL:
					// "will only drain the opponent's Magic level if it is not already reduced" (OSRS Wiki, Seercull)
					if (l.magic >= full.magic)
					{
						l.magic = Math.max(0, l.magic - d.amount);
					}
					break;
				case BGS:
					bgs(l, d.amount, floor);
					break;
				case AYAK:
					if (l.magicDefence > 0)
					{
						l.magicDefence = Math.max(0, l.magicDefence - d.amount);
					}
					break;
				case BONE:
					// "only try to reduce it if it's on (or above) its base level at the time" (OSRS Wiki, Bone dagger and
					// Dorgeshuun crossbow, Mod Ash)
					if (l.defence >= full.defence)
					{
						l.defence = Math.max(floor, l.defence - d.amount);
					}
					break;
				case ANCHOR:
					anchor(l, d.amount / 10, floor);
					break;
				default:
					break;
			}
		}
		return l;
	}

	/** An Arclight-type special attack, taking {@code twentieths}/20 of the full level, plus 1. */
	private static void arclight(Levels l, Levels full, int twentieths, int floor)
	{
		l.attack = Math.max(0, l.attack - (twentieths * full.attack / 20 + 1));
		l.strength = Math.max(0, l.strength - (twentieths * full.strength / 20 + 1));
		l.defence = Math.max(floor, l.defence - (twentieths * full.defence / 20 + 1));
	}

	/**
	 * A Bandos godsword's damage: Defence first; what it leaves over goes on to Strength, Attack, Magic and Ranged, in that
	 * order, only past a level taken to 0 (a level the floor holds up stops it).
	 */
	private static void bgs(Levels l, int damage, int floor)
	{
		int left = damage;
		int start = l.defence;
		l.defence = Math.max(floor, start - left);
		left = l.defence > 0 ? 0 : left - start;
		start = l.strength;
		l.strength = Math.max(0, start - left);
		left = l.strength > 0 ? 0 : left - start;
		start = l.attack;
		l.attack = Math.max(0, start - left);
		left = l.attack > 0 ? 0 : left - start;
		start = l.magic;
		l.magic = Math.max(0, start - left);
		left = l.magic > 0 ? 0 : left - start;
		l.ranged = Math.max(0, l.ranged - left);
	}

	/**
	 * The Barrelchest anchor's drain: "Drains one stat by 10% of damage - if Defence is already on 1, it drains Attack,
	 * etc." (OSRS Wiki, Barrelchest anchor, Mod Ash), in the order Defence, Attack, Ranged, Magic, the first one above 1;
	 * the tenth rounded down (the wiki does not say how it rounds).
	 */
	private static void anchor(Levels l, int drain, int floor)
	{
		if (drain <= 0)
		{
			return;
		}
		if (l.defence > 1)
		{
			l.defence = Math.max(floor, l.defence - drain);
		}
		else if (l.attack > 1)
		{
			l.attack = Math.max(0, l.attack - drain);
		}
		else if (l.ranged > 1)
		{
			l.ranged = Math.max(0, l.ranged - drain);
		}
		else if (l.magic > 1)
		{
			l.magic = Math.max(0, l.magic - drain);
		}
	}

	/**
	 * The levels the monster fought is left with after the special attacks counted on it ({@code drains}, null for none):
	 * its full ones for a fight that takes no drain ({@link #immune}); else lowered down to its floor, by the table's row
	 * and by the NPC's own id ({@link #floor}), with what the Nightmares won back {@code ticksSinceLast} ticks after the
	 * last special attack ({@link #recovered}).
	 */
	static Levels left(StyleData.Monster monster, int npcId, Levels full, DefenceDrains drains, int ticksSinceLast)
	{
		if (drains == null || drains.none() || immune(monster) || immune(npcId))
		{
			return full;
		}
		int floor = Math.max(floor(monster.id, full.defence), floor(npcId, full.defence));
		Levels drained = drains.apply(full, monster.demon, floor);
		// the Nightmare and Phosani's Nightmare win their Defence back with time
		int back = recovered(npcId, ticksSinceLast);
		if (back > 0 && drained.defence < full.defence)
		{
			drained.defence = Math.min(full.defence, drained.defence + back);
		}
		return drained;
	}

	private static final int[] VERZIK = {10830, 10831, 10832, 8369, 8370, 8371, 10847, 10848, 10849,
		10833, 10834, 10835, 8372, 8373, 8374, 10850, 10851, 10852};
	private static final int[] VARDORVIS = {12223, 12224, 12228, 12425, 12426, 13656};
	private static final int[] SOTETSEG = {8387, 8388, 10867, 10868};
	private static final int[] NIGHTMARE = {378, 9425, 9426, 9427, 9428, 9429, 9430, 9431, 9432, 9433, 9460,
		377, 9423, 9416, 9417, 9418, 9419, 9420, 9421, 9422, 9424, 11153, 11154, 11155};
	private static final int[] AKKHA = {11789, 11790, 11791, 11792, 11793, 11794, 11795, 11796};
	private static final int[] BABA = {11778, 11779, 11780};
	private static final int[] KEPHRI = {11719, 11721};
	private static final int[] ZEBAK = {11730, 11732, 11733};
	private static final int[] WARDEN_P3 = {11761, 11763, 11762, 11764};
	private static final int[] OBELISK = {11751, 11750, 11752};
	private static final int[] NEX = {11278, 11279, 11280, 11281, 11282};
	private static final int[] ARAXXOR = {13668};
	private static final int[] HUEYCOATL = {14009, 14010, 14013, 14017, 14014};
	private static final int[] YAMA = {14176};
	/** Tekton, in each of his forms (the Special Attack Counter's list). */
	private static final int[] TEKTON = {7540, 7541, 7542, 7543, 7544, 7545};
	/** The Phantom Muspah in each of its forms (OSRS Wiki: Ranged, Melee, Teleporting, Shielded, Post-shield). */
	private static final int[] PHANTOM_MUSPAH = {12077, 12078, 12082, 12079, 12080};
	/** Its shielded form: its Defence is back to full on entering it. */
	private static final int MUSPAH_SHIELDED = 12079;
	/** The Doom of Mokhaiotl, shielded and burrowed too. */
	private static final int[] DOOM_OF_MOKHAIOTL = {14707, 14708, 14709};
	private static final int[] FRAGMENT_OF_SEREN = {8917, 8918, 8919, 8920};
	private static final int[] MENAPHITE_AKH = {11492};
	/** Galvek's four phases, each with its ids (OSRS Wiki, Galvek: Fire, Air, Water, Earth). */
	private static final int[][] GALVEK = {{8094, 8095, 8177}, {8097}, {8096}, {8098, 8178, 8179}};
	/**
	 * The fights that take no drain at all, by their own ids (OSRS Wiki): Demonic Brutus ("immune to stat drains") and the
	 * Moons of Peril ("Defence-reducing special attacks and Death Charge have no effect on the Moons of Peril").
	 */
	private static final int[] IMMUNE = {15628, 15629, 13011, 13012, 13013};

	/**
	 * The lowest Defence the monster can be taken to, by its NPC id: the calculator's {@code getDefenceFloor} (its full
	 * Defence for Verzik Vitur and Vardorvis, which cannot be drained, and 0 for most monsters), and the floors the OSRS
	 * Wiki gives that the calculator does not have: the Phantom Muspah 125 (Phantom Muspah/Strategies: "Muspah's defence can
	 * be reduced down to 125"), the Doom of Mokhaiotl 60 (Doom of Mokhaiotl/Strategies: "a defence drain cap of 30, meaning
	 * that its Defence can only be lowered to a minimum of 60"), the Fragment of Seren 60 (Fragment of Seren Speed-Trialist:
	 * "her defence cannot be lowered below 60") and the Menaphite Akh 100 (Menaphite Akh: "her defence cannot be lowered
	 * below 100", Mod Ash).
	 */
	static int floor(int npcId, int defence)
	{
		if (has(VERZIK, npcId) || has(VARDORVIS, npcId))
		{
			return defence;
		}
		if (has(SOTETSEG, npcId))
		{
			return 100;
		}
		if (has(NIGHTMARE, npcId) || has(WARDEN_P3, npcId) || has(HUEYCOATL, npcId))
		{
			return 120;
		}
		if (has(AKKHA, npcId))
		{
			return 70;
		}
		if (has(BABA, npcId) || has(KEPHRI, npcId) || has(OBELISK, npcId))
		{
			return 60;
		}
		if (has(ZEBAK, npcId))
		{
			return 50;
		}
		if (has(NEX, npcId))
		{
			return 250;
		}
		if (has(ARAXXOR, npcId))
		{
			return 90;
		}
		if (has(YAMA, npcId))
		{
			return 145;
		}
		if (has(PHANTOM_MUSPAH, npcId))
		{
			return 125;
		}
		if (has(DOOM_OF_MOKHAIOTL, npcId) || has(FRAGMENT_OF_SEREN, npcId))
		{
			return 60;
		}
		if (has(MENAPHITE_AKH, npcId))
		{
			return 100;
		}
		return 0;
	}

	/**
	 * True for a fight that takes no drain at all, whatever lands (OSRS Wiki): the awakened fights of Desert Treasure II's
	 * bosses (Hard mode: "They also become immune to stat drains, absorbing them"; the table's rows under the negative of
	 * the id), Demonic Brutus and the Moons of Peril ({@link #IMMUNE}).
	 */
	static boolean immune(StyleData.Monster monster)
	{
		return monster != null && (monster.awakened || has(IMMUNE, monster.id));
	}

	/** True for an NPC id of a fight that takes no drain at all ({@link #IMMUNE}). */
	static boolean immune(int npcId)
	{
		return has(IMMUNE, npcId);
	}

	/**
	 * True when a monster that changes from {@code oldId} into {@code newId} has its Defence back to full, whatever the
	 * table's levels of the two say (OSRS Wiki): the Phantom Muspah entering its shielded phase ("The defence reduction will
	 * preserve between phases until the shielded phase, where Muspah's defence resets to 200"), and Galvek going on to
	 * another of his phases ("he will reset his Defence to its original level after every phase"; an id the wiki does not
	 * put in a phase changes nothing).
	 */
	static boolean resets(int oldId, int newId)
	{
		if (newId == MUSPAH_SHIELDED && oldId != MUSPAH_SHIELDED && has(PHANTOM_MUSPAH, oldId))
		{
			return true;
		}
		int before = phase(GALVEK, oldId);
		int after = phase(GALVEK, newId);
		return before >= 0 && after >= 0 && before != after;
	}

	private static int phase(int[][] phases, int id)
	{
		for (int i = 0; i < phases.length; i++)
		{
			if (has(phases[i], id))
			{
				return i;
			}
		}
		return -1;
	}

	private static final int[] THE_NIGHTMARE = {378, 9425, 9426, 9427, 9428, 9429, 9430, 9431, 9432, 9433, 9460};
	private static final int[] PHOSANIS_NIGHTMARE = {377, 9423, 9416, 9417, 9418, 9419, 9420, 9421, 9422, 9424, 11153,
		11154, 11155};

	/**
	 * The Defence levels a monster has won back {@code ticks} after the last drain counted on it (the calculator has no
	 * time; OSRS Wiki): the Nightmare "will restore her defence stat back at the rate of 20 defence per minute"
	 * (The Nightmare/Strategies; when in the minute is not said: counted here from the drain), Phosani's Nightmare one
	 * level every 3 seconds, 5 ticks (Phosani's Nightmare/Strategies). 0 for any other monster.
	 */
	static int recovered(int npcId, int ticks)
	{
		if (ticks <= 0)
		{
			return 0;
		}
		if (has(PHOSANIS_NIGHTMARE, npcId))
		{
			return ticks / 5;
		}
		return has(THE_NIGHTMARE, npcId) ? ticks / 100 * 20 : 0;
	}

	/** True for Tekton: his own rules for a special attack that fails to hit. */
	static boolean tekton(int npcId)
	{
		return has(TEKTON, npcId);
	}

	private static boolean has(int[] ids, int id)
	{
		return Arrays.stream(ids).anyMatch(i -> i == id);
	}
}
