/*
 * Copyright (c) 2026, GuimGod
 * All rights reserved. Licensed under the BSD 2-Clause License (see LICENSE).
 */
package com.caplanner.core;

import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/** The way out ("leave" lines) and the fairy rings among the ways to a boss ("arrive" lines, in order). */
public class BagLeaveTest
{
	private static final String TABLE = "Zulrah\tmapped\n"
		+ "Zulrah\tarrive\t1\tzul-andra teleport\tthe way there\n"
		+ "Zulrah\tarrive\t1\tdramen staff|lunar staff\tthe fairy ring\tlumbridge-elite\n"
		+ "Kraken\tmapped\n"
		+ "Kraken\tarrive\t1\tdramen staff|lunar staff\tthe fairy ring\tlumbridge-elite\n"
		+ "Scorpia\tmapped\n"
		+ "Scorpia\twithout\t0\tmax cape\n"
		+ "*\tleave\t1\tmax cape|teleport to house|~ring of dueling(|varrock teleport|~ring of wealth (\tthe way out\n"
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

	private static List<Bag.Line> of(Bag bag, String boss, Map<String, Bag.Have> owned, Set<String> done)
	{
		return bag.of(boss, "ranged", owned, done, Collections.<Integer>emptySet());
	}

	@Test
	public void theStaffIsCarriedOnlyWhenNoOtherWayThereIsOwned() throws Exception
	{
		Bag bag = Bag.read(new StringReader(TABLE));
		Set<String> none = new HashSet<>();
		Map<String, Bag.Have> both = have(12938, 5, "Zul-andra teleport", 772, 1, "Dramen staff", 385, 100, "Shark");
		List<Bag.Line> lines = of(bag, "Zulrah", both, none);
		assertNotNull(line(lines, "Zul-andra teleport"));
		assertNull("the scroll is the way there: no staff", line(lines, "Dramen staff"));

		Map<String, Bag.Have> staff = have(772, 1, "Dramen staff", 385, 100, "Shark");
		assertNotNull(line(of(bag, "Zulrah", staff, none), "Dramen staff"));
		assertTrue(bag.wayThereNotOwned("Zulrah", staff, none).isEmpty());
	}

	@Test
	public void theDiaryMakesTheStaffNeedless() throws Exception
	{
		Bag bag = Bag.read(new StringReader(TABLE));
		Set<String> diary = new HashSet<>(Collections.singleton("lumbridge-elite"));
		Map<String, Bag.Have> staff = have(772, 1, "Dramen staff", 385, 100, "Shark");
		assertNull(line(of(bag, "Kraken", staff, diary), "Dramen staff"));
		// and with nothing owned the fairy ring is still a way there
		Map<String, Bag.Have> nothing = have(385, 100, "Shark");
		assertTrue(bag.wayThereNotOwned("Kraken", nothing, diary).isEmpty());
		assertEquals(2, bag.wayThereNotOwned("Kraken", nothing, new HashSet<String>()).size());
	}

	@Test
	public void theWayOutIsTheBestOneOwnedDownToATabletOrARing() throws Exception
	{
		Bag bag = Bag.read(new StringReader(TABLE));
		Set<String> none = new HashSet<>();
		Map<String, Bag.Have> owned = have(8007, 20, "Varrock teleport", 11980, 1, "Ring of wealth (5)", 385, 100, "Shark");
		List<Bag.Line> lines = of(bag, "Kraken", owned, none);
		assertNotNull("the tablet comes before the ring of wealth", line(lines, "Varrock teleport"));
		assertNull(line(lines, "Ring of wealth (5)"));
		assertFalse(bag.noWayOut("Kraken", owned));
	}

	@Test
	public void withNoWayOutOwnedItIsSaid() throws Exception
	{
		Bag bag = Bag.read(new StringReader(TABLE));
		Map<String, Bag.Have> nothing = have(385, 100, "Shark");
		assertTrue(bag.noWayOut("Kraken", nothing));
		// deep Wilderness has its own rule: the usual way out is left out there, and nothing is said of it
		assertFalse(bag.noWayOut("Scorpia", nothing));
	}

	@Test
	public void theTableShippedHasAWayOutForEveryBossAndTheStaffForTheKraken() throws Exception
	{
		try (Reader in = new InputStreamReader(Bag.class.getResourceAsStream("/com/caplanner/bag.tsv"), StandardCharsets.UTF_8))
		{
			Bag bag = Bag.read(in);
			Map<String, Bag.Have> nothing = have(385, 100, "Shark");
			assertTrue(bag.noWayOut("Vorkath", nothing));
			assertFalse(bag.noWayOut("Vorkath", have(2552, 1, "Ring of dueling(8)", 385, 100, "Shark")));
			assertFalse("deep Wilderness keeps its own rule", bag.noWayOut("Chaos Fanatic", nothing));
			Map<String, Bag.Have> staff = have(772, 1, "Dramen staff", 385, 100, "Shark");
			assertNotNull(line(of(bag, "Kraken", staff, new HashSet<String>()), "Dramen staff"));
		}
	}

	@Test
	public void theFairyRingStandsWhereTheWikiPutsItAmongTheWaysThere() throws Exception
	{
		try (Reader in = new InputStreamReader(Bag.class.getResourceAsStream("/com/caplanner/bag.tsv"), StandardCharsets.UTF_8))
		{
			Bag bag = Bag.read(in);
			Set<String> none = new HashSet<>();
			Set<String> diary = new HashSet<>(Collections.singleton("lumbridge-elite"));
			Map<String, Bag.Have> both = have(772, 1, "Dramen staff", 2552, 1, "Ring of dueling(8)", 13393, 1, "Xeric's talisman",
				12938, 5, "Zul-andra teleport", 385, 100, "Shark");
			// the smoke devil: the fairy ring is the quickest, so the staff goes and the ring is not taken as a way there
			assertNotNull(line(of(bag, "Thermonuclear Smoke Devil", both, none), "Dramen staff"));
			// Vardorvis: with no ring of shadows the fairy ring comes before the talisman
			List<Bag.Line> vardorvis = of(bag, "Vardorvis", both, none);
			assertNotNull(line(vardorvis, "Dramen staff"));
			assertNull(line(vardorvis, "Xeric's talisman"));
			// Zulrah: the scroll comes first
			List<Bag.Line> zulrah = of(bag, "Zulrah", both, none);
			assertNotNull(line(zulrah, "Zul-andra teleport"));
			assertNull(line(zulrah, "Dramen staff"));
			// with the diary the fairy ring needs no item: at Vardorvis nothing is carried for the way there
			List<Bag.Line> withDiary = of(bag, "Vardorvis", both, diary);
			assertNull(line(withDiary, "Dramen staff"));
			assertNull(line(withDiary, "Xeric's talisman"));
		}
	}
}
