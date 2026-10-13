package com.caplanner.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.Test;

public class SessionTest
{
	private static final Task MOLE_1 = new Task(1, "Mole one", "Kill it.", Tier.EASY, TaskType.KILL_COUNT, "Giant Mole");
	private static final Task MOLE_2 = new Task(2, "Mole two", "Kill it well.", Tier.HARD, TaskType.PERFECTION, "Giant Mole");
	private static final Task HYDRA = new Task(3, "Hydra one", "Kill it.", Tier.ELITE, TaskType.KILL_COUNT, "Alchemical Hydra");
	private static final Task RAID = new Task(4, "Raid one", "Finish it.", Tier.MASTER, TaskType.KILL_COUNT, "Chambers of Xeric");
	private static final Task BLOCKED = new Task(5, "Blocked one", "Kill it.", Tier.GRANDMASTER, TaskType.KILL_COUNT, "Giant Mole");

	private static Planner.State state(Integer... done) throws Exception
	{
		Map<Integer, Verdict> verdicts = new HashMap<>();
		List<String> why = new ArrayList<>();
		why.add("Slayer 95");
		verdicts.put(BLOCKED.id, Verdict.blocked(why));
		return new Planner.State(Arrays.asList(MOLE_1, MOLE_2, HYDRA, RAID, BLOCKED), new HashSet<>(Arrays.asList(done)), verdicts, null);
	}

	private static Session.Lengths lengths() throws Exception
	{
		return Session.Lengths.read(new StringReader("# c\nGiant Mole\tshort\nChambers of Xeric\tlong\nbad\nOdd\tsometime\n"));
	}

	private static final Set<Integer> NO_TASKS = new HashSet<>();
	private static final Set<String> NO_BOSSES = new HashSet<>();

	@Test
	public void theTimeDecidesWhichBossesFit() throws Exception
	{
		Session.Lengths lengths = lengths();
		assertEquals(Session.Time.SHORT, lengths.of("giant mole"));
		assertEquals(Session.Time.LONG, lengths.of("Chambers of Xeric"));
		assertEquals("a boss that is not in the table is a middling one", Session.Time.MEDIUM, lengths.of("Alchemical Hydra"));
		assertEquals(Session.Time.MEDIUM, lengths.of("Odd"));
		// half an hour: only the short boss, whatever the others are worth
		Session.Pick quick = Session.next(state(), lengths, Session.Time.SHORT, null, NO_TASKS, NO_BOSSES);
		assertEquals("Giant Mole", quick.boss);
		// more than two hours: the boss worth the most of them all
		Session.Pick longer = Session.next(state(), lengths, Session.Time.LONG, null, NO_TASKS, NO_BOSSES);
		assertEquals("Chambers of Xeric", longer.boss);
		// an hour or two leaves the raid out
		assertTrue(!"Chambers of Xeric".equals(Session.next(state(), lengths, Session.Time.MEDIUM, null, NO_TASKS, NO_BOSSES).boss));
		// with the short boss finished, a middling one is next for an hour or two, and nothing for half an hour
		assertEquals("Alchemical Hydra", Session.next(state(1, 2), lengths, Session.Time.MEDIUM, null, NO_TASKS, NO_BOSSES).boss);
		assertNull(Session.next(state(1, 2), lengths, Session.Time.SHORT, null, NO_TASKS, NO_BOSSES));
	}

	@Test
	public void itMovesOnWhenATaskIsDoneAndStaysAtTheBossWhileThereIsMore() throws Exception
	{
		Session.Lengths lengths = lengths();
		Session.Pick first = Session.next(state(), lengths, Session.Time.SHORT, null, NO_TASKS, NO_BOSSES);
		assertEquals(1, first.after.size());
		assertTrue("what cannot be done is never pointed at", first.task.id != BLOCKED.id && first.after.get(0).id != BLOCKED.id);
		// the game marks it done: the other task of the same boss is next
		Session.Pick second = Session.next(state(first.task.id), lengths, Session.Time.SHORT, "Giant Mole", NO_TASKS, NO_BOSSES);
		assertEquals("Giant Mole", second.boss);
		assertEquals(first.after.get(0).id, second.task.id);
		assertTrue(second.after.isEmpty());
		// the boss the player is at is kept even when another is worth more
		assertEquals("Giant Mole", Session.next(state(), lengths, Session.Time.LONG, "Giant Mole", NO_TASKS, NO_BOSSES).boss);
		// nothing left there: on to the next boss that fits, or nothing
		assertNull(Session.next(state(1, 2), lengths, Session.Time.SHORT, "Giant Mole", NO_TASKS, NO_BOSSES));
		assertEquals("Alchemical Hydra", Session.next(state(1, 2), lengths, Session.Time.MEDIUM, "Giant Mole", NO_TASKS, NO_BOSSES).boss);
	}

	@Test
	public void whatThePlayerSetsAsideIsLeftAlone() throws Exception
	{
		Session.Lengths lengths = lengths();
		Session.Pick first = Session.next(state(), lengths, Session.Time.SHORT, null, NO_TASKS, NO_BOSSES);
		Set<Integer> skipped = new HashSet<>(Arrays.asList(first.task.id));
		Session.Pick other = Session.next(state(), lengths, Session.Time.SHORT, "Giant Mole", skipped, NO_BOSSES);
		assertTrue(other.task.id != first.task.id);
		Set<String> bosses = new HashSet<>(Arrays.asList("Giant Mole"));
		assertNull(Session.next(state(), lengths, Session.Time.SHORT, "Giant Mole", NO_TASKS, bosses));
		assertEquals("Alchemical Hydra", Session.next(state(), lengths, Session.Time.MEDIUM, "Giant Mole", NO_TASKS, bosses).boss);
	}

	@Test
	public void theBundledTablesAreSound() throws Exception
	{
		Set<String> known = new HashSet<>();
		Set<Integer> ids = new HashSet<>();
		try (BufferedReader in = reader("/com/caplanner/bosses.txt"))
		{
			String line;
			while ((line = in.readLine()) != null)
			{
				known.add(Requirements.key(line));
			}
		}
		try (BufferedReader in = reader("/com/caplanner/tasks.tsv"))
		{
			String line;
			while ((line = in.readLine()) != null)
			{
				String[] c = line.split("\t");
				if (c.length > 1 && c[0].matches("\\d+"))
				{
					ids.add(Integer.parseInt(c[0]));
				}
			}
		}
		Session.Lengths lengths;
		try (BufferedReader in = reader("/com/caplanner/sessions.tsv"))
		{
			lengths = Session.Lengths.read(in);
		}
		assertTrue(lengths.bosses().size() > 30);
		for (String boss : lengths.bosses())
		{
			assertTrue("not a Combat Achievements boss: " + boss, known.contains(boss));
		}
		assertEquals(Session.Time.LONG, lengths.of("Theatre of Blood"));
		assertEquals(Session.Time.SHORT, lengths.of("Scurrius"));
		// the wiki's text: every line is a task of the game, and the file says whose text it is
		HowTo wiki;
		try (BufferedReader in = reader("/com/caplanner/howto-wiki.tsv"))
		{
			wiki = HowTo.read(in);
		}
		assertTrue(wiki.size() > 400);
		int withText = 0;
		for (int id : ids)
		{
			withText += wiki.of(id).isEmpty() ? 0 : 1;
		}
		assertEquals("a text for a task the game does not have", wiki.size(), withText);
		StringBuilder head = new StringBuilder();
		try (BufferedReader in = reader("/com/caplanner/howto-wiki.tsv"))
		{
			String line;
			while ((line = in.readLine()) != null && line.startsWith("#"))
			{
				head.append(line);
			}
		}
		assertTrue(head.toString().contains("CC BY-NC-SA 3.0") && head.toString().contains("oldschool.runescape.wiki"));
		try (BufferedReader in = reader("/com/caplanner/howto-own.tsv"))
		{
			assertNotNull(HowTo.read(in));
		}
	}

	@Test
	public void aBossIsFoundByWhatPlayersCallIt() throws Exception
	{
		Task cg = new Task(10, "Corrupted one", "Finish it.", Tier.ELITE, TaskType.KILL_COUNT, "Corrupted Hunllef");
		Task normal = new Task(11, "Normal one", "Finish it.", Tier.ELITE, TaskType.KILL_COUNT, "Crystalline Hunllef");
		Task rex = new Task(12, "Rex one", "Kill it.", Tier.HARD, TaskType.KILL_COUNT, "Dagannoth Rex");
		Task mole = new Task(13, "Mole one", "Kill it.", Tier.EASY, TaskType.KILL_COUNT, "Giant Mole");
		Planner.State state = new Planner.State(Arrays.asList(cg, normal, rex, mole), new HashSet<Integer>(), new HashMap<Integer, Verdict>(), null);
		assertEquals(2, Planner.bosses(state, "gauntlet").size());
		assertEquals(2, Planner.bosses(state, "gaunt").size());
		assertEquals("Corrupted Hunllef", Planner.bosses(state, "cg").get(0).name);
		assertEquals(1, Planner.bosses(state, "cg").size());
		assertEquals("Dagannoth Rex", Planner.bosses(state, "DKS").get(0).name);
		assertEquals("the game's own name still finds it", 1, Planner.bosses(state, "crystalline").size());
		assertEquals("a nickname is matched from its start", 0, Planner.bosses(state, "untlet").size());
		assertEquals(4, Planner.bosses(state, "").size());
		// every boss the bundled table names is a boss of the game
		Set<String> known = new HashSet<>();
		try (BufferedReader in = reader("/com/caplanner/bosses.txt"))
		{
			String line;
			while ((line = in.readLine()) != null)
			{
				known.add(Requirements.key(line));
			}
		}
		int rows = 0;
		try (BufferedReader in = reader("/com/caplanner/nicknames.tsv"))
		{
			String line;
			while ((line = in.readLine()) != null)
			{
				if (!line.trim().isEmpty() && !line.startsWith("#"))
				{
					assertTrue("not a Combat Achievements boss: " + line, known.contains(Requirements.key(line.split("\t")[0])));
					rows++;
				}
			}
		}
		assertTrue(rows > 40);
	}

	@Test
	public void theTextOfATaskIsReadParagraphByParagraph() throws Exception
	{
		HowTo how = HowTo.read(new StringReader("# c\n7\tFirst.\\nStrategy:\\nSecond thing.\nx\tnot a task\n8\t\n"));
		assertEquals(Arrays.asList("First.", "Strategy:", "Second thing."), how.of(7));
		assertTrue(how.of(8).isEmpty());
		assertTrue(how.of(99).isEmpty());
		assertEquals(1, how.size());
	}

	private static BufferedReader reader(String resource)
	{
		return new BufferedReader(new InputStreamReader(SessionTest.class.getResourceAsStream(resource), StandardCharsets.UTF_8));
	}
}
