package com.caplanner.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.caplanner.gear.Fights;
import com.caplanner.gear.Gear;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * The cases a reviewer found against the first version (stage 4 of the plan), each kept as a test
 * of what the plan asks. Uses the fixture of GearEditTest; builds its own input only to add items the fixture does not have.
 */
public class GearEditReviewTest
{
	private static Gear gear;
	private static Fights fights;
	private static Bag bag;
	private static Rules rules;
	private static Lang pt;

	@BeforeClass
	public static void read() throws Exception
	{
		GearEditTest.read();
		gear = new Gear();
		try (InputStream in = Gear.class.getResourceAsStream("fights.tsv"))
		{
			fights = Fights.read(new InputStreamReader(in, StandardCharsets.UTF_8));
		}
		try (InputStream in = GearEditReviewTest.class.getResourceAsStream("/com/caplanner/bag.tsv"))
		{
			bag = Bag.read(new InputStreamReader(in, StandardCharsets.UTF_8));
		}
		try (InputStream in = GearEditReviewTest.class.getResourceAsStream("/com/caplanner/bag-wiki.tsv"))
		{
			bag.readWiki(new InputStreamReader(in, StandardCharsets.UTF_8));
		}
		try (InputStream in = GearEditReviewTest.class.getResourceAsStream("/com/caplanner/rules.tsv"))
		{
			rules = Rules.read(new InputStreamReader(in, StandardCharsets.UTF_8));
		}
		pt = Lang.read(new InputStreamReader(GearEditReviewTest.class.getResourceAsStream("/com/caplanner/lang/pt.tsv"),
			StandardCharsets.UTF_8));
	}

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

	static final Gear.Stat RUNE_DEFENDER = st(8850, "Rune defender", 5, 20, 19, 18, 0, 0, 5, 0, 0, false);
	static final Gear.Stat CLAWS = st(13652, "Dragon claws", 3, 41, 57, -4, 0, 0, 56, 0, 4, true);

	/** GearEditTest.card, with more items the game knows. */
	static GearView card(String boss, int taskId, List<Gear.Stat> owned, GearChoices choices, Gear.Stat... extra)
	{
		return card(GearEditTest.maxed(), boss, taskId, owned, choices, extra);
	}

	static GearView card(Gear.Levels levels, String boss, int taskId, List<Gear.Stat> owned, GearChoices choices, Gear.Stat... extra)
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
		in.levels = levels;
		in.have = GearEditTest.have(owned);
		in.done.addAll(Arrays.asList("desert-hard", "kourend-elite", "lumbridge-elite"));
		in.choices = choices == null ? new ArrayList<GearChoices.Pin>() : choices.of(boss);
		in.items = GearEditTest.items();
		for (Gear.Stat s : extra)
		{
			in.items.put(s.itemId, new GearCard.Item(s.itemId, s.name, false, s));
		}
		in.items.put(CLAWS.itemId, new GearCard.Item(CLAWS.itemId, CLAWS.name, false, CLAWS));
		in.cardKey = in.taskId >= 0 ? in.boss + "#" + in.taskId : in.boss;
		return GearCard.build(in);
	}

	private static Gear.Stat byId(List<Gear.Stat> owned, int id)
	{
		for (Gear.Stat s : owned)
		{
			if (s.itemId == id)
			{
				return s;
			}
		}
		return null;
	}

	private static Gear.Stat copyOf(Gear.Stat s, int id)
	{
		Gear.Stat c = st(id, s.name, s.slot, s.stab, s.slash, s.crush, s.magic, s.ranged, s.strength, s.rangedStrength, s.speed, s.twoHanded);
		c.magicDamage = s.magicDamage;
		return c;
	}

	private static boolean carries(GearView.Part p, int itemId)
	{
		if (p == null)
		{
			return false;
		}
		for (Bag.Line l : p.carried)
		{
			if (l.itemId == itemId)
			{
				return true;
			}
		}
		return false;
	}

	private static List<String> names(List<Bag.Line> lines)
	{
		List<String> out = new ArrayList<>();
		for (Bag.Line l : lines)
		{
			out.add(l.name);
		}
		return out;
	}

	// ---------------------------------------------------------------- case 8 (and 3): a slot not changed follows the recommendation

	@Test
	public void aWeaponTheRecommendationMovedToIsNotTakenOffByAShieldThePlayerPutInBefore()
	{
		// the player's set: Abyssal whip and Dragon defender recommended; they put in a Rune defender
		List<Gear.Stat> then = GearEditTest.melee();
		then.remove(GearEditTest.SCYTHE);
		then.remove(GearEditTest.MAUL);
		GearView recThen = card("Vorkath", -1, then, null);
		assertEquals(GearEditTest.WHIP.itemId, (int) recThen.itemIdBySlot.get(3));
		assertEquals(GearEditTest.DEFENDER.itemId, (int) recThen.itemIdBySlot.get(5));
		GearChoices shield = GearChoices.none().with("Vorkath", GearChoices.WORN, 5, RUNE_DEFENDER.itemId, 1, GearEditTest.DEFENDER.itemId);
		GearView madeThen = card("Vorkath", -1, then, shield, RUNE_DEFENDER);
		assertEquals(GearEditTest.WHIP.itemId, (int) madeThen.itemIdBySlot.get(3));
		assertEquals(RUNE_DEFENDER.itemId, (int) madeThen.itemIdBySlot.get(5));
		// later the bank gets a stronger two-handed weapon (an ironman's new Scythe): the recommendation moves on
		List<Gear.Stat> now = GearEditTest.melee();
		now.remove(GearEditTest.MAUL);
		GearView recNow = card("Vorkath", -1, now, null);
		assertEquals(GearEditTest.SCYTHE.itemId, (int) recNow.itemIdBySlot.get(3));
		GearView v = card("Vorkath", -1, now, shield, RUNE_DEFENDER);
		// plan, case 8: "A slot the player did not change follows the new recommendation" -- the weapon slot was never changed by the player
		assertEquals("weapon slot (never changed by the player) after the recommendation moved to the Scythe; card: " + v.itemIdBySlot
			+ ", dps " + v.dps + ", changes " + v.changes, Integer.valueOf(GearEditTest.SCYTHE.itemId), v.itemIdBySlot.get(3));
	}

	// ---------------------------------------------------------------- case 18: the damage of a part after a change of the set worn

	@Test
	public void aWornChangeThatChangesNoBonusLeavesEveryPartsDamageAsItWas() throws Exception
	{
		List<List<Gear.Stat>> sets = new ArrayList<>();
		List<Gear.Stat> ranged = GearEditTest.melee();
		ranged.add(GearEditTest.TBOW);
		ranged.add(GearEditTest.ARROWS);
		sets.add(ranged);
		List<Gear.Stat> claws = new ArrayList<>(ranged);
		claws.add(CLAWS);
		sets.add(claws);
		sets.add(GearEditTest.melee());
		// a ranged set of its own beside the melee one, so parts differ from what is worn in more than the weapon
		List<Gear.Stat> both = new ArrayList<>(claws);
		both.addAll(Arrays.asList(st(19547, "Necklace of anguish", 2, 0, 0, 0, 0, 15, 0, 5, 0, false),
			st(11828, "Armadyl chestplate", 4, 0, 0, 0, 0, 33, 0, 0, 0, false),
			st(11830, "Armadyl chainskirt", 7, 0, 0, 0, 0, 20, 0, 0, 0, false),
			st(13237, "Pegasian boots", 10, 0, 0, 0, 0, 12, 0, 0, 0, false),
			st(22109, "Ava's assembler", 1, 0, 0, 0, 0, 8, 0, 2, 0, false),
			st(26235, "Zaryte vambraces", 9, 0, 0, 0, 0, 18, 0, 2, 0, false),
			st(28310, "Venator ring", 12, 0, 0, 0, 0, 10, 0, 2, 0, false)));
		sets.add(both);
		List<Gear.Stat> heavy = new ArrayList<>(both);
		heavy.remove(GearEditTest.SCYTHE);
		sets.add(heavy);
		List<String> wrong = new ArrayList<>();
		int compared = 0;
		for (List<Gear.Stat> owned : sets)
		{
			for (String boss : GearEditTest.bosses())
			{
				GearView rec = card(boss, -1, owned, null);
				if (rec.status != GearView.Status.READY || rec.parts.isEmpty())
				{
					continue;
				}
				// a worn piece swapped for a copy of itself: the same name and bonuses, another id
				int slot = -1;
				for (int s : new int[]{12, 2, 9, 10, 1, 0, 4, 7})
				{
					if (slot < 0 && rec.itemIdBySlot.containsKey(s))
					{
						slot = s;
					}
				}
				if (slot < 0)
				{
					continue;
				}
				Gear.Stat was = byId(owned, rec.itemIdBySlot.get(slot));
				Gear.Stat same = copyOf(was, 990000 + slot);
				GearView v = card(boss, -1, owned, GearChoices.none().with(boss, GearChoices.WORN, slot, same.itemId, 1, was.itemId), same);
				assertTrue(boss, v.edited());
				if (Math.abs(v.dps - rec.dps) > 1e-6)
				{
					wrong.add(boss + ": the set itself " + rec.dps + " -> " + v.dps);
					continue;
				}
				for (GearView.Part p : rec.parts)
				{
					GearView.Part q = GearEditTest.partOf(v, p.key);
					compared++;
					if (q == null || Math.abs(q.dps - p.dps) > 1e-6)
					{
						wrong.add(boss + " / " + p.label + ": " + Math.round(p.dps * 100) / 100.0 + " -> "
							+ (q == null ? "gone" : String.valueOf(Math.round(q.dps * 100) / 100.0)) + " (carried " + names(p.carried)
							+ (p.sharedWeapon == null ? "" : ", shares the " + p.sharedWeapon + " of " + p.sharedWith) + ")");
					}
				}
			}
		}
		assertTrue("compared " + compared, compared > 20);
		assertTrue("a part's damage changed though the change to the set worn changes no bonus: " + wrong, wrong.isEmpty());
	}

	// ---------------------------------------------------------------- case 3 inside a part: a shield with a two-handed weapon

	@Test
	public void aShieldCarriedForAPartIsNotCountedTogetherWithTheTwoHandedWeaponWorn() throws Exception
	{
		List<Gear.Stat> owned = GearEditTest.melee();
		List<String> both = new ArrayList<>();
		int tried = 0;
		for (String boss : GearEditTest.bosses())
		{
			GearView rec = card(boss, -1, owned, null);
			if (rec.status != GearView.Status.READY)
			{
				continue;
			}
			Integer weapon = rec.itemIdBySlot.get(3);
			if (weapon == null || weapon != GearEditTest.SCYTHE.itemId)
			{
				continue;
			}
			for (GearView.Part p : rec.parts)
			{
				List<Integer> slots = rec.pieceSlots.get(p.key);
				if (p.npcId == 0 || p.dps <= 0 || p.key.isEmpty() || slots == null || slots.contains(3) || slots.contains(5))
				{
					continue;
				}
				// a supply taken out for room, then a defender carried for the part (the panel offers a free slot)
				GearChoices c = GearChoices.none().with(boss, GearChoices.BAG, 0, GearChoices.NOTHING, 1, GearChoices.UNKNOWN)
					.with(boss, GearChoices.part(p.key), 5, GearEditTest.DEFENDER.itemId, 1, GearChoices.NOTHING);
				GearView v = card(boss, -1, owned, c);
				GearView.Part q = GearEditTest.partOf(v, p.key);
				if (!carries(q, GearEditTest.DEFENDER.itemId))
				{
					continue;
				}
				tried++;
				Map<Integer, Gear.Stat> counted = new LinkedHashMap<>();
				for (Map.Entry<Integer, Integer> e : v.itemIdBySlot.entrySet())
				{
					counted.put(e.getKey(), byId(owned, e.getValue()));
				}
				for (Bag.Line l : q.carried)
				{
					Gear.Stat s = byId(owned, l.itemId);
					counted.put(s.slot, s);
				}
				double withBoth = gear.dpsAt(p.npcId, counted, GearEditTest.maxed(), false);
				if (counted.get(3).twoHanded && counted.containsKey(5) && Math.abs(q.dps - withBoth) < 1e-9)
				{
					both.add(boss + " / " + p.label + ": " + Math.round(q.dps * 100) / 100.0 + " dps = Scythe of vitur and Dragon defender at once"
						+ " (recommended " + Math.round(p.dps * 100) / 100.0 + ")");
				}
			}
		}
		assertTrue("tried " + tried, tried > 0);
		assertTrue("a part counted with a two-handed weapon and a shield worn together: " + both, both.isEmpty());
	}

	// ---------------------------------------------------------------- case 2: a task's rule on the supplies

	@Test
	public void aSupplyATaskForbidsIsNotCarriedOnThatTasksCard()
	{
		// Doom of Mokhaiotl, "The Praying Mantis" (617): without=super restore|prayer regeneration potion
		assertNotNull(GearCard.limitsOf(rules, 617));
		GearChoices c = GearChoices.none().with("Doom of Mokhaiotl", GearChoices.BAG, 0, 3024, 1, GearChoices.UNKNOWN);
		GearView boss = card("Doom of Mokhaiotl", -1, GearEditTest.melee(), c);
		assertTrue("the boss's own card has the player's change", GearEditTest.holds(boss, 3024));
		GearView task = card("Doom of Mokhaiotl", 617, GearEditTest.melee(), c);
		assertEquals(GearView.Status.READY, task.status);
		assertTrue(task.notes.toString(), task.notes.contains("This task forbids some of the usual supplies: they are left out of the inventory below."));
		assertFalse("617 forbids super restores, its card says so, and carries the player's Super restore(4); changes said: " + task.changes,
			GearEditTest.holds(task, 3024));
	}

	// ---------------------------------------------------------------- case 10: room made by another part

	@Test
	public void aPieceAddedOnceAnotherPartMadeRoomForItIsCarried() throws Exception
	{
		List<Gear.Stat> owned = GearEditTest.melee();
		owned.add(GearEditTest.TBOW);
		owned.add(GearEditTest.ARROWS);
		owned.add(CLAWS);
		List<String> refused = new ArrayList<>();
		int tried = 0;
		for (String boss : GearEditTest.bosses())
		{
			GearView rec = card(boss, -1, owned, null);
			if (rec.status != GearView.Status.READY)
			{
				continue;
			}
			for (int i = 0; i < rec.parts.size(); i++)
			{
				for (int j = i + 1; j < rec.parts.size(); j++)
				{
					GearView.Part a = rec.parts.get(i);
					GearView.Part b = rec.parts.get(j);
					List<Integer> aSlots = rec.pieceSlots.get(a.key);
					List<Integer> bSlots = rec.pieceSlots.get(b.key);
					if (a.carried.isEmpty() || b.carried.isEmpty() || aSlots == null || bSlots == null || bSlots.isEmpty() || aSlots.contains(12))
					{
						continue;
					}
					// first the player takes a piece of the later part out: that frees a slot of the inventory
					GearChoices first = GearChoices.none().with(boss, GearChoices.part(b.key), bSlots.get(0), GearChoices.NOTHING, 1,
						b.carried.get(0).itemId);
					GearView mid = card(boss, -1, owned, first);
					if (!mid.roomForPiece(a.key, 12))
					{
						// the panel itself would say the inventory is full: not this case
						continue;
					}
					tried++;
					// then, the panel having said there is room, a ring for the earlier part
					GearView v = card(boss, -1, owned, first.with(boss, GearChoices.part(a.key), 12, GearEditTest.LIGHTBEARER.itemId, 1,
						GearChoices.NOTHING));
					if (!carries(GearEditTest.partOf(v, a.key), GearEditTest.LIGHTBEARER.itemId))
					{
						refused.add(boss + ": Lightbearer for \"" + a.label + "\" after a piece of \"" + b.label + "\" was taken out; free slots "
							+ (Bag.SLOTS - v.inventory().size()) + "; " + v.changes);
					}
				}
			}
		}
		assertTrue("tried " + tried, tried > 0);
		assertTrue("the panel said there was room, the card refused the piece: " + refused, refused.isEmpty());
	}

	// ---------------------------------------------------------------- case 10/11: the slot number a change is said at

	@Test
	public void theInventorySlotAChangeIsSaidAtIsTheOneItIsShownIn()
	{
		String part = "Special attack|melee|melee";
		// as in GearEditTest's case 10: a supply taken out and a ring carried for the part, so the hole closes up; then the
		// supply shown after the closed hole is changed, at the place the panel maps it to
		GearChoices room = GearChoices.none().with("Vorkath", GearChoices.BAG, 2, GearChoices.NOTHING, 1, GearChoices.UNKNOWN)
			.with("Vorkath", GearChoices.part(part), 12, GearEditTest.LIGHTBEARER.itemId, 1, GearChoices.NOTHING);
		GearView fits = card("Vorkath", -1, GearEditTest.melee(), room);
		int shown = 2;
		int index = fits.supplyIndex(shown);
		assertEquals(3, index);
		// the window's title, as CaPlannerPanel.chooseSupply writes it
		String window = "Inventory slot " + (fits.carriedSlots().size() + shown + 1);
		GearView v = card("Vorkath", -1, GearEditTest.melee(), room.with("Vorkath", GearChoices.BAG, index, 3024, 1, GearChoices.UNKNOWN));
		int at = -1;
		List<int[]> inv = v.inventory();
		for (int i = 0; i < inv.size(); i++)
		{
			if (inv.get(i)[0] == 3024)
			{
				at = i + 1;
			}
		}
		assertEquals(window, "Inventory slot " + at);
		String said = null;
		for (GearView.Change ch : v.changes)
		{
			if (ch.kind == GearView.Change.Kind.CHANGED && ch.line.contains("Super restore(4)"))
			{
				said = ch.line;
			}
		}
		assertNotNull(v.changes.toString(), said);
		assertTrue("the restore is in inventory slot " + at + " (the window said \"" + window + "\"); the card says \"" + said
			+ "\"; all: " + v.changes, said.startsWith("Inventory slot " + at + ": "));
	}

	// ---------------------------------------------------------------- case 20: the report of a changed card

	@Test
	public void theReportOfACardWithTheInventoryChangedListsTheInventoryAsChanged()
	{
		GearView v = card("Vorkath", -1, GearEditTest.melee(), GearChoices.none().with("Vorkath", GearChoices.BAG, 0, 3024, 1, GearChoices.UNKNOWN));
		assertTrue(GearEditTest.holds(v, 3024));
		String body = Report.body("Vorkath", null, "", v, null, "", "English");
		assertTrue(body.contains("what follows is the card as they changed it"));
		String inventory = "";
		for (String line : body.split("\n"))
		{
			inventory = line.startsWith("- inventory:") ? line : inventory;
		}
		assertTrue("after \"what follows is the card as they changed it\", the report's inventory is the recommended one: " + inventory,
			inventory.contains("Super restore"));
	}

	// ---------------------------------------------------------------- case 7: "the recommendation moved on"

	@Test
	public void aChangeMadeOnATasksCardIsNotSaidToHaveAMovedRecommendationOnTheBossCard()
	{
		// Vorkath with a bow owned: the boss's card is ranged; "Stick 'em With the Pointy End" (273, melee only) is melee
		List<Gear.Stat> owned = GearEditTest.melee();
		owned.add(GearEditTest.TBOW);
		owned.add(GearEditTest.ARROWS);
		GearView taskCard = card("Vorkath", 273, owned, null);
		GearView bossCard = card("Vorkath", -1, owned, null);
		assertEquals(GearEditTest.SCYTHE.itemId, (int) taskCard.itemIdBySlot.get(3));
		assertEquals(GearEditTest.TBOW.itemId, (int) bossCard.itemIdBySlot.get(3));
		// the player, on the task's card, puts in a whip: what the panel keeps (chooseWorn: the card's own recommendation)
		GearChoices c = GearChoices.none().with("Vorkath", GearChoices.WORN, 3, GearEditTest.WHIP.itemId, 1, GearEditTest.SCYTHE.itemId,
			"Vorkath#273");
		assertFalse(GearEditTest.says(card("Vorkath", 273, owned, c), GearView.Change.Kind.MOVED, ""));
		// nothing was got and no version came: the boss card's recommendation is what it always was
		GearView v = card("Vorkath", -1, owned, c);
		assertFalse("nothing moved on, yet the boss's card says so (and offers a button that forgets the change on every card): " + v.changes,
			GearEditTest.says(v, GearView.Change.Kind.MOVED, ""));
	}

	// ---------------------------------------------------------------- case 2: Gear.breaks on the planner's own parts

	@Test
	public void noPartThePlannerCarriesUnderATasksRuleBreaksThatRule() throws Exception
	{
		List<List<Gear.Stat>> sets = new ArrayList<>();
		List<Gear.Stat> a = GearEditTest.melee();
		a.add(GearEditTest.TBOW);
		a.add(GearEditTest.ARROWS);
		a.add(CLAWS);
		sets.add(a);
		sets.add(GearEditTest.melee());
		List<String> broken = new ArrayList<>();
		int parts = 0;
		for (List<Gear.Stat> owned : sets)
		{
			try (java.io.BufferedReader in = new java.io.BufferedReader(new InputStreamReader(
				GearEditReviewTest.class.getResourceAsStream("/com/caplanner/rules.tsv"), StandardCharsets.UTF_8)))
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
					GearView rec = card(boss, id, owned, null);
					if (rec.status != GearView.Status.READY)
					{
						continue;
					}
					Map<Integer, Gear.Stat> worn = new LinkedHashMap<>();
					for (Map.Entry<Integer, Integer> e : rec.itemIdBySlot.entrySet())
					{
						worn.put(e.getKey(), byId(owned, e.getValue()));
					}
					String main = gear.breaks(worn, GearEditTest.maxed(), rule);
					if (main != null)
					{
						broken.add(boss + " " + id + " set worn: " + main);
					}
					for (GearView.Part p : rec.parts)
					{
						if (p.carried.isEmpty())
						{
							continue;
						}
						parts++;
						// as GearEdit.changedInto
						Map<Integer, Gear.Stat> with = new LinkedHashMap<>(worn);
						boolean shield = false;
						for (Bag.Line l : p.carried)
						{
							Gear.Stat s = byId(owned, l.itemId);
							with.put(s.slot, s);
							shield |= s.slot == 5;
						}
						if (with.get(3) != null && with.get(3).twoHanded && !shield)
						{
							with.remove(5);
						}
						String why = gear.breaks(with, GearEditTest.maxed(), rule);
						if (why != null)
						{
							broken.add(boss + " " + id + " part \"" + p.label + "\" " + names(p.carried) + ": " + why);
						}
					}
				}
			}
		}
		assertTrue("parts looked at " + parts, parts > 0);
		assertTrue("what the planner itself recommends, said to break the task's rule (no change of the player's is then checked "
			+ "there): " + broken, broken.isEmpty());
	}

	// ---------------------------------------------------------------- the card's own lines about the set, once it is changed

	@Test
	public void aSetChangedFromASpellToAWhipNoLongerSaysToBringTheRunes()
	{
		Gear.Stat kodai = st(21006, "Kodai wand", 3, 0, 0, 0, 28, 0, 0, 0, 4, false);
		kodai.magicDamage = 15;
		List<Gear.Stat> owned = new ArrayList<>(Arrays.asList(kodai));
		Gear.Levels levels = GearEditTest.maxed();
		// runes not known: any spell may be cast
		levels.runes = null;
		GearView rec = card(levels, "Vorkath", -1, owned, null);
		assertEquals(GearView.Status.READY, rec.status);
		assertEquals("Magic", rec.style);
		String casting = null;
		for (String note : rec.notes)
		{
			casting = note.startsWith("Counted casting ") ? note : casting;
		}
		assertNotNull(rec.notes.toString(), casting);
		GearView v = card(levels, "Vorkath", -1, owned, GearChoices.none().with("Vorkath", GearChoices.WORN, 3, GearEditTest.WHIP.itemId, 1, kodai.itemId));
		assertEquals("Melee", v.style);
		assertFalse("the set is now an Abyssal whip (" + v.style + ", spell " + v.editedSpell + ") and the card still says \"" + casting + "\"",
			v.notes.contains(casting));
	}

	@Test
	public void aPartTheSetWornDidIsNotSaidToBeDoneByASetThePlayerChanged()
	{
		Gear.Stat trident = st(12899, "Trident of the swamp", 3, 0, 0, 0, 25, 0, 0, 0, 4, false);
		List<Gear.Stat> owned = GearEditTest.melee();
		owned.add(GearEditTest.TBOW);
		owned.add(GearEditTest.ARROWS);
		List<String> said = new ArrayList<>();
		for (String boss : new String[]{"Zulrah", "Dagannoth Prime", "Dagannoth Rex", "Dagannoth Supreme", "Kalphite Queen", "Phantom Muspah"})
		{
			GearView rec = card(boss, -1, owned, null);
			if (rec.status != GearView.Status.READY)
			{
				continue;
			}
			Integer was = rec.itemIdBySlot.get(3);
			if (was == null)
			{
				continue;
			}
			GearView v = card(boss, -1, owned, GearChoices.none().with(boss, GearChoices.WORN, 3, trident.itemId, 1, was), trident);
			for (GearView.Part p : rec.parts)
			{
				GearView.Part q = GearEditTest.partOf(v, p.key);
				// the panel says "The set you wear does this part too." for such a part, and shows no figure
				boolean doneByTheSetWorn = q != null && !q.nothing && q.carried.isEmpty() && q.sharedWeapon == null && q.npcId == 0;
				if (doneByTheSetWorn && p.dps > 0 && q.dps < p.dps * 0.5)
				{
					said.add(boss + " / " + p.label + ": recommended " + Math.round(p.dps * 100) / 100.0 + " dps with the set worn; with the "
						+ "Trident put in, " + Math.round(q.dps * 100) / 100.0 + ", and still \"The set you wear does this part too.\"");
				}
			}
		}
		assertTrue(said.toString(), said.isEmpty());
	}

	// ---------------------------------------------------------------- case 23: a line said in Portuguese with English left in

	@Test
	public void aRuleOfTwoStylesOrTwoWeaponsIsSaidWhollyInPortuguese()
	{
		List<String> left = new ArrayList<>();
		for (String line : new String[]{
			// GearEditWordsTest's own line (TzKal-Zuk, Nibbler Chaser, 349: style=melee,ranged)
			"Weapon: this task allows only: melee, ranged, so the recommendation stays here.",
			// Gear.breaks for Tormented Demon, Rapid Reload (555: weapon=crossbow|ballista)
			"Weapon: this task asks for one of: crossbow, ballista, so the recommendation stays here.",
			// Fortis Colosseum, I Brought Mine Too (540)
			"Weapon: this task asks for one of: spear, hasta, halberd, dragon hunter lance, so the recommendation stays here."})
		{
			String said = pt.of(line);
			if (said.contains(" or "))
			{
				left.add(said);
			}
		}
		assertTrue("Portuguese lines with the English \"or\" left in: " + left, left.isEmpty());
	}
}
