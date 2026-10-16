/*
 * Copyright (c) 2026, GuimGod
 * All rights reserved. Licensed under the BSD 2-Clause License (see LICENSE).
 */
package com.caplanner.gear;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Fights that one set does not answer: several bosses in a room, a boss that changes form, a part
 * of the fight that only one style harms. For each, the bundled table names the parts; the set
 * worn is picked for the first, and for every other part the pieces to carry and change into.
 * It says what to bring, not when to change: nothing here watches the fight.
 */
public final class Fights
{
	/** Pieces of one part carried at most: one row of the inventory, the most telling ones. */
	private static final int MOST_CARRIED = 4;
	/** Against the clock every piece tells: two rows of the inventory. */
	public static final int MOST_CARRIED_FOR_SPEED = 8;
	/** The order pieces are carried in, most telling first: weapon, ammunition, shield, amulet, body, legs, head, cape, hands, ring, feet. */
	private static final int[] ORDER = {3, 13, 5, 2, 4, 7, 0, 1, 9, 12, 10};
	/**
	 * A weapon already going (worn, or carried for another part) is used for a part whose best does at most this
	 * share more: a tie, and one weapon fewer to carry and change to.
	 */
	static final double TIE = 0.01;

	private static final class Part
	{
		/** "main": worn. "switch": up to two rows of pieces. "weapon": only what is held. */
		String kind;
		int npcId;
		String label;
		String style;
		String note = "";
		/** The sums do not know what makes this part what it is, so no figure is shown for it. */
		boolean noFigure;
		/** Pieces this part may have carried for it when more than the plugin's usual: a part changed into for long. */
		int carry;
		/**
		 * The weapons for this part by name, best first: a part the sums cannot weigh (a spec, a monster that dies to
		 * one kind of weapon). The first one owned is carried; empty for a part the sums pick for.
		 */
		List<String> byName = new ArrayList<>();
		/** The same names as the table writes them, for saying which is the best when none is owned. */
		List<String> byNameShown = new ArrayList<>();
		/**
		 * Pieces to change into with the weapon carried by name, one group a piece, the best of a group first: a group's
		 * first owned piece is carried when it does more than what the set wears in its slot (see {@link #strength}).
		 */
		List<List<String>> pieces = new ArrayList<>();
		/** Pieces are judged by their strength bonus (a special attack that wants damage); otherwise by their attack bonus. */
		boolean strength;
		/** The part is only for a set worn of this style ("melee", "ranged", "magic"); null for any. */
		String when;
		/** Its pieces are carried whenever owned and not worn: they are there for what they do, not for a bonus. */
		boolean keep;
		/** With none of its weapons by name owned, the part is picked by the sums against its monster instead. */
		boolean sums;
		/** On a main line: its note is advice for the fight as a rule, left unsaid under a task's own rule. */
		boolean advice;
	}

	/** One part of a fight other than the first, and what to carry for it. */
	public static final class Role
	{
		public final String label;
		/** "Melee", "Ranged" or "Magic"; empty when nothing owned does it. */
		public final String style;
		/** Damage a second against that part, wearing the set with these pieces changed in. */
		public final double dps;
		/** The pieces to carry, most telling first; empty when the set worn already does it, or nothing owned does. */
		public final List<Gear.Stat> carried;
		public final String spell;
		public final String note;
		/** Nothing the player owns harms this part. */
		public final boolean nothing;
		/** For a part by name when none of its weapons is owned: the best of them, as the table writes it; null otherwise. */
		public String best;
		/**
		 * When nothing is carried for this part because its weapon is carried for another: that weapon's name and
		 * the other part's label. Null otherwise.
		 */
		public final String sharedWeapon;
		public final String sharedWith;
		/** What tells this part from the others of the boss, for the changes the player makes to it. */
		public String key = "";
		/** The monster of the table it is summed against; 0 for a part by name, which is not summed. */
		public int npcId;
		/**
		 * Every piece its damage was counted with, changed into over the set worn: its own pieces and those it shares with
		 * a part before it (a weapon carried for another part). Its own pieces only when not set.
		 */
		public List<Gear.Stat> counted;

		Role(String label, String style, double dps, List<Gear.Stat> carried, String spell, String note, boolean nothing)
		{
			this(label, style, dps, carried, spell, note, nothing, null, null);
		}

		Role(String label, String style, double dps, List<Gear.Stat> carried, String spell, String note, boolean nothing,
			String sharedWeapon, String sharedWith)
		{
			this.label = label;
			this.style = style;
			this.dps = dps;
			this.carried = Collections.unmodifiableList(carried);
			this.spell = spell;
			this.note = note;
			this.nothing = nothing;
			this.sharedWeapon = sharedWeapon;
			this.sharedWith = sharedWith;
		}
	}

	/** The set to wear and what to carry. */
	public static final class Plan
	{
		public final Gear.Result main;
		/** What the set worn is for. */
		public final String mainLabel;
		public final String mainNote;
		public final List<Role> roles;
		/** The parts the task's own rule leaves out (a weapon it does not allow, a style it forbids), by label. */
		public final List<String> leftOut;
		/** The monster of the table the set worn is picked against; 0 for the boss's own row. */
		public int mainNpcId;

		Plan(Gear.Result main, String mainLabel, String mainNote, List<Role> roles, List<String> leftOut)
		{
			this.main = main;
			this.mainLabel = mainLabel;
			this.mainNote = mainNote;
			this.roles = Collections.unmodifiableList(roles);
			this.leftOut = Collections.unmodifiableList(leftOut);
		}
	}

	private final Map<String, List<Part>> byBoss = new HashMap<>();

	/**
	 * Reads "boss, kind, npc id, label, style, note" lines, tab separated; "#" starts a comment.
	 * Kinds: main, switch, weapon. The style ("melee", "ranged", "magic") may be empty for whichever
	 * does the most, or "other" for the best that is not the style of the set worn. The first line of a
	 * boss must be its main part.
	 */
	public static Fights read(Reader table) throws IOException
	{
		Fights out = new Fights();
		BufferedReader in = new BufferedReader(table);
		String line;
		while ((line = in.readLine()) != null)
		{
			if (line.trim().isEmpty() || line.startsWith("#"))
			{
				continue;
			}
			String[] c = line.split("\t", -1);
			if (c.length < 4)
			{
				continue;
			}
			Part p = new Part();
			p.kind = c[1].trim().toLowerCase(Locale.ROOT);
			try
			{
				p.npcId = Integer.parseInt(c[2].trim());
			}
			catch (NumberFormatException e)
			{
				continue;
			}
			p.label = c[3].trim();
			p.style = c.length > 4 && !c[4].trim().isEmpty() ? c[4].trim().toLowerCase(Locale.ROOT) : null;
			p.note = c.length > 5 ? c[5].trim() : "";
			for (String flag : (c.length > 6 ? c[6] : "").trim().toLowerCase(Locale.ROOT).split("\\s+"))
			{
				if (flag.equals("nofigure"))
				{
					p.noFigure = true;
				}
				else if (flag.equals("str"))
				{
					p.strength = true;
				}
				else if (flag.equals("keep"))
				{
					p.keep = true;
				}
				else if (flag.equals("advice"))
				{
					p.advice = true;
				}
				else if (flag.equals("sums"))
				{
					p.sums = true;
				}
				else if (flag.startsWith("when="))
				{
					p.when = flag.substring(5);
				}
				else if (flag.startsWith("carry"))
				{
					try
					{
						p.carry = Integer.parseInt(flag.substring(5));
					}
					catch (NumberFormatException e)
					{
						// not a number of pieces: the usual
					}
				}
			}
			if (c.length > 7)
			{
				for (String name : c[7].split(";"))
				{
					if (!name.trim().isEmpty())
					{
						p.byName.add(name.trim().toLowerCase(Locale.ROOT));
						p.byNameShown.add(name.trim());
						p.noFigure = true;
					}
				}
			}
			if (c.length > 8)
			{
				for (String group : c[8].split(";"))
				{
					List<String> one = new ArrayList<>();
					for (String name : group.split("[|]"))
					{
						if (!name.trim().isEmpty())
						{
							one.add(name.trim().toLowerCase(Locale.ROOT));
						}
					}
					if (!one.isEmpty())
					{
						p.pieces.add(one);
					}
				}
			}
			out.byBoss.computeIfAbsent(key(c[0]), k -> new ArrayList<>()).add(p);
		}
		// a boss whose first line is not its main part is not usable
		out.byBoss.values().removeIf(parts -> !"main".equals(parts.get(0).kind));
		return out;
	}

	private static String key(String boss)
	{
		return boss == null ? "" : boss.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
	}

	/** Whether the table has parts for the boss. */
	public boolean has(String boss)
	{
		return byBoss.containsKey(key(boss));
	}

	public Set<String> bosses()
	{
		return byBoss.keySet();
	}

	/** The npc id of the part the set worn is picked for, or 0 when the boss is not in the table. For the bench. */
	int mainNpc(String boss)
	{
		List<Part> parts = byBoss.get(key(boss));
		return parts == null ? 0 : parts.get(0).npcId;
	}

	/** The npc ids the table names, for checking it against the monster table. */
	List<Integer> npcIds()
	{
		List<Integer> out = new ArrayList<>();
		for (List<Part> parts : byBoss.values())
		{
			for (Part p : parts)
			{
				// 0: the boss's own row, or a part by name that the sums never look at
				if (p.npcId != 0)
				{
					out.add(p.npcId);
				}
			}
		}
		return out;
	}

	/**
	 * @param limits what a task allows, or null; it holds for every part
	 * @return the set to wear and what to carry, or null when the boss is not in the table or
	 *         nothing owned does its main part
	 */
	public Plan plan(Gear gear, String boss, Collection<Gear.Stat> owned, Gear.Levels levels, boolean onTask, Gear.Limits limits)
	{
		return plan(gear, boss, owned, levels, onTask, limits, MOST_CARRIED);
	}

	/** @param mostCarried how many pieces a part may have carried for it */
	public Plan plan(Gear gear, String boss, Collection<Gear.Stat> owned, Gear.Levels levels, boolean onTask, Gear.Limits limits,
		int mostCarried)
	{
		List<Part> parts = byBoss.get(key(boss));
		if (parts == null)
		{
			return null;
		}
		Part first = parts.get(0);
		// npc 0: the boss's own row, as for a boss of one part (the Wilderness and the awakened fights included)
		Gear.Result main = first.npcId == 0 ? gear.best(boss, owned, levels, onTask, within(limits, first.style))
			: gear.bestAt(first.npcId, owned, levels, onTask, within(limits, first.style));
		if (main == null)
		{
			return null;
		}
		// what is carried by name must be something the player can wear and attack with, as for the set
		List<Gear.Stat> usable = new ArrayList<>();
		for (Gear.Stat s : owned)
		{
			if (gear.canUse(s, levels))
			{
				usable.add(s);
			}
		}
		// the weapons going anyway: the one worn, and the one each part carried by name takes
		Map<Integer, Gear.Stat> going = new LinkedHashMap<>();
		Gear.Stat held = main.items.get(3);
		if (held != null)
		{
			going.put(held.itemId, held);
		}
		List<String> leftOut = new ArrayList<>();
		List<Part> used = new ArrayList<>();
		for (Part part : parts.subList(1, parts.size()))
		{
			if (part.when != null && (main.style == null || !java.util.Arrays.asList(part.when.split("[|,]")).contains(main.style.toLowerCase(Locale.ROOT))))
			{
				// a part for a set of another style (a spec of the melee set when the set is ranged)
				continue;
			}
			if (!part.byName.isEmpty() && allowed(part, limits).isEmpty())
			{
				// the task's own rule allows none of its weapons, or forbids its style: left out, and said so
				leftOut.add(part.label);
				continue;
			}
			used.add(part);
			Gear.Stat named = part.byName.isEmpty() ? null : weaponByName(allowed(part, limits), usable, limits);
			if (named != null)
			{
				going.putIfAbsent(named.itemId, named);
			}
		}
		Set<Integer> carriedAlready = new HashSet<>();
		// the part each carried piece is carried for
		Map<Integer, String> carriedFor = new HashMap<>();
		List<Role> roles = new ArrayList<>();
		for (Part part : used)
		{
			Gear.Stat owns = part.byName.isEmpty() ? null : weaponByName(allowed(part, limits), usable, limits);
			// "sums": with none of its weapons owned, the part is picked by the sums against its monster
			if (!part.byName.isEmpty() && !(part.sums && owns == null))
			{
				Gear.Stat named = owns;
				List<Gear.Stat> carried = new ArrayList<>();
				String sharedWith = null;
				if (named != null && (held == null || held.itemId != named.itemId))
				{
					if (carriedAlready.add(named.itemId))
					{
						carried.add(named);
						carriedFor.put(named.itemId, part.label);
					}
					else
					{
						sharedWith = carriedFor.get(named.itemId);
					}
					// a weapon that shoots goes with ammunition it fires, when what the set wears does not
					// ammunition the part names comes before any other
					boolean namedAmmo = false;
					for (Gear.Stat piece : pieces(part, named, main, usable, limits))
					{
						namedAmmo |= piece.slot == 13;
					}
					// a weapon carried for another part goes with that part's ammunition
					Gear.Stat ammo = namedAmmo || sharedWith != null ? null : gear.ammoFor(named, usable, main.items.get(13), limits);
					if (ammo == null && !namedAmmo && sharedWith == null && limits != null && limits.ammoWord != null && gear.needsAmmo(named, main.items.get(13)))
					{
						// the task names the ammunition and none owned of it fits: the part cannot be done its way
						if (carried.contains(named))
						{
							carriedAlready.remove(named.itemId);
						}
						leftOut.add(part.label);
						continue;
					}
					if (ammo != null && carriedAlready.add(ammo.itemId))
					{
						carried.add(ammo);
						carriedFor.put(ammo.itemId, part.label);
					}
				}
				if (named != null && (held == null || held.itemId != named.itemId))
				{
					// with the weapon worn, the set worn is what it is used in: nothing to change into
					for (Gear.Stat piece : pieces(part, named, main, usable, limits))
					{
						if (taken(carried, piece.slot))
						{
							// one a slot: the ammunition the weapon took already, say
							continue;
						}
						if (carried.size() >= Math.max(mostCarried, part.carry))
						{
							break;
						}
						if (carriedAlready.add(piece.itemId))
						{
							carried.add(piece);
							carriedFor.put(piece.itemId, part.label);
						}
					}
				}
				if (named == null && (weaponByName(part.byName, usable, null) != null
					|| (limits != null && (limits.oneHanded || limits.shield || limits.meleeType != null || limits.spellsOnly
					|| limits.spellElement != null))))
				{
					// what the player owns for it is what the task's rule does not allow: the part is left out, and said so
					leftOut.add(part.label);
					continue;
				}
				Role role = new Role(part.label, named == null ? "" : styleOf(styleOfWeapon(named)), 0,
					carried, null, part.note, named == null,
					sharedWith == null ? null : named.name, sharedWith);
				// the best of what the task allows, as the table writes it
				String firstAllowed = allowed(part, limits).get(0);
				role.best = named == null ? part.byNameShown.get(part.byName.indexOf(firstAllowed)) : null;
				roles.add(keyed(role, part));
				continue;
			}
			Gear.Limits lim = "other".equals(part.style) ? otherThan(limits, main.style) : within(limits, part.style);
			Gear.Result best = lim == NOTHING_FITS ? null : gear.bestAt(part.npcId, owned, levels, onTask, lim);
			if (best == null)
			{
				roles.add(keyed(new Role(part.label, "", 0, new ArrayList<Gear.Stat>(), null, part.note, true), part));
				continue;
			}
			best = sameWeaponIfTied(gear, part, best, going.values(), owned, levels, onTask, lim);
			Gear.Stat bestWeapon = best.items.get(3);
			if (bestWeapon != null)
			{
				going.putIfAbsent(bestWeapon.itemId, bestWeapon);
			}
			// what differs from the set worn, most telling first, and no more than fits two rows
			List<Gear.Stat> carried = new ArrayList<>();
			Map<Integer, Gear.Stat> worn = new LinkedHashMap<>(main.items);
			boolean weaponOnly = "weapon".equals(part.kind);
			for (int slot : ORDER)
			{
				Gear.Stat piece = best.items.get(slot);
				Gear.Stat on = main.items.get(slot);
				if (piece == null || (on != null && on.itemId == piece.itemId))
				{
					continue;
				}
				if (weaponOnly && slot != 3 && slot != 13 && slot != 5)
				{
					continue;
				}
				if (slot == 13 && !Weapons.isShotAmmo(piece.name))
				{
					// a blessing is not worth a slot of the inventory
					continue;
				}
				if (carried.size() >= Math.max(mostCarried, part.carry))
				{
					break;
				}
				carried.add(piece);
				worn.put(slot, piece);
			}
			Gear.Stat weapon = worn.get(3);
			if (weapon != null && weapon.twoHanded && best.items.get(5) == null)
			{
				worn.remove(5);
			}
			if (best.items.get(13) == null && main.items.get(13) != null && best.items.get(3) != main.items.get(3))
			{
				// the ammunition of the other weapon stays where it is: it does nothing for this one
				worn.put(13, main.items.get(13));
			}
			double dps = part.noFigure ? 0 : gear.dpsAt(part.npcId, worn, levels, onTask);
			List<Gear.Stat> fresh = new ArrayList<>();
			for (Gear.Stat s : carried)
			{
				// a piece two parts share is carried once
				if (carriedAlready.add(s.itemId))
				{
					fresh.add(s);
					carriedFor.put(s.itemId, part.label);
				}
			}
			Gear.Stat sharedWeapon = fresh.isEmpty() && !carried.isEmpty() ? worn.get(3) : null;
			String sharedWith = sharedWeapon == null ? null : carriedFor.get(sharedWeapon.itemId);
			Role role = keyed(new Role(part.label, best.style, dps, fresh, best.spell, part.note, false,
				sharedWith == null ? null : sharedWeapon.name, sharedWith), part);
			role.counted = Collections.unmodifiableList(new ArrayList<>(carried));
			roles.add(role);
		}
		// advice for the fight as a rule (the wiki's way, a part the task may leave out) is not said under a task's own rule
		Plan plan = new Plan(main, first.label, first.advice && limits != null ? "" : first.note, roles, leftOut);
		plan.mainNpcId = first.npcId;
		return plan;
	}

	/** The part's key and the monster it is summed against, on the part as the card gets it. */
	private static Role keyed(Role role, Part part)
	{
		role.key = keyOf(part);
		role.npcId = part.npcId;
		return role;
	}

	/**
	 * What tells a part of a fight from the others of the boss, whatever the player owns: its label, the style the table
	 * gives it and the style of set it is for (Zulrah has three parts called "Special attack", one for each).
	 */
	private static String keyOf(Part part)
	{
		return part.label + "|" + (part.style == null ? "" : part.style) + "|" + (part.when == null ? "" : part.when);
	}

	/** The keys of every part of the boss but the one worn for, as the table has them; empty for a boss it does not have. */
	public Set<String> partKeys(String boss)
	{
		Set<String> out = new java.util.LinkedHashSet<>();
		List<Part> parts = byBoss.get(key(boss));
		for (int i = 1; parts != null && i < parts.size(); i++)
		{
			out.add(keyOf(parts.get(i)));
		}
		return out;
	}

	/**
	 * The weapons of a part by name that the task's rule allows: none when the task forbids the part's style, and only
	 * the ones it names when it names its weapons.
	 */
	private static List<String> allowed(Part part, Gear.Limits limits)
	{
		if (limits == null)
		{
			return part.byName;
		}
		if (part.style != null && !limits.styles.isEmpty() && !limits.styles.contains("spell".equals(part.style) ? "magic" : part.style))
		{
			return Collections.emptyList();
		}
		if ((limits.spellsOnly || limits.spellElement != null) && part.style != null && !"magic".equals(part.style) && !"spell".equals(part.style))
		{
			// a task of spells only takes no weapon of another style
			return Collections.emptyList();
		}
		List<String> out = new ArrayList<>();
		for (String name : part.byName)
		{
			boolean named = limits.weapon.isEmpty() || limits.weapon.stream().anyMatch(w -> Weapons.has(name, w));
			if (named && limits.exclude.stream().noneMatch(name::contains))
			{
				out.add(name);
			}
		}
		return out;
	}

	/**
	 * The pieces of a part by name to change into with its weapon: of each group, the first one owned that the task
	 * allows, when it does more than what the set wears in that slot. More is the attack bonus of the weapon's own kind
	 * (crush for a maul), or the strength bonus for a part that wants damage.
	 */
	private static List<Gear.Stat> pieces(Part part, Gear.Stat weapon, Gear.Result main, Collection<Gear.Stat> owned, Gear.Limits limits)
	{
		List<Gear.Stat> out = new ArrayList<>();
		for (List<String> group : part.pieces)
		{
			// of the group, the owned piece that gives the most: the table need not rank pieces it has no figures for
			Gear.Stat piece = null;
			for (String name : group)
			{
				Gear.Stat one = byName(java.util.Collections.singletonList(name), owned, limits);
				if (one != null && (piece == null || bonus(one, weapon, part.style, part.strength) > bonus(piece, weapon, part.style, part.strength)))
				{
					piece = one;
				}
			}
			if (piece == null || piece.slot == 3 || (weapon.twoHanded && piece.slot == 5) || taken(out, piece.slot))
			{
				continue;
			}
			Gear.Stat on = main.items.get(piece.slot);
			if (on != null && limits != null && mustWear(on, limits))
			{
				// what the fight makes the player wear (a dragonfire shield, boots of stone) is not changed out of
				continue;
			}
			if (on != null && on.itemId == piece.itemId)
			{
				continue;
			}
			// the ammunition the weapon shoots, and a piece kept for what it does, come whatever their bonus
			if (part.keep || piece.slot == 13 || bonus(piece, weapon, part.style, part.strength) > (on == null ? 0 : bonus(on, weapon, part.style, part.strength)))
			{
				out.add(piece);
			}
		}
		return out;
	}

	/** "melee", "ranged" or "magic": what a weapon is for, by its bonuses (a shield carried by name counts as melee). */
	private static String styleOfWeapon(Gear.Stat s)
	{
		return s.ranged > Math.max(s.magic, Math.max(s.stab, Math.max(s.slash, s.crush))) ? "ranged"
			: s.magic > Math.max(s.stab, Math.max(s.slash, s.crush)) ? "magic" : "melee";
	}

	private static boolean taken(List<Gear.Stat> pieces, int slot)
	{
		for (Gear.Stat p : pieces)
		{
			if (p.slot == slot)
			{
				return true;
			}
		}
		return false;
	}

	private static boolean mustWear(Gear.Stat on, Gear.Limits limits)
	{
		String n = on.name == null ? "" : on.name.toLowerCase(Locale.ROOT);
		for (List<String> group : limits.mustWear)
		{
			for (String part : group)
			{
				if (n.contains(part))
				{
					return true;
				}
			}
		}
		return false;
	}

	/**
	 * The first of the names owned that the task's rule allows to be held: of a style it allows, one-handed when it asks
	 * for a shield or one hand, of the kind of hit it names. A piece that is not a weapon (a shield) is only matched by name.
	 */
	private static Gear.Stat weaponByName(List<String> names, Collection<Gear.Stat> owned, Gear.Limits limits)
	{
		if (limits == null)
		{
			return byName(names, owned, null);
		}
		List<Gear.Stat> fit = new ArrayList<>();
		for (Gear.Stat s : owned)
		{
			if (s.slot != 3)
			{
				fit.add(s);
				continue;
			}
			String style = styleOfWeapon(s);
			if ((!limits.styles.isEmpty() && !limits.styles.contains(style))
				|| ((limits.spellsOnly || limits.spellElement != null) && !"magic".equals(style)))
			{
				continue;
			}
			if ((limits.oneHanded || limits.shield) && s.twoHanded)
			{
				continue;
			}
			if (limits.meleeType != null && "melee".equals(style))
			{
				int best = Math.max(s.stab, Math.max(s.slash, s.crush));
				String type = best == s.crush ? "crush" : best == s.slash ? "slash" : "stab";
				if (!limits.meleeType.equalsIgnoreCase(type))
				{
					continue;
				}
			}
			fit.add(s);
		}
		return byName(names, fit, limits);
	}

	/** As {@link #bonus(Gear.Stat, Gear.Stat, String, boolean)}, the style told by the weapon's own bonuses. */
	static double bonus(Gear.Stat piece, Gear.Stat weapon, boolean strength)
	{
		return bonus(piece, weapon, null, strength);
	}

	/**
	 * What a piece gives the weapon: its attack bonus of the weapon's own kind, or its strength bonus of that style.
	 * The style is the part's when it has one (a staff with more crush than magic is cast with); else the weapon's best.
	 */
	static double bonus(Gear.Stat piece, Gear.Stat weapon, String style, boolean strength)
	{
		boolean ranged = style != null ? "ranged".equals(style)
			: weapon.ranged > Math.max(weapon.magic, Math.max(weapon.stab, Math.max(weapon.slash, weapon.crush)));
		boolean magic = style != null ? "magic".equals(style) || "spell".equals(style)
			: !ranged && weapon.magic > Math.max(weapon.stab, Math.max(weapon.slash, weapon.crush));
		if (strength)
		{
			return ranged ? piece.rangedStrength : magic ? piece.magicDamage : piece.strength;
		}
		if (ranged)
		{
			return piece.ranged;
		}
		if (magic)
		{
			return piece.magic;
		}
		// the kind of hit the weapon is best at
		int best = Math.max(weapon.stab, Math.max(weapon.slash, weapon.crush));
		return best == weapon.crush ? piece.crush : best == weapon.slash ? piece.slash : piece.stab;
	}

	/**
	 * The set for a part with a weapon that is going anyway (worn, or carried for another part) when it does at
	 * most {@link #TIE} less than the part's best: a tie goes to fewer changes and fewer slots. The best one
	 * otherwise.
	 */
	private static Gear.Result sameWeaponIfTied(Gear gear, Part part, Gear.Result best, Collection<Gear.Stat> going,
		Collection<Gear.Stat> owned, Gear.Levels levels, boolean onTask, Gear.Limits lim)
	{
		Gear.Stat weapon = best.items.get(3);
		if (weapon == null || best.dps <= 0)
		{
			return best;
		}
		for (Gear.Stat other : going)
		{
			if (other.itemId == weapon.itemId)
			{
				return best;
			}
		}
		Gear.Result chosen = best;
		for (Gear.Stat other : going)
		{
			Gear.Limits only = within(lim, null);
			only = only == null ? new Gear.Limits() : only;
			String name = other.name == null ? "" : other.name.toLowerCase(Locale.ROOT);
			if (name.isEmpty() || (!only.weapon.isEmpty() && only.weapon.stream().noneMatch(w -> Weapons.has(name, w))))
			{
				// the task names its weapons, and this is not one of them
				continue;
			}
			only.weapon.clear();
			only.weapon.add(name);
			Gear.Result with = gear.bestAt(part.npcId, owned, levels, onTask, only);
			Gear.Stat w = with == null ? null : with.items.get(3);
			if (w != null && w.itemId == other.itemId && with.dps >= best.dps * (1 - TIE)
				&& (chosen == best || with.dps > chosen.dps))
			{
				chosen = with;
			}
		}
		return chosen;
	}

	/** The first weapon of the list that is owned and the task allows, matched by the start of its name. */
	private static Gear.Stat byName(List<String> names, Collection<Gear.Stat> owned, Gear.Limits limits)
	{
		for (String wanted : names)
		{
			for (Gear.Stat s : owned)
			{
				String n = s.name == null ? "" : s.name.toLowerCase(Locale.ROOT);
				if (n.startsWith(wanted) && !n.endsWith("(empty)") && !n.endsWith("(uncharged)")
					&& (limits == null || limits.exclude.stream().noneMatch(n::contains)))
				{
					return s;
				}
			}
		}
		return null;
	}

	private static String styleOf(String style)
	{
		if (style == null || style.isEmpty())
		{
			return "";
		}
		String s = "spell".equals(style) ? "magic" : style;
		return Character.toUpperCase(s.charAt(0)) + s.substring(1);
	}

	/** A task's limits with the style of the set worn left out: for a part that the first style no longer harms. */
	private static Gear.Limits otherThan(Gear.Limits task, String mainStyle)
	{
		Gear.Limits l = within(task, null);
		l = l == null ? new Gear.Limits() : l;
		Set<String> left = new HashSet<>(l.styles.isEmpty() ? java.util.Arrays.asList("melee", "ranged", "magic") : l.styles);
		left.remove(mainStyle == null ? "" : mainStyle.toLowerCase(Locale.ROOT));
		if (left.isEmpty())
		{
			return NOTHING_FITS;
		}
		l.styles.clear();
		l.styles.addAll(left);
		return l;
	}

	/** Limits no weapon passes: a part whose style the task forbids. */
	private static final Gear.Limits NOTHING_FITS = new Gear.Limits();

	/** A task's limits with a part's own style added; {@link #NOTHING_FITS} when the task forbids that style. */
	private static Gear.Limits within(Gear.Limits task, String style)
	{
		if (task == null && style == null)
		{
			return null;
		}
		Gear.Limits l = new Gear.Limits();
		if (task != null)
		{
			l.styles.addAll(task.styles);
			l.weapon.addAll(task.weapon);
			l.exclude.addAll(task.exclude);
			l.oneHanded = task.oneHanded;
			l.shield = task.shield;
			l.meleeType = task.meleeType;
			l.ammoWord = task.ammoWord;
			l.mustWear.addAll(task.mustWear);
			l.spellElement = task.spellElement;
			l.spellsOnly = task.spellsOnly;
		}
		if (style != null)
		{
			// "spell" is magic that must be cast: a staff with an attack of its own does not do
			String wanted = "spell".equals(style) ? "magic" : style;
			if (!l.styles.isEmpty() && !l.styles.contains(wanted))
			{
				return NOTHING_FITS;
			}
			l.styles.clear();
			l.styles.add(wanted);
			l.spellsOnly = "spell".equals(style);
		}
		return l;
	}
}
