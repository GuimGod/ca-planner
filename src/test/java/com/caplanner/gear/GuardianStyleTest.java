/*
 * Copyright (c) 2026, GuimGod
 * All rights reserved. Licensed under the BSD 2-Clause License (see LICENSE).
 */
package com.caplanner.gear;

import static org.junit.Assert.assertTrue;

import java.util.List;
import org.junit.Test;

/**
 * The best style of a pickaxe on the Guardians of the Chambers of Xeric (defence stab +80, slash +180, crush -10): Smash
 * (crush), by far. The calculator, Crystal pickaxe in Torva with Piety alone: Smash 8.58 per second, Impale 6.87; in
 * the Masori 5.99 and 4.35. A report said the plugin had called Smash the wrong style there: not reproduced.
 */
public class GuardianStyleTest
{
	@Test
	public void smashIsTheBestStyleOfAPickaxeOnTheGuardians() throws java.io.IOException
	{
		StyleData data = new StyleData();
		StyleData.Monster guardian = data.monster(7569);
		List<StyleData.Style> styles = data.styles(23680);
		for (boolean torva : new boolean[]{true, false})
		{
			StyleCalc.Input in = new StyleCalc.Input();
			in.attackLevel = 118;
			in.strengthLevel = 118;
			in.prayerAttack = 120;
			in.prayerStrength = 123;
			// Crystal pickaxe, Avernic defender, Rancour, Ferocious gloves, Infernal max cape, Avernic treads; Torva or Masori
			in.stab = 38 + 30 + 25 + (torva ? 16 : -8) + 4 + 5;
			in.slash = -2 + 29 + 25 + (torva ? 16 : -8) + 4 + 5;
			in.crush = 32 + 28 + 25 + (torva ? 16 : -8) + 4 + 5;
			in.strength = 42 + 8 + 12 + (torva ? 14 + 8 + 6 + 4 : 0) + 8 + 6;
			in.defenceLevel = guardian.target().defenceLevel;
			in.defenceStab = guardian.stab;
			in.defenceSlash = guardian.slash;
			in.defenceCrush = guardian.crush;
			double smash = 0;
			double best = 0;
			for (StyleData.Style s : styles)
			{
				double hit = StyleCalc.expectedHit(in, s.type, s.stance);
				if ("Smash".equals(s.name))
				{
					smash = hit;
				}
				else
				{
					best = Math.max(best, hit);
				}
			}
			assertTrue(smash > best * 1.2);
		}
	}
}
