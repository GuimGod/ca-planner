/*
 * Copyright (c) 2026, GuimGod
 * All rights reserved. Licensed under the BSD 2-Clause License (see LICENSE).
 */
package com.caplanner.gear;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The combat spells a staff can be counted with, by their number in the game's autocast setting:
 * the elemental spells of the standard spellbook and the Ancient Magicks. Which of them a player
 * can cast goes by the Magic level and by the rune that each tier is cast with.
 */
final class Spells
{
	private static final String[] ELEMENTS = {"Wind", "Water", "Earth", "Fire"};
	private static final String[] TIERS = {"Strike", "Bolt", "Blast", "Wave", "Surge"};
	/** The Magic level of the wind spell of each tier, then of its water, earth and fire spell. */
	private static final int[][] LEVELS = {{1, 5, 9, 13}, {17, 23, 29, 35}, {41, 47, 53, 59}, {62, 65, 70, 75}, {81, 85, 90, 95}};
	/** The rune each tier cannot be cast without. */
	private static final String[] RUNES = {"mind rune", "chaos rune", "death rune", "blood rune", "wrath rune"};
	private static final int FIRST_SURGE = 48;

	private static final String[] ANCIENT_ELEMENTS = {"Smoke", "Shadow", "Blood", "Ice"};
	private static final String[] ANCIENT_TIERS = {"Rush", "Burst", "Blitz", "Barrage"};
	/** The Magic level of the rush, burst, blitz and barrage of smoke, shadow, blood and ice. */
	private static final int[][] ANCIENT_LEVELS = {{50, 62, 74, 86}, {52, 64, 76, 88}, {56, 68, 80, 92}, {58, 70, 82, 94}};
	private static final int FIRST_ANCIENT = 31;
	/** Spells that only one staff casts; the numbers are this plugin's own. */
	static final int MAGIC_DART = 60;
	static final int IBAN_BLAST = 61;

	private Spells()
	{
	}

	/**
	 * @param magic    the player's Magic level
	 * @param runes    names, in lower case, of the runes owned; null when not known (then no rune is asked for)
	 * @param ancients the Ancient Magicks are unlocked
	 * @param element  "air", "water", "earth" or "fire" for spells of that element only; null for any
	 */
	static List<Integer> castable(int magic, Set<String> runes, boolean ancients, String element)
	{
		List<Integer> out = new ArrayList<>();
		for (int tier = 0; tier < TIERS.length; tier++)
		{
			if (runes != null && !runes.contains(RUNES[tier]))
			{
				continue;
			}
			for (int e = 0; e < ELEMENTS.length; e++)
			{
				int spell = tier < 4 ? tier * 4 + e + 1 : FIRST_SURGE + e;
				if (magic >= LEVELS[tier][e] && (element == null || element.equals(SetEffects.autocastElement(spell))))
				{
					out.add(spell);
				}
			}
		}
		if (element == null && magic >= 50)
		{
			// each asks for its own staff, which the sums check: the Slayer's staff and Iban's staff
			if (runes == null || (runes.contains("death rune") && runes.contains("mind rune")))
			{
				out.add(MAGIC_DART);
			}
			if (runes == null || (runes.contains("death rune") && runes.contains("fire rune")))
			{
				out.add(IBAN_BLAST);
			}
		}
		if (ancients && element == null)
		{
			for (int e = 0; e < ANCIENT_ELEMENTS.length; e++)
			{
				for (int tier = 0; tier < ANCIENT_TIERS.length; tier++)
				{
					// rush and burst are cast with chaos and death runes, blitz and barrage with death and blood
					boolean has = runes == null || (runes.contains("death rune") && runes.contains(tier < 2 ? "chaos rune" : "blood rune"));
					if (has && magic >= ANCIENT_LEVELS[e][tier])
					{
						// the game numbers them tier by tier: the four rushes, then the four bursts
						out.add(FIRST_ANCIENT + tier * 4 + e);
					}
				}
			}
		}
		return out;
	}

	/** Whether the spell is one of the Ancient Magicks. */
	static boolean ancient(int spell)
	{
		return spell >= FIRST_ANCIENT && spell < FIRST_ANCIENT + 16;
	}

	/** "Fire Surge", "Ice Barrage"; null for a number that is no spell here. */
	static String name(int spell)
	{
		if (spell >= 1 && spell <= 16)
		{
			return ELEMENTS[(spell - 1) % 4] + " " + TIERS[(spell - 1) / 4];
		}
		if (spell >= FIRST_SURGE && spell < FIRST_SURGE + 4)
		{
			return ELEMENTS[spell - FIRST_SURGE] + " Surge";
		}
		if (spell >= FIRST_ANCIENT && spell < FIRST_ANCIENT + 16)
		{
			return ANCIENT_ELEMENTS[(spell - FIRST_ANCIENT) % 4] + " " + ANCIENT_TIERS[(spell - FIRST_ANCIENT) / 4];
		}
		return spell == MAGIC_DART ? "Magic Dart" : spell == IBAN_BLAST ? "Iban Blast" : null;
	}

	/** The element of a tome in the shield slot ("fire", "water", "earth"), or null for anything else. */
	static String tome(String shieldName)
	{
		String n = shieldName == null ? "" : shieldName.toLowerCase(Locale.ROOT);
		if (n.contains("(empty)"))
		{
			// a tome with no pages in it gives nothing
			return null;
		}
		for (String element : new String[]{"fire", "water", "earth"})
		{
			if (n.startsWith("tome of " + element))
			{
				return element;
			}
		}
		return null;
	}
}
