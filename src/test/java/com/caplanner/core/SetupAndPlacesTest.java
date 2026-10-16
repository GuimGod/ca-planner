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
	public void aLineWithALabelIsAnotherWayNotTheBossItself() throws Exception
	{
		Places p = Places.read(new StringReader("Bryophyta\t3220\t9933\t0\n"
			+ "Bryophyta\t3159\t9901\t0\tMark the way to the mossy keys\tMoss giants\nObor\t3096\t9833\t0\n"));
		assertArrayEquals(new int[]{3220, 9933, 0}, p.of("Bryophyta"));
		assertEquals(1, p.ways("bryophyta").size());
		assertEquals("Mark the way to the mossy keys", p.ways("Bryophyta").get(0).label);
		assertEquals("Moss giants", p.ways("Bryophyta").get(0).note);
		assertArrayEquals(new int[]{3159, 9901, 0}, p.ways("Bryophyta").get(0).tile());
		assertTrue(p.ways("Obor").isEmpty());
		assertTrue(p.ways(null).isEmpty());
	}

	@Test
	public void aLineWithASecondTileIsARouteThatStopsShort() throws Exception
	{
		Places p = Places.read(new StringReader("Zulrah\t2215\t3057\t0\t2270\t3074\t0\tBoard the boat.\n"
			+ "Vardorvis\t1117\t3428\t0\n"));
		assertArrayEquals("the route goes to the last tile it reaches", new int[]{2215, 3057, 0}, p.of("Zulrah"));
		assertArrayEquals("the boss's own tile is kept", new int[]{2270, 3074, 0}, p.inside("zulrah"));
		assertEquals("Board the boat.", p.note("Zulrah"));
		assertTrue("not another way to a place", p.ways("Zulrah").isEmpty());
		assertNull(p.inside("Vardorvis"));
		assertEquals("", p.note("Vardorvis"));
		assertNull(p.inside(null));
	}

	/**
	 * The audit of October 2026: tiles the route plugins cannot reach go to the last one they do, with a
	 * note in both languages (the God Wars Dungeon's generals and Corp too); the Colosseum is its entrance; the Desert
	 * Treasure II bosses, Skotizo and the Inferno have a way now.
	 */
	@Test
	public void theBundledTableSendsTheRouteOnlyWhereItCanGo() throws Exception
	{
		Places p;
		try (BufferedReader in = reader("/com/caplanner/places.tsv"))
		{
			p = Places.read(in);
		}
		Lang pt;
		try (BufferedReader in = reader("/com/caplanner/lang/pt.tsv"))
		{
			pt = Lang.read(in);
		}
		String[][] stopsShort = {
			{"Zulrah", "2215,3057,0", "2270,3074,0"},
			{"Araxxor", "3656,3408,0", "3632,9815,0"},
			{"Hespori", "1231,3728,0", "1248,10089,0"},
			{"Vorkath", "2272,4053,0", "2269,4062,0"},
			{"Thermonuclear Smoke Devil", "2412,3061,0", "2362,9456,0"},
			{"Fire Giant", "2309,2919,0", "2121,9330,0"},
			{"Greater Demon", "2309,2919,0", "2158,9336,0"},
			{"Tormented Demon", "3245,9500,2", "4136,4372,0"},
			{"Demonic Gorilla", "2436,3520,0", "2072,5651,0"},
			{"Alchemical Hydra", "1311,3807,0", "1368,10270,0"},
			{"Wyrm", "1311,3807,0", "1263,10158,0"},
			{"Moons of Peril", "1439,9550,1", "1485,9632,0"},
			// the route plugins go down the hole, but not through the doors of the generals' rooms
			{"General Graardor", "2914,3744,0", "2872,5358,2"},
			{"K'ril Tsutsaroth", "2914,3744,0", "2925,5322,2"},
			{"Kree'arra", "2914,3744,0", "2832,5302,2"},
			// where the games necklace lands (both route plugins), not the lair north of it
			{"Corporeal Beast", "2965,4254,2", "2993,4254,2"},
		};
		for (String[] row : stopsShort)
		{
			assertEquals(row[0], row[1], tile(p.of(row[0])));
			assertEquals(row[0], row[2], tile(p.inside(row[0])));
			String note = p.note(row[0]);
			assertTrue(row[0], note.startsWith("The route goes as far as "));
			assertTrue("said in Portuguese too: " + row[0], pt.of(note).startsWith("A rota vai até "));
		}
		// the Desert Treasure II bosses, Skotizo and the Inferno: the last tile before each instance
		assertEquals("2064,6436,0", tile(p.of("Leviathan")));
		assertEquals("2588,6435,0", tile(p.of("Whisperer")));
		assertEquals("2912,10336,0", tile(p.of("Duke Sucellus")));
		assertEquals("1639,3673,0", tile(p.of("Skotizo")));
		assertEquals("2496,5122,0", tile(p.of("TzKal-Zuk")));
		assertEquals("1795,3107,0", tile(p.of("Fortis Colosseum")));
		assertNull("the route goes all the way", p.inside("Fortis Colosseum"));
		assertEquals("a tile we measured stays", "1641,3222,0", tile(p.of("Amoxliatl")));
	}

	private static String tile(int[] t)
	{
		return t == null ? null : t[0] + "," + t[1] + "," + t[2];
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
				// four columns for the way to the boss; six (a label and a note more) for another place of its;
				// eight (the boss's own tile and a note) when the route stops short of the boss
				assertTrue(line, cols.length == 4 || cols.length == 6 || cols.length == 8);
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
