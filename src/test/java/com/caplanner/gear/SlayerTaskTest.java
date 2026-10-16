/*
 * Copyright (c) 2026, GuimGod
 * All rights reserved. Licensed under the BSD 2-Clause License (see LICENSE).
 */
package com.caplanner.gear;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/** The Slayer task: when the helmet is asked for, when it is left alone, and the blessing in the ammunition slot. */
public class SlayerTaskTest
{
	private static final int HEAD = 0;
	private static final int NECK = 2;

	private int nextId = 1;

	private static SetEffects.Context context(String combatClass, Boolean onTask, Boolean undead, String... owned)
	{
		SetEffects.Context c = new SetEffects.Context();
		c.combatClass = combatClass;
		c.weaponName = "";
		c.onTask = onTask;
		c.undead = undead;
		c.ownedNames = Arrays.asList(owned);
		c.ammoFree = !StyleCalc.RANGED.equals(combatClass);
		return c;
	}

	/** A piece as the plugin builds it. */
	private SwitchSet.Piece piece(String name, int slot, SetEffects.Context c, int attack, int strength)
	{
		SwitchSet.Piece p = new SwitchSet.Piece();
		p.itemId = nextId++;
		p.slot = slot;
		p.name = name;
		p.stab = attack;
		p.slash = attack;
		p.crush = attack;
		p.ranged = attack;
		p.magic = attack;
		p.strength = strength;
		p.rangedStrength = strength;
		p.setBonus = SetEffects.scoreBonus(name, c);
		p.keep = SetEffects.keep(name, c);
		p.carriedForUse = SetEffects.carriedForUse(name);
		p.mask = SetEffects.maskKind(name);
		p.salve = SetEffects.salveKind(name);
		p.blessing = c.ammoFree && SetEffects.isBlessing(name);
		return p;
	}

	private static SwitchSet.Piece[] worn(SwitchSet.Piece... pieces)
	{
		SwitchSet.Piece[] w = new SwitchSet.Piece[SwitchSet.SLOTS];
		for (SwitchSet.Piece p : pieces)
		{
			w[p.slot] = p;
		}
		return w;
	}

	@Test
	public void aHelmetOfAnyColourIsASlayerHelmet()
	{
		assertEquals(SetEffects.MASK_IMBUED, SetEffects.maskKind("Slayer helmet (i)"));
		assertEquals(SetEffects.MASK_IMBUED, SetEffects.maskKind("Black slayer helmet (i)"));
		assertEquals(SetEffects.MASK_IMBUED, SetEffects.maskKind("Tztok slayer helmet (i)"));
		assertEquals(SetEffects.MASK, SetEffects.maskKind("Hydra slayer helmet"));
		assertEquals(SetEffects.MASK, SetEffects.maskKind("Black mask (10)"));
		assertEquals(SetEffects.MASK_IMBUED, SetEffects.maskKind("Black mask (i)"));
		assertEquals(0, SetEffects.maskKind("Torva full helm"));
		assertTrue(SetEffects.keep("Purple slayer helmet (i)"));
		assertEquals(SetEffects.SALVE_EI, SetEffects.salveKind("Salve amulet(ei)"));
		assertEquals(SetEffects.SALVE_E, SetEffects.salveKind("Salve amulet (e)"));
		assertEquals(SetEffects.SALVE_I, SetEffects.salveKind("Salve amulet(i)"));
		assertEquals(SetEffects.SALVE, SetEffects.salveKind("Salve amulet"));
	}

	@Test
	public void theTaskIsMatchedByCategoryAndUnknownMeansUnknown()
	{
		List<String> known = Arrays.asList("abyssal demon", "hellhound", "aviansie", "tzhaar", "kalphite", "boss");
		assertEquals(Boolean.TRUE, SetEffects.onTask("Abyssal demons", Collections.singleton("abyssal demon"), known));
		assertEquals(Boolean.TRUE, SetEffects.onTask("Hellhounds", Arrays.asList("boss", "hellhound"), known));
		assertEquals(Boolean.TRUE, SetEffects.onTask("Aviansies", Collections.singleton("aviansie"), known));
		assertEquals(Boolean.TRUE, SetEffects.onTask("TzHaar", Collections.singleton("tzhaar"), known));
		assertEquals(Boolean.FALSE, SetEffects.onTask("Kalphite", Collections.singleton("hellhound"), known));
		assertEquals(Boolean.FALSE, SetEffects.onTask("Kalphite", Collections.<String>emptySet(), known));
		// a name the table does not have, the Bosses task, and no name at all
		assertNull(SetEffects.onTask("Something new", Collections.singleton("hellhound"), known));
		assertNull(SetEffects.onTask("Bosses", Collections.singleton("boss"), known));
		assertNull(SetEffects.onTask(null, Collections.singleton("hellhound"), known));
	}

	@Test
	public void theHelmetIsAskedForOnlyOnAMonsterOfTheTask()
	{
		for (Boolean onTask : new Boolean[]{Boolean.TRUE, Boolean.FALSE, null})
		{
			SetEffects.Context c = context(StyleCalc.MELEE, onTask, Boolean.FALSE);
			SwitchSet.Piece torva = piece("Torva full helm", HEAD, c, 0, 8);
			SwitchSet.Piece helmet = piece("Slayer helmet (i)", HEAD, c, 0, 0);
			List<Integer> missing = SwitchSet.missing(StyleCalc.MELEE, null, worn(torva), Collections.singletonList(helmet));
			assertEquals("on task: " + onTask, Boolean.TRUE.equals(onTask), missing.contains(helmet.itemId));
		}
	}

	@Test
	public void theHelmetComesOffOnlyWhenTheMonsterIsKnownNotToBeOfTheTask()
	{
		for (Boolean onTask : new Boolean[]{Boolean.TRUE, Boolean.FALSE, null})
		{
			SetEffects.Context c = context(StyleCalc.MELEE, onTask, Boolean.FALSE);
			SwitchSet.Piece helmet = piece("Black slayer helmet (i)", HEAD, c, 0, 0);
			SwitchSet.Piece torva = piece("Torva full helm", HEAD, c, 0, 8);
			List<Integer> missing = SwitchSet.missing(StyleCalc.MELEE, null, worn(helmet), Collections.singletonList(torva));
			assertEquals("on task: " + onTask, Boolean.FALSE.equals(onTask), missing.contains(torva.itemId));
		}
	}

	@Test
	public void aPlainHelmetIsNotAskedForWithABow()
	{
		SetEffects.Context c = context(StyleCalc.RANGED, Boolean.TRUE, Boolean.FALSE);
		SwitchSet.Piece masori = piece("Masori mask (f)", HEAD, c, 12, 2);
		SwitchSet.Piece plain = piece("Slayer helmet", HEAD, c, -3, 0);
		SwitchSet.Piece imbued = piece("Slayer helmet (i)", HEAD, c, 3, 0);
		assertTrue(SwitchSet.missing(StyleCalc.RANGED, null, worn(masori), Collections.singletonList(plain)).isEmpty());
		assertFalse(SwitchSet.missing(StyleCalc.RANGED, null, worn(masori), Collections.singletonList(imbued)).isEmpty());
	}

	@Test
	public void againstTheUndeadTheSalveIsAskedForAndTheHelmetIsNot()
	{
		SetEffects.Context c = context(StyleCalc.MELEE, Boolean.TRUE, Boolean.TRUE, "Salve amulet(ei)", "Slayer helmet (i)");
		SwitchSet.Piece torva = piece("Torva full helm", HEAD, c, 0, 8);
		SwitchSet.Piece rancour = piece("Amulet of rancour", NECK, c, 16, 12);
		SwitchSet.Piece helmet = piece("Slayer helmet (i)", HEAD, c, 0, 0);
		SwitchSet.Piece salve = piece("Salve amulet(ei)", NECK, c, 0, 0);
		List<Integer> missing = SwitchSet.missing(StyleCalc.MELEE, null, worn(torva, rancour), Arrays.asList(helmet, salve));
		assertEquals(Collections.singletonList(salve.itemId), missing);

		// and against something that is not undead the amulet is like any other
		SetEffects.Context alive = context(StyleCalc.MELEE, Boolean.FALSE, Boolean.FALSE, "Salve amulet(ei)");
		SwitchSet.Piece wornSalve = piece("Salve amulet(ei)", NECK, alive, 0, 0);
		SwitchSet.Piece carried = piece("Amulet of rancour", NECK, alive, 16, 12);
		assertEquals(Collections.singletonList(carried.itemId),
			SwitchSet.missing(StyleCalc.MELEE, null, worn(wornSalve), Collections.singletonList(carried)));
	}

	@Test
	public void theHelmetNeverBreaksAVoidSet()
	{
		String[] owned = {"Void melee helm", "Void knight top", "Void knight robe", "Void knight gloves", "Slayer helmet (i)"};
		SetEffects.Context c = context(StyleCalc.MELEE, Boolean.TRUE, Boolean.FALSE, owned);
		SwitchSet.Piece voidHelm = piece("Void melee helm", HEAD, c, 0, 0);
		SwitchSet.Piece helmet = piece("Slayer helmet (i)", HEAD, c, 0, 0);
		assertTrue(SwitchSet.missing(StyleCalc.MELEE, null, worn(voidHelm), Collections.singletonList(helmet)).isEmpty());
	}

	@Test
	public void aBlessingIsAskedForIntoAnEmptyAmmunitionSlotTheWeaponDoesNotUse()
	{
		SetEffects.Context melee = context(StyleCalc.MELEE, null, null);
		SwitchSet.Piece blessing = piece("Rada's blessing 4", SwitchSet.AMMO, melee, 0, 0);
		assertFalse(SetEffects.carriedForUse("Rada's blessing 4"));
		assertEquals(Collections.singletonList(blessing.itemId),
			SwitchSet.missing(StyleCalc.MELEE, null, worn(), Collections.singletonList(blessing)));
		// worn once it is put on
		assertEquals(blessing, SwitchSet.after(StyleCalc.MELEE, null, worn(), Collections.singletonList(blessing))[SwitchSet.AMMO]);

		// something already in the slot: left alone
		SwitchSet.Piece arrows = piece("Dragon arrow", SwitchSet.AMMO, melee, 0, 60);
		assertTrue(SwitchSet.missing(StyleCalc.MELEE, null, worn(arrows), Collections.singletonList(blessing)).isEmpty());

		// a bow in hand: the slot is for its arrows
		SetEffects.Context bow = context(StyleCalc.RANGED, null, null);
		SwitchSet.Piece withBow = piece("Holy blessing", SwitchSet.AMMO, bow, 0, 0);
		assertTrue(SwitchSet.missing(StyleCalc.RANGED, null, worn(), Collections.singletonList(withBow)).isEmpty());

		// ammunition is never asked for, with or without a blessing
		SwitchSet.Piece carriedArrows = piece("Dragon arrow", SwitchSet.AMMO, melee, 0, 60);
		assertTrue(SwitchSet.missing(StyleCalc.MELEE, null, worn(), Collections.singletonList(carriedArrows)).isEmpty());
	}

	@Test
	public void theHelmetAndTheAmuletDoNotStack()
	{
		SetCalc.Loadout l = new SetCalc.Loadout();
		l.mask = SetEffects.MASK_IMBUED;
		l.salve = SetEffects.SALVE_EI;
		SetCalc.Target t = new SetCalc.Target();
		t.onTask = true;
		t.undead = true;
		assertEquals(6, SetCalc.headOrNeck(l, t, StyleCalc.MELEE)[0]);
		assertEquals(20, SetCalc.headOrNeck(l, t, StyleCalc.MAGIC)[2]);
		t.undead = false;
		assertEquals(7, SetCalc.headOrNeck(l, t, StyleCalc.MELEE)[0]);
		assertEquals(23, SetCalc.headOrNeck(l, t, StyleCalc.RANGED)[0]);
		t.onTask = false;
		assertEquals(1, SetCalc.headOrNeck(l, t, StyleCalc.MELEE)[0]);
		// a helmet that is not imbued does nothing for a bow
		l.mask = SetEffects.MASK;
		t.onTask = true;
		assertEquals(1, SetCalc.headOrNeck(l, t, StyleCalc.RANGED)[0]);
	}

	@Test
	public void everyMonsterOfTheTableHasItsCategoriesRead() throws IOException
	{
		StyleData data = new StyleData();
		assertTrue(data.monster(7241).slayer.contains("abyssal demon"));
		assertTrue(data.monster(5862).slayer.contains("hellhound"));
		assertTrue(data.monster(498).slayer.contains("smoke devil"));
		assertTrue(data.monster(2).undead);
		assertFalse(data.monster(7241).undead);
		assertTrue(data.slayerCategories().size() > 100);
		assertEquals(Boolean.TRUE, SetEffects.onTask("Smoke devils", data.monster(498).slayer, data.slayerCategories()));
		assertEquals(Boolean.FALSE, SetEffects.onTask("Smoke devils", data.monster(7241).slayer, data.slayerCategories()));
	}

	@Test
	public void aDiaryItemIsNeverAskedOff()
	{
		// clue gear with a blowpipe: the diary pieces have negative ranged bonuses and stay on all the same
		SetEffects.Context c = context(StyleCalc.RANGED, null, null);
		SwitchSet.Piece headgear = piece("Kandarin headgear 4", HEAD, c, -3, 0);
		SwitchSet.Piece legs = piece("Morytania legs 4", 7, c, -7, 0);
		SwitchSet.Piece boots = piece("Fremennik sea boots 4", 10, c, -3, 0);
		SwitchSet.Piece platebody = piece("Rune platebody", 4, c, -15, 0);
		SwitchSet.Piece[] on = worn(headgear, legs, boots, platebody);
		assertEquals(Collections.singletonList(platebody.itemId),
			SwitchSet.takeOff(StyleCalc.RANGED, null, on, Collections.<SwitchSet.Piece>emptyList()));
		SwitchSet.Piece[] done = SwitchSet.after(StyleCalc.RANGED, null, on, Collections.<SwitchSet.Piece>emptyList());
		assertEquals(headgear, done[HEAD]);
		assertNull(done[4]);
	}
}
