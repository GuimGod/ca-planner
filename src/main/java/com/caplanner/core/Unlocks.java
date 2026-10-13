/*
 * Copyright (c) 2026, GuimGod
 * All rights reserved. Licensed under the BSD 2-Clause License (see LICENSE).
 */
package com.caplanner.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What would open the most tasks: every missing thing (a quest, a level, a Slayer task, an item,
 * a group), with the tasks it stands in the way of. Worked out from the verdicts alone.
 */
public final class Unlocks
{
	private Unlocks()
	{
	}

	/** One missing thing and what getting it would do. */
	public static final class Unlock
	{
		/** The missing thing, as the task pages say it. */
		public final String what;
		/** Every task it is in the way of. */
		public final List<Task> tasks = new ArrayList<>();
		/** The tasks it alone is in the way of: these open as soon as it is had. */
		public final List<Task> alone = new ArrayList<>();

		Unlock(String what)
		{
			this.what = what;
		}

		public int alonePoints()
		{
			return points(alone);
		}

		public int points()
		{
			return points(tasks);
		}

		private static int points(List<Task> list)
		{
			int sum = 0;
			for (Task t : list)
			{
				sum += t.points();
			}
			return sum;
		}
	}

	/**
	 * A level is one thing however far the player is from it: "Slayer 87 (you have 85)" and
	 * "Slayer 87 (you have 86)" are the same goal.
	 */
	static String goal(String reason)
	{
		int at = reason.indexOf(" (you have ");
		return at < 0 ? reason : reason.substring(0, at);
	}

	/**
	 * The missing things, the ones that open the most points on their own first. Ties are broken
	 * by every point they help towards, then by name.
	 */
	public static List<Unlock> of(Planner.State state)
	{
		Map<String, Unlock> byGoal = new LinkedHashMap<>();
		for (Task t : state.tasks)
		{
			if (state.isDone(t))
			{
				continue;
			}
			List<String> reasons = state.verdict(t).reasons;
			for (String reason : reasons)
			{
				Unlock u = byGoal.computeIfAbsent(goal(reason), k -> new Unlock(reason));
				u.tasks.add(t);
				// a task that would then still want a group is not opened by this alone
				if (reasons.size() == 1 && (state.verdict(t).status != Verdict.Status.BLOCKED || !Requirements.needsGroup(t)))
				{
					u.alone.add(t);
				}
			}
		}
		List<Unlock> out = new ArrayList<>(byGoal.values());
		out.sort(Comparator.comparing((Unlock u) -> -u.alonePoints())
			.thenComparing(u -> -u.points())
			.thenComparing(u -> u.what.toLowerCase()));
		return out;
	}
}
