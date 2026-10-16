/*
 * Copyright (c) 2026, GuimGod
 * All rights reserved. Licensed under the BSD 2-Clause License (see LICENSE).
 */
package com.caplanner.gear;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * A newer copy of the tables (weapons, styles, monsters) than the one bundled with the plugin, read
 * once at startup from the plugin's own repository: a monster the game added, or one whose defence it
 * changed, is known without waiting for a new version of the plugin.
 *
 * <p>Only numbers and names travel this way. The tables bundled with the plugin are what it starts
 * with and what it keeps whenever anything about the newer copy is off: no answer, a file cut short,
 * a file that is not the one its list describes, a table that does not read, or one much smaller than
 * the bundled one.
 */
final class RemoteData
{
	/** Where the tables are published, in the layout this version of the plugin reads. */
	static final String BASE = "https://raw.githubusercontent.com/GuimGod/combat-check/master/data/v1/";
	/** The list of the tables: when they were made, and the SHA-256 of each. */
	static final String MANIFEST = "manifest.tsv";
	static final String[] FILES = {"weapons.tsv", "styles.tsv", "monsters.tsv"};
	/** No table is anywhere near this size: a longer answer is not one. */
	static final int MAX_BYTES = 2 * 1024 * 1024;
	/** A newer table with fewer rows than this share of the bundled one is taken for a broken one. */
	static final double SMALLEST_SHARE = 0.9;

	/** When the tables were made, and what each file must hash to. */
	static final class Manifest
	{
		/** An ISO date and time in UTC ("2026-10-13T15:00"): a later one sorts after an earlier one. */
		final String stamp;
		final Map<String, String> sha256 = new HashMap<>();

		Manifest(String stamp)
		{
			this.stamp = stamp;
		}
	}

	private RemoteData()
	{
	}

	/**
	 * Reads the list: a "stamp" line and one line for each file, tab separated ("monsters.tsv", its
	 * SHA-256). Null when it has no stamp or lacks one of the three files.
	 */
	static Manifest manifest(String text)
	{
		if (text == null)
		{
			return null;
		}
		String stamp = null;
		Map<String, String> hashes = new HashMap<>();
		for (String line : text.split("\n"))
		{
			String[] c = line.trim().split("\t");
			if (c.length < 2 || c[0].startsWith("#"))
			{
				continue;
			}
			if ("stamp".equals(c[0]))
			{
				stamp = c[1].trim();
			}
			else
			{
				hashes.put(c[0].trim(), c[1].trim().toLowerCase(java.util.Locale.ROOT));
			}
		}
		if (stamp == null || stamp.isEmpty())
		{
			return null;
		}
		Manifest m = new Manifest(stamp);
		for (String file : FILES)
		{
			String hash = hashes.get(file);
			if (hash == null || hash.length() != 64)
			{
				return null;
			}
			m.sha256.put(file, hash);
		}
		return m;
	}

	/** The list of the tables bundled with the plugin, or null when it is missing or does not read. */
	static Manifest bundled()
	{
		try
		{
			return manifest(StyleData.resource(MANIFEST));
		}
		catch (IOException e)
		{
			return null;
		}
	}

	/** Whether the published tables were made after the bundled ones. */
	static boolean newer(Manifest published, Manifest bundled)
	{
		return published != null && bundled != null && published.stamp.compareTo(bundled.stamp) > 0;
	}

	/** The SHA-256 of a table's text, with line ends made the same whatever the machine that wrote it. */
	static String sha256(String text)
	{
		try
		{
			byte[] hash = MessageDigest.getInstance("SHA-256").digest(text.replace("\r\n", "\n").getBytes(StandardCharsets.UTF_8));
			StringBuilder hex = new StringBuilder();
			for (byte b : hash)
			{
				hex.append(String.format("%02x", b));
			}
			return hex.toString();
		}
		catch (NoSuchAlgorithmException e)
		{
			throw new IllegalStateException(e);
		}
	}

	/**
	 * The tables of the three files, when every one of them is the file the list describes, reads as a
	 * table and is not much smaller than the bundled one. Null otherwise: the bundled tables stay.
	 */
	static StyleData tables(Manifest published, Map<String, String> files, StyleData bundled)
	{
		if (published == null || files == null || bundled == null)
		{
			return null;
		}
		for (String file : FILES)
		{
			String text = files.get(file);
			if (text == null || !sha256(text).equals(published.sha256.get(file)))
			{
				return null;
			}
		}
		StyleData made;
		try
		{
			made = new StyleData(files.get("weapons.tsv"), files.get("styles.tsv"), files.get("monsters.tsv"));
		}
		catch (IOException | RuntimeException e)
		{
			// a row that does not read: a table from a later layout, or a broken one
			return null;
		}
		if (made.monsterCount() < bundled.monsterCount() * SMALLEST_SHARE
			|| made.weaponCount() < bundled.weaponCount() * SMALLEST_SHARE
			|| made.categoryCount() < bundled.categoryCount() * SMALLEST_SHARE)
		{
			return null;
		}
		return made;
	}

	/**
	 * Asks for the list and, when the published tables are newer than the bundled ones, for the three
	 * files. {@code done} is called once with the newer tables, on a thread of the HTTP client; it is
	 * not called at all when the bundled tables are to stay.
	 */
	static void fetch(OkHttpClient http, Manifest bundledList, StyleData bundled, Consumer<StyleData> done)
	{
		if (bundledList == null)
		{
			// nothing to compare the published tables with: none is asked for
			return;
		}
		get(http, MANIFEST, text ->
		{
			final Manifest published = manifest(text);
			if (!newer(published, bundledList))
			{
				return;
			}
			final Map<String, String> files = new HashMap<>();
			next(http, 0, files, () ->
			{
				StyleData made = tables(published, files, bundled);
				if (made != null)
				{
					done.accept(made);
				}
			});
		});
	}

	private static void next(OkHttpClient http, int index, Map<String, String> files, Runnable all)
	{
		if (index >= FILES.length)
		{
			all.run();
			return;
		}
		get(http, FILES[index], text ->
		{
			files.put(FILES[index], text);
			next(http, index + 1, files, all);
		});
	}

	/** One file of the published tables; {@code then} gets its text, and is not called when it did not come whole. */
	private static void get(OkHttpClient http, String file, Consumer<String> then)
	{
		http.newCall(new Request.Builder().url(BASE + file).build()).enqueue(new Callback()
		{
			@Override
			public void onFailure(Call call, IOException e)
			{
				// no network, no answer: the bundled tables stay
			}

			@Override
			public void onResponse(Call call, Response response)
			{
				try (ResponseBody body = response.body())
				{
					if (!response.isSuccessful() || body == null || body.contentLength() > MAX_BYTES)
					{
						return;
					}
					String text = body.string();
					if (text.length() <= MAX_BYTES)
					{
						then.accept(text);
					}
				}
				catch (IOException | RuntimeException e)
				{
					// cut short or unreadable: the bundled tables stay
				}
			}
		});
	}
}
