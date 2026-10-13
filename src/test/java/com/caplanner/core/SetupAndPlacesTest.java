package com.caplanner.core;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;

public class SetupAndPlacesTest
{
	@Test
	public void theSetupTextIsWhatInventorySetupsImports()
	{
		Map<Integer, Integer> set = new HashMap<>();
		set.put(0, 24271);   // head
		set.put(3, 22325);   // weapon
		set.put(13, 22947);  // ammunition slot
		String text = SetupText.of(SetupText.nameFor("Vardorvis"), set);
		JsonObject root = new Gson().fromJson(text, JsonObject.class);
		JsonObject setup = root.getAsJsonObject("setup");
		assertEquals("CA Vardorvis", setup.get("name").getAsString());
		JsonArray eq = setup.getAsJsonArray("eq");
		assertEquals(14, eq.size());
		assertEquals(24271, eq.get(0).getAsJsonObject().get("id").getAsInt());
		assertEquals(22325, eq.get(3).getAsJsonObject().get("id").getAsInt());
		assertTrue("an empty slot is null", eq.get(1).isJsonNull());
		JsonArray inv = setup.getAsJsonArray("inv");
		assertEquals(28, inv.size());
		assertTrue(inv.get(0).isJsonNull());
		JsonArray layout = root.getAsJsonArray("layout");
		assertEquals(56, layout.size());
		assertEquals("the head leads the layout", 24271, layout.get(0).getAsInt());
		assertEquals("the weapon is second in the second row", 22325, layout.get(9).getAsInt());
		assertEquals(22947, layout.get(5).getAsInt());
		assertEquals(-1, layout.get(1).getAsInt());
		assertEquals(-1, layout.get(55).getAsInt());
	}

	@Test
	public void aSetupIsKeptUnderTheKeyInventorySetupsMakesFromItsName()
	{
		// the same hash Inventory Setups takes of a setup's name; the values were worked out separately
		assertEquals("109c70b1bea623847613be39b8d573f8",
			com.google.common.hash.Hashing.murmur3_128().hashUnencodedChars("CA Vardorvis").toString());
		assertEquals("c66da781ca8f304029d618b98a7b5f4f",
			com.google.common.hash.Hashing.murmur3_128().hashUnencodedChars("VARDORVIS NORMAL (PET)").toString());
	}

	@Test
	public void aTaskWithItsOwnSetGetsItsOwnName()
	{
		assertEquals("CA Skotizo", SetupText.nameFor("Skotizo", -1));
		assertEquals("CA Skotizo #48", SetupText.nameFor("Skotizo", 48));
		String longName = SetupText.nameFor("A boss with a very long name that goes on and on and on and on", 617);
		assertTrue(longName.length() <= 50);
		assertTrue(longName.endsWith(" #617"));
		// the whole text is the setup and its layout put together
		Map<Integer, Integer> set = new HashMap<>();
		set.put(3, 22325);
		String whole = SetupText.of("CA X", set);
		assertTrue(whole.contains(SetupText.setup("CA X", set)));
		assertTrue(whole.contains("[" + SetupText.layout(set) + "]"));
		assertEquals(56, SetupText.layout(set).split(",").length);
	}

	@Test
	public void aNameCannotBreakTheText()
	{
		String name = SetupText.nameFor("K'ril \"Tsutsaroth\" \\ and a very long boss name that goes on and on and on");
		assertTrue(name.length() <= 50);
		JsonObject root = new Gson().fromJson(SetupText.of(name, new HashMap<>()), JsonObject.class);
		assertEquals(name, root.getAsJsonObject("setup").get("name").getAsString());
	}

	@Test
	public void placesAreReadAndUnknownBossesHaveNone() throws Exception
	{
		Places p = Places.read(new StringReader("# c\nVardorvis\t1117\t3428\t0\nbad\tline\n\nThe Hueycoatl\t1528\t3289\t0\n"));
		assertArrayEquals(new int[]{1117, 3428, 0}, p.of("Vardorvis"));
		assertArrayEquals(new int[]{1528, 3289, 0}, p.of("the hueycoatl"));
		assertNull(p.of("Zulrah"));
		assertNull(p.of(null));
	}

	@Test
	public void everyPlaceInTheBundledTableIsABossOfTheGame() throws Exception
	{
		java.util.Set<String> bosses = new java.util.HashSet<>();
		try (BufferedReader in = reader("/com/caplanner/bosses.txt"))
		{
			String line;
			while ((line = in.readLine()) != null)
			{
				bosses.add(Requirements.key(line));
			}
		}
		int rows = 0;
		try (BufferedReader in = reader("/com/caplanner/places.tsv"))
		{
			String line;
			while ((line = in.readLine()) != null)
			{
				if (line.trim().isEmpty() || line.startsWith("#"))
				{
					continue;
				}
				String[] cols = line.split("\t");
				assertEquals(line, 4, cols.length);
				assertTrue("not a Combat Achievements boss: " + cols[0], bosses.contains(Requirements.key(cols[0])));
				assertTrue(Integer.parseInt(cols[1]) > 0 && Integer.parseInt(cols[2]) > 0);
				rows++;
			}
		}
		assertTrue(rows > 10);
	}

	private static BufferedReader reader(String resource)
	{
		return new BufferedReader(new InputStreamReader(
			SetupAndPlacesTest.class.getResourceAsStream(resource), StandardCharsets.UTF_8));
	}
}
