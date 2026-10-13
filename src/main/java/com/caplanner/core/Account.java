/*
 * Copyright (c) 2026, GuimGod
 * All rights reserved. Licensed under the BSD 2-Clause License (see LICENSE).
 */
package com.caplanner.core;

import java.util.HashMap;
import java.util.Map;

/**
 * What the plugin knows about the player's account at one moment: enough to tell whether a
 * task can be attempted. Plain values, so the checks can be tested without a client.
 */
public final class Account
{
	/** How far a quest is. */
	public enum QuestProgress
	{
		NOT_STARTED, STARTED, FINISHED
	}

	/** Real (unboosted) levels, by skill name in lower case. */
	public final Map<String, Integer> levels = new HashMap<>();
	/** Quest progress by {@link Requirements#key} of the quest's name. A quest that is not here is not known. */
	public final Map<String, QuestProgress> quests = new HashMap<>();
	/** The current Slayer task as the game names it, or null with no task. */
	public String slayerTask;
	/** There is a Slayer task but its name could not be read. */
	public boolean slayerTaskUnknown;
	/** Other things the account has done once, by the names the requirements table uses ("gauntlet-done"). */
	public final java.util.Set<String> done = new java.util.HashSet<>();

	public int level(String skill)
	{
		return levels.getOrDefault(skill.toLowerCase(), 1);
	}
}
