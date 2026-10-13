/*
 * Copyright (c) 2026, GuimGod
 * All rights reserved. Licensed under the BSD 2-Clause License (see LICENSE).
 */
package com.caplanner.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Whether a task can be attempted now, and if not, what is missing. It says nothing of skill at the fight. */
public final class Verdict
{
	public enum Status
	{
		/** Nothing the plugin checks stands in the way. */
		CAN,
		/** The account can get there, but the task asks for more than one player. */
		NEEDS_GROUP,
		/** A quest, a level or a Slayer task is missing. */
		BLOCKED
	}

	public static final Verdict CAN = new Verdict(Status.CAN, Collections.emptyList());

	public final Status status;
	/** What is missing, one short line each; empty when the task can be done. */
	public final List<String> reasons;

	Verdict(Status status, List<String> reasons)
	{
		this.status = status;
		this.reasons = Collections.unmodifiableList(new ArrayList<>(reasons));
	}

	/** A task that cannot be attempted, with what is missing. */
	public static Verdict blocked(List<String> reasons)
	{
		return new Verdict(Status.BLOCKED, reasons);
	}

	public boolean can()
	{
		return status == Status.CAN;
	}
}
