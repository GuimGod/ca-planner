/*
 * Copyright (c) 2026, GuimGod
 * All rights reserved. Licensed under the BSD 2-Clause License (see LICENSE).
 */
package com.caplanner.core;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * The inventory to expect for a boss: what the fight itself needs (a key, a tool, a potion one
 * cannot do without), then the usual supplies of the combat style. Supplies are only ever ones the
 * player owns; what the fight needs is listed whether owned or not, and marked when it is not.
 * It knows nothing of how the fight goes, so the amounts are a place to start from.
 */
public final class Bag
{
	public static final int SLOTS = 28;
	private static final String EVERY_BOSS = "*";
	private static final String[] DOSES = {"(4)", "(3)", "(2)", "(1)"};

	/** Something the player owns: its id, how many, and its name as the game writes it. */
	public static final class Have
	{
		public final int itemId;
		public final int quantity;
		public final String name;

		public Have(int itemId, int quantity, String name)
		{
			this.itemId = itemId;
			this.quantity = quantity;
			this.name = name;
		}
	}

	/** One item of the inventory. */
	public static final class Line
	{
		public final int itemId;
		public final String name;
		public final int quantity;
		/** The whole quantity sits in one slot (tablets, runes). */
		public final boolean stack;
		/** The fight cannot be done without it. */
		public final boolean needed;
		public final boolean owned;
		/** What it is for, in a few words; may be empty. */
		public final String why;

		Line(int itemId, String name, int quantity, boolean stack, boolean needed, boolean owned, String why)
		{
			this.itemId = itemId;
			this.name = name;
			this.quantity = quantity;
			this.stack = stack;
			this.needed = needed;
			this.owned = owned;
			this.why = why;
		}

		/** Something the fight makes the player wear, as a line for the panel. */
		public static Line worn(int itemId, String name, boolean owned, String why)
		{
			return new Line(itemId, name, 1, false, true, owned, why);
		}

		/** How many inventory slots it takes. */
		public int slots()
		{
			return stack ? 1 : quantity;
		}
	}

	private static final class Row
	{
		String kind;
		int quantity;
		boolean stack;
		/** Names in lower case, best first; one that starts with "~" matches any name that has the rest in it. */
		final List<String> names = new ArrayList<>();
		/** The item id given for a name, for showing one the player does not own. */
		final Map<String, Integer> ids = new HashMap<>();
		String why = "";
		/** Something the player may have done that makes the item needless (a diary); null for none. */
		String unless;
		/** The name to show, as it was written; null to make one from the first name. */
		String label;
	}

	/** What the wiki's inventory for a boss carries: how many of each kind, its special items, its pieces to change into. */
	private static final class Wiki
	{
		final Map<String, Integer> counts = new java.util.LinkedHashMap<>();
		final List<String> special = new ArrayList<>();
		final List<String> gear = new ArrayList<>();
	}

	/**
	 * The inventory of a boss this plugin has not gone through: the amounts of the wiki's inventory,
	 * filled with what the player owns. A place to start from, and said to be so.
	 */
	public static final class Provisional
	{
		public final List<Line> lines;
		/** Special items of the wiki's inventory that the player does not own. */
		public final List<String> lacking;
		/** Pieces of gear the wiki's inventory carries to change into: the fight may ask for switches. */
		public final List<String> switches;

		Provisional(List<Line> lines, List<String> lacking, List<String> switches)
		{
			this.lines = Collections.unmodifiableList(lines);
			this.lacking = Collections.unmodifiableList(lacking);
			this.switches = Collections.unmodifiableList(switches);
		}

		/** The same, leaving out of the pieces to change into those the set already wears (names in lower case). */
		public Provisional without(Set<String> wornNames)
		{
			List<String> left = new ArrayList<>();
			for (String piece : switches)
			{
				String plain = piece.toLowerCase(Locale.ROOT);
				boolean on = false;
				for (String worn : wornNames)
				{
					// worn in another dress (an ornament kit) is worn all the same
					on |= worn.equals(plain) || worn.startsWith(plain + " (") || worn.endsWith(" " + plain);
				}
				if (!on)
				{
					left.add(piece);
				}
			}
			return new Provisional(new ArrayList<>(lines), new ArrayList<>(lacking), left);
		}
	}

	/** What each kind of supply of the wiki's inventories is, best first. */
	private final Map<String, List<String>> kinds = new HashMap<>();
	private final Map<String, Wiki> wiki = new HashMap<>();

	private final Map<String, List<Row>> rows = new HashMap<>();
	private final Set<String> onlyItsOwn = new HashSet<>();
	/** Bosses whose fight was gone through, so that an inventory can be suggested for them. */
	private final Set<String> mapped = new HashSet<>();
	/** The usual supplies that do nothing against a boss, by their first name in the table. */
	private final Map<String, Set<String>> without = new HashMap<>();

	/**
	 * Reads "boss, kind, quantity, names, what for" lines, tab separated; "#" starts a comment.
	 * The boss is a name, "*" for every boss, or "*melee", "*ranged", "*magic" for a combat style.
	 * Kinds: need (listed even when not owned), take (only when owned), food (fills what is left),
	 * only (the boss takes none of the usual supplies), mapped (an inventory is suggested for the boss), wear (something that must be worn), wield
	 * (the only weapons that harm it), note (a line to tell the player). A quantity ending in "s" is one stack.
	 * Names are alternatives joined by "|", best first, each optionally "name=item id". A last
	 * column may name something done ("desert-hard") that makes the line needless.
	 */
	public static Bag read(Reader table) throws IOException
	{
		Bag out = new Bag();
		BufferedReader in = new BufferedReader(table);
		String line;
		while ((line = in.readLine()) != null)
		{
			if (line.trim().isEmpty() || line.startsWith("#"))
			{
				continue;
			}
			String[] c = line.split("\t");
			if (c.length < 2)
			{
				continue;
			}
			String boss = c[0].trim().startsWith(EVERY_BOSS) ? c[0].trim().toLowerCase(Locale.ROOT) : Requirements.key(c[0]);
			Row row = new Row();
			row.kind = c[1].trim().toLowerCase(Locale.ROOT);
			if ("only".equals(row.kind))
			{
				out.onlyItsOwn.add(boss);
				continue;
			}
			if ("mapped".equals(row.kind))
			{
				out.mapped.add(boss);
				continue;
			}
			if ("without".equals(row.kind) && c.length > 3)
			{
				// one of the usual supplies that does nothing in this fight (thralls on a boss they cannot hit)
				for (String name : c[3].split("[|]"))
				{
					out.without.computeIfAbsent(boss, k -> new HashSet<>()).add(name.trim().toLowerCase(Locale.ROOT));
				}
				continue;
			}
			if (c.length < 4)
			{
				continue;
			}
			String amount = c[2].trim().toLowerCase(Locale.ROOT);
			row.stack = amount.endsWith("s");
			try
			{
				row.quantity = Integer.parseInt(row.stack ? amount.substring(0, amount.length() - 1) : amount);
			}
			catch (NumberFormatException e)
			{
				continue;
			}
			for (String alternative : c[3].split("\\|"))
			{
				String a = alternative.trim().toLowerCase(Locale.ROOT);
				int eq = a.lastIndexOf('=');
				String name = eq < 0 ? a : a.substring(0, eq).trim();
				if (name.isEmpty())
				{
					continue;
				}
				row.names.add(name);
				if (eq >= 0)
				{
					try
					{
						row.ids.put(name, Integer.parseInt(a.substring(eq + 1).trim()));
					}
					catch (NumberFormatException e)
					{
						// shown without a picture
					}
				}
			}
			row.why = c.length > 4 ? c[4].trim() : "";
			row.unless = c.length > 5 && !c[5].trim().isEmpty() ? c[5].trim().toLowerCase(Locale.ROOT) : null;
			if (!row.names.isEmpty())
			{
				out.rows.computeIfAbsent(boss, k -> new ArrayList<>()).add(row);
			}
		}
		return out;
	}

	/**
	 * Reads the table of provisional inventories: "=kind, names" lines saying what each kind of
	 * supply is, then "boss, kinds (kind N; ...), special items, pieces of gear" lines, tab separated.
	 */
	public void readWiki(Reader table) throws IOException
	{
		BufferedReader in = new BufferedReader(table);
		String line;
		while ((line = in.readLine()) != null)
		{
			if (line.trim().isEmpty() || line.startsWith("#"))
			{
				continue;
			}
			String[] c = line.split("\t", -1);
			if (c.length < 2)
			{
				continue;
			}
			if (c[0].startsWith("="))
			{
				List<String> names = new ArrayList<>();
				for (String name : c[1].split("\\|"))
				{
					if (!name.trim().isEmpty())
					{
						names.add(name.trim().toLowerCase(Locale.ROOT));
					}
				}
				kinds.put(c[0].substring(1).trim().toLowerCase(Locale.ROOT), names);
				continue;
			}
			Wiki w = new Wiki();
			for (String one : c[1].split(";"))
			{
				String[] kind = one.trim().split(" ");
				try
				{
					if (kind.length == 2)
					{
						w.counts.put(kind[0].toLowerCase(Locale.ROOT), Integer.parseInt(kind[1]));
					}
				}
				catch (NumberFormatException e)
				{
					// a kind with no amount is left out
				}
			}
			for (int column = 2; column <= 3 && column < c.length; column++)
			{
				for (String one : c[column].split(";"))
				{
					if (!one.trim().isEmpty())
					{
						(column == 2 ? w.special : w.gear).add(one.trim());
					}
				}
			}
			if (!w.counts.isEmpty())
			{
				wiki.put(Requirements.key(c[0]), w);
			}
		}
	}

	/** Whether a provisional inventory can be given: the boss was not gone through, and the wiki has an inventory for it. */
	public boolean isProvisional(String boss)
	{
		String key = Requirements.key(boss);
		return !mapped.contains(key) && wiki.containsKey(key);
	}

	/**
	 * The provisional inventory of a boss: what its fight needs (as for any boss), the special items
	 * of the wiki's inventory that the player owns, then as many of each kind of supply as the wiki
	 * carries, of the best the player owns. Null when there is none to give.
	 *
	 * @param style "melee", "ranged" or "magic": the style of the set that was picked
	 */
	public Provisional provisional(String boss, String style, Map<String, Have> have, Set<String> done, Set<Integer> inSet, int slots)
	{
		return provisional(boss, style, have, done, inSet, slots, Collections.<String>emptySet());
	}

	/** @param useless supplies that do nothing with this set or that the task forbids, by a name of theirs in lower case */
	public Provisional provisional(String boss, String style, Map<String, Have> have, Set<String> done, Set<Integer> inSet, int slots,
		Set<String> useless)
	{
		String key = Requirements.key(boss);
		Wiki w = wiki.get(key);
		if (w == null || mapped.contains(key))
		{
			return null;
		}
		Set<String> skip = new HashSet<>(useless);
		skip.addAll(without.getOrDefault(key, Collections.<String>emptySet()));
		List<Row> ordered = new ArrayList<>();
		for (String kind : new String[]{"need", "take"})
		{
			for (Row row : rows.getOrDefault(key, Collections.emptyList()))
			{
				if (kind.equals(row.kind) && (row.unless == null || !done.contains(row.unless)) && Collections.disjoint(skip, row.names))
				{
					ordered.add(row);
				}
			}
		}
		for (String special : w.special)
		{
			Row row = new Row();
			row.kind = "special";
			row.quantity = 1;
			row.label = special;
			int x = special.lastIndexOf(" x");
			if (x > 0 && special.substring(x + 2).matches("\\d+"))
			{
				row.stack = true;
				row.quantity = Integer.parseInt(special.substring(x + 2));
				row.label = special.substring(0, x).trim();
			}
			// "Label: one|~another" names the items that will do, best first, under one name to show
			String names = row.label;
			int colon = names.indexOf(": ");
			if (colon > 0)
			{
				row.label = names.substring(0, colon).trim();
				names = names.substring(colon + 2);
			}
			for (String name : names.split("[|]"))
			{
				if (!name.trim().isEmpty())
				{
					row.names.add(name.trim().toLowerCase(Locale.ROOT));
				}
			}
			ordered.add(row);
		}
		List<Row> food = new ArrayList<>();
		for (Map.Entry<String, Integer> e : w.counts.entrySet())
		{
			if ("boost".equals(e.getKey()))
			{
				// the potion of the style of the set
				for (Row usual : rows.getOrDefault(EVERY_BOSS + (style == null ? "" : style.trim().toLowerCase(Locale.ROOT)),
					Collections.emptyList()))
				{
					if ("take".equals(usual.kind) && Collections.disjoint(skip, usual.names))
					{
						Row row = new Row();
						row.kind = "take";
						row.quantity = e.getValue();
						row.names.addAll(usual.names);
						ordered.add(row);
					}
				}
				continue;
			}
			List<String> names = kinds.get(e.getKey());
			if (names == null || !Collections.disjoint(skip, names))
			{
				continue;
			}
			Row row = new Row();
			row.kind = "take";
			row.quantity = e.getValue();
			row.names.addAll(names);
			("food".equals(e.getKey()) ? food : ordered).add(row);
		}
		ordered.addAll(food);
		List<String> lacking = new ArrayList<>();
		List<Line> lines = fill(ordered, have, inSet, slots, lacking);
		return new Provisional(lines, lacking, new ArrayList<>(w.gear));
	}

	/** Something a fight makes the player wear: one of several items, by parts of their names in lower case. */
	public static final class Wear
	{
		public final List<String> names;
		/** The item to show when none is owned. */
		public final int itemId;
		public final String name;
		public final String why;

		Wear(List<String> names, int itemId, String name, String why)
		{
			this.names = names;
			this.itemId = itemId;
			this.name = name;
			this.why = why;
		}
	}

	/** What the fight makes the player wear ("wear" lines), leaving out what something done makes needless. */
	public List<Wear> wear(String boss, Set<String> done)
	{
		List<Wear> out = new ArrayList<>();
		for (Row row : rows.getOrDefault(Requirements.key(boss), Collections.emptyList()))
		{
			if ("wear".equals(row.kind) && (row.unless == null || !done.contains(row.unless)))
			{
				out.add(new Wear(Collections.unmodifiableList(row.names), row.ids.getOrDefault(row.names.get(0), 0),
					display(row.names.get(0)), row.why));
			}
		}
		return out;
	}

	/** The weapons a boss can only be harmed with ("wield" lines), by parts of their names; empty for any. */
	public List<String> wield(String boss)
	{
		List<String> out = new ArrayList<>();
		for (Row row : rows.getOrDefault(Requirements.key(boss), Collections.emptyList()))
		{
			if ("wield".equals(row.kind))
			{
				out.addAll(row.names);
			}
		}
		return out;
	}

	/** What to tell the player about a fight before anything else ("note" lines). */
	public List<String> notes(String boss)
	{
		List<String> out = new ArrayList<>();
		for (Row row : rows.getOrDefault(Requirements.key(boss), Collections.emptyList()))
		{
			if ("note".equals(row.kind) && !row.why.isEmpty())
			{
				out.add(row.why);
			}
		}
		return out;
	}

	/**
	 * What the fight cannot be done without and the player does not own, one short line each: an
	 * item to bring, something to wear, a weapon that can harm it. Empty when nothing is missing.
	 *
	 * @param have everything the player owns, by its name in lower case
	 * @param done what the player has done that makes a line needless
	 */
	public List<String> missing(String boss, Map<String, Have> have, Set<String> done)
	{
		List<String> out = new ArrayList<>();
		Map<String, Have> sorted = new TreeMap<>(have);
		for (Row row : rows.getOrDefault(Requirements.key(boss), Collections.emptyList()))
		{
			if (row.unless != null && done.contains(row.unless))
			{
				continue;
			}
			boolean need = "need".equals(row.kind);
			boolean wear = "wear".equals(row.kind);
			boolean wield = "wield".equals(row.kind);
			if (!need && !wear && !wield)
			{
				continue;
			}
			boolean owned = false;
			for (String name : row.names)
			{
				// what is worn or wielded is named in part ("slayer helmet" for any of them)
				owned |= !matches(need || name.startsWith("~") ? name : "~" + name, sorted).isEmpty();
			}
			if (!owned)
			{
				String what = display(row.names.get(0));
				out.add(wield ? "Needs a weapon that can harm it: " + what : wear ? "Needs " + what + " to wear" : "Needs " + what);
			}
		}
		return out;
	}

	/**
	 * Whether an inventory is suggested for the boss: only for fights that were gone through one by
	 * one. For the others the plugin says the inventory is still to come, rather than guess.
	 */
	public boolean isMapped(String boss)
	{
		return mapped.contains(Requirements.key(boss));
	}

	/** Bosses the table says something of their own about. */
	public Set<String> bosses()
	{
		Set<String> out = new HashSet<>(rows.keySet());
		out.addAll(onlyItsOwn);
		out.addAll(mapped);
		out.removeIf(b -> b.startsWith(EVERY_BOSS));
		return out;
	}

	/**
	 * @param boss  the boss as the Combat Achievements name it
	 * @param style "melee", "ranged" or "magic": the style of the set that was picked
	 * @param have  everything the player owns, by its name in lower case
	 * @return the inventory, what the fight needs first; never more than fits
	 */
	public List<Line> of(String boss, String style, Map<String, Have> have)
	{
		return of(boss, style, have, Collections.emptySet(), Collections.emptySet());
	}

	/**
	 * @param done  what the player has done that a line of the table may name as making it needless
	 * @param inSet the item ids of the set that was picked: what is worn is not listed again, and
	 *              a need the set already wears is met
	 */
	public List<Line> of(String boss, String style, Map<String, Have> have, Set<String> done, Set<Integer> inSet)
	{
		return of(boss, style, have, done, inSet, SLOTS);
	}

	/**
	 * @param style  the style of the set, or several joined by commas when pieces of other styles are carried
	 * @param slots  how many inventory slots are left for it, when some are taken by pieces to change into
	 */
	public List<Line> of(String boss, String style, Map<String, Have> have, Set<String> done, Set<Integer> inSet, int slots)
	{
		return of(boss, style, have, done, inSet, slots, Collections.<String>emptySet());
	}

	/**
	 * @param useless usual supplies that do nothing with this set, by their first name in the table in
	 *                lower case (the book of the dead when the set casts from another spellbook)
	 */
	public List<Line> of(String boss, String style, Map<String, Have> have, Set<String> done, Set<Integer> inSet, int slots,
		Set<String> useless)
	{
		String key = Requirements.key(boss);
		Set<String> skip = new HashSet<>(useless);
		skip.addAll(without.getOrDefault(key, Collections.<String>emptySet()));
		List<Row> all = new ArrayList<>(rows.getOrDefault(key, Collections.emptyList()));
		if (!onlyItsOwn.contains(key))
		{
			Set<String> styles = new java.util.LinkedHashSet<>();
			for (String one : (style == null ? "" : style).split(","))
			{
				if (!one.trim().isEmpty())
				{
					styles.add(one.trim().toLowerCase(Locale.ROOT));
				}
			}
			for (String one : styles)
			{
				all.addAll(rows.getOrDefault(EVERY_BOSS + one, Collections.emptyList()));
			}
			// once: twice over, the second time round would take the next best of everything
			all.addAll(rows.getOrDefault(EVERY_BOSS, Collections.emptyList()));
		}
		// what the fight needs goes in before anything that is merely useful
		List<Row> ordered = new ArrayList<>();
		for (String kind : new String[]{"need", "take", "food"})
		{
			for (Row row : all)
			{
				if (kind.equals(row.kind) && (row.unless == null || !done.contains(row.unless)) && Collections.disjoint(skip, row.names))
				{
					ordered.add(row);
				}
			}
		}
		return fill(ordered, have, inSet, slots, null);
	}

	/**
	 * Fills the inventory row by row with what the player owns.
	 *
	 * @param lacking null for the inventory of a fight gone through; for a provisional one, where the
	 *                special items not owned are put, and a kind already met by an earlier row is not taken twice
	 */
	private static List<Line> fill(List<Row> ordered, Map<String, Have> have, Set<Integer> inSet, int slots, List<String> lacking)
	{
		// the same items must always give the same answer
		Map<String, Have> sorted = new TreeMap<>(have);
		List<Line> out = new ArrayList<>();
		Set<Integer> taken = new HashSet<>();
		int free = Math.max(0, Math.min(SLOTS, slots));
		for (Row row : ordered)
		{
			if (free <= 0)
			{
				break;
			}
			boolean need = "need".equals(row.kind);
			List<Have> owned = new ArrayList<>();
			boolean worn = false;
			for (String name : row.names)
			{
				for (Have h : matches(name, sorted))
				{
					worn |= inSet.contains(h.itemId);
					owned.add(h);
				}
			}
			if (worn)
			{
				// the set already has it on (a crossbow for the grapple, a cape that teleports)
				continue;
			}
			if (lacking != null && !need)
			{
				boolean already = false;
				for (Have h : owned)
				{
					already |= taken.contains(h.itemId);
				}
				if (already)
				{
					// what the fight's own line took (an antifire) is not taken again as a kind
					continue;
				}
				if ("special".equals(row.kind) && owned.isEmpty())
				{
					lacking.add(row.label);
					continue;
				}
			}
			int wanted = "food".equals(row.kind) ? free : row.quantity;
			for (Have h : owned)
			{
				if (wanted <= 0 || free <= 0)
				{
					break;
				}
				if (!taken.add(h.itemId))
				{
					continue;
				}
				// when the best is scarce the next best makes up the amount
				int quantity = Math.min(wanted, h.quantity);
				if (!row.stack)
				{
					quantity = Math.min(quantity, free);
				}
				Line line = new Line(h.itemId, h.name, quantity, row.stack, need, true, row.why);
				out.add(line);
				free -= line.slots();
				// a heart is not used up: one is all there is to take, however many potions the amount stands for
				wanted = row.stack || h.name.toLowerCase(Locale.ROOT).endsWith(" heart") ? 0 : wanted - quantity;
			}
			if (need && wanted > 0 && free > 0)
			{
				// what is missing of something the fight needs is listed all the same
				int quantity = row.stack ? wanted : Math.min(wanted, free);
				Line line = new Line(row.ids.getOrDefault(row.names.get(0), 0), display(row.names.get(0)), quantity, row.stack,
					true, false, row.why);
				out.add(line);
				free -= line.slots();
			}
		}
		return out;
	}

	/** The inventory slot by slot, as item id and quantity; at most {@link #SLOTS}. A line with no item id is left out. */
	public static List<int[]> slots(List<Line> lines)
	{
		List<int[]> out = new ArrayList<>();
		for (Line line : lines)
		{
			if (line.itemId <= 0)
			{
				continue;
			}
			for (int i = 0; i < line.slots() && out.size() < SLOTS; i++)
			{
				out.add(new int[]{line.itemId, line.stack ? line.quantity : 1});
			}
		}
		return out;
	}

	/**
	 * The owned items a table name stands for, best first: the name itself, then its doses from
	 * the fullest down; or, for a name in part, every item that has it in its name.
	 */
	private static List<Have> matches(String name, Map<String, Have> sorted)
	{
		List<Have> out = new ArrayList<>();
		if (name.startsWith("~"))
		{
			String part = name.substring(1);
			for (Map.Entry<String, Have> e : sorted.entrySet())
			{
				if (e.getKey().contains(part) && e.getValue().quantity > 0 && !isAPart(e.getKey()))
				{
					out.add(e.getValue());
				}
			}
			return out;
		}
		Have exact = sorted.get(name);
		if (exact != null && exact.quantity > 0)
		{
			out.add(exact);
		}
		for (String dose : DOSES)
		{
			Have dosed = sorted.get(name + dose);
			if (dosed != null && dosed.quantity > 0)
			{
				out.add(dosed);
			}
		}
		// the same item in another dress: an ornament kit, a trim, a locked one ("cannon base (or)")
		String dressed = name + " (";
		for (Map.Entry<String, Have> e : ((TreeMap<String, Have>) sorted).subMap(dressed, dressed + Character.MAX_VALUE).entrySet())
		{
			if (e.getKey().endsWith(")") && !e.getKey().endsWith("(unf)") && !e.getKey().endsWith("(empty)")
				&& e.getValue().quantity > 0 && !isAPart(e.getKey()) && !out.contains(e.getValue()))
			{
				out.add(e.getValue());
			}
		}
		return out;
	}

	/** A piece something is made from (a crossbow string, a pickaxe handle) is not the thing. */
	private static boolean isAPart(String name)
	{
		for (String word : new String[]{"string", "(u)", "handle", "broken", "limbs", "stock", "head", "karil", "hunters'", "inactive"})
		{
			if (name.contains(word))
			{
				return true;
			}
		}
		return false;
	}

	private static String display(String name)
	{
		String n = name.startsWith("~") ? "any " + name.substring(1) : name;
		return n.substring(0, 1).toUpperCase(Locale.ROOT) + n.substring(1);
	}
}
