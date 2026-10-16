package com.caplanner.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.caplanner.gear.Fights;
import com.caplanner.gear.Gear;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * The player's changes put on the card the planner works out, one test a case of the plan
 * ("where it can break"), with real items written out by hand.
 */
public class GearEditTest
{
	private static Gear gear;
	private static Fights fights;
	private static Bag bag;
	private static Rules rules;

	@BeforeClass
	public static void read() throws Exception
	{
		gear = new Gear();
		try (InputStream in = Gear.class.getResourceAsStream("fights.tsv"))
		{
			fights = Fights.read(new InputStreamReader(in, StandardCharsets.UTF_8));
		}
		try (InputStream in = GearEditTest.class.getResourceAsStream("/com/caplanner/bag.tsv"))
		{
			bag = Bag.read(new InputStreamReader(in, StandardCharsets.UTF_8));
		}
		try (InputStream in = GearEditTest.class.getResourceAsStream("/com/caplanner/bag-wiki.tsv"))
		{
			bag.readWiki(new InputStreamReader(in, StandardCharsets.UTF_8));
		}
		try (InputStream in = GearEditTest.class.getResourceAsStream("/com/caplanner/rules.tsv"))
		{
			rules = Rules.read(new InputStreamReader(in, StandardCharsets.UTF_8));
		}
	}

	// ---------------------------------------------------------------- items as the game has them

	private static Gear.Stat st(int id, String name, int slot, int stab, int slash, int crush, int magic, int ranged, int str,
		int rstr, int speed, boolean twoHanded)
	{
		Gear.Stat s = new Gear.Stat();
		s.itemId = id;
		s.name = name;
		s.slot = slot;
		s.stab = stab;
		s.slash = slash;
		s.crush = crush;
		s.magic = magic;
		s.ranged = ranged;
		s.strength = str;
		s.rangedStrength = rstr;
		s.speed = speed;
		s.twoHanded = twoHanded;
		return s;
	}

	static final Gear.Stat WHIP = st(4151, "Abyssal whip", 3, 0, 82, 0, 0, 0, 82, 0, 4, false);
	static final Gear.Stat SCYTHE = st(22325, "Scythe of vitur", 3, 70, 125, 30, 0, 0, 75, 0, 5, true);
	static final Gear.Stat MAUL = st(21003, "Elder maul", 3, 0, 0, 135, 0, 0, 147, 0, 6, true);
	static final Gear.Stat DEFENDER = st(12954, "Dragon defender", 5, 25, 24, 23, 0, 0, 6, 0, 0, false);
	static final Gear.Stat FACEGUARD = st(24271, "Neitiznot faceguard", 0, 0, 0, 0, 0, 0, 6, 0, 0, false);
	static final Gear.Stat TORTURE = st(19553, "Amulet of torture", 2, 15, 15, 15, 0, 0, 10, 0, 0, false);
	static final Gear.Stat FIRE_CAPE = st(6570, "Fire cape", 1, 1, 1, 1, 1, 1, 4, 0, 0, false);
	static final Gear.Stat CHEST = st(11832, "Bandos chestplate", 4, 0, 0, 0, 0, 0, 4, 0, 0, false);
	static final Gear.Stat TASSETS = st(11834, "Bandos tassets", 7, 0, 0, 0, 0, 0, 2, 0, 0, false);
	static final Gear.Stat GLOVES = st(22981, "Ferocious gloves", 9, 16, 16, 16, 0, 0, 14, 0, 0, false);
	static final Gear.Stat BOOTS = st(13239, "Primordial boots", 10, 2, 2, 2, 0, 0, 5, 0, 0, false);
	static final Gear.Stat BERSERKER = st(11773, "Berserker ring (i)", 12, 0, 0, 0, 0, 0, 8, 0, 0, false);
	static final Gear.Stat LIGHTBEARER = st(25975, "Lightbearer", 12, 0, 0, 0, 0, 0, 0, 0, 0, false);
	static final Gear.Stat TBOW = st(20997, "Twisted bow", 3, 0, 0, 0, 0, 70, 0, 20, 5, true);
	static final Gear.Stat ARROWS = st(11212, "Dragon arrow", 13, 0, 0, 0, 0, 0, 0, 60, 0, false);

	static List<Gear.Stat> melee()
	{
		return new ArrayList<>(Arrays.asList(WHIP, SCYTHE, MAUL, DEFENDER, FACEGUARD, TORTURE, FIRE_CAPE, CHEST, TASSETS, GLOVES, BOOTS,
			BERSERKER));
	}

	static Gear.Levels maxed()
	{
		Gear.Levels l = new Gear.Levels();
		l.attack = l.strength = l.ranged = l.magic = l.prayer = l.hitpoints = 99;
		l.piety = l.rigour = l.augury = true;
		l.runes = new HashSet<>();
		for (String skill : new String[]{"attack", "strength", "defence", "ranged", "magic", "prayer", "hitpoints", "slayer", "agility"})
		{
			l.all.put(skill, 99);
		}
		return l;
	}

	static Map<String, Bag.Have> have(List<Gear.Stat> owned)
	{
		Map<String, Bag.Have> h = new HashMap<>();
		Object[][] supplies = {{385, "Shark", 100}, {2434, "Prayer potion(4)", 20}, {12695, "Super combat potion(4)", 10},
			{2444, "Ranging potion(4)", 10}, {22209, "Extended super antifire(4)", 10}, {12913, "Anti-venom+(4)", 5},
			{8013, "Teleport to house", 50}, {560, "Death rune", 1000}};
		for (Object[] s : supplies)
		{
			h.put(((String) s[1]).toLowerCase(), new Bag.Have((Integer) s[0], (Integer) s[2], (String) s[1]));
		}
		for (Gear.Stat s : owned)
		{
			h.put(s.name.toLowerCase(), new Bag.Have(s.itemId, 1, s.name));
		}
		return h;
	}

	/** The items the changes of these tests put in, as the plugin reads them from the game. */
	static Map<Integer, GearCard.Item> items()
	{
		Map<Integer, GearCard.Item> out = new HashMap<>();
		for (Gear.Stat s : Arrays.asList(WHIP, SCYTHE, MAUL, DEFENDER, FACEGUARD, TORTURE, FIRE_CAPE, CHEST, TASSETS, GLOVES, BOOTS,
			BERSERKER, LIGHTBEARER, TBOW, ARROWS))
		{
			out.put(s.itemId, new GearCard.Item(s.itemId, s.name, false, s));
		}
		out.put(385, new GearCard.Item(385, "Shark", false, null));
		out.put(560, new GearCard.Item(560, "Death rune", true, null));
		out.put(3024, new GearCard.Item(3024, "Super restore(4)", false, null));
		return out;
	}

	static GearView card(String boss, int taskId, List<Gear.Stat> owned, GearChoices choices)
	{
		GearCard.Input in = new GearCard.Input();
		in.gear = gear;
		in.fights = fights;
		in.bag = bag;
		in.rules = rules;
		in.boss = boss;
		in.taskId = taskId;
		in.taskLimits = GearCard.limitsOf(rules, taskId);
		in.owned = owned;
		in.levels = maxed();
		in.have = have(owned);
		in.done.addAll(Arrays.asList("desert-hard", "kourend-elite", "lumbridge-elite"));
		in.choices = choices == null ? new ArrayList<GearChoices.Pin>() : choices.of(boss);
		in.items = items();
		// the card's key as the panel has it: a task with a rule of its own has a card of its own
		in.cardKey = taskId >= 0 ? boss + "#" + taskId : boss;
		return GearCard.build(in);
	}

	static GearChoices pin(GearChoices c, String boss, String place, int slot, int itemId)
	{
		return c.with(boss, place, slot, itemId, 1, GearChoices.UNKNOWN);
	}

	static boolean says(GearView v, GearView.Change.Kind kind, String part)
	{
		for (GearView.Change c : v.changes)
		{
			if (c.kind == kind && c.line.contains(part))
			{
				return true;
			}
		}
		return false;
	}

	static String export(GearView v)
	{
		return SetupText.of("CA x", v.itemIdBySlot, v.inventory());
	}

	// ---------------------------------------------------------------- the cases

	@Test
	public void aSlotChangedIsShownAndSaidAndTheRestStaysTheRecommendation()
	{
		GearView rec = card("Vorkath", -1, melee(), null);
		assertEquals(GearView.Status.READY, rec.status);
		assertEquals(11773, (int) rec.itemIdBySlot.get(12));
		GearView v = card("Vorkath", -1, melee(), GearChoices.none().with("Vorkath", GearChoices.WORN, 12, 25975, 1, 11773));
		assertTrue(v.edited());
		assertEquals(25975, (int) v.itemIdBySlot.get(12));
		assertTrue(v.changes.toString(), says(v, GearView.Change.Kind.CHANGED, "Ring: you changed Berserker ring (i) → Lightbearer"));
		for (Map.Entry<Integer, Integer> e : rec.itemIdBySlot.entrySet())
		{
			if (e.getKey() != 12)
			{
				assertEquals(e.getValue(), v.itemIdBySlot.get(e.getKey()));
			}
		}
		// case 18: the damage of the set as changed, by the same sums
		assertTrue(v.dps > 0 && v.dps < rec.dps);
		assertEquals(rec.dps, v.recommended.dps, 1e-9);
	}

	@Test
	public void aChangeTheTaskRuleBreaksShowsTheRecommendationThereAndSaysWhy()
	{
		// case 2: a two-handed weapon put in for the Doom, and the task that asks for one hand (Duel of Mokhaiotl, 618)
		GearChoices c = pin(GearChoices.none(), "Doom of Mokhaiotl", GearChoices.WORN, 3, SCYTHE.itemId);
		GearView boss = card("Doom of Mokhaiotl", -1, melee(), c);
		GearView task = card("Doom of Mokhaiotl", 618, melee(), c);
		GearView taskRec = card("Doom of Mokhaiotl", 618, melee(), null);
		assertNotEquals(SCYTHE.itemId, (int) taskRec.itemIdBySlot.get(3));
		if (boss.edited())
		{
			assertEquals(SCYTHE.itemId, (int) boss.itemIdBySlot.get(3));
		}
		assertEquals(taskRec.itemIdBySlot, task.itemIdBySlot);
		assertTrue(task.changes.toString(), says(task, GearView.Change.Kind.RULE, "Weapon: this task asks for a one-handed weapon"));
		// the change is kept: the boss's card has it
		assertEquals(SCYTHE.itemId, (int) boss.itemIdBySlot.get(3));
		// and a bow on a task of melee only (Vorkath, Stick 'em With the Pointy End, 273)
		GearChoices bow = pin(GearChoices.none(), "Vorkath", GearChoices.WORN, 3, TBOW.itemId);
		GearView melee = card("Vorkath", 273, melee(), bow);
		assertEquals(card("Vorkath", 273, melee(), null).itemIdBySlot, melee.itemIdBySlot);
		assertTrue(melee.changes.toString(), says(melee, GearView.Change.Kind.RULE, "this task allows melee only"));
		assertEquals(TBOW.itemId, (int) card("Vorkath", -1, melee(), bow).itemIdBySlot.get(3));
	}

	@Test
	public void aTwoHandedWeaponTakesTheShieldOffAndAShieldTheWeaponWhicheverCameLast()
	{
		// case 3
		List<Gear.Stat> owned = melee();
		owned.remove(SCYTHE);
		owned.remove(MAUL);
		GearView rec = card("Vorkath", -1, owned, null);
		assertEquals(DEFENDER.itemId, (int) rec.itemIdBySlot.get(5));
		GearChoices c = pin(GearChoices.none(), "Vorkath", GearChoices.WORN, 3, SCYTHE.itemId);
		GearView two = card("Vorkath", -1, owned, c);
		assertNull(two.itemIdBySlot.get(5));
		assertTrue(two.changes.toString(), says(two, GearView.Change.Kind.TAKEN_OFF, "Shield taken off: Scythe of vitur is two-handed."));
		// the shield put back in after: the weapon comes off
		GearView shield = card("Vorkath", -1, owned, pin(c, "Vorkath", GearChoices.WORN, 5, DEFENDER.itemId));
		assertNull(shield.itemIdBySlot.get(3));
		assertEquals(DEFENDER.itemId, (int) shield.itemIdBySlot.get(5));
		assertTrue(shield.changes.toString(), says(shield, GearView.Change.Kind.TAKEN_OFF, "Weapon taken off: Dragon defender goes in"));
		// and in the pieces carried for a part
		String part = GearChoices.part("Special attack|melee|melee");
		GearView spec = card("Vorkath", -1, melee(), null);
		GearView.Part p = partOf(spec, "Special attack|melee|melee");
		assertNotNull(p);
		assertTrue(p.carried.toString(), p.carried.get(0).itemId == MAUL.itemId);
		// a shield does not take off a two-handed weapon the player did not put there: it is left out, and said
		GearView refused = card("Vorkath", -1, melee(), pin(GearChoices.none(), "Vorkath", part, 5, DEFENDER.itemId));
		assertEquals(MAUL.itemId, partOf(refused, "Special attack|melee|melee").carried.get(0).itemId);
		assertTrue(refused.changes.toString(), says(refused, GearView.Change.Kind.MOVED,
			"Shield carried for Special attack: Elder maul is two-handed and recommended here, so Dragon defender is left out."));
		// the player's own two-handed weapon, then a shield: the shield comes last, and takes the weapon off
		GearChoices own = pin(pin(GearChoices.none(), "Vorkath", part, 3, SCYTHE.itemId), "Vorkath", part, 5, DEFENDER.itemId);
		GearView withShield = card("Vorkath", -1, melee(), own);
		GearView.Part q = partOf(withShield, "Special attack|melee|melee");
		assertEquals(1, q.carried.size());
		assertEquals(DEFENDER.itemId, q.carried.get(0).itemId);
		assertTrue(withShield.changes.toString(), says(withShield, GearView.Change.Kind.TAKEN_OFF, "Weapon carried for Special attack taken off"));
	}

	static GearView.Part partOf(GearView v, String key)
	{
		for (GearView.Part p : v.parts)
		{
			if (p.key.equals(key))
			{
				return p;
			}
		}
		return null;
	}

	@Test
	public void anItemOfAnotherSlotIsNotPutIn()
	{
		// case 4: a helmet in the ring's slot (only a text written by hand can say so)
		GearView v = card("Vorkath", -1, melee(), pin(GearChoices.none(), "Vorkath", GearChoices.WORN, 12, FACEGUARD.itemId));
		assertEquals(BERSERKER.itemId, (int) v.itemIdBySlot.get(12));
		assertTrue(v.changes.toString(), says(v, GearView.Change.Kind.ORPHAN, "1 old change no longer applies."));
	}

	@Test
	public void anItemNotOwnedIsPutInSaidAndCounted()
	{
		// case 5
		GearView rec = card("Vorkath", -1, melee(), null);
		GearView v = card("Vorkath", -1, melee(), pin(GearChoices.none(), "Vorkath", GearChoices.WORN, 3, TBOW.itemId)
			.with("Vorkath", GearChoices.WORN, 13, ARROWS.itemId, 1, GearChoices.UNKNOWN));
		assertEquals(TBOW.itemId, (int) v.itemIdBySlot.get(3));
		assertTrue(v.notOwned.contains(3) && v.notOwned.contains(13));
		assertTrue(says(v, GearView.Change.Kind.NOT_OWNED, "Twisted bow is not in your bank."));
		assertTrue(export(v).contains("{\"id\":20997}"));
		assertEquals("Ranged", v.style);
		assertTrue(v.dps > 0);
		assertNotEquals(rec.dps, v.dps, 1e-6);
	}

	@Test
	public void anotherDressOfAnItemAndAPlaceholderAreOwned()
	{
		// case 6
		Map<String, Bag.Have> have = new HashMap<>();
		have.put("torva full helm (or)", new Bag.Have(1, 1, "Torva full helm (or)"));
		have.put("holy scythe of vitur", new Bag.Have(2, 1, "Holy scythe of vitur"));
		have.put("salve amulet(ei)", new Bag.Have(3, 1, "Salve amulet(ei)"));
		have.put("berserker ring (i)", new Bag.Have(4, 0, "Berserker ring (i)"));
		Set<Integer> none = new HashSet<>();
		assertTrue(GearEdit.owns(100, "Torva full helm", have, none));
		assertTrue(GearEdit.owns(101, "Scythe of vitur", have, none));
		assertTrue(GearEdit.owns(102, "Salve amulet(ei)", have, none));
		assertFalse("the (e) is another item than the (ei)", GearEdit.owns(103, "Salve amulet(e)", have, none));
		assertTrue("a placeholder is owned", GearEdit.owns(104, "Berserker ring (i)", have, none));
		assertTrue(GearEdit.owns(105, "Berserker ring", have, none));
		assertFalse(GearEdit.owns(106, "Torva platebody", have, none));
		assertTrue("by its id, whatever the name", GearEdit.owns(107, "x", have, new HashSet<>(Arrays.asList(107))));
	}

	@Test
	public void aRecommendationThatMovedOnSinceTheChangeIsSaidAndTheChangeStays()
	{
		// case 7
		GearView v = card("Vorkath", -1, melee(), GearChoices.none().with("Vorkath", GearChoices.WORN, 12, 25975, 1, 28310));
		assertEquals(25975, (int) v.itemIdBySlot.get(12));
		assertTrue(v.changes.toString(), says(v, GearView.Change.Kind.MOVED, "Ring: the recommendation here is now Berserker ring (i)."));
		// and nothing is said when it is what it was
		GearView same = card("Vorkath", -1, melee(), GearChoices.none().with("Vorkath", GearChoices.WORN, 12, 25975, 1, 11773));
		assertFalse(says(same, GearView.Change.Kind.MOVED, ""));
	}

	@Test
	public void whatWasNotChangedFollowsANewRecommendationInventoryAndAll()
	{
		// case 8: a stronger weapon gets into the bank (an ironman's new bow); the ring the player changed stays
		GearChoices ring = GearChoices.none().with("Vorkath", GearChoices.WORN, 12, 25975, 1, 11773);
		GearView before = card("Vorkath", -1, melee(), ring);
		List<Gear.Stat> more = melee();
		more.add(TBOW);
		more.add(ARROWS);
		GearView rec = card("Vorkath", -1, more, null);
		GearView after = card("Vorkath", -1, more, ring);
		assertEquals("Ranged", rec.style);
		assertEquals(TBOW.itemId, (int) after.itemIdBySlot.get(3));
		assertEquals(25975, (int) after.itemIdBySlot.get(12));
		assertNotEquals(before.itemIdBySlot.get(3), after.itemIdBySlot.get(3));
		// the inventory is the new recommendation's: a ranging potion, not a super combat
		assertEquals(rec.inventory().size(), after.inventory().size());
		for (int i = 0; i < rec.inventory().size(); i++)
		{
			assertTrue(Arrays.equals(rec.inventory().get(i), after.inventory().get(i)));
		}
		assertTrue(holds(after, 2444));
		assertFalse(holds(after, 12695));
		assertTrue(holds(before, 12695));
	}

	static boolean holds(GearView v, int itemId)
	{
		for (int[] slot : v.inventory())
		{
			if (slot[0] == itemId)
			{
				return true;
			}
		}
		return false;
	}

	@Test
	public void thePlayersOwnChangeLeavesTheInventoryAsItWas()
	{
		// case 9: a bow in place of the melee weapon, and the inventory is the same slot by slot
		GearView rec = card("Vorkath", -1, melee(), null);
		GearView v = card("Vorkath", -1, melee(), pin(GearChoices.none(), "Vorkath", GearChoices.WORN, 3, TBOW.itemId));
		assertNull(v.cells);
		assertEquals(rec.inventory().size(), v.inventory().size());
		for (int i = 0; i < rec.inventory().size(); i++)
		{
			assertTrue(Arrays.equals(rec.inventory().get(i), v.inventory().get(i)));
		}
	}

	@Test
	public void aFullInventoryTakesNoMorePiecesAndItsSuppliesAreChangedSlotBySlot()
	{
		// case 10: the food fills what is left, so the inventory recommended is full
		String part = "Special attack|melee|melee";
		GearView rec = card("Vorkath", -1, melee(), null);
		assertEquals(Bag.SLOTS, rec.inventory().size());
		assertFalse(rec.roomForPiece(part, 12));
		assertTrue("a piece in place of one of its slot always fits", rec.roomForPiece(part, 3));
		GearChoices ring = pin(GearChoices.none(), "Vorkath", GearChoices.part(part), 12, LIGHTBEARER.itemId);
		GearView full = card("Vorkath", -1, melee(), ring);
		assertTrue(full.changes.toString(), says(full, GearView.Change.Kind.CUT, "The inventory is full, so Lightbearer is not carried"));
		assertEquals(1, partOf(full, part).carried.size());
		assertEquals(Bag.SLOTS, full.inventory().size());
		// one supply taken out, wherever it is, and the piece fits: the slot closes up, the others keep their order
		int carried = rec.carriedSlots().size();
		GearChoices room = pin(GearChoices.none(), "Vorkath", GearChoices.BAG, 2, GearChoices.NOTHING);
		GearView emptied = card("Vorkath", -1, melee(), room);
		assertEquals(0, emptied.inventory().get(carried + 2)[0]);
		assertTrue(emptied.roomForPiece(part, 12));
		GearView fits = card("Vorkath", -1, melee(), pin(room, "Vorkath", GearChoices.part(part), 12, LIGHTBEARER.itemId));
		assertEquals(2, partOf(fits, part).carried.size());
		assertEquals(Bag.SLOTS, fits.inventory().size());
		List<int[]> before = new ArrayList<>(rec.inventory().subList(carried, Bag.SLOTS));
		before.remove(2);
		List<int[]> after = fits.inventory().subList(Bag.SLOTS - before.size(), Bag.SLOTS);
		for (int i = 0; i < before.size(); i++)
		{
			assertTrue(i + "", Arrays.equals(before.get(i), after.get(i)));
		}
		// the slots shown map back to the supplies the changes count: past the one that closed up, one further on
		int shown = Bag.SLOTS - before.size() - fits.carriedSlots().size();
		assertEquals(0, shown);
		assertEquals(1, fits.supplyIndex(1));
		assertEquals(3, fits.supplyIndex(2));
		// a supply changed for another is in its place
		GearView other = card("Vorkath", -1, melee(), pin(GearChoices.none(), "Vorkath", GearChoices.BAG, 0, 3024));
		assertEquals(3024, other.inventory().get(carried)[0]);
		assertTrue(says(other, GearView.Change.Kind.NOT_OWNED, "Super restore(4) is not in your bank."));
		assertTrue(other.changes.toString(), says(other, GearView.Change.Kind.CHANGED, "Inventory slot " + (carried + 1) + ": you changed"));
	}

	@Test
	public void aStackHasItsAmountAndAnItemThatDoesNotStackIsOneASlot()
	{
		// case 11
		GearView v = card("Vorkath", -1, melee(), GearChoices.none().with("Vorkath", GearChoices.BAG, 1, 560, 500, GearChoices.UNKNOWN)
			.with("Vorkath", GearChoices.BAG, 2, 385, 5, GearChoices.UNKNOWN));
		int carried = v.carriedSlots().size();
		assertTrue(Arrays.equals(new int[]{560, 500}, v.inventory().get(carried + 1)));
		assertTrue(Arrays.equals(new int[]{385, 1}, v.inventory().get(carried + 2)));
		assertTrue(v.changes.toString(), says(v, GearView.Change.Kind.CHANGED, "Death rune x500"));
	}

	@Test
	public void aChangeOfAPartThereIsNoLongerIsSaidAndOneOfAnotherSetIsNot()
	{
		// case 12
		GearView gone = card("Vorkath", -1, melee(), pin(GearChoices.none(), "Vorkath", GearChoices.part("A part of old|melee|"), 3, MAUL.itemId));
		assertTrue(gone.changes.toString(), says(gone, GearView.Change.Kind.ORPHAN, "1 old change no longer applies."));
		// the special attack of a ranged set, on a card of a melee set: kept, and not shown here
		GearView other = card("Vorkath", -1, melee(), pin(GearChoices.none(), "Vorkath", GearChoices.part("Special attack|ranged|ranged"), 3,
			TBOW.itemId));
		assertFalse(other.edited());
		assertTrue(other.changes.isEmpty());
	}

	@Test
	public void theExportCarriesTheChangesAndWithoutThemIsAsBefore()
	{
		// case 17
		GearView rec = card("Vorkath", -1, melee(), null);
		GearChoices c = GearChoices.none().with("Vorkath", GearChoices.WORN, 12, 25975, 1, 11773);
		GearView v = card("Vorkath", -1, melee(), c);
		assertTrue(export(v).contains("{\"id\":25975}"));
		assertFalse(export(v).contains("{\"id\":11773}"));
		assertEquals(export(rec), export(card("Vorkath", -1, melee(), c.withoutBoss("Vorkath"))));
		assertEquals(export(rec), export(card("Vorkath", -1, melee(), c.without("Vorkath", GearChoices.WORN, 12))));
	}

	@Test
	public void aPartChangedIsOfThatPartAlone()
	{
		// case 1: the Kings are three bosses; a part's change stays in its part
		GearChoices c = pin(GearChoices.none(), "Dagannoth Rex", GearChoices.WORN, 12, LIGHTBEARER.itemId);
		assertTrue(card("Dagannoth Rex", -1, melee(), c).edited());
		assertFalse(card("Dagannoth Prime", -1, melee(), c).edited());
		String part = "Special attack|melee|melee";
		// room made for it first: the inventory recommended is full of food
		GearChoices room = pin(GearChoices.none(), "Vorkath", GearChoices.BAG, 0, GearChoices.NOTHING);
		GearView v = card("Vorkath", -1, melee(), pin(room, "Vorkath", GearChoices.part(part), 12, LIGHTBEARER.itemId));
		assertEquals(BERSERKER.itemId, (int) v.itemIdBySlot.get(12));
		boolean carried = false;
		for (Bag.Line l : partOf(v, part).carried)
		{
			carried |= l.itemId == LIGHTBEARER.itemId;
		}
		assertTrue(carried);
		for (GearView.Part p : v.parts)
		{
			for (Bag.Line l : p.carried)
			{
				assertTrue(p.key.equals(part) || l.itemId != LIGHTBEARER.itemId);
			}
		}
	}

	/** Every boss there is: a change that is the recommendation leaves the card as it is, export and damage alike. */
	@Test
	public void changesThatAreTheRecommendationLeaveEveryCardAsItIs() throws Exception
	{
		List<Gear.Stat> owned = melee();
		owned.add(TBOW);
		owned.add(ARROWS);
		int cards = 0;
		for (String boss : bosses())
		{
			GearView rec = card(boss, -1, owned, null);
			if (rec.status != GearView.Status.READY)
			{
				continue;
			}
			GearChoices c = GearChoices.none();
			for (Map.Entry<Integer, Integer> e : rec.itemIdBySlot.entrySet())
			{
				c = c.with(boss, GearChoices.WORN, e.getKey(), e.getValue(), 1, e.getValue());
			}
			GearView same = card(boss, -1, owned, c);
			assertFalse(boss, same.edited());
			assertEquals(boss, export(rec), export(same));
			assertEquals(boss, rec.dps, same.dps, 1e-9);
			cards++;
		}
		assertTrue(cards > 50);
	}

	/** Case 18: the damage the changes are counted with is the one the planner counts its own set with. */
	@Test
	public void theSetAsRecommendedIsWorthWhatThePlannerSays() throws Exception
	{
		List<Gear.Stat> owned = melee();
		owned.add(TBOW);
		owned.add(ARROWS);
		int compared = 0;
		for (String boss : bosses())
		{
			Fights.Plan plan = fights.has(boss) ? fights.plan(gear, boss, owned, maxed(), false, null) : null;
			Gear.Result r = plan != null ? plan.main : fights.has(boss) ? null : gear.best(boss, owned, maxed(), false);
			if (r == null)
			{
				continue;
			}
			Gear.Worth w = gear.worthOf(boss, plan == null ? 0 : plan.mainNpcId, r.items, maxed(), false, null);
			assertEquals(boss, r.dps, w.dps, 1e-6);
			assertEquals(boss, r.style, w.style);
			compared++;
		}
		assertTrue(compared > 50);
	}

	/** And a change made under a task's rule is counted by that rule, as the task's own set is. */
	@Test
	public void underATasksRuleTheSetIsWorthWhatTheTasksCardSays() throws Exception
	{
		List<Gear.Stat> owned = melee();
		owned.add(TBOW);
		owned.add(ARROWS);
		int compared = 0;
		try (BufferedReader in = new BufferedReader(new InputStreamReader(GearEditTest.class.getResourceAsStream("/com/caplanner/rules.tsv"),
			StandardCharsets.UTF_8)))
		{
			String line;
			while ((line = in.readLine()) != null)
			{
				String[] c = line.split("\t");
				if (line.startsWith("#") || c.length < 3 || !c[0].matches("\\d+"))
				{
					continue;
				}
				int id = Integer.parseInt(c[0]);
				String boss = c[2].substring(0, c[2].indexOf(':'));
				Gear.Limits rule = GearCard.limitsOf(rules, id);
				if (rule == null || !GearCard.knows(gear, fights, boss))
				{
					continue;
				}
				Fights.Plan plan = fights.has(boss) ? fights.plan(gear, boss, owned, maxed(), false, GearCard.limitsOf(rules, id)) : null;
				Gear.Result r = plan != null ? plan.main : fights.has(boss) ? null : gear.best(boss, owned, maxed(), false, GearCard.limitsOf(rules, id));
				if (r == null)
				{
					continue;
				}
				assertEquals(boss + " " + id, r.dps, gear.worthOf(boss, plan == null ? 0 : plan.mainNpcId, r.items, maxed(), false, rule).dps, 1e-6);
				assertNull(boss + " " + id, gear.breaks(r.items, maxed(), rule));
				compared++;
			}
		}
		assertTrue(compared > 5);
	}

	static Set<String> bosses() throws Exception
	{
		Set<String> out = new LinkedHashSet<>();
		try (BufferedReader in = new BufferedReader(new InputStreamReader(GearEditTest.class.getResourceAsStream("/com/caplanner/bosses.txt"),
			StandardCharsets.UTF_8)))
		{
			String line;
			while ((line = in.readLine()) != null)
			{
				if (!line.trim().isEmpty())
				{
					out.add(line.trim());
				}
			}
		}
		return out;
	}
}
