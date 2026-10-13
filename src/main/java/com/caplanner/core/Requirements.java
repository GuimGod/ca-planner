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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * What stands between an account and a task: the quests, levels and Slayer task its boss needs
 * (from the bundled table) and whether the task's own wording asks for more than one player.
 */
public final class Requirements
{
	/** A task that says it takes several players. */
	private static final Pattern GROUP = Pattern.compile(
		"group of (two|three|four|five|\\d)|group size|in a group|\\bduo\\b|\\btrio\\b|team of|\\d-scale"
			+ "|with (two|three|four|five|\\d) or more|or more players|another player|other players|teammate|team ?mate"
			+ "|at least \\d (people|players)|with one player alive",
		Pattern.CASE_INSENSITIVE);
	/**
	 * Wording that looks like a group but is not: a Colosseum modifier's name, "at any group size",
	 * and an upper limit on players ("Nex Trio: three or less players"), which one player meets.
	 */
	private static final Pattern NOT_GROUP = Pattern.compile(
		"Dynamic Duo|at any group size|Nex (Trio|Duo)|(two|three|four|five|\\d) or (less|fewer) players",
		Pattern.CASE_INSENSITIVE);

	/** Something to have done once that is neither a quest nor a level: a first completion, a cape handed over, a diary. */
	static final class Other
	{
		/** The name the account reader gives it when done ("gauntlet-done"). */
		String done;
		/** Slayer tasks that do as well; empty when none does. */
		final List<String> orOnTask = new ArrayList<>();
		/** How to say it is missing. */
		String text;
	}

	/** What one boss needs. */
	static final class Boss
	{
		final List<Other> others = new ArrayList<>();
		/** Quest name to whether having started it is enough. */
		final Map<String, Boolean> quests = new LinkedHashMap<>();
		/** Skill name to level. */
		final Map<String, Integer> levels = new LinkedHashMap<>();
		/** The Slayer tasks the boss can be killed on, when it needs one; empty when it does not. */
		final List<String> slayerTasks = new ArrayList<>();
	}

	private final Map<String, Boss> bosses = new HashMap<>();

	/** Lower case, letters and digits only: how boss, quest and task names are compared. */
	public static String key(String name)
	{
		return name == null ? "" : name.toLowerCase().replaceAll("[^a-z0-9]", "");
	}

	/**
	 * Reads the table: one boss a line, tab-separated, "#" for a comment.
	 * Columns: boss, quests ("; " between them, a trailing "*" when having started is enough),
	 * levels ("Skill N; ..."), Slayer tasks the boss can only be killed on ("; " between the
	 * names the game may give that task), and other things to have done ("name=how to say it").
	 */
	public static Requirements read(Reader table) throws IOException
	{
		Requirements out = new Requirements();
		BufferedReader in = new BufferedReader(table);
		String line;
		while ((line = in.readLine()) != null)
		{
			if (line.trim().isEmpty() || line.startsWith("#"))
			{
				continue;
			}
			String[] cols = line.split("\t", -1);
			Boss boss = new Boss();
			for (String quest : cell(cols, 1))
			{
				boolean started = quest.endsWith("*");
				boss.quests.put(started ? quest.substring(0, quest.length() - 1).trim() : quest, started);
			}
			for (String level : cell(cols, 2))
			{
				// a cell that is not "Skill N" is left out, rather than losing the whole table
				int space = level.lastIndexOf(' ');
				try
				{
					boss.levels.put(level.substring(0, Math.max(0, space)).trim(), Integer.parseInt(level.substring(space + 1).trim()));
				}
				catch (NumberFormatException e)
				{
					continue;
				}
			}
			boss.slayerTasks.addAll(cell(cols, 3));
			for (String other : cell(cols, 4))
			{
				// "what must be done=how to say it", with "/task:Name,Name" after the first part when being
				// on one of those Slayer tasks does as well
				int eq = other.indexOf('=');
				if (eq <= 0)
				{
					continue;
				}
				Other o = new Other();
				String what = other.substring(0, eq).trim();
				int slash = what.indexOf("/task:");
				o.done = (slash < 0 ? what : what.substring(0, slash)).trim().toLowerCase();
				if (slash >= 0)
				{
					for (String name : what.substring(slash + 6).split(","))
					{
						if (!name.trim().isEmpty())
						{
							o.orOnTask.add(name.trim());
						}
					}
				}
				o.text = other.substring(eq + 1).trim();
				boss.others.add(o);
			}
			out.bosses.put(key(cols[0]), boss);
		}
		return out;
	}

	private static List<String> cell(String[] cols, int index)
	{
		List<String> out = new ArrayList<>();
		if (index < cols.length)
		{
			for (String part : cols[index].split(";"))
			{
				if (!part.trim().isEmpty())
				{
					out.add(part.trim());
				}
			}
		}
		return out;
	}

	/** The names of every quest the table mentions: the only ones the account needs read. */
	public List<String> questNames()
	{
		List<String> names = new ArrayList<>();
		for (Boss b : bosses.values())
		{
			for (String q : b.quests.keySet())
			{
				if (!names.contains(q))
				{
					names.add(q);
				}
			}
		}
		return names;
	}

	/** Whether the task's wording asks for more than one player. */
	public static boolean needsGroup(Task task)
	{
		return GROUP.matcher(NOT_GROUP.matcher(task.name + " " + task.description).replaceAll("")).find();
	}

	/** Whether the account can attempt the task now, with what is missing when it cannot. */
	public Verdict check(Task task, Account account)
	{
		List<String> missing = new ArrayList<>();
		Boss boss = bosses.get(key(task.boss));
		if (boss != null)
		{
			for (Map.Entry<String, Boolean> q : boss.quests.entrySet())
			{
				Account.QuestProgress progress = account.quests.get(key(q.getKey()));
				boolean met = progress == Account.QuestProgress.FINISHED
					|| (q.getValue() && progress == Account.QuestProgress.STARTED);
				if (!met)
				{
					missing.add((q.getValue() ? "Start " : "Finish ") + q.getKey());
				}
			}
			for (Map.Entry<String, Integer> l : boss.levels.entrySet())
			{
				int have = account.level(l.getKey());
				if (have < l.getValue())
				{
					missing.add(l.getKey() + " " + l.getValue() + " (you have " + have + ")");
				}
			}
			if (!boss.slayerTasks.isEmpty() && !onTask(boss.slayerTasks, account))
			{
				missing.add("Only on a Slayer task: " + boss.slayerTasks.get(0));
			}
			for (Other o : boss.others)
			{
				if (!account.done.contains(o.done) && (o.orOnTask.isEmpty() || !onTask(o.orOnTask, account)))
				{
					missing.add(o.text);
				}
			}
		}
		if (!missing.isEmpty())
		{
			return new Verdict(Verdict.Status.BLOCKED, missing);
		}
		if (needsGroup(task))
		{
			List<String> group = new ArrayList<>();
			group.add("Needs more than one player");
			return new Verdict(Verdict.Status.NEEDS_GROUP, group);
		}
		return Verdict.CAN;
	}

	/**
	 * Whether the account's Slayer task lets it kill a boss that needs one of {@code needed}.
	 * A task the plugin could not name, and the "Bosses" task (whose boss it does not read),
	 * count as yes: better to send the player than to hide a boss that can be killed.
	 */
	static boolean onTask(List<String> needed, Account account)
	{
		if (account.slayerTaskUnknown)
		{
			return true;
		}
		String current = account.slayerTask;
		if (current == null)
		{
			return false;
		}
		if (key(current).equals("bosses") || key(current).equals("boss"))
		{
			return true;
		}
		for (String name : needed)
		{
			if (sameTask(name, current))
			{
				return true;
			}
		}
		return false;
	}

	/**
	 * Whether the boss is one of the account's Slayer task, named as such: then the Slayer helmet
	 * counts against it. A boss that needs no task, the "Bosses" task and an unreadable task are no.
	 */
	public boolean isOnItsTask(String bossName, Account account)
	{
		Boss boss = bosses.get(key(bossName));
		if (boss == null || account == null || account.slayerTask == null)
		{
			return false;
		}
		for (String name : boss.slayerTasks)
		{
			if (sameTask(name, account.slayerTask))
			{
				return true;
			}
		}
		return false;
	}

	/** Task names compared without case, spaces or a plural "s": "Abyssal demons" is "ABYSSAL DEMON". */
	static boolean sameTask(String needed, String current)
	{
		return current != null && singular(key(needed)).equals(singular(key(current)));
	}

	private static String singular(String k)
	{
		return k.endsWith("s") ? k.substring(0, k.length() - 1) : k;
	}
}
