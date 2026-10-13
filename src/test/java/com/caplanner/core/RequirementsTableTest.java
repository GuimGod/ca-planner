package com.caplanner.core;

import static org.junit.Assert.assertTrue;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import net.runelite.api.Quest;
import net.runelite.api.Skill;
import org.junit.Test;

/** The bundled table must only name quests, skills and bosses that exist, or a requirement would never be met. */
public class RequirementsTableTest
{
	@Test
	public void everyNameInTheTableExists() throws Exception
	{
		Set<String> quests = new HashSet<>();
		for (Quest q : Quest.values())
		{
			quests.add(Requirements.key(q.getName()));
		}
		Set<String> skills = new HashSet<>();
		for (Skill s : Skill.values())
		{
			skills.add(s.getName().toLowerCase());
		}
		Set<String> bosses = new HashSet<>();
		try (BufferedReader in = reader("/com/caplanner/bosses.txt"))
		{
			String line;
			while ((line = in.readLine()) != null)
			{
				bosses.add(Requirements.key(line));
			}
		}
		try (BufferedReader in = reader("/com/caplanner/requirements.tsv"))
		{
			String line;
			while ((line = in.readLine()) != null)
			{
				if (line.trim().isEmpty() || line.startsWith("#"))
				{
					continue;
				}
				String[] cols = line.split("\t", -1);
				assertTrue("boss not in the Combat Achievements: " + cols[0], bosses.contains(Requirements.key(cols[0])));
				for (String quest : cols[1].split(";"))
				{
					String name = quest.replace("*", "").trim();
					assertTrue("quest the client does not know: " + name, name.isEmpty() || quests.contains(Requirements.key(name)));
				}
				for (String level : cols[2].split(";"))
				{
					if (!level.trim().isEmpty())
					{
						String skill = level.trim().substring(0, level.trim().lastIndexOf(' ')).toLowerCase();
						assertTrue("skill the client does not know: " + skill, skills.contains(skill));
					}
				}
			}
		}
	}

	private static BufferedReader reader(String resource)
	{
		return new BufferedReader(new InputStreamReader(
			RequirementsTableTest.class.getResourceAsStream(resource), StandardCharsets.UTF_8));
	}
}
