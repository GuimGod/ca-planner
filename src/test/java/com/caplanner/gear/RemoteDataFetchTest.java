/*
 * Copyright (c) 2026, GuimGod
 * All rights reserved. Licensed under the BSD 2-Clause License (see LICENSE).
 */
package com.caplanner.gear;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Response;
import okhttp3.ResponseBody;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/** The whole of the fetch, against answers made up here: no request leaves the machine. */
public class RemoteDataFetchTest
{
	/** What was asked for, in order, and what is answered for each file; a file with no answer is a 404. */
	private static final class Site
	{
		final Map<String, String> answers = new HashMap<>();
		final List<String> asked = Collections.synchronizedList(new ArrayList<>());

		OkHttpClient client()
		{
			return new OkHttpClient.Builder().addInterceptor(chain ->
			{
				String url = chain.request().url().toString();
				assertTrue(url, url.startsWith(RemoteData.BASE));
				String file = url.substring(RemoteData.BASE.length());
				asked.add(file);
				String text = answers.get(file);
				return new Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
					.code(text == null ? 404 : 200).message(text == null ? "Not Found" : "OK")
					.body(ResponseBody.create(MediaType.parse("text/plain"), text == null ? "404: Not Found" : text)).build();
			}).build();
		}
	}

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
		StringBuilder text = new StringBuilder("stamp\t" + stamp + "\n");
		for (String file : RemoteData.FILES)
		{
			text.append(file).append('\t').append(RemoteData.sha256(files.get(file))).append('\n');
		}
		return text.toString();
	}

	/** Runs the fetch to its end and gives the tables it handed over, or null when it handed over none. */
	private static StyleData fetch(Site site, RemoteData.Manifest bundledList) throws Exception
	{
		OkHttpClient http = site.client();
		AtomicReference<StyleData> got = new AtomicReference<>();
		RemoteData.fetch(http, bundledList, new StyleData(), got::set);
		// every request is made from the one before it: wait until none is running or queued
		long until = System.currentTimeMillis() + 10000;
		do
		{
			Thread.sleep(50);
		}
		while ((http.dispatcher().runningCallsCount() > 0 || http.dispatcher().queuedCallsCount() > 0) && System.currentTimeMillis() < until);
		Thread.sleep(100);
		http.dispatcher().executorService().shutdown();
		assertTrue(http.dispatcher().executorService().awaitTermination(10, TimeUnit.SECONDS));
		return got.get();
	}

	private static Site siteWith(String stamp, Map<String, String> files)
	{
		Site site = new Site();
		site.answers.put(RemoteData.MANIFEST, listOf(stamp, files));
		site.answers.putAll(files);
		return site;
	}

	@Test
	public void newerTablesAreFetchedAndHandedOver() throws Exception
	{
		Map<String, String> files = bundledFiles();
		files.put("monsters.tsv", files.get("monsters.tsv") + files.get("monsters.tsv").split("\n")[5].replaceFirst("^\\d+\t[^\t]*", "999999\tA monster of next week") + "\n");
		Site site = siteWith("2099-01-01T00:00", files);
		StyleData got = fetch(site, RemoteData.bundled());
		assertNotNull(got);
		assertEquals("A monster of next week", got.monster(999999).name);
		assertEquals(4, site.asked.size());
		assertEquals(RemoteData.MANIFEST, site.asked.get(0));
	}

	@Test
	public void tablesThatAreNotNewerAreNotEvenAskedFor() throws Exception
	{
		Site site = siteWith(RemoteData.bundled().stamp, bundledFiles());
		assertNull(fetch(site, RemoteData.bundled()));
		assertEquals(Collections.singletonList(RemoteData.MANIFEST), site.asked);
		Site older = siteWith("2000-01-01T00:00", bundledFiles());
		assertNull(fetch(older, RemoteData.bundled()));
		assertEquals(1, older.asked.size());
	}

	@Test
	public void withNothingPublishedTheBundledTablesStay() throws Exception
	{
		Site empty = new Site();
		assertNull(fetch(empty, RemoteData.bundled()));
		assertEquals(Collections.singletonList(RemoteData.MANIFEST), empty.asked);
	}

	@Test
	public void aFileMissingOrChangedOnTheWayKeepsTheBundledTables() throws Exception
	{
		Site missing = siteWith("2099-01-01T00:00", bundledFiles());
		missing.answers.remove("styles.tsv");
		assertNull(fetch(missing, RemoteData.bundled()));

		Site changed = siteWith("2099-01-01T00:00", bundledFiles());
		changed.answers.put("monsters.tsv", changed.answers.get("monsters.tsv").replaceFirst("Vorkath", "Vorkatt"));
		assertNull(fetch(changed, RemoteData.bundled()));
	}

	@Test
	public void withNoListOfItsOwnThePluginAsksForNothing() throws Exception
	{
		Site site = siteWith("2099-01-01T00:00", bundledFiles());
		assertNull(fetch(site, null));
		assertTrue(site.asked.isEmpty());
	}
}
