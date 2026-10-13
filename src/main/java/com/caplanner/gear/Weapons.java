/*
 * Copyright (c) 2026, GuimGod
 * All rights reserved. Licensed under the BSD 2-Clause License (see LICENSE).
 */
package com.caplanner.gear;

import java.util.Locale;

/** What the damage sums need to know about a weapon or a monster from its name alone. */
final class Weapons
{
	private Weapons()
	{
	}

	static boolean isFang(String weaponName)
	{
		return weaponName != null && weaponName.startsWith("Osmumten's fang");
	}

	/** Weapons that deal full damage to the Corporeal Beast when they stab. */
	static boolean isCorpbane(String weaponName)
	{
		if (weaponName == null)
		{
			return false;
		}
		String n = weaponName.toLowerCase(Locale.ROOT);
		return isFang(weaponName) || n.contains("halberd") || (n.contains("spear") && !n.startsWith("blue moon spear"));
	}

	/** Monsters the calculator treats as always hit. */
	static boolean alwaysHit(String monsterName)
	{
		return "Tormented Demon".equals(monsterName);
	}

	static int inquisitorWeight(String itemName)
	{
		if (itemName == null)
		{
			return 0;
		}
		if (itemName.startsWith("Inquisitor's great helm"))
		{
			return 1;
		}
		return itemName.startsWith("Inquisitor's hauberk") || itemName.startsWith("Inquisitor's plateskirt") ? 2 : 0;
	}

	/** The word in the name of the ammunition a weapon shoots: "bolts", "arrow", or null when it takes none from the slot. */
	static String ammoKind(String category, String weaponName)
	{
		if ("Crossbow".equals(category))
		{
			// Karil's crossbow shoots bolt racks and nothing else; no other crossbow shoots them
			return has(weaponName, "karil's crossbow") ? "bolt rack" : "bolts";
		}
		return "Bow".equals(category) && !shootsNoAmmo(category, weaponName) ? "arrow" : null;
	}

	/** A bow that makes its own arrows: what is in the ammunition slot gives it nothing. */
	static boolean shootsNoAmmo(String category, String weaponName)
	{
		String weapon = weaponName == null ? "" : weaponName.toLowerCase(Locale.ROOT);
		return "Bow".equals(category) && (weapon.contains("crystal bow") || weapon.contains("faerdhinen")
			|| weapon.contains("craw's bow") || weapon.contains("webweaver bow"));
	}

	/** True for the weapons that are their own ammunition (thrown weapons, the blowpipe, chinchompas). */
	static boolean throwsItself(String category)
	{
		return "Thrown".equals(category) || "Chinchompas".equals(category);
	}

	/** True for what a bow, a crossbow or a ballista shoots. */
	static boolean isShotAmmo(String name)
	{
		return has(name, "arrow") || has(name, "bolts") || has(name, "bolt rack") || has(name, "javelin");
	}

	/** Arrow metals, worst to best: a bow fires arrows up to one of them. */
	private static final String[] ARROWS = {"bronze", "iron", "steel", "mithril", "adamant", "rune", "amethyst", "dragon"};
	/** Bolt metals, worst to best, each with the gems cut for it. */
	private static final String[][] BOLTS = {
		{"bronze", "opal"}, {"blurite", "jade"}, {"iron", "pearl"}, {"steel", "topaz"},
		{"mithril", "sapphire", "emerald"}, {"adamant", "ruby", "diamond"}, {"runite", "rune", "dragonstone", "onyx"}};

	/**
	 * Whether the weapon can fire the ammunition, by the tier each is made for: a magic shortbow
	 * fires nothing better than amethyst arrows, a rune crossbow nothing better than runite bolts.
	 * A weapon or ammunition this does not know counts as yes, so that a new item is not left out.
	 */
	static boolean canFire(String weaponName, String ammoName)
	{
		String weapon = weaponName == null ? "" : weaponName.toLowerCase(Locale.ROOT);
		String ammo = ammoName == null ? "" : ammoName.toLowerCase(Locale.ROOT);
		if (ammo.contains("arrow"))
		{
			int tier = -1;
			for (int i = 0; i < ARROWS.length; i++)
			{
				if (ammo.contains(ARROWS[i] + " arrow"))
				{
					tier = i;
				}
			}
			int most = weapon.contains("magic shortbow") || weapon.contains("magic longbow") || weapon.contains("magic comp")
				|| weapon.contains("seercull") ? 6
				: weapon.contains("yew ") ? 5 : weapon.contains("maple ") ? 4 : weapon.contains("willow ") ? 3
				: weapon.contains("oak ") ? 2 : weapon.equals("shortbow") || weapon.equals("longbow") ? 1 : ARROWS.length;
			return tier <= most;
		}
		if (ammo.contains("bolts"))
		{
			int tier = -1;
			if (ammo.contains("dragon bolts"))
			{
				tier = BOLTS.length;
			}
			else
			{
				for (int i = 0; i < BOLTS.length; i++)
				{
					for (String word : BOLTS[i])
					{
						if (ammo.startsWith(word + " ") || ammo.contains(" " + word + " "))
						{
							tier = i;
						}
					}
				}
			}
			int most = BOLTS.length;
			// the oldest crossbows: bronze bolts at most, or only bolts of their own (bone, kebbit)
			if (weapon.equals("crossbow") || weapon.contains("phoenix crossbow"))
			{
				most = 0;
			}
			else if (weapon.contains("dorgeshuun crossbow") || weapon.contains("hunters' crossbow")
				|| weapon.contains("hunters' sunlight crossbow"))
			{
				most = tier < 0 ? BOLTS.length : -1;
			}
			String[] crossbows = {"bronze crossbow", "blurite crossbow", "iron crossbow", "steel crossbow",
				"mithril crossbow", "adamant crossbow", "rune crossbow"};
			for (int i = 0; i < crossbows.length; i++)
			{
				if (weapon.contains(crossbows[i]))
				{
					most = i;
				}
			}
			return tier <= most;
		}
		return true;
	}

	static boolean has(String name, String part)
	{
		return name != null && name.toLowerCase(Locale.ROOT).contains(part);
	}

	/**
	 * True for an item that is owned but does nothing as it is: with no charges, switched off,
	 * unfinished, or worn down to nothing (Barrows equipment at 0). Its bonuses are those of the
	 * working item, so it would be picked as if it worked.
	 */
	static boolean cannotBeUsed(String name)
	{
		String n = name == null ? "" : name.toLowerCase(Locale.ROOT);
		if (n.contains("scythe of vitur") || n.contains("venator bow") || n.contains("tonalztics of ralos"))
		{
			// these work with no charges, as the weaker item the game then makes of them (OSRS Wiki)
			return false;
		}
		if (n.startsWith("iban's staff") || n.startsWith("bryophyta's staff"))
		{
			// "(u)" is the upgraded staff, and the other still casts with no charges
			return false;
		}
		return n.contains("(uncharged)") || n.contains("(inactive)") || n.contains("(empty)") || n.contains("(broken)")
			|| n.endsWith("(u)") || n.endsWith(" 0") || n.startsWith("uncharged ");
	}

	/** Bosses fought in the Wilderness, where the weapons made for it do more. */
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
