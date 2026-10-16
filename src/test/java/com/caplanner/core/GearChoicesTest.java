package com.caplanner.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.caplanner.gear.Fights;
import com.caplanner.gear.Gear;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import org.junit.Test;

/** The player's changes to the recommended gear as they are kept: per boss, per place, and read back whatever the text. */
public class GearChoicesTest
{
	private static final String PART = GearChoices.part("Special attack|ranged|ranged");

	@Test
	public void changesAreReadBackAsTheyWereKept()
	{
		GearChoices c = GearChoices.none()
			.with("Vorkath", GearChoices.WORN, 12, 25975, 1, 28310)
			.with("Vorkath", GearChoices.BAG, 3, 385, 1, 2434)
			.with("Zulrah", PART, 3, 12926, 1, 0)
			.with("Vorkath", GearChoices.WORN, 3, GearChoices.NOTHING, 1, 20997);
		GearChoices back = GearChoices.decode(c.encode());
		assertEquals(c.encode(), back.encode());
		List<GearChoices.Pin> vorkath = back.of("Vorkath");
		assertEquals(3, vorkath.size());
		assertEquals("in the order they were made", 12, vorkath.get(0).slot);
		assertEquals(GearChoices.NOTHING, vorkath.get(2).itemId);
		assertEquals(20997, vorkath.get(2).recommended);
		assertEquals("Special attack|ranged|ranged", back.of("Zulrah").get(0).partKey());
		// a change made after reading goes after the ones read
		GearChoices more = back.with("Vorkath", GearChoices.WORN, 0, 11865, 1, 24271);
		List<GearChoices.Pin> after = more.of("Vorkath");
		assertEquals(0, after.get(after.size() - 1).slot);
	}

	@Test
	public void aChangeIsOfItsBossAndItsPlaceOnly()
	{
		// case 1: the Kings are three bosses; a part's place is not what is worn
		GearChoices c = GearChoices.none().with("Dagannoth Rex", GearChoices.WORN, 12, 25975, 1, 0)
			.with("Zulrah", PART, 3, 12926, 1, 0);
		assertTrue(c.of("Dagannoth Prime").isEmpty());
		assertTrue(c.of("Dagannoth Supreme").isEmpty());
		assertNull(c.at("Zulrah", GearChoices.WORN, 3));
		assertNull(c.at("Zulrah", GearChoices.part("Special attack|melee|melee"), 3));
		assertNotNull(c.at("Zulrah", PART, 3));
	}

	@Test
	public void theSamePlaceChangedAgainKeepsTheLatestAndOneCanBeUndone()
	{
		GearChoices c = GearChoices.none().with("Vorkath", GearChoices.WORN, 12, 25975, 1, 28310)
			.with("Vorkath", GearChoices.WORN, 12, 11773, 1, 28310);
		assertEquals(1, c.of("Vorkath").size());
		assertEquals(11773, c.at("Vorkath", GearChoices.WORN, 12).itemId);
		GearChoices undone = c.without("Vorkath", GearChoices.WORN, 12);
		assertTrue(undone.isEmpty());
		assertEquals("", undone.encode());
		assertSame("nothing to undo leaves it as it is", undone, undone.without("Vorkath", GearChoices.WORN, 12));
		GearChoices two = c.with("Zulrah", PART, 3, 12926, 1, 0);
		assertEquals(1, two.withoutBoss("Vorkath").bosses().size());
		assertTrue(two.withoutBoss("Vorkath").of("Vorkath").isEmpty());
	}

	@Test
	public void aChangeThereIsNoPlaceForIsNotKept()
	{
		GearChoices none = GearChoices.none();
		// slots 6, 8 and 11 are not equipment slots; the inventory has 28
		assertTrue(none.with("Vorkath", GearChoices.WORN, 6, 1, 1, 0).isEmpty());
		assertTrue(none.with("Vorkath", GearChoices.WORN, 14, 1, 1, 0).isEmpty());
		assertTrue(none.with("Vorkath", GearChoices.BAG, 28, 1, 1, 0).isEmpty());
		assertTrue(none.with("Vorkath", "elsewhere", 3, 1, 1, 0).isEmpty());
		assertTrue(none.with("Vorkath", GearChoices.part(""), 3, 1, 1, 0).isEmpty());
		assertTrue(none.with("", GearChoices.WORN, 3, 1, 1, 0).isEmpty());
		assertTrue(none.with("Vorkath", GearChoices.WORN, 3, -5, 1, 0).isEmpty());
		// and a boss with too many changes takes no new one, but a change of one it has
		GearChoices many = none;
		for (int i = 0; i < GearChoices.MOST_PER_BOSS; i++)
		{
			many = many.with("Vorkath", GearChoices.part("p" + i), 3, 1, 1, 0);
		}
		assertSame(many, many.with("Vorkath", GearChoices.WORN, 3, 1, 1, 0));
		assertEquals(5, many.with("Vorkath", GearChoices.part("p0"), 3, 5, 1, 0).at("Vorkath", GearChoices.part("p0"), 3).itemId);
	}

	@Test
	public void aTextThatIsNotRightGivesNoChangeAndNeverThrows()
	{
		// case 13: corrupted, of another version, far too long
		assertTrue(GearChoices.decode(null).isEmpty());
		assertTrue(GearChoices.decode("").isEmpty());
		assertTrue(GearChoices.decode("garbage").isEmpty());
		assertTrue(GearChoices.decode("v2\nVorkath\tworn\t12\t25975\t1\t0\t1").isEmpty());
		StringBuilder huge = new StringBuilder("v1");
		while (huge.length() <= 4 * GearChoices.MOST_TEXT)
		{
			huge.append("\nVorkath\tworn\t12\t25975\t1\t0\t1");
		}
		assertTrue(GearChoices.decode(huge.toString()).isEmpty());
		// a bad line is left out, the good ones kept
		GearChoices some = GearChoices.decode("v1\nVorkath\tworn\t12\t25975\t1\t28310\t1\nVorkath\tworn\tx\t1\t1\t0\t2\n"
			+ "Vorkath\tworn\t6\t1\t1\t0\t3\nZulrah\tbag\t40\t1\t1\t0\t4\nZulrah\tbag\t2\t1\t0\t0\t5\nZulrah\tbag\t2\t1\t1\t-7\t6\n"
			+ "\tworn\t3\t1\t1\t0\t7\nVorkath\tworn\t3\t1\t1\t0\nVorkath\tworn\t0\t11865\t1\t0\t-1\n\n");
		assertEquals(1, some.of("Vorkath").size());
		assertTrue(some.of("Zulrah").isEmpty());
		Random random = new Random(7);
		for (int i = 0; i < 2000; i++)
		{
			StringBuilder s = new StringBuilder(random.nextBoolean() ? "v1\n" : "");
			for (int n = random.nextInt(200); n > 0; n--)
			{
				s.append("\t\n\\v1x0-9 worn bag part:".charAt(random.nextInt(24)));
			}
			GearChoices.decode(s.toString());
		}
	}

	@Test
	public void namesWithTabsAndNewLinesComeBackAsTheyWere()
	{
		GearChoices c = GearChoices.none().with("Odd\tboss\nname\\", GearChoices.part("a|b\t|c"), 3, 1, 1, 0);
		GearChoices back = GearChoices.decode(c.encode());
		assertEquals("a|b\t|c", back.of("Odd\tboss\nname\\").get(0).partKey());
	}

	@Test
	public void thePartsOfAFightAreToldApartWhateverIsOwned() throws Exception
	{
		// case 1: Zulrah has three parts called "Special attack", one for each style of set
		Fights fights;
		try (InputStream in = Gear.class.getResourceAsStream("fights.tsv"))
		{
			fights = Fights.read(new InputStreamReader(in, StandardCharsets.UTF_8));
		}
		Set<String> zulrah = fights.partKeys("Zulrah");
		assertTrue(zulrah.contains("Special attack|ranged|ranged"));
		assertTrue(zulrah.contains("Special attack|melee|melee"));
		assertTrue(zulrah.contains("Special attack|magic|magic"));
		assertTrue(fights.partKeys("Not a boss").isEmpty());
		// and no boss has two parts the same key would mix up
		for (String boss : fights.bosses())
		{
			// the table counts the boss's main part apart; every other part has a key of its own
			Set<String> seen = new HashSet<>();
			for (String key : fights.partKeys(boss))
			{
				assertTrue(boss + ": " + key, seen.add(key));
			}
		}
	}

	@Test
	public void theChangesAreKeptPerAccountInThisPluginsSettingsOnly() throws Exception
	{
		// cases 14 and 15: read and written with the account's (RuneScape profile's) settings of the plugin's group, and a
		// change written there redraws no setting
		String plugin = new String(Files.readAllBytes(Paths.get("src/main/java/com/caplanner/CaPlannerPlugin.java")), StandardCharsets.UTF_8);
		assertTrue(plugin.contains("getRSProfileConfiguration(CONFIG_GROUP, GearChoices.KEY)"));
		assertTrue(plugin.contains("setRSProfileConfiguration(CONFIG_GROUP, GearChoices.KEY, text)"));
		assertTrue(plugin.contains("unsetRSProfileConfiguration(CONFIG_GROUP, GearChoices.KEY)"));
		assertFalse("never for every account at once", plugin.contains("setConfiguration(CONFIG_GROUP, GearChoices.KEY"));
		assertTrue(com.caplanner.CaPlannerPluginAccess.isOwnData(GearChoices.KEY));
		assertTrue(com.caplanner.CaPlannerPluginAccess.isOwnData("bank"));
		assertFalse(com.caplanner.CaPlannerPluginAccess.isOwnData("language"));
	}
}
