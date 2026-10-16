package com.caplanner.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.BeforeClass;
import org.junit.Test;

/** Review probes on the kept text of the changes (case 13). */
public class GearChoicesReviewTest
{
	@BeforeClass
	public static void read() throws Exception
	{
		GearEditTest.read();
	}

	@Test
	public void aChangeMadeAfterReadingAnOrderAtTheTopIsStillTheLatest()
	{
		// a text written by hand or damaged: one change whose order is the largest a long holds
		GearChoices read = GearChoices.decode("v1\nVorkath\tworn\t5\t12954\t1\t-1\t" + Long.MAX_VALUE);
		assertEquals("the line is taken, not left out", 1, read.of("Vorkath").size());
		GearChoices now = read.with("Vorkath", GearChoices.WORN, 3, GearEditTest.SCYTHE.itemId, 1, GearChoices.UNKNOWN);
		List<GearChoices.Pin> pins = now.of("Vorkath");
		// on the card, the order decides which of a two-handed weapon and a shield stands
		List<com.caplanner.gear.Gear.Stat> owned = GearEditTest.melee();
		owned.remove(GearEditTest.SCYTHE);
		owned.remove(GearEditTest.MAUL);
		GearView v = GearEditTest.card("Vorkath", -1, owned, now);
		assertEquals("the change just made should be the latest; order " + pins + "; card weapon " + v.itemIdBySlot.get(3) + ", shield "
			+ v.itemIdBySlot.get(5) + ", " + v.changes, 3, pins.get(pins.size() - 1).slot);
	}

	@Test
	public void changesTooManyForTheTextAreNotAllLostAtOnce() throws Exception
	{
		// every slot changed (28 of the inventory, 11 worn) on boss after boss, as the plugin takes them
		GearChoices c = GearChoices.none();
		int made = 0;
		int bosses = 0;
		for (String boss : GearEditTest.bosses())
		{
			for (int slot = 0; slot < 28; slot++)
			{
				c = c.with(boss, GearChoices.BAG, slot, 385, 1, 2434);
				made++;
			}
			for (int s : GearChoices.EQUIPMENT)
			{
				c = c.with(boss, GearChoices.WORN, s, 4151, 1, 4151);
				made++;
			}
			bosses++;
			if (c.encode().length() > GearChoices.MOST_TEXT)
			{
				break;
			}
		}
		int kept = 0;
		for (String boss : c.bosses())
		{
			kept += c.of(boss).size();
		}
		// past the limit a change is refused, never taken and then lost: what is kept is all read back
		assertTrue(kept < made);
		String text = c.encode();
		assertTrue(text.length() <= GearChoices.MOST_TEXT);
		// the next time the plugin reads its settings (CaPlannerPlugin.choices), and so the next change written over them
		assertFalse("the text of " + made + " changes on " + bosses + " bosses (" + text.length() + " characters) reads back as no change at all",
			GearChoices.decode(text).isEmpty());
	}

	@Test
	public void howFarARealAccountIsFromTheLimit() throws Exception
	{
		// informative only (prints): a few changes on every boss
		List<String> all = new ArrayList<>(GearEditTest.bosses());
		for (int each : new int[]{3, 5, 10, 16})
		{
			GearChoices c = GearChoices.none();
			for (String boss : all)
			{
				for (int n = 0; n < each; n++)
				{
					c = n < 11 ? c.with(boss, GearChoices.WORN, new int[]{0, 1, 2, 3, 4, 5, 7, 9, 10, 12, 13}[n], 4151, 1, 4151)
						: c.with(boss, GearChoices.BAG, n - 11, 385, 1, 2434);
				}
			}
			System.out.println("REVIEW-PROBE " + all.size() + " bosses x " + each + " changes: " + c.encode().length() + " of "
				+ GearChoices.MOST_TEXT + " characters");
		}
		assertTrue(all.size() > 0);
	}
}
