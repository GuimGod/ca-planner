package com.caplanner.gear;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.caplanner.core.Bag;
import com.caplanner.core.GearView;
import com.caplanner.core.Requirements;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.Test;

/** Fights of several parts: the set is for the first, and pieces are carried for the others. */
public class FightsTest
{
	private static final int NECK = 2;
	private static final int WEAPON = 3;
	private static final int BODY = 4;
	private static final int SHIELD = 5;
	private static final int AMMO = 13;

	private static Gear.Stat item(int id, String name, int slot)
	{
		Gear.Stat s = new Gear.Stat();
		s.itemId = id;
		s.name = name;
		s.slot = slot;
		return s;
	}

	private static Gear.Levels maxed()
	{
		Gear.Levels l = new Gear.Levels();
		l.attack = l.strength = l.ranged = l.magic = l.prayer = l.hitpoints = 99;
		l.piety = l.rigour = l.augury = true;
		return l;
	}

	/** A melee, a ranged and a magic weapon, each with a piece or two of its own. */
	private static List<Gear.Stat> threeStyles()
	{
		Gear.Stat whip = item(4151, "Abyssal whip", WEAPON);
		whip.slash = 82;
		whip.strength = 82;
		whip.speed = 4;
		Gear.Stat defender = item(12954, "Dragon defender", SHIELD);
		defender.slash = 24;
		defender.strength = 6;
		Gear.Stat torture = item(19553, "Amulet of torture", NECK);
		torture.slash = 15;
		torture.strength = 10;
		Gear.Stat bow = item(20997, "Twisted bow", WEAPON);
		bow.twoHanded = true;
		bow.ranged = 70;
		bow.rangedStrength = 20;
		bow.speed = 5;
		Gear.Stat arrows = item(11212, "Dragon arrow", AMMO);
		arrows.rangedStrength = 60;
		Gear.Stat anguish = item(19547, "Necklace of anguish", NECK);
		anguish.ranged = 15;
		anguish.rangedStrength = 5;
		Gear.Stat blessing = item(20220, "Holy blessing", AMMO);
		Gear.Stat staff = item(1405, "Mystic air staff", WEAPON);
		staff.magic = 14;
		staff.speed = 5;
		Gear.Stat occult = item(12002, "Occult necklace", NECK);
		occult.magic = 12;
		occult.magicDamage = 5;
		Gear.Stat robe = item(21021, "Ancestral robe top", BODY);
		robe.magic = 35;
		robe.magicDamage = 3;
		return Arrays.asList(whip, defender, torture, bow, arrows, anguish, blessing, staff, occult, robe);
	}

	private static Fights bundled() throws Exception
	{
		try (BufferedReader in = reader("/com/caplanner/gear/fights.tsv"))
		{
			return Fights.read(in);
		}
	}

	private static List<String> names(List<Gear.Stat> items)
	{
		List<String> out = new ArrayList<>();
		for (Gear.Stat s : items)
		{
			out.add(s.name);
		}
		return out;
	}

	@Test
	public void theSetIsForTheFirstPartAndPiecesAreCarriedForTheOthers() throws Exception
	{
		Gear gear = new Gear();
		Fights fights = bundled();
		Fights.Plan plan = fights.plan(gear, "Dagannoth Rex", threeStyles(), maxed(), false, null);
		// one row of pieces a part as a rule, two against the clock
		for (Fights.Role role : plan.roles)
		{
			assertTrue(role.carried.size() <= 4);
		}
		for (Fights.Role role : fights.plan(gear, "Dagannoth Rex", threeStyles(), maxed(), false, null, Fights.MOST_CARRIED_FOR_SPEED).roles)
		{
			assertTrue(role.carried.size() <= 8);
		}
		assertNotNull(plan);
		assertEquals("Magic", plan.main.style);
		assertEquals("Dagannoth Rex", plan.mainLabel);
		assertEquals("Mystic air staff", plan.main.items.get(WEAPON).name);
		assertEquals(2, plan.roles.size());
		Fights.Role prime = plan.roles.get(0);
		assertEquals("Dagannoth Prime", prime.label);
		assertEquals("Ranged", prime.style);
		// the weapon first, then its ammunition; a blessing is never carried
		assertEquals(Arrays.asList("Twisted bow", "Dragon arrow", "Necklace of anguish"), names(prime.carried));
		assertTrue(prime.dps > 1);
		Fights.Role supreme = plan.roles.get(1);
		assertEquals("Melee", supreme.style);
		assertEquals(Arrays.asList("Abyssal whip", "Dragon defender", "Amulet of torture"), names(supreme.carried));
		// each king's own page wears the set for that king
		assertEquals("Ranged", fights.plan(gear, "Dagannoth Prime", threeStyles(), maxed(), false, null).main.style);
		assertEquals("Melee", fights.plan(gear, "Dagannoth Supreme", threeStyles(), maxed(), false, null).main.style);
		// with one style owned the others are said to be missing, not made up
		Fights.Plan onlyMelee = fights.plan(gear, "Dagannoth Supreme", threeStyles().subList(0, 3), maxed(), false, null);
		assertEquals("Melee", onlyMelee.main.style);
		assertTrue(onlyMelee.roles.get(0).nothing || onlyMelee.roles.get(0).carried.isEmpty());
		assertNull("a boss the table does not have", fights.plan(gear, "Vorkath", threeStyles(), maxed(), false, null));
		assertFalse(fights.has("Vorkath"));
	}

	@Test
	public void aPartThatAsksForAStyleGetsThatStyleAndATaskStillHolds() throws Exception
	{
		Gear gear = new Gear();
		Fights fights = bundled();
		// the totems of the Nightmare are for magic, whatever else would do more
		Fights.Plan nightmare = fights.plan(gear, "The Nightmare", threeStyles(), maxed(), false, null);
		assertEquals("Melee", nightmare.main.style);
		assertEquals("Magic", nightmare.roles.get(0).style);
		assertEquals("Mystic air staff", nightmare.roles.get(0).carried.get(0).name);
		assertNotNull(nightmare.roles.get(0).spell);
		// a task that allows melee only leaves the other parts with nothing, rather than breaking its rule
		Gear.Limits melee = new Gear.Limits();
		melee.styles.add("melee");
		Fights.Plan held = fights.plan(gear, "The Nightmare", threeStyles(), maxed(), false, melee);
		assertTrue(held.roles.get(0).nothing);
		// elementals want a spell that is cast: a staff with an attack of its own is not it
		Gear.Stat shadow = item(27275, "Tumeken's shadow", WEAPON);
		shadow.twoHanded = true;
		shadow.magic = 35;
		shadow.speed = 5;
		List<Gear.Stat> owned = new ArrayList<>(threeStyles());
		owned.add(shadow);
		Fights.Plan titans = fights.plan(gear, "Royal Titans", owned, maxed(), false, null);
		Fights.Role elementals = titans.roles.get(1);
		assertEquals("Mystic air staff", elementals.carried.get(0).name);
		assertEquals("no figure where the sums do not know the part", 0, elementals.dps, 0);
		assertEquals(1, elementals.carried.size());
	}

	@Test
	public void theBundledTableIsSound() throws Exception
	{
		Gear gear = new Gear();
		Fights fights = bundled();
		Set<String> known = new HashSet<>();
		try (BufferedReader in = reader("/com/caplanner/bosses.txt"))
		{
			String line;
			while ((line = in.readLine()) != null)
			{
				known.add(Requirements.key(line));
			}
		}
		assertTrue(fights.bosses().size() >= 10);
		for (String boss : fights.bosses())
		{
			assertTrue("not a Combat Achievements boss: " + boss, known.contains(boss));
		}
		for (int id : fights.npcIds())
		{
			assertTrue("no monster of id " + id, gear.hasMonster(id));
		}
		// a boss whose first line is not its main part is left out
		assertFalse(Fights.read(new StringReader("X\tswitch\t2265\tA\t\t\n")).has("X"));
		assertTrue(Fights.read(new StringReader("# c\nX\tmain\t2265\tA\t\t\nbad\n")).has("x"));
	}

	@Test
	public void piecesToChangeIntoSitInRowsOfTheirOwnBeforeTheSupplies()
	{
		List<Bag.Line> six = new ArrayList<>();
		for (int i = 0; i < 6; i++)
		{
			six.add(Bag.Line.worn(100 + i, "piece " + i, true, ""));
		}
		List<Bag.Line> two = Arrays.asList(Bag.Line.worn(200, "a", true, ""), Bag.Line.worn(201, "b", true, ""));
		GearView view = GearView.ready(new HashMap<>(), new HashMap<>(), "Melee", 1, 1).withParts("First",
			Arrays.asList(new GearView.Part("Second", "Ranged", 5, six, null, "", false),
				new GearView.Part("Third", "Magic", 5, two, null, "", false),
				new GearView.Part("Fourth", "", 0, new ArrayList<Bag.Line>(), null, "", true)), new ArrayList<Bag.Line>());
		List<Bag.Line> slots = view.carriedSlots();
		// more than four: down two rows a column at a time (first on top, second under it, third beside the first)
		assertEquals(100, slots.get(0).itemId);
		assertEquals(101, slots.get(4).itemId);
		assertEquals(102, slots.get(1).itemId);
		assertEquals(103, slots.get(5).itemId);
		assertEquals(104, slots.get(2).itemId);
		assertEquals(105, slots.get(6).itemId);
		assertNull(slots.get(3));
		assertNull(slots.get(7));
		// four or fewer: one row, and nothing trails after the last piece
		assertEquals(200, slots.get(8).itemId);
		assertEquals(201, slots.get(9).itemId);
		assertEquals(10, slots.size());
		List<int[]> inventory = view.inventory();
		assertEquals(10, inventory.size());
		assertEquals(0, inventory.get(3)[0]);
		assertEquals(105, inventory.get(6)[0]);
		assertEquals("First", view.wornFor);
	}

	private static BufferedReader reader(String resource)
	{
		return new BufferedReader(new InputStreamReader(FightsTest.class.getResourceAsStream(resource), StandardCharsets.UTF_8));
	}
}
