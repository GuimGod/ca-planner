/*
 * Copyright (c) 2026, GuimGod
 * All rights reserved. Licensed under the BSD 2-Clause License (see LICENSE).
 */
package com.caplanner.gear;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/**
 * The gear engine is a copy of Combat Check's, made by a script (one engine, one source). It fails when a copied
 * file was changed here by hand, and, on a machine that has Combat Check next to this plugin, when Combat Check's
 * engine moved on after the copy.
 */
public class EngineCopyTest
{
	private static final String AGAIN = "copy it again from Combat Check";

	private static Map<String, String> source() throws Exception
	{
		InputStream in = EngineCopyTest.class.getResourceAsStream("engine-source.tsv");
		assertNotNull("engine-source.tsv is missing: " + AGAIN, in);
		Map<String, String> rows = new LinkedHashMap<>();
		try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8)))
		{
			for (String line; (line = r.readLine()) != null; )
			{
				if (!line.startsWith("#") && line.contains("\t"))
				{
					String[] c = line.split("\t", 2);
					rows.put(c[0], c[1]);
				}
			}
		}
		return rows;
	}

	private static String sha256(String text) throws Exception
	{
		StringBuilder hex = new StringBuilder();
		for (byte b : MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)))
		{
			hex.append(String.format("%02x", b));
		}
		return hex.toString();
	}

	@Test
	public void noCopiedFileWasChangedByHand() throws Exception
	{
		List<String> changed = new ArrayList<>();
		for (Map.Entry<String, String> e : source().entrySet())
		{
			if (e.getKey().equals("commit"))
			{
				continue;
			}
			File f = new File(e.getKey());
			String text = f.isFile() ? new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8).replace("\r\n", "\n") : null;
			if (text == null || !sha256(text).equals(e.getValue()))
			{
				changed.add(e.getKey());
			}
		}
		assertTrue("changed by hand (a change to the engine goes into Combat Check first, then " + AGAIN + "): " + changed,
			changed.isEmpty());
	}

	@Test
	public void combatCheckDidNotMoveOn() throws Exception
	{
		File cc = new File("../combat-check");
		if (!new File(cc, ".git").exists())
		{
			return; // not on the authors' machine (the Plugin Hub builds this plugin alone)
		}
		String commit = source().get("commit");
		Process p = new ProcessBuilder("git", "-C", cc.getPath(), "diff", "--name-only", commit, "HEAD", "--",
			"src/main/java/com/combatcheck", "src/main/resources/com/combatcheck", "src/test")
			.redirectErrorStream(true).start();
		List<String> moved = new ArrayList<>();
		try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8)))
		{
			for (String line; (line = r.readLine()) != null; )
			{
				String name = line.substring(line.lastIndexOf('/') + 1);
				for (String copied : source().keySet())
				{
					if (copied.endsWith("/" + name))
					{
						moved.add(line);
					}
				}
			}
		}
		if (p.waitFor() != 0)
		{
			return; // git missing, or the commit is not in that repository: nothing to compare with
		}
		assertTrue("Combat Check's engine changed after commit " + commit + ": " + moved + "; " + AGAIN, moved.isEmpty());
	}
}
