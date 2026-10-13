/*
 * Copyright (c) 2026, GuimGod
 * All rights reserved. Licensed under the BSD 2-Clause License (see LICENSE).
 */
package com.caplanner.core;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/** The tiles the plugin can mark, on the table it ships. */
public class TilesTest
{
	private static Tiles tiles() throws Exception
	{
		try (BufferedReader in = new BufferedReader(new InputStreamReader(
			TilesTest.class.getResourceAsStream("/com/caplanner/tiles.tsv"), StandardCharsets.UTF_8)))
		{
			return Tiles.read(in);
		}
	}

	@Test
	public void aBossHasItsTilesAndWhatTheyAre() throws Exception
	{
		Tiles tiles = tiles();
		List<Tiles.Tile> vardorvis = tiles.of("Vardorvis");
		assertEquals(4, vardorvis.size());
		assertEquals(1124, vardorvis.get(0).x);
		assertEquals(3418, vardorvis.get(0).y);
		// the colour keeps its alpha
		assertEquals(0xFF, vardorvis.get(0).colour >>> 24);
		// one line of the legend for each different label
		assertEquals(3, tiles.legend("Vardorvis").size());
		assertEquals("Stand here", tiles.legend("Vardorvis").get(0)[0]);
		assertFalse(tiles.legend("Vardorvis").get(0)[1].isEmpty());
		// tiles with nothing written on them are drawn and not listed
		assertTrue(tiles.of("Whisperer").size() > 100);
		assertTrue(tiles.legend("Whisperer").size() < 10);
		assertEquals(34, tiles.of("Moons of Peril").size());
		// a boss whose tiles nobody of the authors used has none
		assertTrue(tiles.of("Yama").isEmpty());
		assertTrue(tiles.legend("Cerberus").isEmpty());
	}

	@Test
	public void everyTileIsWellFormed() throws Exception
	{
		Tiles tiles = tiles();
		for (String boss : new String[]{"Vardorvis", "Amoxliatl", "Zalcano", "Giant Mole", "Obor", "Phantom Muspah", "Moons of Peril"})
		{
			assertFalse(boss, tiles.of(boss).isEmpty());
			for (Tiles.Tile t : tiles.of(boss))
			{
				assertTrue(boss, t.x > 1000 && t.x < 4000 && t.y > 2500 && t.y < 13000 && t.plane >= 0 && t.plane <= 3);
				assertFalse(boss, t.label.isEmpty());
			}
		}
	}
}
