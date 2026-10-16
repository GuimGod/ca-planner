/*
 * Copyright (c) 2026, GuimGod
 * All rights reserved. Licensed under the BSD 2-Clause License (see LICENSE).
 */
package com.caplanner.gear;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/** The planner takes a newer copy of the engine's tables, fetched at startup, only in place of the bundled ones. */
public class NewerTablesTest
{
	@Test
	public void aNewerCopyReplacesTheBundledTablesOnce() throws Exception
	{
		Gear gear = new Gear();
		StyleData bundled = gear.tables();
		assertFalse("tables fetched against other tables are left", gear.useNewer(new StyleData(), new StyleData()));
		assertSame(bundled, gear.tables());
		assertFalse("nothing fetched, nothing changed", gear.useNewer(bundled, null));
		StyleData newer = new StyleData();
		assertTrue(gear.useNewer(bundled, newer));
		assertSame(newer, gear.tables());
		assertFalse("a second answer does not undo the first", gear.useNewer(bundled, new StyleData()));
		assertSame(newer, gear.tables());
	}
}
