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
			int weight = crystalWeight(itemName);
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
	/**
	 * Weapons held for their special attack and put away again, by a part of their name in lower case:
	 * with one of them in hand, the weapon it was taken out for is not the one to go back to yet.
	 */
	private static final String[] SPECIAL_ATTACK_WEAPONS = {
		// damage
		"dragon claws", "burning claws", "voidwaker", "armadyl godsword", "crimson kisten", "dragon dagger", "abyssal dagger",
		"granite maul", "dark bow", "magic shortbow", "dragon thrownaxe", "dragon knife", "dragon halberd", "crystal halberd",
		"dragon mace", "dragon longsword", "dragon sword", "dragon 2h sword", "saradomin sword", "saradomin's blessed sword",
		"vesta's longsword", "vesta's spear", "statius's warhammer", "morrigan's javelin", "morrigan's throwing axe",
		"dragon crossbow", "armadyl crossbow", "light ballista", "heavy ballista", "volatile nightmare staff",
		"eldritch nightmare staff", "rune claws", "brine sabre",
		// the Tonalztics of Ralos: its special attack lowers the monster's defence (DefenceDrains), like the hammers below
		"tonalztics",
		// the monster's defence or other levels
		"dragon warhammer", "elder maul", "bandos godsword", "barrelchest anchor", "bone dagger", "darklight", "seercull",
		"ancient mace", "dorgeshuun crossbow",
		// healing, holding in place, a boost
		"saradomin godsword", "zamorak godsword", "ancient godsword", "dragon spear", "zamorakian spear", "dragon battleaxe",
		"excalibur", "dinh's bulwark"};

	/** Whether the weapon is one held for its special attack rather than to fight with. */
	static boolean isSpecialAttackWeapon(String weaponName)
	{
		String n = weaponName == null ? "" : weaponName.toLowerCase(java.util.Locale.ROOT);
		for (String part : SPECIAL_ATTACK_WEAPONS)
		{
			if (n.contains(part))
			{
				return true;
			}
		}
		return false;
	}

	/**
	 * Weapons whose worth is in hitting several monsters with one attack, by a part of their name in lower
	 * case: the figures here are for one monster, so with one of them in hand no other weapon is pointed at.
	 */
	private static final String[] MANY_TARGET_WEAPONS = {"venator bow"};

	/**
	 * The Ancient Magicks that hit up to nine monsters in a 3x3 area, by their number in the game's autocast setting
	 * (OSRS Wiki, Varbit 276): the bursts (35 to 38) and the barrages (43 to 46). The rushes and the blitzes hit one.
	 */
	static boolean isManyTargetSpell(int spell)
	{
		return spell >= 35 && spell <= 38 || spell >= 43 && spell <= 46;
	}

	/**
	 * Whether no other weapon is pointed at while this one is in hand: one held for its special attack, or a weapon or a
	 * spell on autocast chosen for hitting several monsters at once (the figures here are for one).
	 */
	static boolean pointsAtNoOtherWeapon(String weaponName, int spell)
	{
		return isSpecialAttackWeapon(weaponName) || isManyTargetWeapon(weaponName) || isManyTargetSpell(spell);
	}

	/** Whether the weapon is one chosen for what it does to several monsters at once. */
	static boolean isManyTargetWeapon(String weaponName)
	{
		String n = weaponName == null ? "" : weaponName.toLowerCase(java.util.Locale.ROOT);
		for (String part : MANY_TARGET_WEAPONS)
		{
			if (n.contains(part))
			{
				return true;
			}
		}
		return false;
	}

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
	 * boss is not in the name: {@link #onBossTask} takes the boss the game names).
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

	/**
	 * The monsters a boss task is done on, by the name the game gives the task (lower case, without a leading "the"),
	 * when they are not the one named: the bosses the OSRS Wiki (Boss slayer tasks, Assignable bosses) lets kill instead,
	 * the brothers, kings and guardians of a task named for all of them, and the Kraken of "The Cave Kraken Boss". The
	 * task names are those of RuneLite's Slayer plugin (Task.java), which reads them from the same table of the game.
	 */
	private static final java.util.Map<String, List<String>> BOSS_TASK_TARGETS = new java.util.HashMap<>();

	static
	{
		BOSS_TASK_TARGETS.put("barrows brothers", java.util.Arrays.asList("ahrim the blighted", "dharok the wretched",
			"guthan the infested", "karil the tainted", "torag the corrupted", "verac the defiled"));
		BOSS_TASK_TARGETS.put("callisto", java.util.Arrays.asList("callisto", "artio"));
		BOSS_TASK_TARGETS.put("venenatis", java.util.Arrays.asList("venenatis", "spindel"));
		BOSS_TASK_TARGETS.put("vet'ion", java.util.Arrays.asList("vet'ion", "calvar'ion"));
		BOSS_TASK_TARGETS.put("crazy archaeologists", java.util.Arrays.asList("crazy archaeologist", "deranged archaeologist"));
		BOSS_TASK_TARGETS.put("dagannoth kings", java.util.Arrays.asList("dagannoth rex", "dagannoth prime", "dagannoth supreme"));
		BOSS_TASK_TARGETS.put("grotesque guardians", java.util.Arrays.asList("dusk", "dawn"));
		BOSS_TASK_TARGETS.put("cave kraken boss", java.util.Collections.singletonList("kraken"));
		// the Abyssal Sire's fight: its respiratory systems carry the Sire's categories (OSRS Wiki, Respiratory system)
		BOSS_TASK_TARGETS.put("abyssal sire", java.util.Arrays.asList("abyssal sire", "respiratory system"));
	}

	/** A boss's or a boss task's name as compared: lower case, without a leading "the" ("The Leviathan" is "leviathan"). */
	static String bossKey(String name)
	{
		String key = name == null ? "" : name.trim().toLowerCase(java.util.Locale.ROOT);
		return key.startsWith("the ") ? key.substring(4) : key;
	}

	/**
	 * Whether a monster is of the player's boss task (the game's task "Bosses", with the boss read from the game's table
	 * of boss tasks): TRUE for the boss named or one the wiki lets kill instead, FALSE for any other monster and for the
	 * awakened fights of Desert Treasure II's bosses, on which Slayer equipment has no effect (OSRS Wiki, Slayer helmet
	 * and Boss slayer tasks); null when the boss is not known.
	 *
	 * @param bossTaskName the boss task's name as the game gives it ("The Abyssal Sire"), or null
	 * @param monsterName  the monster's name
	 * @param awakened     whether it is the awakened fight of one of Desert Treasure II's bosses
	 * @param konar        whether Konar gave the task: on hers the Deranged archaeologist does not stand in for the Crazy one
	 */
	static Boolean onBossTask(String bossTaskName, String monsterName, boolean awakened, boolean konar)
	{
		String task = bossKey(bossTaskName);
		if (task.isEmpty())
		{
			return null;
		}
		if (awakened)
		{
			return Boolean.FALSE;
		}
		String monster = bossKey(monsterName);
		List<String> targets = BOSS_TASK_TARGETS.get(task);
		if (targets == null)
		{
			return monster.equals(task);
		}
		if (konar && monster.equals("deranged archaeologist"))
		{
			return Boolean.FALSE;
		}
		return targets.contains(monster);
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

	/**
	 * The weight of a crystal armour piece of Prifddinas with a crystal bow: helm 1, legs 2, body 3; 0 for anything else.
	 * The Gauntlet's armour ("(basic)", "(attuned)", "(perfected)") is another item, and a piece without charges
	 * ("(inactive)") loses its set effect (OSRS Wiki, Crystal body; the calculator counts both by the name).
	 */
	static int crystalWeight(String itemName)
	{
		if (itemName == null || gauntletItem(itemName) || itemName.toLowerCase(java.util.Locale.ROOT).contains("(inactive)"))
		{
			return 0;
		}
		return itemName.startsWith("Crystal helm") ? 1 : itemName.startsWith("Crystal legs") ? 2
			: itemName.startsWith("Crystal body") ? 3 : 0;
	}

	/** An item of the Gauntlet, by the tier in its name: "(basic)", "(attuned)" or "(perfected)". */
	static boolean gauntletItem(String itemName)
	{
		String n = itemName == null ? "" : itemName.toLowerCase(java.util.Locale.ROOT);
		return n.endsWith("(basic)") || n.endsWith("(attuned)") || n.endsWith("(perfected)");
	}

	/** The whole obsidian armour (the calculator's isWearingObsidian): helmet, platebody and platelegs. */
	static boolean obsidianSet(Collection<String> names)
	{
		return hasStart(names, "Obsidian helmet") && hasStart(names, "Obsidian platebody") && hasStart(names, "Obsidian platelegs");
	}

	/** A Berserker necklace, plain or (or). */
	static boolean berserkerNecklace(Collection<String> names)
	{
		return hasStart(names, "Berserker necklace");
	}

	/**
	 * Ahrim's whole set with the Amulet of the damned (the calculator's isWearingAhrims): the staff, the hood, the robetop
	 * and the robeskirt, none broken.
	 */
	static boolean ahrimsSet(Collection<String> names)
	{
		if (!wholeBarrowsSet(names, "ahrim's ") || names == null)
		{
			return false;
		}
		for (String name : names)
		{
			if (name != null && name.toLowerCase(java.util.Locale.ROOT).startsWith("amulet of the damned"))
			{
				return true;
			}
		}
		return false;
	}

	/**
	 * What the names of everything worn (the weapon among them) say about the sets that count in the damage: the crystal
	 * armour's weight, the Void sets, Dharok's, Ahrim's with the damned, the obsidian armour and the Berserker necklace.
	 */
	static void wornSets(SetCalc.Loadout l, Collection<String> names)
	{
		l.crystal = 0;
		if (names != null)
		{
			for (String name : names)
			{
				l.crystal += crystalWeight(name);
			}
		}
		l.voidMelee = voidKind(names, StyleCalc.MELEE);
		l.voidRanged = voidKind(names, StyleCalc.RANGED);
		l.voidMagic = voidKind(names, StyleCalc.MAGIC);
		l.dharokSet = wholeBarrowsSet(names, "dharok's ");
		l.ahrimsSet = ahrimsSet(names);
		l.obsidianSet = obsidianSet(names);
		l.berserkerNecklace = berserkerNecklace(names);
	}

	private static boolean hasStart(Collection<String> names, String prefix)
	{
		if (names != null)
		{
			for (String name : names)
			{
				if (startsWith(name, prefix))
				{
					return true;
				}
			}
		}
		return false;
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

	/**
	 * The bows the crystal armour of Prifddinas works with (the calculator's isWearingCrystalBow): the Crystal bow and the
	 * Bow of Faerdhinen. The Gauntlet's Crystal bow ("(basic)", "(attuned)", "(perfected)") is another item.
	 */
	static boolean isCrystalBow(String weaponName)
	{
		// the game writes "Bow of Faerdhinen (c)": the comparison must not depend on capitals
		String n = weaponName == null ? "" : weaponName.toLowerCase(java.util.Locale.ROOT);
		return n.startsWith("bow of faerdhinen") || n.startsWith("crystal bow") && !gauntletItem(n);
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
