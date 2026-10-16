package com.caplanner.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.Test;

public class BagTest
{
	private static final String TABLE = "# a comment\n"
		+ "Gargoyle\tneed\t1\trock hammer=4162|rock thrownhammer\tto finish it off\n"
		+ "Vorkath\ttake\t1\textended anti-venom+|anti-venom+\tvenom\n"
		+ "Wintertodt\tonly\n"
		+ "Wintertodt\ttake\t1\tknife\tfletching\n"
		+ "*melee\ttake\t1\tdivine super combat potion|super combat potion\n"
		+ "*ranged\ttake\t1\tdivine ranging potion|ranging potion\n"
		+ "*\ttake\t4\tsuper restore|prayer potion\n"
		+ "*\ttake\t5s\tteleport to house\tthe way out\n"
		+ "*\ttake\t1\t~crossbow\n"
		+ "*\tfood\t0\tanglerfish|shark\n"
		+ "bad line\n";

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
	public void aPlaceholderIsOwnedButNotInStock() throws Exception
	{
		Bag bag = Bag.read(new StringReader(TABLE + "Smoke Devil\twear\t1\tslayer helmet|facemask=4164\tthe smoke\n"
			+ "Gargoyle\tmapped\n"));
		// kept as placeholders (quantity 0): a facemask, a rock hammer, sharks
		Map<String, Bag.Have> kept = have(4164, 0, "Facemask", 4162, 0, "Rock hammer", 385, 0, "Shark", 2434, 4, "Prayer potion(4)");
		Set<String> none = new HashSet<>();
		assertTrue("the set can wear it: nothing is missing", bag.missing("Smoke Devil", kept, none).isEmpty());
		assertTrue(bag.missing("Gargoyle", kept, none).isEmpty());
		List<Bag.Line> lines = bag.of("Gargoyle", "Melee", kept, none, new HashSet<Integer>());
		// none in stock: the hammer the fight needs is listed as still to get, and no shark is taken
		assertFalse(line(lines, "Rock hammer").owned);
		assertNull(line(lines, "Shark"));
		assertNotNull(line(lines, "Prayer potion(4)"));
		// a placeholder the set wears is not listed again
		Set<Integer> worn = new HashSet<>();
		worn.add(4162);
		assertNull(line(bag.of("Gargoyle", "Melee", kept, none, worn), "Rock hammer"));
	}

	@Test
	public void suppliesAreOnlyOnesThePlayerOwns() throws Exception
	{
		Bag bag = Bag.read(new StringReader(TABLE));
		Map<String, Bag.Have> owned = have(2440, 3, "Super combat potion(4)", 2434, 10, "Prayer potion(4)",
			385, 100, "Shark", 8013, 2, "Teleport to house");
		List<Bag.Line> lines = bag.of("Sarachnis", "Melee", owned);
		// the better potion is not owned: the one that is owned is the one listed
		assertNotNull(line(lines, "Super combat potion(4)"));
		assertNull(line(lines, "Divine super combat potion"));
		assertNull("another style's potion", line(lines, "Ranging potion(4)"));
		assertEquals(4, line(lines, "Prayer potion(4)").quantity);
		// no more than is owned, and a stack takes one slot
		assertEquals(2, line(lines, "Teleport to house").quantity);
		assertEquals(1, line(lines, "Teleport to house").slots());
		// food fills what is left
		assertEquals(Bag.SLOTS - 1 - 4 - 1, line(lines, "Shark").quantity);
		int slots = 0;
		for (Bag.Line l : lines)
		{
			assertTrue(l.owned);
			assertFalse(l.needed);
			slots += l.slots();
		}
		assertEquals(Bag.SLOTS, slots);
		assertEquals(Bag.SLOTS, Bag.slots(lines).size());
		// with nothing owned there is nothing to list
		assertTrue(bag.of("Sarachnis", "Melee", new HashMap<>()).isEmpty());
	}

	@Test
	public void whatTheFightNeedsIsListedEvenWhenNotOwned() throws Exception
	{
		Bag bag = Bag.read(new StringReader(TABLE));
		List<Bag.Line> lines = bag.of("Gargoyle", "Melee", have(385, 5, "Shark"));
		Bag.Line hammer = lines.get(0);
		assertEquals("Rock hammer", hammer.name);
		assertEquals(4162, hammer.itemId);
		assertTrue(hammer.needed);
		assertFalse(hammer.owned);
		assertEquals("to finish it off", hammer.why);
		assertEquals("no more food than is owned", 5, line(lines, "Shark").quantity);
		// the alternative that is owned is the one listed
		List<Bag.Line> thrown = bag.of("Gargoyle", "Melee", have(21754, 1, "Rock thrownhammer"));
		assertEquals("Rock thrownhammer", thrown.get(0).name);
		assertTrue(thrown.get(0).owned && thrown.get(0).needed);
		// what is merely useful is left out when not owned
		assertTrue(bag.of("Vorkath", "Ranged", new HashMap<>()).isEmpty());
	}

	@Test
	public void dosesAndNamesInPart() throws Exception
	{
		Bag bag = Bag.read(new StringReader(TABLE));
		// the fullest dose owned; a name in part matches whatever has it
		Map<String, Bag.Have> owned = have(12915, 1, "Anti-venom+(2)", 12913, 1, "Anti-venom+(4)", 9185, 1, "Rune crossbow");
		List<Bag.Line> lines = bag.of("Vorkath", "Ranged", owned);
		assertEquals("Anti-venom+(4)", lines.get(0).name);
		assertNotNull(line(lines, "Rune crossbow"));
		// a part of the thing is not the thing
		assertNull(line(bag.of("Vorkath", "Ranged", have(9438, 5, "Crossbow string")), "Crossbow string"));
	}

	@Test
	public void aLineIsDroppedWhenWhatMakesItNeedlessIsDone() throws Exception
	{
		Bag bag;
		try (BufferedReader in = reader("/com/caplanner/bag.tsv"))
		{
			bag = Bag.read(in);
		}
		List<Bag.Line> before = bag.of("Kalphite Queen", "melee", new HashMap<>());
		assertEquals("Rope", before.get(0).name);
		assertEquals(2, before.get(0).quantity);
		assertFalse(before.get(0).owned);
		Set<String> done = new HashSet<>();
		done.add("desert-hard");
		assertTrue(bag.of("Kalphite Queen", "melee", new HashMap<>(), done, new HashSet<>()).isEmpty());
		// the skilling bosses take no potions or food
		assertTrue(bag.of("Wintertodt", "melee", have(385, 100, "Shark")).isEmpty());
		assertEquals("Any pickaxe", bag.of("Zalcano", "melee", have(385, 100, "Shark")).get(0).name);
	}

	@Test
	public void whenTheBestIsScarceTheNextMakesUpTheAmount() throws Exception
	{
		Bag bag = Bag.read(new StringReader(TABLE));
		Map<String, Bag.Have> owned = have(3024, 1, "Super restore(4)", 3026, 1, "Super restore(3)", 2434, 10, "Prayer potion(4)",
			13441, 2, "Anglerfish", 385, 100, "Shark");
		List<Bag.Line> lines = bag.of("Sarachnis", "Melee", owned);
		assertEquals(1, line(lines, "Super restore(4)").quantity);
		assertEquals(1, line(lines, "Super restore(3)").quantity);
		assertEquals(2, line(lines, "Prayer potion(4)").quantity);
		assertEquals(2, line(lines, "Anglerfish").quantity);
		assertEquals(Bag.SLOTS - 4 - 2, line(lines, "Shark").quantity);
	}

	@Test
	public void whatIsMissingOfANeedIsListedAndWhatTheSetWearsIsNot() throws Exception
	{
		Bag bag;
		try (BufferedReader in = reader("/com/caplanner/bag.tsv"))
		{
			bag = Bag.read(in);
		}
		// one rope of the two: one owned, one missing
		List<Bag.Line> ropes = bag.of("Kalphite Queen", "melee", have(954, 1, "Rope"));
		assertTrue(ropes.get(0).owned && ropes.get(0).quantity == 1);
		assertTrue(!ropes.get(1).owned && ropes.get(1).needed && ropes.get(1).quantity == 1 && ropes.get(1).itemId == 954);
		// the set wields the crossbow: the grapple's crossbow is not asked for again, nor carried twice
		Map<String, Bag.Have> owned = have(9419, 1, "Mith grapple", 11785, 1, "Armadyl crossbow", 21285, 1, "Infernal max cape",
			4734, 1, "Karil's crossbow");
		Set<Integer> wearing = new HashSet<>();
		wearing.add(11785);
		wearing.add(21285);
		List<Bag.Line> worn = bag.of("Kree'arra", "ranged", owned, new HashSet<>(), wearing);
		assertEquals(1, worn.size());
		assertEquals("Mith grapple", worn.get(0).name);
		// not wielded: it is carried, and a crossbow that fires no grapple is not it
		wearing.remove(11785);
		List<Bag.Line> carried = bag.of("Kree'arra", "ranged", owned, new HashSet<>(), wearing);
		assertEquals("Armadyl crossbow", carried.get(1).name);
		assertEquals(2, carried.size());
		// a cape of the same kind under another name is the way out all the same
		wearing.clear();
		assertNotNull(line(bag.of("Sarachnis", "melee", owned, new HashSet<>(), wearing), "Infernal max cape"));
	}

	@Test
	public void aBossCanTakeNoneOfTheUsualSupplies() throws Exception
	{
		Bag bag = Bag.read(new StringReader(TABLE));
		List<Bag.Line> lines = bag.of("Wintertodt", "Melee", have(946, 1, "Knife", 385, 100, "Shark", 2434, 10, "Prayer potion(4)"));
		assertEquals(1, lines.size());
		assertEquals("Knife", lines.get(0).name);
		Set<String> bosses = bag.bosses();
		assertTrue(bosses.contains("gargoyle") && bosses.contains("wintertodt") && bosses.size() == 3);
	}

	@Test
	public void theInventoryGoesIntoTheSetupAndItsLayout() throws Exception
	{
		Bag bag = Bag.read(new StringReader(TABLE));
		List<Bag.Line> lines = bag.of("Gargoyle", "Melee", have(2434, 10, "Prayer potion(4)", 8013, 9, "Teleport to house", 385, 2, "Shark"));
		List<int[]> slots = Bag.slots(lines);
		Map<Integer, Integer> set = new HashMap<>();
		set.put(3, 4151);
		JsonObject root = new Gson().fromJson(SetupText.of("CA Gargoyle", set, slots), JsonObject.class);
		JsonArray inv = root.getAsJsonObject("setup").getAsJsonArray("inv");
		assertEquals(28, inv.size());
		assertEquals(4162, inv.get(0).getAsJsonObject().get("id").getAsInt());
		assertFalse("one of them: no quantity", inv.get(0).getAsJsonObject().has("q"));
		assertEquals(2434, inv.get(1).getAsJsonObject().get("id").getAsInt());
		assertEquals(8013, inv.get(5).getAsJsonObject().get("id").getAsInt());
		assertEquals(5, inv.get(5).getAsJsonObject().get("q").getAsInt());
		assertEquals(385, inv.get(7).getAsJsonObject().get("id").getAsInt());
		assertTrue(inv.get(8).isJsonNull());
		// under the two rows of equipment, going down each column before the next
		JsonArray layout = root.getAsJsonArray("layout");
		assertEquals(56, layout.size());
		assertEquals(4151, layout.get(9).getAsInt());
		assertEquals(4162, layout.get(16).getAsInt());
		assertEquals(2434, layout.get(24).getAsInt());
		assertEquals(2434, layout.get(17).getAsInt());
		assertEquals(8013, layout.get(26).getAsInt());
		assertEquals(385, layout.get(27).getAsInt());
		assertEquals(-1, layout.get(20).getAsInt());
		// a full inventory fills four rows, six columns of the last two
		List<int[]> full = new java.util.ArrayList<>();
		for (int i = 0; i < 28; i++)
		{
			full.add(new int[]{1000 + i, 1});
		}
		String[] cells = SetupText.layout(set, full).split(",");
		assertEquals("1016", cells[32]);
		assertEquals("1017", cells[40]);
		assertEquals("1027", cells[45]);
		assertEquals("-1", cells[46]);
		assertEquals("-1", cells[48]);
		// the setup without an inventory is as it was
		assertEquals(SetupText.of("CA X", set), SetupText.of("CA X", set, new java.util.ArrayList<>()));
	}

	/** The bundled table names only bosses of the game, and gives an item id to whatever it lists as needed. */
	@Test
	public void theBundledTableIsSound() throws Exception
	{
		Set<String> known = new HashSet<>();
		try (BufferedReader in = reader("/com/caplanner/bosses.txt"))
		{
			String line;
			while ((line = in.readLine()) != null)
			{
				known.add(Requirements.key(line));
			}
		}
		Bag bag;
		try (BufferedReader in = reader("/com/caplanner/bag.tsv"))
		{
			bag = Bag.read(in);
		}
		for (String boss : bag.bosses())
		{
			assertTrue("not a Combat Achievements boss: " + boss, known.contains(boss));
			for (Bag.Line line : bag.of(boss, "melee", new HashMap<>()))
			{
				assertTrue(boss + ": " + line.name, line.needed && line.itemId > 0 && !line.why.isEmpty());
			}
		}
		assertTrue(bag.bosses().size() >= 8);
		// an inventory only for the fights that were gone through
		assertTrue(bag.isMapped("Vorkath") && bag.isMapped("the nightmare") && bag.isMapped("Dagannoth Rex"));
		assertFalse(bag.isMapped("Deranged Archaeologist"));
		assertFalse(bag.isMapped("Corrupted Hunllef"));
		// every style has its own potion among the usual supplies
		int lines = 0;
		try (BufferedReader in = reader("/com/caplanner/bag.tsv"))
		{
			String line;
			while ((line = in.readLine()) != null)
			{
				if (!line.trim().isEmpty() && !line.startsWith("#"))
				{
					String[] c = line.split("\t");
					assertTrue(line, c.length == 2 ? "only".equals(c[1]) || "mapped".equals(c[1]) : c.length >= 4);
					lines++;
				}
			}
		}
		assertTrue(lines > 15);
	}

	private static BufferedReader reader(String resource)
	{
		return new BufferedReader(new InputStreamReader(BagTest.class.getResourceAsStream(resource), StandardCharsets.UTF_8));
	}
}
