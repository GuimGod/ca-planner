/*
 * Copyright (c) 2026, GuimGod
 * All rights reserved. Licensed under the BSD 2-Clause License (see LICENSE).
 */
package com.caplanner.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * What the player changed in the gear the planner recommends, boss by boss: the item they put in a slot (or none),
 * and the item that was recommended there when they did. Everything they did not change stays the recommendation as it
 * is worked out each time. Kept as one short text in the plugin's settings of the account; a text that cannot be read,
 * or a line of it, is left out rather than trusted. An object of this class is never changed: each change makes another.
 */
public final class GearChoices
{
	/** The key of the account's settings the changes are kept under. */
	public static final String KEY = "gearChoices";
	/** What the set wears. */
	public static final String WORN = "worn";
	/** The supplies of the inventory, by their place after the pieces to change into. */
	public static final String BAG = "bag";
	/** The pieces to change into for a part of the fight: this, then the part's key. */
	public static final String PART = "part:";
	/** The plugin writes no longer text than this: a change that would pass it is refused, and said. */
	public static final int MOST_TEXT = 64 * 1024;
	/** A text longer than this was not written by the plugin: nothing of it is read. */
	private static final int MOST_READ = 4 * MOST_TEXT;
	/** Of one boss, no more changes than this are kept. */
	public static final int MOST_PER_BOSS = 120;
	/** What a slot holds when the player took its item out. */
	public static final int NOTHING = 0;
	/** The recommendation of a slot when it was not known. */
	public static final int UNKNOWN = -1;
	/** The equipment slots there are: head, cape, amulet, weapon, body, shield, legs, gloves, boots, ring, ammunition. */
	public static final Set<Integer> EQUIPMENT = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(0, 1, 2, 3, 4, 5, 7, 9, 10, 12, 13)));
	private static final int SLOTS = 28;
	private static final int MOST_ITEM_ID = 1_000_000;
	private static final String VERSION = "v1";

	/** One slot the player changed. */
	public static final class Pin
	{
		/** {@link #WORN}, {@link #BAG}, or {@link #PART} and a part's key. */
		public final String place;
		/** The equipment slot index for what is worn or carried for a part; the place among the supplies for the inventory. */
		public final int slot;
		/** The item put there, or {@link #NOTHING} for taken out. */
		public final int itemId;
		/** How many, for an item that stacks; 1 otherwise. */
		public final int quantity;
		/** What was recommended there when the player made the change: an item id, {@link #NOTHING} or {@link #UNKNOWN}. */
		public final int recommended;
		/** The order changes were made in: of two that cannot both stand (a two-handed weapon and a shield), the later one does. */
		public final long order;
		/**
		 * The card the change was made on ("Vorkath", or "Vorkath#273" for a task with a set of its own): what was
		 * recommended there is only compared on that card; empty for the boss's own card.
		 */
		public final String card;

		public Pin(String place, int slot, int itemId, int quantity, int recommended, long order)
		{
			this(place, slot, itemId, quantity, recommended, order, "");
		}

		public Pin(String place, int slot, int itemId, int quantity, int recommended, long order, String card)
		{
			this.place = place;
			this.slot = slot;
			this.itemId = itemId;
			this.quantity = Math.max(1, quantity);
			this.recommended = recommended;
			this.order = order;
			this.card = card == null ? "" : card;
		}

		/** Whether the change was made on this card of the boss (its key as the panel has it). */
		public boolean madeOn(String boss, String cardKey)
		{
			String here = cardKey == null || cardKey.isEmpty() ? boss : cardKey;
			String there = card.isEmpty() ? boss : card;
			return here.equals(there);
		}

		/** Whether this is a change to a part of the fight. */
		public boolean isPart()
		{
			return place.startsWith(PART);
		}

		/** The part's key, for a change to a part; empty otherwise. */
		public String partKey()
		{
			return isPart() ? place.substring(PART.length()) : "";
		}

		@Override
		public String toString()
		{
			return place + " " + slot + " -> " + itemId + (quantity > 1 ? " x" + quantity : "") + " (was " + recommended + ")";
		}
	}

	private static final GearChoices NONE = new GearChoices(new LinkedHashMap<String, Map<String, Pin>>(), 1);

	/** Boss, then place and slot, then the change; in the order they were read or made. */
	private final Map<String, Map<String, Pin>> byBoss;
	/** The order the next change gets. */
	private final long next;

	private GearChoices(Map<String, Map<String, Pin>> byBoss, long next)
	{
		this.byBoss = byBoss;
		this.next = next;
	}

	/** No change at all. */
	public static GearChoices none()
	{
		return NONE;
	}

	/** The place of what is carried for one part of the fight. */
	public static String part(String partKey)
	{
		return PART + (partKey == null ? "" : partKey);
	}

	public boolean isEmpty()
	{
		return byBoss.isEmpty();
	}

	/** The bosses with changes, in the order they were made. */
	public List<String> bosses()
	{
		return new ArrayList<>(byBoss.keySet());
	}

	/** The changes of a boss, in the order they were made; empty when there are none. */
	public List<Pin> of(String boss)
	{
		Map<String, Pin> pins = boss == null ? null : byBoss.get(boss);
		if (pins == null)
		{
			return Collections.emptyList();
		}
		List<Pin> out = new ArrayList<>(pins.values());
		out.sort((a, b) -> Long.compare(a.order, b.order));
		return out;
	}

	/** The change of one slot, or null when it was not changed. */
	public Pin at(String boss, String place, int slot)
	{
		Map<String, Pin> pins = boss == null ? null : byBoss.get(boss);
		return pins == null ? null : pins.get(where(place, slot));
	}

	/**
	 * The same with one slot changed: the item put there (or {@link #NOTHING}), how many, and what was recommended there.
	 * A change that cannot be kept (a slot there is not, a boss with too many changes) leaves things as they were.
	 */
	public GearChoices with(String boss, String place, int slot, int itemId, int quantity, int recommended)
	{
		return with(boss, place, slot, itemId, quantity, recommended, "");
	}

	/** As {@link #with(String, String, int, int, int, int)}, made on a card of the boss: its key as the panel has it. */
	public GearChoices with(String boss, String place, int slot, int itemId, int quantity, int recommended, String card)
	{
		Pin pin = new Pin(place, slot, itemId, quantity, recommended, next, card == null || card.equals(boss) ? "" : card);
		if (boss == null || boss.isEmpty() || !valid(pin))
		{
			return this;
		}
		Map<String, Map<String, Pin>> all = copy();
		Map<String, Pin> pins = all.computeIfAbsent(boss, b -> new LinkedHashMap<>());
		if (!pins.containsKey(where(place, slot)) && pins.size() >= MOST_PER_BOSS)
		{
			return this;
		}
		// a change made again goes after the others: it is the latest
		pins.remove(where(place, slot));
		pins.put(where(place, slot), pin);
		GearChoices out = new GearChoices(all, next + 1);
		// never more than can be read back: the change is refused, and the panel says so
		return out.encode().length() > MOST_TEXT ? this : out;
	}

	/** The same without the change of one slot: it is the recommendation again. */
	public GearChoices without(String boss, String place, int slot)
	{
		if (at(boss, place, slot) == null)
		{
			return this;
		}
		Map<String, Map<String, Pin>> all = copy();
		Map<String, Pin> pins = all.get(boss);
		pins.remove(where(place, slot));
		if (pins.isEmpty())
		{
			all.remove(boss);
		}
		return new GearChoices(all, next);
	}

	/** The same without any change of the boss: all of it is the recommendation again. */
	public GearChoices withoutBoss(String boss)
	{
		if (boss == null || !byBoss.containsKey(boss))
		{
			return this;
		}
		Map<String, Map<String, Pin>> all = copy();
		all.remove(boss);
		return new GearChoices(all, next);
	}

	/** The text kept in the settings; empty for no change at all. */
	public String encode()
	{
		if (byBoss.isEmpty())
		{
			return "";
		}
		StringBuilder out = new StringBuilder(VERSION);
		for (Map.Entry<String, Map<String, Pin>> boss : byBoss.entrySet())
		{
			for (Pin p : of(boss.getKey()))
			{
				out.append('\n').append(escape(boss.getKey())).append('\t').append(escape(p.place)).append('\t').append(p.slot)
					.append('\t').append(p.itemId).append('\t').append(p.quantity).append('\t').append(p.recommended)
					.append('\t').append(p.order).append(p.card.isEmpty() ? "" : "\t" + escape(p.card));
			}
		}
		return out.toString();
	}

	/**
	 * Reads what {@link #encode} wrote. Nothing of a text that is not of this version, or far too long, is read; a line
	 * that is not right is left out and the others are kept. Never throws.
	 */
	public static GearChoices decode(String text)
	{
		if (text == null || text.isEmpty() || text.length() > MOST_READ)
		{
			return NONE;
		}
		String[] lines = text.split("\n", -1);
		if (!VERSION.equals(lines[0].trim()))
		{
			return NONE;
		}
		Map<String, Map<String, Pin>> all = new LinkedHashMap<>();
		long next = 1;
		for (int i = 1; i < lines.length; i++)
		{
			String[] c = lines[i].split("\t", -1);
			if (c.length != 7 && c.length != 8)
			{
				continue;
			}
			try
			{
				String boss = unescape(c[0]);
				Pin pin = new Pin(unescape(c[1]), Integer.parseInt(c[2]), Integer.parseInt(c[3]), Integer.parseInt(c[4]),
					Integer.parseInt(c[5]), Long.parseLong(c[6]), c.length == 8 ? unescape(c[7]) : "");
				if (boss.isEmpty() || !valid(pin) || Integer.parseInt(c[4]) < 1 || pin.order < 0)
				{
					continue;
				}
				Map<String, Pin> pins = all.computeIfAbsent(boss, b -> new LinkedHashMap<>());
				if (pins.size() >= MOST_PER_BOSS && !pins.containsKey(where(pin.place, pin.slot)))
				{
					continue;
				}
				pins.put(where(pin.place, pin.slot), pin);
			}
			catch (NumberFormatException e)
			{
				// a line that is not right is left out
			}
		}
		// numbered again 1, 2, 3... in the order they were made, whatever numbers the text had
		List<Pin> every = new ArrayList<>();
		for (Map<String, Pin> pins : all.values())
		{
			every.addAll(pins.values());
		}
		every.sort((a, b) -> Long.compare(a.order, b.order));
		Map<Pin, Long> renumbered = new java.util.IdentityHashMap<>();
		long n = 1;
		for (Pin p : every)
		{
			renumbered.put(p, n++);
		}
		for (Map<String, Pin> pins : all.values())
		{
			for (Map.Entry<String, Pin> e : pins.entrySet())
			{
				Pin p = e.getValue();
				e.setValue(new Pin(p.place, p.slot, p.itemId, p.quantity, p.recommended, renumbered.get(p), p.card));
			}
		}
		return new GearChoices(all, n);
	}

	/** Whether a change is one the card can hold. */
	private static boolean valid(Pin p)
	{
		if (p.place == null || p.itemId < 0 || p.itemId >= MOST_ITEM_ID || p.recommended < UNKNOWN || p.recommended >= MOST_ITEM_ID)
		{
			return false;
		}
		if (WORN.equals(p.place))
		{
			return EQUIPMENT.contains(p.slot);
		}
		if (BAG.equals(p.place))
		{
			return p.slot >= 0 && p.slot < SLOTS;
		}
		return p.isPart() && !p.partKey().isEmpty() && EQUIPMENT.contains(p.slot);
	}

	private Map<String, Map<String, Pin>> copy()
	{
		Map<String, Map<String, Pin>> all = new LinkedHashMap<>();
		for (Map.Entry<String, Map<String, Pin>> e : byBoss.entrySet())
		{
			all.put(e.getKey(), new LinkedHashMap<>(e.getValue()));
		}
		return all;
	}

	private static String where(String place, int slot)
	{
		return place + "\t" + slot;
	}

	private static String escape(String s)
	{
		return s.replace("\\", "\\\\").replace("\t", "\\t").replace("\n", "\\n").replace("\r", "\\r");
	}

	private static String unescape(String s)
	{
		StringBuilder out = new StringBuilder();
		for (int i = 0; i < s.length(); i++)
		{
			char ch = s.charAt(i);
			if (ch == '\\' && i + 1 < s.length())
			{
				char n = s.charAt(++i);
				out.append(n == 't' ? '\t' : n == 'n' ? '\n' : n == 'r' ? '\r' : n);
			}
			else
			{
				out.append(ch);
			}
		}
		return out.toString();
	}
}
