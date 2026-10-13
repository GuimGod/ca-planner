/*
 * Copyright (c) 2026, GuimGod
 * All rights reserved. Licensed under the BSD 2-Clause License (see LICENSE).
 */
package com.caplanner.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * What the panel lists, worked out from the tasks, the finished ones and what the account can
 * attempt. No game types: everything here can be tested without a client.
 */
public final class Planner
{
	private Planner()
	{
	}

	/** Everything the lists are worked out from, at one moment. */
	public static final class State
	{
		public final List<Task> tasks;
		/** Ids of the finished tasks. */
		public final Set<Integer> done;
		/** Whether each task can be attempted; a task that is not here can. */
		public final Map<Integer, Verdict> verdicts;
		/** Share of players who have done each task, in percent; a task that is not here is not known. */
		public final Map<Integer, Double> ease;

		public State(List<Task> tasks, Set<Integer> done, Map<Integer, Verdict> verdicts, Map<Integer, Double> ease)
		{
			this.tasks = Collections.unmodifiableList(new ArrayList<>(tasks));
			this.done = Collections.unmodifiableSet(new HashSet<>(done));
			this.verdicts = verdicts == null ? Collections.emptyMap() : verdicts;
			this.ease = ease == null ? Collections.emptyMap() : ease;
		}

		public static State empty()
		{
			return new State(Collections.emptyList(), Collections.emptySet(), null, null);
		}

		public boolean isDone(Task t)
		{
			return done.contains(t.id);
		}

		public Verdict verdict(Task t)
		{
			return verdicts.getOrDefault(t.id, Verdict.CAN);
		}

		/** Percent of players who have done the task, or -1 when not known. */
		public double ease(Task t)
		{
			return ease.getOrDefault(t.id, -1.0);
		}
	}

	/** How a list of tasks is ordered, inside each section. */
	public enum Sort
	{
		/** The tasks most players have done first. */
		EASIEST,
		/** The tasks worth the most points first. */
		POINTS,
		/** The most points for the least trouble first: see {@link #value}. */
		VALUE
	}

	/**
	 * What a task is worth going for: its points, cut down when few players have done it. A task
	 * a quarter of the players have done counts in full; below that, in proportion (a 5-point
	 * task 5% have done counts as 1). With no figure for the task it counts as half.
	 */
	public static double value(State state, Task t)
	{
		double ease = state.ease(t);
		double share = ease < 0 ? 0.5 : Math.min(1, Math.max(ease, 1) / 25);
		return t.points() * share;
	}

	/** One boss in the list, with how much of it is left. */
	public static final class BossRow
	{
		public final String name;
		public final int total;
		public final int done;
		public final int pointsLeft;
		/** Tasks left that can be attempted now. */
		public final int canNow;
		/** The {@link #value} of the tasks that can be attempted now, added up: what a trip there is worth. */
		public final double value;

		BossRow(String name, int total, int done, int pointsLeft, int canNow, double value)
		{
			this.name = name;
			this.total = total;
			this.done = done;
			this.pointsLeft = pointsLeft;
			this.canNow = canNow;
			this.value = value;
		}

		public int left()
		{
			return total - done;
		}
	}

	/** What the "All" tab shows. */
	public static final class Filter
	{
		/** Leave finished tasks out. */
		public boolean pendingOnly = true;
		/** Leave out the tasks that cannot be attempted now. */
		public boolean canOnly;
		/** Only this tier, or null for every tier. */
		public Tier tier;
		/** Only this type, or null for every type. */
		public TaskType type;
		/** What the player typed, or null. Matched against the task's name, boss and objective. */
		public String typed;
		public Sort sort = Sort.EASIEST;
	}

	/** Letters and digits only, lower case: how names are compared with what the player types. */
	public static String key(String text)
	{
		return text == null ? "" : text.toLowerCase().replaceAll("[^a-z0-9]", "");
	}

	/** Whether what was typed is the start of something players call the boss ("cg", "dks", "gaunt"). */
	private static boolean nicknamed(String typedKey, String boss)
	{
		if (typedKey.isEmpty())
		{
			return false;
		}
		for (String name : Nicknames.of(boss))
		{
			// from its start: two letters typed must not find every boss that has them somewhere in a nickname
			if (name.startsWith(typedKey))
			{
				return true;
			}
		}
		return false;
	}

	private static boolean matches(String typedKey, String... texts)
	{
		if (typedKey.isEmpty())
		{
			return true;
		}
		for (String t : texts)
		{
			if (key(t).contains(typedKey))
			{
				return true;
			}
		}
		return false;
	}

	/**
	 * The bosses whose name has what was typed. Those with something to do now come first, the
	 * trip worth the most on top (the {@link #value} of what can be done there); then those with
	 * tasks left but none possible; finished bosses go last. Ties are broken by points left and
	 * then by name, so the order never jumps.
	 */
	public static List<BossRow> bosses(State state, String typed)
	{
		Map<String, int[]> byBoss = new LinkedHashMap<>();
		Map<String, Double> worth = new HashMap<>();
		for (Task t : state.tasks)
		{
			int[] c = byBoss.computeIfAbsent(t.boss, k -> new int[4]);
			c[0]++;
			if (state.isDone(t))
			{
				c[1]++;
			}
			else
			{
				c[2] += t.points();
				if (state.verdict(t).can())
				{
					c[3]++;
					worth.merge(t.boss, value(state, t), Double::sum);
				}
			}
		}
		String typedKey = key(typed);
		List<BossRow> rows = new ArrayList<>();
		for (Map.Entry<String, int[]> e : byBoss.entrySet())
		{
			if (matches(typedKey, e.getKey()) || nicknamed(typedKey, e.getKey()))
			{
				int[] c = e.getValue();
				rows.add(new BossRow(e.getKey(), c[0], c[1], c[2], c[3], worth.getOrDefault(e.getKey(), 0.0)));
			}
		}
		rows.sort(Comparator
			.comparing((BossRow r) -> r.left() == 0 ? 2 : r.canNow == 0 ? 1 : 0)
			.thenComparing(r -> -r.value)
			.thenComparing(r -> -r.pointsLeft)
			.thenComparing(r -> r.name.toLowerCase()));
		return rows;
	}

	/** A boss's tasks, in the order of {@link #order}. */
	public static List<Task> ofBoss(State state, String boss)
	{
		List<Task> out = new ArrayList<>();
		for (Task t : state.tasks)
		{
			if (t.boss.equals(boss))
			{
				out.add(t);
			}
		}
		out.sort(order(state, Sort.EASIEST));
		return out;
	}

	/** Every task the filter lets through, in the order of {@link #order}. */
	public static List<Task> all(State state, Filter filter)
	{
		String typedKey = key(filter.typed);
		List<Task> out = new ArrayList<>();
		for (Task t : state.tasks)
		{
			if (filter.pendingOnly && state.isDone(t))
			{
				continue;
			}
			if (filter.canOnly && (state.isDone(t) || !state.verdict(t).can()))
			{
				continue;
			}
			if (filter.tier != null && t.tier != filter.tier)
			{
				continue;
			}
			if (filter.type != null && t.type != filter.type)
			{
				continue;
			}
			if (matches(typedKey, t.name, t.boss, t.description) || nicknamed(typedKey, t.boss))
			{
				out.add(t);
			}
		}
		out.sort(order(state, filter.sort == null ? Sort.EASIEST : filter.sort));
		return out;
	}

	/** Where a task goes in a list: 0 can be done now, 1 needs a group, 2 is blocked, 3 is finished. */
	public static int section(State state, Task t)
	{
		if (state.isDone(t))
		{
			return 3;
		}
		switch (state.verdict(t).status)
		{
			case CAN:
				return 0;
			case NEEDS_GROUP:
				return 1;
			default:
				return 2;
		}
	}

	/**
	 * What can be done now first, then what needs a group, what is blocked, and the finished ones.
	 * Inside each, as {@code sort} says; then the tasks most players have done (the easiest) and,
	 * when that is not known, the lower tier.
	 */
	static Comparator<Task> order(State state, Sort sort)
	{
		return Comparator
			.comparing((Task t) -> section(state, t))
			.thenComparing(t -> sort == Sort.POINTS ? -t.points() : sort == Sort.VALUE ? -value(state, t) : 0.0)
			.thenComparing(t -> -state.ease(t))
			.thenComparing(t -> t.tier.ordinal())
			.thenComparing(t -> t.boss.toLowerCase())
			.thenComparing(t -> t.name.toLowerCase())
			.thenComparing(t -> t.id);
	}

	/** Points of the finished tasks. */
	public static int points(State state)
	{
		int sum = 0;
		for (Task t : state.tasks)
		{
			if (state.isDone(t))
			{
				sum += t.points();
			}
		}
		return sum;
	}

	public static int totalPoints(State state)
	{
		int sum = 0;
		for (Task t : state.tasks)
		{
			sum += t.points();
		}
		return sum;
	}

	/** Tasks left that can be attempted now. */
	public static int canNow(State state)
	{
		int n = 0;
		for (Task t : state.tasks)
		{
			if (section(state, t) == 0)
			{
				n++;
			}
		}
		return n;
	}
}
