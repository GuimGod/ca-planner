package com.caplanner.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import org.junit.Test;

/** The cases a review of the first version found. */
public class RequirementsEdgeTest
{
	private static final Task KRAKEN = new Task(1, "Krakan't Hurt Me", "Kill the Kraken.", Tier.HARD, TaskType.KILL_COUNT, "Kraken");

	private static Requirements table(String lines) throws Exception
	{
		return Requirements.read(new StringReader(lines));
	}

	private static Account onTask(String task)
	{
		Account a = new Account();
		a.levels.put("slayer", 99);
		a.slayerTask = task;
		return a;
	}

	@Test
	public void theBossesTaskAndAnUnreadableTaskDoNotHideABoss() throws Exception
	{
		Requirements r = table("Kraken\t\tSlayer 87\tCave kraken; Kraken\n");
		assertFalse("no task at all", r.check(KRAKEN, onTask(null)).can());
		assertFalse(r.check(KRAKEN, onTask("Hellhounds")).can());
		assertTrue(r.check(KRAKEN, onTask("CAVE KRAKEN")).can());
		assertTrue("the other name the game may use", r.check(KRAKEN, onTask("Kraken")).can());
		assertTrue("a boss task: which boss is not read, so it is not hidden", r.check(KRAKEN, onTask("Bosses")).can());
		Account unknown = onTask(null);
		unknown.slayerTaskUnknown = true;
		assertTrue("a task whose name could not be read", r.check(KRAKEN, unknown).can());
		// the level still counts on task
		Account low = onTask("Cave kraken");
		low.levels.put("slayer", 86);
		assertEquals("Slayer 87 (you have 86)", r.check(KRAKEN, low).reasons.get(0));
	}

	@Test
	public void aBadCellLosesOnlyItself() throws Exception
	{
		Requirements r = table("Kraken\tRegicide*\tSlayer87; Agility x; Slayer 87\t\n# a comment\n\nMole\n");
		Account a = new Account();
		a.levels.put("slayer", 50);
		assertEquals(2, r.check(KRAKEN, a).reasons.size());
		assertEquals("Start Regicide", r.check(KRAKEN, a).reasons.get(0));
		assertEquals("Slayer 87 (you have 50)", r.check(KRAKEN, a).reasons.get(1));
		// a line with only a boss name is a boss that needs nothing
		assertTrue(r.check(new Task(2, "x", "Kill the mole.", Tier.EASY, TaskType.KILL_COUNT, "Mole"), new Account()).can());
	}

	@Test
	public void wordingAboutPlayersIsReadTheRightWay()
	{
		assertFalse("an upper limit is met alone", group("Nex Trio", "Kill Nex with three or less players at the start of the fight."));
		assertFalse(group("Nex Duo", "Kill Nex with two or less players at the start of the fight."));
		assertFalse(group("Hueycoatl Speed-Runner", "Kill the Hueycoatl in 2:30 with three or fewer players."));
		assertFalse(group("Tombs Speed Runner", "Complete the Tombs of Amascut (normal) within 18 mins at any group size."));
		assertFalse(group("Chicken Killer", "Kill the Corporeal Beast solo."));
		assertTrue(group("Theatre (Trio) Speed-Chaser", "Complete the Theatre of Blood (Trio) in less than 20 minutes."));
		assertTrue(group("Nightmare (5-Scale) Speed-Chaser", "Defeat the Nightmare (5-scale) in less than 4 minutes."));
		assertTrue(group("Personal Space", "Defeat the Pestilent Bloat with at least 3 people in the room."));
		assertTrue(group("Pass It On", "Successfully pass on the green ball to a team mate."));
		assertTrue(group("Perfect Akkha", "Complete Akkha in a group of two or more, without anyone taking damage."));
	}

	@Test
	public void oneLevelOfTwoIsEnough() throws Exception
	{
		Requirements r = table("K'ril Tsutsaroth\t\tHitpoints 70; Strength 60 or Agility 60\t\n");
		Task kril = new Task(3, "x", "Kill K'ril.", Tier.HARD, TaskType.KILL_COUNT, "K'ril Tsutsaroth");
		Account a = new Account();
		a.levels.put("hitpoints", 70);
		a.levels.put("strength", 50);
		a.levels.put("agility", 40);
		assertEquals("Strength 60 (you have 50) or Agility 60 (you have 40)", r.check(kril, a).reasons.get(0));
		assertEquals(1, r.check(kril, a).reasons.size());
		a.levels.put("agility", 60);
		assertTrue("Agility alone does", r.check(kril, a).can());
		a.levels.put("agility", 40);
		a.levels.put("strength", 60);
		assertTrue("Strength alone does", r.check(kril, a).can());
	}

	@Test
	public void aLineForOneTaskAddsToItsBoss() throws Exception
	{
		Requirements r = table("Ket-Rak\tTzHaar\t\t\t\nKet-Rak#362\t\t\t\tinferno-done=Complete the Inferno once\nKet-Rak#x\tNot a quest\n");
		Task first = new Task(361, "x", "First challenge.", Tier.ELITE, TaskType.KILL_COUNT, "Ket-Rak");
		Task third = new Task(362, "x", "Fourth challenge.", Tier.ELITE, TaskType.KILL_COUNT, "Ket-Rak");
		Account a = new Account();
		a.quests.put("tzhaar", Account.QuestProgress.FINISHED);
		assertTrue("the task with no line of its own needs only its boss's", r.check(first, a).can());
		assertEquals("Complete the Inferno once", r.check(third, a).reasons.get(0));
		a.done.add("inferno-done");
		assertTrue(r.check(third, a).can());
		// the boss's own needs still count for the task with a line of its own
		a.quests.clear();
		assertEquals("Finish TzHaar", r.check(third, a).reasons.get(0));
		assertFalse("a line with no task id is left out", r.questNames().contains("Not a quest"));
	}

	/** The audit of October 2026: what the bundled table was missing. */
	@Test
	public void theBundledTableAsksForWhatTheWikiDoes() throws Exception
	{
		Requirements r;
		try (BufferedReader in = new BufferedReader(new InputStreamReader(
			RequirementsEdgeTest.class.getResourceAsStream("/com/caplanner/requirements.tsv"), StandardCharsets.UTF_8)))
		{
			r = Requirements.read(in);
		}
		// TzHaar-Ket-Rak: the third challenge on asks for the Inferno; the first two do not
		Account a = new Account();
		for (int id : new int[]{361, 364, 367})
		{
			assertTrue("task " + id, r.check(new Task(id, "x", "x", Tier.ELITE, TaskType.KILL_COUNT, "TzHaar-Ket-Rak's Challenges"), a).can());
		}
		for (int id : new int[]{362, 363, 365, 366, 368, 369, 370, 371})
		{
			Task t = new Task(id, "x", "x", Tier.ELITE, TaskType.KILL_COUNT, "TzHaar-Ket-Rak's Challenges");
			assertEquals("task " + id, "Complete the Inferno once", r.check(t, a).reasons.get(0));
		}
		a.done.add("inferno-done");
		assertTrue(r.check(new Task(371, "x", "x", Tier.ELITE, TaskType.KILL_COUNT, "TzHaar-Ket-Rak's Challenges"), a).can());
		// the Grotesque Guardians' rooftop and the Abyss, on the boss's Slayer task
		Account slayer = onTask("Gargoyles");
		assertFalse(r.check(new Task(1, "x", "x", Tier.HARD, TaskType.KILL_COUNT, "Grotesque Guardians"), slayer).can());
		slayer.done.add("gg-roof");
		assertTrue(r.check(new Task(1, "x", "x", Tier.HARD, TaskType.KILL_COUNT, "Grotesque Guardians"), slayer).can());
		Account abyss = onTask("Abyssal demons");
		assertFalse(r.check(new Task(1, "x", "x", Tier.HARD, TaskType.KILL_COUNT, "Abyssal Sire"), abyss).can());
		abyss.done.add("abyss-access");
		assertTrue(r.check(new Task(1, "x", "x", Tier.HARD, TaskType.KILL_COUNT, "Abyssal Sire"), abyss).can());
		// K'ril and Kree'arra: into the God Wars Dungeon with Strength 60 or Agility 60
		Account gwd = new Account();
		gwd.quests.put(Requirements.key("Troll Stronghold"), Account.QuestProgress.FINISHED);
		gwd.levels.put("hitpoints", 70);
		gwd.levels.put("ranged", 70);
		gwd.levels.put("strength", 59);
		gwd.levels.put("agility", 59);
		assertFalse(r.check(new Task(1, "x", "x", Tier.HARD, TaskType.KILL_COUNT, "K'ril Tsutsaroth"), gwd).can());
		assertFalse(r.check(new Task(1, "x", "x", Tier.HARD, TaskType.KILL_COUNT, "Kree'arra"), gwd).can());
		gwd.levels.put("agility", 60);
		assertTrue(r.check(new Task(1, "x", "x", Tier.HARD, TaskType.KILL_COUNT, "K'ril Tsutsaroth"), gwd).can());
		assertTrue(r.check(new Task(1, "x", "x", Tier.HARD, TaskType.KILL_COUNT, "Kree'arra"), gwd).can());
		// and each new reason is said in Portuguese
		Lang pt;
		try (BufferedReader in = new BufferedReader(new InputStreamReader(
			RequirementsEdgeTest.class.getResourceAsStream("/com/caplanner/lang/pt.tsv"), StandardCharsets.UTF_8)))
		{
			pt = Lang.read(in);
		}
		assertEquals("Strength 60 (você tem 50) ou Agility 60 (você tem 40)",
			pt.of("Strength 60 (you have 50) or Agility 60 (you have 40)"));
		assertEquals("Complete o Inferno uma vez", pt.of("Complete the Inferno once"));
		assertTrue(pt.of("Unlock the rooftop with a brittle key, from gargoyles on a Slayer task").startsWith("Destranque"));
		assertTrue(pt.of("Reach the Abyss once: the Enter the Abyss miniquest, or the fairy rings").startsWith("Chegue"));
	}

	private static boolean group(String name, String description)
	{
		return Requirements.needsGroup(new Task(1, name, description, Tier.MASTER, TaskType.SPEED, "x"));
	}
}
