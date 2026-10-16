package com.caplanner.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;

/** The search of items for a slot the player changes (cases 4, 6 and 16 of the plan). */
public class ItemSearchTest
{
	private static ItemSearch search()
	{
		return new ItemSearch(Arrays.asList(
			new ItemSearch.Entry(11773, "Berserker ring (i)", 12, false),
			new ItemSearch.Entry(6737, "Berserker ring", 12, false),
			new ItemSearch.Entry(25975, "Lightbearer", 12, false),
			new ItemSearch.Entry(28307, "Ultor ring", 12, false),
			new ItemSearch.Entry(24271, "Neitiznot faceguard", 0, false),
			new ItemSearch.Entry(10588, "Salve amulet (e)", 2, false),
			new ItemSearch.Entry(12018, "Salve amulet(ei)", 2, false),
			new ItemSearch.Entry(4081, "Salve amulet", 2, false),
			new ItemSearch.Entry(26246, "Torva full helm", 0, false),
			new ItemSearch.Entry(28254, "Torva full helm (or)", 0, false),
			new ItemSearch.Entry(385, "Shark", -1, false),
			new ItemSearch.Entry(386, "Shark", -1, false),
			new ItemSearch.Entry(560, "Death rune", -1, true),
			new ItemSearch.Entry(9999, "Ring of dueling(8)", 12, false)));
	}

	@Test
	public void theSearchOfASlotListsOnlyWhatGoesInIt()
	{
		// case 4: the ring's slot lists rings, and nothing to eat goes in the gear
		List<ItemSearch.Hit> rings = search().find("ring", 12, new HashMap<>(), id -> -1);
		assertFalse(rings.isEmpty());
		for (ItemSearch.Hit h : rings)
		{
			assertEquals(12, h.entry.slot);
		}
		assertTrue(search().find("shark", 12, new HashMap<>(), id -> -1).isEmpty());
		assertTrue(search().find("shark", ItemSearch.ANY_WORN, new HashMap<>(), id -> -1).isEmpty());
		assertEquals("one of each name", 1, search().find("shark", ItemSearch.ANYTHING, new HashMap<>(), id -> -1).size());
		assertTrue(search().find("r", ItemSearch.ANYTHING, new HashMap<>(), id -> -1).isEmpty());
		assertTrue(search().find("death", ItemSearch.ANYTHING, new HashMap<>(), id -> -1).get(0).entry.stackable);
	}

	@Test
	public void whatIsOwnedComesFirstAndEachItemByItsWholeName()
	{
		// case 6: each Salve amulet by its own name; an ornament kit owned is the item owned
		Map<String, Bag.Have> have = new HashMap<>();
		have.put("torva full helm (or)", new Bag.Have(28254, 1, "Torva full helm (or)"));
		have.put("ultor ring", new Bag.Have(28307, 0, "Ultor ring"));
		List<ItemSearch.Hit> salve = search().find("salve", 2, have, id -> -1);
		List<String> names = new ArrayList<>();
		for (ItemSearch.Hit h : salve)
		{
			names.add(h.entry.name);
		}
		assertEquals(Arrays.asList("Salve amulet", "Salve amulet (e)", "Salve amulet(ei)"), names);
		List<ItemSearch.Hit> torva = search().find("torva", 0, have, id -> 0);
		assertTrue(torva.get(0).owned && torva.get(1).owned);
		List<ItemSearch.Hit> rings = search().find("ring", 12, have, id -> 12);
		assertEquals("a placeholder is owned, and comes first", "Ultor ring", rings.get(0).entry.name);
		assertTrue(rings.get(0).owned);
		assertFalse(rings.get(rings.size() - 1).owned);
	}

	@Test
	public void theItemsAreReadOnTheClientThreadOnly() throws Exception
	{
		// case 16: the game's items are read in parts, inside the client thread's own queue, never from the panel's
		String plugin = new String(Files.readAllBytes(Paths.get("src/main/java/com/caplanner/CaPlannerPlugin.java")), StandardCharsets.UTF_8);
		int find = plugin.indexOf("public void findItems(");
		int queued = plugin.indexOf("clientThread.invokeLater(() ->", find);
		int read = plugin.indexOf("readItemIndex()", find);
		assertTrue(find > 0 && queued > find && read > queued);
		// called there only: once, and its own definition
		assertEquals(2, plugin.split("readItemIndex\\(\\)", -1).length - 1);
		assertTrue(plugin.contains("private static final int INDEX_STEP = 4000;"));
	}
}
