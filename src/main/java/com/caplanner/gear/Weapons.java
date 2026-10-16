/*
 * Copyright (c) 2026, GuimGod
 * All rights reserved. Licensed under the BSD 2-Clause License (see LICENSE).
 */
package com.caplanner.gear;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

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

	/**
	 * The Tormented Demon's share of attacks that meet it without defence (every one of them lands, the calculator's
	 * phase "Shielded (Defenceless)"; the others meet its defence, "Shielded"). Its flames are not read from the game
	 * yet, so half of the attacks are taken to be without defence, in every class: the
	 * one place to change once they are read.
	 */
	static final double TORMENTED_DEMON_DEFENCELESS = 0.5;

	/**
	 * The Tormented Demon, by the name as the calculator has it: its fire shield takes a fifth off each hit of anything
	 * but a demonbane weapon or an abyssal one in melee.
	 */
	static boolean fireShield(String monsterName)
	{
		return "Tormented Demon".equals(monsterName);
	}

	/**
	 * Araxxor's Acidic, Mirrorback and Ruptura araxytes, by the name: every attack lands at the max hit with the Noxious
	 * halberd, with crush while crush is the highest attack bonus worn, and with a crossbow while ranged is (OSRS Wiki,
	 * Acidic Araxyte, Mirrorback Araxyte and Ruptura Araxyte, the same sentence on the three pages: "Players will always
	 * max hit when attacking them with a crush or heavy Ranged weapon"). The calculator has the rule for the Acidic and
	 * the Mirrorback only (PlayerVsNPCCalc.ts); the Ruptura is the wiki's, which decides the mechanics.
	 */
	static boolean araxyteMax(String monsterName)
	{
		return "Acidic Araxyte".equals(monsterName) || "Mirrorback Araxyte".equals(monsterName)
			|| "Ruptura Araxyte".equals(monsterName);
	}

	/**
	 * Xarpus in every mode and Verzik Vitur's second phase in every mode (with its transition), by NPC id (OSRS Wiki
	 * infoboxes: Xarpus Entry 10766-10769, normal 8338-8341, Hard 10770-10773; Verzik's Phase 2 10833-10834, 8372-8373,
	 * 10850-10851, the ids of the plugin's table and of DefenceDrains): a Scythe of vitur attacks there once every 16/3
	 * ticks, not every 5. Melee has to step back from Xarpus's poison and from Verzik's bombs, and a 5-tick weapon loses one
	 * tick of its cycle: Theatre of Blood/Strategies, Xarpus: "With a 5 tick weapon (Scythe or Soulreaper axe), players
	 * will only delay an attack once in their cycle. This uses 15/16 available ticks to damage the boss, or effectively a
	 * 5.33 tick attack speed". On these two, the Scythe always at 5.333, the damage it
	 * really deals; the other 5-tick weapons stay as they are.
	 */
	private static final int[] SCYTHE_WALKED = ids(10766, 10767, 10768, 10769, 8338, 8339, 8340, 8341, 10770, 10771,
		10772, 10773, 10833, 10834, 8372, 8373, 10850, 10851);
	/** The ticks a Scythe of vitur's attack takes on Xarpus and on Verzik's second phase, as the fight is played. */
	static final double SCYTHE_WALKED_SPEED = 16.0 / 3;

	/**
	 * The ticks a Scythe of vitur's attack takes on the monster of this NPC id: {@link #SCYTHE_WALKED_SPEED} on Xarpus
	 * and Verzik's second phase, 0 everywhere else (the weapon's own speed).
	 */
	static double scytheSpeed(int npcId)
	{
		for (int id : SCYTHE_WALKED)
		{
			if (id == npcId)
			{
				return SCYTHE_WALKED_SPEED;
			}
		}
		return 0;
	}

	/** The share of the attacks that meet the monster without defence, every one of them landing: 0 but on the Tormented Demon. */
	static double defencelessShare(String monsterName)
	{
		return fireShield(monsterName) ? TORMENTED_DEMON_DEFENCELESS : 0;
	}

	/*
	 * The ammunition each ranged weapon fires, by item id, as the OSRS Wiki DPS calculator lists it
	 * (lib/Equipment.ts, commonAmmoCategories and ammoForRangedWeapons): a Magic shortbow (i) fires arrows up to
	 * amethyst, a Rune crossbow bolts up to runite, the Comp ogre bow ogre and brutal arrows. An empty list is a weapon
	 * that takes nothing from the ammunition slot.
	 */
	private static final int[] NONE = new int[0];
	private static final int[] BOW_T1 = ids(882, 883, 5616, 5622, 598, 942, 33553, 884, 885, 5617, 5623, 2532, 2533, 33559, 22227, 22228, 22229, 22230);
	private static final int[] CB_T1 = ids(877, 878, 6061, 6062, 879, 9236);
	private static final int[] JAVELIN = ids(825, 831, 5642, 5648, 826, 832, 5643, 5649, 827, 833, 5644, 5650, 828, 834, 5645, 5651, 829, 835, 5646, 5652, 830, 836, 5647, 5653, 21318, 21320, 21322, 21324, 19484, 19486, 19488, 19490);
	private static final int[] BOW_T5 = ids(BOW_T1, 886, 887, 5618, 5624, 2534, 2535, 33565); // Steel arrow + variants
	private static final int[] BOW_T20 = ids(BOW_T5, 888, 889, 5619, 5625, 2536, 2537, 33571); // Mithril arrow + variants
	private static final int[] BOW_T30 = ids(BOW_T20, 890, 891, 5620, 5626, 2538, 2539, 33577); // Adamant arrow + variants
	private static final int[] BOW_T40 = ids(BOW_T30, 892, 893, 5621, 5627, 78, 2540, 2541, 33583); // Rune arrow + variants, ice arrows
	private static final int[] BOW_T50 = ids(BOW_T40, 21326, 21332, 21334, 21336, 4160, 21328, 21330, 33589, 33601); // Amethyst arrow + variants, broad arrows
	private static final int[] BOW_T60 = ids(BOW_T50, 11212, 11227, 11228, 11229, 11217, 11222, 33595); // Dragon arrow + variants
	private static final int[] CB_T16 = ids(CB_T1, 9139, 9286, 9293, 9300, 9335, 9237); // Blurite bolts + variants, jade bolts + (e)
	private static final int[] CB_T26 = ids(CB_T16, 9140, 9287, 9294, 9301, 880, 9238, 9145, 9292, 9299, 9306); // Iron bolts + variants, pearl bolts + (e), silver bolts
	private static final int[] CB_T31 = ids(CB_T26, 9141, 9288, 9295, 9302, 9336, 9239); // Steel bolts + variants, topaz bolts + (e)
	private static final int[] CB_T36 = ids(CB_T31, 9142, 9289, 9296, 9303, 9337, 9240, 9338, 9241); // Mithril bolts + variants, sapphire/emerald bolts + (e)
	private static final int[] CB_T46 = ids(CB_T36, 9143, 9290, 9297, 9304, 9339, 9242, 9340, 9243); // Adamant bolts + variants, ruby/diamond bolts + (e)
	private static final int[] CB_T61 = ids(CB_T46, 9144, 9291, 9298, 9305, 11875, 21316, 9341, 9244, 9342, 9245); // Runite bolts + variants, broad bolts, amethyst broad bolts, dragonstone/onyx bolts + (e)
	private static final int[] CB_T64 = ids(CB_T61, 21905, 21924, 21926, 21928, 21955, 21932, 21957, 21934, 21959, 21936, 21961, 21938, 21963, 21940, 21965, 21942, 21967, 21944, 21969, 21946, 21971, 21948, 21973, 21950); // Dragon bolts + variants, many gem-tipped bolts
	private static final Map<Integer, int[]> FIRES = new HashMap<>();
	/** Every ammunition the calculator's lists name, for any weapon. */
	private static final Set<Integer> KNOWN_AMMO = new HashSet<>();

	static
	{
		FIRES.put(11708, BOW_T1); // Cursed goblin bow
		FIRES.put(23357, BOW_T1); // Rain bow
		FIRES.put(9705, ids(9706)); // Training bow
		FIRES.put(841, BOW_T1); // Shortbow
		FIRES.put(839, BOW_T1); // Longbow
		FIRES.put(843, BOW_T5); // Oak shortbow
		FIRES.put(845, BOW_T5); // Oak longbow
		FIRES.put(4236, BOW_T5); // Signed oak bow
		FIRES.put(849, BOW_T20); // Willow shortbow
		FIRES.put(847, BOW_T20); // Willow longbow
		FIRES.put(10280, BOW_T20); // Willow comp bow
		FIRES.put(853, BOW_T30); // Maple shortbow
		FIRES.put(851, BOW_T30); // Maple longbow
		FIRES.put(2883, ids(2866, 4773, 4778, 4783, 4788, 4793)); // Ogre bow
		FIRES.put(4827, ids(2866, 4773, 4778, 4783, 4788, 4793, 4798, 4803)); // Comp ogre bow
		FIRES.put(857, BOW_T40); // Yew shortbow
		FIRES.put(855, BOW_T40); // Yew longbow
		FIRES.put(10282, BOW_T40); // Yew comp bow
		FIRES.put(28794, BOW_T50); // Bone shortbow
		FIRES.put(6724, BOW_T50); // Seercull
		FIRES.put(861, BOW_T50); // Magic shortbow
		FIRES.put(12788, BOW_T50); // Magic shortbow (i)
		FIRES.put(859, BOW_T50); // Magic longbow
		FIRES.put(10284, BOW_T50); // Magic comp bow
		FIRES.put(11235, BOW_T60); // Dark bow
		FIRES.put(27853, BOW_T60); // Dark bow (bh)
		FIRES.put(12424, BOW_T60); // 3rd age bow
		FIRES.put(27610, BOW_T60); // Venator bow
		FIRES.put(27612, BOW_T60); // Venator bow (uncharged)
		FIRES.put(20997, BOW_T60); // Twisted bow
		FIRES.put(29591, BOW_T60); // Scorching bow
		FIRES.put(837, CB_T1); // Crossbow
		FIRES.put(767, CB_T1); // Phoenix crossbow
		FIRES.put(9174, CB_T1); // Bronze crossbow
		FIRES.put(9176, CB_T16); // Blurite crossbow
		FIRES.put(9177, CB_T26); // Iron crossbow
		FIRES.put(9179, CB_T31); // Steel crossbow
		FIRES.put(9181, CB_T36); // Mithril crossbow
		FIRES.put(9183, CB_T46); // Adamant crossbow
		FIRES.put(9185, CB_T61); // Rune crossbow
		FIRES.put(21902, CB_T64); // Dragon crossbow
		FIRES.put(19478, JAVELIN); // Light ballista
		FIRES.put(19481, JAVELIN); // Heavy ballista
		FIRES.put(8880, ids(CB_T16, 9140, 9287, 9294, 9301, 8882)); // Dorgeshuun crossbow
		FIRES.put(10156, ids(10158, 10159)); // Hunters' crossbow
		FIRES.put(4734, ids(4740)); // Karil's crossbow (undmg)
		FIRES.put(21012, CB_T64); // Dragon hunter crossbow
		FIRES.put(11785, CB_T64); // Armadyl crossbow
		FIRES.put(26374, CB_T64); // Zaryte crossbow
		FIRES.put(33251, CB_T64); // King's barrage todo(leagues): replace id from cache
		FIRES.put(12924, NONE); // Toxic blowpipe (empty)
		FIRES.put(12926, NONE); // Toxic blowpipe (charged)
		FIRES.put(22547, NONE); // Craw's bow (empty)
		FIRES.put(22550, NONE); // Craw's bow (charged)
		FIRES.put(23983, NONE); // Crystal bow (empty)
		FIRES.put(23985, NONE); // Crystal bow (inactive)
		FIRES.put(24123, NONE); // Crystal bow (new)
		FIRES.put(27652, NONE); // Webweaver bow (empty)
		FIRES.put(27655, NONE); // Webweaver bow (charged)
		FIRES.put(25862, NONE); // Bow of Faerdhinen (empty)
		FIRES.put(25865, NONE); // Bow of Faerdhinen (charged)
		FIRES.put(10149, ids(10142)); // Swamp lizard, Guam tar
		FIRES.put(10146, ids(10143)); // Orange salamander, Marrentill tar
		FIRES.put(10147, ids(10144)); // Red salamander, Tarromin tar
		FIRES.put(10148, ids(10145)); // Black salamander, Harralander tar
		FIRES.put(28834, ids(28837)); // Tecu salamander, Irit tar
		FIRES.put(28869, ids(28872, 28878)); // Hunters' sunlight crossbow
		FIRES.put(29000, ids(28991)); // Eclipse atlatl
		FIRES.put(33245, BOW_T60); // Nature's recurve
		for (int[] ammo : FIRES.values())
		{
			for (int id : ammo)
			{
				KNOWN_AMMO.add(id);
			}
		}
	}

	private static int[] ids(int... ids)
	{
		return ids;
	}

	private static int[] ids(int[] base, int... more)
	{
		int[] all = java.util.Arrays.copyOf(base, base.length + more.length);
		System.arraycopy(more, 0, all, base.length, more.length);
		return all;
	}

	/**
	 * Whether the weapon fires this ammunition. A weapon and an ammunition of the calculator's list (by item id) go by
	 * its list, as its ammoApplicability does; anything else by the word in the name ({@link #ammoKind}), as before.
	 */
	static boolean canFire(int weaponId, String category, String weaponName, int ammoId, String ammoName)
	{
		int[] fires = FIRES.get(weaponId);
		if (fires != null && KNOWN_AMMO.contains(ammoId))
		{
			for (int id : fires)
			{
				if (id == ammoId)
				{
					return true;
				}
			}
			return false;
		}
		String kind = ammoKind(category, weaponName);
		return kind != null && has(ammoName, kind);
	}

	/** True when the calculator lists this weapon with the ammunition it fires (for the tests). */
	static boolean listed(int weaponId)
	{
		return FIRES.containsKey(weaponId);
	}

	/** The TzHaar weapons the obsidian set and the Berserker necklace work with (the calculator's isWearingTzhaarWeapon). */
	static boolean isTzhaarWeapon(String weaponName)
	{
		String n = weaponName == null ? "" : weaponName.toLowerCase(Locale.ROOT);
		return n.startsWith("tzhaar-ket-em") || n.startsWith("tzhaar-ket-om") || n.startsWith("toktz-xil-ak")
			|| n.startsWith("toktz-xil-ek") || n.startsWith("toktz-mej-tal");
	}

	/**
	 * A weapon that cannot attack without its charges (OSRS Wiki): the powered staves Tumeken's shadow, the Sanguinesti
	 * staff, the Eye of Ayak and the Warped sceptre when "(uncharged)", and Craw's bow and the Webweaver bow without
	 * revenant ether, "(u)", and the Bow of Faerdhinen and the Crystal bow "(inactive)", their charges of crystal used up
	 * (OSRS Wiki, Crystal bow: "Upon degrading, it reverts into an inactive version of itself, which can then be
	 * recharged"; Bow of Faerdhinen: the inactive bow is the one left without its shards). The Scythe of vitur, the
	 * Venator bow and the Tonalztics of Ralos still attack uncharged.
	 */
	static boolean cannotAttackUncharged(String weaponName)
	{
		String n = weaponName == null ? "" : weaponName.toLowerCase(Locale.ROOT);
		if (n.endsWith("(inactive)") && (n.startsWith("bow of faerdhinen") || n.startsWith("crystal bow")))
		{
			return true;
		}
		if (n.endsWith("(uncharged)"))
		{
			return n.contains("tumeken's shadow") || n.contains("sanguinesti staff") || n.contains("eye of ayak")
				|| n.contains("warped sceptre");
		}
		return n.endsWith("(u)") && (n.startsWith("craw's bow") || n.startsWith("webweaver bow"));
	}

	/** A revenant weapon without revenant ether, "(u)": its extra 50% in the Wilderness needs the charges (the calculator's). */
	static boolean uncharged(String weaponName)
	{
		String n = weaponName == null ? "" : weaponName.toLowerCase(Locale.ROOT);
		return n.endsWith("(u)") || n.endsWith("(uncharged)");
	}

	/**
	 * The Keris partisan of amascut (item 30891): the RuneLite item data gives it no stab and no strength bonus, the
	 * OSRS Wiki and the calculator +108 and +67 (equipment.json), which the calculator takes 50 and 22 off of outside
	 * the Tombs of Amascut (Equipment.ts).
	 */
	static final int KERIS_PARTISAN_OF_AMASCUT = 30891;
	static final int KERIS_OF_AMASCUT_STAB = 108;
	static final int KERIS_OF_AMASCUT_STRENGTH = 67;

	static int stab(int itemId, int fromGame)
	{
		return itemId == KERIS_PARTISAN_OF_AMASCUT ? KERIS_OF_AMASCUT_STAB : fromGame;
	}

	static int strength(int itemId, int fromGame)
	{
		return itemId == KERIS_PARTISAN_OF_AMASCUT ? KERIS_OF_AMASCUT_STRENGTH : fromGame;
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

	static boolean has(String name, String part)
	{
		return name != null && name.toLowerCase(Locale.ROOT).contains(part);
	}
}
