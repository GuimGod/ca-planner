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
		return c.castingStaff && c.ancientSpellbook && isVirtus(itemName) ? VIRTUS_ANCIENT_BONUS : 0;
	}

	/**
	 * A piece of Virtus, the echo Virtus too: the ornament kit "gives no additional bonuses, and is only used to add
	 * aesthetics" (OSRS Wiki, Echo virtus robe top). The calculator gives the Ancient Magicks' bonus only to a name with
	 * "Virtus" in capitals, so the echo pieces get none there; here they are Virtus.
	 */
	static boolean isVirtus(String itemName)
	{
		return startsWith(itemName, "Virtus ") || startsWith(itemName, "Echo virtus ");
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

	/**
	 * The element of the tome in the shield slot, when it is that of the spell on autocast and is charged (the calculator
	 * counts only the "Charged" version: "Tome of fire (empty)" adds nothing), or null.
	 */
	static String tomeElement(String shieldName, int spell)
	{
		String element = autocastElement(spell);
		return element != null && shieldName != null && shieldName.equalsIgnoreCase("Tome of " + element) ? element : null;
	}

	/**
	 * The Elemental amulet, or the amulet of the element on autocast, while a staff casts it: 2 more on the spell's base
	 * (the calculator's hasMatchingElementalAmulet), more than the Occult necklace's 5% magic damage on any elemental
	 * spell, which the bonuses alone would rank first. So on an elemental spell, never the Occult.
	 */
	static boolean elementalAmuletInUse(String itemName, Context c)
	{
		if (itemName == null || c == null || !c.castingStaff || c.autocastElement == null)
		{
			return false;
		}
		String element = elementalAmulet(java.util.Collections.singletonList(itemName));
		return "all".equals(element) || c.autocastElement.equals(element);
	}

	/**
	 * The varbits that turn 1 when an elemental fragment of the Fractured Archive is broken, as recorded fights saw
	 * them (2026-10-24, fire; 2026-10-25, earth). Water's and air's were not recorded: they are known by the game's
	 * message or by the plugin's option.
	 */
	static final int FRAGMENT_FIRE_VARBIT = 16436;
	static final int FRAGMENT_EARTH_VARBIT = 16434;

	/**
	 * The element of the fragment the game says was broken ("You break open the fragment of burning fire and consume its
	 * power. Fire spells will have an increased max hit of +2."), or null for any other message.
	 */
	static String brokenFragment(String message)
	{
		if (message == null || !message.contains("spells will have an increased max hit"))
		{
			return null;
		}
		for (String element : new String[]{"fire", "water", "earth", "air"})
		{
			if (message.toLowerCase(java.util.Locale.ROOT).contains(element + " spells will have"))
			{
				return element;
			}
		}
		return null;
	}

	/**
	 * Whether the game says the Tome of Fire holds searing pages (TRUE) or burnt pages (FALSE), on charging it ("Your
	 * tome has been charged with Searing Pages. It currently holds 7,147 charges.") or checking it (it names the pages
	 * since 2024-04-24, OSRS Wiki); null for any other message.
	 */
	static Boolean searingPages(String message)
	{
		String m = message == null ? "" : message.toLowerCase(java.util.Locale.ROOT);
		if (!m.contains("tome"))
		{
			return null;
		}
		return m.contains("searing page") ? Boolean.TRUE : m.contains("burnt page") ? Boolean.FALSE : null;
	}

	/** The element of a spell by its number in the game's autocast setting (OSRS Wiki, varbit 276), or null. */
	static String autocastElement(int spell)
	{
		int i = spell >= 1 && spell <= 16 ? (spell - 1) % 4 : spell >= 48 && spell <= 51 ? spell - 48 : -1;
		return i < 0 ? null : new String[]{"air", "water", "earth", "fire"}[i];
	}

	/**
	 * What a piece's defence bonuses are worth with Dinh's bulwark in hand, in the score's units (a point of strength
	 * counts twice): every 12 of stab, slash, crush and ranged defence is a point of strength once the sum is over the
	 * bulwark's threshold ({@link SetCalc#bulwarkStrength}), so a defensive piece is not swapped for one with a little
	 * more strength. 0 with any other weapon.
	 */
	static double bulwarkBonus(int defence, Context c)
	{
		return c != null && StyleCalc.MELEE.equals(c.combatClass) && SetCalc.isBulwark(c.weaponName) ? 2 * defence / 12.0 : 0;
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
		if (elementalAmuletInUse(itemName, c))
		{
			return TASK_PIECE;
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
	 * with one of them in hand, the weapon it was taken out for is not the one to go back to yet. First those held for
	 * the damage, then ({@link #SPECIAL_ATTACK_WEAPONS}) the others.
	 */
	private static final String[] DAMAGE_SPECIAL_ATTACK_WEAPONS = {
		"dragon claws", "burning claws", "voidwaker", "armadyl godsword", "crimson kisten", "dragon dagger", "abyssal dagger",
		"granite maul", "dark bow", "magic shortbow", "dragon thrownaxe", "dragon knife", "dragon halberd", "crystal halberd",
		"dragon mace", "dragon longsword", "dragon sword", "dragon 2h sword", "saradomin sword", "saradomin's blessed sword",
		"vesta's longsword", "vesta's spear", "statius's warhammer", "morrigan's javelin", "morrigan's throwing axe",
		"dragon crossbow", "armadyl crossbow", "light ballista", "heavy ballista", "volatile nightmare staff",
		"eldritch nightmare staff", "rune claws", "brine sabre",
		// Zorya's tome: "Its primary purpose is for its Special Attack" (the post "Out Today" of 2026-10-20); its spec's
		// three faster attacks after it are still the tome's
		"zorya's tome"};

	/** The weapons held for a special attack that is not for its damage. */
	private static final String[] SPECIAL_ATTACK_WEAPONS = {
		// the Tonalztics of Ralos: its special attack lowers the monster's defence (DefenceDrains), like the hammers below
		"tonalztics",
		// the monster's defence or other levels
		"dragon warhammer", "elder maul", "bandos godsword", "barrelchest anchor", "bone dagger", "darklight", "seercull",
		"ancient mace", "dorgeshuun crossbow",
		// healing, holding in place, a boost
		"saradomin godsword", "zamorak godsword", "ancient godsword", "dragon spear", "zamorakian spear", "dragon battleaxe",
		"excalibur", "dinh's bulwark", "dinh's blazing bulwark"};

	/** Whether the weapon is one held for its special attack rather than to fight with. */
	static boolean isSpecialAttackWeapon(String weaponName)
	{
		return isDamageSpecialAttackWeapon(weaponName) || has(SPECIAL_ATTACK_WEAPONS, weaponName);
	}

	/** Whether the weapon is one held for its special attack's damage (not for lowering the monster's levels). */
	static boolean isDamageSpecialAttackWeapon(String weaponName)
	{
		return has(DAMAGE_SPECIAL_ATTACK_WEAPONS, weaponName);
	}

	private static boolean has(String[] parts, String weaponName)
	{
		String n = weaponName == null ? "" : weaponName.toLowerCase(java.util.Locale.ROOT);
		for (String part : parts)
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
		if (tomeInUse(itemName, c) || elementalAmuletInUse(itemName, c))
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
	 * Dizana's quiver with charges, the Blessed Dizana's quiver or Dizana's max cape, locked or not: the quiver's bonus on
	 * the arrows and bolts shot ({@link SetCalc#QUIVER_ACCURACY}). The charges are told by the item: the quiver without
	 * them is another item, "Dizana's quiver (uncharged)" (28947, locked 28949), and has no bonus; the blessed quiver and
	 * the max cape made from it have the effect for good (OSRS Wiki: "essentially acting as a variant of the original with
	 * unlimited charges"). A charged quiver whose charges are not known counts as charged. Not a broken or mangled one.
	 */
	static boolean dizanaQuiver(Collection<String> names)
	{
		if (names != null)
		{
			for (String name : names)
			{
				String n = name == null ? "" : name.replace(" (l)", "");
				if (n.equalsIgnoreCase("Dizana's quiver") || n.equalsIgnoreCase("Blessed Dizana's quiver")
					|| n.equalsIgnoreCase("Dizana's max cape"))
				{
					return true;
				}
			}
		}
		return false;
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
		l.dizanaQuiver = dizanaQuiver(names);
		l.conflictionGauntlets = names != null && names.stream().anyMatch(n -> n != null && n.equalsIgnoreCase("Confliction gauntlets"));
		l.elementalAmulet = elementalAmulet(names);
		// the calculator's isWearingBloodMoonSet: the Dual macuahuitl with the helm, the chestplate and the tassets
		l.bloodMoonSet = hasStart(names, "Dual macuahuitl") && hasStart(names, "Blood moon helm")
			&& hasStart(names, "Blood moon chestplate") && hasStart(names, "Blood moon tassets");
	}

	/**
	 * The element of the amulet worn that adds 2 to a spell of its element: "all" for the Elemental amulet, "air",
	 * "water", "earth" or "fire" for the Amulet of that element, null for none (the calculator's hasMatchingElementalAmulet).
	 */
	static String elementalAmulet(Collection<String> names)
	{
		if (names == null)
		{
			return null;
		}
		for (String name : names)
		{
			if ("Elemental amulet".equalsIgnoreCase(name))
			{
				return "all";
			}
			for (String element : new String[]{"air", "water", "earth", "fire"})
			{
				if (("Amulet of " + element).equalsIgnoreCase(name))
				{
					return element;
				}
			}
		}
		return null;
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
