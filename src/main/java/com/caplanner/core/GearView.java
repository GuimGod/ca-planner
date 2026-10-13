/*
 * Copyright (c) 2026, GuimGod
 * All rights reserved. Licensed under the BSD 2-Clause License (see LICENSE).
 */
package com.caplanner.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** What the panel shows about the gear for a boss: the set that was picked, or why there is none. */
public final class GearView
{
	public enum Status
	{
		/** The bank was never opened with the plugin on, so the player's items are not known. */
		NO_BANK,
		/** The sums have no such boss (a raid, a fight of several bosses). */
		NO_BOSS,
		/** Nothing the player owns can hit it, as far as the sums know. */
		NOTHING,
		/** The player owns the weapon the task asks for, and the sums have no figure for it. */
		NO_FIGURE,
		WORKING,
		READY
	}

	public final Status status;
	/** Item id of each equipment slot index; empty unless {@link Status#READY}. */
	public final Map<Integer, Integer> itemIdBySlot;
	public final Map<Integer, String> nameBySlot;
	/** "Melee", "Ranged" or "Magic". */
	public final String style;
	public final double dps;
	/** The boss's hitpoints, or 0 when not known. */
	public final int bossHitpoints;
	/** The weapons owned that the task asks for; empty unless {@link Status#NO_FIGURE}. */
	public final List<String> weapons;
	/** The inventory to expect with the set; empty when there is none to show. */
	public final List<Bag.Line> bag;
	/** What the fight makes the player wear: in the set when owned, to be got when not. */
	public final List<Bag.Line> worn;
	/** Lines about the fight to show before anything else. */
	public final List<String> notes;
	/** The other parts of a fight of several, with the pieces to carry for each; empty for a fight of one. */
	public final List<Part> parts;
	/** What the set worn is for, in a fight of several parts; empty otherwise. */
	public final String wornFor;
	/** No inventory is suggested for this boss yet: the panel says it is still to come. */
	public boolean inventoryToCome;
	/** The inventory shown is a provisional one, from the wiki's amounts; null when it is not. */
	public Bag.Provisional provisional;

	/** One part of a fight other than the one the set is worn for. */
	public static final class Part
	{
		public final String label;
		/** "Melee", "Ranged" or "Magic"; empty when nothing owned does it. */
		public final String style;
		public final double dps;
		/** The pieces to carry and change into, most telling first. */
		public final List<Bag.Line> carried;
		/** The spell it is counted with, or null. */
		public final String spell;
		public final String note;
		/** Nothing the player owns harms this part. */
		public final boolean nothing;

		public Part(String label, String style, double dps, List<Bag.Line> carried, String spell, String note, boolean nothing)
		{
			this.label = label;
			this.style = style;
			this.dps = dps;
			this.carried = Collections.unmodifiableList(new ArrayList<>(carried));
			this.spell = spell;
			this.note = note;
			this.nothing = nothing;
		}
	}

	/**
	 * The pieces to change into as they sit in the inventory, before the supplies: each part in rows
	 * of its own, more than four going down two rows a column at a time. A null is an empty slot.
	 */
	public List<Bag.Line> carriedSlots()
	{
		List<Bag.Line> out = new ArrayList<>();
		for (Part part : parts)
		{
			int n = part.carried.size();
			if (n == 0)
			{
				continue;
			}
			Bag.Line[] block = new Bag.Line[n > 4 ? 8 : 4];
			for (int i = 0; i < n && i < 8; i++)
			{
				block[n > 4 ? (i % 2) * 4 + i / 2 : i] = part.carried.get(i);
			}
			out.addAll(java.util.Arrays.asList(block));
		}
		while (!out.isEmpty() && out.get(out.size() - 1) == null)
		{
			out.remove(out.size() - 1);
		}
		return out;
	}

	/** The whole inventory slot by slot, as item id and quantity: the pieces to change into, then the supplies. */
	public List<int[]> inventory()
	{
		List<int[]> out = new ArrayList<>();
		for (Bag.Line line : carriedSlots())
		{
			out.add(line == null ? new int[]{0, 0} : new int[]{line.itemId, 1});
		}
		for (int[] slot : Bag.slots(bag))
		{
			if (out.size() < Bag.SLOTS)
			{
				out.add(slot);
			}
		}
		return out;
	}

	private GearView(Status status, Map<Integer, Integer> ids, Map<Integer, String> names, String style, double dps, int hp)
	{
		this(status, ids, names, style, dps, hp, Collections.emptyList(), Collections.emptyList());
	}

	private GearView(Status status, Map<Integer, Integer> ids, Map<Integer, String> names, String style, double dps, int hp,
		List<String> weapons, List<Bag.Line> bag)
	{
		this(status, ids, names, style, dps, hp, weapons, bag, Collections.emptyList(), Collections.emptyList());
	}

	private GearView(Status status, Map<Integer, Integer> ids, Map<Integer, String> names, String style, double dps, int hp,
		List<String> weapons, List<Bag.Line> bag, List<Bag.Line> worn, List<String> notes)
	{
		this(status, ids, names, style, dps, hp, weapons, bag, worn, notes, Collections.emptyList(), "");
	}

	private GearView(Status status, Map<Integer, Integer> ids, Map<Integer, String> names, String style, double dps, int hp,
		List<String> weapons, List<Bag.Line> bag, List<Bag.Line> worn, List<String> notes, List<Part> parts, String wornFor)
	{
		this.parts = Collections.unmodifiableList(new ArrayList<>(parts));
		this.wornFor = wornFor == null ? "" : wornFor;
		this.worn = Collections.unmodifiableList(new ArrayList<>(worn));
		this.notes = Collections.unmodifiableList(new ArrayList<>(notes));
		this.weapons = Collections.unmodifiableList(new ArrayList<>(weapons));
		this.bag = Collections.unmodifiableList(new ArrayList<>(bag));
		this.status = status;
		this.itemIdBySlot = Collections.unmodifiableMap(new LinkedHashMap<>(ids));
		this.nameBySlot = Collections.unmodifiableMap(new LinkedHashMap<>(names));
		this.style = style;
		this.dps = dps;
		this.bossHitpoints = hp;
	}

	public static GearView of(Status status)
	{
		return new GearView(status, Collections.emptyMap(), Collections.emptyMap(), "", 0, 0);
	}

	/** The same, with what the fight makes the player wear and what to tell them about it. */
	public GearView with(List<Bag.Line> worn, List<String> notes)
	{
		return new GearView(status, itemIdBySlot, nameBySlot, style, dps, bossHitpoints, weapons, bag, worn, notes, parts, wornFor);
	}

	/** The same, as a fight of several parts: what the set worn is for, and what to carry for the others. */
	public GearView withParts(String wornForPart, List<Part> others, List<Bag.Line> inventory)
	{
		return new GearView(status, itemIdBySlot, nameBySlot, style, dps, bossHitpoints, weapons, inventory, worn, notes, others, wornForPart);
	}

	/** The same, with the inventory to expect: for a boss that gets no set. */
	public GearView withBag(List<Bag.Line> inventory)
	{
		return new GearView(status, itemIdBySlot, nameBySlot, style, dps, bossHitpoints, weapons, inventory, worn, notes, parts, wornFor);
	}

	public static GearView noFigure(List<String> weapons)
	{
		return new GearView(Status.NO_FIGURE, Collections.emptyMap(), Collections.emptyMap(), "", 0, 0, weapons, Collections.emptyList());
	}

	public static GearView ready(Map<Integer, Integer> ids, Map<Integer, String> names, String style, double dps, int hp,
		List<Bag.Line> bag)
	{
		return new GearView(Status.READY, ids, names, style, dps, hp, Collections.emptyList(), bag);
	}

	public static GearView ready(Map<Integer, Integer> ids, Map<Integer, String> names, String style, double dps, int hp)
	{
		return new GearView(Status.READY, ids, names, style, dps, hp);
	}
}
