/*
 * Copyright (c) 2026, GuimGod
 * All rights reserved. Licensed under the BSD 2-Clause License (see LICENSE).
 */
package com.caplanner.gear;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.game.ItemEquipmentStats;

/**
 * The Fractured Archive's weapons and bolts (released 2026-10-20), whose bonuses RuneLite's item data does not have yet
 * (its stats.ids.min.json of 2026-10-20 21:34 UTC stops at item 34643): the OSRS Wiki's, read 2026-10-22 (Obligator,
 * Ascension crossbows, Ascension bolts, Diamond and Onyx ascension bolts and their (e), Zorya's tome). They are used only
 * while RuneLite's own data has nothing for the item ({@link #equipment}); once it has, the game's numbers are taken.
 *
 * <p>The rules of these items:
 * <ul>
 * <li>The Obligator ({@link #OBLIGATOR}): its Thrust style rolls the damage up to four times and keeps the best, and deals
 * 20% more to a monster of 2x2 and 40% more to one of 3x3 or larger; its Smash style attacks every 3 ticks with neither
 * ({@link #obligatorRolls}, {@link #obligatorSize}, {@link #OBLIGATOR_SMASH_SPEED}).</li>
 * <li>Zeal, the prayer that replaces Piety: 25% Attack, 28% Strength ({@link #ZEAL_ATTACK}).</li>
 * <li>The Ascension crossbows fire the Ascension bolts only, and the bolts' effect goes off half as often
 * ({@link #ascensionCrossbows}).</li>
 * <li>Zorya's tome: a powered staff of 3 ticks, +15% magic damage, held for its special attack. Its max hit by the Magic
 * level is not published (only "a base max hit of 18"): it gets no figure ({@link #isTome}).</li>
 * </ul>
 */
final class ArchiveItems
{
	static final int OBLIGATOR = ItemID.OBLIGATOR;
	static final int OBLIGATOR_UNCHARGED = ItemID.OBLIGATOR_UNCHARGED;
	static final int ASCENSION_CROSSBOWS = ItemID.ASCENSION_CROSSBOWS;
	static final int ASCENSION_BOLTS = ItemID.ASCENSION_BOLTS;
	static final int DIAMOND_ASCENSION_BOLTS = ItemID.ASCENSION_BOLTS_DIAMOND_UNENCHANTED;
	static final int DIAMOND_ASCENSION_BOLTS_E = ItemID.ASCENSION_BOLTS_DIAMOND_ENCHANTED;
	static final int ONYX_ASCENSION_BOLTS = ItemID.ASCENSION_BOLTS_ONYX_UNENCHANTED;
	static final int ONYX_ASCENSION_BOLTS_E = ItemID.ASCENSION_BOLTS_ONYX_ENCHANTED;
	static final int ZORYAS_TOME = ItemID.ZORYAS_TOME;
	static final int ZORYAS_TOME_UNCHARGED = ItemID.ZORYAS_TOME_UNCHARGED;

	/** The equipment slots of RuneLite's item data (EquipmentInventorySlot): the weapon and the ammunition. */
	private static final int WEAPON_SLOT = 3;
	private static final int AMMO_SLOT = 13;

	/**
	 * The Obligator's Smash: "a faster attack speed of 3 ticks, at the cost of only being able to hit one target" (OSRS
	 * Wiki, Obligator; Module:CombatStyles gives the Extending Chain Mace's Smash 3 ticks). Its Thrust has the item's 5.
	 */
	static final int OBLIGATOR_SMASH_SPEED = 3;

	/**
	 * How many monsters besides the one fought the Obligator's Thrust reaches: "up to two additional targets on or behind
	 * the main target, giving it an effective reach of 3 tiles" (OSRS Wiki, Obligator, read 2026-10-28). Measured in the
	 * Fractured Archive (recorded fights of 2026-10-27 and 28, 721 Thrusts): five Jellies on one tile, three of them
	 * hit by each Thrust.
	 */
	static final int OBLIGATOR_EXTRA_TARGETS = 2;

	/**
	 * Zeal: "Increases your defence by 25%, strength by 28%, and attack by 25%" (OSRS Wiki, Zeal), in the place of Piety's
	 * 20% and 23%; it needs 84 Prayer, 75 Attack and 75 Strength to be used.
	 */
	static final int ZEAL_ATTACK = 125;
	static final int ZEAL_STRENGTH = 128;
	static final int ZEAL_PRAYER = 84;
	static final int ZEAL_ATTACK_STRENGTH = 75;

	private static final Map<Integer, ItemEquipmentStats> STATS = new HashMap<>();

	static
	{
		// OSRS Wiki, Obligator (both versions have the same bonuses): stab +22, slash -8, crush +120; slash defence +16,
		// magic +3, ranged +8; strength +40; prayer +3; two-handed, speed 5
		ItemEquipmentStats obligator = ItemEquipmentStats.builder().slot(WEAPON_SLOT).isTwoHanded(true)
			.astab(22).aslash(-8).acrush(120).dslash(16).dmagic(3).drange(8).str(40).prayer(3).aspeed(5).build();
		STATS.put(OBLIGATOR, obligator);
		STATS.put(OBLIGATOR_UNCHARGED, obligator);
		// OSRS Wiki, Ascension crossbows: ranged +34, magic +3; stab defence -7, slash -5, magic +10; two-handed, speed 3
		STATS.put(ASCENSION_CROSSBOWS, ItemEquipmentStats.builder().slot(WEAPON_SLOT).isTwoHanded(true)
			.amagic(3).arange(34).dstab(-7).dslash(-5).dmagic(10).aspeed(3).build());
		// OSRS Wiki, Ascension bolts +53; Diamond and Onyx ascension bolts, with and without (e), +58
		STATS.put(ASCENSION_BOLTS, ItemEquipmentStats.builder().slot(AMMO_SLOT).rstr(53).build());
		for (int id : new int[]{DIAMOND_ASCENSION_BOLTS, DIAMOND_ASCENSION_BOLTS_E, ONYX_ASCENSION_BOLTS, ONYX_ASCENSION_BOLTS_E})
		{
			STATS.put(id, ItemEquipmentStats.builder().slot(AMMO_SLOT).rstr(58).build());
		}
		// OSRS Wiki, Zorya's tome (both versions): magic +45; stab defence +1, slash +5, crush +5, magic +10; magic damage
		// +15% (RuneLite's data gives it in percent: the Occult necklace 5.0); prayer +2; two-handed, speed 3
		ItemEquipmentStats tome = ItemEquipmentStats.builder().slot(WEAPON_SLOT).isTwoHanded(true)
			.amagic(45).dstab(1).dslash(5).dcrush(5).dmagic(10).mdmg(15f).prayer(2).aspeed(3).build();
		STATS.put(ZORYAS_TOME, tome);
		STATS.put(ZORYAS_TOME_UNCHARGED, tome);
	}

	private ArchiveItems()
	{
	}

	/** The OSRS Wiki's bonuses of one of these items, or null for any other item. */
	static ItemEquipmentStats equipment(int itemId)
	{
		return STATS.get(itemId);
	}

	/** Every item with bonuses here (for the tests). */
	static Iterable<Integer> items()
	{
		return STATS.keySet();
	}

	/** The Obligator, charged or not, by its name (uncharged it has no figure: {@link #noFigure}). */
	static boolean isObligator(String lowerName)
	{
		return lowerName.startsWith("obligator");
	}

	/**
	 * How many times the Obligator's Thrust rolls its damage on a monster: twice, three times when it is the second
	 * weakest to crush and four times when it is the weakest (Rewards Poll Blog of 2026-07-30: "rolls damage twice by
	 * default, then an extra roll for every Melee defence that's higher than Crush"; the post "Out Today" of 2026-10-20:
	 * "up to four times ... then picks the best result"). A defence equal to crush is not higher: no extra roll.
	 */
	static int obligatorRolls(int stab, int slash, int crush)
	{
		return 2 + (stab > crush ? 1 : 0) + (slash > crush ? 1 : 0);
	}

	/**
	 * What the Obligator's Thrust multiplies each damage roll by on a monster of this size, as {numerator, denominator}:
	 * 20% more on 2x2, 40% on 3x3 or larger (post "Out Today": "20% and 40% bonus damage vs. 2x2 and 3x3 (or bigger)
	 * targets respectively").
	 */
	static int[] obligatorSize(int size)
	{
		return size >= 3 ? new int[]{14, 10} : size == 2 ? new int[]{12, 10} : new int[]{1, 1};
	}

	/**
	 * The other monsters the Obligator's Thrust hits along with the one fought, as indices into {@code others} (each
	 * {x, y, size} of its south-west tile), nearest first, {@link #OBLIGATOR_EXTRA_TARGETS} at most: those standing on the
	 * tile of the monster fought nearest the player or on the two tiles behind it, in the line from the player to that
	 * tile. Each is hit with its own rolls and size. Measured in recorded fights (2026-10-27 and 28, the Fractured
	 * Archive): a Thrust on a Corpse with Xarth on the tile behind it hit Xarth too 5 times of 5 (the sixth, Xarth was
	 * dead), with his own 40% more (up to 43; fought, he took up to 53 in that kill, 38 without the 40%); on Xarth from the tile next to him, a
	 * Corpse on the tile behind him (the fourth from the player) was never hit (7 of 7), and a Corpse beside him not on the
	 * line never (167); the Jellies on the tile fought, two besides it. The posts of 2026-06-16 and 2026-07-30 said a large
	 * monster around takes the Thrust's targets away; the wiki and the post "Out Today" (2026-10-20) do not, and the game
	 * does not. Empty when the player stands on the monster's tiles.
	 */
	static List<Integer> obligatorReach(int playerX, int playerY, int x, int y, int size, List<int[]> others)
	{
		int nearX = Math.max(x, Math.min(playerX, x + size - 1));
		int nearY = Math.max(y, Math.min(playerY, y + size - 1));
		int dx = Integer.signum(nearX - playerX);
		int dy = Integer.signum(nearY - playerY);
		List<Integer> hit = new ArrayList<>();
		for (int step = 0; step < 3 && (dx != 0 || dy != 0); step++)
		{
			int tx = nearX + step * dx;
			int ty = nearY + step * dy;
			for (int i = 0; i < others.size() && hit.size() < OBLIGATOR_EXTRA_TARGETS; i++)
			{
				int[] o = others.get(i);
				if (!hit.contains(i) && tx >= o[0] && tx < o[0] + o[2] && ty >= o[1] && ty < o[1] + o[2])
				{
					hit.add(i);
				}
			}
		}
		return hit;
	}

	/** The Obligator's Smash style, by its name in Combat Options. */
	static boolean smash(String styleName)
	{
		return "Smash".equals(styleName);
	}

	/** The Ascension crossbows, which fire nothing but Ascension bolts. */
	static boolean ascensionCrossbows(String weaponName)
	{
		return weaponName != null && weaponName.toLowerCase(Locale.ROOT).startsWith("ascension crossbows");
	}

	/** An Ascension bolt of any kind (plain, diamond, onyx, enchanted or not): only the Ascension crossbows fire one. */
	static boolean ascensionBolts(String ammoName)
	{
		return ammoName != null && ammoName.toLowerCase(Locale.ROOT).contains("ascension bolts");
	}

	/**
	 * A weapon of these that gets no figure anywhere (not the weapon notice, the bank line, the attack style, the pieces'
	 * or the prayer's gain): the Obligator without charges (the OSRS Wiki does not say whether it attacks) and Zorya's
	 * tome ({@link #isTome}).
	 */
	static boolean noFigure(String lowerName)
	{
		return lowerName.equals("obligator (uncharged)") || isTome(lowerName);
	}

	/**
	 * Zorya's tome, charged or not. Its max hit by the Magic level is not published: the wiki and the Rewards Poll Blog
	 * say only "a base max hit of 18, putting it below the Eye of Ayak" (the Eye's page gives its formula, the tome's
	 * none), so it gets no figure ({@link SetCalc#plain}).
	 */
	static boolean isTome(String lowerName)
	{
		return lowerName.startsWith("zorya's tome");
	}
}
