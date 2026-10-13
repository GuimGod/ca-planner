/*
 * Copyright (c) 2026, GuimGod
 * All rights reserved. Licensed under the BSD 2-Clause License (see LICENSE).
 */
package com.caplanner.gear;

import java.util.Collection;
import java.util.List;

/**
 * The effects of armour that the numbers on the item do not show. A piece is ranked for a gear
 * switch by its attack and damage bonuses; a few sets give more than that under a condition,
 * and a few items are worn for a reason that has nothing to do with their bonuses. This class
 * knows those, by item name, so the switch reminder neither overlooks a conditional piece nor
 * asks the player to take one off. The conditions and sizes follow the OSRS Wiki DPS calculator.
 */
final class SetEffects
{
	/** What the player is doing with the weapon in hand. */
	static final class Context
	{
		/** melee, ranged or magic. */
		String combatClass;
		/** stab, slash or crush when a melee style is selected. */
		String meleeType;
		String weaponName = "";
		/** A staff that casts spells (not one with its own built-in attack). */
		boolean castingStaff;
		boolean ancientSpellbook;
		/** The element of the spell on autocast ("water", "earth", "fire", "air"), or null. */
		String autocastElement;
		/** Names of everything worn and carried, to tell which void set is in use. */
		Collection<String> ownedNames;
		/**
		 * Whether the monster being fought is of the player's Slayer task: TRUE, FALSE, or null when that
		 * is not known (no monster, a task the table does not have). Not knowing changes nothing.
		 */
		Boolean onTask;
		/** Whether the monster being fought is undead: TRUE, FALSE, or null when no monster is known. */
		Boolean undead;
		/** The weapon in hand does not use the ammunition slot. */
		boolean ammoFree;
	}

	/** Kinds of Slayer helmet and Black mask. */
	static final int MASK = 1;
	static final int MASK_IMBUED = 2;
	/** Kinds of Salve amulet: plain, (e), (i), (ei). */
	static final int SALVE = 1;
	static final int SALVE_E = 2;
	static final int SALVE_I = 3;
	static final int SALVE_EI = 4;
	/**
	 * What the Slayer helmet on a task, or the Salve amulet against the undead, adds to its score: more
	 * than any piece of its slot offers, and less than {@link #VOID_PIECE}, so it never breaks a set.
	 */
	static final double TASK_PIECE = 400;

	/** Extra magic damage, in percent, of each Virtus piece on Ancient Magicks cast from a staff. */
	static final double VIRTUS_ANCIENT_BONUS = 3;
	/** Extra magic damage, in percent, of the tome of the element on autocast. */
	static final double TOME_BONUS = 10;
	/**
	 * Score a crystal piece gains per unit of weight with a crystal bow (helm 1, legs 2, body 3): each
	 * unit is 5% accuracy and 2.5% damage, about 12 points of ranged attack and 4 of ranged strength
	 * on a typical set.
	 */
	static final double CRYSTAL_UNIT = 20;
	/** Score an Inquisitor's piece gains per unit of weight on crush (helm 1, hauberk 2, plateskirt 2): 0.5% each way. */
	static final double INQUISITOR_UNIT = 3;
	/** Enough to rank a piece of the void set in use above any other piece of its slot. */
	static final double VOID_PIECE = 500;

	private static final String[] KEPT = {
		// in the Revenant Caves
		"Amulet of avarice",
		// sets that work as a whole
		"Void ", "Elite void ", "Obsidian helmet", "Obsidian platebody", "Obsidian platelegs", "Berserker necklace",
		// a Barrows set is kept only with its own weapon in hand and all its pieces on the player: isBarrowsSetPiece
		"Amulet of the damned",
		"Blood moon ", "Blue moon ", "Eclipse moon ",
		// worn for the special attack energy it restores; it has no bonuses to compare
		"Lightbearer",
		// Slayer bracelets: worn for what they do to the task, with no bonuses at all, so any glove "beats" them
		"Expeditious bracelet", "Bracelet of slaughter",
	};
	/** Carried to be used, not worn for the fight: never asked for, whatever their bonuses. */
	private static final String[] CARRIED_FOR_USE = {
		// a teleport
		"Ring of shadows", "Xeric's talisman", "Digsite pendant", "Skills necklace", "Games necklace", "Necklace of passage",
		"Burning amulet", "Ring of dueling", "Ring of wealth", "Drakan's medallion", "Ring of the elements",
		// diary and utility items carried for their teleports and perks; their +1 bonuses are not why they are there
		"Karamja gloves", "Explorer's ring", "Slayer ring", "Ardougne cloak", "Desert amulet", "Morytania legs",
		"Fremennik sea boots", "Kandarin headgear", "Falador shield", "Varrock armour", "Western banner", "Wilderness sword",
	};

	private SetEffects()
	{
	}

	/** Extra magic damage, in percent, the piece has in this context. */
	static double magicDamageBonus(String itemName, Context c)
	{
		if (tomeInUse(itemName, c))
		{
			return TOME_BONUS;
		}
		return c.castingStaff && c.ancientSpellbook && startsWith(itemName, "Virtus ") ? VIRTUS_ANCIENT_BONUS : 0;
	}

	/** A charged Tome of fire, water or earth while a spell of its element is on autocast: a tenth more damage. */
	static boolean tomeInUse(String itemName, Context c)
	{
		if (itemName == null || c == null || !c.castingStaff || c.autocastElement == null || itemName.contains("(empty)"))
		{
			return false;
		}
		return itemName.equalsIgnoreCase("Tome of " + c.autocastElement);
	}

	/** The element of a spell by its number in the game's autocast setting (OSRS Wiki, varbit 276), or null. */
	static String autocastElement(int spell)
	{
		int i = spell >= 1 && spell <= 16 ? (spell - 1) % 4 : spell >= 48 && spell <= 51 ? spell - 48 : -1;
		return i < 0 ? null : new String[]{"air", "water", "earth", "fire"}[i];
	}

	/** Extra score the piece has in this context. */
	static double scoreBonus(String itemName, Context c)
	{
		if (itemName == null)
		{
			return 0;
		}
		if (StyleCalc.RANGED.equals(c.combatClass) && isCrystalBow(c.weaponName))
		{
			int weight = itemName.startsWith("Crystal helm") ? 1 : itemName.startsWith("Crystal legs") ? 2
				: itemName.startsWith("Crystal body") ? 3 : 0;
			if (weight > 0)
			{
				return weight * CRYSTAL_UNIT;
			}
		}
		if (StyleCalc.CRUSH.equals(c.meleeType))
		{
			int weight = Weapons.inquisitorWeight(itemName);
			if (weight > 0)
			{
				return weight * INQUISITOR_UNIT;
			}
		}
		if (isVoidOf(itemName, c))
		{
			return VOID_PIECE;
		}
		if (isBarrowsSetPiece(itemName, c))
		{
			return VOID_PIECE;
		}
		boolean melee = StyleCalc.MELEE.equals(c.combatClass);
		int salve = salveKind(itemName);
		if (salve != 0 && Boolean.TRUE.equals(c.undead) && (melee || salve == SALVE_I || salve == SALVE_EI))
		{
			return TASK_PIECE;
		}
		int mask = maskKind(itemName);
		if (mask != 0 && Boolean.TRUE.equals(c.onTask) && (melee || mask == MASK_IMBUED)
			&& !(Boolean.TRUE.equals(c.undead) && ownsWorkingSalve(c)))
		{
			return TASK_PIECE;
		}
		return 0;
	}

	/** The kind of Slayer helmet or Black mask this is, in any colour, or 0. */
	static int maskKind(String itemName)
	{
		if (itemName == null)
		{
			return 0;
		}
		String n = itemName.toLowerCase(java.util.Locale.ROOT);
		if (!n.contains("slayer helmet") && !n.startsWith("black mask"))
		{
			return 0;
		}
		return n.endsWith("(i)") ? MASK_IMBUED : MASK;
	}

	/** The kind of Salve amulet this is, or 0. */
	static int salveKind(String itemName)
	{
		if (itemName == null || !itemName.startsWith("Salve amulet"))
		{
			return 0;
		}
		String n = itemName.replace(" ", "");
		return n.endsWith("(ei)") ? SALVE_EI : n.endsWith("(i)") ? SALVE_I : n.endsWith("(e)") ? SALVE_E : SALVE;
	}

	/** True for a blessing: the one thing worn in the ammunition slot that is not ammunition. */
	static boolean isBlessing(String itemName)
	{
		return itemName != null && itemName.toLowerCase(java.util.Locale.ROOT).contains(" blessing");
	}

	/** True when a Salve amulet that works for the class in use is worn or carried: the helmet adds nothing to it. */
	private static boolean ownsWorkingSalve(Context c)
	{
		if (c.ownedNames == null)
		{
			return false;
		}
		boolean melee = StyleCalc.MELEE.equals(c.combatClass);
		for (String name : c.ownedNames)
		{
			int salve = salveKind(name);
			if (salve != 0 && (melee || salve == SALVE_I || salve == SALVE_EI))
			{
				return true;
			}
		}
		return false;
	}

	/**
	 * A Slayer task or category name as the plugin compares them: lower case, without the plural "s"
	 * ("Abyssal Demons" and "abyssal demon" are the same key; so are "Aviansies" and "aviansie").
	 */
	static String taskKey(String name)
	{
		if (name == null)
		{
			return "";
		}
		String key = name.trim().toLowerCase(java.util.Locale.ROOT);
		return key.endsWith("s") && !key.endsWith("ss") ? key.substring(0, key.length() - 1) : key;
	}

	/**
	 * Whether a monster is of the player's task: TRUE or FALSE when the task is one of the known
	 * categories, null when it is not (a name the table does not have, or the "Bosses" task, whose
	 * boss is not in the name).
	 *
	 * @param taskName         the task's name as the game gives it, or null
	 * @param monsterCategories the monster's categories, as {@link #taskKey} writes them
	 * @param knownCategories   every category of the table, as {@link #taskKey} writes them
	 */
	static Boolean onTask(String taskName, Collection<String> monsterCategories, Collection<String> knownCategories)
	{
		String key = taskKey(taskName);
		if (key.isEmpty() || key.equals("bosse") || key.equals("boss") || knownCategories == null
			|| !knownCategories.contains(key) || monsterCategories == null)
		{
			return null;
		}
		return monsterCategories.contains(key);
	}

	/** True for a piece that should stay on whatever else is carried, when nothing is known of the monster. */
	static boolean keep(String itemName)
	{
		return keep(itemName, null);
	}

	/**
	 * True for a piece that should stay on whatever else is carried. A Slayer helmet stays unless the
	 * monster is known not to be of the task; a Salve amulet stays unless it is known not to be undead.
	 */
	static boolean keep(String itemName, Context c)
	{
		if (itemName == null)
		{
			return false;
		}
		if (maskKind(itemName) != 0)
		{
			return c == null || !Boolean.FALSE.equals(c.onTask);
		}
		if (salveKind(itemName) != 0)
		{
			return c == null || !Boolean.FALSE.equals(c.undead);
		}
		if (tomeInUse(itemName, c))
		{
			return true;
		}
		if (c != null && isBarrowsSetPiece(itemName, c))
		{
			return true;
		}
		for (String prefix : KEPT)
		{
			if (itemName.startsWith(prefix))
			{
				return true;
			}
		}
		return false;
	}

	/** True for an item that is carried to be used (a teleport) and must never be listed as a switch piece. */
	static boolean carriedForUse(String itemName)
	{
		if (itemName == null)
		{
			return false;
		}
		for (String prefix : CARRIED_FOR_USE)
		{
			if (itemName.startsWith(prefix))
			{
				return true;
			}
		}
		return false;
	}

	/** The weight of a crystal armour piece with a crystal bow: helm 1, legs 2, body 3; 0 for anything else. */
	static int crystalWeight(String itemName)
	{
		return itemName == null ? 0 : itemName.startsWith("Crystal helm") ? 1 : itemName.startsWith("Crystal legs") ? 2
			: itemName.startsWith("Crystal body") ? 3 : 0;
	}

	/**
	 * The Void set worn for a combat class: 0 when it is not whole, 1 for Void, 2 for Elite Void. The set is
	 * the helm of the class, the gloves, a top and a robe; it is Elite when both the top and the robe are.
	 */
	static int voidKind(Collection<String> wornNames, String combatClass)
	{
		String helm = StyleCalc.MAGIC.equals(combatClass) ? "Void mage helm"
			: StyleCalc.RANGED.equals(combatClass) ? "Void ranger helm"
			: StyleCalc.MELEE.equals(combatClass) ? "Void melee helm" : null;
		if (helm == null || wornNames == null)
		{
			return 0;
		}
		boolean hasHelm = false;
		boolean gloves = false;
		int top = 0;
		int robe = 0;
		for (String name : wornNames)
		{
			if (name == null)
			{
				continue;
			}
			hasHelm |= name.startsWith(helm);
			gloves |= name.startsWith("Void knight gloves");
			top = Math.max(top, name.startsWith("Elite void top") ? 2 : name.startsWith("Void knight top") ? 1 : 0);
			robe = Math.max(robe, name.startsWith("Elite void robe") ? 2 : name.startsWith("Void knight robe") ? 1 : 0);
		}
		return hasHelm && gloves && top > 0 && robe > 0 ? (top == 2 && robe == 2 ? 2 : 1) : 0;
	}

	private static final String[] BROTHERS = {"ahrim's ", "dharok's ", "guthan's ", "karil's ", "torag's ", "verac's "};
	private static final String[] BARROWS_WEAPONS = {"staff", "greataxe", "warspear", "crossbow", "hammers", "flail"};
	private static final String[] BARROWS_HEADS = {"helm", "hood", "coif"};
	private static final String[] BARROWS_BODIES = {"platebody", "robetop", "leathertop", "brassard"};
	private static final String[] BARROWS_LEGS = {"platelegs", "robeskirt", "chainskirt", "leatherskirt", "plateskirt"};

	/** The Barrows brother an item belongs to ("dharok's "), or null. */
	static String brother(String itemName)
	{
		String n = itemName == null ? "" : itemName.toLowerCase(java.util.Locale.ROOT);
		for (String b : BROTHERS)
		{
			if (n.startsWith(b))
			{
				return b;
			}
		}
		return null;
	}

	/** Which part of a Barrows set an item is: 0 the weapon, 1 the head, 2 the body, 3 the legs; -1 for none. */
	static int barrowsPart(String itemName)
	{
		String b = brother(itemName);
		// the pieces carry how worn they are in their name ("Dharok's helm 75"); one at 0 is broken, cannot be
		// worn and is no part of a set
		if (b == null || itemName.endsWith(" 0"))
		{
			return -1;
		}
		String rest = itemName.toLowerCase(java.util.Locale.ROOT).substring(b.length());
		String[][] parts = {BARROWS_WEAPONS, BARROWS_HEADS, BARROWS_BODIES, BARROWS_LEGS};
		for (int i = 0; i < parts.length; i++)
		{
			for (String word : parts[i])
			{
				if (rest.startsWith(word))
				{
					return i;
				}
			}
		}
		return -1;
	}

	/** True when the names hold the weapon, the head, the body and the legs of one Barrows brother. */
	static boolean wholeBarrowsSet(Collection<String> names, String brother)
	{
		if (names == null || brother == null)
		{
			return false;
		}
		boolean[] has = new boolean[4];
		for (String name : names)
		{
			int part = brother.equals(brother(name)) ? barrowsPart(name) : -1;
			if (part >= 0)
			{
				has[part] = true;
			}
		}
		return has[0] && has[1] && has[2] && has[3];
	}

	/**
	 * True for an armour piece of the Barrows brother whose weapon is in hand, when the player has the whole
	 * set on them (worn or carried): the set's effect needs all four, so the pieces go together and none is
	 * asked off for a piece with better numbers. With a piece missing the set does nothing and each piece is
	 * compared like any other.
	 */
	static boolean isBarrowsSetPiece(String itemName, Context c)
	{
		if (c == null || c.ownedNames == null || barrowsPart(c.weaponName) != 0 || barrowsPart(itemName) <= 0)
		{
			return false;
		}
		String b = brother(c.weaponName);
		if (!b.equals(brother(itemName)))
		{
			return false;
		}
		List<String> all = new java.util.ArrayList<>(c.ownedNames);
		all.add(c.weaponName);
		return wholeBarrowsSet(all, b);
	}

	static boolean isCrystalBow(String weaponName)
	{
		// the game writes "Bow of Faerdhinen (c)": the comparison must not depend on capitals
		String n = weaponName == null ? "" : weaponName.toLowerCase(java.util.Locale.ROOT);
		return n.startsWith("bow of faerdhinen") || n.startsWith("crystal bow");
	}

	/**
	 * True for a piece of the void set of the class in use: its helm, or a body piece when that
	 * helm is worn or carried. Without the helm the robes belong to another style's set.
	 */
	private static boolean isVoidOf(String itemName, Context c)
	{
		String helm = StyleCalc.MAGIC.equals(c.combatClass) ? "Void mage helm"
			: StyleCalc.RANGED.equals(c.combatClass) ? "Void ranger helm"
			: StyleCalc.MELEE.equals(c.combatClass) ? "Void melee helm" : null;
		if (helm == null || c.ownedNames == null)
		{
			return false;
		}
		boolean hasHelm = false;
		for (String name : c.ownedNames)
		{
			hasHelm |= startsWith(name, helm);
		}
		if (!hasHelm)
		{
			return false;
		}
		return itemName.startsWith(helm) || itemName.startsWith("Void knight top") || itemName.startsWith("Void knight robe")
			|| itemName.startsWith("Void knight gloves") || itemName.startsWith("Elite void top")
			|| itemName.startsWith("Elite void robe");
	}

	private static boolean startsWith(String s, String prefix)
	{
		return s != null && s.startsWith(prefix);
	}
}
