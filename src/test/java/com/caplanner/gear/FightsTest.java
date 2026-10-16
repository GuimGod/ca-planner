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
import java.util.Map;
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
		// the other two kings, and the special attack of the wiki's tribrid equipment
		assertEquals(3, plan.roles.size());
		assertEquals("Special attack", plan.roles.get(2).label);
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
		assertNull("a boss the table does not have", fights.plan(gear, "Zalcano", threeStyles(), maxed(), false, null));
		assertFalse(fights.has("Zalcano"));
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
		// small parts share a row when the whole part fits: one, then two, then two more start the next row
		List<Bag.Line> one = Arrays.asList(Bag.Line.worn(300, "spec", true, ""));
		List<Bag.Line> three = Arrays.asList(Bag.Line.worn(400, "c", true, ""), Bag.Line.worn(401, "d", true, ""),
			Bag.Line.worn(402, "e", true, ""));
		GearView shared = GearView.ready(new HashMap<>(), new HashMap<>(), "Melee", 1, 1).withParts("",
			Arrays.asList(new GearView.Part("A", "Melee", 0, one, null, "", false), new GearView.Part("B", "Magic", 5, two, null, "", false),
				new GearView.Part("C", "Ranged", 5, three, null, "", false)), new ArrayList<Bag.Line>());
		List<Bag.Line> packed = shared.carriedSlots();
		assertEquals(300, packed.get(0).itemId);
		assertEquals(200, packed.get(1).itemId);
		assertEquals(201, packed.get(2).itemId);
		assertNull("three do not fit in the one slot left: a row of their own", packed.get(3));
		assertEquals(400, packed.get(4).itemId);
		assertEquals(7, packed.size());
	}

	private static BufferedReader reader(String resource)
	{
		return new BufferedReader(new InputStreamReader(FightsTest.class.getResourceAsStream(resource), StandardCharsets.UTF_8));
	}

	/** A melee weapon for Phosani's body and a whole magic set for her totems, more than one row of it. */
	private static List<Gear.Stat> phosaniBank()
	{
		Gear.Stat scythe = item(22325, "Scythe of vitur", WEAPON);
		scythe.crush = 30;
		scythe.slash = 125;
		scythe.strength = 75;
		scythe.speed = 5;
		scythe.twoHanded = true;
		Gear.Stat torture = item(19553, "Amulet of torture", NECK);
		torture.slash = 15;
		torture.crush = 15;
		torture.strength = 10;
		Gear.Stat shadow = item(27275, "Tumeken's shadow", WEAPON);
		shadow.magic = 35;
		shadow.speed = 5;
		shadow.twoHanded = true;
		Gear.Stat occult = item(12002, "Occult necklace", NECK);
		occult.magic = 12;
		occult.magicDamage = 5;
		Gear.Stat top = item(21021, "Ancestral robe top", BODY);
		top.magic = 35;
		top.magicDamage = 3;
		Gear.Stat bottom = item(21024, "Ancestral robe bottom", 7);
		bottom.magic = 26;
		bottom.magicDamage = 3;
		Gear.Stat hat = item(21018, "Ancestral hat", 0);
		hat.magic = 8;
		hat.magicDamage = 3;
		Gear.Stat cape = item(21780, "Imbued zamorak max cape", 1);
		cape.magic = 15;
		cape.magicDamage = 2;
		Gear.Stat gloves = item(31106, "Confliction gauntlets", 9);
		gloves.magic = 20;
		gloves.magicDamage = 7;
		Gear.Stat ring = item(28313, "Magus ring", 12);
		ring.magic = 15;
		ring.magicDamage = 2;
		Gear.Stat mace = item(1434, "Dragon mace", WEAPON);
		mace.crush = 40;
		mace.strength = 39;
		mace.speed = 5;
		Gear.Stat pipe = item(28688, "Blazing blowpipe", WEAPON);
		pipe.ranged = 30;
		pipe.speed = 3;
		pipe.twoHanded = true;
		return Arrays.asList(scythe, torture, shadow, occult, top, bottom, hat, cape, gloves, ring, mace, pipe);
	}

	@Test
	public void phosanisTotemsAreTheOnesThatTakeMagicTwiceAndGetTheWholeMagicSet() throws Exception
	{
		Gear gear = new Gear();
		Fights.Plan plan = bundled().plan(gear, "Phosani's Nightmare", phosaniBank(), maxed(), false, null);
		Fights.Role totems = plan.roles.get(0);
		assertEquals("Totems", totems.label);
		assertEquals("Magic", totems.style);
		// the totem that can be attacked (the calculator's: twice the magic, weak to fire), not the charged one
		assertTrue(fightsNpcIds().contains(9435));
		assertFalse(fightsNpcIds().contains(9436));
		// more than one row: the whole magic set the bank has, up to two rows
		assertTrue(names(totems.carried).toString(), totems.carried.size() > 4 && totems.carried.size() <= 8);
		assertTrue(names(totems.carried).contains("Ancestral hat"));
	}

	private static List<Integer> fightsNpcIds() throws Exception
	{
		return bundled().npcIds();
	}

	@Test
	public void phosanisPartsTheSumsCannotWeighAreCarriedByName() throws Exception
	{
		Gear gear = new Gear();
		Fights.Plan plan = bundled().plan(gear, "Phosani's Nightmare", phosaniBank(), maxed(), false, null);
		java.util.Map<String, Fights.Role> by = new HashMap<>();
		for (Fights.Role r : plan.roles)
		{
			by.put(r.label, r);
		}
		// no Hallowfell or Ham joint: nothing of the list for the husks
		assertTrue(by.get("Husks").nothing);
		assertEquals("the body is for the scythe", "Scythe of vitur", plan.main.items.get(WEAPON).name);
		// no Hallowfell nor Elder maul: the last of the list
		assertEquals(Arrays.asList("Dragon mace"), names(by.get("Parasites").carried));
		assertEquals(0, by.get("Parasites").dps, 0);
		// no Eye of Ayak: the blowpipe
		assertEquals(Arrays.asList("Blazing blowpipe"), names(by.get("Sleepwalkers").carried));
		// no Crimson kisten nor Voidwaker: nothing for the last phase's special attacks
		assertTrue(by.get("Last phase: special attacks").nothing);
		// a weapon two parts would take is carried once
		Fights.Plan rich = bundled().plan(gear, "Phosani's Nightmare", plus(phosaniBank(), item(30000, "Hallowfell", WEAPON)),
			maxed(), false, null);
		int hallowfells = 0;
		for (Fights.Role r : rich.roles)
		{
			hallowfells += names(r.carried).contains("Hallowfell") ? 1 : 0;
		}
		assertEquals(1, hallowfells);
	}

	private static Gear.Stat eyeOfAyak()
	{
		Gear.Stat eye = item(31113, "Eye of Ayak", WEAPON);
		eye.magic = 30;
		eye.speed = 3;
		return eye;
	}

	private static Gear.Stat ward()
	{
		Gear.Stat ward = item(27251, "Elidinis' ward (f)", SHIELD);
		ward.magic = 25;
		ward.magicDamage = 5;
		return ward;
	}

	/** How much less the Eye of Ayak does on Phosani's totems than Tumeken's shadow, as a share of the shadow's. */
	private static double eyeBehindShadowOnTotems(Gear gear, List<Gear.Stat> bank)
	{
		double[] dps = new double[2];
		String[] weapons = {"tumeken's shadow", "eye of ayak"};
		for (int i = 0; i < 2; i++)
		{
			Gear.Limits only = new Gear.Limits();
			only.styles.add("magic");
			only.weapon.add(weapons[i]);
			dps[i] = gear.bestAt(9435, bank, maxed(), false, only).dps;
		}
		return (dps[0] - dps[1]) / dps[0];
	}

	private static Map<String, Fights.Role> byLabel(Fights.Plan plan)
	{
		Map<String, Fights.Role> by = new HashMap<>();
		for (Fights.Role r : plan.roles)
		{
			by.put(r.label, r);
		}
		return by;
	}

	@Test
	public void aTieGoesToTheWeaponAnotherPartCarriesAnyway() throws Exception
	{
		Gear gear = new Gear();
		// the Eye of Ayak with a ward, against the shadow on the totems: under one per cent apart (23.17 and 23.26 in game)
		List<Gear.Stat> bank = plus(plus(phosaniBank(), eyeOfAyak()), ward());
		double behind = eyeBehindShadowOnTotems(gear, bank);
		assertTrue(String.valueOf(behind), behind > 0 && behind <= Fights.TIE);
		Map<String, Fights.Role> by = byLabel(bundled().plan(gear, "Phosani's Nightmare", bank, maxed(), false, null));
		// the totems take the Eye the sleepwalkers need anyway: the shadow stays in the bank
		List<String> totems = names(by.get("Totems").carried);
		assertTrue(totems.toString(), totems.contains("Eye of Ayak") && !totems.contains("Tumeken's shadow"));
		int eyes = 0;
		for (Fights.Role r : by.values())
		{
			assertFalse(r.label, names(r.carried).contains("Tumeken's shadow"));
			eyes += names(r.carried).contains("Eye of Ayak") ? 1 : 0;
		}
		assertEquals(1, eyes);
		// and the sleepwalkers' card says which part carries it
		Fights.Role sleepwalkers = by.get("Sleepwalkers");
		assertTrue(sleepwalkers.carried.isEmpty());
		assertFalse(sleepwalkers.nothing);
		assertEquals("Eye of Ayak", sleepwalkers.sharedWeapon);
		assertEquals("Totems", sleepwalkers.sharedWith);
	}

	@Test
	public void outsideATieBothWeaponsAreCarried() throws Exception
	{
		Gear gear = new Gear();
		// no ward: the Eye is well behind the shadow on the totems
		List<Gear.Stat> bank = plus(phosaniBank(), eyeOfAyak());
		assertTrue(eyeBehindShadowOnTotems(gear, bank) > Fights.TIE);
		Map<String, Fights.Role> by = byLabel(bundled().plan(gear, "Phosani's Nightmare", bank, maxed(), false, null));
		assertTrue(names(by.get("Totems").carried).contains("Tumeken's shadow"));
		assertEquals(Arrays.asList("Eye of Ayak"), names(by.get("Sleepwalkers").carried));
		assertNull(by.get("Sleepwalkers").sharedWeapon);
	}

	private static List<Gear.Stat> plus(List<Gear.Stat> items, Gear.Stat one)
	{
		List<Gear.Stat> out = new ArrayList<>(items);
		out.add(one);
		return out;
	}

	@Test
	public void flagsAndNamesAreRead() throws Exception
	{
		Fights f = Fights.read(new StringReader("Boss\tmain\t1\tIt\n"
			+ "Boss\tswitch\t2\tA part\tmagic\t\tcarry8\n"
			+ "Boss\tweapon\t3\tAnother\tmelee\tA note.\t\tFirst axe;Second axe\n"));
		assertTrue(f.has("Boss"));
		assertEquals(Arrays.asList(1, 2, 3), f.npcIds());
	}
}
