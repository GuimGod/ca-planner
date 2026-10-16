/*
 * Copyright (c) 2026, GuimGod
 * All rights reserved. Licensed under the BSD 2-Clause License (see LICENSE).
 */
package com.caplanner.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The search of items for a slot the player changes: every item of the game, read once from the client, found by a part
 * of its name; the ones the player owns first. Nothing here asks the game.
 */
public final class ItemSearch
{
	/** Anything that can be worn, in whichever slot it goes. */
	public static final int ANY_WORN = -2;
	/** Any item at all. */
	public static final int ANYTHING = -1;
	/** Items found at most. */
	public static final int MOST = 50;

	/** One item of the game. */
	public static final class Entry
	{
		public final int itemId;
		public final String name;
		final String lower;
		/** The equipment slot it is worn in, or -1. */
		public final int slot;
		public final boolean stackable;

		public Entry(int itemId, String name, int slot, boolean stackable)
		{
			this.itemId = itemId;
			this.name = name;
			this.lower = name.toLowerCase(Locale.ROOT);
			this.slot = slot;
			this.stackable = stackable;
		}
	}

	/** One item found, and whether the player owns it. */
	public static final class Hit
	{
		public final Entry entry;
		/** The item to put in: the player's own one of that name when they have it. */
		public final int itemId;
		public final boolean owned;

		Hit(Entry entry, int itemId, boolean owned)
		{
			this.entry = entry;
			this.itemId = itemId;
			this.owned = owned;
		}
	}

	private final List<Entry> entries;

	/** Of the items of one name and slot, only the first is kept: the game has many copies of some. */
	public ItemSearch(List<Entry> all)
	{
		Set<String> seen = new HashSet<>();
		List<Entry> one = new ArrayList<>();
		for (Entry e : all)
		{
			if (seen.add(e.lower + "\t" + e.slot))
			{
				one.add(e);
			}
		}
		entries = Collections.unmodifiableList(one);
	}

	public int size()
	{
		return entries.size();
	}

	/**
	 * The items whose name has what was typed, that go in the slot, those owned first, then those whose name starts with
	 * it, then the shorter names; at most {@link #MOST}. Nothing for fewer than two letters.
	 *
	 * @param slot an equipment slot, {@link #ANY_WORN} or {@link #ANYTHING}
	 * @param have everything the player owns, by its name in lower case
	 * @param slotOf the equipment slot of an item id the player owns, or -1
	 */
	public List<Hit> find(String typed, int slot, Map<String, Bag.Have> have, java.util.function.IntUnaryOperator slotOf)
	{
		String q = typed == null ? "" : typed.trim().toLowerCase(Locale.ROOT);
		List<Hit> out = new ArrayList<>();
		if (q.length() < 2)
		{
			return out;
		}
		for (Entry e : entries)
		{
			boolean fits = slot == ANYTHING || (slot == ANY_WORN ? e.slot >= 0 : e.slot == slot);
			if (!fits || !e.lower.contains(q))
			{
				continue;
			}
			// the player's own one, under that name or another dress of it, when it goes in the same slot: the setup takes
			// the very item they have
			Bag.Have mine = GearEdit.ownedAs(e.name, have);
			int id = mine != null && (e.slot < 0 || slotOf.applyAsInt(mine.itemId) == e.slot) ? mine.itemId : e.itemId;
			out.add(new Hit(e, id, mine != null));
		}
		out.sort(Comparator.comparing((Hit h) -> !h.owned)
			.thenComparing(h -> !h.entry.lower.startsWith(q))
			.thenComparingInt(h -> h.entry.name.length())
			.thenComparing(h -> h.entry.name));
		return new ArrayList<>(out.subList(0, Math.min(MOST, out.size())));
	}
}
