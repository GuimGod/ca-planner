package com.caplanner.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.StringReader;
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

	private static boolean group(String name, String description)
	{
		return Requirements.needsGroup(new Task(1, name, description, Tier.MASTER, TaskType.SPEED, "x"));
	}
}
