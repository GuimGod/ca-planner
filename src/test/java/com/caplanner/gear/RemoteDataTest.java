/*
 * Copyright (c) 2026, GuimGod
 * All rights reserved. Licensed under the BSD 2-Clause License (see LICENSE).
 */
package com.caplanner.gear;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.Assume;
import org.junit.Test;

/** The newer tables read from the plugin's repository: when they are taken, and when the bundled ones stay. */
public class RemoteDataTest
{
	private static Map<String, String> bundledFiles() throws Exception
	{
		Map<String, String> files = new HashMap<>();
		for (String file : RemoteData.FILES)
		{
			files.put(file, StyleData.resource(file));
		}
		return files;
	}

	private static String listOf(String stamp, Map<String, String> files)
	{
		StringBuilder text = new StringBuilder("# a comment\nstamp\t" + stamp + "\n");
		for (String file : RemoteData.FILES)
		{
			text.append(file).append('\t').append(RemoteData.sha256(files.get(file))).append('\n');
		}
		return text.toString();
	}

	@Test
	public void theListGivesTheStampAndAHashForEachFile() throws Exception
	{
		RemoteData.Manifest m = RemoteData.manifest(listOf("2026-10-13T15:00", bundledFiles()));
		assertNotNull(m);
		assertEquals("2026-10-13T15:00", m.stamp);
		assertEquals(3, m.sha256.size());
		assertEquals(64, m.sha256.get("monsters.tsv").length());
	}

	@Test
	public void aListWithNoStampOrAFileMissingIsNoList() throws Exception
	{
		assertNull(RemoteData.manifest(null));
		assertNull(RemoteData.manifest(""));
		assertNull(RemoteData.manifest("<html>404: Not Found</html>"));
		String whole = listOf("2026-10-13T15:00", bundledFiles());
		assertNull(RemoteData.manifest(whole.replace("stamp\t2026-10-13T15:00\n", "")));
		assertNull(RemoteData.manifest(whole.substring(0, whole.indexOf("monsters.tsv"))));
		// a hash cut short is not a hash
		assertNull(RemoteData.manifest(whole.substring(0, whole.length() - 10)));
	}

	@Test
	public void onlyALaterStampIsNewer() throws Exception
	{
		Map<String, String> files = bundledFiles();
		RemoteData.Manifest bundled = RemoteData.manifest(listOf("2026-10-13T15:00", files));
		assertFalse(RemoteData.newer(RemoteData.manifest(listOf("2026-10-13T15:00", files)), bundled));
		assertFalse(RemoteData.newer(RemoteData.manifest(listOf("2026-10-12T23:59", files)), bundled));
		assertTrue(RemoteData.newer(RemoteData.manifest(listOf("2026-10-13T15:01", files)), bundled));
		assertTrue(RemoteData.newer(RemoteData.manifest(listOf("2026-11-01T00:00", files)), bundled));
		assertFalse(RemoteData.newer(null, bundled));
		assertFalse(RemoteData.newer(bundled, null));
	}

	@Test
	public void theHashIsSha256AndDoesNotCareAboutLineEnds()
	{
		assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", RemoteData.sha256("abc"));
		assertEquals(RemoteData.sha256("a\nb\n"), RemoteData.sha256("a\r\nb\r\n"));
		assertFalse(RemoteData.sha256("a\nb\n").equals(RemoteData.sha256("a\nc\n")));
	}

	@Test
	public void theSameFilesGiveTheSameTables() throws Exception
	{
		StyleData bundled = new StyleData();
		Map<String, String> files = bundledFiles();
		StyleData made = RemoteData.tables(RemoteData.manifest(listOf("2027-01-01T00:00", files)), files, bundled);
		assertNotNull(made);
		assertEquals(bundled.monsterCount(), made.monsterCount());
		assertEquals(bundled.weaponCount(), made.weaponCount());
		assertEquals(bundled.categoryCount(), made.categoryCount());
		assertEquals(bundled.slayerCategories(), made.slayerCategories());
		// Vorkath, and the Scythe of vitur's styles
		assertEquals(bundled.monster(8058).level, made.monster(8058).level);
		assertEquals(bundled.monster(8058).name, made.monster(8058).name);
		assertEquals(bundled.styles(22325).size(), made.styles(22325).size());
	}

	@Test
	public void aNewMonsterInTheNewerTableIsKnown() throws Exception
	{
		StyleData bundled = new StyleData();
		Map<String, String> files = bundledFiles();
		String[] rows = files.get("monsters.tsv").split("\n");
		String vorkath = null;
		for (String row : rows)
		{
			if (row.startsWith("8058\t"))
			{
				vorkath = row;
			}
		}
		assertNotNull(vorkath);
		assertNull(bundled.monster(999999));
		files.put("monsters.tsv", files.get("monsters.tsv") + vorkath.replaceFirst("8058\tVorkath", "999999\tA monster of next week") + "\n");
		StyleData made = RemoteData.tables(RemoteData.manifest(listOf("2027-01-01T00:00", files)), files, bundled);
		assertNotNull(made);
		assertEquals("A monster of next week", made.monster(999999).name);
		assertEquals(bundled.monster(8058).level, made.monster(999999).level);
		assertEquals(bundled.monsterCount() + 1, made.monsterCount());
	}

	@Test
	public void aFileThatIsNotTheOneTheListDescribesKeepsTheBundledTables() throws Exception
	{
		StyleData bundled = new StyleData();
		Map<String, String> files = bundledFiles();
		RemoteData.Manifest list = RemoteData.manifest(listOf("2027-01-01T00:00", files));
		// one character of one file changed after the list was written
		Map<String, String> changed = new HashMap<>(files);
		changed.put("weapons.tsv", files.get("weapons.tsv").replaceFirst("Scythe", "Scythf"));
		assertNull(RemoteData.tables(list, changed, bundled));
		// a file that did not come
		Map<String, String> missing = new HashMap<>(files);
		missing.remove("styles.tsv");
		assertNull(RemoteData.tables(list, missing, bundled));
		assertNull(RemoteData.tables(null, files, bundled));
		assertNull(RemoteData.tables(list, null, bundled));
	}

	@Test
	public void aTableCutShortKeepsTheBundledTablesEvenWhenItsHashAgrees() throws Exception
	{
		StyleData bundled = new StyleData();
		Map<String, String> files = bundledFiles();
		String monsters = files.get("monsters.tsv");
		// the first tenth of the monsters, published with a list that matches it
		files.put("monsters.tsv", monsters.substring(0, monsters.indexOf('\n', monsters.length() / 10) + 1));
		assertNull(RemoteData.tables(RemoteData.manifest(listOf("2027-01-01T00:00", files)), files, bundled));
		// and an empty one
		files.put("monsters.tsv", "");
		assertNull(RemoteData.tables(RemoteData.manifest(listOf("2027-01-01T00:00", files)), files, bundled));
	}

	@Test
	public void aTableThatDoesNotReadKeepsTheBundledTables() throws Exception
	{
		StyleData bundled = new StyleData();
		Map<String, String> files = bundledFiles();
		// a row of another layout: its first column is not an NPC id
		files.put("monsters.tsv", files.get("monsters.tsv") + "not a number\tSomething\t1\n");
		assertNull(RemoteData.tables(RemoteData.manifest(listOf("2027-01-01T00:00", files)), files, bundled));
		Map<String, String> short2 = bundledFiles();
		// a row with too few columns
		short2.put("monsters.tsv", short2.get("monsters.tsv") + "999998\tHalf a row\n");
		assertNull(RemoteData.tables(RemoteData.manifest(listOf("2027-01-01T00:00", short2)), short2, bundled));
	}

	@Test
	public void theListBundledWithThePluginDescribesTheTablesBundledWithIt() throws Exception
	{
		RemoteData.Manifest list = RemoteData.bundled();
		assertNotNull("manifest.tsv is bundled with the tables", list);
		for (String file : RemoteData.FILES)
		{
			assertEquals(file, list.sha256.get(file), RemoteData.sha256(StyleData.resource(file)));
		}
		// so the tables the plugin came with are never taken for newer ones
		assertFalse(RemoteData.newer(list, list));
	}

	/** In the plugin's own repository the published tables are the bundled ones: what the tests checked is what is fetched. */
	@Test
	public void theTablesPublishedAreTheTablesBundled() throws Exception
	{
		File dir = new File("data/v1");
		Assume.assumeTrue("not run from the plugin's repository", dir.isDirectory());
		String list = new String(Files.readAllBytes(new File(dir, RemoteData.MANIFEST).toPath()), StandardCharsets.UTF_8);
		RemoteData.Manifest published = RemoteData.manifest(list);
		assertNotNull(published);
		assertEquals(RemoteData.bundled().stamp, published.stamp);
		for (String file : RemoteData.FILES)
		{
			String text = new String(Files.readAllBytes(new File(dir, file).toPath()), StandardCharsets.UTF_8);
			assertEquals(file, RemoteData.sha256(StyleData.resource(file)), RemoteData.sha256(text));
			assertEquals(file, published.sha256.get(file), RemoteData.sha256(text));
		}
	}
}
