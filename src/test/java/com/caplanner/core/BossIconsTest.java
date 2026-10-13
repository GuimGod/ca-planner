package com.caplanner.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.TreeSet;
import net.runelite.client.hiscore.HiscoreSkill;
import org.junit.Test;

public class BossIconsTest
{
	/** The Combat Achievement bosses that are not on the hiscores: these get no icon, on purpose. */
	private static final List<String> NOT_ON_HISCORES = Arrays.asList(
		"Aberrant Spectre", "Basilisk Knight", "Black Dragon", "Bloodveld", "Brutal Black Dragon", "Demonic Gorilla",
		"Fire Giant", "Fragment of Seren", "Galvek", "Gargoyle", "Giants", "Glough", "Greater Demon", "Hellhound",
		"Kurask", "Lizardman Shaman", "None", "Skeletal Wyvern", "Tormented Demon", "Wyrm");

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
	public void everyBossHasAnIconOrIsKnownNotToHaveOne() throws Exception
	{
		List<String> hiscores = hiscores();
		TreeSet<String> without = new TreeSet<>();
		try (BufferedReader in = new BufferedReader(new InputStreamReader(
			BossIconsTest.class.getResourceAsStream("/com/caplanner/bosses.txt"), StandardCharsets.UTF_8)))
		{
			String boss;
			while ((boss = in.readLine()) != null)
			{
				if (!boss.trim().isEmpty() && BossIcons.match(boss, hiscores) == null)
				{
					without.add(boss);
				}
			}
		}
		assertEquals(new TreeSet<>(NOT_ON_HISCORES).toString(), without.toString());
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
	}
}
