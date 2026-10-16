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
	/** The most pieces one part carries: two rows of the inventory, as {@link #carriedSlots} lays a part out. */
	public static final int MOST_PIECES = 8;
	/** The card as the planner recommends it, when the player changed this one; null for a card as recommended. */
	public GearView recommended;
	/** What the player changed and what to tell them about it, a line each; empty for a card as recommended. */
	public List<Change> changes = Collections.emptyList();
	/** The equipment slots holding an item the player put there and does not own. */
	public java.util.Set<Integer> notOwned = Collections.emptySet();
	/**
	 * When the player changed the inventory: its supplies slot by slot after the pieces to change into, a null for an
	 * empty slot; null when the supplies are as recommended ({@link #bag}).
	 */
	public List<Bag.Line> cells;
	/** For each of {@link #cells}, its place among the supplies the changes are made to; null when they are the same. */
	public List<Integer> cellIndex;
	/** The spell the set is counted with when the player changed the set; null otherwise. */
	public String editedSpell;

	/** One line about the player's changes, with what can be done about it. */
	public static final class Change
	{
		public enum Kind
		{
			/** A slot the player changed, as it is shown. */
			CHANGED,
			/** The item they put there is not owned. */
			NOT_OWNED,
			/** The task's rule does not allow it here: the recommendation is shown in its place. */
			RULE,
			/** The recommendation of that slot is another one now. */
			MOVED,
			/** What the change took off (a shield under a two-handed weapon). */
			TAKEN_OFF,
			/** Something the fight needs is no longer there. */
			NEEDED,
			/** A change that has no place on the card any more. */
			ORPHAN,
			/** The inventory had to be cut at 28 slots. */
			CUT
		}

		public final Kind kind;
		/** The change it is about: its place and slot ({@link GearChoices}); empty for none. */
		public final String place;
		public final int slot;
		/** What to say, in English. */
		public final String line;

		public Change(Kind kind, String place, int slot, String line)
		{
			this.kind = kind;
			this.place = place == null ? "" : place;
			this.slot = slot;
			this.line = line;
		}

		@Override
		public String toString()
		{
			return kind + ": " + line;
		}
	}

	/** Whether the player changed this card. */
	public boolean edited()
	{
		return recommended != null;
	}

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
		/** When nothing owned does a part carried by name: the best weapon for it, to get; null otherwise. */
		public String best;
		/** The weapon carried for another part that does this one too, and that part's label; null otherwise. */
		public final String sharedWeapon;
		public final String sharedWith;
		/** What tells this part from the others of the boss, for the player's changes; empty when not known. */
		public String key = "";
		/** The monster of the table it is summed against; 0 for none. */
		public int npcId;

		public Part(String label, String style, double dps, List<Bag.Line> carried, String spell, String note, boolean nothing)
		{
			this(label, style, dps, carried, spell, note, nothing, null, null);
		}

		public Part(String label, String style, double dps, List<Bag.Line> carried, String spell, String note, boolean nothing,
			String sharedWeapon, String sharedWith)
		{
			this.sharedWeapon = sharedWeapon;
			this.sharedWith = sharedWith;
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
	 * The pieces to change into as they sit in the inventory, before the supplies. A part of more than four goes in two
	 * rows of its own, down a column at a time; parts of four or fewer share a row when the whole part fits in what is
	 * left of it (a special attack weapon beside another), else start the next. A null is an empty slot.
	 */
	public List<Bag.Line> carriedSlots()
	{
		List<Bag.Line> out = new ArrayList<>();
		// where the next piece goes in the row of small parts being filled; 0 = a row of its own
		int column = 0;
		for (Part part : parts)
		{
			int n = part.carried.size();
			if (n == 0)
			{
				continue;
			}
			if (n > 4 || column + n > 4)
			{
				// what is left of the row stays empty: a part is never cut across two rows
				for (; column > 0 && column < 4; column++)
				{
					out.add(null);
				}
				column = 0;
			}
			if (n > 4)
			{
				Bag.Line[] block = new Bag.Line[8];
				for (int i = 0; i < n && i < 8; i++)
				{
					block[(i % 2) * 4 + i / 2] = part.carried.get(i);
				}
				out.addAll(java.util.Arrays.asList(block));
				continue;
			}
			out.addAll(part.carried);
			column = (column + n) % 4;
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
		if (cells != null)
		{
			// the supplies as the player left them, slot by slot
			for (Bag.Line line : cells)
			{
				if (out.size() < Bag.SLOTS)
				{
					out.add(line == null || line.itemId <= 0 ? new int[]{0, 0} : new int[]{line.itemId, line.stack ? line.quantity : 1});
				}
			}
			while (!out.isEmpty() && out.get(out.size() - 1)[0] <= 0)
			{
				out.remove(out.size() - 1);
			}
			return out;
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

	/** The supplies slot by slot as the panel lays them out after the pieces to change into: a null for an empty slot. */
	public List<Bag.Line> supplyCells()
	{
		if (cells != null)
		{
			return cells;
		}
		List<Bag.Line> out = new ArrayList<>();
		for (Bag.Line line : bag)
		{
			for (int i = 0; i < line.slots() && out.size() < Bag.SLOTS; i++)
			{
				out.add(line);
			}
		}
		return out;
	}

	/** The pieces to change into as {@link #carriedSlots} lays them out, for parts given as their pieces. */
	public static int carriedCells(List<List<Bag.Line>> pieces)
	{
		List<Part> parts = new ArrayList<>();
		for (List<Bag.Line> p : pieces)
		{
			parts.add(new Part("", "", 0, p, null, "", false));
		}
		return of(Status.READY).withParts("", parts, Collections.<Bag.Line>emptyList()).carriedSlots().size();
	}

	/**
	 * Whether one more piece fits in the inventory for a part (its key): the pieces laid out again with it, and the
	 * supplies after them, in 28 slots. A piece in place of one of that slot always fits.
	 */
	public boolean roomForPiece(String partKey, int equipmentSlot)
	{
		List<List<Bag.Line>> pieces = new ArrayList<>();
		for (Part part : parts)
		{
			List<Bag.Line> carried = new ArrayList<>(part.carried);
			if (part.key.equals(partKey))
			{
				if (slotsOf(part).contains(equipmentSlot))
				{
					return true;
				}
				if (part.carried.size() >= GearEdit.MOST_PIECES)
				{
					return false;
				}
				carried.add(Bag.Line.worn(1, "", true, ""));
			}
			pieces.add(carried);
		}
		// a slot the player emptied among the supplies closes up to make room, as in the game
		return carriedCells(pieces) + GearEdit.filled(supplyCells()) <= Bag.SLOTS;
	}

	/**
	 * The place among the supplies (as {@link GearChoices#BAG} changes count them) of a supply slot as the panel shows it:
	 * the n-th slot after the pieces to change into, the empty ones after the last included.
	 */
	public int supplyIndex(int shown)
	{
		if (cellIndex == null)
		{
			return shown;
		}
		if (shown < cellIndex.size())
		{
			return cellIndex.get(shown);
		}
		int last = -1;
		for (int i : cellIndex)
		{
			last = Math.max(last, i);
		}
		return last + 1 + (shown - cellIndex.size());
	}

	/** The equipment slots of a part's pieces, as {@link #pieceSlots} were given; empty when not known. */
	private java.util.Set<Integer> slotsOf(Part part)
	{
		java.util.Set<Integer> out = new java.util.HashSet<>();
		List<Integer> slots = pieceSlots.get(part.key);
		if (slots != null)
		{
			out.addAll(slots);
		}
		return out;
	}

	/** The equipment slot of each piece carried for a part, by the part's key, in the order of its pieces. */
	public Map<String, List<Integer>> pieceSlots = Collections.emptyMap();

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

	/** The same set and parts with other items: for a card the player changed. Its other fields are left to be set. */
	public GearView changed(Map<Integer, Integer> ids, Map<Integer, String> names, String newStyle, double newDps, List<Part> newParts,
		List<String> newNotes)
	{
		return new GearView(status, ids, names, newStyle, newDps, bossHitpoints, weapons, bag, worn, newNotes, newParts, wornFor);
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
