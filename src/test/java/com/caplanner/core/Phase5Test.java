package com.caplanner.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.Test;

/** Ordering by value, what is missing, and the limit of Speed tasks. */
public class Phase5Test
{
	private static final Task EASY = new Task(1, "Easy one", "Kill it.", Tier.EASY, TaskType.KILL_COUNT, "A");
	private static final Task RARE = new Task(2, "Rare one", "Kill it perfectly.", Tier.GRANDMASTER, TaskType.PERFECTION, "A");
	private static final Task MIDDLE = new Task(3, "Middle one", "Kill it fast.", Tier.HARD, TaskType.SPEED, "B");
	private static final Task QUEST = new Task(4, "Quest one", "Kill it.", Tier.MASTER, TaskType.KILL_COUNT, "C");
	private static final Task BOTH = new Task(5, "Quest and level", "Kill it.", Tier.ELITE, TaskType.KILL_COUNT, "C");
	private static final Task LEVEL = new Task(6, "Level one", "Kill it.", Tier.MEDIUM, TaskType.KILL_COUNT, "D");

	private static List<String> names(List<Task> tasks)
	{
		return tasks.stream().map(t -> t.name).collect(Collectors.toList());
	}

	@Test
	public void valueIsPointsCutDownWhenFewHaveDoneIt()
	{
		Map<Integer, Double> ease = new HashMap<>();
		ease.put(EASY.id, 60.0);
		ease.put(RARE.id, 2.5);
		ease.put(MIDDLE.id, 12.5);
		Planner.State s = new Planner.State(Arrays.asList(EASY, RARE, MIDDLE, QUEST), new HashSet<>(), null, ease);
		assertEquals(1.0, Planner.value(s, EASY), 1e-9);
		assertEquals("6 points, a tenth of a quarter of the players", 0.6, Planner.value(s, RARE), 1e-9);
		assertEquals(1.5, Planner.value(s, MIDDLE), 1e-9);
		assertEquals("no figure: half", 2.5, Planner.value(s, QUEST), 1e-9);
		Planner.Filter f = new Planner.Filter();
		assertEquals(Arrays.asList("Easy one", "Middle one", "Rare one", "Quest one"), names(Planner.all(s, f)));
		f.sort = Planner.Sort.POINTS;
		assertEquals(Arrays.asList("Rare one", "Quest one", "Middle one", "Easy one"), names(Planner.all(s, f)));
		f.sort = Planner.Sort.VALUE;
		assertEquals(Arrays.asList("Quest one", "Middle one", "Easy one", "Rare one"), names(Planner.all(s, f)));
		// the boss whose doable tasks are worth the most comes first: C (2.5), A (1.6), B (1.5)
		assertEquals(Arrays.asList("C", "A", "B"),
			Planner.bosses(s, null).stream().map(r -> r.name).collect(Collectors.toList()));
	}

	@Test
	public void whatIsMissingIsRankedByWhatItOpensAlone()
	{
		Map<Integer, Verdict> verdicts = new HashMap<>();
		verdicts.put(QUEST.id, Verdict.blocked(Arrays.asList("Finish Some Quest")));
		verdicts.put(BOTH.id, Verdict.blocked(Arrays.asList("Finish Some Quest", "Slayer 87 (you have 85)")));
		verdicts.put(LEVEL.id, Verdict.blocked(Arrays.asList("Slayer 87 (you have 86)")));
		Planner.State s = new Planner.State(Arrays.asList(EASY, QUEST, BOTH, LEVEL), new HashSet<>(), verdicts, null);
		List<Unlocks.Unlock> u = Unlocks.of(s);
		assertEquals(2, u.size());
		assertEquals("Finish Some Quest", u.get(0).what);
		assertEquals(Arrays.asList("Quest one"), names(u.get(0).alone));
		assertEquals(2, u.get(0).tasks.size());
		assertEquals(5, u.get(0).alonePoints());
		assertEquals(9, u.get(0).points());
		// the same level is one goal, however far from it each line says the player is
		assertEquals(2, u.get(1).tasks.size());
		assertEquals(Arrays.asList("Level one"), names(u.get(1).alone));
		// a finished task is in the way of nothing
		Planner.State done = new Planner.State(Arrays.asList(QUEST), new HashSet<>(Arrays.asList(QUEST.id)), verdicts, null);
		assertTrue(Unlocks.of(done).isEmpty());
	}

	@Test
	public void theLimitOfASpeedTaskIsReadFromItsObjective()
	{
		assertEquals(65, SpeedLimit.seconds("Kill Vardorvis in less than 1:05."));
		assertEquals(100, SpeedLimit.seconds("Kill the Grotesque Guardians in less than 1:40 minutes."));
		assertEquals(180, SpeedLimit.seconds("Kill Galvek in less than 3 minutes."));
		assertEquals(48, SpeedLimit.seconds("Kill the Hespori in less than 48 seconds."));
		assertEquals(26 * 60 + 30, SpeedLimit.seconds("Complete the Fight Caves in less than 26 minutes and 30 seconds."));
		assertEquals(80, SpeedLimit.seconds("Kill Zulrah in less than 1 minute 20 seconds, without a slayer task."));
		assertEquals(75, SpeedLimit.seconds("Kill Vorkath in less than 1 minute and 15 seconds."));
		assertEquals(18 * 60, SpeedLimit.seconds("Complete the Tombs of Amascut (normal) within 18 mins at any group size."));
		assertEquals(600, SpeedLimit.seconds("Kill Araxxor 5 times in 10:00."));
		assertEquals(2, SpeedLimit.seconds("Kill Brutus in less than 2 seconds."));
		assertEquals(0, SpeedLimit.seconds("Complete a Chambers of Xeric Challenge mode raid in the target time."));
		assertEquals(0, SpeedLimit.seconds(null));
		assertTrue(SpeedLimit.isOneKill("Kill Vardorvis in less than 1:05."));
		assertFalse("several kills", SpeedLimit.isOneKill("Kill Araxxor 5 times in 10:00."));
		assertFalse("a whole run", SpeedLimit.isOneKill("Complete the Fight Caves in less than 26 minutes and 30 seconds."));
		assertFalse("an average", SpeedLimit.isOneKill("Defeat Yama in an average time of under 3:00 over your last four kills."));
		assertFalse(SpeedLimit.isOneKill("Defeat the Doom of Mokhaiotl levels 1-8 in less than 10:00."));
		assertEquals("1:05", SpeedLimit.clock(65));
		assertEquals("0:02", SpeedLimit.clock(2));
	}

	/** Every Speed task of the game has a limit the plugin can read, bar the ones that name none. */
	@Test
	public void everySpeedTaskIsRead() throws Exception
	{
		List<String> unread = new ArrayList<>();
		try (BufferedReader in = new BufferedReader(new InputStreamReader(
			Phase5Test.class.getResourceAsStream("/com/caplanner/speed.tsv"), StandardCharsets.UTF_8)))
		{
			String line;
			while ((line = in.readLine()) != null)
			{
				String[] c = line.split("\t");
				if (SpeedLimit.seconds(c[1]) == 0)
				{
					unread.add(c[1]);
				}
			}
		}
		assertEquals(unread.toString(), 2, unread.size());
		for (String objective : unread)
		{
			assertTrue(objective, objective.contains("target time") || objective.contains("challenge time"));
		}
	}
}
