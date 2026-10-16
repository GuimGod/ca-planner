package com.caplanner.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import net.runelite.client.hiscore.HiscoreSkill;
import org.junit.Test;

public class BossIconsTest
{
	private static List<String> hiscores()
	{
		List<String> names = new ArrayList<>();
		for (HiscoreSkill s : HiscoreSkill.values())
		{
			names.add(s.getName());
		}
		return names;
	}

	@Test
	public void everyBossOfTheAchievementsHasAnIcon() throws Exception
	{
		List<String> hiscores = hiscores();
		TreeSet<String> without = new TreeSet<>();
		TreeSet<String> byItem = new TreeSet<>();
		try (BufferedReader in = new BufferedReader(new InputStreamReader(
			BossIconsTest.class.getResourceAsStream("/com/caplanner/bosses.txt"), StandardCharsets.UTF_8)))
		{
			String boss;
			while ((boss = in.readLine()) != null)
			{
				if (boss.trim().isEmpty() || BossIcons.match(boss, hiscores) != null)
				{
					continue;
				}
				if (BossIcons.item(boss) > 0)
				{
					byItem.add(BossIcons.key(boss));
				}
				else
				{
					without.add(boss);
				}
			}
		}
		assertEquals("bosses with no icon at all", "[]", without.toString());
		// an item only for a boss the hiscores do not have: none left over from a boss they now have
		assertEquals(new TreeSet<>(BossIcons.withItems()).toString(), byItem.toString());
	}

	@Test
	public void namesAreMatchedLoosely()
	{
		List<String> hiscores = hiscores();
		assertEquals("Vardorvis", BossIcons.match("Vardorvis", hiscores));
		assertEquals(BossIcons.match("Leviathan", hiscores), BossIcons.match("The Leviathan", hiscores));
		assertEquals(BossIcons.match("Theatre of Blood", hiscores), BossIcons.match("Theatre of Blood: Entry Mode", hiscores));
		assertNull(BossIcons.match("Bloodveld", hiscores));
		assertNull(BossIcons.match(null, hiscores));
		assertEquals(13496, BossIcons.item("Bloodveld"));
		assertEquals(-1, BossIcons.item("Vardorvis"));
		assertEquals(-1, BossIcons.item(null));
	}
}
