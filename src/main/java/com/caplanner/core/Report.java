/*
 * Copyright (c) 2026, GuimGod
 * All rights reserved. Licensed under the BSD 2-Clause License (see LICENSE).
 */
package com.caplanner.core;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * A bug report a player writes about what the planner told them for a boss or a task: their own
 * words, and under them what the planner said, so that whoever reads it sees the same thing.
 * Nothing is sent by the plugin: the report is shown to the player, who posts it or copies it.
 * It carries no name of the player or of the account.
 */
public final class Report
{
	/** Where reports are posted. */
	public static final String REPOSITORY = "GuimGod/ca-planner";
	/** A link longer than this is cut short by browsers and by the site: the whole report is copied instead. */
	private static final int LONGEST_BODY_IN_A_LINK = 5500;
	private static final String[] SLOTS = {"head", "cape", "neck", "weapon", "body", "shield", "", "legs", "", "hands", "feet", "", "ring", "ammo"};

	private Report()
	{
	}

	/** What the report is about, in one line: the boss, and the task when it is about one. */
	public static String title(String boss, Task task, String words)
	{
		String about = "[" + boss + "]" + (task == null ? "" : " " + task.name);
		String first = words == null ? "" : words.trim().replaceAll("\\s+", " ");
		if (first.length() > 60)
		{
			first = first.substring(0, 60).trim() + "...";
		}
		return first.isEmpty() ? about : about + ": " + first;
	}

	/**
	 * @param words    what the player wrote
	 * @param view     what the planner showed for the boss or the task; may be null
	 * @param verdict  whether the planner said the task can be done, with why not; may be null
	 * @param account  one line about the account (levels, Slayer task), with no name in it
	 * @param language the language the panel was in
	 */
	public static String body(String boss, Task task, String words, GearView view, Verdict verdict, String account, String language)
	{
		StringBuilder out = new StringBuilder();
		out.append("**What is wrong**\n").append(words == null || words.trim().isEmpty() ? "(nothing written)" : words.trim()).append("\n\n");
		out.append("**Boss:** ").append(boss).append('\n');
		if (task != null)
		{
			out.append("**Task:** ").append(task.name).append(" (id ").append(task.id).append(", ").append(task.tier.label())
				.append(", ").append(task.type.label()).append(")\n");
			out.append("**Objective:** ").append(task.description).append('\n');
		}
		if (verdict != null)
		{
			out.append("**The planner says:** ").append(verdict.status);
			if (!verdict.reasons.isEmpty())
			{
				out.append(" (").append(String.join("; ", verdict.reasons)).append(')');
			}
			out.append('\n');
		}
		out.append("\n**What the planner gave**\n");
		if (view == null)
		{
			out.append("- nothing shown yet\n");
		}
		else
		{
			out.append("- status: ").append(view.status).append('\n');
			if (view.edited())
			{
				// so that nobody hunts for a bug of the planner in a set it did not pick
				out.append("- gear edited by the player: what follows is the card as they changed it\n");
				for (GearView.Change change : view.changes)
				{
					if (!change.line.isEmpty())
					{
						out.append("- change: ").append(change.line).append('\n');
					}
				}
				List<String> recommended = new ArrayList<>();
				for (Map.Entry<Integer, String> e : new TreeMap<>(view.recommended.nameBySlot).entrySet())
				{
					String slot = e.getKey() >= 0 && e.getKey() < SLOTS.length ? SLOTS[e.getKey()] : String.valueOf(e.getKey());
					recommended.add(slot + " " + e.getValue());
				}
				out.append("- recommended set: ").append(String.join(" | ", recommended)).append(", ")
					.append(Math.round(view.recommended.dps * 100) / 100.0).append(" dps\n");
			}
			if (!view.wornFor.isEmpty())
			{
				out.append("- worn for: ").append(view.wornFor).append('\n');
			}
			if (!view.nameBySlot.isEmpty())
			{
				List<String> set = new ArrayList<>();
				for (Map.Entry<Integer, String> e : new TreeMap<>(view.nameBySlot).entrySet())
				{
					String slot = e.getKey() >= 0 && e.getKey() < SLOTS.length ? SLOTS[e.getKey()] : String.valueOf(e.getKey());
					set.add(slot + " " + e.getValue());
				}
				out.append("- set: ").append(String.join(" | ", set)).append('\n');
				out.append("- ").append(view.style).append(", ").append(Math.round(view.dps * 100) / 100.0).append(" dps, boss hitpoints ")
					.append(view.bossHitpoints).append('\n');
			}
			for (GearView.Part part : view.parts)
			{
				List<String> carried = new ArrayList<>();
				for (Bag.Line line : part.carried)
				{
					carried.add(line.name);
				}
				out.append("- part \"").append(part.label).append("\": ").append(part.nothing ? "nothing owned" : part.style
					+ (part.dps > 0 ? ", " + Math.round(part.dps * 100) / 100.0 + " dps" : ", no figure shown")
					+ (part.spell == null ? "" : ", casting " + part.spell))
					.append(carried.isEmpty() ? "" : " -> " + String.join(", ", carried)).append('\n');
			}
			for (Bag.Line line : view.worn)
			{
				out.append("- must wear: ").append(line.name).append(line.owned ? "" : " (not owned)").append('\n');
			}
			if (view.cells != null)
			{
				// the supplies as the player left them, slot by slot
				List<String> bag = new ArrayList<>();
				for (Bag.Line line : view.cells)
				{
					bag.add(line == null ? "(empty)" : (line.stack ? line.quantity + " x " : "") + line.name + (line.owned ? "" : " (not owned)"));
				}
				out.append("- inventory: ").append(String.join(", ", bag)).append(" (as changed, slot by slot)\n");
			}
			else if (!view.bag.isEmpty())
			{
				List<String> bag = new ArrayList<>();
				for (Bag.Line line : view.bag)
				{
					bag.add(line.quantity + " x " + line.name + (line.needed ? " (needed" + (line.owned ? "" : ", not owned") + ")" : ""));
				}
				out.append("- inventory: ").append(String.join(", ", bag)).append('\n');
			}
			if (!view.weapons.isEmpty())
			{
				out.append("- weapons owned with no figure: ").append(String.join(", ", view.weapons)).append('\n');
			}
			for (String note : view.notes)
			{
				out.append("- note: ").append(note).append('\n');
			}
		}
		out.append("\n**Account:** ").append(account == null || account.isEmpty() ? "not known" : account).append('\n');
		out.append("**Panel language:** ").append(language == null ? "English" : language).append('\n');
		return out.toString();
	}

	/**
	 * The page where the report is posted, already filled in. A report too long for a link is cut
	 * at a line and says so: the whole of it is what the "copy" button gives.
	 */
	public static String link(String title, String body)
	{
		String text = body;
		if (text.length() > LONGEST_BODY_IN_A_LINK)
		{
			int cut = text.lastIndexOf('\n', LONGEST_BODY_IN_A_LINK);
			text = text.substring(0, cut < 0 ? LONGEST_BODY_IN_A_LINK : cut)
				+ "\n\n(Cut short to fit in a link. Paste the copied report here for the rest.)";
		}
		return "https://github.com/" + REPOSITORY + "/issues/new?title=" + encode(title) + "&body=" + encode(text);
	}

	private static String encode(String text)
	{
		try
		{
			return URLEncoder.encode(text, "UTF-8").replace("+", "%20");
		}
		catch (UnsupportedEncodingException e)
		{
			throw new IllegalStateException(e);
		}
	}
}
