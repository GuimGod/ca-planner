/*
 * Copyright (c) 2026, GuimGod
 * All rights reserved. Licensed under the BSD 2-Clause License (see LICENSE).
 */
package com.caplanner.core;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** The time a Speed task allows, read from its own objective ("in less than 1:05", "within 6 minutes"). */
public final class SpeedLimit
{
	private static final Pattern CLOCK = Pattern.compile("\\b(\\d{1,2}):(\\d{2})\\b");
	private static final Pattern MINUTES = Pattern.compile("\\b(\\d+) ?(?:minutes?|mins?)(?: and)?(?: (\\d+) seconds?)?", Pattern.CASE_INSENSITIVE);
	private static final Pattern SECONDS = Pattern.compile("\\b(\\d+) seconds?", Pattern.CASE_INSENSITIVE);
	private static final Pattern TIMES = Pattern.compile("\\b(\\d+) times\\b", Pattern.CASE_INSENSITIVE);
	private static final Pattern WHOLE_RUN = Pattern.compile("\\b(complete|levels|all three|average)\\b", Pattern.CASE_INSENSITIVE);

	private SpeedLimit()
	{
	}

	/** The limit in seconds, or 0 when the objective names none ("in the target time"). */
	public static int seconds(String objective)
	{
		if (objective == null)
		{
			return 0;
		}
		Matcher m = CLOCK.matcher(objective);
		if (m.find())
		{
			return Integer.parseInt(m.group(1)) * 60 + Integer.parseInt(m.group(2));
		}
		m = MINUTES.matcher(objective);
		if (m.find())
		{
			return Integer.parseInt(m.group(1)) * 60 + (m.group(2) == null ? 0 : Integer.parseInt(m.group(2)));
		}
		m = SECONDS.matcher(objective);
		return m.find() ? Integer.parseInt(m.group(1)) : 0;
	}

	/**
	 * Whether the limit is for one kill of the boss, so that the time a set needs to deal the
	 * boss's hitpoints can be held against it. False for several kills in a row, a whole raid or
	 * run, and an average over kills: there the limit covers more than one fight.
	 */
	public static boolean isOneKill(String objective)
	{
		return objective != null && seconds(objective) > 0 && !TIMES.matcher(objective).find() && !WHOLE_RUN.matcher(objective).find();
	}

	/** "1:05" for 65. */
	public static String clock(int seconds)
	{
		return seconds / 60 + ":" + (seconds % 60 < 10 ? "0" : "") + seconds % 60;
	}
}
