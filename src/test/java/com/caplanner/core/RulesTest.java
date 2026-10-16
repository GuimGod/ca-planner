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

	/** What the audit of October 2026 found missing or wrong in the bundled table, task by task (OSRS Wiki pages). */
	@Test
	public void theBundledTableFollowsTheWikiOnTheAuditedTasks() throws Exception
	{
		Rules r;
		try (BufferedReader in = reader("/com/caplanner/rules.tsv"))
		{
			r = Rules.read(in);
		}
		// Facing Jad Head-on (I and III): the Keris partisan fails them, as do the Lunar staff, the Blisterwood flail
		// and the Pharaoh's sceptre
		assertTrue(r.of(153).exclude.containsAll(Arrays.asList("keris partisan", "blisterwood flail", "lunar staff")));
		assertTrue(r.of(367).exclude.containsAll(Arrays.asList("keris partisan", "pharaoh's sceptre")));
		// I Brought Mine Too: the Dragon hunter lance does, and owning only it is not "cannot"
		assertNull(r.missing(540, Arrays.asList("Dragon hunter lance")));
		assertEquals("Needs spear or hasta or halberd", r.missing(540, Arrays.asList("Abyssal whip")));
		// Mage of the Swamp: magic attacks only
		assertEquals(1, r.of(78).styles.size());
		assertTrue(r.of(78).styles.contains("magic"));
		// The Bane of Demons: demonbane spells, no recoil, no thralls; the spell itself is beyond the set
		assertTrue(r.of(336).styles.contains("magic"));
		assertTrue(r.of(336).exclude.containsAll(Arrays.asList("ring of recoil", "ring of suffering", "echo boots")));
		assertTrue(r.of(336).without.contains("book of the dead"));
		assertTrue(r.of(336).beyondTheSet);
		// Rapid Reload: a crossbow or a ballista
		assertTrue(r.of(555).weapon.containsAll(Arrays.asList("crossbow", "ballista")));
		assertNull(r.missing(555, Arrays.asList("Armadyl crossbow")));
		// Beef vs Beef: nothing worn and nothing eaten, which no set can follow
		assertTrue(r.of(635).beyondTheSet);
		// a weapon owned for the finishing blow, and the rest said
		assertEquals("Needs dragon halberd or crystal halberd", r.missing(389, Arrays.asList("Abyssal whip")));
		assertEquals("Needs rune thrownaxe", r.missing(200, Arrays.asList("Abyssal whip")));
		assertNull(r.missing(548, Arrays.asList("Burning claws")));
		for (int id : new int[]{124, 160, 227, 6, 482, 532, 606, 11, 215})
		{
			assertTrue("task " + id, r.of(id).beyondTheSet);
		}
		// Collateral Damage and Moving Collateral: chinchompas are one way of several, so nothing is asked to be owned
		assertTrue(r.of(11).have.isEmpty());
		assertTrue(r.of(215).have.isEmpty());
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
				// the game files some tasks about what is worn or used under another kind: Mage of the Ruins and of the
				// Swamp (magic attacks only), and those the audit of October 2026 found (a weapon, a special attack,
				// Vengeance, nothing worn)
				assertTrue("only Restriction tasks have a gear rule: " + line, "Restriction".equals(task[3])
					|| Arrays.asList("72", "78", "336", "555", "635", "389", "124", "160", "227", "6", "200", "548", "482", "606", "11")
					.contains(c[0]));
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
