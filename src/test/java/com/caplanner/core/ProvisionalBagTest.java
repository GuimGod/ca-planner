/*
 * Copyright (c) 2026, GuimGod
 * All rights reserved. Licensed under the BSD 2-Clause License (see LICENSE).
 */
package com.caplanner.core;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/** The provisional inventory of bosses not gone through yet, from the tables the plugin ships. */
public class ProvisionalBagTest
{
	private static BufferedReader reader(String path)
	{
		return new BufferedReader(new InputStreamReader(ProvisionalBagTest.class.getResourceAsStream(path), StandardCharsets.UTF_8));
	}

	private static Bag bag() throws Exception
	{
		Bag bag;
		try (BufferedReader in = reader("/com/caplanner/bag.tsv"))
		{
			bag = Bag.read(in);
		}
		try (BufferedReader in = reader("/com/caplanner/bag-wiki.tsv"))
		{
			bag.readWiki(in);
		}
		return bag;
	}

	private static Map<String, Bag.Have> have(Object... idQuantityName)
	{
		Map<String, Bag.Have> out = new HashMap<>();
		for (int i = 0; i < idQuantityName.length; i += 3)
		{
			String name = (String) idQuantityName[i + 2];
			out.put(name.toLowerCase(Locale.ROOT), new Bag.Have((Integer) idQuantityName[i], (Integer) idQuantityName[i + 1], name));
		}
		return out;
	}

	private static int count(List<Bag.Line> lines, String startOfName)
	{
		int n = 0;
		for (Bag.Line l : lines)
		{
			if (l.name.startsWith(startOfName))
			{
				n += l.quantity;
			}
		}
		return n;
	}

	private static Bag.Provisional of(Bag bag, String boss, String style, Map<String, Bag.Have> have)
	{
		return bag.provisional(boss, style, have, new HashSet<String>(), new HashSet<Integer>(), Bag.SLOTS);
	}

	@Test
	public void theAmountsAreTheWikisAndTheItemsAreThePlayers() throws Exception
	{
		Bag bag = bag();
		Map<String, Bag.Have> owned = have(3024, 100, "Super restore(4)", 6685, 100, "Saradomin brew(4)", 385, 100, "Shark",
			12695, 10, "Super combat potion(4)", 2444, 10, "Ranging potion(4)", 11942, 3, "Ecumenical key", 8013, 5, "Teleport to house");
		Bag.Provisional p = of(bag, "Commander Zilyana", "Ranged", owned);
		assertNotNull(p);
		assertEquals(16, count(p.lines, "Super restore"));
		assertEquals(1, count(p.lines, "Saradomin brew"));
		assertEquals(4, count(p.lines, "Shark"));
		// the potion of the style of the set, not the one of another style
		assertEquals(1, count(p.lines, "Ranging potion"));
		assertEquals(0, count(p.lines, "Super combat"));
		assertEquals(1, count(p.lines, "Ecumenical key"));
		assertTrue(p.lacking.isEmpty());
		// the wiki carries a staff and a shield to change into: said, and not worked out
		assertFalse(p.switches.isEmpty());
		// a piece the set already wears is not a piece to change into
		Set<String> on = new HashSet<>();
		on.add("eldritch nightmare staff");
		assertFalse(p.without(on).switches.contains("Eldritch nightmare staff"));
		assertTrue(p.without(on).switches.contains("Elidinis' ward (f)"));
		on.clear();
		on.add("eldritch nightmare staff (or)");
		assertFalse(p.without(on).switches.contains("Eldritch nightmare staff"));
		// what the fight needs, owned in another dress, is owned
		Bag.Provisional graardor = of(bag, "General Graardor", "Melee", have(99001, 1, "Hammer (or)", 385, 100, "Shark"));
		assertEquals("Hammer (or)", graardor.lines.get(0).name);
		assertTrue(graardor.lines.get(0).owned);
		int slots = 0;
		for (Bag.Line l : p.lines)
		{
			slots += l.slots();
		}
		assertTrue(slots <= Bag.SLOTS);
	}

	@Test
	public void aSpecialItemNotOwnedIsSaidAndNotPutIn() throws Exception
	{
		Bag bag = bag();
		Bag.Provisional p = of(bag, "Commander Zilyana", "Ranged", have(385, 100, "Shark"));
		assertEquals(0, count(p.lines, "Ecumenical key"));
		assertTrue(p.lacking.contains("Ecumenical key"));
		// nothing the player does not own is made up
		assertEquals(0, count(p.lines, "Super restore"));
		assertEquals(4, count(p.lines, "Shark"));
	}

	@Test
	public void whatTheFightNeedsComesFirstAndIsNotTakenTwice() throws Exception
	{
		Bag bag = bag();
		// the gong at Graardor's door: listed though not owned
		Bag.Provisional graardor = of(bag, "General Graardor", "Melee", have(385, 100, "Shark"));
		assertEquals("Hammer", graardor.lines.get(0).name);
		assertTrue(graardor.lines.get(0).needed);
		assertFalse(graardor.lines.get(0).owned);
		// the antifire the dragon's own line takes is not taken again as a kind
		Bag.Provisional dragon = of(bag, "Brutal Black Dragon", "Ranged",
			have(22209, 10, "Extended super antifire(4)", 2452, 10, "Antifire potion(4)", 385, 100, "Shark"));
		assertEquals(1, count(dragon.lines, "Extended super antifire"));
		assertEquals(0, count(dragon.lines, "Antifire potion"));
	}

	@Test
	public void aStackTakesOneSlot() throws Exception
	{
		Bag bag = bag();
		Bag.Provisional p = of(bag, "Lizardman Shaman", "Ranged", have(2, 5000, "Cannonball", 6, 1, "Cannon base"));
		Bag.Line balls = null;
		for (Bag.Line l : p.lines)
		{
			if ("Cannonball".equals(l.name))
			{
				balls = l;
			}
		}
		assertNotNull(balls);
		assertEquals(2000, balls.quantity);
		assertEquals(1, balls.slots());
		assertTrue(p.lacking.contains("Cannon stand"));
		assertFalse(p.lacking.contains("Cannon base"));
		// a cannon with an ornament on it, and cannonballs of another metal, are a cannon and cannonballs
		Bag.Provisional or = of(bag, "Lizardman Shaman", "Ranged", have(31906, 6096, "Steel cannonball", 31900, 12553, "Granite cannonball", 31916, 261, "Rune cannonball",
			26520, 1, "Cannon base (or)", 26522, 1, "Cannon stand (or)", 26524, 1, "Cannon barrels (or)", 26526, 1, "Cannon furnace (or)"));
		assertTrue(or.lacking.toString(), or.lacking.isEmpty());
		assertEquals(2000, count(or.lines, "Granite cannonball"));
		assertEquals(0, count(or.lines, "Steel cannonball"));
		// the other metals are for the cannon of a boat, not for this one
		assertEquals(0, count(or.lines, "Rune cannonball"));
		assertEquals(1, count(or.lines, "Cannon base (or)"));
	}

	@Test
	public void onlyForBossesNotGoneThroughAndInTheTable() throws Exception
	{
		Bag bag = bag();
		Set<String> gone = new HashSet<>();
		for (String boss : new String[]{"Vorkath", "Zulrah", "Dagannoth Rex", "Sarachnis"})
		{
			assertTrue(boss, bag.isMapped(boss));
			assertFalse(boss, bag.isProvisional(boss));
			assertNull(boss, of(bag, boss, "Ranged", have(385, 100, "Shark")));
			gone.add(boss);
		}
		// a boss in neither table has none
		assertFalse(bag.isProvisional("Crystalline Hunllef"));
		assertNull(of(bag, "Crystalline Hunllef", "Ranged", have(385, 100, "Shark")));
		assertTrue(bag.isProvisional("Nex"));
	}

	/** What the audit of October 2026 found in the wiki's inventories (each boss's Strategies page). */
	@Test
	public void theAuditedInventoriesAreTheWikis() throws Exception
	{
		Bag bag = bag();
		// Deranged archaeologist: a Forgotten brew (a Magic boost), not a Saradomin brew, and an axe for the swamp vine
		Bag.Provisional deranged = of(bag, "Deranged Archaeologist", "Magic", have(6685, 10, "Saradomin brew(4)",
			27629, 5, "Forgotten brew(4)", 1359, 1, "Rune axe", 385, 100, "Shark"));
		assertEquals(1, count(deranged.lines, "Forgotten brew"));
		assertEquals(0, count(deranged.lines, "Saradomin brew"));
		assertEquals(1, count(deranged.lines, "Rune axe"));
		Bag.Provisional noAxe = of(bag, "Deranged Archaeologist", "Magic", have(385, 100, "Shark"));
		assertTrue(noAxe.lacking.contains("Axe"));
		assertTrue("a pickaxe is not an axe", of(bag, "Deranged Archaeologist", "Magic",
			have(11920, 1, "Dragon pickaxe", 385, 100, "Shark")).lacking.contains("Axe"));
		// Nex: 13 Saradomin brews and 3 Armadyl brews
		Bag.Provisional nex = of(bag, "Nex", "Ranged", have(6685, 30, "Saradomin brew(4)", 22461, 10, "Armadyl brew(4)",
			3024, 30, "Super restore(4)"));
		assertEquals(13, count(nex.lines, "Saradomin brew"));
		assertEquals(3, count(nex.lines, "Armadyl brew"));
		// Scorpia: a cure for her poison and a Lockpick
		Bag.Provisional scorpia = of(bag, "Scorpia", "Ranged", have(12913, 5, "Anti-venom+(4)", 1523, 1, "Lockpick",
			385, 100, "Shark"));
		assertEquals(2, count(scorpia.lines, "Anti-venom+"));
		assertEquals(1, count(scorpia.lines, "Lockpick"));
		// General Graardor: the wiki's inventory has no Ecumenical key, so none is said to be missing
		assertFalse(of(bag, "General Graardor", "Melee", have(385, 100, "Shark")).lacking.contains("Ecumenical key"));
	}
}
