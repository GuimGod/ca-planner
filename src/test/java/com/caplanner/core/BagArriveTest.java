/*
 * Copyright (c) 2026, GuimGod
 * All rights reserved. Licensed under the BSD 2-Clause License (see LICENSE).
 */
package com.caplanner.core;

import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/** The teleport that takes the player to the boss ("arrive" lines of the inventory table). */
public class BagArriveTest
{
	private static final String TABLE = "Skotizo\tmapped\n"
		+ "Skotizo\tarrive\t1\txeric's talisman|~ring of dueling(\tthe way there\n"
		+ "Kraken\tmapped\n"
		+ "*\tfood\t0\tshark\n";

	private static Map<String, Bag.Have> have(Object... idQuantityName)
	{
		Map<String, Bag.Have> out = new HashMap<>();
		for (int i = 0; i < idQuantityName.length; i += 3)
		{
			String name = (String) idQuantityName[i + 2];
			out.put(name.toLowerCase(), new Bag.Have((Integer) idQuantityName[i], (Integer) idQuantityName[i + 1], name));
		}
		return out;
	}

	private static Bag.Line line(List<Bag.Line> lines, String name)
	{
		for (Bag.Line l : lines)
		{
			if (l.name.equals(name))
			{
				return l;
			}
		}
		return null;
	}

	@Test
	public void theBestTeleportOwnedIsCarriedAndOnlyOne() throws Exception
	{
		Bag bag = Bag.read(new StringReader(TABLE));
		Map<String, Bag.Have> owned = have(13393, 1, "Xeric's talisman", 2552, 3, "Ring of dueling(8)", 385, 100, "Shark");
		List<Bag.Line> lines = bag.of("Skotizo", "melee", owned);
		assertNotNull(line(lines, "Xeric's talisman"));
		assertEquals(1, line(lines, "Xeric's talisman").quantity);
		assertNull("one way there is enough", line(lines, "Ring of dueling(8)"));
		assertTrue(bag.wayThereNotOwned("Skotizo", owned).isEmpty());
	}

	@Test
	public void theNextOneIsCarriedWhenTheBestIsNotOwned() throws Exception
	{
		Bag bag = Bag.read(new StringReader(TABLE));
		Map<String, Bag.Have> owned = have(2552, 3, "Ring of dueling(8)", 385, 100, "Shark");
		assertNotNull(line(bag.of("Skotizo", "melee", owned), "Ring of dueling(8)"));
		assertTrue(bag.wayThereNotOwned("Skotizo", owned).isEmpty());
	}

	@Test
	public void withNoneOwnedNothingIsMadeUpAndTheNamesAreSaid() throws Exception
	{
		Bag bag = Bag.read(new StringReader(TABLE));
		Map<String, Bag.Have> owned = have(385, 100, "Shark");
		List<Bag.Line> lines = bag.of("Skotizo", "melee", owned);
		assertNull(line(lines, "Xeric's talisman"));
		assertEquals(Arrays.asList("Xeric's talisman", "Ring of dueling"), bag.wayThereNotOwned("Skotizo", owned));
	}

	@Test
	public void aBossWithNoLineSaysNothing() throws Exception
	{
		Bag bag = Bag.read(new StringReader(TABLE));
		assertTrue(bag.wayThereNotOwned("Kraken", have(385, 100, "Shark")).isEmpty());
	}

	@Test
	public void theTableShippedNamesAWayToSkotizoAndToCerberus() throws Exception
	{
		try (Reader in = new InputStreamReader(Bag.class.getResourceAsStream("/com/caplanner/bag.tsv"), StandardCharsets.UTF_8))
		{
			Bag bag = Bag.read(in);
			Map<String, Bag.Have> nothing = have(385, 100, "Shark");
			assertEquals(Arrays.asList("Xeric's talisman"), bag.wayThereNotOwned("Skotizo", nothing));
			assertEquals(Arrays.asList("Key master teleport"), bag.wayThereNotOwned("Cerberus", nothing));
			Map<String, Bag.Have> scroll = have(13249, 5, "Key master teleport", 385, 100, "Shark");
			assertTrue(bag.wayThereNotOwned("Cerberus", scroll).isEmpty());
		}
	}
}
