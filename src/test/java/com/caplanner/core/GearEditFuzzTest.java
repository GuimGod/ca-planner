package com.caplanner.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.caplanner.gear.Gear;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.BeforeClass;
import org.junit.Test;

/** Changes made at random on every boss: a card always comes, and what it gives to copy holds no more than the game does. */
public class GearEditFuzzTest
{
	private static final int[] SLOTS = {0, 1, 2, 3, 4, 5, 7, 9, 10, 12, 13};
	private static final int[] ITEMS = {0, 4151, 22325, 21003, 12954, 24271, 19553, 6570, 11832, 11834, 22981, 13239, 11773, 25975, 20997,
		11212, 385, 560, 3024, 99999};

	@BeforeClass
	public static void read() throws Exception
	{
		GearEditTest.read();
	}

	@Test
	public void changesAtRandomNeverBreakACard() throws Exception
	{
		Random random = new Random(11);
		List<Gear.Stat> owned = GearEditTest.melee();
		owned.add(GearEditTest.TBOW);
		owned.add(GearEditTest.ARROWS);
		int cards = 0;
		for (String boss : GearEditTest.bosses())
		{
			GearView rec = GearEditTest.card(boss, -1, owned, null);
			List<String> places = new ArrayList<>();
			places.add(GearChoices.WORN);
			places.add(GearChoices.BAG);
			for (GearView.Part p : rec.parts)
			{
				places.add(GearChoices.part(p.key));
			}
			places.add(GearChoices.part("Not a part||"));
			for (int round = 0; round < 6; round++)
			{
				GearChoices c = GearChoices.none();
				for (int n = random.nextInt(8); n > 0; n--)
				{
					String place = places.get(random.nextInt(places.size()));
					int slot = GearChoices.BAG.equals(place) ? random.nextInt(28) : SLOTS[random.nextInt(SLOTS.length)];
					int item = ITEMS[random.nextInt(ITEMS.length)];
					c = c.with(boss, place, slot, item, 1 + random.nextInt(3), random.nextBoolean() ? GearChoices.UNKNOWN : ITEMS[random.nextInt(ITEMS.length)]);
				}
				GearView v = GearEditTest.card(boss, -1, owned, c);
				assertNotNull(boss, v);
				assertEquals(boss, rec.status, v.status);
				assertTrue(boss + " " + c.encode(), v.inventory().size() <= Bag.SLOTS);
				assertTrue(boss, SetupText.of("CA x", v.itemIdBySlot, v.inventory()).startsWith("{\"setup\":"));
				for (Integer slot : v.itemIdBySlot.keySet())
				{
					assertTrue(boss + " slot " + slot, slot >= 0 && slot < 14);
				}
				Integer weapon = v.itemIdBySlot.get(3);
				if (weapon != null && (weapon == 22325 || weapon == 21003 || weapon == 20997))
				{
					assertTrue(boss + ": a two-handed weapon with a shield " + c.encode(), !v.itemIdBySlot.containsKey(5));
				}
				for (int i = 0; i < 28; i++)
				{
					v.supplyIndex(i);
				}
				cards++;
			}
		}
		assertTrue(cards > 300);
	}
}
