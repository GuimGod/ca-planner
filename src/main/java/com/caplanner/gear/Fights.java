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

		Role(String label, String style, double dps, List<Gear.Stat> carried, String spell, String note, boolean nothing)
		{
			this.label = label;
			this.style = style;
			this.dps = dps;
			this.carried = Collections.unmodifiableList(carried);
			this.spell = spell;
			this.note = note;
			this.nothing = nothing;
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

		Plan(Gear.Result main, String mainLabel, String mainNote, List<Role> roles)
		{
			this.main = main;
			this.mainLabel = mainLabel;
			this.mainNote = mainNote;
			this.roles = Collections.unmodifiableList(roles);
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
			p.noFigure = c.length > 6 && "nofigure".equalsIgnoreCase(c[6].trim());
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

	/** The npc ids the table names, for checking it against the monster table. */
	List<Integer> npcIds()
	{
		List<Integer> out = new ArrayList<>();
		for (List<Part> parts : byBoss.values())
		{
			for (Part p : parts)
			{
				out.add(p.npcId);
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
		Gear.Result main = gear.bestAt(first.npcId, owned, levels, onTask, within(limits, first.style));
		if (main == null)
		{
			return null;
		}
		Set<Integer> carriedAlready = new HashSet<>();
		List<Role> roles = new ArrayList<>();
		for (Part part : parts.subList(1, parts.size()))
		{
			Gear.Limits lim = "other".equals(part.style) ? otherThan(limits, main.style) : within(limits, part.style);
			Gear.Result best = lim == NOTHING_FITS ? null : gear.bestAt(part.npcId, owned, levels, onTask, lim);
			if (best == null)
			{
				roles.add(new Role(part.label, "", 0, new ArrayList<Gear.Stat>(), null, part.note, true));
				continue;
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
				if (carried.size() >= mostCarried)
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
				}
			}
			roles.add(new Role(part.label, best.style, dps, fresh, best.spell, part.note, false));
		}
		return new Plan(main, first.label, first.note, roles);
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
