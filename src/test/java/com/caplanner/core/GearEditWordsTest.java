package com.caplanner.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.BeforeClass;
import org.junit.Test;

/** Case 23: every line the changes to the gear write is said in Portuguese too, with the game's names left as they are. */
public class GearEditWordsTest
{
	/** Words of the plugin's English that a line said in Portuguese no longer has. */
	private static final List<String> ENGLISH = Arrays.asList("you changed", "you took", "you put", "recommendation", "asks for", "allows",
		"does not allow", "taken off", "carried for", "not in your bank", "Inventory slot", "two-handed", "The fight needs", "old change",
		"is full", "left out", "damage a second", "shield's hand", " or ", "carries", "comes off", "does no damage", "Inventory");

	private static Lang pt;

	@BeforeClass
	public static void read() throws Exception
	{
		GearEditTest.read();
		pt = Lang.read(new InputStreamReader(GearEditWordsTest.class.getResourceAsStream("/com/caplanner/lang/pt.tsv"), StandardCharsets.UTF_8));
	}

	@Test
	public void everyLineOfTheChangesIsSaidInPortuguese()
	{
		List<String> lines = new ArrayList<>();
		// what the cards of the other tests write, as they write it
		List<GearView> cards = new ArrayList<>();
		cards.add(GearEditTest.card("Vorkath", -1, GearEditTest.melee(), GearChoices.none().with("Vorkath", GearChoices.WORN, 12, 25975, 1, 28310)
			.with("Vorkath", GearChoices.WORN, 3, 20997, 1, GearChoices.UNKNOWN).with("Vorkath", GearChoices.BAG, 1, 560, 500, GearChoices.UNKNOWN)
			.with("Vorkath", GearChoices.BAG, 2, GearChoices.NOTHING, 1, GearChoices.UNKNOWN)
			.with("Vorkath", GearChoices.part("Gone|melee|"), 3, 21003, 1, GearChoices.UNKNOWN)));
		cards.add(GearEditTest.card("Vorkath", -1, GearEditTest.melee(), GearChoices.none().with("Vorkath", GearChoices.WORN, 3, 22325, 1, 0)
			.with("Vorkath", GearChoices.WORN, 5, 12954, 1, 0)
			.with("Vorkath", GearChoices.part("Special attack|melee|melee"), 12, 25975, 1, 0)
			.with("Vorkath", GearChoices.part("Special attack|melee|melee"), 5, 12954, 1, 0)));
		cards.add(GearEditTest.card("Doom of Mokhaiotl", 618, GearEditTest.melee(), GearChoices.none()
			.with("Doom of Mokhaiotl", GearChoices.WORN, 3, 22325, 1, 0)));
		cards.add(GearEditTest.card("Vorkath", 273, GearEditTest.melee(), GearChoices.none().with("Vorkath", GearChoices.WORN, 3, 20997, 1, 0)));
		for (GearView v : cards)
		{
			for (GearView.Change c : v.changes)
			{
				if (!c.line.isEmpty())
				{
					lines.add(c.line);
				}
			}
		}
		assertTrue(lines.toString(), lines.size() >= 12);
		// and every other kind of line, as GearEdit writes it
		lines.addAll(Arrays.asList(
			"Head: you took out Neitiznot faceguard",
			"Feet carried for Blue form: you put in Primordial boots",
			"Weapon: this task asks for a shield, so the recommendation stays here.",
			"Weapon: this task asks for a weapon, so the recommendation stays here.",
			"Weapon: this task asks for spells, so the recommendation stays here.",
			"Weapon: this task asks for crush hits, so the recommendation stays here.",
			"Ammunition: this task asks for dragon ammunition, so the recommendation stays here.",
			"Body: this task does not allow Bandos chestplate, so the recommendation stays here.",
			"Weapon: this task asks for dharok's greataxe, so the recommendation stays here.",
			"Weapon: this task allows only: melee, ranged, so the recommendation stays here.", "Weapon: this task asks for one of: crossbow, ballista, so the recommendation stays here.",
			"Ring: the recommendation here is now nothing.",
			"The fight needs Anti-dragon shield worn: you changed it.",
			"The fight needs Dragon hunter lance held: you changed it.",
			"The fight needs Rock hammer in the inventory: you took it out.",
			"2 old changes no longer apply.",
			"Over 28 inventory slots: the last 1 is left out.",
			"Over 28 inventory slots: the last 3 are left out.",
			"Melee · 9.9 damage a second (recommended 10.8) · 1:16 of hitting"));
		for (String line : lines)
		{
			String said = pt.of(line);
			assertNotEquals(line, line, said);
			for (String word : ENGLISH)
			{
				assertFalse(line + " -> " + said, said.contains(word));
			}
		}
	}

	@Test
	public void theWordsOfTheButtonsAndTheWindowAreSaidInPortuguese()
	{
		for (String s : new String[]{"GEAR FROM YOUR BANK, CHANGED BY YOU", "Change the gear", "Done changing",
			"Lets you change any item of this card: the set, the pieces for each part, the inventory", "Stops the slots answering a click",
			"Click a slot to change it: what is worn, the pieces carried for each part, the inventory. A change is for every task of this boss.",
			"Back to the recommendation", "Forgets every change you made to this boss's gear",
			"Forget every change you made to the gear of Vorkath? Every slot goes back to the recommendation.", "Use the recommendation",
			"Puts back what the planner recommends in this slot now", "Forget them", "Forgets the changes that have no place on this card any more",
			"Head", "Cape", "Neck", "Weapon", "Body", "Shield", "Legs", "Hands", "Feet", "Ring", "Ammunition", "A piece carried for Blue form",
			"Weapon carried for Blue form", "Inventory slot 12", "not in your bank", "Empty: click to put an item in", "change it in its part above",
			"Now: Shark", "Recommended: nothing", "Type a part of the item's name", "How many, for an item that stacks:",
			"The inventory is full: take an item out first.", "Nothing of that name goes here.",
			"Type at least two letters. What you own comes first; an item you do not own can be put in too, and is marked.", "Take it out",
			"Leaves this slot empty", "Back to the recommendation (this slot)", "Puts back what the planner recommends here", "Close",
			"Changes nothing", "owned", "Put it in", "Counted casting Fire Surge: bring the runes for it."})
		{
			assertNotEquals(s, s, pt.of(s));
		}
		// a line said whole, each place in it said where it stands
		assertEquals("Espaço 28 da mochila: você trocou Anglerfish → Shark", pt.of("Inventory slot 28: you changed Anglerfish → Shark"));
		assertEquals("Escudo levado pra Forma azul: você pôs Dragon defender", pt.of("Shield carried for Blue form: you put in Dragon defender"));
		// the game's names stay as they are
		assertTrue(pt.of("Ring: you changed Ultor ring → Lightbearer").endsWith("Ultor ring → Lightbearer"));
		assertTrue(pt.of("Weapon carried for Blue form: you changed Twisted bow → Bow of Faerdhinen (c)").contains("Forma azul"));
	}
}
