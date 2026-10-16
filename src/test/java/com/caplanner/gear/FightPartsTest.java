package com.caplanner.gear;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;

/**
 * The parts of a fight carried by name ("for this, that item"): a special attack with the pieces it wants, a part only
 * for a set of one style, what a task's rule leaves out, the best one to get when none is owned.
 */
public class FightPartsTest
{
	private static final int HEAD = 0;
	private static final int CAPE = 1;
	private static final int NECK = 2;
	private static final int WEAPON = 3;
	private static final int BODY = 4;
	private static final int SHIELD = 5;
	private static final int LEGS = 7;
	private static final int RING = 12;

	/** Corp from its own row; a special attack to lower its Defence with crush pieces, and one for damage with strength pieces. */
	private static final String TABLE = "Corporeal Beast\tmain\t0\tCorp\n"
		+ "Corporeal Beast\tweapon\t319\tSpec: lower its Defence\tmelee\tUntil two hit.\t\tElder maul;Dragon warhammer"
		+ "\tInquisitor's great helm;Tyrannical ring (i)|Brimstone ring;Mythical cape\n"
		+ "Corporeal Beast\tweapon\t319\tSpec: damage\tmelee\t\tstr\tVoidwaker;Osmumten's fang\tAmulet of rancour;Ultor ring|Berserker ring (i)\n"
		+ "Corporeal Beast\tweapon\t319\tOnly with a ranged set\tranged\t\twhen=ranged\tZaryte crossbow\n";

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

	/** A spear for the set, a torso, a helm with no crush and a ring of strength; the special attack weapons and their pieces. */
	private static List<Gear.Stat> bank(boolean maul, boolean voidwaker)
	{
		List<Gear.Stat> out = new ArrayList<>();
		Gear.Stat spear = item(11889, "Zamorakian hasta", WEAPON);
		spear.stab = 85;
		spear.slash = 65;
		spear.crush = 65;
		spear.strength = 75;
		spear.speed = 4;
		out.add(spear);
		Gear.Stat helm = item(10828, "Helm of neitiznot", HEAD);
		helm.strength = 3;
		out.add(helm);
		Gear.Stat ring = item(11773, "Berserker ring (i)", RING);
		ring.strength = 8;
		out.add(ring);
		Gear.Stat body = item(10551, "Fighter torso", BODY);
		body.strength = 4;
		out.add(body);
		Gear.Stat amulet = item(19553, "Amulet of torture", NECK);
		amulet.stab = amulet.slash = amulet.crush = 15;
		amulet.strength = 10;
		out.add(amulet);
		if (maul)
		{
			Gear.Stat elder = item(21003, "Elder maul", WEAPON);
			elder.twoHanded = true;
			elder.crush = 135;
			elder.strength = 147;
			elder.speed = 6;
			out.add(elder);
		}
		else
		{
			Gear.Stat dwh = item(13576, "Dragon warhammer", WEAPON);
			dwh.crush = 95;
			dwh.strength = 85;
			dwh.speed = 6;
			out.add(dwh);
		}
		Gear.Stat inq = item(24419, "Inquisitor's great helm", HEAD);
		inq.crush = 8;
		inq.strength = 4;
		out.add(inq);
		Gear.Stat tyr = item(12691, "Tyrannical ring (i)", RING);
		tyr.crush = 8;
		out.add(tyr);
		if (voidwaker)
		{
			Gear.Stat vw = item(27690, "Voidwaker", WEAPON);
			vw.slash = 70;
			vw.strength = 80;
			vw.speed = 4;
			out.add(vw);
		}
		Gear.Stat rancour = item(29801, "Amulet of rancour", NECK);
		rancour.stab = rancour.slash = rancour.crush = 25;
		rancour.strength = 12;
		out.add(rancour);
		return out;
	}

	private static Map<String, Fights.Role> parts(Fights.Plan plan)
	{
		Map<String, Fights.Role> by = new HashMap<>();
		for (Fights.Role r : plan.roles)
		{
			by.put(r.label, r);
		}
		return by;
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
	public void aSpecialAttackTakesItsWeaponAndOnlyThePiecesThatDoMoreThanWhatIsWorn() throws Exception
	{
		Gear gear = new Gear();
		Fights fights = Fights.read(new StringReader(TABLE));
		Fights.Plan plan = fights.plan(gear, "Corporeal Beast", bank(true, true), maxed(), false, null);
		// npc 0: the set is the one for the boss's own row, as for a boss of one part
		assertEquals(gear.best("Corporeal Beast", bank(true, true), maxed(), false).items.get(WEAPON).name,
			plan.main.items.get(WEAPON).name);
		Map<String, Fights.Role> by = parts(plan);
		Fights.Role defence = by.get("Spec: lower its Defence");
		// the maul, and the crush pieces: the ring has crush, the berserker worn has none; the Inquisitor's helm is worn
		// already, so it is not carried twice
		assertEquals("Inquisitor's great helm", plan.main.items.get(HEAD).name);
		assertEquals(Arrays.asList("Elder maul", "Tyrannical ring (i)"), names(defence.carried));
		assertFalse(defence.nothing);
		assertEquals(0, defence.dps, 0);
		// the Voidwaker wants strength: the amulet of rancour has more than the torture worn, the ring worn is the berserker
		Fights.Role damage = by.get("Spec: damage");
		assertTrue(names(damage.carried).toString(), names(damage.carried).contains("Voidwaker"));
		assertFalse(names(damage.carried).contains("Ultor ring"));
		assertFalse(names(damage.carried).contains("Berserker ring (i)"));
		// a part only for a ranged set is not there when the set is melee
		assertNull(by.get("Only with a ranged set"));
		assertTrue(plan.leftOut.isEmpty());
	}

	@Test
	public void withoutTheBestItTakesTheNextAndWithNoneItNamesTheBest() throws Exception
	{
		Gear gear = new Gear();
		Fights fights = Fights.read(new StringReader(TABLE));
		Map<String, Fights.Role> by = parts(fights.plan(gear, "Corporeal Beast", bank(false, false), maxed(), false, null));
		// no maul: the warhammer, with the same crush pieces
		assertEquals("Dragon warhammer", by.get("Spec: lower its Defence").carried.get(0).name);
		assertTrue(names(by.get("Spec: lower its Defence").carried).contains("Tyrannical ring (i)"));
		// no Voidwaker nor fang: nothing, and the best of the list is named, to get
		Fights.Role damage = by.get("Spec: damage");
		assertTrue(damage.nothing);
		assertTrue(damage.carried.isEmpty());
		assertEquals("Voidwaker", damage.best);
		assertNull(by.get("Spec: lower its Defence").best);
	}

	@Test
	public void aTasksRuleLeavesOutWhatItDoesNotAllow() throws Exception
	{
		Gear gear = new Gear();
		Fights fights = Fights.read(new StringReader(TABLE));
		// a task with its own weapon: neither special attack is of it
		Gear.Limits flail = new Gear.Limits();
		flail.weapon.add("zamorakian hasta");
		Fights.Plan plan = fights.plan(gear, "Corporeal Beast", bank(true, true), maxed(), false, flail);
		assertTrue(plan.roles.isEmpty());
		assertEquals(Arrays.asList("Spec: lower its Defence", "Spec: damage"), plan.leftOut);
		// a task that names the maul keeps that part only
		Gear.Limits maul = new Gear.Limits();
		maul.weapon.add("elder maul");
		maul.weapon.add("zamorakian hasta");
		plan = fights.plan(gear, "Corporeal Beast", bank(true, true), maxed(), false, maul);
		assertEquals(1, plan.roles.size());
		assertEquals("Elder maul", plan.roles.get(0).carried.get(0).name);
		assertEquals(Arrays.asList("Spec: damage"), plan.leftOut);
		// what a task excludes is not carried as a piece either
		Gear.Limits noTyrannical = new Gear.Limits();
		noTyrannical.exclude.add("tyrannical");
		plan = fights.plan(gear, "Corporeal Beast", bank(true, true), maxed(), false, noTyrannical);
		assertFalse(names(parts(plan).get("Spec: lower its Defence").carried).contains("Tyrannical ring (i)"));
		// a style the task forbids leaves the part out, and a part with no style of its own keeps only the weapons of the
		// styles allowed
		Fights styles = Fights.read(new StringReader("Corporeal Beast\tmain\t0\tCorp\n"
			+ "Corporeal Beast\tweapon\t0\tRanged part\tranged\t\t\tZaryte crossbow\n"
			+ "Corporeal Beast\tweapon\t0\tAny style\t\t\t\tZaryte crossbow;Voidwaker\n"));
		Gear.Stat zcb = item(26374, "Zaryte crossbow", WEAPON);
		zcb.ranged = 110;
		List<Gear.Stat> withBow = new ArrayList<>(bank(true, true));
		withBow.add(zcb);
		Gear.Limits melee = new Gear.Limits();
		melee.styles.add("melee");
		plan = styles.plan(gear, "Corporeal Beast", withBow, maxed(), false, melee);
		assertEquals(Arrays.asList("Ranged part"), plan.leftOut);
		assertEquals(1, plan.roles.size());
		assertEquals("Voidwaker", plan.roles.get(0).carried.get(0).name);
		// "the best" is the best the task allows: with the maul excluded and no warhammer, the warhammer is named
		Gear.Limits noMaul = new Gear.Limits();
		noMaul.exclude.add("elder maul");
		Fights.Role defence = parts(fights.plan(gear, "Corporeal Beast", bank(false, true).subList(0, 5), maxed(), false, noMaul))
			.get("Spec: lower its Defence");
		assertTrue(defence.nothing);
		assertEquals("Dragon warhammer", defence.best);
		// one hand asked for: the two-handed maul is not held, the warhammer is
		Gear.Limits oneHand = new Gear.Limits();
		oneHand.oneHanded = true;
		List<Gear.Stat> both = new ArrayList<>(bank(true, true));
		both.addAll(bank(false, false).subList(5, 6));
		Fights.Role held = parts(fights.plan(gear, "Corporeal Beast", both, maxed(), false, oneHand)).get("Spec: lower its Defence");
		assertEquals("Dragon warhammer", held.carried.get(0).name);
	}

	@Test
	public void aWeaponThatShootsGoesWithAmmunitionItFiresAndATaskOfSpellsTakesNoOtherWeapon() throws Exception
	{
		Gear gear = new Gear();
		// the set worn is melee, whatever the crossbow would do
		Fights fights = Fights.read(new StringReader("Corporeal Beast\tmain\t0\tCorp\tmelee\n"
			+ "Corporeal Beast\tweapon\t0\tSpec\tranged\t\t\tZaryte crossbow\n"
			+ "Corporeal Beast\tweapon\t0\tMaul\tmelee\t\t\tElder maul\n"));
		Gear.Stat zcb = item(26374, "Zaryte crossbow", WEAPON);
		zcb.ranged = 10;
		Gear.Stat bolts = item(21944, "Ruby dragon bolts (e)", 13);
		bolts.rangedStrength = 10;
		List<Gear.Stat> owned = new ArrayList<>(bank(true, false));
		owned.add(zcb);
		owned.add(bolts);
		Fights.Plan plan = fights.plan(gear, "Corporeal Beast", owned, maxed(), false, null);
		Fights.Role spec = parts(plan).get("Spec");
		assertEquals(Arrays.asList("Zaryte crossbow", "Ruby dragon bolts (e)"), names(spec.carried));
		assertEquals("Ranged", spec.style);
		// earth spells only (the Hueycoatl's "You're a wizard"): the maul and the crossbow are left out, not carried
		Gear.Limits earth = new Gear.Limits();
		earth.spellElement = "earth";
		Gear.Stat staff = item(1381, "Staff of air", WEAPON);
		staff.magic = 10;
		owned.add(staff);
		Fights.Plan wizard = fights.plan(gear, "Corporeal Beast", owned, maxed(), false, earth);
		if (wizard != null)
		{
			assertTrue(wizard.roles.isEmpty());
			assertEquals(Arrays.asList("Spec", "Maul"), wizard.leftOut);
		}
	}

	@Test
	public void oneAmmunitionAPartAndTheBundledCorpAsTheWikiHasIt() throws Exception
	{
		Gear gear = new Gear();
		Fights shield = Fights.read(new StringReader("Corporeal Beast\tmain\t0\tCorp\tmelee\n"
			+ "Corporeal Beast\tweapon\t0\tShield\tranged\t\t\tZaryte crossbow\tSapphire dragon bolts (e)\n"));
		Gear.Stat zcb = item(26374, "Zaryte crossbow", WEAPON);
		zcb.ranged = 10;
		Gear.Stat ruby = item(21944, "Ruby dragon bolts (e)", 13);
		ruby.rangedStrength = 10;
		Gear.Stat sapphire = item(21932, "Sapphire dragon bolts (e)", 13);
		sapphire.rangedStrength = 10;
		List<Gear.Stat> owned = new ArrayList<>(bank(true, false));
		owned.addAll(Arrays.asList(zcb, ruby, sapphire));
		List<String> carried = names(fights(shield, gear, owned).get("Shield").carried);
		assertEquals(carried.toString(), 2, carried.size());
		assertEquals("Zaryte crossbow", carried.get(0));
		assertTrue("the ammunition the part names", carried.contains("Sapphire dragon bolts (e)"));
		// the bundled table: the Corp's two special attacks of the wiki's Quick pool, with the maul's crush pieces
		Fights bundled;
		try (java.io.BufferedReader in = new java.io.BufferedReader(new java.io.InputStreamReader(
			FightPartsTest.class.getResourceAsStream("/com/caplanner/gear/fights.tsv"), java.nio.charset.StandardCharsets.UTF_8)))
		{
			bundled = Fights.read(in);
		}
		Map<String, Fights.Role> corp = fights(bundled, gear, bank(true, true));
		assertEquals("Elder maul", corp.get("Special attack: lower its Defence").carried.get(0).name);
		assertTrue(names(corp.get("Special attack: lower its Defence").carried).contains("Tyrannical ring (i)"));
		assertEquals("Voidwaker", corp.get("Special attack: damage").carried.get(0).name);
		// no maul, a warhammer: the warhammer; with neither, the maul is named as the best
		assertEquals("Dragon warhammer", fights(bundled, gear, bank(false, true)).get("Special attack: lower its Defence").carried.get(0).name);
		Fights.Role none = fights(bundled, gear, bank(false, true).subList(0, 5)).get("Special attack: lower its Defence");
		assertTrue(none.nothing);
		assertEquals("Elder maul", none.best);
	}

	private static Map<String, Fights.Role> fights(Fights table, Gear gear, List<Gear.Stat> owned)
	{
		return parts(table.plan(gear, "Corporeal Beast", owned, maxed(), false, null));
	}

	@Test
	public void aWeaponTheTaskRefusesIsSaidToBeLeftOutNotMissing() throws Exception
	{
		Gear gear = new Gear();
		Fights fights = Fights.read(new StringReader(TABLE));
		// the maul is owned, the task excludes it, the warhammer is not owned: the part is left out, not "you own none"
		Gear.Limits noMaul = new Gear.Limits();
		noMaul.exclude.add("elder maul");
		Fights.Plan plan = fights.plan(gear, "Corporeal Beast", bank(true, true), maxed(), false, noMaul);
		assertNull(parts(plan).get("Spec: lower its Defence"));
		assertTrue(plan.leftOut.contains("Spec: lower its Defence"));
	}

	@Test
	public void aPieceNeverTakesThePlaceOfWhatTheFightMakesThePlayerWear() throws Exception
	{
		Gear gear = new Gear();
		Fights fights = Fights.read(new StringReader(TABLE));
		Gear.Limits ring = new Gear.Limits();
		ring.mustWear.add(Arrays.asList("berserker ring"));
		Fights.Plan plan = fights.plan(gear, "Corporeal Beast", bank(true, true), maxed(), false, ring);
		assertEquals("Berserker ring (i)", plan.main.items.get(RING).name);
		assertFalse(names(parts(plan).get("Spec: lower its Defence").carried).contains("Tyrannical ring (i)"));
	}

	@Test
	public void aWeaponTwoPartsShareIsSaidWhenThePiecesOfTheSecondComeAlong() throws Exception
	{
		Gear gear = new Gear();
		Fights fights = Fights.read(new StringReader("Corporeal Beast\tmain\t0\tCorp\n"
			+ "Corporeal Beast\tweapon\t0\tFirst\tmelee\t\t\tVoidwaker\n"
			+ "Corporeal Beast\tweapon\t0\tSecond\tmelee\t\t\tVoidwaker\tTyrannical ring (i)\n"));
		Gear.Stat vwCrush = item(27690, "Voidwaker", WEAPON);
		vwCrush.crush = 80;
		List<Gear.Stat> owned = new ArrayList<>(bank(true, false));
		owned.add(vwCrush);
		Fights.Role second = parts(fights.plan(gear, "Corporeal Beast", owned, maxed(), false, null)).get("Second");
		assertEquals(Arrays.asList("Tyrannical ring (i)"), names(second.carried));
		assertEquals("Voidwaker", second.sharedWeapon);
		assertEquals("First", second.sharedWith);
	}

	@Test
	public void aSpecialAttackOfTheWeaponWornIsNotCarriedAgain() throws Exception
	{
		Gear gear = new Gear();
		Fights fights = Fights.read(new StringReader("Corporeal Beast\tmain\t0\tCorp\n"
			+ "Corporeal Beast\tweapon\t319\tSpec\tmelee\t\t\tZamorakian hasta;Elder maul\tTyrannical ring (i)\n"));
		Fights.Plan plan = fights.plan(gear, "Corporeal Beast", bank(true, false), maxed(), false, null);
		assertEquals("Zamorakian hasta", plan.main.items.get(WEAPON).name);
		Fights.Role spec = plan.roles.get(0);
		// the hasta is worn: no second one, and its pieces judged against the set do not come along with nothing to carry
		assertFalse(names(spec.carried).contains("Zamorakian hasta"));
		assertFalse(spec.nothing);
	}

	@Test
	public void piecesAreJudgedByTheWeaponsOwnKindOfHit() throws Exception
	{
		Gear.Stat maul = item(1, "Elder maul", WEAPON);
		maul.crush = 135;
		Gear.Stat helm = item(2, "Inquisitor's great helm", HEAD);
		helm.crush = 8;
		helm.slash = -1;
		assertEquals(8, Fights.bonus(helm, maul, false), 0);
		Gear.Stat bow = item(3, "Zaryte crossbow", WEAPON);
		bow.ranged = 110;
		Gear.Stat body = item(4, "Masori body (f)", BODY);
		body.ranged = 43;
		body.rangedStrength = 4;
		assertEquals(43, Fights.bonus(body, bow, false), 0);
		assertEquals(4, Fights.bonus(body, bow, true), 0);
		Gear.Stat cape = item(5, "Mythical cape", CAPE);
		cape.strength = 1;
		assertEquals(1, Fights.bonus(cape, maul, true), 0);
		Gear.Stat ward = item(6, "Shield", SHIELD);
		Gear.Stat legs = item(7, "Legs", LEGS);
		assertEquals(0, Fights.bonus(ward, maul, false) + Fights.bonus(legs, maul, false), 0);
	}
}
