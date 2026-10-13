/*
 * Copyright (c) 2026, GuimGod
 * All rights reserved. Licensed under the BSD 2-Clause License (see LICENSE).
 */
package com.caplanner.bridge;

import com.caplanner.core.Account;
import com.caplanner.core.Requirements;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;
import net.runelite.api.gameval.DBTableID;
import net.runelite.api.gameval.VarPlayerID;

/** Reads from the game what {@link Requirements} checks. Every method must be called on the client thread. */
@Slf4j
public final class AccountReader
{
	private AccountReader()
	{
	}

	/**
	 * @param questNames the quests to read; asking the game for a quest's state runs a script,
	 *                   so only the ones the requirements mention are read
	 */
	public static Account read(Client client, Collection<String> questNames)
	{
		Account account = new Account();
		for (Skill skill : Skill.values())
		{
			account.levels.put(skill.getName().toLowerCase(), client.getRealSkillLevel(skill));
		}
		Set<String> wanted = new HashSet<>();
		for (String name : questNames)
		{
			wanted.add(Requirements.key(name));
		}
		for (Quest quest : Quest.values())
		{
			String key = Requirements.key(quest.getName());
			if (!wanted.contains(key))
			{
				continue;
			}
			QuestState state = quest.getState(client);
			account.quests.put(key, state == QuestState.FINISHED ? Account.QuestProgress.FINISHED
				: state == QuestState.IN_PROGRESS ? Account.QuestProgress.STARTED : Account.QuestProgress.NOT_STARTED);
		}
		// things done once that open a boss: the game keeps a count or a flag of each
		if (client.getVarpValue(VarPlayerID.TOTAL_COMPLETED_GAUNTLET) > 0 || client.getVarpValue(VarPlayerID.TOTAL_COMPLETED_GAUNTLET_HM) > 0)
		{
			account.done.add("gauntlet-done");
		}
		if (client.getVarpValue(VarPlayerID.TOTAL_COMPLETED_THEATREOFBLOOD) > 0
			|| client.getVarpValue(VarPlayerID.TOTAL_COMPLETED_THEATREOFBLOOD_HARD) > 0)
		{
			account.done.add("tob-done");
		}
		if (client.getVarbitValue(net.runelite.api.gameval.VarbitID.INFERNO_SACRIFICED_FIRECAPE) > 0)
		{
			account.done.add("fire-cape-given");
		}
		if (client.getVarbitValue(net.runelite.api.gameval.VarbitID.WILDERNESS_DIARY_MEDIUM_COMPLETE) > 0)
		{
			account.done.add("wilderness-medium");
		}
		if (client.getVarpValue(VarPlayerID.SLAYER_COUNT) > 0)
		{
			account.slayerTask = slayerTask(client);
			account.slayerTaskUnknown = account.slayerTask == null;
		}
		return account;
	}

	/** Whether a variable that just changed is one the Slayer task is read from. */
	public static boolean isSlayerVariable(int varpId)
	{
		return varpId == VarPlayerID.SLAYER_COUNT || varpId == VarPlayerID.SLAYER_TARGET;
	}

	/** The name of the current Slayer task, from the game's own table of tasks; null when it cannot be read. */
	private static String slayerTask(Client client)
	{
		try
		{
			int id = client.getVarpValue(VarPlayerID.SLAYER_TARGET);
			List<Integer> rows = client.getDBRowsByValue(DBTableID.SlayerTask.ID, DBTableID.SlayerTask.COL_ID, 0, id);
			if (rows != null && !rows.isEmpty())
			{
				Object[] field = client.getDBTableField(rows.get(0), DBTableID.SlayerTask.COL_NAME_UPPERCASE, 0);
				return field != null && field.length > 0 && field[0] != null ? field[0].toString() : null;
			}
		}
		catch (RuntimeException e)
		{
			log.debug("could not read the slayer task", e);
		}
		return null;
	}
}
