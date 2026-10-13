package com.caplanner.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;

public class ReportTest
{
	private static final Task TASK = new Task(272, "Dodging the Dragon", "Kill Vorkath 5 times without taking any damage.",
		Tier.MASTER, TaskType.PERFECTION, "Vorkath");

	private static GearView view()
	{
		Map<Integer, Integer> ids = new HashMap<>();
		Map<Integer, String> names = new HashMap<>();
		ids.put(3, 22978);
		names.put(3, "Dragon hunter lance");
		ids.put(5, 22322);
		names.put(5, "Avernic defender");
		List<Bag.Line> carried = Arrays.asList(Bag.Line.worn(20997, "Twisted bow", true, ""));
		List<Bag.Line> bag = new ArrayList<>();
		bag.add(Bag.Line.worn(4162, "Rock hammer", false, "to finish it off"));
		return GearView.ready(ids, names, "Melee", 10.784, 750, bag)
			.withParts("The dragon", Arrays.asList(new GearView.Part("Spawn", "Ranged", 5.5, carried, null, "", false)), bag);
	}

	@Test
	public void theReportSaysWhatThePlayerWroteAndWhatThePlannerGave()
	{
		List<String> why = new ArrayList<>();
		why.add("Finish Dragon Slayer II");
		String body = Report.body("Vorkath", TASK, "  It says I cannot, but I have done the quest.  ", view(), Verdict.blocked(why),
			"Attack 99, Slayer 90; Slayer task: none", "Portuguese");
		assertTrue(body.contains("It says I cannot, but I have done the quest."));
		assertTrue(body.contains("**Boss:** Vorkath"));
		assertTrue(body.contains("Dodging the Dragon (id 272, Master, Perfection)"));
		assertTrue(body.contains("Kill Vorkath 5 times without taking any damage."));
		assertTrue(body.contains("BLOCKED (Finish Dragon Slayer II)"));
		assertTrue(body.contains("weapon Dragon hunter lance | shield Avernic defender"));
		assertTrue(body.contains("Melee, 10.78 dps, boss hitpoints 750"));
		assertTrue(body.contains("worn for: The dragon"));
		assertTrue(body.contains("part \"Spawn\": Ranged, 5.5 dps -> Twisted bow"));
		assertTrue(body.contains("1 x Rock hammer (needed, not owned)"));
		assertTrue(body.contains("Attack 99, Slayer 90; Slayer task: none"));
		assertTrue(body.contains("**Panel language:** Portuguese"));
		// a boss alone, with nothing shown and nothing written, is still a report
		String bare = Report.body("Zulrah", null, "", null, null, "", null);
		assertTrue(bare.contains("(nothing written)") && bare.contains("nothing shown yet") && bare.contains("not known"));
		assertFalse(bare.contains("**Task:**"));
	}

	@Test
	public void theTitleNamesTheBossAndTheTaskAndTheLinkCarriesTheReport() throws Exception
	{
		assertEquals("[Vorkath] Dodging the Dragon: It says I cannot", Report.title("Vorkath", TASK, " It says\n I cannot "));
		assertEquals("[Zulrah]", Report.title("Zulrah", null, ""));
		String longWords = new String(new char[30]).replace("\0", "word ");
		assertTrue(Report.title("Zulrah", null, longWords).endsWith("..."));
		String link = Report.link("[Vorkath] a & b", "line one\nline two: 100%");
		assertTrue(link.startsWith("https://github.com/GuimGod/ca-planner/issues/new?title="));
		assertFalse("a space must not be a plus, which the site keeps as a plus", link.contains("+"));
		String query = link.substring(link.indexOf('?') + 1);
		assertEquals("[Vorkath] a & b", URLDecoder.decode(query.substring(6, query.indexOf("&body=")), "UTF-8"));
		assertEquals("line one\nline two: 100%", URLDecoder.decode(query.substring(query.indexOf("&body=") + 6), "UTF-8"));
		// a report too long for a link is cut at a line and says so
		StringBuilder big = new StringBuilder();
		for (int i = 0; i < 400; i++)
		{
			big.append("- a line of the report number ").append(i).append('\n');
		}
		String cut = URLDecoder.decode(Report.link("t", big.toString()), "UTF-8");
		assertTrue(cut.length() < 6200);
		assertTrue(cut.endsWith("(Cut short to fit in a link. Paste the copied report here for the rest.)"));
	}
}
