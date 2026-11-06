/*
 * Copyright (c) 2026, GuimGod
 * All rights reserved. Licensed under the BSD 2-Clause License (see LICENSE).
 */
package com.caplanner.gear;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Every item in another dress and the item it is (variants.tsv, tools/gen_variants.py): an ornament kit ("(or)", the
 * Holy and Sanguine Scythe of vitur, the Radiant oathplate, the Sanguine torva, the Twisted ancestral), a locked one
 * ("(l)"), a trim, a recolour (the slayer helmets, the Bow of Faerdhinen and the Blade of Saeldor (c), the crystal
 * armour), a corrupted one of Bounty Hunter, and the same item under other ids (Last Man Standing, Deadman). Each has the
 * item's equipment bonuses, and the OSRS Wiki DPS calculator counts it as the item (EquipmentAliases: "identical in
 * function"). The plugin reads every item as the item it is, so that no rule, kept by name or by id, misses a dress.
 */
final class Variants
{
	// the names first: load() fills them
	private static final Map<Integer, String> NAMES = new HashMap<>();
	private static final Map<Integer, String> BASE_NAMES = new HashMap<>();
	private static final Map<Integer, Integer> BASE = load();

	private Variants()
	{
	}

	/** The item an item is: itself unless it is another dress of one. */
	static int base(int itemId)
	{
		Integer base = BASE.get(itemId);
		return base == null ? itemId : base;
	}

	/** Every dress and the item it is. */
	static Map<Integer, Integer> all()
	{
		return Collections.unmodifiableMap(BASE);
	}

	/** The name of a dress or of its item, as the table has it (RuneLite's); null for an item not in it. */
	static String name(int itemId)
	{
		String n = NAMES.get(itemId);
		return n != null ? n : BASE_NAMES.get(itemId);
	}

	private static Map<Integer, Integer> load()
	{
		Map<Integer, Integer> base = new HashMap<>();
		try
		{
			for (String line : StyleData.resource("variants.tsv").split("\n"))
			{
				if (line.isEmpty() || line.startsWith("#"))
				{
					continue;
				}
				String[] cols = line.split("\t");
				int variant = Integer.parseInt(cols[0].trim());
				int item = Integer.parseInt(cols[1].trim());
				base.put(variant, item);
				if (cols.length >= 4)
				{
					NAMES.put(variant, cols[2]);
					BASE_NAMES.put(item, cols[3].trim());
				}
			}
		}
		catch (IOException | RuntimeException e)
		{
			// without the table each item is only itself, as before it
			return new HashMap<>();
		}
		return base;
	}
}
