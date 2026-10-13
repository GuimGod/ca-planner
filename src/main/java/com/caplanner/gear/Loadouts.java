/*
 * Copyright (c) 2026, GuimGod
 * All rights reserved. Licensed under the BSD 2-Clause License (see LICENSE).
 */
package com.caplanner.gear;

import java.util.ArrayList;
import java.util.List;

/** Adds up a set of pieces into what {@link SetCalc} works with. */
final class Loadouts
{
	private Loadouts()
	{
	}

	/**
	 * The bonuses of a whole set, with the weapon's own data.
	 *
	 * @param pieces the piece of each slot (null for an empty one), {@link SwitchSet#SLOTS} long
	 */
	static SetCalc.Loadout of(SwitchSet.Piece[] pieces, StyleData data)
	{
		SetCalc.Loadout l = new SetCalc.Loadout();
		SwitchSet.Piece weapon = pieces[SwitchSet.WEAPON];
		int weaponId = weapon == null ? -1 : weapon.itemId;
		String category = data.category(weaponId);
		String weaponName = weapon == null ? null : weapon.name;
		SwitchSet.Piece ammo = pieces[SwitchSet.AMMO];
		// ammunition the weapon does not shoot gives it nothing: bolts under a bow, arrows under a crossbow,
		// anything under a bow that makes its own arrows or a weapon that is its own ammunition
		String kind = Weapons.ammoKind(category, weaponName);
		if (ammo != null && Weapons.isShotAmmo(ammo.name) && (kind == null || !Weapons.has(ammo.name, kind)))
		{
			ammo = null;
		}
		else if (ammo != null && !Weapons.isShotAmmo(ammo.name) && ammo.rangedStrength != 0)
		{
			// what is left with ranged strength is fuel for one weapon of its own (tar for a salamander):
			// under any other weapon it gives nothing
			ammo = null;
		}
		List<String> names = new ArrayList<>();
		for (int slot = 0; slot < pieces.length; slot++)
		{
			SwitchSet.Piece p = slot == SwitchSet.AMMO ? ammo : pieces[slot];
			if (p == null)
			{
				continue;
			}
			l.stab += p.stab;
			l.slash += p.slash;
			l.crush += p.crush;
			l.magic += p.magic;
			l.ranged += p.ranged;
			l.strength += p.strength;
			l.rangedStrength += p.rangedStrength;
			l.magicDamage += p.magicDamage;
			l.inquisitor += p.inquisitor;
			l.mask = Math.max(l.mask, p.mask);
			l.salve = Math.max(l.salve, p.salve);
			l.crystal += SetEffects.crystalWeight(p.name);
			l.virtus += p.name != null && p.name.startsWith("Virtus ") ? 1 : 0;
			names.add(p.name);
		}
		l.voidMelee = SetEffects.voidKind(names, StyleCalc.MELEE);
		l.voidRanged = SetEffects.voidKind(names, StyleCalc.RANGED);
		l.dharokSet = SetEffects.wholeBarrowsSet(names, "dharok's ");
		l.weaponName = weaponName == null ? "" : weaponName;
		l.speed = weapon == null || weapon.speed <= 0 ? 4 : weapon.speed;
		l.category = category;
		l.styles = data.styles(weaponId);
		boolean shot = ammo != null && Weapons.isShotAmmo(ammo.name);
		l.bolt = shot ? SetCalc.boltKind(ammo.name) : 0;
		l.boltEffect = shot && l.bolt == 0 && ammo.name.endsWith("(e)") && !SetCalc.harmlessBolts(ammo.name);
		return l;
	}
}
