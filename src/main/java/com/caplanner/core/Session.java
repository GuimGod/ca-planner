/*
 * Copyright (c) 2026, GuimGod
 * All rights reserved. Licensed under the BSD 2-Clause License (see LICENSE).
 */
package com.caplanner.core;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The autopilot: the player says how long they have, and it names the boss worth the most in that
 * time and the next task to go for there, one after another. It only ever picks among tasks the
 * account can attempt now, and it moves on by itself when the game marks one as done.
 */
public final class Session
{
	/** How long the player has. */
	public enum Time
	{
		SHORT("30 minutes"),
		MEDIUM("1 to 2 hours"),
		LONG("More than 2 hours");

		private final String label;

		Time(String label)
		{
			this.label = label;
		}

		public String label()
		{
			return label;
		}
	}

	/** How long a sitting at each boss is, from the bundled table. A boss that is not there is a middling one. */
	public static final class Lengths
	{
		private final Map<String, Time> byBoss = new HashMap<>();

		/** Reads "boss, tab, short | medium | long" lines; "#" starts a comment. */
		public static Lengths read(Reader table) throws IOException
		{
			Lengths out = new Lengths();
			BufferedReader in = new BufferedReader(table);
			String line;
			while ((line = in.readLine()) != null)
			{
				if (line.trim().isEmpty() || line.startsWith("#"))
				{
					continue;
				}
				String[] c = line.split("\t");
				if (c.length < 2)
				{
					continue;
				}
				try
				{
					out.byBoss.put(Requirements.key(c[0]), Time.valueOf(c[1].trim().toUpperCase(Locale.ROOT)));
				}
				catch (IllegalArgumentException e)
				{
					// a word that is not a length: the boss stays a middling one
				}
			}
			return out;
		}

		public Time of(String boss)
		{
			return byBoss.getOrDefault(Requirements.key(boss), Time.MEDIUM);
		}

		public Set<String> bosses()
		{
			return byBoss.keySet();
		}
	}

	/** What to go for now. */
	public static final class Pick
	{
		public final String boss;
		public final Task task;
		/** The tasks to go for at the same boss after this one, in order. */
		public final List<Task> after;

		Pick(String boss, Task task, List<Task> after)
		{
			this.boss = boss;
			this.task = task;
			this.after = after;
		}
	}

	private Session()
	{
	}

	/**
	 * @param current       the boss the player is at, kept while it has a task left; null for none
	 * @param skippedTasks  ids of tasks the player set aside for this sitting
	 * @param skippedBosses bosses the player asked to leave for another time
	 * @return the next thing to go for, or null when nothing the account can do now fits the time
	 */
	public static Pick next(Planner.State state, Lengths lengths, Time time, String current,
		Set<Integer> skippedTasks, Set<String> skippedBosses)
	{
		if (current != null && !skippedBosses.contains(current))
		{
			Pick here = at(state, current, skippedTasks);
			if (here != null)
			{
				return here;
			}
		}
		// the boss worth the most among those a sitting of that length can hold
		for (Planner.BossRow row : Planner.bosses(state, ""))
		{
			if (row.canNow == 0 || skippedBosses.contains(row.name) || lengths.of(row.name).ordinal() > time.ordinal())
			{
				continue;
			}
			Pick pick = at(state, row.name, skippedTasks);
			if (pick != null)
			{
				return pick;
			}
		}
		return null;
	}

	/** The tasks of one boss the account can do now, the ones worth the most first. */
	private static Pick at(Planner.State state, String boss, Set<Integer> skippedTasks)
	{
		List<Task> open = new ArrayList<>();
		for (Task t : Planner.ofBoss(state, boss))
		{
			if (!state.isDone(t) && state.verdict(t).can() && !skippedTasks.contains(t.id))
			{
				open.add(t);
			}
		}
		if (open.isEmpty())
		{
			return null;
		}
		return new Pick(boss, open.get(0), new ArrayList<>(open.subList(1, open.size())));
	}
}
