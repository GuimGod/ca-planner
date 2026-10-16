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
import java.util.List;
import java.util.Map;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Adversarial review probes of the gear changes ("where it can break"). Each test
 * states what the plan asks and fails while the code does otherwise. Written by the review; not part of the suite's
 * green state until the findings are fixed.
 */
public class GearEditAdversarialTest
{
	private static Gear gear;
	private static Fights fights;
	private static Bag bag;
	private static Rules rules;

	/** A shield of the game the fixture of GearEditTest does not have. */
	static final Gear.Stat AVERNIC = stat(22322, "Avernic defender", 5, 30, 29, 28, -5, -4, 8);

	@BeforeClass
	public static void read() throws Exception
	{
		GearEditTest.read();
		gear = new Gear();
		try (InputStream in = Gear.class.getResourceAsStream("fights.tsv"))
		{
			fights = Fights.read(new InputStreamReader(in, StandardCharsets.UTF_8));
		}
		try (InputStream in = GearEditAdversarialTest.class.getResourceAsStream("/com/caplanner/bag.tsv"))
		{
			bag = Bag.read(new InputStreamReader(in, StandardCharsets.UTF_8));
		}
		try (InputStream in = GearEditAdversarialTest.class.getResourceAsStream("/com/caplanner/bag-wiki.tsv"))
		{
			bag.readWiki(new InputStreamReader(in, StandardCharsets.UTF_8));
		}
		try (InputStream in = GearEditAdversarialTest.class.getResourceAsStream("/com/caplanner/rules.tsv"))
		{
			rules = Rules.read(new InputStreamReader(in, StandardCharsets.UTF_8));
		}
	}

	private static Gear.Stat stat(int id, String name, int slot, int stab, int slash, int crush, int magic, int ranged, int str)
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
		return s;
	}

	/** As GearEditTest.card, with the items of GearEditTest and the Avernic defender known to the game. */
	private static GearView card(String boss, int taskId, List<Gear.Stat> owned, GearChoices choices)
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
		in.levels = GearEditTest.maxed();
		in.have = GearEditTest.have(owned);
		in.done.addAll(Arrays.asList("desert-hard", "kourend-elite", "lumbridge-elite"));
		in.choices = choices == null ? new ArrayList<GearChoices.Pin>() : choices.of(boss);
		in.items = GearEditTest.items();
		in.items.put(AVERNIC.itemId, new GearCard.Item(AVERNIC.itemId, AVERNIC.name, false, AVERNIC));
		in.cardKey = in.taskId >= 0 ? in.boss + "#" + in.taskId : in.boss;
		return GearCard.build(in);
	}

	private static List<Gear.Stat> meleeAndBow()
	{
		List<Gear.Stat> owned = GearEditTest.melee();
		owned.add(GearEditTest.TBOW);
		owned.add(GearEditTest.ARROWS);
		return owned;
	}

	// ------------------------------------------------------------------ case 8 (and 3)

	/**
	 * Case 8: "A slot the player did not change follows the new recommendation". The player changed the shield only (Avernic for the
	 * Dragon defender, beside a whip). The bank then gets a Scythe and the recommendation moves to it: the weapon slot,
	 * never changed by the player, must follow. Instead the old shield change is put on as if it came after the Scythe,
	 * takes the Scythe off, and the card is left with no weapon at all.
	 */
	@Test
	public void anOldShieldChangeDoesNotTakeTheNewlyRecommendedWeaponOff()
	{
		List<Gear.Stat> before = GearEditTest.melee();
		before.remove(GearEditTest.SCYTHE);
		before.remove(GearEditTest.MAUL);
		GearView rec0 = card("Vorkath", -1, before, null);
		assertEquals(GearEditTest.WHIP.itemId, (int) rec0.itemIdBySlot.get(3));
		assertEquals(GearEditTest.DEFENDER.itemId, (int) rec0.itemIdBySlot.get(5));
		GearChoices shield = GearChoices.none().with("Vorkath", GearChoices.WORN, 5, AVERNIC.itemId, 1, GearEditTest.DEFENDER.itemId);
		GearView then = card("Vorkath", -1, before, shield);
		assertEquals(GearEditTest.WHIP.itemId, (int) then.itemIdBySlot.get(3));
		assertEquals(AVERNIC.itemId, (int) then.itemIdBySlot.get(5));

		List<Gear.Stat> after = GearEditTest.melee();
		GearView rec1 = card("Vorkath", -1, after, null);
		assertEquals("the recommendation moved to the Scythe", GearEditTest.SCYTHE.itemId, (int) rec1.itemIdBySlot.get(3));
		GearView now = card("Vorkath", -1, after, shield);
		assertNotNull("the weapon slot, which the player never changed, is left empty: " + now.changes, now.itemIdBySlot.get(3));
		assertTrue("the set does no damage at all: " + now.dps, now.dps > 0);
	}

	// ------------------------------------------------------------------ case 1 / 7: one boss, several cards

	/**
	 * Case 1 ("troca no lugar errado"): a supply change is kept by its place among the supplies, for every card of the
	 * boss, but the cards of one boss do not have the same supplies (a task's rule changes the style, and the potion).
	 * On Vorkath's own card (a Twisted bow is owned, so the set is ranged) the player swaps the Ranging potion for a
	 * Super restore. On the card of "Stick 'em With the Pointy End" (273, melee only) the same place holds the Super
	 * combat potion, which the player never touched: it is taken out, and the card says the recommendation "moved".
	 */
	@Test
	public void aSupplyChangedOnTheBossCardDoesNotHitAnotherItemOnATaskCard()
	{
		List<Gear.Stat> owned = meleeAndBow();
		GearView boss = card("Vorkath", -1, owned, null);
		GearView task = card("Vorkath", 273, owned, null);
		assertEquals(2444, boss.supplyCells().get(2).itemId);
		assertEquals(12695, task.supplyCells().get(2).itemId);
		GearChoices c = GearChoices.none().with("Vorkath", GearChoices.BAG, 2, 3024, 1, 2444);
		GearView bossChanged = card("Vorkath", -1, owned, c);
		assertEquals(3024, bossChanged.supplyCells().get(2).itemId);
		GearView taskChanged = card("Vorkath", 273, owned, c);
		assertTrue("the melee task's Super combat potion is gone; the card says " + taskChanged.changes,
			GearEditTest.holds(taskChanged, 12695));
		assertFalse("nothing moved since the change was made, yet: " + taskChanged.changes,
			GearEditTest.says(taskChanged, GearView.Change.Kind.MOVED, ""));
	}

	/**
	 * Case 7: "the recommendation here is now X" is for a recommendation that moved on (a new version, a new bank). A ring
	 * put in on Vorkath's own card (ranged: no ring recommended there) is said on the melee task's card to have a
	 * recommendation that moved to the Berserker ring, which is what that card always recommended. Its "Use the
	 * recommendation" button then forgets the change for the boss's own card too.
	 */
	@Test
	public void aWornChangeMadeOnTheBossCardIsNotSaidToHaveMovedOnATaskCard()
	{
		List<Gear.Stat> owned = meleeAndBow();
		GearView boss = card("Vorkath", -1, owned, null);
		assertEquals(null, boss.itemIdBySlot.get(12));
		GearChoices ring = GearChoices.none().with("Vorkath", GearChoices.WORN, 12, GearEditTest.LIGHTBEARER.itemId, 1, GearChoices.NOTHING);
		assertFalse(GearEditTest.says(card("Vorkath", -1, owned, ring), GearView.Change.Kind.MOVED, ""));
		GearView task = card("Vorkath", 273, owned, ring);
		assertFalse("nothing moved since the change was made, yet: " + task.changes, GearEditTest.says(task, GearView.Change.Kind.MOVED, ""));
	}

	// ------------------------------------------------------------------ case 13 (and assumption 8)

	/**
	 * Case 13 / assumption 8: GearChoices.with takes every change (MOST_PER_BOSS is per boss, nothing caps the whole),
	 * but decode reads nothing of a text over MOST_TEXT. A player who changed the inventory of some forty bosses gets a
	 * text the plugin wrote itself and then reads as no change at all; the next change is written over it, and every
	 * other change of the account is gone for good.
	 */
	@Test
	public void changesThatOutgrowTheTextAreNotAllLostByTheNextOne() throws Exception
	{
		GearChoices c = GearChoices.none();
		int bosses = 0;
		for (String boss : GearEditTest.bosses())
		{
			for (int slot = 0; slot < 28; slot++)
			{
				c = c.with(boss, GearChoices.BAG, slot, 385, 1, 2434);
			}
			for (int s : GearChoices.EQUIPMENT)
			{
				c = c.with(boss, GearChoices.WORN, s, 4151, 1, 4151);
			}
			bosses++;
			if (c.encode().length() > GearChoices.MOST_TEXT)
			{
				break;
			}
		}
		String kept = c.encode();
		// a change that would pass the limit is refused (the panel says so): what was written is always read back
		assertTrue(kept.length() <= GearChoices.MOST_TEXT);
		GearChoices read = GearChoices.decode(kept);
		// what the plugin does on the next click: read the settings, add one change, write the settings
		String next = read.with("Vorkath", GearChoices.WORN, 12, 25975, 1, 11773).encode();
		assertFalse(bosses + " bosses changed, " + kept.length() + " characters written by the plugin: read back as "
			+ read.bosses().size() + " bosses, and the next change writes " + next.split("\n").length + " lines over them", read.isEmpty());
	}

	// ------------------------------------------------------------------ case 18 (what the card says of a part)

	/**
	 * Case 18: a part whose weapon the player changed is counted with the new weapon (melee against Dagannoth Prime), but
	 * the card still writes the recommended part's style: "Ranged · 1.6 damage a second with these on" under an Abyssal
	 * whip. (Its spell, for a staff changed out, stays the same way.)
	 */
	@Test
	public void aPartWhoseWeaponWasChangedSaysTheStyleOfItsNewWeapon()
	{
		List<Gear.Stat> owned = meleeAndBow();
		String key = "Dagannoth Prime||";
		GearView rec = card("Dagannoth Rex", -1, owned, null);
		assertEquals("Ranged", GearEditTest.partOf(rec, key).style);
		GearView v = card("Dagannoth Rex", -1, owned,
			GearChoices.none().with("Dagannoth Rex", GearChoices.part(key), 3, GearEditTest.WHIP.itemId, 1, GearEditTest.TBOW.itemId));
		GearView.Part p = GearEditTest.partOf(v, key);
		assertEquals(GearEditTest.WHIP.itemId, p.carried.get(0).itemId);
		assertTrue(p.dps > 0 && p.dps < GearEditTest.partOf(rec, key).dps);
		assertEquals("the part now holds a whip", "Melee", p.style);
	}

	// ------------------------------------------------------------------ case 3 (inside a part)

	/**
	 * Case 3, in a part's pieces: the worn weapon is the two-handed Scythe; the player puts a Dragon defender in the free
	 * slot of the Dagannoth Supreme part. Changing into the defender takes the Scythe off in the game, but the part is
	 * counted wearing both: more damage than the Scythe alone, and no line says the Scythe comes off.
	 */
	@Test
	public void aShieldCarriedForAPartIsNotCountedWithTheTwoHandedWeaponWorn()
	{
		List<Gear.Stat> owned = meleeAndBow();
		String key = "Dagannoth Supreme||";
		GearView rec = card("Dagannoth Rex", -1, owned, null);
		assertEquals(GearEditTest.SCYTHE.itemId, (int) rec.itemIdBySlot.get(3));
		double alone = GearEditTest.partOf(rec, key).dps;
		int last = rec.supplyCells().size() - 1;
		// room first (the inventory recommended is full): the last shark out, then the defender in for the part
		GearChoices c = GearChoices.none().with("Dagannoth Rex", GearChoices.BAG, last, GearChoices.NOTHING, 1, 385)
			.with("Dagannoth Rex", GearChoices.part(key), 5, GearEditTest.DEFENDER.itemId, 1, GearChoices.NOTHING);
		GearView v = card("Dagannoth Rex", -1, owned, c);
		GearView.Part p = GearEditTest.partOf(v, key);
		assertEquals(v.changes.toString(), 1, p.carried.size());
		assertEquals(GearEditTest.DEFENDER.itemId, p.carried.get(0).itemId);
		assertTrue("counted with the Scythe and the defender at once: " + p.dps + " against " + alone + " for the Scythe alone",
			p.dps <= alone + 1e-9);
	}

	// ------------------------------------------------------------------ case 10 and 17: a ninth piece

	/**
	 * Cases 10 and 17: GearView.carriedSlots lays a part of more than four pieces out in a block of two rows, eight
	 * places, and drops whatever comes after the eighth. Phantom Muspah's melee form carries eight pieces (its table line
	 * says carry8). The player puts a cape in the part's free slot: the change is taken and said ("Cape carried for Melee
	 * form: you put in Infernal cape"), the room check passes because the ninth piece takes no place, and the piece is in
	 * neither the inventory shown nor the setup exported. The inventory was full: in the game there is no room for it.
	 */
	@Test
	public void aNinthPieceForAPartIsCarriedOrRefusedNotDroppedFromTheInventory()
	{
		Gear.Stat infernal = stat(21295, "Infernal cape", 1, 4, 4, 4, 1, 1, 8);
		List<Gear.Stat> owned = meleeAndBow();
		String key = "Melee form||";
		GearView rec = card("Phantom Muspah", -1, owned, null);
		assertEquals(8, GearEditTest.partOf(rec, key).carried.size());
		assertEquals("the recommended inventory is full", Bag.SLOTS, rec.inventory().size());
		GearChoices c = GearChoices.none().with("Phantom Muspah", GearChoices.part(key), 1, infernal.itemId, 1, GearChoices.NOTHING);
		GearView v = cardWith("Phantom Muspah", owned, c, infernal);
		boolean refused = GearEditTest.says(v, GearView.Change.Kind.CUT, "Infernal cape");
		boolean inInventory = GearEditTest.holds(v, infernal.itemId);
		assertTrue("the part shows " + GearEditTest.partOf(v, key).carried.size() + " pieces, the inventory " + v.inventory().size()
			+ " slots without the cape; said: " + v.changes, refused || inInventory);
	}

	// ------------------------------------------------------------------ case 6: what is marked owned is not what is put in

	/**
	 * Case 6: "tenho" counts an ornament kit or an imbue as the item. The search lists the plain item first (owned
	 * first, then the shorter name) and marks it owned, but puts in the plain item's id: the card then says nothing is
	 * missing, while the setup exported holds an item the bank does not have (Torva full helm, when the bank has the
	 * (or)), and a Berserker ring counted at the plain ring's +4 strength when the bank has the (i).
	 */
	@Test
	public void anItemMarkedOwnedIsPutInAsTheOneOwned()
	{
		ItemSearch search = new ItemSearch(Arrays.asList(
			new ItemSearch.Entry(26382, "Torva full helm", 0, false),
			new ItemSearch.Entry(28254, "Torva full helm (or)", 0, false),
			new ItemSearch.Entry(6737, "Berserker ring", 12, false),
			new ItemSearch.Entry(11773, "Berserker ring (i)", 12, false)));
		Map<String, Bag.Have> have = new java.util.HashMap<>();
		have.put("torva full helm (or)", new Bag.Have(28254, 1, "Torva full helm (or)"));
		have.put("berserker ring (i)", new Bag.Have(11773, 1, "Berserker ring (i)"));
		ItemSearch.Hit torva = search.find("torva", 0, have, id -> 0).get(0);
		ItemSearch.Hit ring = search.find("berserker", 12, have, id -> 12).get(0);
		assertEquals("Torva full helm", torva.entry.name);
		assertEquals("Berserker ring", ring.entry.name);
		assertTrue("first hit '" + torva.entry.name + "' marked owned=" + torva.owned + ", put in as id " + torva.itemId,
			!torva.owned || torva.itemId == 28254);
		assertTrue("first hit '" + ring.entry.name + "' marked owned=" + ring.owned + ", put in as id " + ring.itemId,
			!ring.owned || ring.itemId == 11773);
	}

	private static GearView cardWith(String boss, List<Gear.Stat> owned, GearChoices choices, Gear.Stat extra)
	{
		GearCard.Input in = new GearCard.Input();
		in.gear = gear;
		in.fights = fights;
		in.bag = bag;
		in.rules = rules;
		in.boss = boss;
		in.taskId = -1;
		in.owned = owned;
		in.levels = GearEditTest.maxed();
		in.have = GearEditTest.have(owned);
		in.done.addAll(Arrays.asList("desert-hard", "kourend-elite", "lumbridge-elite"));
		in.choices = choices.of(boss);
		in.items = GearEditTest.items();
		in.items.put(extra.itemId, new GearCard.Item(extra.itemId, extra.name, false, extra));
		in.cardKey = in.taskId >= 0 ? in.boss + "#" + in.taskId : in.boss;
		return GearCard.build(in);
	}
}
