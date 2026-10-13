package com.caplanner.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.InputStreamReader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.Test;

public class PlannerTest
{
	private static final Task SPECTRE = new Task(0, "Noxious Foe", "Kill an Aberrant Spectre.", Tier.EASY, TaskType.KILL_COUNT, "Aberrant Spectre");
	private static final Task CHASER = new Task(486, "Vardorvis Speed-Chaser", "Kill Vardorvis in less than 1:05.", Tier.MASTER, TaskType.SPEED, "Vardorvis");
	private static final Task RUNNER = new Task(487, "Vardorvis Speed-Runner", "Kill Vardorvis in less than 0:55.", Tier.GRANDMASTER, TaskType.SPEED, "Vardorvis");
	private static final Task PERFECT = new Task(480, "Perfect Vardorvis", "Kill Vardorvis perfectly.", Tier.ELITE, TaskType.PERFECTION, "Vardorvis");
	private static final Task WIZARD = new Task(574, "You're a wizard", "Kill the Hueycoatl using only earth spells.", Tier.MEDIUM, TaskType.RESTRICTION, "The Hueycoatl");
	private static final Task HYDRA = new Task(300, "Hydra Champion", "Kill the Alchemical Hydra once.", Tier.ELITE, TaskType.KILL_COUNT, "Alchemical Hydra");
	private static final Task TEAM = new Task(301, "Together We'll Fall", "Kill Vorkath in a group of two or more.", Tier.HARD, TaskType.RESTRICTION, "Vorkath");
	private static final List<Task> TASKS = Arrays.asList(SPECTRE, CHASER, RUNNER, PERFECT, WIZARD);

	private static Set<Integer> done(Integer... ids)
	{
		return new HashSet<>(Arrays.asList(ids));
	}

	private static Planner.State state(List<Task> tasks, Integer... doneIds)
	{
		return new Planner.State(tasks, done(doneIds), null, null);
	}

	private static List<String> names(List<Task> tasks)
	{
		return tasks.stream().map(t -> t.name).collect(Collectors.toList());
	}

	private static Requirements requirements() throws Exception
	{
		return Requirements.read(new InputStreamReader(
			PlannerTest.class.getResourceAsStream("/com/caplanner/requirements.tsv"), StandardCharsets.UTF_8));
	}

	/** An account with every level at 99 and the given quests finished. */
	private static Account maxed(String... finished)
	{
		Account a = new Account();
		for (String skill : Arrays.asList("slayer", "agility", "strength", "hitpoints", "ranged", "farming", "fishing", "firemaking"))
		{
			a.levels.put(skill, 99);
		}
		for (String q : finished)
		{
			a.quests.put(Requirements.key(q), Account.QuestProgress.FINISHED);
		}
		return a;
	}

	@Test
	public void completionIsOneBitPerTask()
	{
		int[] words = new int[21];
		words[0] = 1;                       // task 0
		words[486 / 32] = 1 << (486 % 32);  // task 486
		assertTrue(Completion.done(0, words));
		assertTrue(Completion.done(486, words));
		assertFalse(Completion.done(487, words));
		assertFalse(Completion.done(1, words));
		// bit 31 is the sign bit: it must still read as done
		words[1] = 1 << 31;
		assertTrue(Completion.done(63, words));
		// a task past the last variable, or a bad id, is simply not done
		assertFalse(Completion.done(21 * 32, words));
		assertFalse(Completion.done(-1, words));
		assertEquals(done(0, 486), Completion.doneIds(TASKS, new int[]{1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1 << 6}));
	}

	@Test
	public void bossesWithMostPointsLeftComeFirstAndFinishedOnesLast()
	{
		List<Planner.BossRow> rows = Planner.bosses(state(TASKS, 0), null);
		assertEquals(Arrays.asList("Vardorvis", "The Hueycoatl", "Aberrant Spectre"),
			rows.stream().map(r -> r.name).collect(Collectors.toList()));
		assertEquals(15, rows.get(0).pointsLeft);
		assertEquals(3, rows.get(0).left());
		assertEquals(3, rows.get(0).canNow);
		assertEquals(0, rows.get(2).left());
		// typing filters by name, ignoring case, spaces and punctuation
		assertEquals(1, Planner.bosses(state(TASKS), "huey").size());
		assertEquals(1, Planner.bosses(state(TASKS), "the hueycoatl").size());
		assertEquals(0, Planner.bosses(state(TASKS), "zulrah").size());
	}

	@Test
	public void aBossListsWhatIsLeftFirstLowestTierOnTop()
	{
		assertEquals(Arrays.asList("Perfect Vardorvis", "Vardorvis Speed-Runner", "Vardorvis Speed-Chaser"),
			names(Planner.ofBoss(state(TASKS, 486), "Vardorvis")));
	}

	@Test
	public void theEasiestTaskComesFirstWhenThatIsKnown()
	{
		Map<Integer, Double> ease = new HashMap<>();
		ease.put(PERFECT.id, 2.5);
		ease.put(RUNNER.id, 9.0);
		ease.put(CHASER.id, 30.0);
		Planner.State s = new Planner.State(TASKS, done(), null, ease);
		assertEquals(Arrays.asList("Vardorvis Speed-Chaser", "Vardorvis Speed-Runner", "Perfect Vardorvis"),
			names(Planner.ofBoss(s, "Vardorvis")));
	}

	@Test
	public void theAllTabFilters()
	{
		Planner.Filter f = new Planner.Filter();
		assertEquals(4, Planner.all(state(TASKS, 0), f).size());
		f.pendingOnly = false;
		assertEquals(5, Planner.all(state(TASKS, 0), f).size());
		f.type = TaskType.SPEED;
		assertEquals(Arrays.asList("Vardorvis Speed-Chaser", "Vardorvis Speed-Runner"), names(Planner.all(state(TASKS), f)));
		f.tier = Tier.GRANDMASTER;
		assertEquals(Arrays.asList("Vardorvis Speed-Runner"), names(Planner.all(state(TASKS), f)));
		f.type = null;
		f.tier = null;
		// the objective is searched too
		f.typed = "earth spells";
		assertEquals(Arrays.asList("You're a wizard"), names(Planner.all(state(TASKS), f)));
	}

	@Test
	public void pointsFollowTheTier()
	{
		assertEquals(1 + 5 + 6 + 4 + 2, Planner.totalPoints(state(TASKS)));
		assertEquals(6, Planner.points(state(TASKS, 0, 486)));
	}

	@Test
	public void theBankIsRememberedAsText()
	{
		OwnedItems bank = OwnedItems.of(new int[]{4151, -1, 995, 20997, 4151}, new int[]{1, 0, 1000, 0, 2});
		assertTrue(bank.has(4151));
		assertEquals(3, bank.quantity(4151));
		assertFalse("a placeholder is not owned", bank.has(20997));
		assertEquals(2, bank.size());
		OwnedItems back = OwnedItems.decode(bank.encode());
		assertEquals(1000, back.quantity(995));
		assertEquals(2, back.size());
		assertEquals(0, OwnedItems.decode("rubbish,12:x,:3").size());
		assertEquals(0, OwnedItems.decode(null).size());
	}

	@Test
	public void aQuestALevelOrASlayerTaskCanStandInTheWay() throws Exception
	{
		Requirements r = requirements();
		// Vardorvis: the quest
		Verdict v = r.check(CHASER, maxed());
		assertEquals(Verdict.Status.BLOCKED, v.status);
		assertEquals(Arrays.asList("Finish Desert Treasure II - The Fallen Empire"), v.reasons);
		assertTrue(r.check(CHASER, maxed("Desert Treasure II - The Fallen Empire")).can());
		// Aberrant Spectre: the Slayer level
		Account low = maxed();
		low.levels.put("slayer", 59);
		assertEquals(Arrays.asList("Slayer 60 (you have 59)"), r.check(SPECTRE, low).reasons);
		assertTrue(r.check(SPECTRE, maxed()).can());
		// Alchemical Hydra: only on the task, whatever the level
		assertEquals(Arrays.asList("Only on a Slayer task: Hydras"), r.check(HYDRA, maxed()).reasons);
		Account onTask = maxed();
		onTask.slayerTask = "HYDRAS";
		assertTrue(r.check(HYDRA, onTask).can());
		onTask.slayerTask = "Hellhounds";
		assertFalse(r.check(HYDRA, onTask).can());
		// a quest that only has to be started
		Account started = maxed();
		started.quests.put(Requirements.key("Regicide"), Account.QuestProgress.STARTED);
		Task zulrah = new Task(302, "Snake Rehabilitation", "Kill Zulrah.", Tier.ELITE, TaskType.KILL_COUNT, "Zulrah");
		assertTrue(r.check(zulrah, started).can());
		assertEquals(Arrays.asList("Start Regicide"), r.check(zulrah, maxed()).reasons);
		// a boss the table does not have needs nothing
		Task mole = new Task(303, "Mole", "Kill the Giant Mole.", Tier.MEDIUM, TaskType.KILL_COUNT, "Giant Mole");
		assertTrue(r.check(mole, new Account()).can());
	}

	@Test
	public void aTaskForSeveralPlayersIsSetApart() throws Exception
	{
		Requirements r = requirements();
		Account a = maxed("Dragon Slayer II");
		assertEquals(Verdict.Status.NEEDS_GROUP, r.check(TEAM, a).status);
		// what is missing comes before the group: first get there
		assertEquals(Verdict.Status.BLOCKED, r.check(TEAM, maxed()).status);
		assertFalse(Requirements.needsGroup(new Task(1, "Dynamic Duo", "Complete wave 12 with the Dynamic Duo modifier.",
			Tier.MASTER, TaskType.MECHANICAL, "Fortis Colosseum")));
		assertFalse(Requirements.needsGroup(new Task(2, "x", "Kill it at any group size.", Tier.EASY, TaskType.KILL_COUNT, "x")));
		assertTrue(Requirements.needsGroup(new Task(3, "x", "Kill it as a duo.", Tier.EASY, TaskType.KILL_COUNT, "x")));
	}

	@Test
	public void sectionsAndFiltersFollowTheVerdict() throws Exception
	{
		Requirements r = requirements();
		List<Task> tasks = Arrays.asList(SPECTRE, CHASER, HYDRA, TEAM);
		Account a = maxed("Dragon Slayer II");
		Map<Integer, Verdict> verdicts = new HashMap<>();
		for (Task t : tasks)
		{
			verdicts.put(t.id, r.check(t, a));
		}
		Planner.State s = new Planner.State(tasks, done(), verdicts, null);
		assertEquals(0, Planner.section(s, SPECTRE));
		assertEquals(1, Planner.section(s, TEAM));
		assertEquals(2, Planner.section(s, CHASER));
		assertEquals(1, Planner.canNow(s));
		Planner.Filter f = new Planner.Filter();
		f.canOnly = true;
		assertEquals(Arrays.asList("Noxious Foe"), names(Planner.all(s, f)));
		// bosses with something to do now come before the ones with nothing possible
		assertEquals("Aberrant Spectre", Planner.bosses(s, null).get(0).name);
	}

	@Test
	public void theTableReadsAndNamesOnlyRealThings() throws Exception
	{
		Requirements r = requirements();
		assertTrue(r.questNames().contains("Desert Treasure II - The Fallen Empire"));
		assertEquals(0, Requirements.read(new StringReader("# nothing\n\n")).questNames().size());
		assertEquals(23.6, Ease.read(new StringReader("# c\n574\t23.6\nbad line\n")).get(574), 0.001);
	}
}
