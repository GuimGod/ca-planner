package com.caplanner.core;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.BeforeClass;
import org.junit.Test;

/** Case 20: a report of a card the player changed says so, with the changes, and the set the planner recommended. */
public class ReportEditTest
{
	@BeforeClass
	public static void read() throws Exception
	{
		GearEditTest.read();
	}

	@Test
	public void aReportOfAChangedCardSaysItWasChangedAndHow()
	{
		GearView rec = GearEditTest.card("Vorkath", -1, GearEditTest.melee(), null);
		GearView v = GearEditTest.card("Vorkath", -1, GearEditTest.melee(),
			GearChoices.none().with("Vorkath", GearChoices.WORN, 12, 25975, 1, 11773));
		String report = Report.body("Vorkath", null, "x", v, null, "", "English");
		assertTrue(report, report.contains("- gear edited by the player"));
		assertTrue(report, report.contains("- change: Ring: you changed Berserker ring (i) → Lightbearer"));
		assertTrue(report, report.contains("ring Lightbearer"));
		assertTrue(report, report.contains("- recommended set: ") && report.contains("ring Berserker ring (i)"));
		String plain = Report.body("Vorkath", null, "x", rec, null, "", "English");
		assertFalse(plain.contains("edited by the player"));
	}
}
