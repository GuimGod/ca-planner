/*
 * Copyright (c) 2026, GuimGod
 * All rights reserved. Licensed under the BSD 2-Clause License (see LICENSE).
 */
package com.caplanner.core;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/** What reading the fights again against their sheets changed in the inventory, on the tables the plugin ships. */
public class RereadTest
{
	private static BufferedReader reader(String path)
	{
		return new BufferedReader(new InputStreamReader(RereadTest.class.getResourceAsStream(path), StandardCharsets.UTF_8));
	}

	private static Bag bag() throws Exception
	{
		Bag bag;
		try (BufferedReader in = reader("/com/caplanner/bag.tsv"))
		{
			bag = Bag.read(in);
		}
		try (BufferedReader in = reader("/com/caplanner/bag-wiki.tsv"))
		{
			bag.readWiki(in);
		}
		return bag;
	}

	private static Map<String, Bag.Have> everything()
	{
		Map<String, Bag.Have> out = new HashMap<>();
		Object[] all = {12625, 5, "Book of the dead", 3024, 100, "Super restore(4)", 30125, 20, "Prayer regeneration potion(4)",
			13441, 500, "Anglerfish", 3144, 100, "Cooked karambwan", 23685, 10, "Divine super combat potion(4)",
			24635, 10, "Divine ranging potion(4)", 5952, 20, "Antidote++(4)", 13280, 1, "Max cape", 8013, 50, "Teleport to house",
			19564, 1, "Royal seed pod", 27641, 1, "Saturated heart", 20724, 1, "Imbued heart", 3040, 10, "Magic potion(4)",
			6685, 100, "Saradomin brew(4)", 12913, 10, "Anti-venom+(4)", 12625 + 1, 10, "Extended stamina potion(4)", 11941, 1, "Looting bag"};
		for (int i = 0; i < all.length; i += 3)
		{
			String name = (String) all[i + 2];
			out.put(name.toLowerCase(Locale.ROOT), new Bag.Have((Integer) all[i], (Integer) all[i + 1], name));
		}
		return out;
	}

	private static boolean has(List<Bag.Line> lines, String startOfName)
	{
		for (Bag.Line l : lines)
		{
			if (l.name.startsWith(startOfName))
			{
				return true;
			}
		}
		return false;
	}

	private static List<Bag.Line> of(Bag bag, String boss, String style, Set<String> useless)
	{
		return bag.of(boss, style, everything(), new HashSet<String>(), new HashSet<Integer>(), Bag.SLOTS, useless);
	}

	@Test
	public void theBookIsNotCarriedWhereThrallsDoNothing() throws Exception
	{
		Bag bag = bag();
		Set<String> none = new HashSet<>();
		assertFalse(has(of(bag, "Vorkath", "ranged", none), "Book of the dead"));
		assertFalse(has(of(bag, "Abyssal Sire", "melee", none), "Book of the dead"));
		assertTrue(has(of(bag, "Cerberus", "melee", none), "Book of the dead"));
		// nor when the set casts from another spellbook
		Set<String> casting = new HashSet<>();
		casting.add("book of the dead");
		assertFalse(has(of(bag, "Cerberus", "magic", casting), "Book of the dead"));
	}

	@Test
	public void aBossThatPoisonsGetsACure() throws Exception
	{
		Bag bag = bag();
		for (String boss : new String[]{"K'ril Tsutsaroth", "King Black Dragon", "Abyssal Sire", "Yama"})
		{
			assertTrue(boss, has(of(bag, boss, "melee", new HashSet<String>()), "Antidote++"));
		}
		assertFalse(has(of(bag, "Cerberus", "melee", new HashSet<String>()), "Antidote++"));
	}

	@Test
	public void whatTheTaskForbidsIsLeftOut() throws Exception
	{
		Bag bag = bag();
		Rules rules;
		try (BufferedReader in = reader("/com/caplanner/rules.tsv"))
		{
			rules = Rules.read(in);
		}
		// Praying to the Gods: nothing that restores prayer is drunk
		Rules.Rule praying = rules.of(64);
		assertTrue(praying.shapesTheSet());
		List<Bag.Line> lines = of(bag, "Chaos Fanatic", "ranged", new HashSet<>(praying.without));
		assertFalse(has(lines, "Super restore"));
		assertFalse(has(lines, "Prayer regeneration"));
		assertTrue(has(lines, "Anglerfish"));
		assertTrue(has(of(bag, "Chaos Fanatic", "ranged", new HashSet<String>()), "Super restore"));
		// Yama with no potions and no food but plain pizza
		List<Bag.Line> yama = of(bag, "Yama", "melee", new HashSet<>(rules.of(604).without));
		assertFalse(has(yama, "Super restore"));
		assertFalse(has(yama, "Anglerfish"));
		assertFalse(has(yama, "Divine super combat"));
		// the provisional inventory follows the task too
		Bag.Provisional doom = bag.provisional("Doom of Mokhaiotl", "Ranged", everything(), new HashSet<String>(), new HashSet<Integer>(),
			Bag.SLOTS, new HashSet<>(rules.of(617).without));
		assertFalse(has(doom.lines, "Super restore"));
		assertTrue(has(doom.lines, "Saradomin brew"));
	}

	@Test
	public void deepInTheWildernessTheUsualWayOutIsNotCarried() throws Exception
	{
		Bag bag = bag();
		List<Bag.Line> fanatic = of(bag, "Chaos Fanatic", "ranged", new HashSet<String>());
		assertFalse(has(fanatic, "Max cape"));
		assertFalse(has(fanatic, "Teleport to house"));
		assertTrue(has(fanatic, "Royal seed pod"));
		assertFalse(bag.notes("Chaos Fanatic").isEmpty());
		Bag.Provisional callisto = bag.provisional("Callisto", "Ranged", everything(), new HashSet<String>(), new HashSet<Integer>(), Bag.SLOTS);
		assertFalse(has(callisto.lines, "Max cape"));
		assertTrue(has(callisto.lines, "Royal seed pod"));
		// outside the Wilderness nothing changed
		assertTrue(has(of(bag, "Cerberus", "melee", new HashSet<String>()), "Max cape"));
	}

	@Test
	public void aHeartIsTakenOnceHoweverManyPotionsTheAmountStandsFor() throws Exception
	{
		Bag bag = bag();
		Bag.Provisional p = bag.provisional("General Graardor", "Magic", everything(), new HashSet<String>(), new HashSet<Integer>(), Bag.SLOTS);
		int boosts = 0;
		for (Bag.Line l : p.lines)
		{
			boosts += l.name.endsWith("heart") || l.name.startsWith("Magic potion") ? l.quantity : 0;
		}
		assertEquals(1, boosts);
	}
}
