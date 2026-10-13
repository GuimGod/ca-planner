/*
 * Copyright (c) 2026, GuimGod
 * All rights reserved. Licensed under the BSD 2-Clause License (see LICENSE).
 */
package com.caplanner.core;

/** One Combat Achievement task, as the game describes it. */
public final class Task
{
	/** The game's own number for the task; also its bit in the completion variables. */
	public final int id;
	public final String name;
	/** The objective, in the game's words. */
	public final String description;
	public final Tier tier;
	public final TaskType type;
	/** The monster or activity the game files the task under. */
	public final String boss;

	public Task(int id, String name, String description, Tier tier, TaskType type, String boss)
	{
		this.id = id;
		this.name = name == null ? "" : name;
		this.description = description == null ? "" : description;
		this.tier = tier;
		this.type = type;
		this.boss = boss == null || boss.trim().isEmpty() ? "Other" : boss.trim();
	}

	public int points()
	{
		return tier.points();
	}
}
