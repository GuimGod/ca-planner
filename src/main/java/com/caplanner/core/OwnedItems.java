/*
 * Copyright (c) 2026, GuimGod
 * All rights reserved. Licensed under the BSD 2-Clause License (see LICENSE).
 */
package com.caplanner.core;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * What the player keeps in the bank, as last seen. The game only shows the bank while it is
 * open, so the plugin remembers it, per account, as the text {@link #encode} writes.
 */
public final class OwnedItems
{
	private final Map<Integer, Integer> quantities;

	private OwnedItems(Map<Integer, Integer> quantities)
	{
		this.quantities = Collections.unmodifiableMap(quantities);
	}

	public static OwnedItems none()
	{
		return new OwnedItems(new LinkedHashMap<>());
	}

	/**
	 * A placeholder counts as owned: whoever keeps one has had the item, knows what it is for, and can buy it
	 * again. The game gives a placeholder an id of its own with quantity 1 (the item's id comes from
	 * canonicalizing it); a slot with quantity 0 is owned all the same, with none of it in stock.
	 *
	 * @param ids        item ids, slot by slot; an empty slot is -1 or 0
	 * @param quantities the quantity in each slot
	 */
	public static OwnedItems of(int[] ids, int[] quantities)
	{
		Map<Integer, Integer> map = new LinkedHashMap<>();
		for (int i = 0; i < ids.length && i < quantities.length; i++)
		{
			if (ids[i] > 0)
			{
				map.merge(ids[i], Math.max(0, quantities[i]), (a, b) -> (int) Math.min(Integer.MAX_VALUE, (long) a + b));
			}
		}
		return new OwnedItems(map);
	}

	public boolean has(int itemId)
	{
		return quantities.containsKey(itemId);
	}

	public int quantity(int itemId)
	{
		return quantities.getOrDefault(itemId, 0);
	}

	/** The ids of everything owned. */
	public java.util.Set<Integer> ids()
	{
		return quantities.keySet();
	}

	/** How many different items. */
	public int size()
	{
		return quantities.size();
	}

	/** "id:quantity" pairs joined by commas. */
	public String encode()
	{
		StringBuilder out = new StringBuilder();
		for (Map.Entry<Integer, Integer> e : quantities.entrySet())
		{
			if (out.length() > 0)
			{
				out.append(',');
			}
			out.append(e.getKey()).append(':').append(e.getValue());
		}
		return out.toString();
	}

	/** Reads what {@link #encode} wrote. A pair that cannot be read is skipped. */
	public static OwnedItems decode(String text)
	{
		Map<Integer, Integer> map = new LinkedHashMap<>();
		if (text != null)
		{
			for (String pair : text.split(","))
			{
				int colon = pair.indexOf(':');
				if (colon <= 0)
				{
					continue;
				}
				try
				{
					int id = Integer.parseInt(pair.substring(0, colon).trim());
					int quantity = Integer.parseInt(pair.substring(colon + 1).trim());
					if (id > 0 && quantity >= 0)
					{
						map.put(id, quantity);
					}
				}
				catch (NumberFormatException e)
				{
					// not a pair this version wrote
				}
			}
		}
		return new OwnedItems(map);
	}
}
