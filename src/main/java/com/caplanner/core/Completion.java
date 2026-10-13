/*
 * Copyright (c) 2026, GuimGod
 * All rights reserved. Licensed under the BSD 2-Clause License (see LICENSE).
 */
package com.caplanner.core;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Which tasks are done. The game keeps one bit per task, 32 to a variable: task {@code id} is
 * bit {@code id % 32} of variable number {@code id / 32}.
 */
public final class Completion
{
	private Completion()
	{
	}

	/** @param words the completion variables, in order; a task past the last one counts as not done */
	public static boolean done(int taskId, int[] words)
	{
		if (taskId < 0 || words == null)
		{
			return false;
		}
		int word = taskId / 32;
		return word < words.length && (words[word] & (1 << (taskId % 32))) != 0;
	}

	public static Set<Integer> doneIds(List<Task> tasks, int[] words)
	{
		Set<Integer> done = new HashSet<>();
		for (Task t : tasks)
		{
			if (done(t.id, words))
			{
				done.add(t.id);
			}
		}
		return done;
	}
}
