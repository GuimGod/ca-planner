package com.caplanner.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;

public class RulesTest
{
	private static BufferedReader reader(String resource)
	{
		return new BufferedReader(new InputStreamReader(RulesTest.class.getResourceAsStream(resource), StandardCharsets.UTF_8));
	}

	@Test
	public void aLineIsReadPartByPart() throws Exception
	{
		Rules r = Rules.read(new StringReader(
			"# c\n48\tweapon=@demonbane\tSkotizo\n618\tstyle=melee;onehand\n511\tstyle=ranged;ammo=mithril;note\n"
				+ "165\ttype=stab\n75\thave=@demonbane\nbad\tline\n9\tnonsense=1;shield\n"));
		assertTrue(r.of(48).weapon.contains("arclight"));
		assertTrue(r.of(48).weapon.contains("emberlight"));
		assertTrue(r.of(48).shapesTheSet());
		assertTrue(r.of(618).oneHanded);
		assertEquals(1, r.of(618).styles.size());
		assertEquals("mithril", r.of(511).ammoWord);
		assertTrue(r.of(511).beyondTheSet);
		assertEquals("stab", r.of(165).meleeType);
		assertFalse("owning it is asked, the set is not shaped", r.of(75).shapesTheSet());
		assertTrue("a part that is not understood is left out", r.of(9).shield);
		assertNull(r.of(1));
	}

	@Test
	public void whatTheTaskNeedsAndThePlayerDoesNotOwn() throws Exception
	{
		Rules r = Rules.read(new StringReader("75\thave=@demonbane\n161\tweapon=verac's flail\n50\thave=chinchompa;note\n"));
		assertEquals("Needs a demonbane weapon", r.missing(75, Arrays.asList("Abyssal whip", "Dragon defender")));
		assertNull(r.missing(75, Arrays.asList("Abyssal whip", "Emberlight")));
		assertEquals("Needs verac's flail", r.missing(161, Arrays.asList("Abyssal whip")));
		assertNull(r.missing(161, Arrays.asList("Verac's flail 100")));
		assertEquals("Needs chinchompa", r.missing(50, Arrays.asList("Twisted bow")));
		assertNull(r.missing(50, Arrays.asList("Black chinchompa")));
		assertNull("the bank was never seen: nothing is claimed", r.missing(75, null));
		assertNull("a task with no rule", r.missing(1, Arrays.asList("Abyssal whip")));
	}

	/** Every line of the bundled table must point at the task it says it does, and that task must be about gear. */
	@Test
	public void theBundledTableMatchesTheTasks() throws Exception
	{
		Map<Integer, String[]> tasks = new HashMap<>();
		try (BufferedReader in = reader("/com/caplanner/tasks.tsv"))
		{
			String line;
			while ((line = in.readLine()) != null)
			{
				String[] c = line.split("\t");
				tasks.put(Integer.parseInt(c[0]), c);
			}
		}
		Rules rules;
		try (BufferedReader in = reader("/com/caplanner/rules.tsv"))
		{
			rules = Rules.read(in);
		}
		assertTrue(rules.taskIds().size() > 30);
		try (BufferedReader in = reader("/com/caplanner/rules.tsv"))
		{
			String line;
			while ((line = in.readLine()) != null)
			{
				if (line.trim().isEmpty() || line.startsWith("#"))
				{
					continue;
				}
				String[] c = line.split("\t");
				assertEquals(line, 3, c.length);
				String[] task = tasks.get(Integer.parseInt(c[0]));
				assertNotNull("no such task: " + line, task);
				assertEquals("the line names another task: " + line, task[1] + ": " + task[2], c[2]);
				// the game files one task about the weapon under another kind: Mage of the Ruins, magic attacks only
				assertTrue("only Restriction tasks have a gear rule: " + line,
					"Restriction".equals(task[3]) || "72".equals(c[0]));
				Rules.Rule rule = rules.of(Integer.parseInt(c[0]));
				assertTrue("a line that says nothing: " + line,
					rule.shapesTheSet() || rule.beyondTheSet || !rule.have.isEmpty());
				for (String style : rule.styles)
				{
					assertTrue(line, Arrays.asList("melee", "ranged", "magic").contains(style));
				}
				assertTrue(line, rule.meleeType == null || Arrays.asList("stab", "slash", "crush").contains(rule.meleeType));
			}
		}
	}
}
