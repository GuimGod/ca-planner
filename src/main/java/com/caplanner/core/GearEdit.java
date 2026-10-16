/*
 * Copyright (c) 2026, GuimGod
 * All rights reserved. Licensed under the BSD 2-Clause License (see LICENSE).
 */
package com.caplanner.core;

import com.caplanner.gear.Fights;
import com.caplanner.gear.Gear;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Puts the player's changes on a card the planner worked out: the item they chose in each slot they changed, and the
 * recommendation everywhere else. A change stands as it was made: the inventory is not filled again around it (that
 * only happens when the recommendation itself changes). Where the task's rule does not allow a change, the
 * recommendation is shown in that slot and the card says why; the change is kept for the boss's other tasks.
 */
final class GearEdit
{
	/** The name of each equipment slot, by its index. */
	static final Map<Integer, String> SLOT_NAMES = new HashMap<>();
	/** Names that only dress an item another way: owning "Holy scythe of vitur" is owning the Scythe of vitur. */
	private static final List<String> DRESSES = Arrays.asList("holy ", "sanguine ", "twisted ", "radiant ", "corrupted ");

	static
	{
		String[] names = {"Head", "Cape", "Neck", "Weapon", "Body", "Shield", "", "Legs", "", "Hands", "Feet", "", "Ring", "Ammunition"};
		for (int i = 0; i < names.length; i++)
		{
			if (!names[i].isEmpty())
			{
				SLOT_NAMES.put(i, names[i]);
			}
		}
	}

	private GearEdit()
	{
	}

	/** Everything the changes are put on with. */
	static final class Context
	{
		GearCard.Input in;
		/** The set recommended and, for a fight of several parts, the plan it came from (null for a fight of one). */
		Gear.Result main;
		Fights.Plan plan;
		/** The task's own rule as the task gives it, before what the fight makes the player wear; null for none. */
		Gear.Limits rule;
		/** What the fight makes the player wear, and the weapons it makes them hold. */
		List<Bag.Wear> wear = new ArrayList<>();
		List<String> wield = new ArrayList<>();
		/** The supplies the task forbids, by a name of theirs in lower case. */
		Set<String> forbidden = new HashSet<>();
	}

	/** The most pieces one part carries. */
	static final int MOST_PIECES = GearView.MOST_PIECES;

	/** A piece the player added to a part: taken back out if, in the end, the inventory has no room for it. */
	private static final class Added
	{
		int part;
		GearChoices.Pin pin;
		Gear.Stat piece;
		/** What putting it in took off (a shield under a two-handed weapon), put back if it comes out. */
		List<Gear.Stat> tookOff = new ArrayList<>();
		/** What was said of it. */
		List<GearView.Change> said = new ArrayList<>();
	}

	static GearView apply(GearView rec, Context c)
	{
		GearCard.Input in = c.in;
		if (in.choices == null || in.choices.isEmpty() || rec.status != GearView.Status.READY)
		{
			return rec;
		}
		final String card = in.cardKey;
		List<GearView.Change> said = new ArrayList<>();
		Set<Integer> notOwned = new HashSet<>();
		Set<Integer> ownedIds = new HashSet<>();
		for (Gear.Stat s : in.owned)
		{
			ownedIds.add(s.itemId);
		}

		// ---- what the set wears
		Map<Integer, Gear.Stat> set = new LinkedHashMap<>(c.main.items);
		// the slots holding what the player put there: of a two-handed weapon and a shield, only a change of theirs takes
		// the other off; a recommendation the player did not touch is never taken off by an older change
		Set<Integer> byPlayer = new HashSet<>();
		boolean wornChanged = false;
		for (GearChoices.Pin pin : in.choices)
		{
			if (!GearChoices.WORN.equals(pin.place))
			{
				continue;
			}
			Gear.Stat recommended = c.main.items.get(pin.slot);
			int recommendedId = recommended == null ? GearChoices.NOTHING : recommended.itemId;
			String where = SLOT_NAMES.get(pin.slot);
			Gear.Stat now = set.get(pin.slot);
			if (pin.itemId == (now == null ? GearChoices.NOTHING : now.itemId))
			{
				// what the planner recommends there now is what the player chose
				continue;
			}
			Gear.Stat chosen = null;
			if (pin.itemId != GearChoices.NOTHING)
			{
				GearCard.Item item = in.items.get(pin.itemId);
				if (item == null || item.stat == null || item.stat.slot != pin.slot)
				{
					said.add(new GearView.Change(GearView.Change.Kind.ORPHAN, pin.place, pin.slot, ""));
					continue;
				}
				chosen = item.stat;
			}
			Map<Integer, Gear.Stat> before = new LinkedHashMap<>(set);
			List<GearView.Change> off = new ArrayList<>();
			String clash = put(set, pin.slot, chosen, "", off, byPlayer);
			if (clash != null)
			{
				set = before;
				said.add(new GearView.Change(GearView.Change.Kind.MOVED, pin.place, pin.slot, where + ": " + clash));
				continue;
			}
			String broken = breaks(c, set, before);
			if (broken != null)
			{
				set = before;
				said.add(new GearView.Change(GearView.Change.Kind.RULE, pin.place, pin.slot,
					where + ": " + broken + ", so the recommendation stays here."));
				continue;
			}
			byPlayer.add(pin.slot);
			byPlayer.retainAll(set.keySet());
			said.add(new GearView.Change(GearView.Change.Kind.CHANGED, pin.place, pin.slot,
				changedLine(where, recommended == null ? null : recommended.name, chosen == null ? null : chosen.name)));
			moved(said, pin, in.boss, card, recommendedId, recommended == null ? null : recommended.name, where);
			said.addAll(off);
			if (chosen != null && !owns(chosen.itemId, chosen.name, in.have, ownedIds))
			{
				notOwned.add(pin.slot);
				said.add(new GearView.Change(GearView.Change.Kind.NOT_OWNED, pin.place, pin.slot, chosen.name + " is not in your bank."));
			}
			wornChanged = true;
		}
		// an item the player does not own that a later change took off is not said to be worn
		notOwned.retainAll(set.keySet());
		if (wornChanged)
		{
			needed(c, set, said);
		}

		// ---- the supplies, slot by slot after the pieces to change into: first, so that a slot the player emptied
		// makes room for a piece. A change is found by the item it took the place of (the cards of a boss do not all
		// carry the same supplies in the same order), and at its own place on the card it was made on.
		List<Bag.Line> recommendedCells = rec.supplyCells();
		List<Bag.Line> cells = new ArrayList<>(recommendedCells);
		boolean bagChanged = false;
		Set<Integer> taken = new HashSet<>();
		// what to say of each, once the pieces before the supplies are known: the pin, what was there, what is now, where
		List<Object[]> bagSaid = new ArrayList<>();
		for (GearChoices.Pin pin : in.choices)
		{
			if (!GearChoices.BAG.equals(pin.place))
			{
				continue;
			}
			boolean here = pin.madeOn(in.boss, card);
			int at = pin.slot;
			Bag.Line there = at < recommendedCells.size() ? recommendedCells.get(at) : null;
			if (pin.recommended != GearChoices.UNKNOWN && (there == null ? GearChoices.NOTHING : there.itemId) != pin.recommended)
			{
				int found = -1;
				for (int i = 0; pin.recommended != GearChoices.NOTHING && found < 0 && i < recommendedCells.size(); i++)
				{
					Bag.Line line = recommendedCells.get(i);
					found = line != null && line.itemId == pin.recommended && !taken.contains(i) ? i : -1;
				}
				if (found >= 0)
				{
					at = found;
				}
				else if (!here)
				{
					// made on another card of the boss, for a supply this card does not carry
					continue;
				}
			}
			taken.add(at);
			Bag.Line recommended = at < recommendedCells.size() ? recommendedCells.get(at) : null;
			while (cells.size() <= at)
			{
				cells.add(null);
			}
			Bag.Line now = cells.get(at);
			boolean sameAmount = now == null || !now.stack || now.quantity == pin.quantity;
			if (pin.itemId == (now == null ? GearChoices.NOTHING : now.itemId) && sameAmount)
			{
				continue;
			}
			Bag.Line chosen = null;
			if (pin.itemId != GearChoices.NOTHING)
			{
				GearCard.Item item = in.items.get(pin.itemId);
				if (item == null)
				{
					said.add(new GearView.Change(GearView.Change.Kind.ORPHAN, pin.place, pin.slot, ""));
					continue;
				}
				boolean owned = owns(item.itemId, item.name, in.have, ownedIds);
				chosen = new Bag.Line(item.itemId, item.name, item.stackable ? pin.quantity : 1, item.stackable, false, owned, "");
				if (forbids(c, item.name))
				{
					// what the task forbids is not carried on its card, whatever the player put in on another
					bagSaid.add(new Object[]{pin, recommended, chosen, at, here, Boolean.TRUE});
					continue;
				}
			}
			cells.set(at, chosen);
			bagSaid.add(new Object[]{pin, recommended, chosen, at, here, Boolean.FALSE});
			bagChanged = true;
		}
		List<Bag.Line> supplies = trimmed(cells);

		// ---- the pieces to change into for each part
		Map<String, Fights.Role> roles = new HashMap<>();
		if (c.plan != null)
		{
			for (Fights.Role role : c.plan.roles)
			{
				roles.put(role.key, role);
			}
		}
		Set<String> keys = in.fights == null ? new HashSet<String>() : in.fights.partKeys(in.boss);
		List<List<Gear.Stat>> partPieces = new ArrayList<>();
		List<Set<Integer>> partNotOwned = new ArrayList<>();
		boolean[] partChanged = new boolean[rec.parts.size()];
		List<Added> added = new ArrayList<>();
		for (int index = 0; index < rec.parts.size(); index++)
		{
			GearView.Part part = rec.parts.get(index);
			Fights.Role role = roles.get(part.key);
			List<Gear.Stat> pieces = new ArrayList<>(role == null ? new ArrayList<Gear.Stat>() : role.carried);
			Set<Integer> mineNotOwned = new HashSet<>();
			Set<Integer> partByPlayer = new HashSet<>();
			for (GearChoices.Pin pin : in.choices)
			{
				if (!pin.isPart() || !pin.partKey().equals(part.key) || role == null)
				{
					continue;
				}
				String where = SLOT_NAMES.get(pin.slot) + " carried for " + part.label;
				Gear.Stat recommended = pieceIn(role.carried, pin.slot);
				int recommendedId = recommended == null ? GearChoices.NOTHING : recommended.itemId;
				Gear.Stat now = pieceIn(pieces, pin.slot);
				if (pin.itemId == (now == null ? GearChoices.NOTHING : now.itemId))
				{
					continue;
				}
				Gear.Stat chosen = null;
				if (pin.itemId != GearChoices.NOTHING)
				{
					GearCard.Item item = in.items.get(pin.itemId);
					if (item == null || item.stat == null || item.stat.slot != pin.slot)
					{
						said.add(new GearView.Change(GearView.Change.Kind.ORPHAN, pin.place, pin.slot, ""));
						continue;
					}
					chosen = item.stat;
				}
				if (now == null && chosen != null && pieces.size() >= MOST_PIECES)
				{
					said.add(new GearView.Change(GearView.Change.Kind.CUT, pin.place, pin.slot, part.label + " carries " + MOST_PIECES
						+ " pieces at most, so " + chosen.name + " is not carried for it."));
					continue;
				}
				List<Gear.Stat> before = new ArrayList<>(pieces);
				List<GearView.Change> off = new ArrayList<>();
				Map<Integer, Gear.Stat> asPieces = bySlot(pieces);
				String clash = put(asPieces, pin.slot, chosen, " carried for " + part.label, off, partByPlayer);
				if (clash != null)
				{
					said.add(new GearView.Change(GearView.Change.Kind.MOVED, pin.place, pin.slot, where + ": " + clash));
					continue;
				}
				pieces = inOrder(before, asPieces);
				Gear.Stat worn = set.get(3);
				if (chosen != null && pin.slot == 5 && pieceIn(pieces, 3) == null && worn != null && worn.twoHanded)
				{
					// changing into the shield for this part takes the two-handed weapon worn off, as in the game
					off.add(new GearView.Change(GearView.Change.Kind.TAKEN_OFF, "", 3, "For " + part.label + ": " + chosen.name
						+ " goes in the shield's hand, so " + worn.name + " comes off."));
				}
				String broken = breaks(c, changedInto(set, pieces), changedInto(set, before));
				if (broken != null)
				{
					pieces = before;
					said.add(new GearView.Change(GearView.Change.Kind.RULE, pin.place, pin.slot,
						where + ": " + broken + ", so the recommendation stays here."));
					continue;
				}
				int from = said.size();
				said.add(new GearView.Change(GearView.Change.Kind.CHANGED, pin.place, pin.slot,
					changedLine(where, recommended == null ? null : recommended.name, chosen == null ? null : chosen.name)));
				moved(said, pin, in.boss, card, recommendedId, recommended == null ? null : recommended.name, where);
				said.addAll(off);
				if (chosen != null && !owns(chosen.itemId, chosen.name, in.have, ownedIds))
				{
					mineNotOwned.add(chosen.itemId);
					said.add(new GearView.Change(GearView.Change.Kind.NOT_OWNED, pin.place, pin.slot, chosen.name + " is not in your bank."));
				}
				partByPlayer.add(pin.slot);
				partChanged[index] = true;
				if (now == null && chosen != null)
				{
					Added a = new Added();
					a.part = index;
					a.pin = pin;
					a.piece = chosen;
					for (Gear.Stat s : before)
					{
						if (!pieces.contains(s))
						{
							a.tookOff.add(s);
						}
					}
					a.said.addAll(said.subList(from, said.size()));
					added.add(a);
				}
			}
			partPieces.add(pieces);
			partNotOwned.add(mineNotOwned);
		}
		// one more piece is carried only when the inventory has room for it in the end, once every part and supply is
		// as the player left it: the latest one added comes back out first
		while (!added.isEmpty() && GearView.carriedCells(laid(partPieces, partNotOwned)) + filled(supplies) > Bag.SLOTS)
		{
			Added last = added.get(0);
			for (Added a : added)
			{
				last = a.pin.order > last.pin.order ? a : last;
			}
			added.remove(last);
			List<Gear.Stat> pieces = partPieces.get(last.part);
			pieces.remove(last.piece);
			for (Gear.Stat s : last.tookOff)
			{
				if (pieceIn(pieces, s.slot) == null)
				{
					pieces.add(s);
				}
			}
			said.removeAll(last.said);
			said.add(new GearView.Change(GearView.Change.Kind.CUT, last.pin.place, last.pin.slot, "The inventory is full, so "
				+ last.piece.name + " is not carried for " + rec.parts.get(last.part).label + ". Take an item out first."));
		}

		List<GearView.Part> parts = new ArrayList<>();
		Map<String, List<Integer>> pieceSlots = new LinkedHashMap<>();
		boolean partsChanged = false;
		for (int index = 0; index < rec.parts.size(); index++)
		{
			GearView.Part part = rec.parts.get(index);
			Fights.Role role = roles.get(part.key);
			List<Gear.Stat> pieces = partPieces.get(index);
			List<Integer> slots = new ArrayList<>();
			for (Gear.Stat s : pieces)
			{
				slots.add(s.slot);
			}
			pieceSlots.put(part.key, slots);
			if (!partChanged[index] && !wornChanged)
			{
				parts.add(part);
				continue;
			}
			partsChanged |= partChanged[index];
			double dps = part.dps;
			String style = part.style;
			String spell = part.spell;
			// a part nothing owned did, recounted only once the player put pieces in for it
			if (role != null && part.npcId != 0 && (part.dps > 0 || (part.nothing && !pieces.isEmpty())))
			{
				// the set worn with this part's pieces changed in, as the plan counts it: its own pieces as the player left
				// them, and the ones it shares with another part as they were
				Map<Integer, Gear.Stat> using = new LinkedHashMap<>();
				for (Gear.Stat s : role.counted == null ? role.carried : role.counted)
				{
					if (!role.carried.contains(s))
					{
						using.put(s.slot, s);
					}
				}
				for (Gear.Stat s : pieces)
				{
					using.put(s.slot, s);
				}
				Gear.Worth worth = in.gear.worthAt(part.npcId, changedInto(set, new ArrayList<>(using.values())), in.levels, in.onTask);
				dps = worth.dps;
				style = worth.style.isEmpty() ? part.style : worth.style;
				spell = worth.style.isEmpty() ? part.spell : worth.spell;
			}
			GearView.Part edited = new GearView.Part(part.label, style, dps, lines(pieces, partNotOwned.get(index)), spell, part.note,
				part.nothing && pieces.isEmpty(), part.sharedWeapon, part.sharedWith);
			edited.best = pieces.isEmpty() ? part.best : null;
			edited.key = part.key;
			edited.npcId = part.npcId;
			parts.add(edited);
		}

		for (GearChoices.Pin pin : in.choices)
		{
			// a part the table no longer has (one renamed in a new version): said, so the player can forget it; a part the
			// table has that is not on this card (of a set of another style, left out by the task) waits for its card
			if (pin.isPart() && !keys.contains(pin.partKey()))
			{
				said.add(new GearView.Change(GearView.Change.Kind.ORPHAN, pin.place, pin.slot, ""));
			}
		}

		List<List<Bag.Line>> partLines = new ArrayList<>();
		for (GearView.Part part : parts)
		{
			partLines.add(part.carried);
		}
		int carried = GearView.carriedCells(partLines);
		if (bagChanged)
		{
			// what the fight cannot be done without, taken out
			for (Bag.Line line : rec.bag)
			{
				if (line.needed && line.itemId > 0 && !holds(cells, line.itemId))
				{
					said.add(new GearView.Change(GearView.Change.Kind.NEEDED, "", -1,
						"The fight needs " + line.name + " in the inventory: you took it out."));
				}
			}
		}
		cells = trimmed(cells);
		// where each supply shown sits among the supplies the changes were made to
		List<Integer> index = new ArrayList<>();
		for (int i = 0; i < cells.size(); i++)
		{
			index.add(i);
		}
		if (carried + cells.size() > Bag.SLOTS)
		{
			// more than the inventory holds: first the slots the player emptied close up, from the last one back, as
			// in the game a piece goes in any free slot; then (the recommendation took more pieces since) the last ones
			// are left out
			for (int i = cells.size() - 1; i >= 0 && carried + cells.size() > Bag.SLOTS; i--)
			{
				if (cells.get(i) == null)
				{
					cells.remove(i);
					index.remove(i);
				}
			}
			int keep = Math.max(0, Bag.SLOTS - carried);
			int lost = 0;
			for (int i = keep; i < cells.size(); i++)
			{
				lost += cells.get(i) == null ? 0 : 1;
			}
			if (cells.size() > keep)
			{
				cells = new ArrayList<>(cells.subList(0, keep));
				index = new ArrayList<>(index.subList(0, keep));
			}
			if (lost > 0)
			{
				said.add(new GearView.Change(GearView.Change.Kind.CUT, "", -1,
					"Over 28 inventory slots: the last " + lost + (lost == 1 ? " is" : " are") + " left out."));
			}
			bagChanged = true;
		}
		for (Object[] one : bagSaid)
		{
			GearChoices.Pin pin = (GearChoices.Pin) one[0];
			Bag.Line recommended = (Bag.Line) one[1];
			Bag.Line chosen = (Bag.Line) one[2];
			int at = (Integer) one[3];
			boolean here = (Boolean) one[4];
			// the slot as it is shown, once the emptied ones closed up
			int shown = index.indexOf(at);
			String where = shown >= 0 || at >= cells.size() ? "Inventory slot " + (carried + (shown >= 0 ? shown : at) + 1) : "Inventory";
			if ((Boolean) one[5])
			{
				said.add(new GearView.Change(GearView.Change.Kind.RULE, pin.place, pin.slot,
					where + ": this task does not allow " + chosen.name + ", so the recommendation stays here."));
				continue;
			}
			said.add(new GearView.Change(GearView.Change.Kind.CHANGED, pin.place, pin.slot,
				changedLine(where, recommended == null ? null : recommended.name,
					chosen == null ? null : chosen.name + (chosen.stack && chosen.quantity > 1 ? " x" + chosen.quantity : ""))));
			if (here)
			{
				moved(said, pin, in.boss, card, recommended == null ? GearChoices.NOTHING : recommended.itemId,
					recommended == null ? null : recommended.name, where);
			}
			if (chosen != null && !chosen.owned)
			{
				said.add(new GearView.Change(GearView.Change.Kind.NOT_OWNED, pin.place, pin.slot, chosen.name + " is not in your bank."));
			}
		}

		if (said.isEmpty())
		{
			return rec;
		}
		int orphans = 0;
		for (GearView.Change change : said)
		{
			orphans += change.kind == GearView.Change.Kind.ORPHAN ? 1 : 0;
		}
		if (orphans > 0)
		{
			said.add(new GearView.Change(GearView.Change.Kind.ORPHAN, "", -1, orphans == 1 ? "1 old change no longer applies."
				: orphans + " old changes no longer apply."));
		}
		String style = rec.style;
		double dps = rec.dps;
		String spell = null;
		List<String> notes = new ArrayList<>(rec.notes);
		if (wornChanged)
		{
			Gear.Worth worth = in.gear.worthOf(in.boss, c.plan == null ? 0 : c.plan.mainNpcId, set, in.levels, in.onTask, c.rule);
			// a set that does nothing here has no style to say
			style = worth.style;
			dps = worth.dps;
			spell = worth.spell;
			// the spell the recommendation was counted with is said again for the set as changed, or not at all
			notes.removeIf(n -> n.startsWith("Counted casting "));
		}
		Map<Integer, Integer> ids = new LinkedHashMap<>();
		Map<Integer, String> names = new LinkedHashMap<>();
		for (Map.Entry<Integer, Gear.Stat> e : set.entrySet())
		{
			ids.put(e.getKey(), e.getValue().itemId);
			names.put(e.getKey(), e.getValue().name);
		}
		GearView out = rec.changed(wornChanged ? ids : rec.itemIdBySlot, wornChanged ? names : rec.nameBySlot, style, dps,
			partsChanged || wornChanged ? parts : rec.parts, notes);
		out.inventoryToCome = rec.inventoryToCome;
		out.provisional = rec.provisional;
		out.pieceSlots = pieceSlots;
		out.recommended = rec;
		out.changes = said;
		out.notOwned = notOwned;
		out.cells = bagChanged ? cells : null;
		out.cellIndex = bagChanged ? index : null;
		out.editedSpell = spell;
		return out;
	}

	/** The pieces of each part as the inventory lays them out. */
	private static List<List<Bag.Line>> laid(List<List<Gear.Stat>> partPieces, List<Set<Integer>> notOwned)
	{
		List<List<Bag.Line>> out = new ArrayList<>();
		for (int i = 0; i < partPieces.size(); i++)
		{
			out.add(lines(partPieces.get(i), notOwned.get(i)));
		}
		return out;
	}

	/** Whether the task forbids a supply, by its name. */
	private static boolean forbids(Context c, String name)
	{
		String n = name == null ? "" : name.toLowerCase(Locale.ROOT);
		for (String f : c.forbidden)
		{
			if (n.equals(f) || n.startsWith(f + "(") || n.startsWith(f + " ("))
			{
				return true;
			}
		}
		return false;
	}

	/**
	 * Puts an item in a slot, or takes it out (null), as the game does: a two-handed weapon takes the shield off, and a
	 * shield a two-handed weapon. What came off is said. A shield does not take off a two-handed weapon the player did
	 * not put there themselves (one the recommendation moved to since): the change gives way, and the line why is
	 * returned; null otherwise.
	 */
	private static String put(Map<Integer, Gear.Stat> set, int slot, Gear.Stat item, String carriedFor, List<GearView.Change> off,
		Set<Integer> byPlayer)
	{
		if (item == null)
		{
			set.remove(slot);
			return null;
		}
		Gear.Stat weapon = set.get(3);
		if (slot == 5 && weapon != null && weapon.twoHanded && !byPlayer.contains(3))
		{
			return weapon.name + " is two-handed and recommended here, so " + item.name + " is left out.";
		}
		set.put(slot, item);
		if (slot == 3 && item.twoHanded && set.containsKey(5))
		{
			set.remove(5);
			off.add(new GearView.Change(GearView.Change.Kind.TAKEN_OFF, "", 5,
				"Shield" + carriedFor + " taken off: " + item.name + " is two-handed."));
		}
		else if (slot == 5 && weapon != null && weapon.twoHanded)
		{
			set.remove(3);
			off.add(new GearView.Change(GearView.Change.Kind.TAKEN_OFF, "", 3,
				"Weapon" + carriedFor + " taken off: " + item.name + " goes in the shield's hand and " + weapon.name + " is two-handed."));
		}
		return null;
	}

	/** The task's rule broken by the set as it is now, when the set before kept to it; null otherwise. */
	private static String breaks(Context c, Map<Integer, Gear.Stat> now, Map<Integer, Gear.Stat> before)
	{
		if (c.rule == null)
		{
			return null;
		}
		String after = c.in.gear.breaks(now, c.in.levels, c.rule);
		return after != null && c.in.gear.breaks(before, c.in.levels, c.rule) == null ? after : null;
	}

	/**
	 * Says that the recommendation of a slot moved on since the player changed it: only on the card the change was made
	 * on, the one whose recommendation it was.
	 */
	private static void moved(List<GearView.Change> said, GearChoices.Pin pin, String boss, String card, int recommendedId,
		String recommendedName, String where)
	{
		if (pin.madeOn(boss, card) && pin.recommended != GearChoices.UNKNOWN && pin.recommended != recommendedId)
		{
			said.add(new GearView.Change(GearView.Change.Kind.MOVED, pin.place, pin.slot,
				where + ": the recommendation here is now " + (recommendedName == null ? "nothing" : recommendedName) + "."));
		}
	}

	static String changedLine(String where, String from, String to)
	{
		if (to == null)
		{
			return where + ": you took out " + from;
		}
		return from == null ? where + ": you put in " + to : where + ": you changed " + from + " → " + to;
	}

	/** What the fight makes the player wear or hold, and the changes took off. */
	private static void needed(Context c, Map<Integer, Gear.Stat> set, List<GearView.Change> said)
	{
		for (Bag.Wear w : c.wear)
		{
			if (wears(c.main.items, w.names) && !wears(set, w.names))
			{
				said.add(new GearView.Change(GearView.Change.Kind.NEEDED, "", -1, "The fight needs " + w.name + " worn: you changed it."));
			}
		}
		Gear.Stat weapon = set.get(3);
		Gear.Stat was = c.main.items.get(3);
		if (!c.wield.isEmpty() && was != null && namedOne(was.name, c.wield) && (weapon == null || !namedOne(weapon.name, c.wield)))
		{
			said.add(new GearView.Change(GearView.Change.Kind.NEEDED, "", -1, "The fight needs " + was.name + " held: you changed it."));
		}
	}

	private static boolean wears(Map<Integer, Gear.Stat> set, List<String> names)
	{
		for (Gear.Stat s : set.values())
		{
			if (s != null && namedOne(s.name, names))
			{
				return true;
			}
		}
		return false;
	}

	private static boolean namedOne(String name, List<String> parts)
	{
		String n = name == null ? "" : name.toLowerCase(Locale.ROOT);
		for (String part : parts)
		{
			if (n.contains(part.toLowerCase(Locale.ROOT)))
			{
				return true;
			}
		}
		return false;
	}

	/**
	 * Whether the player owns an item, counting its other dresses as the item (an ornament kit, a trim, imbued, a
	 * cosmetic name before it) and a placeholder as owned.
	 */
	/**
	 * The player's own item a name stands for: the item itself, or another dress of it they own (an ornament kit, a trim,
	 * imbued, a cosmetic name before it); null when they own none.
	 */
	static Bag.Have ownedAs(String name, Map<String, Bag.Have> have)
	{
		String plain = name == null ? "" : name.toLowerCase(Locale.ROOT);
		if (plain.isEmpty() || have == null)
		{
			return null;
		}
		if (have.containsKey(plain))
		{
			return have.get(plain);
		}
		for (Map.Entry<String, Bag.Have> e : have.entrySet())
		{
			String kept = e.getKey();
			if ((kept.startsWith(plain + " (") || kept.startsWith(plain + "(")) && kept.endsWith(")"))
			{
				return e.getValue();
			}
			for (String dress : DRESSES)
			{
				if (kept.equals(dress + plain))
				{
					return e.getValue();
				}
			}
		}
		return null;
	}

	static boolean owns(int itemId, String name, Map<String, Bag.Have> have, Set<Integer> ownedIds)
	{
		if (ownedIds.contains(itemId))
		{
			return true;
		}
		String plain = name == null ? "" : name.toLowerCase(Locale.ROOT);
		if (plain.isEmpty() || have == null)
		{
			return false;
		}
		if (have.containsKey(plain))
		{
			return true;
		}
		for (String kept : have.keySet())
		{
			if ((kept.startsWith(plain + " (") || kept.startsWith(plain + "(")) && kept.endsWith(")"))
			{
				return true;
			}
			for (String dress : DRESSES)
			{
				if (kept.equals(dress + plain))
				{
					return true;
				}
			}
		}
		return false;
	}

	private static Gear.Stat pieceIn(List<Gear.Stat> pieces, int slot)
	{
		for (Gear.Stat s : pieces)
		{
			if (s.slot == slot)
			{
				return s;
			}
		}
		return null;
	}

	private static Map<Integer, Gear.Stat> bySlot(List<Gear.Stat> pieces)
	{
		Map<Integer, Gear.Stat> out = new LinkedHashMap<>();
		for (Gear.Stat s : pieces)
		{
			out.put(s.slot, s);
		}
		return out;
	}

	/** The pieces after a change, in the order they had: one in place of another keeps its place, a new one goes last. */
	private static List<Gear.Stat> inOrder(List<Gear.Stat> before, Map<Integer, Gear.Stat> after)
	{
		List<Gear.Stat> out = new ArrayList<>();
		Set<Integer> placed = new HashSet<>();
		for (Gear.Stat s : before)
		{
			Gear.Stat now = after.get(s.slot);
			if (now != null && placed.add(s.slot))
			{
				out.add(now);
			}
		}
		for (Map.Entry<Integer, Gear.Stat> e : after.entrySet())
		{
			if (placed.add(e.getKey()))
			{
				out.add(e.getValue());
			}
		}
		return out;
	}

	/**
	 * The set worn with a part's pieces changed in, as the plan counts a part: a two-handed weapon leaves no shield, and
	 * a shield changed into takes a two-handed weapon worn off.
	 */
	private static Map<Integer, Gear.Stat> changedInto(Map<Integer, Gear.Stat> set, List<Gear.Stat> pieces)
	{
		Map<Integer, Gear.Stat> worn = new LinkedHashMap<>(set);
		boolean shield = false;
		boolean weaponCarried = false;
		for (Gear.Stat s : pieces)
		{
			worn.put(s.slot, s);
			shield |= s.slot == 5;
			weaponCarried |= s.slot == 3;
		}
		Gear.Stat weapon = worn.get(3);
		if (weapon != null && weapon.twoHanded && !shield)
		{
			worn.remove(5);
		}
		else if (weapon != null && weapon.twoHanded && shield && !weaponCarried)
		{
			worn.remove(3);
		}
		return worn;
	}

	private static List<Bag.Line> lines(List<Gear.Stat> pieces, Set<Integer> notOwned)
	{
		List<Bag.Line> out = new ArrayList<>();
		for (Gear.Stat s : pieces)
		{
			out.add(Bag.Line.worn(s.itemId, s.name, !notOwned.contains(s.itemId), ""));
		}
		return out;
	}

	private static boolean holds(List<Bag.Line> cells, int itemId)
	{
		for (Bag.Line line : cells)
		{
			if (line != null && line.itemId == itemId)
			{
				return true;
			}
		}
		return false;
	}

	/** The slots that hold something. */
	static int filled(List<Bag.Line> cells)
	{
		int n = 0;
		for (Bag.Line line : cells)
		{
			n += line == null ? 0 : 1;
		}
		return n;
	}

	private static List<Bag.Line> trimmed(List<Bag.Line> cells)
	{
		List<Bag.Line> out = new ArrayList<>(cells);
		while (!out.isEmpty() && out.get(out.size() - 1) == null)
		{
			out.remove(out.size() - 1);
		}
		return out;
	}
}
