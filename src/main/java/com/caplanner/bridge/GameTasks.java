/*
 * Copyright (c) 2026, GuimGod
 * All rights reserved. Licensed under the BSD 2-Clause License (see LICENSE).
 */
package com.caplanner.bridge;

import com.caplanner.core.Task;
import com.caplanner.core.TaskType;
import com.caplanner.core.Tier;
import java.util.ArrayList;
import java.util.List;
import net.runelite.api.Client;
import net.runelite.api.EnumComposition;
import net.runelite.api.StructComposition;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.client.util.Text;

/**
 * Reads the Combat Achievement tasks, and which of them are done, from the game itself: the
 * list the Combat Achievements interface is built from. Nothing is bundled, so a task the game
 * adds shows up without an update. Every method must be called on the client thread.
 */
public final class GameTasks
{
	/** One enum per tier, easiest first; each value is the id of a task's struct. */
	private static final int[] TIER_ENUMS = {3981, 3982, 3983, 3984, 3985, 3986};
	/** Boss number to boss name. */
	private static final int BOSS_NAMES_ENUM = 3971;
	private static final int PARAM_ID = 1306;
	private static final int PARAM_NAME = 1308;
	private static final int PARAM_DESCRIPTION = 1309;
	private static final int PARAM_TYPE = 1311;
	private static final int PARAM_BOSS = 1312;

	/** The completion variables, in order: task id / 32 picks the variable. */
	private static final int[] COMPLETED = {
		VarPlayerID.CA_TASK_COMPLETED_0, VarPlayerID.CA_TASK_COMPLETED_1, VarPlayerID.CA_TASK_COMPLETED_2,
		VarPlayerID.CA_TASK_COMPLETED_3, VarPlayerID.CA_TASK_COMPLETED_4, VarPlayerID.CA_TASK_COMPLETED_5,
		VarPlayerID.CA_TASK_COMPLETED_6, VarPlayerID.CA_TASK_COMPLETED_7, VarPlayerID.CA_TASK_COMPLETED_8,
		VarPlayerID.CA_TASK_COMPLETED_9, VarPlayerID.CA_TASK_COMPLETED_10, VarPlayerID.CA_TASK_COMPLETED_11,
		VarPlayerID.CA_TASK_COMPLETED_12, VarPlayerID.CA_TASK_COMPLETED_13, VarPlayerID.CA_TASK_COMPLETED_14,
		VarPlayerID.CA_TASK_COMPLETED_15, VarPlayerID.CA_TASK_COMPLETED_16, VarPlayerID.CA_TASK_COMPLETED_17,
		VarPlayerID.CA_TASK_COMPLETED_18, VarPlayerID.CA_TASK_COMPLETED_19, VarPlayerID.CA_TASK_COMPLETED_20
	};

	private GameTasks()
	{
	}

	/**
	 * Every task the game has. Empty when any of the game's tables is not there (not logged in
	 * yet): a list with a tier or the boss names missing would be kept for the whole session.
	 */
	public static List<Task> read(Client client)
	{
		List<Task> tasks = new ArrayList<>();
		EnumComposition bosses = client.getEnum(BOSS_NAMES_ENUM);
		if (bosses == null)
		{
			return tasks;
		}
		Tier[] tiers = Tier.values();
		for (int i = 0; i < TIER_ENUMS.length; i++)
		{
			EnumComposition tier = client.getEnum(TIER_ENUMS[i]);
			if (tier == null || tier.getIntVals() == null || tier.getIntVals().length == 0)
			{
				return new ArrayList<>();
			}
			for (int structId : tier.getIntVals())
			{
				StructComposition s = client.getStructComposition(structId);
				if (s == null)
				{
					continue;
				}
				String boss = bosses.getStringValue(s.getIntValue(PARAM_BOSS));
				tasks.add(new Task(s.getIntValue(PARAM_ID), plain(s.getStringValue(PARAM_NAME)),
					plain(s.getStringValue(PARAM_DESCRIPTION)), tiers[i], TaskType.of(s.getIntValue(PARAM_TYPE)), plain(boss)));
			}
		}
		return tasks;
	}

	/** Game text without its markup (colour tags, line breaks), which the panel would show as written. */
	private static String plain(String text)
	{
		return text == null ? null : Text.removeTags(text.replace("<br>", " "));
	}

	/** The completion variables as they are now, for {@link com.caplanner.core.Completion}. */
	public static int[] completionWords(Client client)
	{
		int[] words = new int[COMPLETED.length];
		for (int i = 0; i < words.length; i++)
		{
			words[i] = client.getVarpValue(COMPLETED[i]);
		}
		return words;
	}

	/** Whether a variable that just changed is one of the completion variables. */
	public static boolean isCompletionVariable(int varpId)
	{
		for (int id : COMPLETED)
		{
			if (id == varpId)
			{
				return true;
			}
		}
		return false;
	}
}
