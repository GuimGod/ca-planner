/*
 * Copyright (c) 2026, GuimGod
 * All rights reserved. Licensed under the BSD 2-Clause License (see LICENSE).
 */
package com.caplanner.gear;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Picks, from the items a player owns, the set that deals the most to a boss. It compares
 * expected damage per second against the boss's defences; it knows nothing of the fight itself
 * (mechanics, phases, what the boss does), nor of supplies.
 *
 * <p>What it leaves out, on purpose, because the sums do not know them: the blowpipe (the darts
 * inside are not known), the Soulreaper axe, staves that need a spell on autocast, and effects
 * that only work with several pieces at once (a whole Void set). Special attacks are not counted.
 */
public final class Gear
{
	/** Weapons of each combat class that get the full, slower search. */
	private static final int WEAPONS_PER_CLASS = 5;
	/** Pieces of a slot tried against each other, picked by their plain bonuses. */
	private static final int PIECES_PER_SLOT = 8;
	private static final int PASSES = 3;
	/** The hitpoints left at which ruby bolts reach the most they can deal. */
	private static final int RUBY_FULL = 500;
	private static final String[] CLASSES = {StyleCalc.MELEE, StyleCalc.RANGED, StyleCalc.MAGIC};

	/** An item that can be worn, with the bonuses the game gives it. */
	public static final class Stat
	{
		public int itemId;
		/** The game's equipment slot index (0 head, 3 weapon, 5 shield, 13 ammunition...). */
		public int slot;
		public boolean twoHanded;
		public String name = "";
		public int stab;
		public int slash;
		public int crush;
		public int magic;
		public int ranged;
		public int strength;
		public int rangedStrength;
		/** Magic damage, in percent. */
		public double magicDamage;
		/** Ticks between attacks, for a weapon. */
		public int speed;
	}

	/** The player's real levels and the prayers they have unlocked. */
	public static final class Levels
	{
		public int attack = 1;
		public int strength = 1;
		public int ranged = 1;
		public int magic = 1;
		public int prayer = 1;
		public int hitpoints = 10;
		public boolean piety;
		public boolean chivalry;
		public boolean rigour;
		public boolean augury;
		public boolean deadeye;
		public boolean vigour;
		/** Every real level by skill name in lower case, for what an item asks before it can be worn; empty when not known. */
		public Map<String, Integer> all = new HashMap<>();
		/** Names, in lower case, of the runes the player owns; null when not known, and then any spell may be cast. */
		public Set<String> runes;
		/** The Ancient Magicks are unlocked (Desert Treasure I). */
		public boolean ancients;
	}

	/** What a task allows, on top of what the boss does: the search stays inside it. Names are parts of item names, in lower case. */
	public static final class Limits
	{
		/** The combat styles allowed ("melee", "ranged", "magic"); empty for any. */
		public Set<String> styles = new HashSet<>();
		/** The weapon must have one of these in its name; empty for any weapon. */
		public List<String> weapon = new ArrayList<>();
		/** Nothing with one of these in its name is worn. */
		public List<String> exclude = new ArrayList<>();
		public boolean oneHanded;
		/** A shield must be worn. */
		public boolean shield;
		/** "stab", "slash" or "crush": only melee, and only hits of that kind. */
		public String meleeType;
		/** A word the ammunition (or a thrown weapon) must have in its name. */
		public String ammoWord;
		/** What the fight makes the player wear: of each group of names, one piece is worn whatever it costs in damage. */
		public List<List<String>> mustWear = new ArrayList<>();
		/** "air", "water", "earth" or "fire": only spells of that element, and nothing that is not a spell. */
		public String spellElement;
		/** Only a weapon to cast spells with, of any element: what a staff with an attack of its own cannot do. */
		public boolean spellsOnly;
		/** The spells this player can cast, by their number in the game's autocast setting. Set by the search. */
		List<Integer> castable = new ArrayList<>();
		/** The combat class the search is on. Set by the search. */
		String now;
	}

	/** The set that was picked. */
	public static final class Result
	{
		/** The item of each equipment slot index; a slot left empty is not in the map. */
		public final Map<Integer, Stat> items;
		public final double dps;
		/** "Melee", "Ranged" or "Magic". */
		public final String style;
		/** The boss's hitpoints in the table, or 0 when it does not have them. */
		public final int bossHitpoints;
		/** Which groups of {@link Limits#mustWear} the player owns nothing of, by their place in the list. */
		public final List<Integer> mustWearMissing = new ArrayList<>();
		/** The spell the set is counted with ("Fire Surge"), or null when the weapon needs none. */
		public String spell;

		Result(Map<Integer, Stat> items, double dps, String style, int bossHitpoints)
		{
			this.items = items;
			this.dps = dps;
			this.style = style;
			this.bossHitpoints = bossHitpoints;
		}
	}

	private final StyleData data;
	private final WearLevels wear;

	public Gear() throws IOException
	{
		data = new StyleData();
		try (java.io.InputStream in = Gear.class.getResourceAsStream("wear-levels.tsv"))
		{
			wear = WearLevels.read(new java.io.InputStreamReader(
				in == null ? new java.io.ByteArrayInputStream(new byte[0]) : in, java.nio.charset.StandardCharsets.UTF_8));
		}
	}

	/**
	 * Whether a set can be picked for the boss: the table has it, and it is not fought in gear
	 * made inside the fight (the Gauntlet), where what the player owns does not count.
	 */
	public boolean knows(String boss)
	{
		// nor is a boss that is not fought with weapons at all (Zalcano is mined)
		String b = boss == null ? "" : boss.toLowerCase(Locale.ROOT);
		return boss != null && !b.contains("hunllef") && !b.equals("zalcano") && data.named(boss) != null;
	}

	/**
	 * @param boss   the boss as the Combat Achievements name it
	 * @param owned  everything the player owns that can be worn
	 * @param levels the player's levels
	 * @param onTask the boss is the player's Slayer task: the Slayer helmet counts
	 * @return the best set, or null when the boss is not in the table or nothing owned can hit it
	 */
	public Result best(String boss, Collection<Stat> owned, Levels levels, boolean onTask)
	{
		return best(boss, owned, levels, onTask, null);
	}

	/**
	 * As {@link #best(String, Collection, Levels, boolean)}, inside what a task allows.
	 *
	 * @param limits what the task allows, or null for anything
	 * @return the best set that keeps to the limits, or null when nothing owned does
	 */
	public Result best(String boss, Collection<Stat> owned, Levels levels, boolean onTask, Limits limits)
	{
		StyleData.Monster monster = knows(boss) ? data.named(boss) : null;
		return monster == null ? null : best(monster, Weapons.isInWilderness(boss), owned, levels, onTask, limits);
	}

	/**
	 * The best set against one monster of the table by its id: a form of a boss, one of several in a room.
	 *
	 * @return null when the table has no such monster or nothing owned does it
	 */
	public Result bestAt(int npcId, Collection<Stat> owned, Levels levels, boolean onTask, Limits limits)
	{
		StyleData.Monster monster = data.monster(npcId);
		return monster == null ? null : best(monster, false, owned, levels, onTask, limits);
	}

	/** Whether the table has a monster of that id. */
	public boolean hasMonster(int npcId)
	{
		return data.monster(npcId) != null;
	}

	/**
	 * Damage a second of a set as it is against one monster of the table, in whichever class its
	 * weapon does the most; 0 when the sums cannot answer.
	 */
	public double dpsAt(int npcId, Map<Integer, Stat> worn, Levels levels, boolean onTask)
	{
		StyleData.Monster monster = data.monster(npcId);
		if (monster == null)
		{
			return 0;
		}
		SetCalc.Target target = target(monster, false, onTask);
		SwitchSet.Piece[] set = new SwitchSet.Piece[SwitchSet.SLOTS];
		for (Map.Entry<Integer, Stat> e : worn.entrySet())
		{
			if (e.getKey() >= 0 && e.getKey() < SwitchSet.SLOTS && e.getValue() != null)
			{
				set[e.getKey()] = piece(e.getValue());
			}
		}
		if (set[SwitchSet.WEAPON] == null)
		{
			return 0;
		}
		Limits lim = new Limits();
		lim.castable = Spells.castable(levels.magic, levels.runes, levels.ancients, null);
		double most = 0;
		for (String cls : CLASSES)
		{
			lim.now = cls;
			SetCalc.Loadout l = Loadouts.of(set, data);
			boolean ofClass = cls.equals(SetCalc.combatClass(l)) || (StyleCalc.MAGIC.equals(cls) && casts(l, lim));
			if (ofClass)
			{
				most = Math.max(most, value(set, player(levels, cls), target, lim));
			}
		}
		return most / 0.6;
	}

	private static SetCalc.Target target(StyleData.Monster monster, boolean wilderness, boolean onTask)
	{
		SetCalc.Target target = monster.target();
		target.onTask = onTask;
		target.inWilderness = wilderness;
		// ruby bolts take a share of the hitpoints left, up to a most that is reached at 500 left: the
		// hitpoints to count them at are the ones that give their average over a whole kill
		int hp = monster.hitpoints;
		target.hitpointsNow = Math.max(1, hp <= RUBY_FULL ? hp / 2 : RUBY_FULL * (hp - RUBY_FULL / 2) / hp);
		return target;
	}

	private Result best(StyleData.Monster monster, boolean wilderness, Collection<Stat> owned, Levels levels, boolean onTask, Limits limits)
	{
		final Limits lim = limits == null ? new Limits() : limits;
		SetCalc.Target target = target(monster, wilderness, onTask);

		Map<Integer, SwitchSet.Piece> byId = new LinkedHashMap<>();
		Map<Integer, Stat> stats = new HashMap<>();
		for (Stat s : owned)
		{
			if (s != null && s.itemId > 0 && s.slot >= 0 && s.slot < SwitchSet.SLOTS && !byId.containsKey(s.itemId)
				&& !named(s.name, lim.exclude) && !Weapons.cannotBeUsed(s.name) && wear.canWear(s.name, levels.all))
			{
				byId.put(s.itemId, piece(s));
				stats.put(s.itemId, s);
			}
		}
		Map<Integer, List<SwitchSet.Piece>> bySlot = new HashMap<>();
		for (SwitchSet.Piece p : byId.values())
		{
			bySlot.computeIfAbsent(p.slot, k -> new ArrayList<>()).add(p);
		}
		List<SwitchSet.Piece> weapons = bySlot.getOrDefault(SwitchSet.WEAPON, new ArrayList<>());
		// what the fight makes the player wear: the pieces owned of each such need
		List<List<SwitchSet.Piece>> forced = new ArrayList<>();
		List<Integer> nothingOwned = new ArrayList<>();
		for (int g = 0; g < lim.mustWear.size(); g++)
		{
			List<SwitchSet.Piece> pieces = new ArrayList<>();
			for (SwitchSet.Piece p : byId.values())
			{
				if (p.slot != SwitchSet.WEAPON && named(p.name, lim.mustWear.get(g)))
				{
					pieces.add(p);
				}
			}
			if (pieces.isEmpty())
			{
				nothingOwned.add(g);
			}
			else
			{
				forced.add(pieces);
			}
		}

		lim.castable = Spells.castable(levels.magic, levels.runes, levels.ancients, lim.spellElement);
		SwitchSet.Piece[] bestSet = null;
		double bestValue = 0;
		String bestClass = null;
		for (String cls : CLASSES)
		{
			lim.now = cls;
			SetCalc.Player player = player(levels, cls);
			// every weapon of the class with a quick set around it, to find the few worth the full search
			List<Object[]> quick = new ArrayList<>();
			for (SwitchSet.Piece w : weapons)
			{
				SwitchSet.Piece[] set = new SwitchSet.Piece[SwitchSet.SLOTS];
				set[SwitchSet.WEAPON] = w;
				// a staff of the dead is its blade in melee and a staff to cast with in magic
				boolean ofClass = cls.equals(SetCalc.combatClass(Loadouts.of(set, data)))
					|| (StyleCalc.MAGIC.equals(cls) && "Bladed Staff".equals(data.category(w.itemId)));
				if (!ofClass || !allowed(w, cls, lim) || !leavesRoom(w, forced))
				{
					continue;
				}
				for (int slot = 0; slot < SwitchSet.SLOTS; slot++)
				{
					if (slot != SwitchSet.WEAPON)
					{
						List<SwitchSet.Piece> c = candidates(bySlot, slot, set, cls, lim);
						set[slot] = c.isEmpty() ? null : c.get(0);
					}
				}
				// the ammunition with the highest bonus may be one the sums cannot answer for (onyx bolts):
				// the weapon is judged with the ammunition that does the most, or it would be dropped here
				double value = value(set, player, target, lim);
				SwitchSet.Piece bestAmmo = set[SwitchSet.AMMO];
				for (SwitchSet.Piece ammo : candidates(bySlot, SwitchSet.AMMO, set, cls, lim))
				{
					set[SwitchSet.AMMO] = ammo;
					double with = value(set, player, target, lim);
					if (with > value + 1e-9)
					{
						value = with;
						bestAmmo = ammo;
					}
				}
				set[SwitchSet.AMMO] = bestAmmo;
				if (value > 0)
				{
					quick.add(new Object[]{value, set});
				}
			}
			// ties by the weapon's id, so the same items always give the same set whatever their order
			quick.sort(Comparator.comparingDouble((Object[] q) -> -(Double) q[0])
				.thenComparingInt(q -> ((SwitchSet.Piece[]) q[1])[SwitchSet.WEAPON].itemId));
			for (int i = 0; i < quick.size() && i < WEAPONS_PER_CLASS; i++)
			{
				SwitchSet.Piece[] set = (SwitchSet.Piece[]) quick.get(i)[1];
				// what must be worn goes on first and stays: of each need, the piece that costs the least
				Set<Integer> locked = new HashSet<>();
				boolean fits = true;
				for (List<SwitchSet.Piece> group : forced)
				{
					SwitchSet.Piece pick = null;
					double with = -1;
					for (SwitchSet.Piece p : group)
					{
						if (locked.contains(p.slot) || (p.slot == SwitchSet.SHIELD && set[SwitchSet.WEAPON].twoHanded))
						{
							continue;
						}
						SwitchSet.Piece before = set[p.slot];
						set[p.slot] = p;
						double v = value(set, player, target, lim);
						set[p.slot] = before;
						if (v > with + 1e-9 || (Math.abs(v - with) <= 1e-9 && pick != null && p.itemId < pick.itemId))
						{
							with = v;
							pick = p;
						}
					}
					if (pick == null)
					{
						fits = false;
						break;
					}
					set[pick.slot] = pick;
					locked.add(pick.slot);
				}
				if (!fits)
				{
					continue;
				}
				double value = improve(set, bySlot, cls, player, target, lim, locked);
				if (lim.shield && set[SwitchSet.SHIELD] == null)
				{
					// the task wants a shield worn even when none adds damage: the one that costs the set the least
					SwitchSet.Piece shield = null;
					double withShield = -1;
					for (SwitchSet.Piece p : bySlot.getOrDefault(SwitchSet.SHIELD, new ArrayList<>()))
					{
						set[SwitchSet.SHIELD] = p;
						double v = value(set, player, target, lim);
						if (v > withShield + 1e-9 || (Math.abs(v - withShield) <= 1e-9 && shield != null && p.itemId < shield.itemId))
						{
							withShield = v;
							shield = p;
						}
					}
					set[SwitchSet.SHIELD] = shield;
					if (shield == null)
					{
						continue;
					}
					value = withShield;
				}
				// a piece that is only there for an effect the plain bonuses do not show, and whose effect
				// does nothing here (a Salve amulet against the living), comes off
				for (int slot = 0; slot < SwitchSet.SLOTS; slot++)
				{
					SwitchSet.Piece p = set[slot];
					if (p == null || slot == SwitchSet.WEAPON || locked.contains(slot) || (slot == SwitchSet.SHIELD && lim.shield)
						|| (slot == SwitchSet.AMMO && SetEffects.isBlessing(p.name)) || p.score(cls, null) > 0)
					{
						continue;
					}
					set[slot] = null;
					if (value(set, player, target, lim) < value - 1e-9)
					{
						set[slot] = p;
					}
				}
				if (value > bestValue)
				{
					bestValue = value;
					bestSet = set.clone();
					bestClass = cls;
				}
			}
		}
		if (bestSet == null)
		{
			return null;
		}
		Map<Integer, Stat> items = new LinkedHashMap<>();
		for (int slot = 0; slot < SwitchSet.SLOTS; slot++)
		{
			if (bestSet[slot] != null)
			{
				items.put(slot, stats.get(bestSet[slot].itemId));
			}
		}
		String style = bestClass.substring(0, 1).toUpperCase(Locale.ROOT) + bestClass.substring(1);
		Result result = new Result(items, bestValue / 0.6, style, monster.hitpoints);
		result.mustWearMissing.addAll(nothingOwned);
		lim.now = bestClass;
		int spell = spell(bestSet, player(levels, bestClass), target, lim);
		result.spell = spell > 0 ? Spells.name(spell) : null;
		return result;
	}

	/** Whether the weapon leaves free the slot of something that must be worn (a shield, under a two-handed weapon, has none). */
	private static boolean leavesRoom(SwitchSet.Piece weapon, List<List<SwitchSet.Piece>> forced)
	{
		for (List<SwitchSet.Piece> group : forced)
		{
			boolean room = false;
			for (SwitchSet.Piece p : group)
			{
				room |= !(p.slot == SwitchSet.SHIELD && weapon.twoHanded);
			}
			if (!room)
			{
				return false;
			}
		}
		return true;
	}

	/**
	 * The weapons owned that a task's rule names and the sums cannot answer for: when {@link #best}
	 * finds no set and this is not empty, the player has the weapon and it is the plugin that has no
	 * figure for it. A weapon with a figure that merely cannot be used (a bow with no arrows) is not here.
	 */
	public List<String> weaponsWithoutFigure(Collection<Stat> owned, Limits limits)
	{
		List<String> out = new ArrayList<>();
		if (limits == null || limits.weapon.isEmpty())
		{
			return out;
		}
		for (Stat s : owned)
		{
			if (s == null || s.slot != SwitchSet.WEAPON || !named(s.name, limits.weapon) || named(s.name, limits.exclude)
				|| out.contains(s.name))
			{
				continue;
			}
			SwitchSet.Piece[] alone = new SwitchSet.Piece[SwitchSet.SLOTS];
			alone[SwitchSet.WEAPON] = piece(s);
			SetCalc.Loadout l = Loadouts.of(alone, data);
			if (l.category != null && l.category.endsWith("Staff") && !"Powered Staff".equals(l.category))
			{
				// a staff to cast with has a figure whenever there is a spell to cast: it is the runes that are missing
				l.spell = 1;
			}
			if (data.styles(s.itemId).isEmpty() || !SetCalc.plain(l))
			{
				out.add(s.name);
			}
		}
		java.util.Collections.sort(out);
		return out;
	}

	/** Tries, slot by slot, every candidate against the one in place, until a whole pass changes nothing. */
	private double improve(SwitchSet.Piece[] set, Map<Integer, List<SwitchSet.Piece>> bySlot, String cls,
		SetCalc.Player player, SetCalc.Target target, Limits lim, Set<Integer> locked)
	{
		double value = value(set, player, target, lim);
		for (int pass = 0; pass < PASSES; pass++)
		{
			boolean changed = false;
			for (int slot = 0; slot < SwitchSet.SLOTS; slot++)
			{
				if (slot == SwitchSet.WEAPON || locked.contains(slot))
				{
					continue;
				}
				SwitchSet.Piece kept = set[slot];
				List<SwitchSet.Piece> options = new ArrayList<>(candidates(bySlot, slot, set, cls, lim));
				options.add(null);
				for (SwitchSet.Piece option : options)
				{
					if (option == kept)
					{
						continue;
					}
					set[slot] = option;
					double with = value(set, player, target, lim);
					if (with > value + 1e-9)
					{
						value = with;
						kept = option;
						changed = true;
					}
				}
				set[slot] = kept;
			}
			if (!changed)
			{
				break;
			}
		}
		return value;
	}

	/**
	 * What a slot may hold around the weapon in {@code set}, best plain bonuses first: nothing in
	 * the shield slot under a two-handed weapon, only the ammunition the weapon shoots, and always
	 * the pieces whose worth the plain bonuses do not show (a Slayer helmet, a Salve amulet).
	 */
	private List<SwitchSet.Piece> candidates(Map<Integer, List<SwitchSet.Piece>> bySlot, int slot, SwitchSet.Piece[] set,
		String cls, Limits lim)
	{
		List<SwitchSet.Piece> out = new ArrayList<>();
		SwitchSet.Piece weapon = set[SwitchSet.WEAPON];
		if (slot == SwitchSet.SHIELD && weapon != null && weapon.twoHanded)
		{
			return out;
		}
		String shoots = weapon == null ? null : Weapons.ammoKind(data.category(weapon.itemId), weapon.name);
		List<SwitchSet.Piece> all = new ArrayList<>(bySlot.getOrDefault(slot, new ArrayList<>()));
		all.sort(Comparator.comparingDouble((SwitchSet.Piece p) -> -p.score(cls, null)).thenComparingInt(p -> p.itemId));
		List<SwitchSet.Piece> special = new ArrayList<>();
		for (SwitchSet.Piece p : all)
		{
			boolean hidden = p.mask > 0 || p.salve > 0 || p.inquisitor > 0 || SetEffects.crystalWeight(p.name) > 0;
			if (slot == SwitchSet.AMMO)
			{
				// the ammunition slot holds what the weapon shoots or, when it shoots nothing from
				// there, a blessing; never another thing that merely fits the slot (a grapple)
				boolean shot = Weapons.isShotAmmo(p.name);
				if (shot ? shoots == null || !Weapons.has(p.name, shoots) : !SetEffects.isBlessing(p.name))
				{
					continue;
				}
				if (shot && !Weapons.canFire(weapon.name, p.name))
				{
					// arrows too good for the bow, bolts too good for the crossbow
					continue;
				}
				if (shot && lim.ammoWord != null && !Weapons.has(p.name, lim.ammoWord))
				{
					continue;
				}
			}
			else if (!hidden && p.score(cls, null) <= 0)
			{
				// a piece that adds nothing to the damage is not part of a set picked for damage
				continue;
			}
			if (out.size() < PIECES_PER_SLOT)
			{
				out.add(p);
			}
			else if (hidden || (slot == SwitchSet.AMMO && SetCalc.boltKind(p.name) > 0))
			{
				special.add(p);
			}
		}
		out.addAll(special);
		return out;
	}

	/** Whether the weapon of the loadout is one to cast spells with, in the class the search is on. */
	private static boolean casts(SetCalc.Loadout l, Limits lim)
	{
		// a staff with an attack of its own casts none of these, whether the sums know that attack or not
		return StyleCalc.MAGIC.equals(lim.now) && l.category != null && l.category.endsWith("Staff")
			&& !"Powered Staff".equals(l.category) && SetCalc.staffBase(l.weaponName.toLowerCase(Locale.ROOT), 99) == 0;
	}

	/** The spell the set does the most with, of the ones the player can cast; 0 when its weapon casts none. */
	private int spell(SwitchSet.Piece[] set, SetCalc.Player player, SetCalc.Target target, Limits lim)
	{
		SetCalc.Loadout l = Loadouts.of(set, data);
		if (!casts(l, lim))
		{
			return 0;
		}
		l.tome = Spells.tome(set[SwitchSet.SHIELD] == null ? null : set[SwitchSet.SHIELD].name);
		int best = 0;
		double most = 0;
		for (int spell : lim.castable)
		{
			l.spell = spell;
			if (!SetCalc.plain(l))
			{
				continue;
			}
			double v = SetCalc.perTick(l, player, target, false);
			if (v > most + 1e-9)
			{
				most = v;
				best = spell;
			}
		}
		return best;
	}

	/** Expected damage per tick of the set, or 0 when the sums cannot answer for its weapon. */
	private double value(SwitchSet.Piece[] set, SetCalc.Player player, SetCalc.Target target, Limits lim)
	{
		SetCalc.Loadout l = Loadouts.of(set, data);
		if (casts(l, lim))
		{
			// a staff with no attack of its own: as good as the best spell the player can cast with it
			l.tome = Spells.tome(set[SwitchSet.SHIELD] == null ? null : set[SwitchSet.SHIELD].name);
			double most = 0;
			for (int spell : lim.castable)
			{
				l.spell = spell;
				if (SetCalc.plain(l))
				{
					most = Math.max(most, SetCalc.perTick(l, player, target, false));
				}
			}
			return most;
		}
		if (lim.spellElement != null || lim.spellsOnly)
		{
			// only spells will do: nothing else is a choice
			return 0;
		}
		if (lim.meleeType != null)
		{
			// only the weapon's styles that hit that way count; a weapon with none is not a choice
			List<StyleData.Style> kept = new ArrayList<>();
			for (StyleData.Style style : l.styles)
			{
				if (lim.meleeType.equals(style.type))
				{
					kept.add(style);
				}
			}
			if (kept.isEmpty())
			{
				return 0;
			}
			l.styles = kept;
		}
		if (!SetCalc.plain(l))
		{
			return 0;
		}
		SwitchSet.Piece weapon = set[SwitchSet.WEAPON];
		String shoots = weapon == null ? null : Weapons.ammoKind(l.category, weapon.name);
		SwitchSet.Piece ammo = set[SwitchSet.AMMO];
		if (shoots != null && (ammo == null || !Weapons.has(ammo.name, shoots)))
		{
			// a bow with no arrows, a crossbow with no bolts: it does not shoot
			return 0;
		}
		// the best melee style of the weapon is taken by the sums themselves
		return Math.max(0, SetCalc.perTick(l, player, target, false));
	}

	/** Whether a task's limits let this weapon be the one in hand. */
	private boolean allowed(SwitchSet.Piece weapon, String cls, Limits lim)
	{
		if (!lim.styles.isEmpty() && !lim.styles.contains(cls))
		{
			return false;
		}
		if ((lim.spellElement != null || lim.spellsOnly) && !StyleCalc.MAGIC.equals(cls))
		{
			return false;
		}
		if (lim.meleeType != null && !StyleCalc.MELEE.equals(cls))
		{
			return false;
		}
		if (!lim.weapon.isEmpty() && !named(weapon.name, lim.weapon))
		{
			return false;
		}
		if ((lim.oneHanded || lim.shield) && weapon.twoHanded)
		{
			return false;
		}
		if (lim.ammoWord == null)
		{
			return true;
		}
		// a weapon that is its own ammunition (darts, knives) must itself be of the kind asked for; any
		// other must shoot something from the ammunition slot, or the task's ammunition is never used
		String category = data.category(weapon.itemId);
		return Weapons.throwsItself(category) ? Weapons.has(weapon.name, lim.ammoWord) : Weapons.ammoKind(category, weapon.name) != null;
	}

	private static boolean named(String name, List<String> parts)
	{
		for (String part : parts)
		{
			if (Weapons.has(name, part))
			{
				return true;
			}
		}
		return false;
	}

	/** The player with the potion and the best prayer of the class, as on a prepared kill. */
	private static SetCalc.Player player(Levels v, String cls)
	{
		SetCalc.Player p = new SetCalc.Player();
		// a super combat potion and a ranging potion; magic is taken unboosted
		p.attack = v.attack + 5 + v.attack * 15 / 100;
		p.strength = v.strength + 5 + v.strength * 15 / 100;
		p.ranged = v.ranged + 4 + v.ranged / 10;
		p.magic = v.magic;
		p.hitpoints = v.hitpoints;
		p.hitpointsMax = v.hitpoints;
		p.prayerLevel = v.prayer;
		p.piety = v.piety;
		p.chivalry = v.chivalry;
		p.rigour = v.rigour;
		p.augury = v.augury;
		p.deadeye = v.deadeye;
		p.vigour = v.vigour;
		// SetCalc fills in the best prayer of a class when a prayer of another class is on: say one is
		if (StyleCalc.MAGIC.equals(cls))
		{
			p.prayerRanged = 101;
		}
		else
		{
			p.prayerMagic = 101;
		}
		return SetCalc.withPrayerOf(p, cls);
	}

	private static SwitchSet.Piece piece(Stat s)
	{
		SwitchSet.Piece p = new SwitchSet.Piece();
		p.itemId = s.itemId;
		p.slot = s.slot;
		p.twoHanded = s.twoHanded;
		p.name = s.name == null ? "" : s.name;
		p.stab = s.stab;
		p.slash = s.slash;
		p.crush = s.crush;
		p.magic = s.magic;
		p.ranged = s.ranged;
		p.strength = s.strength;
		p.rangedStrength = s.rangedStrength;
		p.magicDamage = s.magicDamage;
		p.speed = s.speed;
		p.inquisitor = Weapons.inquisitorWeight(p.name);
		p.mask = SetEffects.maskKind(p.name);
		p.salve = SetEffects.salveKind(p.name);
		return p;
	}
}
