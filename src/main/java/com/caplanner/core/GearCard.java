/*
 * Copyright (c) 2026, GuimGod
 * All rights reserved. Licensed under the BSD 2-Clause License (see LICENSE).
 */
package com.caplanner.core;

import com.caplanner.gear.Fights;
import com.caplanner.gear.Gear;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The gear card of a boss from what the player owns: the set, the parts of the fight and what to carry
 * for them, what the fight makes the player wear, the inventory and the lines to tell. Nothing here asks
 * the game: the plugin reads what is owned and hands it in, and the same card can be worked out outside
 * the game.
 */
public final class GearCard
{
	private GearCard()
	{
	}

	/** What the card is worked out from. */
	public static final class Input
	{
		public Gear gear;
		/** The fights of several parts; may be null. */
		public Fights fights;
		/** The inventories; may be null. */
		public Bag bag;
		/** The rules of the tasks; may be null. */
		public Rules rules;
		public String boss;
		/** The task the card is for, or -1. */
		public int taskId = -1;
		/** What the task asks of the set, or null. */
		public Gear.Limits taskLimits;
		public List<Gear.Stat> owned = new ArrayList<>();
		public Gear.Levels levels;
		public boolean onTask;
		/** Everything owned, wearable or not, by its name in lower case. */
		public Map<String, Bag.Have> have = new HashMap<>();
		/** What the player has done that makes a line of the tables needless. */
		public Set<String> done = new HashSet<>();
		/** A task against the clock: more pieces carried to change into. */
		public boolean againstTheClock;
		/** What the player changed in the gear of this boss, in the order they did; empty for the recommendation. */
		public List<GearChoices.Pin> choices = new ArrayList<>();
		/** What the game says of each item the changes put in, by its id. */
		public Map<Integer, Item> items = new HashMap<>();
		/** The card's key as the panel has it ("Vorkath", "Vorkath#273"): the card the player's changes are compared on. */
		public String cardKey = "";
	}

	/** An item the player put in a slot, as the game gives it. */
	public static final class Item
	{
		public final int itemId;
		public final String name;
		/** Many of it sit in one inventory slot (runes, darts). */
		public final boolean stackable;
		/** Its slot and bonuses when it can be worn; null otherwise. */
		public final Gear.Stat stat;

		public Item(int itemId, String name, boolean stackable, Gear.Stat stat)
		{
			this.itemId = itemId;
			this.name = name == null ? "" : name;
			this.stackable = stackable;
			this.stat = stat;
		}
	}

	/**
	 * Whether the player owns an item, counting its other dresses (an ornament kit, a trim, imbued, a cosmetic name before
	 * it) as the item and a placeholder as owned: for what the player puts in a slot.
	 *
	 * @param have everything owned, by its name in lower case
	 */
	public static boolean owns(int itemId, String name, Map<String, Bag.Have> have, Set<Integer> ownedIds)
	{
		return GearEdit.owns(itemId, name, have, ownedIds);
	}

	/** A copy of a task's rule, so that what the fight adds to the search is not taken for the task's. */
	private static Gear.Limits copyOf(Gear.Limits rule)
	{
		if (rule == null)
		{
			return null;
		}
		Gear.Limits l = new Gear.Limits();
		l.styles.addAll(rule.styles);
		l.weapon.addAll(rule.weapon);
		l.exclude.addAll(rule.exclude);
		l.oneHanded = rule.oneHanded;
		l.shield = rule.shield;
		l.meleeType = rule.meleeType;
		l.ammoWord = rule.ammoWord;
		l.spellElement = rule.spellElement;
		l.spellsOnly = rule.spellsOnly;
		return l;
	}

	/** What a task's rule asks of the set, or null when it asks nothing of it. */
	public static Gear.Limits limitsOf(Rules rules, int taskId)
	{
		Rules.Rule rule = taskId < 0 || rules == null ? null : rules.of(taskId);
		if (rule == null || !rule.shapesTheSet())
		{
			return null;
		}
		Gear.Limits l = new Gear.Limits();
		l.styles.addAll(rule.styles);
		l.weapon.addAll(rule.weapon);
		l.exclude.addAll(rule.exclude);
		l.oneHanded = rule.oneHanded;
		l.shield = rule.shield;
		l.meleeType = rule.meleeType;
		l.ammoWord = rule.ammoWord;
		l.spellElement = rule.spellElement;
		return l;
	}

	/** Whether a card can be worked out for the boss at all: the sums know it, or the fight is in parts. */
	public static boolean knows(Gear gear, Fights fights, String boss)
	{
		return gear != null && (gear.knows(boss) || (fights != null && fights.has(boss)));
	}

	/** The card; never null. */
	public static GearView build(Input in)
	{
		final Gear g = in.gear;
		final String boss = in.boss;
		final Map<String, Bag.Have> have = in.have;
		final Set<String> done = in.done;
		final Bag table = in.bag;
		// what the fight itself asks of the gear comes on top of what the task asks
		List<Bag.Wear> wear = table == null ? new ArrayList<Bag.Wear>() : table.wear(boss, done);
		List<String> notes = table == null ? new ArrayList<String>() : table.notes(boss);
		List<String> wayThere = table == null ? new ArrayList<String>() : table.wayThereNotOwned(boss, have, done);
		if (!wayThere.isEmpty())
		{
			notes.add("Getting there: the OSRS Wiki names " + String.join(", then ", wayThere)
				+ ". You own none of these; the route button finds a way with what you have.");
		}
		if (table != null && table.noWayOut(boss, have))
		{
			notes.add("No way out in this inventory: you own no teleport to your house or to a bank. A Teleport to house tablet or a Ring of dueling is enough.");
		}
		List<String> wield = table == null ? new ArrayList<String>() : table.wield(boss);
		// the task's own rule, before what the fight asks is added to the search: what the player's changes keep to
		final Gear.Limits taskRule = copyOf(in.taskLimits);
		Gear.Limits limits = in.taskLimits;
		if (!wear.isEmpty() || !wield.isEmpty())
		{
			limits = limits == null ? new Gear.Limits() : limits;
			for (Bag.Wear w : wear)
			{
				limits.mustWear.add(w.names);
			}
			if (limits.weapon.isEmpty())
			{
				limits.weapon.addAll(wield);
			}
		}
		final Fights fights = in.fights;
		// a fight of several parts: the set is for the first, and pieces are carried for the others
		Fights.Plan plan = fights != null && fights.has(boss)
			? fights.plan(g, boss, in.owned, in.levels, in.onTask, limits, in.againstTheClock ? Fights.MOST_CARRIED_FOR_SPEED : 4) : null;
		Gear.Result r = plan != null ? plan.main : fights != null && fights.has(boss) ? null : g.best(boss, in.owned, in.levels, in.onTask, limits);
		if (r == null)
		{
			// the weapon the task names may be owned and merely have no figure here
			List<String> named = g.weaponsWithoutFigure(in.owned, limits);
			return (named.isEmpty() ? GearView.of(GearView.Status.NOTHING) : GearView.noFigure(named))
				.with(new ArrayList<Bag.Line>(), notes);
		}
		Map<Integer, Integer> ids = new HashMap<>();
		Map<Integer, String> names = new HashMap<>();
		for (Map.Entry<Integer, Gear.Stat> e : r.items.entrySet())
		{
			ids.put(e.getKey(), e.getValue().itemId);
			names.put(e.getKey(), e.getValue().name);
		}
		Bag b = table;
		List<GearView.Part> parts = new ArrayList<>();
		Set<Integer> held = new HashSet<>(ids.values());
		StringBuilder styles = new StringBuilder(r.style);
		if (plan != null)
		{
			for (Fights.Role role : plan.roles)
			{
				List<Bag.Line> carried = new ArrayList<>();
				for (Gear.Stat s : role.carried)
				{
					carried.add(Bag.Line.worn(s.itemId, s.name, true, ""));
					held.add(s.itemId);
				}
				GearView.Part part = new GearView.Part(role.label, role.style, role.dps, carried, role.spell, role.note, role.nothing,
					role.sharedWeapon, role.sharedWith);
				part.best = role.best;
				part.key = role.key;
				part.npcId = role.npcId;
				parts.add(part);
				// the potion of a part's style only when it is fought in that style (a set carried), not for one weapon by name
				// and its ammunition
				int pieces = 0;
				for (Gear.Stat s : role.carried)
				{
					pieces += s.slot == 13 ? 0 : 1;
				}
				if (!role.style.isEmpty() && pieces > 1)
				{
					styles.append(',').append(role.style);
				}
			}
		}
		GearView shape = GearView.of(GearView.Status.READY).withParts("", parts, new ArrayList<Bag.Line>());
		int left = Bag.SLOTS - shape.carriedSlots().size();
		// an inventory only for the fights that were gone through one by one; for the rest it is still to come
		boolean mapped = b != null && b.isMapped(boss);
		// where the fight was not gone through, the amounts of the wiki's inventory, said to be provisional
		// thralls are of the Arceuus spellbook: a set that casts from another has no use for the book
		Set<String> useless = new HashSet<>();
		if (r.spell != null)
		{
			useless.add("book of the dead");
		}
		// and what the task itself forbids is not carried
		Rules taskRules = in.rules;
		Rules.Rule ofThisTask = in.taskId < 0 || taskRules == null ? null : taskRules.of(in.taskId);
		boolean forbids = ofThisTask != null && !ofThisTask.without.isEmpty();
		if (forbids)
		{
			useless.addAll(ofThisTask.without);
		}
		Bag.Provisional provisional = b != null && !mapped
			? b.provisional(boss, r.style, have, done, held, left, useless) : null;
		if (provisional != null)
		{
			Set<String> on = new HashSet<>();
			for (Gear.Stat s : r.items.values())
			{
				if (s.name != null)
				{
					on.add(s.name.toLowerCase());
				}
			}
			provisional = provisional.without(on);
		}
		List<Bag.Line> inventory = mapped ? b.of(boss, styles.toString(), have, done, held, left, useless)
			: provisional != null ? provisional.lines : new ArrayList<Bag.Line>();
		List<Bag.Line> worn = new ArrayList<>();
		for (Bag.Wear w : wear)
		{
			Gear.Stat on = null;
			for (Gear.Stat s : r.items.values())
			{
				for (String part : w.names)
				{
					if (on == null && s.name != null && s.name.toLowerCase().contains(part))
					{
						on = s;
					}
				}
			}
			worn.add(on != null ? Bag.Line.worn(on.itemId, on.name, true, w.why) : Bag.Line.worn(w.itemId, w.name, false, w.why));
		}
		List<String> told = new ArrayList<>(notes);
		if (forbids)
		{
			told.add("This task forbids some of the usual supplies: they are left out of the inventory below.");
		}
		if (r.spell != null)
		{
			told.add("Counted casting " + r.spell + ": bring the runes for it.");
		}
		if (plan != null)
		{
			for (String part : plan.leftOut)
			{
				// one line each, so that each says itself in another language
				told.add("This task's rule leaves out: " + part + ".");
			}
		}
		GearView view = GearView.ready(ids, names, r.style, r.dps, r.bossHitpoints, inventory).with(worn, told);
		if (plan != null)
		{
			if (!plan.mainNote.isEmpty())
			{
				told.add(0, plan.mainNote);
				view = view.with(worn, told);
			}
			// a set worn for the boss itself needs no "worn for" line
			view = view.withParts(plan.mainLabel.equalsIgnoreCase(boss) ? "" : plan.mainLabel, parts, inventory);
		}
		view.inventoryToCome = !mapped && provisional == null;
		view.provisional = provisional;
		Map<String, List<Integer>> pieceSlots = new HashMap<>();
		if (plan != null)
		{
			for (Fights.Role role : plan.roles)
			{
				List<Integer> slots = new ArrayList<>();
				for (Gear.Stat s : role.carried)
				{
					slots.add(s.slot);
				}
				pieceSlots.put(role.key, slots);
			}
		}
		view.pieceSlots = pieceSlots;
		if (in.choices == null || in.choices.isEmpty())
		{
			return view;
		}
		GearEdit.Context edit = new GearEdit.Context();
		edit.in = in;
		edit.main = r;
		edit.plan = plan;
		edit.rule = taskRule;
		edit.wear = wear;
		edit.wield = wield;
		if (forbids)
		{
			edit.forbidden.addAll(ofThisTask.without);
		}
		return GearEdit.apply(view, edit);
	}
}
