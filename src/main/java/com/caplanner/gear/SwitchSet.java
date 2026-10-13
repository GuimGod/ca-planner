/*
 * Copyright (c) 2026, GuimGod
 * All rights reserved. Licensed under the BSD 2-Clause License (see LICENSE).
 */
package com.caplanner.gear;

import java.util.ArrayList;
import java.util.List;

/**
 * Works out which pieces of a gear switch were left behind. For the combat class of the weapon
 * in hand, the pieces still in the inventory that belong with it are the ones that beat what
 * is worn in their slot for that class. Nothing here knows about any monster.
 */
final class SwitchSet
{
	/** Equipment slot indices, as in RuneLite's EquipmentInventorySlot. */
	static final int WEAPON = 3;
	static final int SHIELD = 5;
	static final int AMMO = 13;
	static final int SLOTS = 14;

	/** An equippable item and the bonuses that matter for a switch. */
	static final class Piece
	{
		int itemId;
		int slot;
		boolean twoHanded;
		int stab;
		int slash;
		int crush;
		int magic;
		int ranged;
		int strength;
		int rangedStrength;
		/** Magic damage, in percent. */
		double magicDamage;
		/**
		 * What a conditional effect adds to this piece right now, in the same units as the score:
		 * crystal armour with a crystal bow, Inquisitor's on crush, a void piece of the set in use.
		 */
		double setBonus;
		/**
		 * True for a piece that must not be swapped out for its bonuses: it is worn for an effect the
		 * numbers on the item do not show (a Slayer helmet, a Salve amulet, a set that works as a whole).
		 */
		boolean keep;
		/** True for an item carried to be used (a teleport ring): it is never asked for. */
		boolean carriedForUse;
		String name = "";
		/** Ticks between attacks, for a weapon. */
		int speed;
		/** Inquisitor's weight: helm 1, hauberk 2, plateskirt 2. */
		int inquisitor;
		/** Slayer helmet or Black mask: {@link SetEffects#MASK}, {@link SetEffects#MASK_IMBUED}, or 0. */
		int mask;
		/** Salve amulet: one of the {@code SetEffects.SALVE} kinds, or 0. */
		int salve;
		/**
		 * True for a blessing while the weapon in hand leaves the ammunition slot free: with that slot
		 * empty it is the one thing asked for there.
		 */
		boolean blessing;

		/**
		 * How much this piece offers the combat class: its attack bonus plus its damage bonus, weighted.
		 * For melee the attack bonus is that of {@code meleeType} (stab, slash or crush), or the best of
		 * the three when the type is not known.
		 */
		double score(String combatClass, String meleeType)
		{
			if (StyleCalc.MAGIC.equals(combatClass))
			{
				return magic + 10 * magicDamage + setBonus;
			}
			if (StyleCalc.RANGED.equals(combatClass))
			{
				return ranged + 2 * rangedStrength + setBonus;
			}
			int attack = StyleCalc.STAB.equals(meleeType) ? stab
				: StyleCalc.SLASH.equals(meleeType) ? slash
				: StyleCalc.CRUSH.equals(meleeType) ? crush
				: Math.max(stab, Math.max(slash, crush));
			return attack + 2 * strength + setBonus;
		}
	}

	private SwitchSet()
	{
	}

	/**
	 * The inventory pieces still to put on, in inventory order: for each slot, the carried piece
	 * that offers the class the most, when it offers more than the piece worn there. The weapon
	 * and the ammunition are the player's own choice and are never listed (a blessing is, into an
	 * empty ammunition slot the weapon does not use); neither is a shield while a two-handed
	 * weapon is held.
	 *
	 * @param combatClass melee, ranged or magic: the class of the weapon in hand
	 * @param meleeType   stab, slash or crush: the damage type of the melee style in use, or null
	 * @param worn        the worn piece of each slot (null for an empty slot), {@link #SLOTS} long
	 * @param inventory   the equippable items carried, in inventory order
	 */
	static List<Integer> missing(String combatClass, String meleeType, Piece[] worn, List<Piece> inventory)
	{
		List<Integer> missing = new ArrayList<>();
		if (combatClass == null || worn == null || inventory == null)
		{
			return missing;
		}
		Piece[] best = best(combatClass, meleeType, worn, inventory);
		for (Piece p : inventory)
		{
			if (p != null && p.slot >= 0 && p.slot < SLOTS && best[p.slot] == p)
			{
				missing.add(p.itemId);
			}
		}
		return missing;
	}

	/**
	 * The worn pieces that are worse than an empty slot for the class in use (a negative attack
	 * bonus and nothing to make up for it), in slot order, when nothing carried is better there:
	 * the slot does more bare. Never the weapon or the ammunition, never a piece that is worn
	 * for an effect its bonuses do not show, and never a diary or teleport item: those are worn
	 * for what they do, not for their bonuses.
	 */
	static List<Integer> takeOff(String combatClass, String meleeType, Piece[] worn, List<Piece> inventory)
	{
		List<Integer> off = new ArrayList<>();
		if (combatClass == null || worn == null || inventory == null)
		{
			return off;
		}
		Piece[] best = best(combatClass, meleeType, worn, inventory);
		for (int slot = 0; slot < worn.length && slot < SLOTS; slot++)
		{
			Piece on = worn[slot];
			if (on != null && slot != WEAPON && slot != AMMO && !on.keep && !on.carriedForUse && best[slot] == null
				&& on.score(combatClass, meleeType) < 0)
			{
				off.add(on.itemId);
			}
		}
		return off;
	}

	/**
	 * What would be worn once the switch is finished: the pieces {@link #missing} lists put on and the
	 * ones {@link #takeOff} lists taken off. The array given is not changed.
	 */
	static Piece[] after(String combatClass, String meleeType, Piece[] worn, List<Piece> inventory)
	{
		Piece[] done = worn == null ? new Piece[SLOTS] : worn.clone();
		if (combatClass == null || worn == null || inventory == null)
		{
			return done;
		}
		Piece[] best = best(combatClass, meleeType, worn, inventory);
		for (int slot = 0; slot < done.length && slot < SLOTS; slot++)
		{
			Piece on = done[slot];
			if (best[slot] != null)
			{
				done[slot] = best[slot];
			}
			else if (on != null && slot != WEAPON && slot != AMMO && !on.keep && !on.carriedForUse
				&& on.score(combatClass, meleeType) < 0)
			{
				done[slot] = null;
			}
		}
		return done;
	}

	/** For each slot, the carried piece that beats what is worn there by the most, or null. */
	private static Piece[] best(String combatClass, String meleeType, Piece[] worn, List<Piece> inventory)
	{
		Piece weapon = worn.length > WEAPON ? worn[WEAPON] : null;
		boolean twoHanded = weapon != null && weapon.twoHanded;
		Piece[] best = new Piece[SLOTS];
		for (Piece p : inventory)
		{
			if (p == null || p.carriedForUse || p.slot < 0 || p.slot >= SLOTS || p.slot == WEAPON
				|| (p.slot == SHIELD && twoHanded))
			{
				continue;
			}
			Piece on = p.slot < worn.length ? worn[p.slot] : null;
			if (p.slot == AMMO)
			{
				// the ammunition is the player's choice; only a blessing is asked for, into an empty slot
				if (p.blessing && on == null && best[AMMO] == null)
				{
					best[AMMO] = p;
				}
				continue;
			}
			if (on != null && on.keep && p.setBonus < SetEffects.VOID_PIECE)
			{
				continue; // worn for an effect its bonuses do not show; only the set in use replaces it
			}
			double current = on == null ? 0 : on.score(combatClass, meleeType);
			double offered = p.score(combatClass, meleeType);
			if (offered <= 0)
			{
				// a piece with nothing to give this combat class is never asked for: a teleport amulet does not
				// "beat" a melee amulet under a bow. The worn piece that holds the class back is asked off instead
				continue;
			}
			if (offered > current && (best[p.slot] == null || offered > best[p.slot].score(combatClass, meleeType)))
			{
				best[p.slot] = p;
			}
		}
		return best;
	}
}
