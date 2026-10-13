/*
 * Copyright (c) 2026, GuimGod
 * All rights reserved. Licensed under the BSD 2-Clause License (see LICENSE).
 */
package com.caplanner.core;

/** What kind of thing a task asks for, as the game files it. */
public enum TaskType
{
	STAMINA(1, "Stamina"),
	PERFECTION(2, "Perfection"),
	KILL_COUNT(3, "Kill Count"),
	MECHANICAL(4, "Mechanical"),
	RESTRICTION(5, "Restriction"),
	SPEED(6, "Speed"),
	/** A value the game uses that this plugin does not know yet. */
	OTHER(-1, "Other");

	private final int gameValue;
	private final String label;

	TaskType(int gameValue, String label)
	{
		this.gameValue = gameValue;
		this.label = label;
	}

	public String label()
	{
		return label;
	}

	/** The type for the number the game stores on a task. */
	public static TaskType of(int gameValue)
	{
		for (TaskType t : values())
		{
			if (t.gameValue == gameValue)
			{
				return t;
			}
		}
		return OTHER;
	}
}
