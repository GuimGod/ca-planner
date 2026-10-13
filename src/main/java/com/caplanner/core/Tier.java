/*
 * Copyright (c) 2026, GuimGod
 * All rights reserved. Licensed under the BSD 2-Clause License (see LICENSE).
 */
package com.caplanner.core;

/** The six Combat Achievement tiers, easiest first. A task is worth its tier's points. */
public enum Tier
{
	EASY("Easy", 1),
	MEDIUM("Medium", 2),
	HARD("Hard", 3),
	ELITE("Elite", 4),
	MASTER("Master", 5),
	GRANDMASTER("Grandmaster", 6);

	private final String label;
	private final int points;

	Tier(String label, int points)
	{
		this.label = label;
		this.points = points;
	}

	public String label()
	{
		return label;
	}

	public int points()
	{
		return points;
	}
}
